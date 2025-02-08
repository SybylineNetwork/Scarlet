package net.sybyline.scarlet.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sybyline.scarlet.Scarlet;

public class ScarletBuild
{

    static final Path DIR = Paths.get(".").toAbsolutePath().getParent();

    public static void main(String[] args) throws Throwable
    {
        String build = "_internal/build",
               buildClasses = build+"/out",
               buildManifest = build+"/MANIFEST.MF",
               buildMeta = buildClasses+"/META-INF",
               buildDepsTxt = buildMeta+"/dependencies.txt",
               buildJar = build+"/scarlet-"+Scarlet.VERSION+".jar",
               srcJava = "src/main/java",
               srcRes = "src/main/resources",
               mainClass = "net.sybyline.scarlet.Scarlet";
        
//        mkDirs(build);
//        mkDirs(buildClasses);
//        mkDirs(buildMeta);
        
        System.out.println("Cleaning "+build);
        cleanDir(build);
        
        System.out.println("Compiling "+srcJava+" to "+buildClasses);
        exec("javac",
            "-encoding", "UTF-8",
            "-classpath", "\""+javacClasspath()+"\"",
            "-sourcepath", "\""+abs(srcJava)+"\"",
            "-d", "\""+abs(buildClasses)+"\"",
            "-source", "8",
            "-target", "8",
            listSources(srcJava)
        );

        System.out.println("Copying resources from "+srcRes+" to "+buildClasses);
        copyContents(srcRes, buildClasses);
        
        System.out.println("Writing manifest "+buildManifest);
        try (PrintStream mf = new PrintStream(buildManifest))
        {
            Manifest mff = new Manifest();
                Attributes att = mff.getMainAttributes();
                    att.putValue("Manifest-Version", "1.0");
                    att.putValue("Main-Class", mainClass);
                    att.putValue("Class-Path", mfClasspath());
            mff.write(mf);
        }
        
        System.out.println("Writing dependency list "+buildDepsTxt);
        try (PrintStream mf = new PrintStream(buildDepsTxt))
        {
            mf.append(depsClasspath());
        }
        
        System.out.println("Assembling jar "+buildJar);
        exec("jar",
            "-cfm",
            "\""+abs(buildJar)+"\"",
            "\""+abs(buildManifest)+"\"",
            "-C", "\""+abs(buildClasses)+"\"",
            "."
        );
        System.out.println("Done");
    }

    static int exec(Object... command) throws Exception
    {
        String cmd = exec0(command).collect(Collectors.joining(" "));
        System.out.println("Executing: "+cmd);
        String pwshb64 = Base64.getEncoder().encodeToString(cmd.getBytes(StandardCharsets.UTF_16LE)),
               pwshcmd = "powershell -NoLogo -WindowStyle Hidden -EncodedCommand "+pwshb64;
        Process proc = Runtime.getRuntime().exec(pwshcmd, null, DIR.toFile());
        InputStream stdout = proc.getInputStream(),
                    stderr = proc.getErrorStream();
        do
        {
            byte[] ba = new byte[1024];
            for (int available; (available = stdout.available()) > 0;)
            {
                int read = stdout.read(ba, 0, Math.min(available, ba.length));
                if (read > 0)
                    System.out.write(ba, 0, read);
            }
            for (int available; (available = stderr.available()) > 0;)
            {
                int read = stderr.read(ba, 0, Math.min(available, ba.length));
                if (read > 0)
                    System.err.write(ba, 0, read);
            }
        }
        while (proc.isAlive() || (stdout.available() > 0 || stderr.available() > 0));
        return proc.waitFor();
    }
    static Stream<String> exec0(Object command)
    {
        return command instanceof Object[] ? Arrays.stream((Object[])command).flatMap(ScarletBuild::exec0) : Stream.of(String.valueOf(command));
    }

    @SuppressWarnings("restriction")
    static URL[] classpathURLs()
    {
        return sun.misc.SharedSecrets.getJavaNetAccess().getURLClassPath((URLClassLoader)ClassLoader.getSystemClassLoader()).getURLs();
    }
    static String javacClasspath() throws IOException
    {
        List<String> paths = new ArrayList<>();
        for (URL url : classpathURLs())
            if (!url.toString().endsWith("/")
                && url.getPath().contains("/.m2/repository/") // yee
            )
                paths.add(url.getPath().substring(1));
        return paths.stream().collect(Collectors.joining(";"));
    }
    static String mfClasspath() throws IOException
    {
        String m2 = "/.m2/repository/";
        StringBuilder mfcp = new StringBuilder().append('.');
        for (URL url : classpathURLs())
        {
            String string = url.toString();
            int m2i = string.indexOf(m2);
            if (m2i > 9 && !string.endsWith("/"))
            {
                mfcp.append(" libraries/").append(string.substring(m2i + m2.length()));
            }
        }
        return mfcp.toString();
    }
    static String depsClasspath() throws IOException
    {
        String m2 = "/.m2/repository/";
        StringBuilder mfcp = new StringBuilder();
        for (URL url : classpathURLs())
        {
            String string = url.toString();
            int m2i = string.indexOf(m2);
            if (m2i > 9 && !string.endsWith("/"))
            {
                mfcp.append(string.substring(m2i + m2.length())).append("\n");
            }
        }
        return mfcp.toString();
    }

    static Object[] listSources(String sourcepath) throws IOException
    {
        Set<String> ret = new LinkedHashSet<>();
        Files.walkFileTree(DIR.resolve(sourcepath), new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java"))
                {
                    ret.add(DIR.relativize(file).getParent().toString().replace('\\', '/').concat("/*.java"));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return ret.toArray();
    }
    static String abs(String path)
    {
        return DIR.resolve(path).toString().replace('\\', '/');
    }
    static void mkDirs(String root) throws IOException
    {
        Files.createDirectories(DIR.resolve(root));
    }
    static void cleanDir(String root) throws IOException
    {
        Path root0 = DIR.resolve(root);
        if (Files.isDirectory(root0))
            Files.walkFileTree(root0, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (root0 != dir)
                        Files.delete(dir);
                    return exc == null ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
                }
            });
        else
            Files.createDirectories(root0);
    }
    static void copyContents(String from, String to) throws IOException
    {
        Path from0 = DIR.resolve(from),
             to0 = DIR.resolve(to);
        Files.walkFileTree(from0, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path dest = to0.resolve(from0.relativize(file));
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest);
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
