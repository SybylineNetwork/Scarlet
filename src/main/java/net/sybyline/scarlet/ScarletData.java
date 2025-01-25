package net.sybyline.scarlet;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.JSON;
import io.github.vrchatapi.model.GroupAuditLogEntry;

public class ScarletData
{

    public ScarletData(File dir)
    {
        this.dir = dir;
    }

    final File dir;

    <T> T read(String kind, String id, Class<T> type)
    {
        if (kind == null)
            return null;
        if (id == null)
            return null;
        if (type == null)
            return null;
        File targetDir = new File(this.dir, kind);
        if (!targetDir.isDirectory())
            return null;
        File targetFile = new File(targetDir, id);
        if (!targetFile.isFile())
            return null;
        try (FileReader reader = new FileReader(targetFile))
        {
            return JSON.getGson().fromJson(reader, type);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        return null;
    }

    <T> void write(String kind, String id, Class<T> type, T data)
    {
        if (kind == null)
            return;
        if (id == null)
            return;
        if (type == null)
            return;
        if (data == null)
            return;
        File targetDir = new File(this.dir, kind);
        if (!targetDir.isDirectory())
            targetDir.mkdirs();
        File targetFile = new File(targetDir, id);
        try (FileWriter writer = new FileWriter(targetFile))
        {
            JSON.getGson().toJson(data, type, writer);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    <T> void edit(String kind, String id, Class<T> type, UnaryOperator<T> edit)
    {
        T data = this.read(kind, id, type);
        if (data != null) try
        {
            data = edit.apply(data);
        }
        finally
        {
            this.write(kind, id, type, data);
        }
    }

    public UserMetadata userMetadata(String userId)
    {
        return this.read("usr", userId, UserMetadata.class);
    }
    public void userMetadata(String userId, UserMetadata userMetadata)
    {
        this.write("usr", userId, UserMetadata.class, userMetadata);
    }
    public void userMetadata(String userId, UnaryOperator<UserMetadata> edit)
    {
        this.edit("usr", userId, UserMetadata.class, edit);
    }
    public ScarletData.UserMetadata userMetadata_setSnowflake(String userId, String userSnowflake)
    {
        ScarletData.UserMetadata userMeta = this.userMetadata(userId);
        if (userMeta == null)
            userMeta = new ScarletData.UserMetadata();
        userMeta.userSnowflake = userSnowflake;
        this.userMetadata(userId, userMeta);
        return userMeta;
    }

    public AuditEntryMetadata auditEntryMetadata(String auditEntryId)
    {
        return this.read("gaud", auditEntryId, AuditEntryMetadata.class);
    }
    public void auditEntryMetadata(String auditEntryId, AuditEntryMetadata auditEntryMetadata)
    {
        this.write("gaud", auditEntryId, AuditEntryMetadata.class, auditEntryMetadata);
    }
    public void auditEntryMetadata(String auditEntryId, UnaryOperator<AuditEntryMetadata> edit)
    {
        this.edit("gaud", auditEntryId, AuditEntryMetadata.class, edit);
    }
    public ScarletData.AuditEntryMetadata auditEntryMetadata_setTags(String auditEntryId, String[] entryTags)
    {
        ScarletData.AuditEntryMetadata auditEntryMeta = this.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta == null)
            auditEntryMeta = new ScarletData.AuditEntryMetadata();
        auditEntryMeta.entryTags = entryTags == null || entryTags.length == 0 ? null : entryTags;
        this.auditEntryMetadata(auditEntryId, auditEntryMeta);
        return auditEntryMeta;
    }
    public ScarletData.AuditEntryMetadata auditEntryMetadata_setDescription(String auditEntryId, String entryDescription)
    {
        ScarletData.AuditEntryMetadata auditEntryMeta = this.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta == null)
            auditEntryMeta = new ScarletData.AuditEntryMetadata();
        auditEntryMeta.entryDescription = entryDescription == null || (entryDescription = entryDescription.trim()).isEmpty() ? null : entryDescription;
        this.auditEntryMetadata(auditEntryId, auditEntryMeta);
        return auditEntryMeta;
    }

    public static class UserMetadata
    {
        public String userSnowflake;
        public String[] auditEntryIds;
        
        public void addAuditEntryId(String auditEntryId)
        {
            if (auditEntryId == null)
                return;
            String[] auditEntryIds = this.auditEntryIds;
            if (auditEntryIds == null)
                this.auditEntryIds = new String[]{auditEntryId};
            else
                (this.auditEntryIds = Arrays.copyOf(auditEntryIds, auditEntryIds.length + 1))[auditEntryIds.length] = auditEntryId;
        }
    }

    public static class AuditEntryMetadata
    {
        public String guildSnowflake;
        public String channelSnowflake;
        public String messageSnowflake;
        public String threadSnowflake;
        public String auxMessageSnowflake;
        
        public GroupAuditLogEntry entry;
        
        public String[] entryTags;
        public String entryDescription;
        
        public boolean hasMessage()
        { return this.guildSnowflake != null && this.channelSnowflake != null && this.messageSnowflake != null; }
        public String getMessageDelineated()
        { return !this.hasMessage() ? null : String.format("%s/%s/%s", this.guildSnowflake, this.channelSnowflake, this.messageSnowflake); }
        public String getMessageUrl()
        { return !this.hasMessage() ? null : String.format("https://discord.com/channels/%s/%s/%s", this.guildSnowflake, this.channelSnowflake, this.messageSnowflake); }
        
        public boolean hasChannel()
        { return this.guildSnowflake != null && this.channelSnowflake != null; }
        public String getChannelDelineated()
        { return !this.hasChannel() ? null : String.format("%s/%s", this.guildSnowflake, this.channelSnowflake); }
        public String getChannelUrl()
        { return !this.hasChannel() ? null : String.format("https://discord.com/channels/%s/%s", this.guildSnowflake, this.channelSnowflake); }
        
        public boolean hasAuxMessage()
        { return this.guildSnowflake != null && this.channelSnowflake != null && this.auxMessageSnowflake != null; }
        public String getAuxMessageDelineated()
        { return !this.hasAuxMessage() ? null : String.format("%s/%s/%s", this.guildSnowflake, this.channelSnowflake, this.auxMessageSnowflake); }
        public String getAuxMessageUrl()
        { return !this.hasAuxMessage() ? null : String.format("https://discord.com/channels/%s/%s/%s", this.guildSnowflake, this.channelSnowflake, this.auxMessageSnowflake); }
        
        public boolean hasThread()
        { return this.guildSnowflake != null && this.threadSnowflake != null; }
        public String getThreadDelineated()
        { return !this.hasThread() ? null : String.format("%s/%s", this.guildSnowflake, this.threadSnowflake); }
        public String getThreadUrl()
        { return !this.hasThread() ? null : String.format("https://discord.com/channels/%s/%s", this.guildSnowflake, this.threadSnowflake); }
        
        public boolean hasNonEmptyData()
        { Object data = this.entry.getData(); return data != null && data instanceof Map && !((Map<?, ?>)data).isEmpty(); }
        public Map<String, Object> getData()
        { @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>)this.entry.getData(); return data; }
        public <T> T getData(Type type)
        { Gson gson = JSON.getGson(); return gson.fromJson(gson.toJsonTree(this.entry.getData()), type); }
        public <T> T getData(Class<T> type)
        { Gson gson = JSON.getGson(); return gson.fromJson(gson.toJsonTree(this.entry.getData()), type); }
        public <T> T getData(TypeToken<T> type)
        { Gson gson = JSON.getGson(); return gson.fromJson(gson.toJsonTree(this.entry.getData()), type); }
    }

}
