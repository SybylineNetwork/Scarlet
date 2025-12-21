package net.sybyline.scarlet;

import net.sybyline.scarlet.server.discord.DEnum;

public enum GroupEventPlatform implements DEnum.DEnumString<GroupEventPlatform>
{
    ANDRIOD("android", "Android"),
    IOS("ios", "iOS"),
    WINDOWS("standalonewindows", "Windows"),
    ;

    private GroupEventPlatform(String value, String display)
    {
        this.value = value;
        this.display = display;
    }

    final String value, display;

    @Override
    public String value()
    {
        return this.value;
    }

    @Override
    public String display()
    {
        return this.display;
    }

    public static GroupEventPlatform of(String value)
    {
        if (value == null)
            return null;
        switch (value)
        {
        case "android": return ANDRIOD;
        case "ios": return IOS;
        case "standalonewindows": return WINDOWS;
        default: return null;
        }
    }

}
