package net.sybyline.scarlet.ext;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.ptr.IntByReference;

import net.sybyline.scarlet.util.Location;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.Platform;

public interface VrcLaunch
{

    static void launch(String userId, Location location) throws Exception
    {
        launch(userId, location.world+':'+location.instance);
    }
    static void launch(String userId, String location) throws Exception
    {
        if (Platform.CURRENT.isNT())
            launch_win(userId, location);
        MiscUtils.AWTDesktop.browse(URI.create("vrchat://launch?ref=SybylineNetworkScarlet&id="+location));
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
