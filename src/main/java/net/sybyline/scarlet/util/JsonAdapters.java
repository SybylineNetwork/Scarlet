package net.sybyline.scarlet.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.ParsePosition;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TimeZone;
import java.util.UUID;

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

    public static <T> T copy(Gson gson, T value)
    {
        if (value == null)
            return null;
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>)value.getClass();
        return gson.fromJson(gson.toJson(value, type), type);
    }

    public static GsonBuilder gson()
    {
        return new GsonBuilder()
    // java.sql
        .registerTypeAdapter(java.sql.Date.class, new ByString<>(JsonAdapters::sqlDate2json, JsonAdapters::json2sqlDate))
        .registerTypeAdapter(java.sql.Time.class, new ByString<>(JsonAdapters::sqlTime2json, JsonAdapters::json2sqlTime))
        .registerTypeAdapter(java.sql.Timestamp.class, new ByString<>(JsonAdapters::sqlTimestamp2json, JsonAdapters::json2sqlTimestamp))
    // java.util
        .registerTypeAdapter(TimeZone.class, new ByString<>(JsonAdapters::timeZone2json, JsonAdapters::json2timeZone))
        .registerTypeAdapter(Date.class, new ByString<>(JsonAdapters::date2json, JsonAdapters::json2date))
        .registerTypeAdapter(UUID.class, new ByString<>(JsonAdapters::uuid2json, JsonAdapters::json2uuid))
        .registerTypeAdapter(OptionalInt.class, new OptionalIntAdapter())
        .registerTypeAdapter(OptionalLong.class, new OptionalLongAdapter())
        .registerTypeAdapter(OptionalDouble.class, new OptionalDoubleAdapter())
        .registerTypeAdapterFactory(new OptionalAdapter.Factory())
    // java.net
        .registerTypeAdapter(URL.class, new ByString<>(JsonAdapters::url2json, JsonAdapters::json2url))
        .registerTypeAdapter(URI.class, new ByString<>(JsonAdapters::uri2json, JsonAdapters::json2uri))
        .registerTypeAdapter(InetAddress.class, new ByString<>(JsonAdapters::inetAddress2json, JsonAdapters::json2inetAddress))
        .registerTypeAdapter(InetSocketAddress.class, new ByString<>(JsonAdapters::inetSocketAddress2json, JsonAdapters::json2inetSocketAddress))
    // java.time
        .registerTypeAdapter(ZonedDateTime.class, new ByString<>(JsonAdapters::zonedDateTime2json, JsonAdapters::json2zonedDateTime))
        .registerTypeAdapter(OffsetDateTime.class, new ByString<>(JsonAdapters::offsetDateTime2json, JsonAdapters::json2offsetDateTime))
        .registerTypeAdapter(OffsetTime.class, new ByString<>(JsonAdapters::offsetTime2json, JsonAdapters::json2offsetTime))
        .registerTypeAdapter(LocalDateTime.class, new ByString<>(JsonAdapters::localDateTime2json, JsonAdapters::json2localDateTime))
        .registerTypeAdapter(LocalDate.class, new ByString<>(JsonAdapters::localDate2json, JsonAdapters::json2localDate))
        .registerTypeAdapter(LocalTime.class, new ByString<>(JsonAdapters::localTime2json, JsonAdapters::json2localTime))
        .registerTypeAdapter(Instant.class, new ByString<>(JsonAdapters::instant2json, JsonAdapters::json2instant))
        .registerTypeAdapter(Year.class, new ByString<>(JsonAdapters::year2json, JsonAdapters::json2year))
        .registerTypeAdapter(YearMonth.class, new ByString<>(JsonAdapters::yearMonth2json, JsonAdapters::json2yearMonth))
        .registerTypeAdapter(Period.class, new ByString<>(JsonAdapters::period2json, JsonAdapters::json2period))
        .registerTypeAdapter(Duration.class, new ByString<>(JsonAdapters::duration2json, JsonAdapters::json2duration))        
        .registerTypeAdapter(ZoneId.class, new ByString<>(JsonAdapters::zoneId2json, JsonAdapters::json2zoneId))        
        .registerTypeAdapter(ZoneOffset.class, new ByString<>(JsonAdapters::zoneOffset2json, JsonAdapters::json2zoneOffset))   
    // misc
//        .registerTypeAdapter(byte[].class, new UnsignedByteArrayAdapter())
//        .registerTypeAdapter(byte[].class, new Base64ByteArrayAdapter())
        .registerTypeAdapter(byte[].class, new ByString<>(JsonAdapters::bytes2json, JsonAdapters::json2bytes))
        .registerTypeAdapter(UniqueStrings.class, new UniqueStrings.Adapter())
        .registerTypeAdapter(Void.class, new VoidAdapter())
        .disableHtmlEscaping();
    }

    public static @FunctionalInterface interface Discovery
    {
        <T> TypeAdapter<T> discover(Gson gson, TypeToken<T> type);
    }
    public static class DiscoveringFactory implements TypeAdapterFactory
    {
        public DiscoveringFactory(Discovery discovery)
        {
            if (discovery == null)
                throw new IllegalArgumentException("discovery == null");
            this.discovery = discovery;
        }
        final Discovery discovery;
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
        {
            TypeAdapter<T> discovered = this.discovery.discover(gson, type);
            if (discovered != null)
                return discovered;
            return gson.getDelegateAdapter(this, type);
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

        public static class Base64ByteArrayAdapter extends TypeAdapter<byte[]>
        {
            public Base64ByteArrayAdapter()
            {
            }
            @Override
            public void write(JsonWriter out, byte[] value) throws IOException
            {
                if (value == null)
                {
                    out.nullValue();
                    return;
                }
                out.value(Base64.getEncoder().encodeToString(value));
            }
            @Override
            public byte[] read(JsonReader in) throws IOException
            {
                if (in.peek() == JsonToken.NULL)
                {
                    in.nextNull();
                    return null;
                }
                return Base64.getDecoder().decode(in.nextString());
            }
        }

        public static class Base64UrlByteArrayAdapter extends TypeAdapter<byte[]>
        {
            public Base64UrlByteArrayAdapter()
            {
            }
            @Override
            public void write(JsonWriter out, byte[] value) throws IOException
            {
                if (value == null)
                {
                    out.nullValue();
                    return;
                }
                out.value(Base64.getUrlEncoder().encodeToString(value));
            }
            @Override
            public byte[] read(JsonReader in) throws IOException
            {
                if (in.peek() == JsonToken.NULL)
                {
                    in.nextNull();
                    return null;
                }
                return Base64.getUrlDecoder().decode(in.nextString());
            }
        }

        public static class Base64MimeByteArrayAdapter extends TypeAdapter<byte[]>
        {
            public Base64MimeByteArrayAdapter()
            {
            }
            @Override
            public void write(JsonWriter out, byte[] value) throws IOException
            {
                if (value == null)
                {
                    out.nullValue();
                    return;
                }
                out.value(Base64.getMimeEncoder().encodeToString(value));
            }
            @Override
            public byte[] read(JsonReader in) throws IOException
            {
                if (in.peek() == JsonToken.NULL)
                {
                    in.nextNull();
                    return null;
                }
                return Base64.getMimeDecoder().decode(in.nextString());
            }
        }

        public static class UnsignedByteArrayAdapter extends TypeAdapter<byte[]>
        {
            public UnsignedByteArrayAdapter()
            {
            }
            @Override
            public void write(JsonWriter out, byte[] value) throws IOException
            {
                if (value == null)
                {
                    out.nullValue();
                    return;
                }
                out.beginArray();
                for (int idx = 0, len = value.length; idx < len; idx++)
                {
                    out.value(value[idx] & 0xFFL);
                }
                out.endArray();
            }
            @Override
            public byte[] read(JsonReader in) throws IOException
            {
                if (in.peek() == JsonToken.NULL)
                {
                    in.nextNull();
                    return null;
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                in.beginArray();
                while (in.peek() != JsonToken.END_ARRAY)
                {
                    bytes.write(in.nextInt());
                }
                in.endArray();
                return bytes.toByteArray();
            }
        }
    }
    public static class ByString<T> extends TypeAdapter<T>
    {
        public ByString(Func.F1<Exception, String, T> writer, Func.F1<Exception, T, String> reader)
        {
            this.writer = writer;
            this.reader = reader;
        }
        final Func.F1<Exception, String, T> writer;
        final Func.F1<Exception, T, String> reader;
        @Override
        public void write(JsonWriter out, T value) throws IOException
        {
            try
            {
                out.value(value == null ? null : this.writer.invoke(value));
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
        @Override
        public T read(JsonReader in) throws IOException
        {
            try
            {
                if (in.peek() != JsonToken.NULL)
                    return this.reader.invoke(in.nextString());
                in.nextNull();
                return this.reader.invoke(null);
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
    }

    public static class ByInt<T> extends TypeAdapter<T>
    {
        public ByInt(Func.F1<IOException, Integer, T> writer, Func.F1<IOException, T, Integer> reader)
        {
            this.writer = writer;
            this.reader = reader;
        }
        final Func.F1<IOException, Integer, T> writer;
        final Func.F1<IOException, T, Integer> reader;
        @Override
        public void write(JsonWriter out, T value) throws IOException
        {
            try
            {
                out.value(value == null ? null : this.writer.invoke(value));
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
        @Override
        public T read(JsonReader in) throws IOException
        {
            try
            {
                if (in.peek() != JsonToken.NULL)
                    return this.reader.invoke(in.nextInt());
                in.nextNull();
                return this.reader.invoke(null);
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
    }

    public static class ByLong<T> extends TypeAdapter<T>
    {
        public ByLong(Func.F1<IOException, Long, T> writer, Func.F1<IOException, T, Long> reader)
        {
            this.writer = writer;
            this.reader = reader;
        }
        final Func.F1<IOException, Long, T> writer;
        final Func.F1<IOException, T, Long> reader;
        @Override
        public void write(JsonWriter out, T value) throws IOException
        {
            try
            {
                out.value(value == null ? null : this.writer.invoke(value));
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
        @Override
        public T read(JsonReader in) throws IOException
        {
            try
            {
                if (in.peek() != JsonToken.NULL)
                    return this.reader.invoke(in.nextLong());
                in.nextNull();
                return this.reader.invoke(null);
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
    }

    public static class ByDouble<T> extends TypeAdapter<T>
    {
        public ByDouble(Func.F1<IOException, Double, T> writer, Func.F1<IOException, T, Double> reader)
        {
            this.writer = writer;
            this.reader = reader;
        }
        final Func.F1<IOException, Double, T> writer;
        final Func.F1<IOException, T, Double> reader;
        @Override
        public void write(JsonWriter out, T value) throws IOException
        {
            try
            {
                out.value(value == null ? null : this.writer.invoke(value));
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
        @Override
        public T read(JsonReader in) throws IOException
        {
            try
            {
                if (in.peek() != JsonToken.NULL)
                    return this.reader.invoke(in.nextDouble());
                in.nextNull();
                return this.reader.invoke(null);
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
    }

    public static class ByBoolean<T> extends TypeAdapter<T>
    {
        public ByBoolean(Func.F1<IOException, Boolean, T> writer, Func.F1<IOException, T, Boolean> reader)
        {
            this.writer = writer;
            this.reader = reader;
        }
        final Func.F1<IOException, Boolean, T> writer;
        final Func.F1<IOException, T, Boolean> reader;
        @Override
        public void write(JsonWriter out, T value) throws IOException
        {
            try
            {
                out.value(value == null ? null : this.writer.invoke(value));
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
        @Override
        public T read(JsonReader in) throws IOException
        {
            try
            {
                if (in.peek() != JsonToken.NULL)
                    return this.reader.invoke(in.nextBoolean());
                in.nextNull();
                return this.reader.invoke(null);
            }
            catch (Exception ex)
            {
                throw ex instanceof IOException ? (IOException)ex : new IOException(ex);
            }
        }
    }

    public static class OptionalAdapter<T> extends TypeAdapter<Optional<T>>
    {
        public static class Factory implements TypeAdapterFactory
        {
            public Factory()
            {
            }
            @SuppressWarnings("unchecked")
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
            {
                if (Optional.class.equals(type.getRawType()))
                {
                    Type innerType = type.getType() instanceof ParameterizedType
                        ? ((ParameterizedType)type.getType()).getActualTypeArguments()[0]
                        : Object.class;
                    TypeAdapter<?> delegate = gson.getAdapter(TypeToken.get(innerType));
                    return (TypeAdapter<T>)new OptionalAdapter<>(delegate);
                }
                return null;
            }
        }
        public OptionalAdapter(TypeAdapter<T> delegate)
        {
            this.delegate = delegate;
        }
        final TypeAdapter<T> delegate;
        @Override
        public void write(JsonWriter out, Optional<T> value) throws IOException
        {
            if (value != null && value.isPresent())
                this.delegate.write(out, value.get());
            else
                out.nullValue();
        }
        @Override
        public Optional<T> read(JsonReader in) throws IOException
        {
            if (in.peek() != JsonToken.NULL)
                return Optional.of(this.delegate.read(in));
            in.nextNull();
            return Optional.empty();
        }
    }
    public static class OptionalIntAdapter extends TypeAdapter<OptionalInt>
    {
        public OptionalIntAdapter()
        {
        }
        @Override
        public void write(JsonWriter out, OptionalInt value) throws IOException
        {
            if (value != null && value.isPresent())
                out.value(value.getAsInt());
            else
                out.nullValue();
        }
        @Override
        public OptionalInt read(JsonReader in) throws IOException
        {
            if (in.peek() != JsonToken.NULL)
                return OptionalInt.of(in.nextInt());
            in.nextNull();
            return OptionalInt.empty();
        }
    }
    public static class OptionalLongAdapter extends TypeAdapter<OptionalLong>
    {
        public OptionalLongAdapter()
        {
        }
        @Override
        public void write(JsonWriter out, OptionalLong value) throws IOException
        {
            if (value != null && value.isPresent())
                out.value(value.getAsLong());
            else
                out.nullValue();
        }
        @Override
        public OptionalLong read(JsonReader in) throws IOException
        {
            if (in.peek() != JsonToken.NULL)
                return OptionalLong.of(in.nextLong());
            in.nextNull();
            return OptionalLong.empty();
        }
    }
    public static class OptionalDoubleAdapter extends TypeAdapter<OptionalDouble>
    {
        public OptionalDoubleAdapter()
        {
        }
        @Override
        public void write(JsonWriter out, OptionalDouble value) throws IOException
        {
            if (value != null && value.isPresent())
                out.value(value.getAsDouble());
            else
                out.nullValue();
        }
        @Override
        public OptionalDouble read(JsonReader in) throws IOException
        {
            if (in.peek() != JsonToken.NULL)
                return OptionalDouble.of(in.nextDouble());
            in.nextNull();
            return OptionalDouble.empty();
        }
    }
    public static class VoidAdapter extends TypeAdapter<Void>
    {
        public VoidAdapter()
        {
        }
        @Override
        public void write(JsonWriter out, Void value) throws IOException
        {
            out.nullValue();
        }
        @Override
        public Void read(JsonReader in) throws IOException
        {
            in.skipValue();
            return null;
        }
    }

    // java.sql

    public static String sqlDate2json(java.sql.Date val)
    {
        return val == null ? null : val.toString();
    }
    public static java.sql.Date json2sqlDate(String val)
    {
//        return val == null ? null : java.sql.Date.valueOf(val);
        try
        {
            return new java.sql.Date(ISO8601Utils.parse(val, new ParsePosition(0)).getTime());
        }
        catch (ParseException e)
        {
            throw new JsonParseException(e);
        }
    }

    public static String sqlTime2json(java.sql.Time val)
    {
        return val == null ? null : val.toString();
    }
    public static java.sql.Time json2sqlTime(String val)
    {
        return val == null ? null : java.sql.Time.valueOf(val);
    }

    public static String sqlTimestamp2json(java.sql.Timestamp val)
    {
        return val == null ? null : val.toString();
    }
    public static java.sql.Timestamp json2sqlTimestamp(String val)
    {
        return val == null ? null : java.sql.Timestamp.valueOf(val);
    }

    // java.util

    public static String timeZone2json(TimeZone val)
    {
        return val == null ? null : val.getID();
    }
    public static TimeZone json2timeZone(String val)
    {
        return val == null ? null : TimeZone.getTimeZone(val);
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

    public static String uuid2json(UUID val)
    {
        return val == null ? null : val.toString();
    }
    public static UUID json2uuid(String val)
    {
        return val == null ? null : UUID.fromString(val);
    }

    // java.net

    public static String url2json(URL val)
    {
        return val == null ? null : val.toString();
    }
    public static URL json2url(String val) throws MalformedURLException
    {
        return val == null ? null : new URL(val);
    }

    public static String uri2json(URI val)
    {
        return val == null ? null : val.toString();
    }
    public static URI json2uri(String val) throws URISyntaxException
    {
        return val == null ? null : new URI(val);
    }

    public static String inetAddress2json(InetAddress val)
    {
        return val == null ? null : val.getHostName();
    }
    public static InetAddress json2inetAddress(String val) throws UnknownHostException
    {
        return val == null ? null : InetAddress.getByName(val);
    }

    public static String inetSocketAddress2json(InetSocketAddress val)
    {
        return val == null ? null : val.getHostString()+":"+Integer.toUnsignedString(val.getPort());
    }
    public static InetSocketAddress json2inetSocketAddress(String val)
    {
        if (val == null) return null;
        int colon = val.lastIndexOf(':'),
            port = colon < 0 ? 0 : Integer.parseUnsignedInt(val.substring(colon + 1));
        return InetSocketAddress.createUnresolved(colon < 0 ? val : val.substring(0, colon), port);
    }

    // java.time

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
    public static LocalDateTime json2localDateTime(String val)
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

    public static final DateTimeFormatter YEAR_PARSER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .toFormatter();
    public static String year2json(Year val)
    {
        return val == null ? null : YEAR_PARSER.format(val);
    }
    public static Year json2year(String val)
    {
        return val == null ? null : Year.parse(val);
    }

    public static final DateTimeFormatter YEAR_MONTH_PARSER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .toFormatter();
    public static String yearMonth2json(YearMonth val)
    {
        return val == null ? null : YEAR_MONTH_PARSER.format(val);
    }
    public static YearMonth json2yearMonth(String val)
    {
        return val == null ? null : YearMonth.parse(val);
    }

    public static String period2json(Period val)
    {
        return val == null ? null : val.toString();
    }
    public static Period json2period(String val)
    {
        return val == null ? null : Period.parse(val);
    }

    public static String duration2json(Duration val)
    {
        return val == null ? null : val.toString();
    }
    public static Duration json2duration(String val)
    {
        return val == null ? null : Duration.parse(val);
    }

    public static String zoneId2json(ZoneId val)
    {
        return val == null ? null : val.toString();
    }
    public static ZoneId json2zoneId(String val)
    {
        return val == null ? null : ZoneId.of(val);
    }

    public static String zoneOffset2json(ZoneOffset val)
    {
        return val == null ? null : val.toString();
    }
    public static ZoneOffset json2zoneOffset(String val)
    {
        return val == null ? null : ZoneOffset.of(val);
    }

    // misc

    public static String bytes2json(byte[] val)
    {
        return val == null ? null : Base64.getEncoder().encodeToString(val);
    }
    public static byte[] json2bytes(String val)
    {
        return val == null ? null : Base64.getDecoder().decode(val);
    }

}
