package net.sybyline.scarlet.ext;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import net.sybyline.scarlet.ext.AvatarSearch.VrcxAvatar;
import net.sybyline.scarlet.util.HttpURLInputStream;

public interface AvatarSearch_AvatarSearch_CC
{
    static VrcxAvatar[] searchNamePC(String name)
    {
        return list("https://avatarsearch.cc/Avatar/NewAvatarSearcher?name="+name);
    }
    static VrcxAvatar[] searchNameQuest(String name)
    {
        return list("https://avatarsearch.cc/Avatar/NewQuestAvatarSearcher?name="+name);
    }
    static VrcxAvatar[] searchAuthorPC(String name)
    {
        return list("https://avatarsearch.cc/Avatar/NewAuthorSearcher?authorName=test"+name);
    }
    static VrcxAvatar[] searchAuthorQuest(String name)
    {
        return list("https://avatarsearch.cc/Avatar/NewQuestAuthorSearcher?authorName=test"+name);
    }
    static VrcxAvatar[] listRecentlyLogged()
    {
        return list("https://avatarsearch.cc/Avatar/RecentAvatars");
    }
    static VrcxAvatar[] listRandomPC()
    {
        return list("https://avatarsearch.cc/Avatar/RandomAvatarSearcher");
    }
    static VrcxAvatar[] listRandomQuest()
    {
        return list("https://avatarsearch.cc/Avatar/RandomQuestAvatarSearcher");
    }
    static VrcxAvatar[] list(String url)
    {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(HttpURLInputStream.get(url, ExtendedUserAgent.init_conn))))
        {
            return in.lines().map(AvatarSearch_AvatarSearch_CC::parse).toArray(VrcxAvatar[]::new);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return null;
        }
    }
    static VrcxAvatar parse(String line)
    {
        VrcxAvatar ret = new VrcxAvatar();
        String[] linea = line.split("\\|", 4);
        ret.id = linea[0];
        ret.name = linea[1];
        ret.authorName = linea[2];
        ret.description = linea[3];
        return ret;
    }
}