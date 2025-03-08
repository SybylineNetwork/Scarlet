package net.sybyline.scarlet.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

public class LRUMap<K, V> extends LinkedHashMap<K, V>
{

    private static final long serialVersionUID = 3497444422181844687L;

    public static final int DEFAULT_MAX_ENTRIES = 1024;

    private static final BiConsumer<?, ?> NOOP = (k, v) -> {};

    public static <K, V> LRUMap<K, V> of()
    {
        return new LRUMap<>();
    }
    public static <K, V> LRUMap<K, V> of(int maxEntries)
    {
        return new LRUMap<>(maxEntries);
    }
    public static <K, V> LRUMap<K, V> of(BiConsumer<K, V> onRemoveEldest)
    {
        return new LRUMap<>(onRemoveEldest);
    }
    public static <K, V> LRUMap<K, V> of(int maxEntries, BiConsumer<K, V> onRemoveEldest)
    {
        return new LRUMap<>(maxEntries, onRemoveEldest);
    }

    public static <K, V> Map<K, V> ofSynchronized()
    {
        return Collections.synchronizedMap(of());
    }
    public static <K, V> Map<K, V> ofSynchronized(int maxEntries)
    {
        return Collections.synchronizedMap(of(maxEntries));
    }
    public static <K, V> Map<K, V> ofSynchronized(BiConsumer<K, V> onRemoveEldest)
    {
        return Collections.synchronizedMap(of(onRemoveEldest));
    }
    public static <K, V> Map<K, V> ofSynchronized(int maxEntries, BiConsumer<K, V> onRemoveEldest)
    {
        return Collections.synchronizedMap(of(maxEntries, onRemoveEldest));
    }

    protected LRUMap()
    {
        this(DEFAULT_MAX_ENTRIES, null);
    }

    protected LRUMap(int maxEntries)
    {
        this(maxEntries, null);
    }

    protected LRUMap(BiConsumer<K, V> onRemoveEldest)
    {
        this(DEFAULT_MAX_ENTRIES, onRemoveEldest);
    }

    @SuppressWarnings("unchecked")
    protected LRUMap(int maxEntries, BiConsumer<K, V> onRemoveEldest)
    {
        super(maxEntries + 1, 0.75F, true);
        this.maxEntries = maxEntries;
        this.onRemoveEldest = onRemoveEldest == null ? (BiConsumer<K, V>)NOOP : onRemoveEldest;
        if (maxEntries < 1)
            throw new IllegalArgumentException("maxEntries < 1");
    }

    protected final int maxEntries;
    protected final BiConsumer<K, V> onRemoveEldest;
    
    @Override
    protected boolean removeEldestEntry(Entry<K, V> eldest)
    {
        boolean ret = this.size() > this.maxEntries;
        if (ret) this.onRemoveEldest.accept(eldest.getKey(), eldest.getValue());
        return ret;
    }

}
