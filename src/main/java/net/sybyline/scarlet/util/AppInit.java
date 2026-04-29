package net.sybyline.scarlet.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AppInit<T>
{

    public static <T> AppInit<T> register(String id, Callable<T> callable) throws AppInitError
    {
        Objects.requireNonNull(id, "id");
        // null check callable later to ensure initializer is marked as registered
        AppInit<T> init;
        synchronized (AppInit.class)
        {
            if (closingThread != null)
                throw new AppInitError("Tried to register initializer "+id+" after await");
            init = findUnsynchronized(id);
            // finish in synchronized to avoid race condition submitting callable to executor
            init.declareRegisteration(callable);
        }
        return init;
    }

    public static <T> AppInit<T> require(String id) throws AppInitError
    {
        Objects.requireNonNull(id, "id");
        AppInit<T> init;
        boolean closed;
        synchronized (AppInit.class)
        {
            closed = closingThread != null;
            init = findUnsynchronized(id);
        }
        if (init == null && closed)
            throw new AppInitError("Tried to require unknown initializer "+id+" after await");
        return init;
    }

    @SuppressWarnings("unchecked")
    private static <T> AppInit<T> findUnsynchronized(String id)
    {
        return (AppInit<T>)registry.computeIfAbsent(id, AppInit::new);
    }

    public static void await(int maxMillis) throws AppInitError
    {
        List<String> unregistered;
        synchronized (AppInit.class)
        {
            if (closingThread != null)
                throw new IllegalStateException("Tried to reawait initialization");
            closingThread = Thread.currentThread();
            unregistered = registry
                .values()
                .stream()
                .map(AppInit::assertRegisteration)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
        try
        {
            if (!unregistered.isEmpty())
                throw new AppInitError("Await detected "+unregistered.size()+" unregistered initializer(s): "+unregistered);
        }
        finally
        {
            executor.shutdown();
            boolean terminated = false;
            try
            {
                terminated = executor.awaitTermination(maxMillis, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException iex)
            {
            }
            finally
            {
                if (!terminated)
                {
                    executor.shutdownNow();
                }
            }
        }
    }

    private static Thread closingThread = null;
    private static final Map<String, AppInit<?>> registry = new HashMap<>();
    private static final Map<String, String> awaitChain = new HashMap<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Object sentinelIncomplete = new Object();

    // Use sentinel value: futures can licitly complete with null value
    @SuppressWarnings("unchecked")
    private static <T> T sentinelIncomplete()
    {
        return (T)sentinelIncomplete;
    }

    private AppInit(String id)
    {
        this.id = id;
        this.future = new CompletableFuture<>();
        this.registered = false;
    }

    private final String id;
    private final CompletableFuture<T> future;
    private boolean registered;

    private void declareRegisteration(Callable<T> callable) throws AppInitError
    {
        synchronized (this)
        {
            if (this.registered)
                throw new AppInitError("Tried to reregister initializer "+this.id);
            this.registered = true;
            if (callable == null)
            {
                this.future.cancel(true);
                throw new AppInitError("Tried to register initializer "+this.id+" with null callable");
            }
            executor.submit(() ->
            {
                try
                {
                    this.future.complete(callable.call());
                }
                catch (Throwable t)
                {
                    this.future.completeExceptionally(t);
                    if (t instanceof InterruptedException || !(t instanceof Exception))
                        Throwables.yeet(t);
                }
            });
        }
    }

    private String assertRegisteration()
    {
        synchronized (this)
        {
            if (this.registered)
                return null;
            this.future.cancel(true);
            return this.id;
        }
    }

    public CompletableFuture<Void> thenDo(Consumer<T> consumer)
    {
        return this.future.thenAccept(Objects.requireNonNull(consumer, "consumer"));
    }
    public <U> CompletableFuture<U> thenGet(Function<T, U> function)
    {
        return this.future.thenApply(Objects.requireNonNull(function, "function"));
    }
    public CompletableFuture<T> future()
    {
        // Use thenApply to avoid exposure of the *mutable* internal CompletedFuture
        return this.future.thenApply(Function.identity());
    }

    public T get() throws AppInitError
    {
        return this.get0(new Throwable().getStackTrace()[1].getClassName());
    }
    public T get(String requester) throws AppInitError
    {
        return this.get0(Objects.requireNonNull(requester, "requester"));
    }
    public T get(AppInit<?> requester) throws AppInitError
    {
        return this.get0(Objects.requireNonNull(requester, "requester").id);
    }
    private T get0(String requester) throws AppInitError
    {
        if (Objects.equals(this.id, requester))
            throw new AppInitError("Initializer "+requester+" requested itself");
        T value;
        try
        {
            value = this.future.getNow(sentinelIncomplete());
        }
        catch (CancellationException cex)
        {
            throw new AppInitError("Initializer "+requester+" requested canceled initializer "+this.id, cex);
        }
        catch (CompletionException cex)
        {
            throw new AppInitError("Initializer "+requester+" requested errored initializer "+this.id, cex);
        }
        if (value != sentinelIncomplete())
            return value;
        try
        {
            synchronized (AppInit.class)
            {
                String prevAwait = awaitChain.putIfAbsent(requester, this.id);
                if (prevAwait != null)
                {
                    awaitChain.put(requester, prevAwait);
                    throw new AppInitError("Initializer "+requester+" tried to await "+this.id+", but was already awaiting "+prevAwait);
                }
                for (String target = this.id, cycle = target; (target = awaitChain.get(target)) != null; cycle = cycle + " -> " + target)
                    if (Objects.equals(requester, target))
                        throw new AppInitError("Initializer "+requester+" tried to await "+this.id+", causing deadlock: [ "+cycle+" -> "+target+" ]");
                
            }
            return this.future.get();
        }
        catch (InterruptedException iex)
        {
            throw new AppInitError("Initializer "+requester+" interrupted whilst awaiting initializer "+this.id, iex);
        }
        catch (CancellationException cex)
        {
            throw new AppInitError("Initializer "+requester+" awaited canceled initializer "+this.id, cex);
        }
        catch (ExecutionException eex)
        {
            throw new AppInitError("Initializer "+requester+" awaited errored initializer "+this.id, eex);
        }
        finally
        {
            synchronized (AppInit.class)
            {
                awaitChain.remove(requester, this.id);
            }
        }
    }

}
