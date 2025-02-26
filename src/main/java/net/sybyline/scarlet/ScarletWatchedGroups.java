package net.sybyline.scarlet;

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

public class ScarletWatchedGroups
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/WatchedGroups");

    public ScarletWatchedGroups(Scarlet scarlet, File watchedGroupsFile)
    {
        this.scarlet = scarlet;
        this.watchedGroupsFile = watchedGroupsFile;
        this.watchedGroups = new ConcurrentHashMap<>();
        this.load();
    }

    final Scarlet scarlet;
    final File watchedGroupsFile;
    Map<String, WatchedGroup> watchedGroups;

    public static class WatchedGroup
    {
        public String id;
        public Type type = Type.UNKNOWN;
        public static enum Type
        {
            UNKNOWN(),
            
            MALICIOUS(),
            NUISANCE(),
            
            COMMUNITY(),
            AFFILIATED(),
            
            OTHER(),
            ;
        }
        public String[] tags;
        public boolean critical;
        public String message;
        
        public EmbedBuilder embed(Group group)
        {
            String name = group != null ? group.getName() : this.id,
                   thumbnail = group != null ? group.getBannerUrl() : "https://assets.vrchat.com/www/groups/default_banner.png";
            return new EmbedBuilder()
                .setTitle(name, "https://vrchat.com/home/group/"+this.id)
                .setThumbnail(thumbnail)
                .addField("Critical", this.critical ? "true" : "false", false)
                .addField("Watch type", this.type == null ? "" : this.type.name(), false)
                .addField("Message", this.message == null ? "" : this.message, false)
                .addField("Tags", this.tags == null ? "null" : Arrays
                    .stream(this.tags)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("`, `", "`", "`")), false)
            ;
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
            
            watchedGroup.tags = Arrays
                .stream(record.get(5).split("[,;/\\|]"))
                .filter($ -> !$.isEmpty())
                .map(String::toLowerCase)
                .toArray(String[]::new);
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
            if (overwrite)
            {
                this.watchedGroups.put(watchedGroup.id, watchedGroup);
            }
            else
            {
                this.watchedGroups.putIfAbsent(watchedGroup.id, watchedGroup);
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
            if (watchedGroup != null)
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
