package net.sybyline.scarlet.util;

import java.util.Locale;

public enum Platform
{
    NT(),
    $NIX(),
    XNU(),
    SUN(),
    OTHER(),
    ;
    Platform()
    {
    }
    public static final Platform CURRENT;
    static
    {
        String s = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (s.contains("win"))
            CURRENT = NT;
        else if (s.contains("linux") || s.contains("unix"))
            CURRENT = $NIX;
        else if (s.contains("mac"))
            CURRENT = XNU;
        else if (s.contains("solaris") || s.contains("sunos"))
            CURRENT = SUN;
        else
            CURRENT = OTHER;
    }
    public boolean isNT()
    {
        return this == NT;
    }
    public boolean is$nix()
    {
        return this == $NIX;
    }
    public boolean isXNU()
    {
        return this == XNU;
    }
    public boolean isSun()
    {
        return this == SUN;
    }
    public boolean isOther()
    {
        return this == OTHER;
    }
}
