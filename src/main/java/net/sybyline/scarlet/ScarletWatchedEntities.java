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
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.sybyline.scarlet.server.discord.DEnum;
import net.sybyline.scarlet.util.Func;
import net.sybyline.scarlet.util.UniqueStrings;

public class ScarletWatchedEntities<E>
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/WatchedEntities");

    public ScarletWatchedEntities(File watchedEntitiesFile, String idPrefix_, Func.V3.NE<E, String, EmbedBuilder> builder)
    {
        this(watchedEntitiesFile, id -> id.startsWith(idPrefix_), builder);
    }
    public ScarletWatchedEntities(File watchedEntitiesFile, Pattern idPattern, Func.V3.NE<E, String, EmbedBuilder> builder)
    {
        this(watchedEntitiesFile, id -> idPattern.matcher(id).matches(), builder);
    }
    public ScarletWatchedEntities(File watchedEntitiesFile, Predicate<String> idValid, Func.V3.NE<E, String, EmbedBuilder> builder)
    {
        this.watchedEntitiesFile = watchedEntitiesFile;
        this.idValid = idValid;
        this.watchedEntities = new ConcurrentHashMap<>();
        this.builder = builder;
        this.load();
    }

    final File watchedEntitiesFile;
    final Predicate<String> idValid;
    Map<String, WatchedEntity> watchedEntities;
    final Func.V3.NE<E, String, EmbedBuilder> builder;

    public static class WatchedEntity implements Comparable<WatchedEntity>
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
        
        public <E> EmbedBuilder embed(ScarletWatchedEntities<E> context, E entity)
        {
            EmbedBuilder builder = new EmbedBuilder();
            context.builder.invoke(entity, this.id, builder);
            return builder
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
        public int compareTo(WatchedEntity o)
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
            if (!(obj instanceof WatchedEntity)) return false;
            WatchedEntity other = (WatchedEntity)obj;
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
        List<WatchedEntity> importedWatchedEntities = new ArrayList<>();
        for (CSVRecord record : CSVFormat.EXCEL.parse(reader))
        {
            WatchedEntity watchedEntity = new WatchedEntity();
            watchedEntity.id = record.get(0);
            switch (record.get(1))
            {
            case "TOXIC":   watchedEntity.type = WatchedEntity.Type.MALICIOUS;  break;
            case "WATCH":   watchedEntity.type = WatchedEntity.Type.NUISANCE;   break;
            case "SPECIAL": watchedEntity.type = WatchedEntity.Type.COMMUNITY;  break;
            case "PARTNER": watchedEntity.type = WatchedEntity.Type.AFFILIATED; break;
            default:        watchedEntity.type = WatchedEntity.Type.OTHER;      break;
            }
            
            Arrays
                .stream(record.get(5).split("[,;/\\|]"))
                .filter($ -> !$.isEmpty())
                .map(String::toLowerCase)
                .forEach(watchedEntity.tags.strings()::add);
            watchedEntity.critical = Boolean.parseBoolean(record.get(3));
            watchedEntity.message = record.get(4);
            importedWatchedEntities.add(watchedEntity);
        }
        return this.importWatchedEntities(importedWatchedEntities, overwrite);
    }

    public boolean importJson(Reader reader, boolean overwrite) throws IOException
    {
        WatchedEntity[] watchedEntitiesArray = Scarlet.GSON_PRETTY.fromJson(reader, WatchedEntity[].class);
        List<WatchedEntity> importedWatchedEntities = Arrays.asList(watchedEntitiesArray);
        return this.importWatchedEntities(importedWatchedEntities, overwrite);
    }

    public boolean exportJson(Writer writer) throws IOException
    {
        WatchedEntity[] watchedEntitiesArray = this.watchedEntities.values().toArray(new WatchedEntity[0]);
        Scarlet.GSON_PRETTY.toJson(watchedEntitiesArray, WatchedEntity[].class, writer);
        return true;
    }

    public boolean importWatchedEntities(List<WatchedEntity> importedWatchedEntities, boolean overwrite)
    {
        for (WatchedEntity watchedEntity : importedWatchedEntities)
        {
            if (watchedEntity.id != null && this.idValid.test(watchedEntity.id))
            {
                if (overwrite)
                {
                    this.watchedEntities.put(watchedEntity.id, watchedEntity);
                }
                else
                {
                    this.watchedEntities.putIfAbsent(watchedEntity.id, watchedEntity);
                }
            }
        }
        this.save();
        return true;
    }

    public WatchedEntity getWatchedEntity(String entityId)
    {
        return this.watchedEntities.get(entityId);
    }

    public boolean addWatchedEntity(String entityId, WatchedEntity watchedEntity)
    {
        if (watchedEntity == null)
            return false;
        this.watchedEntities.put(entityId, watchedEntity);
        this.save();
        return true;
    }

    public boolean removeWatchedEntity(String entityId)
    {
        if (this.watchedEntities.remove(entityId) == null)
            return false;
        this.save();
        return true;
    }

    public boolean load()
    {
        if (!this.watchedEntitiesFile.isFile())
        {
            this.save();
            return true;
        }
        WatchedEntity[] watchedEntitiesArray;
        try (FileReader fr = new FileReader(this.watchedEntitiesFile))
        {
            watchedEntitiesArray = Scarlet.GSON_PRETTY.fromJson(fr, WatchedEntity[].class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading watched entities", ex);
            return false;
        }
        Map<String, WatchedEntity> watchedEntities = new ConcurrentHashMap<>();
        for (WatchedEntity watchedEntity : watchedEntitiesArray)
            if (watchedEntity != null && watchedEntity.id != null && this.idValid.test(watchedEntity.id))
                watchedEntities.put(watchedEntity.id, watchedEntity);
        this.watchedEntities = watchedEntities;
        return true;
    }

    public boolean save()
    {
        WatchedEntity[] watchedEntitiesArray = this.watchedEntities.values().toArray(new WatchedEntity[0]);
        try (FileWriter fw = new FileWriter(this.watchedEntitiesFile))
        {
            Scarlet.GSON_PRETTY.toJson(watchedEntitiesArray, WatchedEntity[].class, fw);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving watched entities", ex);
            return false;
        }
        return true;
    }

}
