package net.sybyline.scarlet.util;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public @FunctionalInterface interface ChangeListener<T>
{

    static <T> ChangeListener<T> wrap(Consumer<T> listener)
    {
        return (previous, next, valid, source) -> { if (valid) listener.accept(next); };
    }

    static <T> ChangeListener<T> wrap(BiConsumer<T, String> listener)
    {
        return (previous, next, valid, source) -> { if (valid) listener.accept(next, source); };
    }

    void onMaybeChange(T previous, T next, boolean valid, String source);

    static <T> ListenerList<T> newListenerList()
    {
        return new ListenerList<>();
    }

    class ListenerList<T> implements ChangeListener<T>
    {
        ListenerList()
        {
        }
        final Set<ChangeListenerHolder<T>> listeners = new ConcurrentSkipListSet<>();
        public boolean register(String source, int priority, boolean listensSelf, ChangeListener<T> listener)
        {
            return this.listeners.add(new ChangeListenerHolder<>(source, priority, listensSelf, listener));
        }
        public boolean unregister(String source)
        {
            return this.listeners.removeIf($ -> $.source.equals(source));
        }
        @Override
        public void onMaybeChange(T previous, T next, boolean valid, String source)
        {
            if (Objects.equals(previous, next))
                return;
            for (ChangeListenerHolder<T> holder : this.listeners)
                if (holder.listensSelf || !holder.source.equals(source))
                    holder.listener.onMaybeChange(previous, next, valid, source);
        }
        
    }

}

class ChangeListenerHolder<T> implements Comparable<ChangeListenerHolder<T>>
{
    ChangeListenerHolder(String source, int priority, boolean listensSelf, ChangeListener<T> listener)
    {
        this.source = source;
        this.priority = priority;
        this.listensSelf = listensSelf;
        this.listener = listener;
    }
    final String source;
    final int priority;
    final boolean listensSelf;
    final ChangeListener<T> listener;
    @Override
    public int compareTo(ChangeListenerHolder<T> other)
    {
        return Integer.compare(this.priority, other.priority);
    }
    @Override
    public int hashCode()
    {
        return this.source.hashCode();
    }
    @Override
    public boolean equals(Object obj)
    {
        return this == obj || (obj instanceof ChangeListenerHolder && this.source.equals(((ChangeListenerHolder<?>)obj).source));
    }
}
