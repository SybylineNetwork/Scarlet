package net.sybyline.scarlet.util;

import java.util.Base64;

public interface ScarletURLs
{
    static String vrchatCalendarEvent(String groupId, String eventId)
    {
        String hex = (groupId.substring(4) + eventId.substring(4))
                .replaceAll("[^0-9a-f]", "");
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++)
            bytes[i] = (byte)Integer.parseUnsignedInt(hex.substring(i * 2, i * 2 + 2), 16);
        return "https://scarlet.sybyline.net/e?" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
