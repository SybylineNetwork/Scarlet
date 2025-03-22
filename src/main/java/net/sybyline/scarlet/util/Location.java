package net.sybyline.scarlet.util;

import java.util.Objects;

import io.github.vrchatapi.model.InstanceType;
import io.github.vrchatapi.model.Region;

public class Location
{

    public static final Location
        NULL      = new Location(""),
        OFFLINE   = new Location("offline"),
        TRAVELING = new Location("traveling"),
        PRIVATE   = new Location("private");

    public static boolean isSpecial(String string)
    {
        if (string != null) switch (string)
        {
        default         : return false;
        case ""         :
        case "offline"  :
        case "traveling":
        case "private"  :
        }
        return true;
    }
    public static boolean isConcrete(String string)
    {
        if (string != null) switch (string)
        {
        default         : return true;
        case ""         :
        case "offline"  :
        case "traveling":
        case "private"  :
        }
        return false;
    }

    public static Location of(String string)
    {
        return of(null, string);
    }
    public static Location of(String world, String instance)
    {
        if (instance == null)
            return NULL;
        else switch (instance)
        {
        case ""         : return NULL;
        case "offline"  : return OFFLINE;
        case "traveling": return TRAVELING;
        case "private"  : return PRIVATE;
        }
        String name = null;
        Type type = null;
        String owner = null;
        boolean ageGate = false;
        Region region = null;
        String nonce = null;
        boolean canRequestInvite = false;
        int colon = instance.indexOf(':');
        if (colon != -1)
        {
            if (world != null)
                throw new IllegalArgumentException("duplicate worldId in location string `"+instance+"`");
            world = instance.substring(0, colon);
            instance = instance.substring(colon + 1);
        }
        for (String entry : instance.split("~"))
        {
            if (entry.startsWith("group("))
            {
                if (type != null || owner != null)
                    throw new IllegalArgumentException("duplicate types in location string `"+instance+"`");
                owner = entry.substring(6, entry.length() - 1);
            }
            else if (entry.startsWith("groupAccessType("))
            {
                if (type != null)
                    throw new IllegalArgumentException("duplicate types in location string `"+instance+"`");
                String gtype = entry.substring(16, entry.length() - 1);
                switch (gtype)
                {
                case  "public": type = Type.GROUP_PUBLIC; break;
                case    "plus": type = Type.GROUP_PLUS;   break;
                case "members": type = Type.GROUP;        break;
                default: throw new IllegalArgumentException("Unknown groupAccessType `"+gtype+"` in location string `"+instance+"`");
                }
            }
            else if (entry.startsWith("hidden("))
            {
                if (type != null || owner != null)
                    throw new IllegalArgumentException("duplicate types in location string `"+instance+"`");
                type = Type.FRIENDS_PLUS;
                owner = entry.substring(7, entry.length() - 1);
            }
            else if (entry.startsWith("friends("))
            {
                if (type != null || owner != null)
                    throw new IllegalArgumentException("duplicate types in location string `"+instance+"`");
                type = Type.FRIENDS;
                owner = entry.substring(8, entry.length() - 1);
            }
            else if (entry.startsWith("private("))
            {
                if (type != null || owner != null)
                    throw new IllegalArgumentException("duplicate types in location string `"+instance+"`");
                type = Type.INVITE;
                owner = entry.substring(8, entry.length() - 1);
            }
            else if (entry.equals("ageGate"))
            {
                if (ageGate)
                    throw new IllegalArgumentException("duplicate ageGate in location string `"+instance+"`");
                ageGate = true;
            }
            else if (entry.equals("canRequestInvite"))
            {
                if (canRequestInvite)
                    throw new IllegalArgumentException("duplicate canRequestInvite in location string `"+instance+"`");
                canRequestInvite = true;
            }
            else if (entry.startsWith("region("))
            {
                if (region != null)
                    throw new IllegalArgumentException("duplicate region in location string `"+instance+"`");
                region = Region.fromValue(entry.substring(7, entry.length() - 1));
            }
            else if (entry.startsWith("nonce("))
            {
                if (nonce != null)
                    throw new IllegalArgumentException("duplicate nonce in location string `"+instance+"`");
                nonce = entry.substring(6, entry.length() - 1);
            }
            else
            {
                if (name != null)
                    continue;//throw new IllegalArgumentException("duplicate name in location string `"+instance+"`");
                name = entry;
            }
        }
        if (name == null)
        {
            throw new IllegalArgumentException("missing name in location string `"+instance+"`");
        }
        else if (type == null)
        {
            if (owner != null)
                throw new IllegalArgumentException("missing owner groupId in location string `"+instance+"`");
            type = Type.PUBLIC;
        }
        else if (type == Type.INVITE)
        {
            if (canRequestInvite)
            {
                type = Type.INVITE_PLUS;
            }
        }
        return of(world, instance, name, type, owner, ageGate, region, nonce);
    }

    public static Location of(String world, String name, Type type, String owner, boolean ageGate, Region region, String nonce)
    {
        return of(world, null, name, type, owner, ageGate, region, nonce);
    }

    static Location of(String world, String instance, String name, Type type, String owner, boolean ageGate, Region region, String nonce)
    {
        if (world == null)
            throw new NullPointerException("world");
        if (name == null)
            throw new NullPointerException("name");
        if (type == null)
            throw new NullPointerException("type");
        else switch (type)
        {
        case PUBLIC:
            if (owner != null)
                throw new IllegalArgumentException("Public instances have no owner");
        break;
        case GROUP_PUBLIC:
            if (owner == null)
                throw new IllegalArgumentException("Group Public instances must have an owner");
        break;
        case GROUP_PLUS:
            if (owner == null)
                throw new IllegalArgumentException("Group+ instances must have an owner");
        break;
        case GROUP:
            if (owner == null)
                throw new IllegalArgumentException("Group instances must have an owner");
        break;
        case FRIENDS_PLUS:
            if (owner == null)
                throw new IllegalArgumentException("Friends+ instances must have an owner");
        break;
        case FRIENDS:
            if (owner == null)
                throw new IllegalArgumentException("Friends instances must have an owner");
        break;
        case INVITE_PLUS:
            if (owner == null)
                throw new IllegalArgumentException("Invite+ instances must have an owner");
        break;
        case INVITE:
            if (owner == null)
                throw new IllegalArgumentException("Invite instances must have an owner");
        break;
        default:
            throw new IllegalArgumentException("Unknown type `"+type+"`");
        }
        if (instance == null)
        {
            StringBuilder buf = new StringBuilder();
            buf.append(name);
            switch (type)
            {
            default:
            case PUBLIC:
                ; // noop
            break;
            case GROUP_PUBLIC:
                buf.append("~group(").append(owner).append(")~groupAccessType(public)");
            break;
            case GROUP_PLUS:
                buf.append("~group(").append(owner).append(")~groupAccessType(plus)");
            break;
            case GROUP:
                buf.append("~group(").append(owner).append(")~groupAccessType(members)");
            break;
            case FRIENDS_PLUS:
                buf.append("~hidden(").append(owner).append(')');
            break;
            case FRIENDS:
                buf.append("~friends(").append(owner).append(')');
            break;
            case INVITE_PLUS:
                buf.append("~private(").append(owner).append(")~canRequestInvite");
            break;
            case INVITE:
                buf.append("~private(").append(owner).append(')');
            break;
            }
            if (ageGate)
                buf.append("~ageGate");
            if (region != null)
                buf.append("~region(").append(region.getValue()).append(')');
            if (nonce != null)
                buf.append("~nonce(").append(nonce).append(')');
            instance = buf.toString();
        }
        return new Location(world, instance, name, type, owner, ageGate, region, nonce);
    }

    Location(String specialName)
    {
        this(null, null, specialName, null, null, false, null, null);
    }
    Location(String world, String instance, String name, Type type, String owner, boolean ageGate, Region region, String nonce)
    {
        this.world = world;
        this.instance = instance;
        this.name = name;
        this.type = type;
        this.owner = owner;
        this.ageGate = ageGate;
        this.region = region;
        this.nonce = nonce;
    }

    public final String world;
    public final String instance;
    public final String name;
    public final Type type;
    public static enum Type
    {
        PUBLIC("Public", InstanceType.PUBLIC, null),
        GROUP_PUBLIC("Group Public", InstanceType.GROUP, null),
        GROUP_PLUS("Group+", InstanceType.GROUP, null),
        GROUP("Group", InstanceType.GROUP, null),
        FRIENDS_PLUS("Friends+", InstanceType.HIDDEN, null),
        FRIENDS("Friends", InstanceType.FRIENDS, null),
        INVITE_PLUS("Invite+", InstanceType.PRIVATE, Boolean.TRUE),
        INVITE("Invite", InstanceType.PRIVATE, null),
        ;
        Type(String display, InstanceType apiInstanceType, Boolean apiCanRequestInvite)
        {
            this.display = display;
            this.apiInstanceType = apiInstanceType;
            this.apiCanRequestInvite = apiCanRequestInvite;
        }
        public final String display;
        public final InstanceType apiInstanceType;
        public final Boolean apiCanRequestInvite;
    }
    public final String owner;
    public final boolean ageGate;
    public final Region region;
    public final String nonce;

    private StringBuilder toString0(StringBuilder buf, boolean asUrl)
    {
        if (asUrl)
        {
            buf.append("https://vrchat.com/home/launch?worldId=").append(this.world).append("&instanceId=");
        }
        else
        {
            if (this.world != null)
            {
                buf.append(this.world).append(':');
            }
        }
        buf.append(this.name);
        switch (this.type)
        {
        default:
        case PUBLIC:
            ; // noop
        break;
        case GROUP_PUBLIC:
            buf.append("~group(").append(this.owner).append(")~groupAccessType(public)");
        break;
        case GROUP_PLUS:
            buf.append("~group(").append(this.owner).append(")~groupAccessType(plus)");
        break;
        case GROUP:
            buf.append("~group(").append(this.owner).append(")~groupAccessType(members)");
        break;
        case FRIENDS_PLUS:
            buf.append("~hidden(").append(this.owner).append(')');
        break;
        case FRIENDS:
            buf.append("~friends(").append(this.owner).append(')');
        break;
        case INVITE_PLUS:
            buf.append("~private(").append(this.owner).append(")~canRequestInvite");
        break;
        case INVITE:
            buf.append("~private(").append(this.owner).append(')');
        break;
        }
        if (this.ageGate)
            buf.append("~ageGate");
        if (this.region != null)
            buf.append("~region(").append(this.region.getValue()).append(')');
        if (this.nonce != null)
            buf.append("~nonce(").append(this.nonce).append(')');
        return buf;
    }
    private StringBuilder toString1(StringBuilder buf, boolean asUrl)
    {
        if (asUrl)
            return buf.append("https://vrchat.com/home/launch?worldId=")
                      .append(this.world)
                      .append("&instanceId=")
                      .append(this.instance);
        if (this.world != null)
            buf.append(this.world).append(':');
        return buf.append(this.instance);
    }
    public StringBuilder toString(StringBuilder buf, boolean asUrl)
    {
        if (this.isSpecial())
        {
            if (asUrl)
            {
                throw new IllegalArgumentException("special instance values are illegal for urls");
            }
            else
            {
                return buf.append(this.name);
            }
        }
        else
        {
            return this.toString1(buf, asUrl);
        }
    }
    public String toUrlString()
    {
        if (this.isSpecial())
            throw new IllegalArgumentException("special instance values are illegal for urls");
        return this.toString0(new StringBuilder(), true).toString();
    }
    @Override
    public String toString()
    {
        if (this.isSpecial())
            return this.name;
        return this.toString(new StringBuilder(), false).toString();
    }

    @Override
    public int hashCode()
    {
        int result = 1;
        result = 31 * result + Objects.hashCode(this.world);
        result = 31 * result + Objects.hashCode(this.instance);
        return result;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof Location))
            return false;
        if (o == this)
            return true;
        Location other = (Location)o;
        return Objects.equals(this.world, other.world)
            && Objects.equals(this.instance, other.instance);
    }

    public boolean isSpecial()
    {
        return this.type == null;
    }
    public boolean isConcrete()
    {
        return this.type != null;
    }

}
