package net.sybyline.scarlet.util;

import java.io.IOException;
import java.text.ParseException;
import java.text.ParsePosition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.bind.util.ISO8601Utils;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public interface JsonAdapters
{

    public static GsonBuilder gson()
    {
        return new GsonBuilder()
        .registerTypeAdapter(Date.class, new ByString<>(JsonAdapters::date2json, JsonAdapters::json2date))
        .registerTypeAdapter(java.sql.Date.class, new ByString<>(JsonAdapters::sqlDate2json, JsonAdapters::json2sqlDate))
        .registerTypeAdapter(ZonedDateTime.class, new ByString<>(JsonAdapters::zonedDateTime2json, JsonAdapters::json2zonedDateTime))
        .registerTypeAdapter(OffsetDateTime.class, new ByString<>(JsonAdapters::offsetDateTime2json, JsonAdapters::json2offsetDateTime))
        .registerTypeAdapter(OffsetTime.class, new ByString<>(JsonAdapters::offsetTime2json, JsonAdapters::json2offsetTime))
        .registerTypeAdapter(LocalDateTime.class, new ByString<>(JsonAdapters::localDateTime2json, JsonAdapters::json2LocalDateTime))
        .registerTypeAdapter(LocalDate.class, new ByString<>(JsonAdapters::localDate2json, JsonAdapters::json2localDate))
        .registerTypeAdapter(LocalTime.class, new ByString<>(JsonAdapters::localTime2json, JsonAdapters::json2localTime))
        .registerTypeAdapter(Instant.class, new ByString<>(JsonAdapters::instant2json, JsonAdapters::json2instant))
        .registerTypeAdapter(byte[].class, new ByString<>(JsonAdapters::bytes2json, JsonAdapters::json2bytes))
        .registerTypeAdapter(UUID.class, new ByString<>(JsonAdapters::uuid2json, JsonAdapters::json2uuid))
        .disableHtmlEscaping();
    }

    public static class CatchingAdapter<T> extends TypeAdapter<T>
    {
        public static class Factory implements TypeAdapterFactory
        {
            public Factory()
            {
                this(Enum.class);
            }
            public Factory(Class<?> catchingClass)
            {
                this.catchingClass = catchingClass;
            }
            final Class<?> catchingClass;
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
            {
                TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
                if (this.catchingClass.isAssignableFrom(type.getRawType()))
                    return new CatchingAdapter<T>(delegate);
                return delegate;
            }
        }
        public CatchingAdapter(TypeAdapter<T> delegate)
        {
            this(delegate, null);
        }
        public CatchingAdapter(TypeAdapter<T> delegate, T fallback)
        {
            this.delegate = delegate;
            this.fallback = fallback;
        }
        protected final TypeAdapter<T> delegate;
        protected final T fallback;
        @Override
        public void write(JsonWriter out, T value) throws IOException
        {
            this.delegate.write(out, value);
        }
        @Override
        public T read(JsonReader in) throws IOException
        {
            try
            {
                return this.delegate.read(in);
            }
            catch (IOException ioex)
            {
                throw ioex;
            }
            catch (Exception ex)
            {
                return this.fallback;
            }
        }
        
    }

    public static class ByString<T> extends TypeAdapter<T>
    {
        public ByString(Function<T, String> writer, Function<String, T> reader)
        {
            this.writer = writer;
            this.reader = reader;
        }
        final Function<T, String> writer;
        final Function<String, T> reader;
        @Override
        public void write(JsonWriter out, T value) throws IOException
        {
            out.value(value == null ? null : this.writer.apply(value));
        }
        @Override
        public T read(JsonReader in) throws IOException
        {
            if (in.peek() != JsonToken.NULL)
                return this.reader.apply(in.nextString());
            in.nextNull();
            return this.reader.apply(null);
        }
    }

    public static String date2json(Date val)
    {
        return val == null ? null : ISO8601Utils.format(val, false);
    }
    public static Date json2date(String val)
    {
        try
        {
            return val == null ? null : ISO8601Utils.parse(val, new ParsePosition(0));
        }
        catch (ParseException e)
        {
            throw new JsonParseException(e);
        }
    }

    public static String sqlDate2json(java.sql.Date val)
    {
        return val == null ? null : val.toString();
    }
    public static java.sql.Date json2sqlDate(String val)
    {
        try
        {
            return new java.sql.Date(ISO8601Utils.parse(val, new ParsePosition(0)).getTime());
        }
        catch (ParseException e)
        {
            throw new JsonParseException(e);
        }
    }

    public static String zonedDateTime2json(ZonedDateTime val)
    {
        return val == null ? null : DateTimeFormatter.ISO_ZONED_DATE_TIME.format(val);
    }
    public static ZonedDateTime json2zonedDateTime(String val)
    {
        return val == null ? null : ZonedDateTime.parse(val.endsWith("+0000") ? val.substring(0, val.length()-5) + "Z" : val);
    }

    public static String offsetDateTime2json(OffsetDateTime val)
    {
        return val == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(val);
    }
    public static OffsetDateTime json2offsetDateTime(String val)
    {
        return val == null ? null : OffsetDateTime.parse(val.endsWith("+0000") ? val.substring(0, val.length()-5) + "Z" : val);
    }

    public static String offsetTime2json(OffsetTime val)
    {
        return val == null ? null : DateTimeFormatter.ISO_OFFSET_TIME.format(val);
    }
    public static OffsetTime json2offsetTime(String val)
    {
        return val == null ? null : OffsetTime.parse(val.endsWith("+0000") ? val.substring(0, val.length()-5) + "Z" : val);
    }

    public static String localDateTime2json(LocalDateTime val)
    {
        return val == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(val);
    }
    public static LocalDateTime json2LocalDateTime(String val)
    {
        return val == null ? null : LocalDateTime.parse(val);
    }

    public static String localDate2json(LocalDate val)
    {
        return val == null ? null : DateTimeFormatter.ISO_LOCAL_DATE.format(val);
    }
    public static LocalDate json2localDate(String val)
    {
        return val == null ? null : LocalDate.parse(val);
    }

    public static String localTime2json(LocalTime val)
    {
        return val == null ? null : DateTimeFormatter.ISO_LOCAL_TIME.format(val);
    }
    public static LocalTime json2localTime(String val)
    {
        return val == null ? null : LocalTime.parse(val);
    }

    public static String instant2json(Instant val)
    {
        return val == null ? null : DateTimeFormatter.ISO_INSTANT.format(val);
    }
    public static Instant json2instant(String val)
    {
        return val == null ? null : Instant.parse(val);
    }

    public static String bytes2json(byte[] val)
    {
        return val == null ? null : Base64.getEncoder().encodeToString(val);
    }
    public static byte[] json2bytes(String val)
    {
        return val == null ? null : Base64.getDecoder().decode(val);
    }

    public static String uuid2json(UUID val)
    {
        return val == null ? null : val.toString();
    }
    public static UUID json2uuid(String val)
    {
        return val == null ? null : UUID.fromString(val);
    }

}
