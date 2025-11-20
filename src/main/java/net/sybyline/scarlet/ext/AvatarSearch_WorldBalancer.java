package net.sybyline.scarlet.ext;

import java.io.IOException;

import net.sybyline.scarlet.util.HttpURLInputStream;

/**
 * https://worldbalancer.com/about-us
 */
public interface AvatarSearch_WorldBalancer
{

    public static final String API_ROOT = "https://avatar.worldbalancer.com";

    public static class PutAvatarExternalRequest
    {
        public PutAvatarExternalRequest(String id, String userid)
        {
            this.id = id;
            this.userid = userid;
        }
        public PutAvatarExternalRequest()
        {
        }
        public String id;
        public String userid;
    }

    static boolean putAvatarExternal(String id, String userid)
    {
        try (HttpURLInputStream in = HttpURLInputStream.post(API_ROOT+"/v2/vrchat/avatars/store/putavatarEx", HttpURLInputStream.writeAsJson(null, null, PutAvatarExternalRequest.class, new PutAvatarExternalRequest(id, userid))))
        {
        }
        catch (IOException ioex)
        {
            ioex.printStackTrace();
            return false;
        }
        return true;
    }

}
