package net.sybyline.scarlet.ext;

import java.io.IOException;
import java.net.HttpURLConnection;

import net.sybyline.scarlet.Scarlet;
import net.sybyline.scarlet.util.Func;

public class ExtendedUserAgent
{
    private static String
        currentGroupId,
        currentUserId,
        extendedUserAgent;
    static
    {
        update();
    }
    public static final Func.V1<IOException, HttpURLConnection>
        init_conn = conn -> conn.setRequestProperty("User-Agent", extendedUserAgent),
        init_conn_disable_redirects = conn -> {
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", extendedUserAgent);
        };
    private static void update()
    {
        extendedUserAgent = Scarlet.USER_AGENT
            + (currentGroupId == null ? "" : ("; Group/" + currentGroupId))
            + (currentUserId == null ? "" : ("; Operator/" + currentUserId))
        ;
    }
    public static void setCurrentGroupId(String groupId)
    {
        currentGroupId = groupId = groupId == null || groupId.trim().isEmpty() ? null : groupId;
        update();
    }
    public static void setCurrentUserId(String userId)
    {
        currentUserId = userId = userId == null || userId.trim().isEmpty() ? null : (userId);
        update();
    }
    public static String getExtendedUserAgent(String userId)
    {
        return extendedUserAgent;
    }
}