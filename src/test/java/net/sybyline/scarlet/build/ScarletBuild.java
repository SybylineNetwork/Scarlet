package net.sybyline.scarlet.build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
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
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.sybyline.scarlet.Scarlet;
import net.sybyline.scarlet.ScarletMeta;

public class ScarletBuild
{

    static final Path DIR = Paths.get(".").toAbsolutePath().getParent();
    static final FileTime FILE_TIME = FileTime.fromMillis(System.currentTimeMillis());

    public static void main(String[] args) throws Throwable
    {
        build();
    }

    static void build() throws Throwable
    {
        Scarlet.LOG.info("Building "+Scarlet.NAME+" "+Scarlet.VERSION);
        String build = "_internal/build",
               buildClasses = build+"/out",
               buildManifest = build+"/MANIFEST.MF",
               buildJar = build+"/scarlet-"+Scarlet.VERSION+".jar",
               buildBat = build+"/run.bat",
               buildPkg = build+"/pkg",
               buildPkgJar = buildPkg+"/scarlet-"+Scarlet.VERSION+".jar",
               buildPkgBat = buildPkg+"/run.bat",
               buildZip = build+"/scarlet-"+Scarlet.VERSION+".zip",
               buildPkgLibs = buildPkg+"/libraries",
               buildLibsZip = build+"/libraries.zip",
               buildAllZip = build+"/scarlet-"+Scarlet.VERSION+"-fat.zip",
               
               srcJava = "src/main/java",
               srcRes = "src/main/resources",
               mainClass = "net.sybyline.scarlet.Main",
               
               pom = "pom.xml",
               meta = "meta.json";
        
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
            bat.append("@rem Everything in this file is more-or-less required for restarts and updates").println();
            bat.append("@rem If you want to specify the location of the JDK installation, set the contents of \"scarlet.home.java\" to the root directory of the JDK").println();
            bat.append("@rem If you want to specify the version of Scarlet to run, set the contents of \"scarlet.version\" to the version to run").println();
            bat.append("").println();
            bat.append("setlocal enableextensions enabledelayedexpansion").println();
            bat.append("set \"SCARLET_VERSION=").append(Scarlet.VERSION).append("\"").println();
            bat.append("set \"ORIGINAL_PATH=%PATH%\"").println();
            bat.append("if exist \"%CD%\\scarlet.version\" (").println();
            bat.append("    set /p SCARLET_VERSION=<%CD%\\scarlet.version").println();
            bat.append("    echo Selected !SCARLET_VERSION!").println();
            bat.append(")").println();
            bat.append("set \"ATTEMPTED_VERSION=0.0.0\"").println();
            bat.append(":UPDATE").println();
            bat.append("if exist \"%CD%\\scarlet.version.target\" (").println();
            bat.append("    set /p TARGET_VERSION=<%CD%\\scarlet.version.target").println();
            bat.append("    del \"%CD%\\scarlet.version.target\"").println();
            bat.append("    if exist \"%CD%\\scarlet-!TARGET_VERSION!.jar\" (").println();
            bat.append("        echo Targeting version !TARGET_VERSION!, jar present").println();
            bat.append("        set \"SCARLET_VERSION=!TARGET_VERSION!\"").println();
            bat.append("    ) else (").println();
            bat.append("        set \"TARGET_URL=https://github.com/").append(Scarlet.GROUP).append("/").append(Scarlet.NAME).append("/releases/download/!TARGET_VERSION!/scarlet-!TARGET_VERSION!.jar\"").println();
            bat.append("        set \"TARGET_FILE=%CD%\\scarlet-!TARGET_VERSION!.jar\"").println();
            bat.append("        echo Downloading from !TARGET_URL! to !TARGET_FILE!").println();
            bat.append("        curl -L -o \"%CD%\\scarlet-!TARGET_VERSION!.jar\" \"!TARGET_URL!\"").println();
            bat.append("        if exist \"%CD%\\scarlet-!TARGET_VERSION!.jar\" (").println();
            bat.append("            echo Targeting version !TARGET_VERSION!, jar downloaded").println();
            bat.append("            set \"SCARLET_VERSION=!TARGET_VERSION!\"").println();
            bat.append("        ) else (").println();
            bat.append("            echo Failed to find version !TARGET_VERSION!, download failed").println();
            bat.append("            if !TARGET_VERSION! EQU !ATTEMPTED_VERSION! (").println();
            bat.append("                echo Loop detected, halting").println();
            bat.append("                goto HALT").println();
            bat.append("            ) else (").println();
            bat.append("                set \"ATTEMPTED_VERSION=!TARGET_VERSION!\"").println();
            bat.append("            )").println();
            bat.append("        )").println();
            bat.append("    )").println();
            bat.append(")").println();
            bat.append(":RUN").println();
            bat.append("if exist \"%CD%\\scarlet.home\" (").println();
            bat.append("    set /p SCARLET_HOME=<%CD%\\scarlet.home").println();
            bat.append("    echo Scarlet home is !SCARLET_HOME!").println();
            bat.append(")").println();
            bat.append("if exist \"%CD%\\scarlet.home.java\" (").println();
            bat.append("    set /p JAVA_HOME=<%CD%\\scarlet.home.java").println();
            bat.append("    echo Using JAVA_HOME=!JAVA_HOME!").println();
            bat.append("    set \"PATH=!JAVA_HOME!\\bin;!JAVA_HOME!\\jre\\bin;!JAVA_HOME!\\jre\\bin\\server;!JAVA_HOME!\\bin\\server;!ORIGINAL_PATH!\"").println();
            bat.append(")").println();
            bat.append("if not exist \"%CD%\\scarlet-!SCARLET_VERSION!.jar\" (").println();
            bat.append("    echo !SCARLET_VERSION!>%CD%\\scarlet.version.target").println();
            bat.append("    echo Scarlet version !SCARLET_VERSION! not found, downloading...").println();
            bat.append("    goto UPDATE").println();
            bat.append(")").println();
            bat.append("echo Running version !SCARLET_VERSION!").println();
            bat.append("set JVM_OPTS=").println();
            bat.append("if exist \"%CD%\\java.options\" (").println();
            bat.append("    for /f \"usebackq delims=\" %%A in (\"%CD%\\java.options\") do (").println();
            bat.append("        set \"LINE=%%A\"").println();
            bat.append("        set \"JVM_OPTS=!JVM_OPTS! !LINE!\"").println();
            bat.append("    )").println();
            bat.append(")").println();
            bat.append("java !JVM_OPTS! -jar scarlet-!SCARLET_VERSION!.jar").println();
            bat.append("if %ERRORLEVEL% EQU 69 goto RUN").println();
            bat.append("if %ERRORLEVEL% EQU 70 goto UPDATE").println();
            bat.append("if %ERRORLEVEL% EQU 0 (").println();
            bat.append("    echo !SCARLET_VERSION!>%CD%\\scarlet.version").println();
            bat.append(")").println();
            bat.append(":HALT").println();
            bat.append("pause").println();
        }
        
        System.out.println("Copying release files");
        copyOne(buildJar, buildPkgJar);
        copyOne(buildBat, buildPkgBat);
        
        System.out.println("Zipping release "+buildZip);
        compressDir(buildPkg, buildZip, FILE_TIME, Deflater.DEFAULT_COMPRESSION);
        
        System.out.println("Copying libraries to "+buildPkgLibs);
        Path m2repo = Paths.get(System.getProperty("user.home"), ".m2", "repository");
        for (String cp0 : mfClasspath0())
        {
            copyOne(m2repo.resolve(cp0), buildPkgLibs+"/"+cp0);
        }
        compressDir(buildPkgLibs, buildLibsZip, FILE_TIME, Deflater.DEFAULT_COMPRESSION);
        
        System.out.println("Zipping release with libraries "+buildAllZip);
        compressDir(buildPkg, buildAllZip, FILE_TIME, Deflater.DEFAULT_COMPRESSION);
        
        System.out.println("Updating pom "+pom);
        updatePom(pom);

        System.out.println("Updating meta "+meta);
        updateMeta(meta);
        
        System.out.println("Done");
    }

    static void updatePom(String pom) throws Exception
    {
        Path pom0 = DIR.resolve(pom);
        Pattern pattern = Pattern.compile("\\A(?<pre>\\s*\\Q<version>\\E\\s*)(?<ver>\\S+)(?<post>\\s*\\Q</version><!--scarlet-version-->\\E)\\z");
        List<String> lines = new ArrayList<>();
        boolean change = false;
        try (BufferedReader br = Files.newBufferedReader(pom0))
        {
            for (String line; (line = br.readLine()) != null;)
            {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find())
                {
                    String pre = matcher.group("pre"),
                           ver = matcher.group("ver"),
                           post = matcher.group("post");
                    if (Scarlet.VERSION.equals(ver))
                    {
                        lines.add(line);
                    }
                    else
                    {
                        System.out.println("  Changing version: "+ver+" -> "+Scarlet.VERSION);
                        lines.add(pre+Scarlet.VERSION+post);
                        change = true;
                    }
                }
                else
                {
                    lines.add(line);
                }
            }
        }
        if (!change)
            return;
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(pom0)))
        {
            lines.forEach(pw::println);
        }
    }

    static void updateMeta(String meta) throws Exception
    {
        Path meta0 = DIR.resolve(meta);
        ScarletMeta scarletMeta;
        try (BufferedReader br = Files.newBufferedReader(meta0))
        {
            scarletMeta = Scarlet.GSON_PRETTY.fromJson(br, ScarletMeta.class);
        }
        boolean change = false;
        {
            if (Scarlet.VERSION.indexOf('-') == -1)
            {
                if (change |= !Objects.equals(Scarlet.VERSION, scarletMeta.latest_release))
                {
                    System.out.println("  latest_release: "+scarletMeta.latest_release+" -> "+Scarlet.VERSION);
                    scarletMeta.latest_release = Scarlet.VERSION;
                }
            }
            if (change |= !Objects.equals(Scarlet.VERSION, scarletMeta.latest_build))
            {
                System.out.println("  latest_build: "+scarletMeta.latest_build+" -> "+Scarlet.VERSION);
                scarletMeta.latest_build = Scarlet.VERSION;
            }
        }
        if (!change)
            return;
        try (BufferedWriter bw = Files.newBufferedWriter(meta0))
        {
            Scarlet.GSON_PRETTY.toJson(scarletMeta, ScarletMeta.class, bw);
            bw.append('\n');
        }
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

    static URL[] classpathURLs()
    {
        return ((URLClassLoader)ClassLoader.getSystemClassLoader()).getURLs();
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
    static List<String> mfClasspath0() throws IOException
    {
        String m2 = "/.m2/repository/";
        List<String> cp0 = new ArrayList<>();
        for (URL url : classpathURLs())
        {
            String string = url.toString();
            int m2i = string.indexOf(m2);
            if (m2i > 9 && !string.endsWith("/"))
            {
                cp0.add(string.substring(m2i + m2.length()));
            }
        }
        return cp0;
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
        Path from0 = DIR.resolve(from);
        copyOne(from0, to);
    }
    static void copyOne(Path from, String to) throws IOException
    {
        Path to0 = DIR.resolve(to);
        Files.createDirectories(to0.getParent());
        Files.copy(from, to0);
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
