package net.sybyline.scarlet.util;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return new VersionedFile(matcher.group(), matcher.group("id"), Integer.parseInt(matcher.group("version"), 1), matcher.group("qualifier"));
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

    @Override
    public String toString()
    {
        String toString = this.toString;
        if (toString == null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(this.id).append('/').append(this.version);
            if (!this.qualifier.isEmpty())
                sb.append('/').append(this.qualifier);
            toString = sb.toString();
            this.toString = toString;
        }
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

}
