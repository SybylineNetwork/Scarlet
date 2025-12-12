package net.sybyline.scarlet.util;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAmount;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.vrchatapi.JSON;
import io.github.vrchatapi.model.FileVersion;
import io.github.vrchatapi.model.ModelFile;
import io.github.vrchatapi.model.User;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.sybyline.scarlet.Scarlet;

public interface MiscUtils
{

    static boolean blank(String string)
    {
        return string == null || string.isEmpty();
    }

    static String nonBlankOrNull(String string1)
    {
        return !blank(string1) ? string1 : null;
    }
    static String nonBlankOrNull(String string1, String string2)
    {
        return !blank(string1) ? string1 : !blank(string2) ? string2 : null;
    }
    static String nonBlankOrNull(String string1, String string2, String string3)
    {
        return !blank(string1) ? string1 : !blank(string2) ? string2 : !blank(string3) ? string3 : null;
    }

    static String maybeEllipsis(int maxLength, String string)
    {
        return string == null || string.length() <= maxLength ? string : (string.substring(0, maxLength - 1) + '\u2026');
    }

    static final ZoneRules DEFAULT_ZONE_RULES = ZoneId.systemDefault().getRules();
    static OffsetDateTime odt2utc(LocalDateTime ldt)
    {
        return OffsetDateTime.of(ldt, DEFAULT_ZONE_RULES.getOffset(ldt)).withOffsetSameInstant(ZoneOffset.UTC);
    }

    static final DateTimeFormatter
        DAY_NAME_FULL = new DateTimeFormatterBuilder().appendText(ChronoField.DAY_OF_WEEK, TextStyle.FULL).toFormatter(),
        MONTH_NAME_FULL = new DateTimeFormatterBuilder().appendText(ChronoField.MONTH_OF_YEAR, TextStyle.FULL).toFormatter();

    static String ordinalSuffix(long ordinal)
    {
        switch ((int)Math.abs(ordinal % 10L))
        {
        default: return "th";
        case  1: return "st";
        case  2: return "nd";
        case  3: return "rd";
        }
    }

    @SafeVarargs
    static <T> int indexOf(T target, T... array)
    {
        if (array != null)
    for (int i = 0, l = array.length; i < l; i++)
        if (Objects.equals(array[i], target))
            return i;
        return -1;
    }

    static boolean sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
            return true;
        }
        catch (InterruptedException iex)
        {
            return false;
        }
    }

    static void close(AutoCloseable resource)
    {
        if (resource != null) try
        {
            resource.close();
        }
        catch (Exception ex)
        {
            Scarlet.LOG.error("Exception closing resource", ex);
        }
    }
    static void close(Iterable<? extends AutoCloseable> resources)
    {
        if (resources != null) for (AutoCloseable resource : resources) try
        {
            resource.close();
        }
        catch (Exception ex)
        {
            Scarlet.LOG.error("Exception closing resource", ex);
        }
    }

    static int parseIntElse(String string, int fallback)
    {
        try
        {
            return Integer.parseInt(string);
        }
        catch (RuntimeException rex)
        {
            return fallback;
        }
    }

    static double parseDoubleElse(String string, double fallback)
    {
        try
        {
            return Double.parseDouble(string);
        }
        catch (RuntimeException rex)
        {
            return fallback;
        }
    }

    static Map<DiscordLocale, String> genLocalized(Function<DiscordLocale, String> function)
    {
        Map<DiscordLocale, String> map = new HashMap<>();
        for (DiscordLocale discordLocale : DiscordLocale.values())
            if (discordLocale != DiscordLocale.UNKNOWN)
                map.put(discordLocale, function.apply(discordLocale));
        return map;
    }

    static long lastModified(File file) throws IOException
    {
        return Files.getLastModifiedTime(file.toPath()).toMillis();
    }

    static boolean isNewerThan(File file, long epochMillis) throws IOException
    {
        return file.exists() && lastModified(file) > epochMillis;
    }

    static <T, U> U[] map(T[] arr, IntFunction<U[]> nac, Function<T, U> map)
    {
        int len = arr.length;
        U[] ret = nac.apply(len);
        for (int idx = 0; idx < len; idx++)
            ret[idx] = map.apply(arr[idx]);
        return ret;
    }

    static String extractTypedUuid(String type, String fallback, String idContaining)
    {
        Matcher m = Pattern.compile(type+"_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matcher(idContaining);
        return m.find() ? m.group() : fallback;
    }

    static String userImageUrl(User user)
    {
        String picture = user.getProfilePicOverride();
        if (picture != null && !picture.trim().isEmpty())
            return picture;
        String icon = user.getUserIcon();
        if (icon != null && !icon.trim().isEmpty())
            return icon;
        String avatar = user.getCurrentAvatarImageUrl();
        if (avatar != null && !avatar.trim().isEmpty())
            return avatar;
        return "https://vrchat.com/api/1/file/file_0e8c4e32-7444-44ea-ade4-313c010d4bae/1/file"; // robot
    }

    static String latestContentUrlOrNull(String fileId)
    {
        if (fileId == null || fileId.trim().isEmpty())
            return null;
        int version;
        try (HttpURLInputStream http = HttpURLInputStream.get("https://vrchat.com/api/1/file/"+fileId))
        {
            version = JSON.getGson().fromJson(new InputStreamReader(http), ModelFile.class).getVersions().stream().mapToInt(FileVersion::getVersion).max().orElse(1);
        }
        catch (Exception ex)
        {
            version = 1;
        }
        return "https://vrchat.com/api/1/file/"+fileId+"/"+version+"/file";
    }

    static byte[] readAllBytes(InputStream input) throws IOException
    {
        byte[] buffer = new byte[Math.max(1024, input.available())];
        int offset = 0;
        for (int bytesRead; (bytesRead = input.read(buffer, offset, buffer.length - offset)) != -1; )
            if ((offset += bytesRead) == buffer.length)
                buffer = Arrays.copyOf(buffer, buffer.length + Math.max(input.available(), buffer.length >> 1));
        return offset == buffer.length ? buffer : Arrays.copyOf(buffer, offset);
    }

    static String stringify_ymd_hms_ms_us_ns(TemporalAmount amount)
    {
        if (amount == null)
            return null;
        int years,
            months,
            days,
            hours = 0,
            minutes = 0,
            seconds = 0,
            millis = 0,
            micros = 0,
            nanos = 0;
        if (amount instanceof Period)
        {
            Period period = (Period)amount;
            years = period.getYears();
            months = period.getMonths();
            days = period.getDays();
        }
        else
        {
            Duration duration = amount instanceof Duration ? (Duration)amount : Duration.from(amount);
            Period period = Period.ofDays((int)(duration.getSeconds() / 86400L)).normalized();
            years = period.getYears();
            months = period.getMonths();
            days = period.getDays();
            seconds = (int)(duration.getSeconds() % 86400L);
            if (seconds >= 60)
            {
                minutes = seconds / 60;
                seconds = seconds % 60;
                if (minutes >= 60)
                {
                    hours = minutes / 60;
                    minutes = minutes % 60;
                }
            }
            nanos = duration.getNano();
            if (nanos >= 1000)
            {
                micros = nanos / 1000;
                nanos = nanos % 1000;
                if (micros >= 1000)
                {
                    millis = micros / 1000;
                    micros = micros % 1000;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (years != 0) sb.append(years).append("y ");
        if (months != 0) sb.append(months).append("m ");
        if (days != 0) sb.append(days).append("d ");
        sb.append(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        if (millis != 0 || micros != 0 || nanos != 0) sb.append(String.format(".%03d", millis));
        if (micros != 0 || nanos != 0) sb.append(String.format(".%03d", micros));
        if (nanos != 0) sb.append(String.format(".%03d", nanos));
        return sb.toString();
    }

    static String stringify_ymd_hms(TemporalAmount amount)
    {
        if (amount == null)
            return null;
        int years,
            months,
            days,
            hours = 0,
            minutes = 0,
            seconds = 0;
        if (amount instanceof Period)
        {
            Period period = (Period)amount;
            years = period.getYears();
            months = period.getMonths();
            days = period.getDays();
        }
        else
        {
            Duration duration = amount instanceof Duration ? (Duration)amount : Duration.from(amount);
            Period period = Period.ofDays((int)(duration.getSeconds() / 86400L)).normalized();
            years = period.getYears();
            months = period.getMonths();
            days = period.getDays();
            seconds = (int)(duration.getSeconds() % 86400L);
            if (seconds >= 60)
            {
                minutes = seconds / 60;
                seconds = seconds % 60;
                if (minutes >= 60)
                {
                    hours = minutes / 60;
                    minutes = minutes % 60;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (years != 0) sb.append(years).append("y ");
        if (months != 0) sb.append(months).append("m ");
        if (days != 0) sb.append(days).append("d ");
        sb.append(String.format("%02d:%02d:%02d ", hours, minutes, seconds));
        return sb.length() == 0 ? "0s" : sb.substring(0, sb.length() - 1);
    }

    static String stringify_ymd(TemporalAmount amount)
    {
        if (amount == null)
            return null;
        int years,
            months,
            days;
        if (amount instanceof Period)
        {
            Period period = (Period)amount;
            years = period.getYears();
            months = period.getMonths();
            days = period.getDays();
        }
        else
        {
            Duration duration = amount instanceof Duration ? (Duration)amount : Duration.from(amount);
            Period period = Period.ofDays((int)(duration.getSeconds() / 86400L)).normalized();
            years = period.getYears();
            months = period.getMonths();
            days = period.getDays();
        }
        StringBuilder sb = new StringBuilder();
        if (years != 0) sb.append(years).append("y ");
        if (months != 0) sb.append(months).append("m ");
        if (days != 0) sb.append(days).append("d ");
        return sb.length() == 0 ? "0D" : sb.substring(0, sb.length() - 1);
    }

    static int bin2b64(int len_bin)
    {
        if (len_bin <= 0) return 0;
        int mod = len_bin % 3;
        return (len_bin / 3) * 4 + (mod == 0 ? 0 : (mod + 1));
    }
    static int b642bin(int len_b64)
    {
        if (len_b64 <= 0) return 0;
        int mod = len_b64 & 0x03;
        return (len_b64 / 4) * 3 + (mod == 0 ? 0 : (mod - 1));
    }

    static Runnable withMinimumInterval(long minimumIntervalMillis, Runnable runnable)
    {
        if (runnable == null)
            throw new IllegalArgumentException("runnable == null");
        long minIntervalMs = Math.max(0L, minimumIntervalMillis);
        AtomicLong prev = new AtomicLong(System.currentTimeMillis());
        return () ->
        {
            long nowEpochMs = System.currentTimeMillis(),
                 prevEpochMs = prev.get();
            if (nowEpochMs - prevEpochMs >= minIntervalMs && prev.compareAndSet(prevEpochMs, nowEpochMs))
                runnable.run();
        };
    }

    static Pattern SEMVER = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)([\\.\\-_]?(?<kind>\\w+))?([\\.\\-_]?(?<build>\\d+))?");
    static Comparator<String> SEMVER_CMP_OLDEST_FIRST = MiscUtils::compareSemVer,
                              SEMVER_CMP_NEWEST_FIRST = SEMVER_CMP_OLDEST_FIRST.reversed();
    static boolean isValidVersion(String v)
    {
        return v != null && SEMVER.matcher(v).matches();
    }
    static boolean isPreviewVersion(String v)
    {
        if (v == null)
            return false;
        Matcher vm = SEMVER.matcher(v);
        return vm.matches() && (vm.group("kind") != null || vm.group("build") != null);
    }
    static int compareSemVer(String l, String r)
    {
        try
        {
            if (l == null)
                return r == null ? 0 : 1;
            if (r == null)
                return -1;
            Matcher lm = SEMVER.matcher(l),
                    rm = SEMVER.matcher(r);
            if (!lm.matches())
                return !rm.matches() ? l.compareTo(r) : 1;
            if (!rm.matches())
                return -1;
            
            int li, ri, cmp;
            
            li = Integer.parseInt(lm.group("major"));
            ri = Integer.parseInt(rm.group("major"));
            if ((cmp = Integer.compare(li, ri)) != 0) return cmp;
            
            li = Integer.parseInt(lm.group("minor"));
            ri = Integer.parseInt(rm.group("minor"));
            if ((cmp = Integer.compare(li, ri)) != 0) return cmp;
            
            li = Integer.parseInt(lm.group("patch"));
            ri = Integer.parseInt(rm.group("patch"));
            if ((cmp = Integer.compare(li, ri)) != 0) return cmp;
            
            String lx = lm.group("kind"),
                   rx = rm.group("kind");
            
            cmp = lx == null
                ? rx == null
                    ? 0
                    : 1
                : rx == null
                    ? -1
                    : lx.compareTo(rx);
            if (cmp != 0) return cmp;

            lx = lm.group("build");
            rx = rm.group("build");
            
            li = lx == null ? Integer.MAX_VALUE : Integer.parseInt(lx);
            ri = rx == null ? Integer.MAX_VALUE : Integer.parseInt(rx);
            if ((cmp = Integer.compare(li, ri)) != 0) return cmp;
        }
        catch (RuntimeException rex)
        {
            // ignore
        }
        return 0;
    }

    public static List<String> paginateOnLines(StringBuilder input, int maxComponentLen)
    {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < input.length())
        {
            int end = start + maxComponentLen;
            if (end >= input.length())
            {
                parts.add(input.substring(start).trim());
                start = input.length();
            }
            else
            {
                int lastNewline = input.lastIndexOf("\n", end);
                if (lastNewline > start && lastNewline < end)
                {
                    parts.add(input.substring(start, lastNewline).trim());
                    start = lastNewline + 1;
                }
                else
                {
                    parts.add(input.substring(start, end).trim());
                    start = end;
                }
            }
        }
        return parts;
    }

    static Writer writer(File file) throws FileNotFoundException
    {
        return new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
    }

    static Reader reader(File file) throws FileNotFoundException
    {
        return new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
    }

    static Color lerp(Color lhs, Color rhs, float lerp)
    {
        lerp = Math.max(0, Math.min(1, lerp));
        int r = (int)(lhs.getRed()   + lerp * (rhs.getRed()   - lhs.getRed()));
        int g = (int)(lhs.getGreen() + lerp * (rhs.getGreen() - lhs.getGreen()));
        int b = (int)(lhs.getBlue()  + lerp * (rhs.getBlue()  - lhs.getBlue()));
        int a = (int)(lhs.getAlpha() + lerp * (rhs.getAlpha() - lhs.getAlpha()));
        return new Color(r, g, b, a);
    }

    interface AWTToolkit
    {
        static void set(String text)
        {
            try
            {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), (c, t) -> {});
            }
            catch (Exception ex)
            {
            }
        }
        static String get()
        {
            try
            {
                Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor))
                    return (String)t.getTransferData(DataFlavor.stringFlavor);
            }
            catch (Exception ex)
            {
            }
            return null;
        }
    }

    interface AWTDesktop
    {
        static boolean open(File file)
        {
            if (Desktop.isDesktopSupported()) try
            {
                Desktop.getDesktop().open(file);
                return true;
            }
            catch (Exception ex)
            {
                Scarlet.LOG.error("Exception opening file "+file, ex);
            }
            return false;
        }
        static boolean edit(File file)
        {
            if (Desktop.isDesktopSupported()) try
            {
                Desktop.getDesktop().edit(file);
                return true;
            }
            catch (Exception ex)
            {
                Scarlet.LOG.error("Exception editing file "+file, ex);
            }
            return false;
        }
        static boolean browse(URI uri)
        {
            if (Desktop.isDesktopSupported()) try
            {
                Desktop.getDesktop().browse(uri);
                return true;
            }
            catch (Exception ex)
            {
                Scarlet.LOG.error("Exception browsing to uri "+uri, ex);
            }
            return false;
        }
    }

}