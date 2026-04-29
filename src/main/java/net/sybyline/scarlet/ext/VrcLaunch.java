package net.sybyline.scarlet.ext;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.ptr.IntByReference;

import net.sybyline.scarlet.util.Location;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.Platform;
import net.sybyline.scarlet.util.Sys;

public interface VrcLaunch
{

    Logger LOG = LoggerFactory.getLogger("Scarlet/VrcLaunch");

    // vrchat://launch?ref=<Organization>&id=<Location>&shortName=<ShortName|SecureName>&attach=<integer>

    static void launch(String userId, Location location) throws Exception
    {
        launch(userId, location.world+':'+location.instance);
    }
    static void launch(String userId, String location) throws Exception
    {
        if (Platform.CURRENT.isNT())
            launch_win(userId, location);
        else
            launch_linux(userId, location);
    }

    /**
     * Launch VRChat on Linux via Steam.
     *
     * Strategy (each step falls back to the next on failure):
     *   1. steam -applaunch 438100 <args>   — most reliable, passes args directly
     *   2. steam steam://rungameid/438100    — works when steam binary is present but arg passing is unreliable
     *   3. xdg-open vrchat://...            — original behaviour, relies on URI handler registration
     */
    static void launch_linux(String userId, String location) throws Exception
    {
        String vrchatUri = "vrchat://launch?ref=SybylineNetworkScarlet&id=" + location;

        // Build the VRChat launch arguments (mirrors what launch_win passes on Windows)
        java.util.List<String> vrcArgs = new java.util.ArrayList<>();
        vrcArgs.add("--no-vr");
        vrcArgs.add("--enable-sdk-log-levels");
        vrcArgs.add("--enable-udon-debug-logging");
        vrcArgs.add("--enable-verbose-logging");
        vrcArgs.add("--log-debug-levels=API;All;Always;AssetBundleDownloadManager;ContentCreator;Errors;NetworkData;NetworkProcessing;NetworkTransport;Warnings");
        if (location != null) { vrcArgs.add(vrchatUri); }
        if (userId   != null) { vrcArgs.add("--profile=" + userId); }

        // Strategy 1: steam -applaunch
        if (Sys.hasInPath("steam"))
        {
            try
            {
                java.util.List<String> cmd = new java.util.ArrayList<>();
                cmd.add("steam");
                cmd.add("-applaunch");
                cmd.add(VrcAppData.VRCHAT_APP_ID);
                cmd.addAll(vrcArgs);
                LOG.info("Launching VRChat via: {}", cmd);
                new ProcessBuilder(cmd).inheritIO().start();
                return;
            }
            catch (Exception ex)
            {
                LOG.warn("steam -applaunch failed, trying steam:// URI: {}", ex.getMessage());
            }

            // Strategy 2: steam steam://rungameid/438100
            try
            {
                LOG.info("Launching VRChat via steam://rungameid/{}", VrcAppData.VRCHAT_APP_ID);
                new ProcessBuilder("steam", "steam://rungameid/" + VrcAppData.VRCHAT_APP_ID)
                    .inheritIO().start();
                return;
            }
            catch (Exception ex)
            {
                LOG.warn("steam rungameid failed, falling back to xdg-open URI: {}", ex.getMessage());
            }
        }
        else
        {
            LOG.warn("steam binary not found in PATH, falling back to URI handler");
        }

        // Strategy 3: original xdg-open / URI handler fallback
        LOG.info("Launching VRChat via URI handler: {}", vrchatUri);
        MiscUtils.AWTDesktop.browse(URI.create(vrchatUri));
    }
    static void launch_win(String userId, String location) throws Exception
    {
        String path;
        {
            IntByReference pType = new IntByReference(),
                    pcbData = new IntByReference(256);
             byte[] buffer = new byte[256];
             int werr = Advapi32.INSTANCE.RegGetValue(WinReg.HKEY_CURRENT_USER, "Software\\VRChat", "", Advapi32.RRF_RT_REG_SZ, pType, buffer, pcbData);
             if (werr == WinError.ERROR_MORE_DATA)
             {
                 buffer = new byte[pcbData.getValue()];
                 werr = Advapi32.INSTANCE.RegGetValue(WinReg.HKEY_CURRENT_USER, "Software\\VRChat", "", Advapi32.RRF_RT_REG_SZ, pType, buffer, pcbData);
             }
             if (werr != WinError.ERROR_SUCCESS)
                 throw new Exception(String.format("Failed to locate VRChat via registry: 0x%08x", werr));
             path = new String(buffer, 0, pcbData.getValue() - 2, StandardCharsets.UTF_16LE);
        }
        StringBuilder sb = new StringBuilder();
        sb
        .append(new File(path, "launch.exe").getAbsolutePath())
        ;
        
        if (location != null)
        {
            sb
            .append(' ')
            .append("vrchat://launch?ref=SybylineNetworkScarlet&id=")
            .append(location)
            ;
        }
        
        if (userId != null)
        {
            sb
            .append(' ')
            .append("--profile=")
            .append(userId)
            ;
        }
        
        sb
        .append(' ')
        .append("--no-vr")
        ;
        
        sb
        .append(' ')
        .append("--enable-sdk-log-levels")
        ;
        
        sb
        .append(' ')
        .append("--enable-udon-debug-logging")
        ;
        
        sb
        .append(' ')
        .append("--enable-verbose-logging")
        ;
        
        sb
        .append(' ')
        .append("--log-debug-levels=\"API;All;Always;AssetBundleDownloadManager;ContentCreator;Errors;NetworkData;NetworkProcessing;NetworkTransport;Warnings\"")
        ;
        
        Runtime.getRuntime().exec(sb.toString());
    }

}
