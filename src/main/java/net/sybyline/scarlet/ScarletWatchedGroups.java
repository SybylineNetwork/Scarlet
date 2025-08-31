package net.sybyline.scarlet;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.vrchatapi.model.Group;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.sybyline.scarlet.server.discord.DEnum;
import net.sybyline.scarlet.util.UniqueStrings;

public class ScarletWatchedGroups
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/WatchedGroups");

    public ScarletWatchedGroups(File watchedGroupsFile)
    {
        this.watchedGroupsFile = watchedGroupsFile;
        this.watchedGroups = new ConcurrentHashMap<>();
        this.load();
    }

    final File watchedGroupsFile;
    Map<String, WatchedGroup> watchedGroups;

    public static class WatchedGroup implements Comparable<WatchedGroup>
    {
        public String id;
        public Type type = Type.UNKNOWN;
        public static enum Type implements DEnum.DEnumString<Type>
        {
            UNKNOWN(null),
            
            MALICIOUS(Color.RED),
            NUISANCE(Color.ORANGE),
            
            COMMUNITY(Color.GREEN),
            AFFILIATED(Color.BLUE),
            
            OTHER(null),
            ;
            public final Color text_color;
            private Type(Color text_color)
            {
                this.text_color = text_color;
            }
            @Override
            public String value()
            {
                return this.name();
            }
            @Override
            public String display()
            {
                return this.name();
            }
        }
        public UniqueStrings tags = new UniqueStrings();
        public int priority = 0;
        public boolean critical = false;
        public boolean silent = false;
        public String message = null;
        public String notes = null;
        
        public EmbedBuilder embed(Group group)
        {
            String code = group != null ? (group.getShortCode()+"."+group.getDiscriminator()) : null,
                   icon = group != null ? group.getIconUrl() : null,
                   name = group != null ? MarkdownSanitizer.escape(group.getName()) : this.id,
                   thumbnail = group != null ? group.getBannerUrl() : "https://assets.vrchat.com/www/groups/default_banner.png";
            return new EmbedBuilder()
                .setAuthor(code, null, icon)
                .setTitle(name, "https://vrchat.com/home/group/"+this.id)
                .setThumbnail(thumbnail)
                .addField("Id", "`"+this.id+"`", false)
                .addField("Watch type", this.type == null ? "`none`" : this.type.name(), false)
                .addField("Priority", "`"+this.priority+"`", true)
                .addField("Critical", this.critical ? "`true`" : "`false`", true)
                .addField("Silent", this.silent ? "`true`" : "`false`", true)
                .addField("Message", this.message == null ? "`none`" : this.message, false)
                .addField("Tags", this.tags.isEmpty() ? "`none`" : this.tags
                    .strings()
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", ")), false)
                .addField("Notes", this.notes == null ? "`none`" : this.notes, false)
            ;
        }

        @Override
        public int compareTo(WatchedGroup o)
        {
            int cmp;
            if ((cmp = Integer.compare(this.priority, o.priority)) != 0) return cmp;
            if ((cmp = Boolean.compare(this.critical, o.critical)) != 0) return cmp;
            if ((cmp = Boolean.compare(this.silent, o.silent)) != 0) return cmp;
            return 0;
        }

        @Override
        public int hashCode()
        {
            int hash = 1;
            hash = 31 * hash + Objects.hashCode(this.id);
            hash = 31 * hash + Objects.hashCode(this.type);
            hash = 31 * hash + Objects.hashCode(this.tags);
            hash = 31 * hash + Integer.hashCode(this.priority);
            hash = 31 * hash + Boolean.hashCode(this.critical);
            hash = 31 * hash + Boolean.hashCode(this.silent);
            hash = 31 * hash + Objects.hashCode(this.message);
            return hash;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) return true;
            if (!(obj instanceof WatchedGroup)) return false;
            WatchedGroup other = (WatchedGroup)obj;
            if (!Objects.equals(this.id, other.id)) return false;
            if (!Objects.equals(this.type, other.type)) return false;
            if (!Objects.equals(this.tags, other.tags)) return false;
            if (!Objects.equals(this.priority, other.priority)) return false;
            if (!Objects.equals(this.critical, other.critical)) return false;
            if (!Objects.equals(this.silent, other.silent)) return false;
            if (!Objects.equals(this.message, other.message)) return false;
            return true;
        }

        @Override
        public String toString()
        {
            return this.id;
        }
        
    }

    @SuppressWarnings("resource")
    public boolean importLegacyCSV(Reader reader, boolean overwrite) throws IOException
    {
        List<WatchedGroup> importedWatchedGroups = new ArrayList<>();
        for (CSVRecord record : CSVFormat.EXCEL.parse(reader))
        {
            WatchedGroup watchedGroup = new WatchedGroup();
            watchedGroup.id = record.get(0);
            switch (record.get(1))
            {
            case "TOXIC":   watchedGroup.type = WatchedGroup.Type.MALICIOUS;  break;
            case "WATCH":   watchedGroup.type = WatchedGroup.Type.NUISANCE;   break;
            case "SPECIAL": watchedGroup.type = WatchedGroup.Type.COMMUNITY;  break;
            case "PARTNER": watchedGroup.type = WatchedGroup.Type.AFFILIATED; break;
            default:        watchedGroup.type = WatchedGroup.Type.OTHER;      break;
            }
            
            Arrays
                .stream(record.get(5).split("[,;/\\|]"))
                .filter($ -> !$.isEmpty())
                .map(String::toLowerCase)
                .forEach(watchedGroup.tags.strings()::add);
            watchedGroup.critical = Boolean.parseBoolean(record.get(3));
            watchedGroup.message = record.get(4);
            importedWatchedGroups.add(watchedGroup);
        }
        return this.importWatchedGroups(importedWatchedGroups, overwrite);
    }

    public boolean importJson(Reader reader, boolean overwrite) throws IOException
    {
        WatchedGroup[] watchedGroupsArray = Scarlet.GSON_PRETTY.fromJson(reader, WatchedGroup[].class);
        List<WatchedGroup> importedWatchedGroups = Arrays.asList(watchedGroupsArray);
        return this.importWatchedGroups(importedWatchedGroups, overwrite);
    }

    public boolean exportJson(Writer writer) throws IOException
    {
        WatchedGroup[] watchedGroupsArray = this.watchedGroups.values().toArray(new WatchedGroup[0]);
        Scarlet.GSON_PRETTY.toJson(watchedGroupsArray, WatchedGroup[].class, writer);
        return true;
    }

    public boolean importWatchedGroups(List<WatchedGroup> importedWatchedGroups, boolean overwrite)
    {
        for (WatchedGroup watchedGroup : importedWatchedGroups)
        {
            if (watchedGroup.id != null && watchedGroup.id.startsWith("grp_"))
            {
                if (overwrite)
                {
                    this.watchedGroups.put(watchedGroup.id, watchedGroup);
                }
                else
                {
                    this.watchedGroups.putIfAbsent(watchedGroup.id, watchedGroup);
                }
            }
        }
        this.save();
        return true;
    }

    public WatchedGroup getWatchedGroup(String groupId)
    {
        return this.watchedGroups.get(groupId);
    }

    public boolean addWatchedGroup(String groupId, WatchedGroup watchedGroup)
    {
        if (watchedGroup == null)
            return false;
        this.watchedGroups.put(groupId, watchedGroup);
        this.save();
        return true;
    }

    public boolean removeWatchedGroup(String groupId)
    {
        if (this.watchedGroups.remove(groupId) == null)
            return false;
        this.save();
        return true;
    }

    public boolean load()
    {
        if (!this.watchedGroupsFile.isFile())
        {
            this.save();
            return true;
        }
        WatchedGroup[] watchedGroupsArray;
        try (FileReader fr = new FileReader(this.watchedGroupsFile))
        {
            watchedGroupsArray = Scarlet.GSON_PRETTY.fromJson(fr, WatchedGroup[].class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading watched groups", ex);
            return false;
        }
        Map<String, WatchedGroup> watchedGroups = new ConcurrentHashMap<>();
        for (WatchedGroup watchedGroup : watchedGroupsArray)
            if (watchedGroup != null && watchedGroup.id != null && watchedGroup.id.startsWith("grp_"))
                watchedGroups.put(watchedGroup.id, watchedGroup);
        this.watchedGroups = watchedGroups;
        return true;
    }

    public boolean save()
    {
        WatchedGroup[] watchedGroupsArray = this.watchedGroups.values().toArray(new WatchedGroup[0]);
        try (FileWriter fw = new FileWriter(this.watchedGroupsFile))
        {
            Scarlet.GSON_PRETTY.toJson(watchedGroupsArray, WatchedGroup[].class, fw);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving watched groups", ex);
            return false;
        }
        return true;
    }

}
