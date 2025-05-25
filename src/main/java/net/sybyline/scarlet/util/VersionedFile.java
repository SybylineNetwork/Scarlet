package net.sybyline.scarlet.util;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.vrchatapi.model.MIMEType;
import io.github.vrchatapi.model.ModelFile;

public class VersionedFile
{

    static final Pattern pattern = Pattern.compile("(?<id>file_\\w{8}-\\w{4}-\\w{4}-\\w{4}-\\w{12})[/:](?<version>\\d+)([/:](?<qualifier>[\\-\\?\\&\\/\\w]))?");
    public static final int DEFAULT_FILE_VERSION = 1;

    public static VersionedFile parse(String string)
    {
        if (string == null)
            return null;
        Matcher matcher = pattern.matcher(string);
        if (!matcher.find())
            return null;
        return new VersionedFile(matcher.group(), matcher.group("id"), MiscUtils.parseIntElse(matcher.group("version"), 1), matcher.group("qualifier"));
    }

    public static VersionedFile of(String id)
    {
        return of(null, id, DEFAULT_FILE_VERSION, null);
    }
    public static VersionedFile of(String id, int version)
    {
        return of(null, id, version, null);
    }
    public static VersionedFile of(String id, int version, String qualifier)
    {
        return of(null, id, version, qualifier);
    }
    @Deprecated
    public static VersionedFile of(String toString, String id, int version, String qualifier)
    {
        return id == null ? null : new VersionedFile(toString, id, version, qualifier);
    }

    public VersionedFile(String id, int version, String qualifier)
    {
        this(null, id, version, qualifier);
    }
    @Deprecated
    private VersionedFile(String toString, String id, int version, String qualifier)
    {
        if (id == null)
            throw new IllegalArgumentException("id == null");
        if (qualifier == null || qualifier.trim().isEmpty())
            qualifier = "";
        this.id = id;
        this.version = version;
        this.qualifier = qualifier;
        // lazy:
        this.toString = toString;
        this.hashCode = 0;
    }

    public final String id;
    public final int version;
    public final String qualifier;

    private String toString;
    private int hashCode;

    public String id()
    {
        return this.id;
    }

    public int version()
    {
        return this.version;
    }

    public String qualifier()
    {
        return this.qualifier;
    }

    public String replaceInto(String string)
    {
        return string
            .replace("{fileId}", this.id)
            .replace("{fileVersion}", Integer.toString(this.version))
            .replace("{fileQualifier}", this.qualifier)
            ;
    }

    public String toString(char delimiter)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(this.id).append(delimiter).append(this.version);
        if (!this.qualifier.isEmpty())
            sb.append(delimiter).append(this.qualifier);
        return sb.toString();
    }

    @Override
    public String toString()
    {
        String toString = this.toString;
        if (toString == null)
            this.toString = toString = this.toString('/');
        return toString;
    }

    @Override
    public int hashCode()
    {
        int hashCode = this.hashCode;
        if (hashCode == 0)
        {
            hashCode = 1;
            hashCode = 31 * hashCode + Objects.hashCode(this.id);
            hashCode = 31 * hashCode + this.version;
            hashCode = 31 * hashCode + Objects.hashCode(this.qualifier);
            this.hashCode = hashCode;
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (!(obj instanceof VersionedFile))
            return false;
        VersionedFile other = (VersionedFile)obj;
        if (!Objects.equals(this.id, other.id))
            return false;
        if (this.version != other.version)
            return false;
        if (!Objects.equals(this.qualifier, other.qualifier))
            return false;
        return true;
    }

    public enum Kind
    {
        WORLD(),
        AVATAR(),
        
        WORLD_IMAGE(),
        AVATAR_IMAGE(),
        
        GALLERY(),
        ICON(),
        INVITE_PHOTO(),
        EMOJI(),
        EMOJI_ANIMATED(),
        STICKER(),
        PRINT(),
        
        OTHER(),
        ;
        static final Pattern filename = Pattern.compile("(?<topic>\\S+(?:\\s+\\S+))\\Q - \\E(?<name>\\S+(?:\\s+\\S+))\\Q - \\E(?<type>\\S+(?:\\s+\\S+))\\Q - \\E(?<unity>(?<version>\\d+\\Q.\\E\\d\\\\Q.\\E\\d+\\Qf)\\E\\d\\Q_\\E\\d\\Q_\\E(?<platform>\\w+)\\Q_\\E(?<build>\\w+))");
        public static Kind of(ModelFile file)
        {
            String ext = file.getExtension(),
                   name = file.getName();
            MIMEType mime = file.getMimeType();
            List<String> tags = file.getTags();
            switch (ext)
            {
            case ".vrcw": return WORLD;
            case ".vrca": return AVATAR;
            default: break; // fall through
            }
            switch (mime)
            {
            case APPLICATION_X_WORLD: return WORLD;
            case APPLICATION_X_AVATAR: return AVATAR;
            default: break; // fall through
            }
            {
                Matcher m = filename.matcher(name);
                if (m.matches())
                {
                    String topic = m.group("topic"),
                           type = m.group("type");
                    switch (topic)
                    {
                    case "World":
                        switch (type)
                        {
                        case "Asset bundle": return WORLD;
                        case "Image": return WORLD_IMAGE;
                        default: break; // fall through
                        }
                    break;
                    case "Avatar":
                        switch (type)
                        {
                        case "Asset bundle": return AVATAR;
                        case "Image": return AVATAR_IMAGE;
                        default: break; // fall through
                        }
                    break;
                    default:
                        // fall through
                    break;
                    }
                }
            }
            if (tags.contains("gallery"))     return GALLERY;
            if (tags.contains("icon"))        return ICON;
            if (tags.contains("invitePhoto")) return INVITE_PHOTO;
            if (tags.contains("emoji"))       return tags.contains("animated") ? EMOJI_ANIMATED : EMOJI;
            if (tags.contains("sticker"))     return STICKER;
            if (tags.contains("print"))       return PRINT;
            return OTHER;
        }
    }

}
