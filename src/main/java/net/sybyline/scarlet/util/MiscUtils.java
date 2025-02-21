package net.sybyline.scarlet.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

}