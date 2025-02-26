package net.sybyline.scarlet.util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Manifest;

import org.jetbrains.annotations.Nullable;

import net.sybyline.scarlet.Scarlet;

public class MavenDepsLoader
{

    public static void init()
    {
        // noop
    }

    public static @Nullable Path jarPath()
    {
        return jarPath;
    }

    private MavenDepsLoader()
    {
        throw new UnsupportedOperationException();
    }

    static final String[] repos =
    {
        "https://search.maven.org/remotecontent?filepath=",
        "https://jitpack.io/",
    };

    static Path jarPath = null;

    static
    {
        clinit();
    }

    static void clinit()
    {
        String depsUrlPrefix = "jar:file:/",
               depsAbsPath = "/META-INF/MANIFEST.MF",
               depsUrlSuffix = "!" + depsAbsPath;
        
        URL url = MavenDepsLoader.class.getResource(depsAbsPath);
        if (url == null)
            return;
        String urlString = url.toString();
        if (!urlString.startsWith(depsUrlPrefix) || !urlString.endsWith(depsUrlSuffix))
            return;
        
        String unescapedPath;
        try
        {
            unescapedPath = URLDecoder.decode(urlString.substring(depsUrlPrefix.length(), urlString.length() - depsUrlSuffix.length()), "utf-8");
        }
        catch (Exception ex)
        {
            throw new Error(ex);
        }
        
        
        Path jarPath = Paths.get(unescapedPath),
             jarDir = jarPath.getParent(),
             depsDir = jarDir.resolve("libraries");
        
        MavenDepsLoader.jarPath = jarPath;
        
        if (!Files.isDirectory(depsDir)) try
        {
            Files.createDirectories(depsDir);
        }
        catch (Exception ex)
        {
            System.err.println(String.format("net.sybyline.scarlet.util.MavenDepsLoader: Exception creating dependencies directory '%s'", depsDir));
            ex.printStackTrace(System.err);
            return;
        }
        
        Manifest mf;
        try (InputStream mfIn = url.openStream())
        {
            mf = new Manifest(mfIn);
        }
        catch (Exception ex)
        {
            System.err.println(String.format("net.sybyline.scarlet.util.MavenDepsLoader: Exception reading manifest from '%s'", url));
            ex.printStackTrace(System.err);
            return;
        }
        
        String cpEntriesString = mf.getMainAttributes().getValue("Class-Path");
//        System.out.println(String.format("net.sybyline.scarlet.util.MavenDepsLoader: Class-Path: %s", cpEntriesString));
        
        String[] cpEntries = cpEntriesString.split(" ");
        
        String libPrefix = "libraries/";
        
        for (String cpEntry : cpEntries)
        {
            if (cpEntry.startsWith(libPrefix))
            {
//                System.out.println(String.format("net.sybyline.scarlet.util.MavenDepsLoader: Checking dependency '%s'", cpEntry));
                dlDep(depsDir, cpEntry.substring(libPrefix.length()));
            }
        }
        
    }

    static void dlDep(Path depsDir, String line)
    {
        if ((line = line.trim()).isEmpty())
            return;
        if (line.charAt(0) == '#')
            return;
        if (".".equals(line))
            return;
        
        String depName = Paths.get(line).getFileName().toString();
        
        Path depPath = depsDir.resolve(line);
        if (Files.isRegularFile(depPath))
            return;
        
        String depUrl = findDepUrl(line);
        
        if (depUrl == null)
        {
            System.err.println(String.format("net.sybyline.scarlet.util.MavenDepsLoader: Failed to locate dependency '%s' from '%s' to '%s'", depName, depUrl, depPath));
            return;
        }
        
        Path depParent = depPath.getParent();
        if (!Files.isDirectory(depParent))
        try
        {
            Files.createDirectories(depParent);
        }
        catch (Exception ex)
        {
            System.err.println(String.format("net.sybyline.scarlet.util.MavenDepsLoader: Exception creating directories '%s'", depPath.getParent()));
            ex.printStackTrace(System.err);
            return;
        }
        
        try (HttpURLInputStream depIn = HttpURLInputStream.get(depUrl))
        {
            Files.copy(depIn, depPath);
            System.out.println(String.format("net.sybyline.scarlet.util.MavenDepsLoader: Located and copied dependency '%s' from '%s' to '%s'", depName, depUrl, depPath));
        }
        catch (Exception ex)
        {
            System.err.println(String.format("net.sybyline.scarlet.util.MavenDepsLoader: Exception copying dependency '%s' from '%s' to '%s'", depName, depUrl, depPath));
            ex.printStackTrace(System.err);
        }
    }

    static String findDepUrl(String path)
    {
        for (String repo : repos)
        {
            String url = repo + path;
            try
            {
                URL url0 = new URL(url);
                HttpURLConnection connection = (HttpURLConnection)url0.openConnection();
                try
                {
                    connection.setRequestMethod("HEAD");
                    connection.setRequestProperty("User-Agent", Scarlet.USER_AGENT_STATIC);
                    connection.setConnectTimeout(5_000);
                    connection.setReadTimeout(5_000);
                    int code = connection.getResponseCode();
                    if (code >= 200 && code <= 399)
                    {
                        return url;
                    }
                }
                finally
                {
                    connection.disconnect();
                }
            }
            catch (Exception ex)
            {
                // noop
            }
        }
        return null;
    }

}
