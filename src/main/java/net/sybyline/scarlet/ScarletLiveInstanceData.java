package net.sybyline.scarlet;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.util.MiscUtils;

public class ScarletLiveInstanceData
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/LiveInstanceData");

    public ScarletLiveInstanceData(File liveInstanceDataFile)
    {
        this.liveInstanceDataFile = liveInstanceDataFile;
        this.location2AuditEntryId = new ConcurrentHashMap<>();
        this.location2instanceEmbedMessage = new ConcurrentHashMap<>();
        this.load();
    }

    final File liveInstanceDataFile;
    final Map<String, String> location2AuditEntryId;
    final Map<String, InstanceEmbedMessage> location2instanceEmbedMessage;

    public static class DataSpec
    {
        public Map<String, String> location2AuditEntryId;
        public Map<String, InstanceEmbedMessage> location2instanceEmbedMessage;
    }

    public void setLocationAudit(String location, String auditEntryId)
    {
        this.location2AuditEntryId.put(location, auditEntryId);
    }
    public String getLocationAudit(String location, boolean remove)
    {
        return remove
            ? this.location2AuditEntryId.remove(location)
            : this.location2AuditEntryId.get(location);
    }
    public Set<String> getLocations()
    {
        return new HashSet<>(this.location2AuditEntryId.keySet());
    }

    public static class InstanceEmbedMessage
    {
        public InstanceEmbedMessage(String guildSnowflake, String channelSnowflake, String messageSnowflake)
        {
            this.guildSnowflake = guildSnowflake;
            this.channelSnowflake = channelSnowflake;
            this.messageSnowflake = messageSnowflake;
            this.closedAt = null;
        }
        public InstanceEmbedMessage()
        {
        }
        
        public String guildSnowflake;
        public String channelSnowflake;
        public String messageSnowflake;
        public OffsetDateTime closedAt;
    }
    public void setLocationInstanceEmbedMessage(String location, String guildSnowflake, String channelSnowflake, String messageSnowflake)
    {
        this.location2instanceEmbedMessage.put(location, new InstanceEmbedMessage(guildSnowflake, channelSnowflake, messageSnowflake));
    }
    public InstanceEmbedMessage getLocationInstanceEmbedMessage(String location, boolean remove)
    {
        return remove
            ? this.location2instanceEmbedMessage.remove(location)
            : this.location2instanceEmbedMessage.get(location);
    }

    public boolean load()
    {
        if (!this.liveInstanceDataFile.isFile())
        {
            this.save();
            return true;
        }
        DataSpec spec;
        try (Reader r = MiscUtils.reader(this.liveInstanceDataFile))
        {
            spec = Scarlet.GSON_PRETTY.fromJson(r, DataSpec.class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading live instance data", ex);
            return false;
        }
        this.location2AuditEntryId.clear();
        this.location2instanceEmbedMessage.clear();
        if (spec != null)
        {
            if (spec.location2AuditEntryId != null && !spec.location2AuditEntryId.isEmpty())
            {
                this.location2AuditEntryId.putAll(spec.location2AuditEntryId);
            }
            if (spec.location2instanceEmbedMessage != null && !spec.location2instanceEmbedMessage.isEmpty())
            {
                this.location2instanceEmbedMessage.putAll(spec.location2instanceEmbedMessage);
            }
        }
        return true;
    }

    public boolean save()
    {
        DataSpec spec = new DataSpec();
        spec.location2AuditEntryId = new HashMap<>(this.location2AuditEntryId);
        spec.location2instanceEmbedMessage = new HashMap<>(this.location2instanceEmbedMessage);
        if (!this.liveInstanceDataFile.getParentFile().isDirectory())
            this.liveInstanceDataFile.getParentFile().mkdirs();
        try (Writer w = MiscUtils.writer(this.liveInstanceDataFile))
        {
            Scarlet.GSON_PRETTY.toJson(spec, DataSpec.class, w);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving live instance data", ex);
            return false;
        }
        return true;
    }

}
