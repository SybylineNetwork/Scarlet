package net.sybyline.scarlet;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.vrchatapi.ApiClient;

import net.sybyline.scarlet.util.MiscUtils;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class ScarletVRChatCookieJar implements CookieJar, Closeable
{

    ScarletVRChatCookieJar(File cookieFile)
    {
        this.cookieFile = cookieFile;
        this.list = new ArrayList<>();
        this.url = HttpUrl.parse(Scarlet.API_URL_2);
    }

    private final File cookieFile;
    private final List<Cookie> list;
    private HttpUrl url;

    synchronized void load()
    {
        if (this.cookieFile.isFile()) try (BufferedReader in = new BufferedReader(MiscUtils.reader(this.cookieFile)))
        {
            in.lines()
                .map(String::trim)
                .filter($ -> !$.isEmpty())
                .sequential()
                .forEachOrdered(this::add);
        }
        catch (IOException ioex)
        {
            ScarletVRChat.LOG.error("Exception loading cookies", ioex);
        }
        else try
        {
            this.cookieFile.getParentFile().mkdirs();
            this.cookieFile.createNewFile();
        }
        catch (IOException ioex)
        {
            ScarletVRChat.LOG.error("Exception loading cookies", ioex);
        }
    }

    synchronized void save()
    {
        this.removeStale();
        try (Writer w = MiscUtils.writer(this.cookieFile))
        {
            for (Cookie cookie : this.list)
            {
                w.write(cookie.toString());
                w.write("\n");
            }
        }
        catch (IOException ioex)
        {
            ScarletVRChat.LOG.error("Exception saving cookies", ioex);
        }
    }

    synchronized void setup(ApiClient client)
    {
        this.url = HttpUrl.parse(String.format("https://%s/", HttpUrl.parse(client.getBasePath()).host()));
    }

    synchronized List<Cookie> copy()
    {
        return new ArrayList<>(this.list);
    }

    synchronized Stream<Cookie> stream()
    {
        return this.list.stream();
    }

    boolean add(String key, String value)
    {
        return this.add(key+'='+value);
    }
    boolean add(String string)
    {
        return this.add(Cookie.parse(this.url, string));
    }
    synchronized boolean add(Cookie added)
    {
        if (added == null)
            return false;
        this.list.removeIf($ -> $.domain().equals(added.domain()) && $.name().equals(added.name()));
        return this.list.add(added);
    }

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies)
    {
        cookies.forEach(this::add);
    }

    private synchronized boolean removeStale()
    {
        long now = System.currentTimeMillis();
        return this.list.removeIf(cookie -> cookie.expiresAt() < now);
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url)
    {
        // remove expired cookies and return only matching Cookies
        this.removeStale();
        return this.list.stream().filter(cookie -> cookie.matches(url)).collect(Collectors.toList());
    }

    @Override
    public void close() throws IOException
    {
        this.save();
    }

}
