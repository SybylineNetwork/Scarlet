package net.sybyline.scarlet.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class LRUMap<K, V> extends LinkedHashMap<K, V>
{

    private static final long serialVersionUID = 3497444422181844687L;

    public static final int DEFAULT_MAX_ENTRIES = 1024;

    public static <K, V> LRUMap<K, V> of()
    {
        return new LRUMap<>();
    }
    public static <K, V> LRUMap<K, V> of(int maxEntries)
    {
        return new LRUMap<>(maxEntries);
    }

    public static <K, V> Map<K, V> ofSynchronized()
    {
        return Collections.synchronizedMap(of());
    }
    public static <K, V> Map<K, V> ofSynchronized(int maxEntries)
    {
        return Collections.synchronizedMap(of(maxEntries));
    }

    protected LRUMap()
    {
        this(DEFAULT_MAX_ENTRIES);
    }

    protected LRUMap(int maxEntries)
    {
        super(maxEntries + 1, 0.75F, true);
        this.maxEntries = maxEntries;
        if (maxEntries < 1)
            throw new IllegalArgumentException("maxEntries < 1");
    }

    protected final int maxEntries;
    
    @Override
    protected boolean removeEldestEntry(Entry<K, V> eldest)
    {
        return this.size() > this.maxEntries;
    }
    
}
