package net.sybyline.scarlet;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import net.sybyline.scarlet.util.LRUMap;
import net.sybyline.scarlet.util.MiscUtils;

public class ScarletJsonCache<T>
{

    public @FunctionalInterface interface Populator<T>
    {
        T populate(String id) throws Exception;
        static <T> Populator<T> of(Callable<T> callable)
        {
            return callable == null ? null : $ -> callable.call();
        }
    }

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/JsonCache");

    static final File caches = new File(Scarlet.dir, "caches");

    public ScarletJsonCache(String kind, Class<T> type)
    {
        this(kind, TypeToken.get(type));
    }

    public ScarletJsonCache(String kind, TypeToken<T> type)
    {
        this.kind = kind;
        this.type = type;
        this.dir = new File(caches, kind);
        this.entries = LRUMap.ofSynchronized();
        this.known404s = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.load404s();
    }

    private final String kind;
    private final TypeToken<T> type;
    private final File dir;
    private final Map<String, Entry> entries;
    private final Set<String> known404s;

    public boolean is404(String id)
    {
        return id == null || this.known404s.contains(id);
    }

    public boolean add404(String id)
    {
        if (id == null || !this.known404s.add(id))
            return false;
        this.save404s();
        return true;
    }

    public boolean remove404(String id)
    {
        if (id == null || !this.known404s.remove(id))
            return false;
        this.save404s();
        return true;
    }

    void load404s()
    {
        File known404sf = new File(this.dir, "known_404s.json");
        if (!known404sf.isFile())
            return;
        String[] known404sa;
        try (Reader reader = MiscUtils.reader(known404sf))
        {
            known404sa = Scarlet.GSON_PRETTY.fromJson(reader, String[].class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception reading knows 404s JSON of kind `"+this.kind+"`");
            return;
        }
        if (known404sa != null)
            this.known404s.addAll(Arrays.asList(known404sa));
    }

    void save404s()
    {
        try (Writer writer = MiscUtils.writer(new File(this.dir, "known_404s.json")))
        {
            Scarlet.GSON_PRETTY.toJson(this.known404s, Set.class, writer);
        }
        catch (Exception ex)
        {
            LOG.error("Exception writing knows 404s JSON of kind `"+this.kind+"`");
        }
    }

    public T get(String id)
    {
        return this.get(id, Long.MIN_VALUE, null);
    }
    public T get(String id, long minEpoch)
    {
        return this.get(id, minEpoch, null);
    }
    public T getFrom(String id, long minEpoch, Callable<T> populate)
    {
        return this.get(id, minEpoch, Populator.of(populate));
    }
    public T get(String id, long minEpoch, Populator<T> populate)
    {
        Entry entry = this.getEntry(id, minEpoch, populate);
        return entry == null ? null : entry.value;
    }

    public Entry getEntry(String id)
    {
        return this.getEntry(id, Long.MIN_VALUE, null);
    }
    public Entry getEntry(String id, long minEpoch)
    {
        return this.getEntry(id, minEpoch, null);
    }
    public Entry getEntryFrom(String id, long minEpoch, Callable<T> populate)
    {
        return this.getEntry(id, minEpoch, Populator.of(populate));
    }
    public Entry getEntry(String id, long minEpoch, Populator<T> populate)
    {
        if (id == null)
            return null;
        return this.entries.compute(id, (id0, entry) ->
        {
            if (entry == null)
            {
                entry = this.loadEntry(id0, minEpoch);
            }
            else if (entry.modified >= minEpoch)
            {
                return entry;
            }
            if (populate != null) try
            {
                T value = populate.populate(id0);
                if (value != null)
                {
                    if (entry == null)
                    {
                        entry = new Entry(id0, value, 0L);
                    }
                    else
                    {
                        entry.value = value;
                    }
                    entry.save();
                }
            }
            catch (Exception ex)
            {
                LOG.error("Exception populating JSON cache of kind `"+this.kind+"`, id `"+id0+"`");
                
            }
            return entry;
        });
    }

    public void put(String id, T value)
    {
        if (id == null)
            return;
        if (value == null)
            return;
        Entry entry = this.entries.computeIfAbsent(id, Entry::new);
        entry.value = value;
        entry.save();
    }

    private Entry loadEntry(String id, long minEpoch)
    {
        if (id == null)
            return null;
        File file = new File(this.dir, id+".json");
        if (!file.isFile())
            return null;
        T value;
        long modified;
        try
        {
            modified = MiscUtils.lastModified(file);
            if (modified < minEpoch)
                return null;
            try (Reader reader = MiscUtils.reader(file))
            {
                value = Scarlet.GSON_PRETTY.fromJson(reader, this.type);
            }
        }
        catch (Exception ex)
        {
            LOG.error("Exception reading cached JSON of kind `"+this.kind+"`, id `"+id+"`");
            return null;
        }
        return new Entry(id, value, modified);
    }

    private void saveEntry(Entry entry)
    {
        if (entry == null)
            return;
        File file = new File(this.dir, entry.id+".json");
        if (!this.dir.isDirectory())
            this.dir.mkdirs();
        try
        {
            try (Writer writer = MiscUtils.writer(file))
            {
                Scarlet.GSON_PRETTY.toJson(entry.value, this.type.getType(), writer);
            }
            entry.modified = MiscUtils.lastModified(file);
        }
        catch (Exception ex)
        {
            LOG.error("Exception writing cached JSON of kind `"+this.kind+"`, id `"+entry.id+"`");
        }
    }

    public class Entry
    {
        Entry(String id)
        {
            this(id, null, 0L);
        }
        Entry(String id, T value, long modified)
        {
            this.id = id;
            this.value = value;
            this.modified = modified;
        }
        private final String id;
        private T value;
        private long modified;
        public String id()
        {
            return this.id;
        }
        public T value()
        {
            return this.value;
        }
        public long modified()
        {
            return this.modified;
        }
        public void save()
        {
            ScarletJsonCache.this.saveEntry(this);
        }
    }

}
