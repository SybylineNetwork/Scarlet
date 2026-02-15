package net.sybyline.scarlet;

import io.github.vrchatapi.model.CalendarEventCategory;

import net.sybyline.scarlet.server.discord.DEnum;

public enum GroupEventCategory implements DEnum.DEnumString<GroupEventCategory>
{
    MUSIC(CalendarEventCategory.MUSIC, "Music", "Music Listening & Parties"),
    GAMING(CalendarEventCategory.GAMING, "Gaming", "Casual Games & Tournaments"),
    HANGOUT(CalendarEventCategory.HANGOUT, "Hangout", "Meetups & Meetings"),
    EXPLORATION(CalendarEventCategory.EXPLORATION, "Exploring", "World Hopping & Adventuring"),
    AVATARS(CalendarEventCategory.AVATARS, "Avatars", "Avatar Trading & Showcases"),
    FILM_MEDIA(CalendarEventCategory.FILM_MEDIA, "Film & Media", "Video Watching & Discussion"),
    DANCE(CalendarEventCategory.DANCE, "Dance", "Practice & Dance-offs"),
    ROLEPLAYING(CalendarEventCategory.ROLEPLAYING, "Roleplaying", "Roleplay & Discussion"),
    PERFORMANCE(CalendarEventCategory.PERFORMANCE, "Performance", "Theatre & Live Concerts"),
    WELLNESS(CalendarEventCategory.WELLNESS, "Wellness", "Fitness & Mindfulness"),
    ARTS(CalendarEventCategory.ARTS, "Arts", "Art Creation & Enjoyment"),
    EDUCATION(CalendarEventCategory.EDUCATION, "Education", "Teaching & Learning"),
    OTHER(CalendarEventCategory.OTHER, "Other", "Something Else!"),
    ;

    private GroupEventCategory(CalendarEventCategory value, String display, String info)
    {
        this.value = value;
        this.display = display + " (" + info + ")";
    }

    final CalendarEventCategory value;
    final String display;

    public CalendarEventCategory model()
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

    public static GroupEventCategory of(String string)
    {
        return DEnum.of(OTHER, string);
    }
    public static GroupEventCategory of(CalendarEventCategory model)
    {
        return model == null ? null : DEnum.of(OTHER, model.getValue());
    }

}
