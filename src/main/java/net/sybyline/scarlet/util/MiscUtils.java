package net.sybyline.scarlet.util;

import java.awt.Desktop;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

import io.github.vrchatapi.JSON;
import io.github.vrchatapi.model.FileVersion;
import io.github.vrchatapi.model.ModelFile;
import io.github.vrchatapi.model.User;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.sybyline.scarlet.Scarlet;

public interface MiscUtils
{

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

    static int compareSemVer(String l, String r)
    {
        try
        {
            int li, ri, cmp;
            
            li = l == null ? 0 : 1;
            ri = r == null ? 0 : 1;
            if ((cmp = Integer.compare(li, ri)) != 0) return cmp;
            
            if (l == null) return 0;
            
            String[] la = l.split("\\."),
                     ra = r.split("\\."),
                     l2a = la[2].split("-"),
                     r2a = ra[2].split("-");
            
            li = Integer.parseInt(la[0]);
            ri = Integer.parseInt(ra[0]);
            if ((cmp = Integer.compare(li, ri)) != 0) return cmp;
            
            li = Integer.parseInt(la[1]);
            ri = Integer.parseInt(ra[1]);
            if ((cmp = Integer.compare(li, ri)) != 0) return cmp;
            
            li = Integer.parseInt(l2a[0]);
            ri = Integer.parseInt(r2a[0]);
            if ((cmp = Integer.compare(li, ri)) != 0) return cmp;
            
            li = l2a.length;
            ri = r2a.length;
            if ((cmp = Integer.compare(li, ri)) != 0) return cmp;
            
            if (l2a.length > 1)
            {
                cmp = l2a[1].compareTo(r2a[1]);
                if (cmp != 0)
                    return cmp;
            }
        }
        catch (RuntimeException rex)
        {
            // ignore
        }
        return 0;
    }

    static Writer writer(File file) throws FileNotFoundException
    {
        return new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
    }

    static Reader reader(File file) throws FileNotFoundException
    {
        return new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
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