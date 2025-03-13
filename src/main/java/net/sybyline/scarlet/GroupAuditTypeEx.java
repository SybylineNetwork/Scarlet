package net.sybyline.scarlet;

import java.util.HashMap;
import java.util.Map;


public enum GroupAuditTypeEx
{
    INSTANCE_INACTIVE   ("groupex.instance.inactive"   , "Instance Inactive"     , 0x00_7F7FFF),
    STAFF_JOIN          ("groupex.instance.staff.join" , "Staff Join"            , 0x00_00FF00),
    STAFF_LEAVE         ("groupex.instance.staff.leave", "Staff Leave"           , 0x00_FFFF00),
    VTK_START           ("groupex.instance.vtk"        , "Vote-to-Kick Initiated", 0x00_FF7F00),
    ;

    GroupAuditTypeEx(String id, String title, int color)
    {
        this.id = id;
        this.title = title;
        this.color = color;
    }

    public final String id, title;
    public final int color;

    public String id()
    {
        return this.id;
    }

    public String title()
    {
        return this.title;
    }

    public int color()
    {
        return this.color;
    }

    static final Map<String, GroupAuditTypeEx> BY_ID = new HashMap<>();
    static
    {
        for (GroupAuditTypeEx type : values())
            BY_ID.put(type.id, type);
    }

    public static GroupAuditTypeEx of(String id)
    {
        return id == null ? null : BY_ID.get(id.toLowerCase());
    }

    public static int color(Map<String, Integer> overrides, String id)
    {
        Integer override = overrides.get(id);
        return override != null ? override.intValue() : color(id);
    }

    public static int color(String id)
    {
        GroupAuditTypeEx gat = of(id);
        if (gat != null)
            return gat.color;
        return 0x00_000000;
    }

    public static String title(String id)
    {
        GroupAuditTypeEx gat = of(id);
        if (gat != null)
            return gat.title;
        String title = id.toLowerCase();
        if (title.startsWith("groupex."))
            title = title.substring(8);
        title = title.replace('.', ' ');
        title = GroupAuditType.simpleTitleCase(title);
        return title;
    }

}
