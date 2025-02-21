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
import java.nio.file.attribute.FileTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.sybyline.scarlet.Scarlet;

public class ScarletBuild
{

    static final Path DIR = Paths.get(".").toAbsolutePath().getParent();

    public static void main(String[] args) throws Throwable
    {
        build();
    }

    static void build() throws Throwable
    {
        String build = "_internal/build",
               buildClasses = build+"/out",
               buildManifest = build+"/MANIFEST.MF",
               buildJar = build+"/scarlet-"+Scarlet.VERSION+".jar",
               buildBat = build+"/run.bat",
               buildPkg = build+"/pkg",
               buildPkgJar = buildPkg+"/scarlet-"+Scarlet.VERSION+".jar",
               buildPkgBat = buildPkg+"/run.bat",
               buildZip = build+"/scarlet-"+Scarlet.VERSION+".zip",
               
               srcJava = "src/main/java",
               srcRes = "src/main/resources",
               mainClass = "net.sybyline.scarlet.Scarlet";
        
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
                    att.putValue("Name", "net/sybyline/scarlet/");
                    att.putValue("Specification-Title", Scarlet.NAME);
                    att.putValue("Specification-Version", Scarlet.VERSION);
                    att.putValue("Specification-Vendor", Scarlet.DEV_DISCORD);
                    att.putValue("Implementation-Title", "net.sybyline.scarlet");
                    att.putValue("Implementation-Version", "build-"+OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    att.putValue("Implementation-Vendor", Scarlet.DEV_DISCORD);
            mff.write(mf);
        }
        
        System.out.println("Assembling jar "+buildJar);
        exec("jar",
            "-cfm",
            "\""+abs(buildJar)+"\"",
            "\""+abs(buildManifest)+"\"",
            "-C", "\""+abs(buildClasses)+"\"",
            "."
        );
        
        System.out.println("Printing runner "+buildBat);
        try (PrintStream bat = new PrintStream(buildBat))
        {
            bat.append("@echo off").println();
            bat.append("@rem set JAVA_HOME=").println();
            bat.append("@rem set PATH=%JAVA_HOME%\\bin;%JAVA_HOME%\\jre\\bin;%JAVA_HOME%\\jre\\bin\\server;%JAVA_HOME%\\bin\\server;%PATH%").println();
            bat.append("java -jar scarlet-").append(Scarlet.VERSION).append(".jar").println();
            bat.append("pause").println();
        }
        
        System.out.println("Copying release files");
        copyOne(buildJar, buildPkgJar);
        copyOne(buildBat, buildPkgBat);
        
        System.out.println("Zipping release "+buildZip);
        compressDir(buildPkg, buildZip, FileTime.fromMillis(System.currentTimeMillis()), Deflater.DEFAULT_COMPRESSION);
        
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
    static void copyOne(String from, String to) throws IOException
    {
        Path from0 = DIR.resolve(from),
             to0 = DIR.resolve(to);
        Files.createDirectories(to0.getParent());
        Files.copy(from0, to0);
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
    static void compressDir(String dir, String zip, FileTime filetime, int compression) throws IOException
    {
        Path dir0 = DIR.resolve(dir),
             zip0 = DIR.resolve(zip);
        if (!Files.deleteIfExists(zip0))
            Files.createDirectories(zip0.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip0)))
        {
            Files.walkFileTree(dir0, new SimpleFileVisitor<Path>(){
                public FileVisitResult visitFile(Path rel, BasicFileAttributes attrs) throws IOException {
                    out.setMethod(ZipOutputStream.DEFLATED);
                    out.setLevel(compression);
                    out.putNextEntry(
                        new ZipEntry(dir0.relativize(rel).toString().replace('\\', '/'))
                        .setLastModifiedTime(filetime != null ? filetime : attrs.lastModifiedTime())
                        .setLastAccessTime(filetime != null ? filetime : attrs.lastAccessTime())
                        .setCreationTime(filetime != null ? filetime : attrs.creationTime()));
                    Files.copy(rel, out);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

}
