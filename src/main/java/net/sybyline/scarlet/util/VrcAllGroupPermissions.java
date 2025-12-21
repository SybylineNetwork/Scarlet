package net.sybyline.scarlet.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.vrchatapi.model.GroupPermissions;

/**
 * IN-MEMORY ONLY!!
 */
public final class VrcAllGroupPermissions
{

    public VrcAllGroupPermissions(JsonObject json)
    {
        if (json == null || json.isEmpty())
            this.allPermissions = Collections.emptyMap();
        else
        {
            this.allPermissions = new HashMap<>();
            json.asMap()
                .forEach((groupId, array) ->
                    this.allPermissions.put(
                    groupId, 
                    array
                        .getAsJsonArray()
                        .asList()
                        .stream()
                        .map(JsonElement::getAsString)
                        .map(VRCHAT_PERMS::get)
                        .filter(Objects::nonNull)
                        .mapToInt(GroupPermissions::ordinal)
                        .mapToLong($ -> 1L << $)
                        .reduce(0L, Long::sum)
                    )
                );
        }
    }

    private static final long ALL_SHIFT = 1L << GroupPermissions.group_all.ordinal();
    private final Map<String, Long> allPermissions;

    public boolean has(String groupId, GroupPermissions perm)
    {
        return (this.allPermissions.getOrDefault(groupId, 0L).longValue() & (ALL_SHIFT | (1L << perm.ordinal()))) != 0L;
    }

    private static final Map<String, GroupPermissions> VRCHAT_PERMS = new HashMap<>();
    static
    {
        GroupPermissions[] perms = GroupPermissions.values();
        if (perms.length > Long.SIZE)
            throw new Error("No longer able to fit group permissions in 64 bits");
        for (GroupPermissions perm : perms)
            VRCHAT_PERMS.put(perm.getValue(), perm);
    }

}
