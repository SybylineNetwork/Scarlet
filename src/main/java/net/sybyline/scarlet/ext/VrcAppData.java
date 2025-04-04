package net.sybyline.scarlet.ext;

import java.io.File;

import net.sybyline.scarlet.util.Platform;

public interface VrcAppData
{
    File DIR = new File(
        System.getProperty("user.home"),
        (Platform.CURRENT.is$nix() ? ".steam/steam/steamapps/compatdata/438100/pfx/drive_c/users/steamuser/" : "")
            + "AppData/LocalLow/VRChat/VRChat");
}
