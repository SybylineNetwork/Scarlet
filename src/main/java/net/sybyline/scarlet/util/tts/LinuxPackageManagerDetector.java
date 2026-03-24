package net.sybyline.scarlet.util.tts;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive Linux Package Manager Detection and Management.
 * 
 * This class detects available package managers on Linux systems and provides
 * appropriate installation commands for packages like espeak.
 * 
 * Supported package managers:
 * - apt (Debian/Ubuntu)
 * - apt-get (Debian/Ubuntu legacy)
 * - pacman (Arch Linux)
 * - dnf (Fedora)
 * - yum (RHEL/CentOS)
 * - zypper (openSUSE)
 * - apk (Alpine Linux)
 * - xbps-install (Void Linux)
 * - emerge (Gentoo)
 * - eopkg (Solus)
 * - swupd (Clear Linux)
 * - nix-env / nix-shell (NixOS)
 * - flatpak (Universal)
 * - snap (Universal)
 * - brew (Homebrew Linux)
 * - yay (AUR helper)
 * - paru (AUR helper)
 * - pamac (Manjaro/Arch)
 */
public class LinuxPackageManagerDetector {

    private static final Logger LOG = LoggerFactory.getLogger("Scarlet/TTS/PackageManager");

    /**
     * Represents a detected package manager with its capabilities.
     */
    public static class PackageManager {
        public final String name;
        public final String displayName;
        public final String installCommand;
        public final String searchCommand;
        public final String packageName;
        public final boolean requiresSudo;
        public final boolean isAvailable;
        public final String detectionMethod;

        public PackageManager(String name, String displayName, String installCommand, 
                             String searchCommand, String packageName, boolean requiresSudo, 
                             boolean isAvailable, String detectionMethod) {
            this.name = name;
            this.displayName = displayName;
            this.installCommand = installCommand;
            this.searchCommand = searchCommand;
            this.packageName = packageName;
            this.requiresSudo = requiresSudo;
            this.isAvailable = isAvailable;
            this.detectionMethod = detectionMethod;
        }

        public String getFullInstallCommand() {
            if (requiresSudo) {
                return "sudo " + installCommand;
            }
            return installCommand;
        }

        @Override
        public String toString() {
            return String.format("PackageManager[%s, available=%s, cmd=%s]", 
                name, isAvailable, getFullInstallCommand());
        }
    }

    // Cache for detected package managers
    private static List<PackageManager> detectedManagers = null;
    private static PackageManager primaryManager = null;

    /**
     * Definition of known package managers and how to use them.
     */
    private static final Map<String, String[]> PACKAGE_MANAGER_DEFINITIONS = new LinkedHashMap<>();
    
    static {
        // Format: name -> {install_cmd_format, search_cmd_format, package_name, requires_sudo}
        // The install command format uses {pkg} as placeholder for package name
        
        // Debian/Ubuntu family
        PACKAGE_MANAGER_DEFINITIONS.put("apt", new String[]{
            "apt install -y {pkg}", "apt search {pkg}", "espeak", "true"
        });
        PACKAGE_MANAGER_DEFINITIONS.put("apt-get", new String[]{
            "apt-get install -y {pkg}", "apt-cache search {pkg}", "espeak", "true"
        });
        
        // Red Hat family
        PACKAGE_MANAGER_DEFINITIONS.put("dnf", new String[]{
            "dnf install -y {pkg}", "dnf search {pkg}", "espeak", "true"
        });
        PACKAGE_MANAGER_DEFINITIONS.put("yum", new String[]{
            "yum install -y {pkg}", "yum search {pkg}", "espeak", "true"
        });
        
        // Arch Linux family
        PACKAGE_MANAGER_DEFINITIONS.put("pacman", new String[]{
            "pacman -S --noconfirm {pkg}", "pacman -Ss {pkg}", "espeak", "true"
        });
        PACKAGE_MANAGER_DEFINITIONS.put("yay", new String[]{
            "yay -S --noconfirm --needed {pkg}", "yay -Ss {pkg}", "espeak-ng", "false"
        });
        PACKAGE_MANAGER_DEFINITIONS.put("paru", new String[]{
            "paru -S --noconfirm --needed {pkg}", "paru -Ss {pkg}", "espeak-ng", "false"
        });
        PACKAGE_MANAGER_DEFINITIONS.put("pamac", new String[]{
            "pamac install --no-confirm {pkg}", "pamac search {pkg}", "espeak", "false"
        });
        
        // openSUSE
        PACKAGE_MANAGER_DEFINITIONS.put("zypper", new String[]{
            "zypper install -y {pkg}", "zypper search {pkg}", "espeak", "true"
        });
        
        // Alpine Linux
        PACKAGE_MANAGER_DEFINITIONS.put("apk", new String[]{
            "apk add {pkg}", "apk search {pkg}", "espeak", "true"
        });
        
        // Void Linux
        PACKAGE_MANAGER_DEFINITIONS.put("xbps-install", new String[]{
            "xbps-install -y {pkg}", "xbps-query -Rs {pkg}", "espeak", "true"
        });
        
        // Gentoo
        PACKAGE_MANAGER_DEFINITIONS.put("emerge", new String[]{
            "emerge --ask n {pkg}", "emerge --search {pkg}", "app-accessibility/espeak", "true"
        });
        
        // Solus
        PACKAGE_MANAGER_DEFINITIONS.put("eopkg", new String[]{
            "eopkg install -y {pkg}", "eopkg search {pkg}", "espeak", "true"
        });
        
        // Clear Linux
        PACKAGE_MANAGER_DEFINITIONS.put("swupd", new String[]{
            "swupd bundle-add {pkg}", "swupd search {pkg}", "espeak", "false"
        });
        
        // Nix family
        PACKAGE_MANAGER_DEFINITIONS.put("nix-env", new String[]{
            "nix-env -iA nixpkgs.{pkg}", "nix-env -qaP {pkg}", "espeak", "false"
        });
        PACKAGE_MANAGER_DEFINITIONS.put("nix-shell", new String[]{
            "nix-shell -p {pkg}", "nix-env -qaP {pkg}", "espeak", "false"
        });
        PACKAGE_MANAGER_DEFINITIONS.put("nix", new String[]{
            "nix profile install nixpkgs#{pkg}", "nix search nixpkgs {pkg}", "espeak", "false"
        });
        
        // Universal package managers
        PACKAGE_MANAGER_DEFINITIONS.put("flatpak", new String[]{
            "flatpak install -y flathub {pkg}", "flatpak search {pkg}", "espeak", "false"
        });
        PACKAGE_MANAGER_DEFINITIONS.put("snap", new String[]{
            "snap install {pkg}", "snap find {pkg}", "espeak-ng", "true"
        });
        
        // Homebrew Linux
        PACKAGE_MANAGER_DEFINITIONS.put("brew", new String[]{
            "brew install {pkg}", "brew search {pkg}", "espeak", "false"
        });
    }

    /**
     * Detection commands for each package manager.
     * These commands return success (exit 0) if the package manager is available.
     */
    private static final Map<String, String[]> DETECTION_COMMANDS = new LinkedHashMap<>();
    
    static {
        // Format: name -> {binary_check_command, distro_check_command}
        // binary_check: Check if the binary exists
        // distro_check: Additional check specific to distro (optional)
        
        DETECTION_COMMANDS.put("apt", new String[]{"which apt", "test -f /etc/debian_version"});
        DETECTION_COMMANDS.put("apt-get", new String[]{"which apt-get", "test -f /etc/debian_version"});
        DETECTION_COMMANDS.put("dnf", new String[]{"which dnf", "test -f /etc/fedora-release"});
        DETECTION_COMMANDS.put("yum", new String[]{"which yum", "test -f /etc/redhat-release"});
        DETECTION_COMMANDS.put("pacman", new String[]{"which pacman", "test -f /etc/arch-release"});
        DETECTION_COMMANDS.put("yay", new String[]{"which yay", "test -f /etc/arch-release"});
        DETECTION_COMMANDS.put("paru", new String[]{"which paru", "test -f /etc/arch-release"});
        DETECTION_COMMANDS.put("pamac", new String[]{"which pamac", "test -f /etc/arch-release"});
        DETECTION_COMMANDS.put("zypper", new String[]{"which zypper", "grep -qi opensuse /etc/os-release 2>/dev/null"});
        DETECTION_COMMANDS.put("apk", new String[]{"which apk", "test -f /etc/alpine-release"});
        DETECTION_COMMANDS.put("xbps-install", new String[]{"which xbps-install", "test -d /var/db/xbps"});
        DETECTION_COMMANDS.put("emerge", new String[]{"which emerge", "test -d /etc/portage"});
        DETECTION_COMMANDS.put("eopkg", new String[]{"which eopkg", "test -f /var/lib/eopkg"});
        DETECTION_COMMANDS.put("swupd", new String[]{"which swupd", "test -f /usr/share/clear/bundles"});
        DETECTION_COMMANDS.put("nix-env", new String[]{"which nix-env", "test -d /nix"});
        DETECTION_COMMANDS.put("nix-shell", new String[]{"which nix-shell", "test -d /nix"});
        DETECTION_COMMANDS.put("nix", new String[]{"which nix", "test -d /nix"});
        DETECTION_COMMANDS.put("flatpak", new String[]{"which flatpak", null});
        DETECTION_COMMANDS.put("snap", new String[]{"which snap", "test -d /snap"});
        DETECTION_COMMANDS.put("brew", new String[]{"which brew", null});
    }

    /**
     * Priority order for package managers when multiple are available.
     * Lower number = higher priority.
     */
    private static final Map<String, Integer> PRIORITY_ORDER = new LinkedHashMap<>();
    
    static {
        // Native package managers get highest priority
        PRIORITY_ORDER.put("apt", 10);
        PRIORITY_ORDER.put("apt-get", 11);
        PRIORITY_ORDER.put("dnf", 20);
        PRIORITY_ORDER.put("yum", 21);
        PRIORITY_ORDER.put("pacman", 30);
        PRIORITY_ORDER.put("zypper", 40);
        PRIORITY_ORDER.put("apk", 50);
        PRIORITY_ORDER.put("xbps-install", 60);
        PRIORITY_ORDER.put("emerge", 70);
        PRIORITY_ORDER.put("eopkg", 80);
        PRIORITY_ORDER.put("swupd", 90);
        
        // AUR helpers (for Arch-based systems)
        PRIORITY_ORDER.put("yay", 31);
        PRIORITY_ORDER.put("paru", 32);
        PRIORITY_ORDER.put("pamac", 33);
        
        // Nix
        PRIORITY_ORDER.put("nix", 100);
        PRIORITY_ORDER.put("nix-env", 101);
        PRIORITY_ORDER.put("nix-shell", 102);
        
        // Universal package managers (lower priority)
        PRIORITY_ORDER.put("flatpak", 200);
        PRIORITY_ORDER.put("snap", 201);
        PRIORITY_ORDER.put("brew", 210);
    }

    /**
     * Detect all available package managers on the system.
     * Results are cached for subsequent calls.
     */
    public static List<PackageManager> detectAllPackageManagers() {
        if (detectedManagers != null) {
            return detectedManagers;
        }

        LOG.info("Detecting available package managers...");
        detectedManagers = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : PACKAGE_MANAGER_DEFINITIONS.entrySet()) {
            String name = entry.getKey();
            String[] definition = entry.getValue();
            String[] detectionCmds = DETECTION_COMMANDS.get(name);

            if (detectionCmds == null) {
                LOG.debug("No detection command for package manager: {}", name);
                continue;
            }

            // Check if binary exists
            boolean binaryExists = checkCommand(detectionCmds[0]);
            if (!binaryExists) {
                LOG.debug("Package manager binary not found: {}", name);
                continue;
            }

            // Additional distro-specific check if available
            boolean distroMatch = true;
            if (detectionCmds.length > 1 && detectionCmds[1] != null) {
                distroMatch = checkCommand(detectionCmds[1]);
            }

            String detectionMethod = distroMatch ? "full match" : "binary only";

            // Create PackageManager instance
            String displayName = getDisplayName(name);
            String installCommand = definition[0];
            String searchCommand = definition[1];
            String packageName = definition[2];
            boolean requiresSudo = Boolean.parseBoolean(definition[3]);

            PackageManager pm = new PackageManager(
                name, displayName, installCommand, searchCommand,
                packageName, requiresSudo, true, detectionMethod
            );

            detectedManagers.add(pm);
            LOG.info("Detected package manager: {} ({})", name, detectionMethod);
        }

        // Sort by priority
        detectedManagers.sort((a, b) -> {
            int priorityA = PRIORITY_ORDER.getOrDefault(a.name, 999);
            int priorityB = PRIORITY_ORDER.getOrDefault(b.name, 999);
            // Prefer distro-specific matches over binary-only
            if (a.detectionMethod.equals("full match") && !b.detectionMethod.equals("full match")) {
                return -1;
            }
            if (!a.detectionMethod.equals("full match") && b.detectionMethod.equals("full match")) {
                return 1;
            }
            return Integer.compare(priorityA, priorityB);
        });

        if (detectedManagers.isEmpty()) {
            LOG.warn("No package managers detected on this system!");
        }

        return detectedManagers;
    }

    /**
     * Get the primary (best) package manager for this system.
     */
    public static PackageManager getPrimaryPackageManager() {
        if (primaryManager != null) {
            return primaryManager;
        }

        List<PackageManager> managers = detectAllPackageManagers();
        if (managers.isEmpty()) {
            LOG.warn("No package manager available");
            return null;
        }

        // Prefer the first one (highest priority, distro-matched)
        primaryManager = managers.get(0);
        LOG.info("Selected primary package manager: {}", primaryManager.name);
        return primaryManager;
    }

    /**
     * Get a specific package manager by name.
     */
    public static PackageManager getPackageManager(String name) {
        List<PackageManager> managers = detectAllPackageManagers();
        for (PackageManager pm : managers) {
            if (pm.name.equals(name)) {
                return pm;
            }
        }
        return null;
    }

    /**
     * Get the install command for espeak using the primary package manager.
     */
    public static String getEspeakInstallCommand() {
        PackageManager pm = getPrimaryPackageManager();
        if (pm == null) {
            return "sudo apt-get install -y espeak"; // Default fallback
        }
        return pm.getFullInstallCommand().replace("{pkg}", pm.packageName);
    }

    /**
     * Get all available install commands for espeak.
     * Useful for showing a menu to the user.
     */
    public static List<String> getAllEspeakInstallCommands() {
        List<PackageManager> managers = detectAllPackageManagers();
        List<String> commands = new ArrayList<>();
        
        for (PackageManager pm : managers) {
            String cmd = pm.getFullInstallCommand().replace("{pkg}", pm.packageName);
            commands.add(cmd);
        }
        
        return commands;
    }

    /**
     * Check if a command executes successfully.
     */
    private static boolean checkCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            LOG.debug("Command check failed for '{}': {}", command, e.getMessage());
            return false;
        }
    }

    /**
     * Get a user-friendly display name for a package manager.
     */
    private static String getDisplayName(String name) {
        switch (name) {
            case "apt": return "APT (Debian/Ubuntu)";
            case "apt-get": return "APT-GET (Debian/Ubuntu Legacy)";
            case "dnf": return "DNF (Fedora)";
            case "yum": return "YUM (RHEL/CentOS)";
            case "pacman": return "Pacman (Arch Linux)";
            case "yay": return "Yay (AUR Helper)";
            case "paru": return "Paru (AUR Helper)";
            case "pamac": return "Pamac (Manjaro)";
            case "zypper": return "Zypper (openSUSE)";
            case "apk": return "APK (Alpine Linux)";
            case "xbps-install": return "XBPS (Void Linux)";
            case "emerge": return "Portage/Emerge (Gentoo)";
            case "eopkg": return "Eopkg (Solus)";
            case "swupd": return "Swupd (Clear Linux)";
            case "nix-env": return "Nix Env (NixOS)";
            case "nix-shell": return "Nix Shell (NixOS)";
            case "nix": return "Nix (NixOS)";
            case "flatpak": return "Flatpak (Universal)";
            case "snap": return "Snap (Universal)";
            case "brew": return "Homebrew (Linux)";
            default: return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    /**
     * Check if espeak is available in the package manager's repositories.
     */
    public static boolean isEspeakAvailableInRepo(PackageManager pm) {
        if (pm.searchCommand == null || pm.searchCommand.isEmpty()) {
            return true; // Assume available if we can't check
        }

        try {
            String searchCmd = pm.searchCommand.replace("{pkg}", pm.packageName);
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", searchCmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains(pm.packageName.toLowerCase())) {
                        return true;
                    }
                }
            }
            
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            LOG.debug("Repository search failed for {}: {}", pm.name, e.getMessage());
            return true; // Assume available on error
        }
    }

    /**
     * Get recommended alternative package managers for espeak.
     * Some package managers may not have espeak but have alternatives like espeak-ng.
     */
    public static Map<String, String> getEspeakAlternatives() {
        Map<String, String> alternatives = new LinkedHashMap<>();
        
        // For Arch-based systems, espeak-ng is available in AUR
        alternatives.put("yay", "espeak-ng");
        alternatives.put("paru", "espeak-ng");
        alternatives.put("pamac", "espeak-ng");
        
        // Snap has espeak-ng
        alternatives.put("snap", "espeak-ng");
        
        return alternatives;
    }

    /**
     * Clear the detection cache (useful for testing).
     */
    public static void clearCache() {
        detectedManagers = null;
        primaryManager = null;
    }

    /**
     * Print detected package managers (for debugging).
     */
    public static void printDetectedManagers() {
        List<PackageManager> managers = detectAllPackageManagers();
        LOG.info("=== Detected Package Managers ===");
        for (int i = 0; i < managers.size(); i++) {
            PackageManager pm = managers.get(i);
            LOG.info("{}. {} - {} (sudo: {})", 
                i + 1, pm.displayName, pm.getFullInstallCommand().replace("{pkg}", pm.packageName), 
                pm.requiresSudo);
        }
        LOG.info("================================");
    }
}