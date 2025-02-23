package net.sybyline.scarlet;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        public Type type;
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
    }

    @SuppressWarnings("resource")
    public boolean importLegacyCSV(Reader reader) throws IOException
    {
        Map<String, WatchedGroup> importedWatchedGroups = new HashMap<>();
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
            importedWatchedGroups.put(watchedGroup.id, watchedGroup);
        }
        this.watchedGroups.putAll(importedWatchedGroups);
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
