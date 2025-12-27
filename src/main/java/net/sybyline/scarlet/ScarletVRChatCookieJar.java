package net.sybyline.scarlet;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.ApiClient;

import net.sybyline.scarlet.util.CollectionMap;
import net.sybyline.scarlet.util.Func;
import net.sybyline.scarlet.util.MiscUtils;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class ScarletVRChatCookieJar implements CookieJar, Closeable
{

    ScarletVRChatCookieJar(Scarlet scarlet, String domain, File cookieFile)
    {
        this.cookieStore = scarlet.settings.new RegistryStringEncrypted(domain+".cookieStore", true);
        this.cookieStoreAlt = scarlet.settings.new RegistryJsonEncrypted<>(domain+".cookieStoreAlt", true, null, new TypeToken<Map<String, List<String>>>(){}.getType());
        this.cookieFile = cookieFile;
        this.list = new ArrayList<>();
        this.alternateLists = CollectionMap.listsConcurrent();
        this.url = HttpUrl.parse(Scarlet.API_URL_2);
    }

    private final ScarletSettings.RegistryStringEncrypted cookieStore;
    private final ScarletSettings.RegistryJsonEncrypted<Map<String, List<String>>> cookieStoreAlt;
    private final File cookieFile;
    private final List<Cookie> list;
    private final CollectionMap.OfLists<String, Cookie> alternateLists;
    private HttpUrl url;

    static final ThreadLocal<String> _context = new ThreadLocal<>();
    static final ThreadLocal<List<Cookie>> _context2 = new ThreadLocal<>();
    static String _set(String context)
    {
        try
        {
            return _context.get();
        }
        finally
        {
            _context.set(context);
        }
    }
    static void _reset(String context, String previousContext)
    {
        String currentContext = _context.get();
        if (!Objects.equals(currentContext, context))
            ScarletVRChat.LOG.warn("AltCredContext mismatch", new IllegalStateException("context = "+context+", currentContext = "+currentContext+", previousContext = "+previousContext));
        _context.set(previousContext);
    }

    public class AltCredContext implements Closeable
    {
        public AltCredContext(String context)
        {
            this.context = context;
            this.previousContext = _set(context);
            if (context == null)
                _context2.set(this.list = new CopyOnWriteArrayList<>());
            else
                this.list = ScarletVRChatCookieJar.this.alternateLists.valuesGetOrCreate(context);
            
        }
        private final String context, previousContext;
        private final List<Cookie> list;
        public String context()
        {
            return this.context;
        }
        public List<Cookie> list()
        {
            return this.list;
        }
        public boolean transferTo(String toContext)
        {
            return this.context == null && toContext != null && ScarletVRChatCookieJar.this.alternateLists.valuesAddAll(toContext, this.list);
        }
        @Override
        public void close()
        {
            _reset(this.context, this.previousContext);
            if (this.context == null && _context2.get() == this.list)
                _context2.remove();
        }
    }
    public static <X extends Throwable> void contextRun(String context, Func.V0<X> task) throws X
    {
        String previousContext = _set(context);
        try
        {
            task.invoke();
        }
        finally
        {
            _reset(context, previousContext);
        }
    }
    public static <X extends Throwable, R> R contextGet(String context, Func.F0<X, R> task) throws X
    {
        String previousContext = _set(context);
        try
        {
            return task.invoke();
        }
        finally
        {
            _reset(context, previousContext);
        }
    }

    synchronized void clear()
    {
        this.list.clear();
    }

    synchronized void load()
    {
        boolean save = false;
        if (this.cookieFile.isFile())
        {
            try (BufferedReader in = new BufferedReader(MiscUtils.reader(this.cookieFile)))
            {
                in.lines()
                    .map(String::trim)
                    .filter($ -> !$.isEmpty())
                    .sequential()
                    .forEachOrdered(this::add);
                save = true;
            }
            catch (IOException ioex)
            {
                ScarletVRChat.LOG.error("Exception loading cookies", ioex);
            }
            if (save)
            {
                this.cookieFile.delete();
            }
        }
        {
            String store = this.cookieStore.getOrNull();
            if (store != null) try (BufferedReader in = new BufferedReader(new StringReader(store)))
            {
                in.lines()
                    .map(String::trim)
                    .filter($ -> !$.isEmpty())
                    .sequential()
                    .forEachOrdered(this::add);
            }
            catch (IOException ioex)
            {
                ScarletVRChat.LOG.error("Exception loading cookies from store", ioex);
            }
        }
        {
            Map<String, List<String>> altStore = this.cookieStoreAlt.getOrNull();
            if (altStore != null && !altStore.isEmpty())
                altStore.forEach((context, list) -> list.forEach(cookie -> this.addAlt(context, cookie)));
        }
        if (save)
        {
            this.save();
        }
    }

    synchronized void save()
    {
        StringBuilder sb = new StringBuilder();
        for (Cookie cookie : removeStale(this.list))
            sb.append(cookie.toString()).append('\n');
        this.cookieStore.set(sb.toString());
        if (!this.alternateLists.isEmpty())
        {
            Map<String, List<String>> altStore = new HashMap<>();
            this.alternateLists.forEach((context, list) -> altStore.put(context, list.stream().map(Cookie::toString).collect(Collectors.toList())));
            this.cookieStoreAlt.set(altStore);
        }
    }

    synchronized void setup(ApiClient client)
    {
        this.url = HttpUrl.parse(String.format("https://%s/", HttpUrl.parse(client.getBasePath()).host()));
    }

    boolean removeAlt(String alt)
    {
        return this.alternateLists.remove(alt) != null;
    }

    Set<String> alts()
    {
        return this.alternateLists.keySet();
    }

    synchronized List<Cookie> copy()
    {
        return new ArrayList<>(this.list);
    }

    synchronized Stream<Cookie> stream()
    {
        return this.list.stream();
    }

    boolean addAlt(String context, String key, String value)
    {
        return contextGet(context, () -> this.add(key, value));
    }
    boolean addAlt(String context, String string)
    {
        return contextGet(context, () -> this.add(string));
    }
    boolean addAlt(String context, Cookie added)
    {
        return contextGet(context, () -> this.add(added));
    }
    boolean addAlt(String context, List<Cookie> added)
    {
        return contextGet(context, () -> this.add(added));
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
        List<Cookie> list = this.getForRequest(true);
        return addTo(list, added);
    }
    synchronized boolean add(List<Cookie> added)
    {
        if (added == null || added.isEmpty())
            return false;
        List<Cookie> list = this.getForRequest(true);
        boolean changed = false;
        for (Cookie cookie : added)
            changed |= addTo(list, cookie);
        return changed;
    }

    List<Cookie> getForRequest(boolean add)
    {
        List<Cookie> list = _context2.get();
        if (list == null)
        {
            list = this.list;
            String context = _context.get();
            if (context != null)
                list = add ? this.alternateLists.valuesGetOrCreate(context) : this.alternateLists.getOrDefault(context, list);
        }
        return list;
    }

    static boolean addTo(List<Cookie> list, Cookie added)
    {
        list.removeIf($ -> $.domain().equals(added.domain()) && $.name().equals(added.name()));
        return list.add(added);
    }
    static List<Cookie> removeStale(List<Cookie> list)
    {
        long now = System.currentTimeMillis();
        list.removeIf(cookie -> cookie.expiresAt() < now);
        return list;
    }

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies)
    {
        cookies.forEach(this::add);
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url)
    {
        // remove expired cookies and return only matching Cookies
        return removeStale(this.getForRequest(false)).stream().filter(cookie -> cookie.matches(url)).collect(Collectors.toList());
    }

    @Override
    public void close() throws IOException
    {
        this.save();
    }

}
