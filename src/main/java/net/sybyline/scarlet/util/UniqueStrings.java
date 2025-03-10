package net.sybyline.scarlet.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

@JsonAdapter(UniqueStrings.Adapter.class)
public final class UniqueStrings implements Iterable<String>
{

    public UniqueStrings()
    {
    }

    public UniqueStrings(String value)
    {
        this.add(value);
    }

    public UniqueStrings(String... values)
    {
        this.addAll(values);
    }

    public UniqueStrings(Collection<String> values)
    {
        this.addAll(values);
    }

    private final Set<String> strings = new LinkedHashSet<>(0);

    public Set<String> strings()
    {
        return this.strings;
    }

    public boolean isEmpty()
    {
        return this.strings.isEmpty();
    }

    public int size()
    {
        return this.strings.size();
    }

    public boolean add(String string)
    {
        return string != null && this.strings.add(string);
    }

    public boolean remove(String string)
    {
        return string != null && this.strings.remove(string);
    }

    public boolean addAll(String... strings)
    {
        if (strings == null)
            return false;
        return this.addAll(Arrays.asList(strings));
    }

    public boolean addAll(Collection<? extends String> strings)
    {
        if (strings == null)
            return false;
        if (!this.strings.addAll(strings))
            return false;
        this.strings.removeIf(Objects::isNull);
        return true;
    }

    public UniqueStrings clear()
    {
        this.strings.clear();
        return this;
    }

    public String[] toArray()
    {
        return this.strings.toArray(new String[this.strings.size()]);
    }

    public boolean contains(Object o)
    {
        return this.strings.contains(o);
    }

    public Stream<String> stream()
    {
        return this.strings.stream();
    }

    @Override
    public Iterator<String> iterator()
    {
        return this.strings.iterator();
    }

    @Override
    public void forEach(Consumer<? super String> action)
    {
        this.strings.forEach(action);
    }

    @Override
    public Spliterator<String> spliterator()
    {
        return this.strings.spliterator();
    }

    @Override
    public int hashCode()
    {
        return this.strings.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof UniqueStrings ? this.strings.equals(((UniqueStrings)obj).strings) : this.strings.equals(obj);
    }

    @Override
    public String toString()
    {
        return this.strings.toString();
    }

    public static final class Adapter extends TypeAdapter<UniqueStrings>
    {

        public Adapter()
        {
        }

        @Override
        public void write(JsonWriter out, UniqueStrings value) throws IOException
        {
            if (value == null || value.strings.isEmpty())
            {
                out.nullValue();
                return;
            }
            if (value.strings.size() == 1)
            {
                String one = value.strings.stream().findAny().orElse(null);
                if (one != null)
                {
                    out.value(one);
                    return;
                }
            }
            out.beginArray();
            for (String string : value.strings)
                out.value(string);
            out.endArray();
        }

        @Override
        public UniqueStrings read(JsonReader in) throws IOException
        {
            UniqueStrings ret = new UniqueStrings();
            this.read(ret, in);
            return ret;
        }

        private void read(UniqueStrings ret, JsonReader in) throws IOException
        {
            switch (in.peek())
            {
            case NULL:
                in.nextNull();
            break;
            case NUMBER:
            case STRING:
                ret.strings.add(in.nextString());
            break;
            case BEGIN_ARRAY:
                in.beginArray();
                while (in.peek() != JsonToken.END_ARRAY)
                    this.read(ret, in);
                in.endArray();
            break;
            case BOOLEAN:
                ret.strings.add(Boolean.toString(in.nextBoolean()));
            break;
            default:
                in.skipValue();
            break;
            }
        }

    }

}
