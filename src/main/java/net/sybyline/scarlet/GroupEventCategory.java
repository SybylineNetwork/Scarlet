package net.sybyline.scarlet;

import net.sybyline.scarlet.server.discord.DEnum;

public enum GroupEventCategory implements DEnum.DEnumString<GroupEventCategory>
{
    MUSIC("music", "Music", "Music Listening & Parties"),
    GAMING("gaming", "Gaming", "Casual Games & Tournaments"),
    HANGOUT("hangout", "Hangout", "Meetups & Meetings"),
    EXPLORATION("exploration", "Exploring", "World Hopping & Adventuring"),
    AVATARS("avatars", "Avatars", "Avatar Trading & Showcases"),
    FILM_MEDIA("film_media", "Film & Media", "Video Watching & Discussion"),
    DANCE("dance", "Dance", "Practice & Dance-offs"),
    ROLEPLAYING("roleplaying", "Roleplaying", "Roleplay & Discussion"),
    PERFORMANCE("performance", "Performance", "Theatre & Live Concerts"),
    WELLNESS("wellness", "Wellness", "Fitness & Mindfulness"),
    ARTS("arts", "Arts", "Art Creation & Enjoyment"),
    EDUCATION("education", "Education", "Teaching & Learning"),
    OTHER("other", "Other", "Something Else!"),
    ;

    private GroupEventCategory(String value, String display, String info)
    {
        this.value = value;
        this.display = display + " (" + info + ")";
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

    public static GroupEventCategory of(String string)
    {
        return DEnum.of(OTHER, string);
    }

}
