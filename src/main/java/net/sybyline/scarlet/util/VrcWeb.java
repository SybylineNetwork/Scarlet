package net.sybyline.scarlet.util;

public interface VrcWeb
{
    String HELLO = "https://hello.vrchat.com";
    interface About
    {
        String CAREERS = HELLO+"/careers",
               BLOG = HELLO+"/blog",
               PRESS = HELLO+"/press";
    }
    interface Creators
    {
        String GET_AN_AVATAR = HELLO+"/avatar-systems",
               AVATAR_MARKETPLACE = HELLO+"/avatar-marketplace",
               USER_DOCUMENTATION = "https://docs.vrchat.com",
               CREATOR_DOCUMENTATION = "https://creators.vrchat.com",
               SEND_SOME_FEEDBACK = "https://feedback.vrchat.com",
               PATCHNOTES = USER_DOCUMENTATION+"/docs/latest-release",
               FAQ = HELLO+"/developer-faq";
    }
    interface Community
    {
        String GUIDELINES = HELLO+"/community-guidelines",
               CREATOR_GUIDELINES = HELLO+"/creator-guidelines",
               VIDEO_CONTENT = HELLO+"/video-content-guidelines",
               WIKI = "https://wiki.vrchat.com",
               FORUMS = "https://ask.vrchat.com",
               DISCORD = "https://discord.gg/vrchat",
               FAQ = HELLO+"/community-faq";
    }
    String MERCH = HELLO+"/merch-hub",
           HELP = "https://help.vrchat.com",
           VRCPLUS = HELLO+"/vrchatplus",
           TWITCH = "https://www.twitch.tv/vrchat",
           FACEBOOK = "https://www.facebook.com/groups/vrchat",
           X = "https://x.com/VRChat",
           YOUTUBE = "https://www.youtube.com/VRChat",
           TIKTOK = "https://www.tiktok.com/@vrchat";
    interface Home
    {
        String HOME = "https://vrchat.com/home",
               ME = HOME+"/user/me",
               JOIN_FRIENDS = HOME+"/locations",
               GROUPS = HOME+"/groups",
               DOWNLOAD = HOME+"/download",
               DISCOVER_WORLDS = HOME+"/worlds",
               MY_WORLDS = HOME+"/content/worlds",
               AVATARS = HOME+"/avatars",
               FAVORITE_WORLDS = HOME+"/favorites/world",
               FAVORITE_AVATARS = HOME+"/favorites/avatar",
               FAVORITE_FRIENDS = HOME+"/favorites/friend",
               INVENTORY = HOME+"/inventory",
               MARKETPLACE = HOME+"/marketplace",
               ACCOUNT_LINK = HOME+"/accountlink",
               BLOCKS_MUTES = HOME+"/playermoderations",
               SUBSCRIPTIONS = HOME+"/subscriptions",
               MESSAGES = HOME+"/messages";
        static String user(String userId) { return user+userId;}String user = HOME+"/user/";
        static String avatar(String avatarId) { return avatar+avatarId;}String avatar = HOME+"/avatar/";
        static String world(String worldId) { return world+worldId;}String world = HOME+"/world/";
        static String instance(String location) { int $=location.indexOf(':');return instance(location.substring(0,$),location.substring($+1));}
        static String instance(String worldId, String instanceId) { return "https://vrchat.com/home/launch?worldId="+worldId+"&instanceId="+instanceId;}
        static String instanceShort(String shortOrSecureName) { return instanceShort + shortOrSecureName;}String instanceShort = "https://vrch.at/";
        static String instanceShort2(String shortOrSecureName) { return instanceShort2 + shortOrSecureName;}String instanceShort2 = "https://vrchat.com/i/";
        static String group(String groupId) { return group + groupId; }String group = HOME+"/group/";
        static String groupShort(String groupShortCode) { return groupShort + groupShortCode; }String groupShort = "https://vrc.group/";
        static String groupShort2(String groupShortCode) { return groupShort2 + groupShortCode; }String groupShort2 = "https://vrch.at/g/";
        static String groupPosts(String groupId) { return group + groupId + "/posts"; }
        static String groupEvents(String groupId) { return group + groupId + "/events"; }
        static String groupCalendar(String groupId, String eventId) { return group + groupId + "/calendar/" + eventId; }
        static String groupStore(String groupId) { return group + groupId + "/store"; }
        static String groupInstances(String groupId) { return group + groupId + "/instances"; }
        static String groupGalleries(String groupId) { return group + groupId + "/galleries"; }
        static String groupMembers(String groupId) { return group + groupId + "/members"; }
        static String groupInvites(String groupId) { return group + groupId + "/invites"; }
        static String groupSettings(String groupId) { return group + groupId + "/settings"; }
        static String groupSettingsMe(String groupId) { return group + groupId + "/settings/me"; }
        static String groupSettingsRoles(String groupId) { return group + groupId + "/settings/roles"; }
        static String groupSettingsRoles(String groupId, String roleId) { return group + groupId + "/settings/roles/" + roleId; }
        static String groupSettingsRolesNew(String groupId) { return group + groupId + "/settings/roles/new"; }
        static String groupSettingsRolesNewPaid(String groupId) { return group + groupId + "/settings/roles/new/paid"; }
        static String groupSettingsLogs(String groupId) { return group + groupId + "/settings/logs"; }
        static String groupBans(String groupId) { return group + groupId + "/bans"; }
        @Deprecated // The "/home/group/event/:groupId/:eventId" link seems to be sent to amplitude for some reason, even though it doesn't actually resolve in-browser
        static String groupEvent(String groupId, String eventId) { return group + "event/" + groupId + "/" + eventId; }
        @Deprecated // Feature deprecated
        static String groupAgeVerification(String groupId, String betaCode) { return group + groupId + "/ageverification/" + betaCode; }
    }
    String TERMS_OF_SERVICE = HELLO+"/legal",
           PRIVACY_POLICY = HELLO+"/privacy";
    static String file1Data(String fileId) { return file + fileId + "/1/file"; }String file = "https://api.vrchat.cloud/api/1/file/";
}
