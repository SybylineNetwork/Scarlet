package net.sybyline.scarlet;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

public class ScarletModerationTags
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/ModTags");

    public ScarletModerationTags(File moderationTagsFile)
    {
        this.moderationTagsFile = moderationTagsFile;
        this.tags = null;
    }

    final File moderationTagsFile;
    private List<Tag> tags;

    public static class Tag
    {
        public static final TypeToken<List<Tag>> LISTOF = new TypeToken<List<Tag>>(){};
        public Tag()
        {
            this("", "", "");
        }
        public Tag(String value, String label, String description)
        {
            this.value = value != null ? value : "";;
            this.label = label != null ? label : "";
            this.description = description != null ? description : "";
        }
        public String value, label, description;
    }

    synchronized List<Tag> getJson()
    {
        List<Tag> tags = this.tags;
        if (tags != null)
            return tags;
        try (FileReader fr = new FileReader(this.moderationTagsFile))
        {
            tags = new CopyOnWriteArrayList<>(Scarlet.GSON_PRETTY.fromJson(fr, Tag.LISTOF));
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading moderation tags", ex);
            tags = new CopyOnWriteArrayList<>();
        }
        this.tags = tags;
        return tags;
    }

    synchronized void saveJson()
    {
        List<Tag> tags = this.tags;
        if (tags == null)
            return;
        if (!this.moderationTagsFile.getParentFile().isDirectory())
            this.moderationTagsFile.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(this.moderationTagsFile))
        {
            Scarlet.GSON_PRETTY.toJson(tags, Tag.LISTOF.getType(), fw);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving moderation tags", ex);
        }
    }

    public Tag getTag(String value)
    {
        List<Tag> tags = this.getJson();
        if (tags == null)
            return null;
        for (Tag tag : this.tags)
            if (tag.value.equals(value))
                return tag;
        return null;
    }

    public int addOrUpdateTag(String value, String label, String description)
    {
        List<Tag> tags = this.getJson();
        if (tags == null)
            return Integer.MIN_VALUE;
        Tag old = this.getTag(value);
        if (old != null)
        {
            if (label != null)
                old.label = label;
            if (description != null)
                old.description = description;
            this.saveJson();
            return 1;
        }
        if (tags.size() >= 25)
            return -2;
        Tag tag = new Tag(value, label, description);
        if (!tags.add(tag))
            return -1;
        this.saveJson();
        return 0;
    }

    public int removeTag(String value)
    {
        List<Tag> tags = this.getJson();
        if (tags == null)
            return Integer.MIN_VALUE;
        if (tags.isEmpty())
            return -2;
        Tag old = this.getTag(value);
        if (old == null)
            return -3;
        if (!tags.remove(old))
            return -1;
        this.saveJson();
        return 0;
    }

    public List<Tag> getTags()
    {
        List<Tag> tags = this.getJson();
        if (tags == null)
            return new ArrayList<>(0);
        return new ArrayList<>(this.tags);
    }

    public List<String> getTagValues()
    {
        List<Tag> tags = this.getJson();
        if (tags == null)
            return new ArrayList<>(0);
        List<String> values = new ArrayList<>(tags.size());
        for (Tag tag : tags)
            values.add(tag.value);
        return values;
    }

    public String getTagLabel(String value)
    {
        Tag tag = this.getTag(value);
        if (tag == null)
            return value;
        return tag.label;
    }

}
