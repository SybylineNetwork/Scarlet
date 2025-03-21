package net.sybyline.scarlet.util;

import java.util.Base64;
import java.util.regex.Pattern;

public interface VRChatHelpDeskURLs
{

    static String newSupportRequest(String requesterEmail, SupportCategory supportCategory, String requesterUserId, SupportPlatform supportPlatform, String subject, String description)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("https://help.vrchat.com/hc/en-us/requests/new?ticket_form_id=360006750513");
        if (requesterEmail != null)
            sb.append("&tf_anonymous_requester_email=").append(escape(requesterEmail, false));
        if (supportCategory != null)
            sb.append("&tf_1500001394041=").append(escape(supportCategory.value, false));
        if (requesterUserId != null)
            sb.append("&tf_360057451993=").append(escape(requesterUserId, false));
        if (supportPlatform != null)
            sb.append("&tf_1500001394021=").append(escape(supportPlatform.value, false));
        if (subject != null)
            sb.append("&tf_subject=").append(escape(subject, false));
        if (description != null)
            sb.append("&tf_description=").append(escape(description, true));
        return sb.toString();
    }
    public enum SupportCategory
    {
        AGE_VERIFICATION_ISSUES("age_verification_issues", "Age Verification Issues"),
        APPLICATION_ISSUES("application_issues", "I'm having trouble launching the VRChat application"),
        ACCOUNT_SUPPORT("account_support", "My account is experiencing issues"),
        TWO_FACTOR_SUPPORT("2fa_support", "I've lost access to my 2-Factor Authentication codes"),
        WEBSITE_SUPPORT("website_support", "I need help with the VRChat website"),
        ACCOUNT_CREATION_ISSUES("i_can_t_create_a_vrchat_account", "I'm having issues creating an account"),
        APPLICATION_CRASHING("the_vrchat_application_keeps_crashing", "VRChat keeps crashing"),
        CONNECTION_ISSUES("connection_issues", "Connection issues"),
        PERFORMANCE_FPS_ISSUES("performance/fps_issues", "Performance / FPS issues"),
        LINKING_ISSUES("issues_linking_with_steam/oculus/viveport", "Issues merging with platform account"),
        AUDIO_VOICE_ISSUES("i_m_having_problems_with_audio/voice", "I'm having problems with audio / voice"),
        VIDEO_PLAYER_ISSUES("video_players_aren_t_working", "Video players aren't working"),
        FAVORITES_ISSUES("issues_with_favorites", "Issues with favorites"),
        INVITES_NOTIFICATIONS_ISSUES("issues_with_invites/notifications", "Issues with invites/notifications"),
        STUCK_IN_AVATAR("i_m_stuck_in_an_avatar", "I'm stuck in an avatar"),
        OTHER_USERS_INVISIBLE("i_can_t_see_other_users", "I can't see other users"),
        VRCHAT_SDK_ISSUES("i_m_having_issues_with_the_vrchat_sdk", "I'm having issues with the SDK / VCC"),
        VRCHAT_PLUS_ISSUES("i_m_having_issues_with_a_vrchat_plus_subscription", "I'm having issues with my VRChat+ subscription"),
        CREATOR_ECONOMY_ISSUES("ce_support", "I need help with the Creator Economy"),
        DATA_PRIVACY_REQUEST("data_privacy_request", "I want to invoke my rights concerning my personal information under applicable law (GDPR, CCPA, etc.)"),
        ;
        SupportCategory(String value, String label)
        {
            this.value = value;
            this.label = label;
        }
        public final String value, label;
    }
    public enum SupportPlatform
    {
        STEAM("steam", "Steam"),
        OCULUS_PC("oculus__pc_", "Meta (PC)"),
        QUEST_2("oculus_quest_2", "Meta Quest 2"),
        QUEST_3("meta_quest_3", "Meta Quest 3"),
        QUEST_3S("meta_quest_3s", "Meta Quest 3S"),
        ANDROID("android__alpha", "Android (Alpha)"),
        PICO("pico", "Pico"),
        VIVEPORT("viveport", "Viveport"),
        GEFORCE_NOW("geforce_now", "GeForce NOW"),
        OTHER("other", "Other"),
        ;
        SupportPlatform(String value, String label)
        {
            this.value = value;
            this.label = label;
        }
        public final String value, label;
    }

    static final Pattern ID_PATTERN = Pattern.compile("[a-zA-Z]+_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    static String shortId(String id)
    {
        if (id == null || !ID_PATTERN.matcher(id).matches())
            return id;
        byte[] bytes = new byte[16];
        id.substring(id.indexOf('_') + 1).replaceAll("-", "");
        for (int i = 0; i < 16; i++)
            bytes[i] = Byte.decode("0x"+id.substring(i*2, i*2+2));
        return Base64.getUrlEncoder().encodeToString(bytes);
    }
    static String newUserModerationRequest_shortened(String requesterEmail, String requesterUserId, String targetUserId, String subject, String description, String[] modTags, String appName, String appVersion, String groupId, String auditId)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("https://sybylinenetwork.github.io/vrchdur.html");
        
        if (requesterEmail != null)
            sb.append((sb.length() == 46 ? '?' : '&')).append("e=").append(escape(requesterEmail, false));
        if (requesterUserId != null)
            sb.append((sb.length() == 46 ? '?' : '&')).append("r=").append(shortId(requesterUserId));
        if (targetUserId != null)
            sb.append((sb.length() == 46 ? '?' : '&')).append("t=").append(shortId(targetUserId));
        if (subject != null)
            sb.append((sb.length() == 46 ? '?' : '&')).append("s=").append(escape(subject, false));
        if (description != null)
            sb.append((sb.length() == 46 ? '?' : '&')).append("d=").append(escape(description, false));
        if (modTags != null && modTags.length > 0)
        {
            int len = modTags.length;
            sb.append((sb.length() == 46 ? '?' : '&')).append("M=").append(escape(modTags[0], false));
            for (int i = 1; i < len; i++)
                sb.append("`").append(escape(modTags[i], false));
        }
        if (appName != null)
            sb.append((sb.length() == 46 ? '?' : '&')).append("N=").append(escape(appName, false));
        if (appVersion != null)
            sb.append((sb.length() == 46 ? '?' : '&')).append("V=").append(escape(appVersion, false));
        if (groupId != null)
            sb.append((sb.length() == 46 ? '?' : '&')).append("G=").append(shortId(groupId));
        if (auditId != null)
            sb.append((sb.length() == 46 ? '?' : '&')).append("A=").append(shortId(auditId));
        return sb.toString();
    }
    static String newModerationRequest(String requesterEmail, ModerationCategory moderationCategory, String requesterUserId, String targetUserId, String subject, String description)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("https://help.vrchat.com/hc/en-us/requests/new?ticket_form_id=1500000182242");
        if (requesterEmail != null)
            sb.append("&tf_anonymous_requester_email=").append(escape(requesterEmail, false));
        if (moderationCategory != null)
            sb.append("&tf_360056455174=").append(escape(moderationCategory.value, false));
        if (requesterUserId != null)
            sb.append("&tf_360057451993=").append(escape(requesterUserId, false));
        if (targetUserId != null)
            sb.append("&tf_1500001445142=").append(escape(targetUserId, false));
        if (subject != null)
            sb.append("&tf_subject=").append(escape(subject, false));
        if (description != null)
            sb.append("&tf_description=").append(escape(description, true));
        return sb.toString();
    }
    public enum ModerationCategory
    {
        USER_REPORT("user_report", "User Report"),
        BAN_APPEAL("ban_appeal", "Ban Appeal"),
        ;
        ModerationCategory(String value, String label)
        {
            this.value = value;
            this.label = label;
        }
        public final String value, label;
    }

    static String newSecurityRequest(String requesterEmail, String subject, String vulnerability, String reproduce, String impact, String description, Boolean confirmation)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("https://help.vrchat.com/hc/en-us/requests/new?ticket_form_id=1500001130621");
        if (requesterEmail != null)
            sb.append("&tf_anonymous_requester_email=").append(escape(requesterEmail, false));
        if (subject != null)
            sb.append("&tf_subject=").append(subject);
        if (vulnerability != null)
            sb.append("&tf_14871541233043=").append(escape(vulnerability, false));
        if (reproduce != null)
            sb.append("&tf_14871567333267=").append(escape(reproduce, false));
        if (impact != null)
            sb.append("&tf_14871574761875=").append(escape(impact, false));
        if (description != null)
            sb.append("&tf_description=").append(escape(description, true));
        if (confirmation != null)
            sb.append("&tf_1900000428585=").append(confirmation);
        return sb.toString();
    }

    static String newRecoveryRequest(String requesterEmail, Boolean confirmation, String requesterUserId, String subject, String description, String recoveryToken)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("https://help.vrchat.com/hc/en-us/requests/new?ticket_form_id=1900000725685");
        if (requesterEmail != null)
            sb.append("&tf_anonymous_requester_email=").append(escape(requesterEmail, false));
        if (confirmation != null)
            sb.append("&tf_1900003404965=").append(confirmation);
        if (requesterUserId != null)
            sb.append("&tf_360057451993=").append(escape(requesterUserId, false));
        if (subject != null)
            sb.append("&tf_subject=").append(escape(subject, false));
        if (description != null)
            sb.append("&tf_description=").append(escape(description, true));
        if (recoveryToken != null)
            sb.append("&tf_1900004384185=").append(escape(recoveryToken, false));
        return sb.toString();
    }

    static String escape(String string, boolean html)
    {
        if (string == null)
            return null;
        if (html)
            string = string.replaceAll("\\R", "<br>");
        return URLs.encode(string);
    }

}
