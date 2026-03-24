package net.sybyline.scarlet;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonParseException;
import com.sun.nio.file.SensitivityWatchEventModifier;

import io.github.vrchatapi.JSON;

import net.sybyline.scarlet.ext.VrcAppData;
import net.sybyline.scarlet.util.EventSchemas;
import net.sybyline.scarlet.util.EventSchemas.AmplitudeCache;
import net.sybyline.scarlet.util.MiscUtils;

public class ScarletVRChatAmplitude implements Closeable
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/VRChat/Amplitude");

    public interface Listener
    {
        void amplitude(AmplitudeCache amplitude);
    }

    public ScarletVRChatAmplitude(Listener listener)
    {
        this.temp = VrcAppData.TEMP.toPath();
        this.listener = listener;
        this.thread = new AtomicReference<>();
        this.running = true;
        Thread t = new Thread(this::scan, "Scarlet VRChat Amplitude");
        t.setDaemon(true);
        t.start();
    }

    private final Path temp;
    private final Listener listener;
    private final AtomicReference<Thread> thread;
    public volatile boolean running;

    public void scan()
    {
        this.run(true);
    }
    public void run(boolean watch)
    {
        if (!this.thread.compareAndSet(null, Thread.currentThread()))
            throw new IllegalStateException();
        try
        {
            if (watch)
                this.runWatch();
            else
                this.runLoop();
        }
        finally
        {
            this.thread.compareAndSet(Thread.currentThread(), null);
        }
    }
    private boolean pollRunning()
    {
        return this.running && !Thread.interrupted();
    }
    private void runLoop()
    {
        Path abs = this.temp.resolve("amplitude.cache");
        long lastSize = -1L,
             lastModified = -1L;
        while (this.pollRunning() && MiscUtils.sleep(100L)) try
        {
            if (!Files.exists(abs))
                continue;
            long modified = Files.getLastModifiedTime(abs).toMillis();
            if (modified == lastModified)
                continue;
            long size = Files.size(abs);
            if (size != lastSize && size != 0L)
            {
                this.event(abs);
            }
            lastSize = size;
        }
        catch (IOException ioex)
        {
            LOG.warn("Exception in loop", ioex);
        }
    }
    private void runWatch()
    {
        try (WatchService watch = this.temp.getFileSystem().newWatchService())
        {
            @SuppressWarnings("unused")
            WatchKey wk, wk0 = this.temp.register(watch, new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_MODIFY}, SensitivityWatchEventModifier.HIGH);
            long lastSize = 2L;
            while (this.pollRunning())
            {
                wk = watch.take();
                List<WatchEvent<?>> evts = wk.pollEvents();
                wk.reset();
                for (WatchEvent<?> evt : evts)
                {
                    Path target = (Path)evt.context();
                    Path abs = this.temp.resolve(target);
                    if (!Files.exists(abs))
                        continue;
                    if (!"amplitude.cache".equals(target.getFileName().toString()))
                        continue;
                    try
                    {
                        long size = Files.size(abs);
                        if (size != lastSize && size != 0L)
                        {
                            this.event(abs);
                        }
                        lastSize = size;
                    }
                    catch (IOException ioex)
                    {
                        LOG.warn("Exception in watch size", ioex);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            if (ex instanceof InterruptedException)
                LOG.info("Watch ending");
            else
                LOG.warn("Exception in watch", ex);
        }
    }

    protected int[] prev_event_ids = new int[0];
    public void event(Path abs)
    {
        EventSchemas.AmplitudeCache[] amplitudes;
        try (BufferedReader read = Files.newBufferedReader(abs, StandardCharsets.UTF_8))
        {
            amplitudes = JSON.getGson().fromJson(read, EventSchemas.AmplitudeCache[].class);
        }
        catch (JsonParseException|IOException ex)
        {
            LOG.warn("Exception in event", ex);
            return;
        }
        if (amplitudes != null)
        try
        {
            Arrays.sort(amplitudes, Comparator.comparingInt($ -> $.event_id));
            for (EventSchemas.AmplitudeCache amplitude : amplitudes)
            {
                if (MiscUtils.indexOf(amplitude.event_id, this.prev_event_ids) > -1)
                {
                    this.listener.amplitude(amplitude);
                }
            }
        }
        finally
        {
            this.prev_event_ids = Stream.of(amplitudes).mapToInt($ -> $.event_id).toArray();
        }
    }

    @Override
    public void close()
    {
        this.running = false;
        Thread thread = this.thread.get();
        if (thread != null)
        {
            thread.interrupt();
            try
            {
                thread.join(3_000L);
            }
            catch (InterruptedException iex)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

}
