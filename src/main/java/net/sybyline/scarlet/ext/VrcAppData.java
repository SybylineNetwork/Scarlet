package net.sybyline.scarlet.ext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.util.Platform;

public interface VrcAppData
{
    Logger LOG = LoggerFactory.getLogger("Scarlet/VrcAppData");

    // VRChat's Steam App ID
    String VRCHAT_APP_ID = "438100";

    // Proton compatdata relative path (same for all Steam libraries)
    String PROTON_PREFIX = "steamapps/compatdata/" + VRCHAT_APP_ID + "/pfx/drive_c/users/steamuser/";

    File DIR  = resolve("AppData/LocalLow/VRChat/VRChat");
    File TEMP = resolve("AppData/Local/Temp/VRChat/VRChat");

    /**
     * Resolves a VRChat appdata path.
     * On Linux: tries each Steam library root in order, falling back to ~/.steam/steam.
     * On Windows: resolves directly from user.home.
     */
    static File resolve(String relative)
    {
        if (!Platform.CURRENT.is$nix())
            return new File(System.getProperty("user.home"), relative);

        // Try Steam library detection first
        File detected = findInSteamLibraries(relative);
        if (detected != null)
        {
            LOG.info("Resolved VRChat path via Steam library detection: {}", detected);
            return detected;
        }

        // Fallback: original hardcoded path
        File fallback = new File(
            System.getProperty("user.home"),
            ".steam/steam/" + PROTON_PREFIX + relative);
        LOG.warn("Could not detect Steam library for VRChat, falling back to default path: {}", fallback);
        return fallback;
    }

    /**
     * Searches all Steam library folders for the VRChat compatdata path.
     * Returns the first match found, or null if none.
     */
    static File findInSteamLibraries(String relative)
    {
        List<File> libraryRoots = detectSteamLibraryRoots();
        for (File root : libraryRoots)
        {
            File candidate = new File(root, PROTON_PREFIX + relative);
            LOG.debug("Checking Steam library candidate: {}", candidate);
            if (candidate.exists())
                return candidate;
        }
        return null;
    }

    /**
     * Returns all Steam library root directories by parsing libraryfolders.vdf.
     * Always includes the primary Steam home as the first entry.
     * Falls back gracefully if the VDF cannot be read.
     */
    static List<File> detectSteamLibraryRoots()
    {
        String home = System.getProperty("user.home");
        List<File> roots = new ArrayList<>();

        // Steam can live in several places on Linux; try most common first
        String[] steamHomes = {
            home + "/.steam/steam",
            home + "/.local/share/Steam",
            home + "/.var/app/com.valvesoftware.Steam/data/Steam", // Flatpak Steam
        };

        File vdf = null;
        File primarySteamHome = null;
        for (String steamHome : steamHomes)
        {
            File candidate = new File(steamHome, "steamapps/libraryfolders.vdf");
            if (candidate.exists())
            {
                vdf = candidate;
                primarySteamHome = new File(steamHome);
                break;
            }
        }

        // Always add the primary Steam home as first root (original hardcoded fallback)
        if (primarySteamHome != null)
            roots.add(primarySteamHome);
        else
            roots.add(new File(home, ".steam/steam"));

        if (vdf == null)
        {
            LOG.warn("Could not find Steam libraryfolders.vdf, using default Steam path only");
            return roots;
        }

        LOG.info("Parsing Steam library folders from: {}", vdf);
        try (BufferedReader reader = new BufferedReader(new FileReader(vdf)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                line = line.trim();
                // VDF line format:  "path"   "/path/to/library"
                if (line.startsWith("\"path\""))
                {
                    String[] parts = line.split("\"");
                    // parts[1]="path", parts[3]=value
                    if (parts.length >= 4)
                    {
                        File libraryRoot = new File(parts[3]);
                        if (libraryRoot.exists() && !roots.contains(libraryRoot))
                        {
                            LOG.info("Found additional Steam library root: {}", libraryRoot);
                            roots.add(libraryRoot);
                        }
                    }
                }
            }
        }
        catch (IOException ex)
        {
            LOG.warn("Failed to parse Steam libraryfolders.vdf: {}", ex.getMessage());
        }

        return roots;
    }
}
