package net.sybyline.scarlet;

import java.util.HashMap;
import java.util.Map;

import net.sybyline.scarlet.server.discord.DEnum;

public enum ScarletPermission implements DEnum.DEnumString<ScarletPermission>
{

    GROUPEX_BANS_MANAGE("groupex-bans-manage", "Override: Manage Bans"),
    ;

    ScarletPermission(String id, String title)
    {
        this.id = id;
        this.title = title;
    }

    @Override
    public String value()
    {
        return this.id;
    }

    @Override
    public String display()
    {
        return this.title;
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
