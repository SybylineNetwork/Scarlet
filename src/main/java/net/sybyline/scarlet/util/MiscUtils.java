package net.sybyline.scarlet.util;

import java.io.InputStreamReader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.github.vrchatapi.JSON;
import io.github.vrchatapi.model.FileVersion;
import io.github.vrchatapi.model.ModelFile;
import io.github.vrchatapi.model.User;

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

    @SafeVarargs
    static <T> Stream<T> stream(T... array)
    {
        return StreamSupport.stream(Spliterators.spliterator(array, 0, array.length, Spliterator.ORDERED | Spliterator.IMMUTABLE), false);
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

}