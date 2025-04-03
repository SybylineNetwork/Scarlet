package net.sybyline.scarlet;

import java.util.HashMap;
import java.util.Map;

public enum ScarletPermission
{

    EVENT_SET_DESCRIPTION("event.set_description", "Set Description"),
    EVENT_SET_TAGS("event.set_tags", "Set Tags"),
    EVENT_USE_REPORT_LINK("event.use_report_link", "Use Report Link"),
    EVENT_SUBMIT_EVIDENCE("event.submit_evidence", "Submit Evidence"),
    EVENT_BAN_USER("event.ban_user", "Ban Users"),
    EVENT_UNBAN_USER("event.unban_user", "Unban Users"),
    EVENT_REDACT("event.redact", "Redact Events"),
    EVENT_UNREDACT("event.unredact", "Unredact Events"),
    CONFIG_IMPORT_WATCHED_GROUPS("config.import_watched_groups", "Submit Evidence"),
    CONFIG_EXPORT_LOGS("config.export_logs", "Export Logs"),
    CONFIG_SERVER_RESTART("config.server_restart", "Restart Server"),
    ;

    ScarletPermission(String id, String title)
    {
        this.id = id;
        this.title = title;
    }

    public final String id, title;

    public String id()
    {
        return this.id;
    }

    public String title()
    {
        return this.title;
    }

    static final Map<String, ScarletPermission> BY_ID = new HashMap<>();
    static
    {
        for (ScarletPermission type : values())
            BY_ID.put(type.id, type);
    }

    public static ScarletPermission of(String id)
    {
        return id == null ? null : BY_ID.get(id.toLowerCase());
    }

}
