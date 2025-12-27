package net.sybyline.scarlet.util;

public class ThreadLocalSwap<T> extends ThreadLocal<T>
{

    public static <T> ThreadLocalSwap<T> of()
    {
        return of(null);
    }

    public static <T> ThreadLocalSwap<T> of(T defaultInitialValue)
    {
        return new ThreadLocalSwap<T>(defaultInitialValue);
    }

    protected ThreadLocalSwap(T defaultInitialValue)
    {
        super();
        this.defaultInitialValue = defaultInitialValue;
    }

    private final T defaultInitialValue;

    @Override
    protected T initialValue()
    {
        return this.defaultInitialValue;
    }

    public T swap(T value)
    {
        try
        {
            return this.get();
        }
        finally
        {
            this.set(value);
        }
    }

    public Context<T> push(T value)
    {
        return new Context<>(this, value);
    }

    public static class Context<T> implements Resource
    {
        final ThreadLocal<T> parent;
        final T prev, value;
        public Context(ThreadLocal<T> parent, T value)
        {
            this.parent = parent;
            this.prev = parent.get();
            this.value = value;
            parent.set(value);
        }
        public T previous()
        {
            return this.prev;
        }
        public T value()
        {
            return this.value;
        }
        @Override
        public void close()
        {
            this.parent.set(this.prev);
        }
    }

}
