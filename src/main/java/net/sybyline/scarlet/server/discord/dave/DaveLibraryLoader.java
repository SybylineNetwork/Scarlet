package net.sybyline.scarlet.server.discord.dave;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native;

import net.sybyline.scarlet.util.Platform;
import net.sybyline.scarlet.util.tts.LinuxPackageManagerDetector;

/**
 * Handles loading of the DAVE native library with dependency installation support.
 */
public class DaveLibraryLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(DaveLibraryLoader.class);

    private static final String OPUS_DOWNLOAD_URL = "https://opus-codec.org/downloads/";

    /**
     * Fallback distro→package table used only when LinuxPackageManagerDetector finds nothing.
     */
    private static final String[][] DISTRO_PACKAGES_FALLBACK = {
        {"ubuntu",              "libopus0",        "sudo apt install -y libopus0"},
        {"debian",              "libopus0",        "sudo apt install -y libopus0"},
        {"linuxmint",           "libopus0",        "sudo apt install -y libopus0"},
        {"pop",                 "libopus0",        "sudo apt install -y libopus0"},
        {"elementary",          "libopus0",        "sudo apt install -y libopus0"},
        {"arch",                "opus",            "sudo pacman -S --noconfirm opus"},
        {"manjaro",             "opus",            "sudo pacman -S --noconfirm opus"},
        {"endeavouros",         "opus",            "sudo pacman -S --noconfirm opus"},
        {"garuda",              "opus",            "sudo pacman -S --noconfirm opus"},
        {"fedora",              "opus",            "sudo dnf install -y opus"},
        {"rhel",                "opus",            "sudo dnf install -y opus"},
        {"centos",              "opus",            "sudo dnf install -y opus"},
        {"rocky",               "opus",            "sudo dnf install -y opus"},
        {"almalinux",           "opus",            "sudo dnf install -y opus"},
        {"opensuse",            "libopus0",        "sudo zypper install -y libopus0"},
        {"opensuse-tumbleweed", "libopus0",        "sudo zypper install -y libopus0"},
        {"gentoo",              "media-libs/opus", "sudo emerge --quiet-build=y media-libs/opus"},
        {"slackware",           "opus",            "sudo slackpkg install opus"},
        {"alpine",              "opus",            "sudo apk add opus"},
        {"void",                "opus",            "sudo xbps-install -y opus"},
        {"solus",               "opus",            "sudo eopkg install -y opus"},
        {"nixos",               "opus",            "nix-env -iA nixos.opus"},
    };

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static DaveLibrary load()
    {
        try
        {
            return attemptLoad();
        }
        catch (UnsatisfiedLinkError e)
        {
            LOG.error("Failed to load DAVE native library: {}", e.getMessage());
            String missingLib = identifyMissingLibrary(e.getMessage());
            if (handleMissingDependency(missingLib, e.getMessage()))
            {
                try { return attemptLoad(); }
                catch (UnsatisfiedLinkError e2)
                {
                    LOG.error("Still failed after dependency installation: {}", e2.getMessage());
                    showDialog("Installation Error",
                        "DAVE library still could not be loaded after attempting to install dependencies.\n\nError: " + e2.getMessage(),
                        JOptionPane.ERROR_MESSAGE);
                }
            }
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Library loading
    // -------------------------------------------------------------------------

    private static DaveLibrary attemptLoad()
    {
        try
        {
            Path lib = extractNativeLibrary();
            if (lib != null)
            {
                LOG.info("Loading DAVE library from: {}", lib);
                return Native.load(lib.toString(), DaveLibrary.class);
            }
        }
        catch (Exception e)
        {
            LOG.debug("Could not extract native library from resources: {}", e.getMessage());
        }
        String libName = getLibraryName();
        LOG.info("Loading DAVE library as: {}", libName);
        return Native.load(libName, DaveLibrary.class);
    }

    private static String getLibraryName()
    {
        switch (Platform.CURRENT)
        {
            case $NIX: return "dave";
            default:   return "libdave";
        }
    }

    private static Path extractNativeLibrary()
    {
        String resourcePath = getNativeResourcePath();
        if (resourcePath == null) return null;
        String resourceName = getNativeResourceName();
        URL resource = DaveLibraryLoader.class.getClassLoader().getResource(resourcePath + "/" + resourceName);
        if (resource == null)
        {
            LOG.warn("Native library resource not found: {}/{}", resourcePath, resourceName);
            return null;
        }
        try
        {
            Path tempDir = Files.createTempDirectory("scarlet-native");
            tempDir.toFile().deleteOnExit();
            Path target = tempDir.resolve(resourceName);
            try (java.io.InputStream is = resource.openStream())
            {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            return target;
        }
        catch (IOException e)
        {
            LOG.error("Failed to extract native library", e);
            return null;
        }
    }

    private static String getNativeResourcePath()
    {
        String os   = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String osDir   = os.contains("linux")                    ? "linux"
                       : os.contains("mac") || os.contains("darwin") ? "darwin"
                       : os.contains("win")                      ? "win32"
                       : null;
        String archDir = arch.contains("aarch64") || arch.contains("arm64") ? "aarch64"
                       : arch.contains("x86_64") || arch.contains("amd64") || arch.contains("x64") ? "x86-64"
                       : arch.contains("x86") || arch.contains("i386") || arch.contains("i686") ? "x86"
                       : null;
        return (osDir != null && archDir != null) ? osDir + "-" + archDir : null;
    }

    private static String getNativeResourceName()
    {
        switch (Platform.CURRENT)
        {
            case $NIX: return "libdave.so";
            case XNU:  return "libdave.dylib";
            default:   return "libdave.dll";
        }
    }

    // -------------------------------------------------------------------------
    // Dependency handling
    // -------------------------------------------------------------------------

    private static String identifyMissingLibrary(String msg)
    {
        if (msg.contains("libopus"))   return "libopus";
        if (msg.contains("libm.so"))   return "libm";
        if (msg.contains("libpthread"))return "libpthread";
        if (msg.contains("libstdc++")) return "libstdc++";
        if (msg.contains("libgcc_s"))  return "libgcc_s";
        return "unknown";
    }

    private static boolean handleMissingDependency(String missingLib, String errorMessage)
    {
        if (!Platform.CURRENT.is$nix())
        {
            showDialog("Installation Error",
                "DAVE native library could not be loaded.\n\nError: " + errorMessage,
                JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // Strategy 1: LinuxPackageManagerDetector
        LinuxPackageManagerDetector.PackageManager pm = LinuxPackageManagerDetector.getPrimaryPackageManager();
        if (pm != null)
        {
            String pkg = opusPackageNameFor(pm.name);
            String cmd = pm.getFullInstallCommand().replace(pm.packageName, pkg);
            LOG.info("Using package manager '{}' to install opus (package: {})", pm.name, pkg);
            LinuxDistro distro = detectLinuxDistro();
            return showInstallDialog(missingLib, pkg, cmd, distro);
        }

        // Strategy 2: fallback distro table
        LOG.warn("LinuxPackageManagerDetector found no package manager, falling back to distro table");
        LinuxDistro distro = detectLinuxDistro();
        LOG.info("Detected Linux distribution: {} ({})", distro.name, distro.id);
        String[] info = findPackageForDistroFallback(distro);
        if (info != null)
            return showInstallDialog(missingLib, info[0], info[1], distro);

        showManualInstallDialog(missingLib);
        return false;
    }

    private static String opusPackageNameFor(String pmName)
    {
        switch (pmName)
        {
            case "emerge":                 return "media-libs/opus";
            case "apt": case "apt-get":
            case "zypper":                 return "libopus0";
            default:                       return "opus";
        }
    }

    private static String[] findPackageForDistroFallback(LinuxDistro distro)
    {
        for (String[] e : DISTRO_PACKAGES_FALLBACK)
            if (e[0].equals(distro.id)) return new String[]{e[1], e[2]};
        if (distro.idLike != null)
        {
            for (String like : distro.idLike.split("\\s+"))
                for (String[] e : DISTRO_PACKAGES_FALLBACK)
                    if (e[0].equals(like)) return new String[]{e[1], e[2].replace(e[0], distro.id)};
        }
        if (distro.idLike != null && distro.idLike.contains("debian"))
            return new String[]{"libopus0", "sudo apt install -y libopus0"};
        if (distro.idLike != null && distro.idLike.contains("arch"))
            return new String[]{"opus", "sudo pacman -S --noconfirm opus"};
        return null;
    }

    private static LinuxDistro detectLinuxDistro()
    {
        File osRelease = new File("/etc/os-release");
        if (osRelease.exists())
        {
            try
            {
                String id = null, idLike = null, name = null;
                for (String line : Files.readAllLines(osRelease.toPath()))
                {
                    if (line.startsWith("ID="))       id     = line.substring(3).trim().replace("\"", "");
                    else if (line.startsWith("ID_LIKE=")) idLike = line.substring(8).trim().replace("\"", "");
                    else if (line.startsWith("NAME="))   name   = line.substring(5).trim().replace("\"", "");
                }
                if (id != null) return new LinuxDistro(id, idLike, name);
            }
            catch (IOException e) { LOG.debug("Could not read /etc/os-release: {}", e.getMessage()); }
        }
        if (new File("/etc/arch-release").exists())      return new LinuxDistro("arch",      null, "Arch Linux");
        if (new File("/etc/debian_version").exists())    return new LinuxDistro("debian",    null, "Debian");
        if (new File("/etc/redhat-release").exists())    return new LinuxDistro("rhel",      null, "Red Hat Linux");
        if (new File("/etc/gentoo-release").exists())    return new LinuxDistro("gentoo",    null, "Gentoo");
        if (new File("/etc/slackware-version").exists()) return new LinuxDistro("slackware", null, "Slackware");
        return new LinuxDistro("unknown", null, "Unknown Linux");
    }

    // -------------------------------------------------------------------------
    // Dialogs
    // -------------------------------------------------------------------------

    private static boolean showInstallDialog(String missingLib, String packageName, String installCommand, LinuxDistro distro)
    {
        if (GraphicsEnvironment.isHeadless())
        {
            System.err.println("\n========================================");
            System.err.println("MISSING DEPENDENCY: " + missingLib);
            System.err.println("Distribution: " + distro.name + "  Package: " + packageName);
            System.err.println("Run: " + installCommand);
            System.err.println("Or download from: " + OPUS_DOWNLOAD_URL);
            System.err.println("========================================\n");
            return false;
        }
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        try
        {
            EventQueue.invokeAndWait(() ->
            {
                String msg = String.format(
                    "A required library is missing: %s\n\nDistribution: %s\nRequired package: %s\n\n" +
                    "Would you like to install it now?\n\nCommand: %s",
                    missingLib, distro.name, packageName, installCommand);
                String[] opts = {"Install", "Open Download Page", "Manual Instructions", "Cancel"};
                switch (JOptionPane.showOptionDialog(null, msg, "Missing Dependency - " + missingLib,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[0]))
                {
                    case 0: result.set(runInstallCommand(installCommand)); break;
                    case 1: openBrowser(OPUS_DOWNLOAD_URL); showManualInstructions(packageName, installCommand); break;
                    case 2: showManualInstructions(packageName, installCommand); break;
                }
            });
        }
        catch (Exception e)
        {
            LOG.error("Error showing install dialog", e);
            System.err.println("Run: " + installCommand);
        }
        return result.get();
    }

    private static void showManualInstallDialog(String missingLib)
    {
        String text = "A required library is missing: " + missingLib + "\n\n" +
            "Please install the Opus library for your distribution:\n\n" +
            "  Ubuntu/Debian:  sudo apt install libopus0\n" +
            "  Arch Linux:     sudo pacman -S opus\n" +
            "  Fedora/RHEL:    sudo dnf install opus\n" +
            "  openSUSE:       sudo zypper install libopus0\n" +
            "  Gentoo:         sudo emerge media-libs/opus\n" +
            "  Alpine:         sudo apk add opus\n\n" +
            "Download: " + OPUS_DOWNLOAD_URL;
        if (GraphicsEnvironment.isHeadless()) { System.err.println(text); return; }
        try
        {
            EventQueue.invokeAndWait(() ->
            {
                JTextArea ta = new JTextArea(text);
                ta.setEditable(false); ta.setColumns(50); ta.setRows(12);
                String[] opts = {"Open Download Page", "Close"};
                if (JOptionPane.showOptionDialog(null, new JScrollPane(ta), "Manual Installation Required",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[0]) == 0)
                    openBrowser(OPUS_DOWNLOAD_URL);
            });
        }
        catch (Exception e) { LOG.error("Error showing manual install dialog", e); }
    }

    private static void showManualInstructions(String packageName, String installCommand)
    {
        String text = "To install the missing dependency manually, run:\n\n" + installCommand +
            "\n\nOr install the package '" + packageName + "' using your distribution's package manager.";
        if (GraphicsEnvironment.isHeadless()) { System.err.println(text); return; }
        try
        {
            EventQueue.invokeAndWait(() ->
            {
                JTextArea ta = new JTextArea(text);
                ta.setEditable(false); ta.setColumns(50); ta.setRows(8);
                JOptionPane.showMessageDialog(null, new JScrollPane(ta), "Manual Installation", JOptionPane.INFORMATION_MESSAGE);
            });
        }
        catch (Exception e) { LOG.error("Error showing manual instructions", e); }
    }

    /** Show a simple dialog (or print to stderr if headless). */
    private static void showDialog(String title, String message, int type)
    {
        if (GraphicsEnvironment.isHeadless()) { System.err.println("[DAVE] " + title + ": " + message); return; }
        try
        {
            EventQueue.invokeAndWait(() ->
            {
                JTextArea ta = new JTextArea(message);
                ta.setEditable(false); ta.setColumns(50); ta.setRows(8);
                JOptionPane.showMessageDialog(null, new JScrollPane(ta), title, type);
            });
        }
        catch (Exception e) { LOG.error("Error showing dialog '{}': {}", title, e.getMessage()); }
    }

    // -------------------------------------------------------------------------
    // Installation
    // -------------------------------------------------------------------------

    private static boolean runInstallCommand(String installCommand)
    {
        try
        {
            String[] cmd;
            if (installCommand.startsWith("sudo "))
            {
                String inner = installCommand.substring(5);
                if (isCommandAvailable("pkexec"))
                    cmd = new String[]{"pkexec", "sh", "-c", inner};
                else if (isCommandAvailable("gksudo"))
                    cmd = new String[]{"gksudo", "sh", "-c", inner};
                else if (isCommandAvailable("kdesu"))
                    cmd = new String[]{"kdesu", "-c", inner};
                else if (isCommandAvailable("xterm"))
                    cmd = new String[]{"xterm", "-e", installCommand};
                else if (isCommandAvailable("gnome-terminal"))
                    cmd = new String[]{"gnome-terminal", "--", "sh", "-c", installCommand + "; echo 'Press Enter to close'; read"};
                else if (isCommandAvailable("konsole"))
                    cmd = new String[]{"konsole", "-e", "sh", "-c", installCommand + "; echo 'Press Enter to close'; read"};
                else
                {
                    showDialog("Error", "Could not find a suitable method to run:\n" + installCommand, JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
            else
            {
                cmd = installCommand.split("\\s+");
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream())))
            {
                for (String line; (line = r.readLine()) != null;)
                {
                    output.append(line).append("\n");
                    LOG.info("[install] {}", line);
                }
            }
            int exit = proc.waitFor();
            if (exit == 0)
            {
                showDialog("Installation Complete", "Installation successful!\n\nPlease restart Scarlet for the changes to take effect.", JOptionPane.INFORMATION_MESSAGE);
                return true;
            }
            showDialog("Installation Error", "Installation failed with exit code " + exit + "\n\nOutput:\n" + output, JOptionPane.ERROR_MESSAGE);
            return false;
        }
        catch (Exception e)
        {
            LOG.error("Failed to run install command", e);
            showDialog("Error", "Failed to run installation command:\n" + e.getMessage() + "\n\nPlease run manually:\n" + installCommand, JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static boolean isCommandAvailable(String cmd)
    {
        try { return new ProcessBuilder("which", cmd).redirectErrorStream(true).start().waitFor() == 0; }
        catch (Exception e) { return false; }
    }

    private static void openBrowser(String url)
    {
        try
        {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Desktop.getDesktop().browse(URI.create(url));
            else if (isCommandAvailable("xdg-open"))
                new ProcessBuilder("xdg-open", url).start();
        }
        catch (Exception e) { LOG.error("Failed to open browser", e); }
    }

    // -------------------------------------------------------------------------
    // LinuxDistro helper
    // -------------------------------------------------------------------------

    private static class LinuxDistro
    {
        final String id, idLike, name;
        LinuxDistro(String id, String idLike, String name) { this.id = id; this.idLike = idLike; this.name = name != null ? name : id; }
        @Override public String toString() { return name + " (id=" + id + ", idLike=" + idLike + ")"; }
    }
}
