package net.sybyline.scarlet.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.sybyline.scarlet.ext.VrcApiStatic;

public interface VrcIds
{
    String P_UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
           P_ID_LEGACY = "[a-zA-Z0-9]{10}",
           P_ID_TYPED_HEADER = "[a-zA-Z]",
           P_ID_TYPED = P_ID_TYPED_HEADER+"_"+P_UUID,
           P_ID = P_ID_LEGACY+"|"+P_ID_TYPED,
           P_ID_USER = P_ID_LEGACY+"|usr_"+P_UUID,
           P_ID_AVATAR = "avtr_"+P_UUID,
           P_ID_FILE = "file_"+P_UUID,
           P_ID_GROUP = "grp_"+P_UUID,
           P_ID_GROUP_CODE_NAME = "[A-Z0-9]{3,6}",
           P_ID_GROUP_CODE_DISC = "[0-9]{4}",
           P_ID_GROUP_CODE = P_ID_GROUP_CODE_NAME+"\\."+P_ID_GROUP_CODE_DISC,
           P_ID_GROUPAUDIT = "gaud_"+P_UUID,
           P_ID_WORLD = "wr?ld_"+P_UUID,
           P_ID_INSTANCE = "[-_a-zA-Z0-9]+(~[a-zA-Z]+(\\([-_a-zA-Z0-9]+\\)))*",
           P_ID_LOCATION = P_ID_WORLD+":"+P_ID_INSTANCE,
           P_ID_LOCATION_SECURE = "[a-z1-8]{8}",
           P_ID_ANY = P_ID+"|"+P_ID_GROUP_CODE+"|"+P_ID_INSTANCE+"|"+P_ID_LOCATION+"|"+P_ID_LOCATION_SECURE;
    
    Pattern id_user = Pattern.compile(P_ID_USER),
            id_avatar = Pattern.compile(P_ID_AVATAR),
            id_file = Pattern.compile(P_ID_FILE),
            id_group = Pattern.compile(P_ID_GROUP),
            id_group_code = Pattern.compile(P_ID_GROUP_CODE),
            id_groupaudit = Pattern.compile(P_ID_GROUPAUDIT),
            id_world = Pattern.compile(P_ID_WORLD),
            id_location = Pattern.compile(P_ID_LOCATION),
            id_location_secure = Pattern.compile(P_ID_LOCATION_SECURE),
            url = Pattern.compile("(https://vrchat\\.com/home|vrcx:/)/(?<type>[a-z]+)/(?<id>"+P_ID+")"),
            url_user = Pattern.compile("(https://vrchat\\.com/home|vrcx:/)/user/(?<id>"+P_ID_USER+")"),
            url_avatar = Pattern.compile("(https://vrchat\\.com/home|vrcx:/)/avatar/(?<id>"+P_ID_AVATAR+")"),
            url_group = Pattern.compile("(https://vrchat\\.com/home|vrcx:/)/group/(?<id>"+P_ID_GROUP+")"),
            url_group_code = Pattern.compile("(https://vrc\\.group/|https://(api\\.)?vrchat\\.(com|cloud)/api/1/groups/redirect/)(?<id>"+P_ID_GROUP_CODE+")"),
            url_world = Pattern.compile("(https://vrchat\\.com/home|vrcx:/)/world/(?<id>"+P_ID_WORLD+")(/info)?"),
            url_location = Pattern.compile("(https://vrchat\\.com/home/launch\\?worldId=|vrcx://world/)(?<world>"+P_ID_WORLD+")(\\&instanceId=|:)(?<instance>"+P_ID_INSTANCE+")(\\&shortName="+P_ID_LOCATION_SECURE+")?"),
            url_location_secure = Pattern.compile("(vrcx://world/)?(https://vrch\\.at/|https://vrchat\\.com/i/)(?<id>"+P_ID_LOCATION_SECURE+")");
    static String resolveUserId(String string)
    {
        if (string == null) return null;
        string = string.trim();
        Matcher m;
        if ((m = id_user.matcher(string)).matches()) return string;
        if ((m = url_user.matcher(string)).matches()) return m.group("id");
        if ((m = id_user.matcher(string)).find()) return m.group();
        return string;
    }
    static String resolveGroupId(String string)
    {
        if (string == null) return null;
        string = string.trim();
        Matcher m;
        if ((m = id_group.matcher(string)).matches()) return string;
        if ((m = url_group.matcher(string)).matches()) return m.group("id");
        if ((m = id_group_code.matcher(string)).matches()) return VrcApiStatic.groupResolveCached(string);
        if ((m = url_group_code.matcher(string)).matches()) return VrcApiStatic.groupResolveCached(m.group("id"));
        if ((m = id_group.matcher(string)).find()) return m.group();
        if ((m = id_group_code.matcher(string)).find()) return VrcApiStatic.groupResolveCached(m.group());
        return string;
    }
    static String resolveWorldId(String string)
    {
        if (string == null) return null;
        string = string.trim();
        Matcher m;
        if ((m = id_world.matcher(string)).matches()) return string;
        if ((m = url_world.matcher(string)).matches()) return m.group("id");
        if ((m = id_world.matcher(string)).find()) return m.group();
        return string;
    }
    static String resolveLocation(String string)
    {
        if (string == null) return null;
        string = string.trim();
        Matcher m;
        if ((m = id_location.matcher(string)).matches()) return string;
        if ((m = url_location.matcher(string)).matches()) return m.group("world")+":"+m.group("instance");
        if ((m = id_location_secure.matcher(string)).matches()) return VrcApiStatic.locationSecureNameResolveCached(string);
        if ((m = url_location_secure.matcher(string)).matches()) return VrcApiStatic.locationSecureNameResolveCached(m.group("id"));
        if ((m = id_location.matcher(string)).find()) return m.group();
        if ((m = id_location_secure.matcher(string)).find()) return VrcApiStatic.locationSecureNameResolveCached(m.group());
        return string;
    }
    static String sanitizeUrl(String id)
    {
        if (id == null) return null;
        Matcher m = url.matcher(id);
        return m.matches() ? m.group("id") : id;
    }
    static String sanitize(String string, Pattern p_id, Pattern p_url)
    {
        if (string == null) return null;
        string = string.trim();
        Matcher m;
        if ((m = p_id.matcher(string)).matches()) return string;
        if ((m = p_url.matcher(string)).matches()) return m.group("id");
        if ((m = p_id.matcher(string)).find()) return m.group();
        return string;
    }
    static String getAsString_user(OptionMapping om)
    {
        return om == null ? null : resolveUserId(om.getAsString());
    }
    static String getAsString_group(OptionMapping om)
    {
        return om == null ? null : resolveGroupId(om.getAsString());
    }
    static String getAsString_world(OptionMapping om)
    {
        return om == null ? null : resolveWorldId(om.getAsString());
    }
    static String getAsString_location(OptionMapping om)
    {
        return om == null ? null : resolveLocation(om.getAsString());
    }
    static String getAsString_generic_noresolve(OptionMapping om)
    {
        return om == null ? null : sanitizeUrl(om.getAsString());
    }

}
