package net.sybyline.scarlet;

import io.github.vrchatapi.model.CalendarEventPlatform;

import net.sybyline.scarlet.server.discord.DEnum;

public enum GroupEventPlatform implements DEnum.DEnumString<GroupEventPlatform>
{
    ANDRIOD(CalendarEventPlatform.ANDROID, "Android"),
    IOS(CalendarEventPlatform.IOS, "iOS"),
    WINDOWS(CalendarEventPlatform.STANDALONEWINDOWS, "Windows"),
    ;

    private GroupEventPlatform(CalendarEventPlatform value, String display)
    {
        this.value = value;
        this.display = display;
    }

    final CalendarEventPlatform value;
    final String display;

    public CalendarEventPlatform model()
    {
        return this.value;
    }

    @Override
    public String value()
    {
        return this.value.getValue();
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
    public static GroupEventPlatform of(CalendarEventPlatform model)
    {
        if (model == null)
            return null;
        switch (model)
        {
        case ANDROID: return ANDRIOD;
        case IOS: return IOS;
        case STANDALONEWINDOWS: return WINDOWS;
        default: return null;
        }
    }

}
