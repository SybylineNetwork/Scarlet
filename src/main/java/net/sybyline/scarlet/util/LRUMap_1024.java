package net.sybyline.scarlet.util;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

public class LRUMap_1024<K, V> extends LinkedHashMap<K, V>
{

    private static final long serialVersionUID = 7362354054187517097L;

    public static final int MAX_ENTRIES = 1024;

    public LRUMap_1024()
    {
        super(MAX_ENTRIES + 1, 0.75F, true);
    }
    
    @Override
    protected boolean removeEldestEntry(Entry<K, V> eldest)
    {
        return this.size() > MAX_ENTRIES;
    }
    
}
