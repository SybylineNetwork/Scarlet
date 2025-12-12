package net.sybyline.scarlet.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public interface ScarletURLs
{
    static String vrchatCalendarEvent(String groupId, String eventId)
    {
        return "https://scarlet.sybyline.net/e?" +
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            (groupId.substring(4) + eventId.substring(4))
            .replaceAll("[^0-9a-f]+", "")
            .getBytes(StandardCharsets.UTF_8)
        );
    }
}
