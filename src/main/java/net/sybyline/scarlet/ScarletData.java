package net.sybyline.scarlet;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.JSON;
import io.github.vrchatapi.model.GroupAuditLogEntry;

import net.sybyline.scarlet.util.LRUMap;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.UniqueStrings;

public class ScarletData
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Data");

    public ScarletData(File dir)
    {
        this.dir = dir;
        this.objs = new ConcurrentHashMap<>();
        this.subs = new ConcurrentHashMap<>();
    }

    final File dir;
    final Map<String, Datum<?>> objs;
    final Map<String, Data<?>> subs;

    public <T> Datum<T> getDatum(String name, Class<T> type)
    {
        return this.objs.computeIfAbsent(name, _name -> new ObjDatum<>(_name, type)).as(type);
    }
    public Datum<?> freeDatum(String name)
    {
        return this.objs.remove(name);
    }
    public <T> Data<T> getData(String kind, Class<T> type)
    {
        return this.subs.computeIfAbsent(kind, _kind -> new SubData<>(_kind, type)).as(type);
    }
    public Data<?> freeData(String name)
    {
        return this.subs.remove(name);
    }

    public void saveDirty()
    {
        this.objs.values().forEach(Datum::saveIfDirty);
        this.subs.values().forEach(Data::saveIfDirty);
    }

    public void saveAll()
    {
        this.objs.values().forEach(Datum::save);
        this.subs.values().forEach(Data::save);
    }

    <T> T readObj(String name, Class<T> type)
    {
        return this.readObj(name, type, false);
    }
    <T> T readObj(String name, Class<T> type, boolean orNew)
    {
        if (name == null)
            return null;
        if (type == null)
            return null;
        File targetFile = new File(this.dir, name);
        if (!targetFile.isFile())
            return null;
        try (Reader reader = MiscUtils.reader(targetFile))
        {
            return JSON.getGson().fromJson(reader, type);
        }
        catch (Exception ex)
        {
            LOG.error("Exception reading data "+name, ex);
        }
        if (orNew) try
        {
            return type.getDeclaredConstructor().newInstance();
        }
        catch (Exception ex)
        {
            LOG.error("Exception creating data "+name, ex);
        }
        return null;
    }
    <T> void writeObj(String name, Class<T> type, T data)
    {
        if (name == null)
            return;
        if (type == null)
            return;
        if (data == null)
            return;
        File targetFile = new File(this.dir, name);
        try (Writer writer = MiscUtils.writer(targetFile))
        {
            JSON.getGson().toJson(data, type, writer);
        }
        catch (Exception ex)
        {
            LOG.error("Exception writing data "+name, ex);
        }
    }
    <T> void editObj(String name, Class<T> type, UnaryOperator<T> edit)
    {
        T data = this.readObj(name, type);
        if (data != null) try
        {
            data = edit.apply(data);
        }
        finally
        {
            this.writeObj(name, type, data);
        }
    }

    <T> T readSub(String kind, String id, Class<T> type)
    {
        return this.readSub(kind, id, type, false);
    }
    <T> T readSub(String kind, String id, Class<T> type, boolean orNew)
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
        try (Reader reader = MiscUtils.reader(targetFile))
        {
            return JSON.getGson().fromJson(reader, type);
        }
        catch (Exception ex)
        {
            LOG.error("Exception reading sub data "+kind+":"+id, ex);
        }
        if (orNew) try
        {
            return type.getDeclaredConstructor().newInstance();
        }
        catch (Exception ex)
        {
            LOG.error("Exception creating sub data "+kind+":"+id, ex);
        }
        return null;
    }
    <T> void writeSub(String kind, String id, Class<T> type, T data)
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
        try (Writer writer = MiscUtils.writer(targetFile))
        {
            JSON.getGson().toJson(data, type, writer);
        }
        catch (Exception ex)
        {
            LOG.error("Exception writing sub data "+kind+":"+id, ex);
        }
    }
    <T> void editSub(String kind, String id, Class<T> type, UnaryOperator<T> edit)
    {
        T data = this.readSub(kind, id, type);
        if (data != null) try
        {
            data = edit.apply(data);
        }
        finally
        {
            this.writeSub(kind, id, type, data);
        }
    }

    public interface Datum<T>
    {
        Class<T> getType();
        default <TT> Datum<TT> as(Class<TT> ttype)
        {
            if (this.getType() != ttype)
                throw new ClassCastException(this.getType()+" is not the same as "+ttype);
            @SuppressWarnings("unchecked")
            Datum<TT> tthis = (Datum<TT>)this;
            return tthis;
        }
        boolean free();
        boolean exists();
        void markDirty();
        boolean pollDirty();
        void save();
        default void saveIfDirty()
        {
            if (this.pollDirty())
                this.save();
        }
        T get();
    }

    public interface Data<T>
    {
        Class<T> getType();
        default <TT> Data<TT> as(Class<TT> ttype)
        {
            if (this.getType() != ttype)
                throw new ClassCastException(this.getType()+" is not the same as "+ttype);
            @SuppressWarnings("unchecked")
            Data<TT> tthis = (Data<TT>)this;
            return tthis;
        }
        boolean free();
        boolean exists(String id);
        void flush();
        void save();
        void saveIfDirty();
        Datum<T> get(String id);
    }

    final class ObjDatum<T> implements Datum<T>
    {
        ObjDatum(String name, Class<T> type)
        {
            this.name = name;
            this.type = type;
            this.value = null;
            this.dirty = false;
        }
        final String name;
        final Class<T> type;
        T value;
        boolean dirty;
        @Override
        public Class<T> getType()
        {
            return this.type;
        }
        @Override
        public boolean free()
        {
            return ScarletData.this.objs.remove(this.name, this);
        }
        @Override
        public boolean exists()
        {
            return this.value != null || new File(ScarletData.this.dir, this.name).isFile();
        }
        @Override
        public void markDirty()
        {
            this.dirty = true;
        }
        @Override
        public boolean pollDirty()
        {
            try
            {
                return this.dirty;
            }
            finally
            {
                this.dirty = false;
            }
        }
        @Override
        public synchronized void save()
        {
            this.dirty = false;
            T value = this.value;
            if (value != null)
            {
                ScarletData.this.writeObj(this.name, this.type, value);
            }
        }
        @Override
        public synchronized T get()
        {
            T value = this.value;
            if (value == null)
            {
                value = ScarletData.this.readObj(this.name, this.type, true);
                this.value = value;
            }
            return value;
        }
    }

    final class SubData<T> implements Data<T>
    {
        SubData(String kind, Class<T> type)
        {
            this.kind = kind;
            this.type = type;
            this.subdir = new File(ScarletData.this.dir, kind);
            this.values = LRUMap.ofSynchronized(this::save);
            this.dirties = Collections.newSetFromMap(new ConcurrentHashMap<>());
        }
        final String kind;
        final Class<T> type;
        final File subdir;
        final Map<String, T> values;
        final Set<String> dirties;
        synchronized void saveIfDirty(String id, T value)
        {
            if (this.dirties.remove(id) && value != null)
            {
                ScarletData.this.writeSub(this.kind, id, this.type, value);
            }
        }
        synchronized void save(String id, T value)
        {
            this.dirties.remove(id);
            if (value != null)
            {
                ScarletData.this.writeSub(this.kind, id, this.type, value);
            }
        }
        synchronized void flush(String id, T value)
        {
            this.dirties.remove(id);
            if (value != null)
            {
                this.values.remove(id, value);
                ScarletData.this.writeSub(this.kind, id, this.type, value);
            }
        }
        T get0(String id)
        {
            return this.values.computeIfAbsent(id, _id -> ScarletData.this.readSub(_id, id, this.type, true));
        }
        @Override
        public Class<T> getType()
        {
            return this.type;
        }
        @Override
        public boolean free()
        {
            return ScarletData.this.subs.remove(this.kind, this);
        }
        @Override
        public boolean exists(String id)
        {
            return this.values.containsKey(id) || new File(this.subdir, id).isFile();
        }
        @Override
        public void flush()
        {
            new HashMap<>(this.values).forEach(this::flush);
        }
        @Override
        public void save()
        {
            this.values.forEach(this::save);
        }
        @Override
        public void saveIfDirty()
        {
            this.values.forEach(this::saveIfDirty);
        }
        @Override
        public synchronized Datum<T> get(String id)
        {
            return new Datum<T>()
            {
                @Override
                public Class<T> getType()
                {
                    return SubData.this.type;
                }
                @Override
                public boolean free()
                {
                    return false;
                }
                @Override
                public boolean exists()
                {
                    return SubData.this.exists(id);
                }
                @Override
                public void markDirty()
                {
                    SubData.this.dirties.add(id);
                }
                @Override
                public boolean pollDirty()
                {
                    return SubData.this.dirties.remove(id);
                }
                @Override
                public void save()
                {
                    SubData.this.save(id, SubData.this.values.get(id));
                }
                @Override
                public T get()
                {
                    return SubData.this.get0(id);
                }
            };
        }
    }

    public GlobalMetadata globalMetadata()
    {
        return this.readObj("global.json", GlobalMetadata.class);
    }
    public void globalMetadata(GlobalMetadata globalMetadata)
    {
        this.writeObj("global.json", GlobalMetadata.class, globalMetadata);
    }
    public void globalMetadata(UnaryOperator<GlobalMetadata> edit)
    {
        this.editObj("global.json", GlobalMetadata.class, edit);
    }
    public String globalMetadata_getSnowflakeId(String userSnowflake)
    {
        ScarletData.GlobalMetadata globalMeta = this.globalMetadata();
        if (globalMeta == null)
            return null;
        return globalMeta.getSnowflakeId(userSnowflake);
    }
    public ScarletData.GlobalMetadata globalMetadata_setSnowflakeId(String userSnowflake, String userId)
    {
        ScarletData.GlobalMetadata globalMeta = this.globalMetadata();
        if (globalMeta == null)
            globalMeta = new ScarletData.GlobalMetadata();
        globalMeta.setSnowflakeId(userSnowflake, userId);
        this.globalMetadata(globalMeta);
        return globalMeta;
    }

    public LiveInstancesMetadata liveInstancesMetadata()
    {
        return this.readObj("live.json", LiveInstancesMetadata.class);
    }
    public void liveInstancesMetadata(LiveInstancesMetadata liveInstancesMeta)
    {
        this.writeObj("live.json", LiveInstancesMetadata.class, liveInstancesMeta);
    }
    public void liveInstancesMetadata(UnaryOperator<LiveInstancesMetadata> edit)
    {
        this.editObj("live.json", LiveInstancesMetadata.class, edit);
    }
    public synchronized String liveInstancesMetadata_getLocationAudit(String location, boolean remove)
    {
        ScarletData.LiveInstancesMetadata liveInstancesMeta = this.liveInstancesMetadata();
        if (liveInstancesMeta == null)
            return null;
        String auditEntryId = liveInstancesMeta.getLocationAudit(location, remove);
        if (remove && auditEntryId != null)
            this.liveInstancesMetadata(liveInstancesMeta);
        return auditEntryId;
    }
    public synchronized ScarletData.LiveInstancesMetadata liveInstancesMetadata_setLocationAudit(String location, String auditEntryId)
    {
        ScarletData.LiveInstancesMetadata liveInstancesMeta = this.liveInstancesMetadata();
        if (liveInstancesMeta == null)
            liveInstancesMeta = new ScarletData.LiveInstancesMetadata();
        liveInstancesMeta.setLocationAudit(location, auditEntryId);
        this.liveInstancesMetadata(liveInstancesMeta);
        return liveInstancesMeta;
    }

    public UserMetadata userMetadata(String userId)
    {
        return this.readSub("usr", userId, UserMetadata.class);
    }
    public void userMetadata(String userId, UserMetadata userMetadata)
    {
        this.writeSub("usr", userId, UserMetadata.class, userMetadata);
    }
    public void userMetadata(String userId, UnaryOperator<UserMetadata> edit)
    {
        this.editSub("usr", userId, UserMetadata.class, edit);
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
    public String userMetadata_getSnowflake(String userId)
    {
        ScarletData.UserMetadata userMeta = this.userMetadata(userId);
        if (userMeta == null)
            return null;
        return userMeta.userSnowflake;
    }

    public AuditEntryMetadata auditEntryMetadata(String auditEntryId)
    {
        return this.readSub("gaud", auditEntryId, AuditEntryMetadata.class);
    }
    public void auditEntryMetadata(String auditEntryId, AuditEntryMetadata auditEntryMetadata)
    {
        this.writeSub("gaud", auditEntryId, AuditEntryMetadata.class, auditEntryMetadata);
    }
    public void auditEntryMetadata(String auditEntryId, UnaryOperator<AuditEntryMetadata> edit)
    {
        this.editSub("gaud", auditEntryId, AuditEntryMetadata.class, edit);
    }
    public ScarletData.AuditEntryMetadata auditEntryMetadata_setTags(String auditEntryId, String[] entryTags)
    {
        ScarletData.AuditEntryMetadata auditEntryMeta = this.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta == null)
            auditEntryMeta = new ScarletData.AuditEntryMetadata();
        auditEntryMeta.entryTags.clear().addAll(entryTags);
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

    public static class GlobalMetadata
    {
        public GlobalMetadata()
        {
        }
        
        public Map<String, String> userSnowflake2userId;
        
        public synchronized void setSnowflakeId(String userSnowflake, String userId)
        {
            Map<String, String> userSnowflake2userId = this.userSnowflake2userId;
            if (userSnowflake2userId == null)
                this.userSnowflake2userId = userSnowflake2userId = new HashMap<>();
            if (!(userSnowflake2userId instanceof HashMap))
                this.userSnowflake2userId = userSnowflake2userId = new HashMap<>(userSnowflake2userId);
            userSnowflake2userId.put(userSnowflake, userId);
        }
        public synchronized String getSnowflakeId(String userSnowflake)
        {
            Map<String, String> userSnowflake2userId = this.userSnowflake2userId;
            if (userSnowflake2userId == null)
                return null;
            return userSnowflake2userId.get(userSnowflake);
        }
    }

    public static class LiveInstancesMetadata
    {
        public LiveInstancesMetadata()
        {
        }
        
        public Map<String, String> location2AuditEntryId;
        
        public synchronized void setLocationAudit(String location, String auditEntryId)
        {
            Map<String, String> location2AuditEntryId = this.location2AuditEntryId;
            if (location2AuditEntryId == null)
                this.location2AuditEntryId = location2AuditEntryId = new HashMap<>();
            if (!(location2AuditEntryId instanceof HashMap))
                this.location2AuditEntryId = location2AuditEntryId = new HashMap<>(location2AuditEntryId);
            location2AuditEntryId.put(location, auditEntryId);
        }
        public synchronized String getLocationAudit(String location, boolean remove)
        {
            Map<String, String> location2AuditEntryId = this.location2AuditEntryId;
            if (location2AuditEntryId == null)
                return null;
            return remove
                ? location2AuditEntryId.remove(location)
                : location2AuditEntryId.get(location);
        }
    }

    public static class UserMetadata
    {
        public UserMetadata()
        {
        }
        
        public String userSnowflake;
        public String[] auditEntryIds;
        public EvidenceSubmission[] evidenceSubmissions;
        
        public synchronized void addAuditEntryId(String auditEntryId)
        {
            if (auditEntryId == null)
                return;
            String[] auditEntryIds = this.auditEntryIds;
            if (auditEntryIds == null)
                this.auditEntryIds = new String[]{auditEntryId};
            else
                (this.auditEntryIds = Arrays.copyOf(auditEntryIds, auditEntryIds.length + 1))[auditEntryIds.length] = auditEntryId;
        }
        
        public synchronized void addUserCaseEvidence(EvidenceSubmission evidenceSubmission)
        {
            if (evidenceSubmission == null)
                return;
            EvidenceSubmission[] evidenceSubmissions = this.evidenceSubmissions;
            if (evidenceSubmissions == null)
                this.evidenceSubmissions = new EvidenceSubmission[]{evidenceSubmission};
            else
                (this.evidenceSubmissions = Arrays.copyOf(evidenceSubmissions, evidenceSubmissions.length + 1))[evidenceSubmissions.length] = evidenceSubmission;
        }
    }
    public static class EvidenceSubmission
    {
        public EvidenceSubmission(String auditEntryId, String submitterSnowflake, String submitterDisplayName, OffsetDateTime submissionTime, String fileName, String url, String proxyUrl)
        {
            this.auditEntryId = auditEntryId;
            this.submitterSnowflake = submitterSnowflake;
            this.submitterDisplayName = submitterDisplayName;
            this.submissionTime = submissionTime;
            this.fileName = fileName;
            this.url = url;
            this.proxyUrl = proxyUrl;
        }
        public EvidenceSubmission()
        {
        }
        
        public String auditEntryId;
        public String submitterSnowflake;
        public String submitterDisplayName;
        public OffsetDateTime submissionTime;
        public String fileName;
        public String url;
        public String proxyUrl;
    }

    public static class AuditEntryMetadata
    {
        public AuditEntryMetadata()
        {
        }
        
        public String guildSnowflake;
        public String channelSnowflake;
        public String messageSnowflake;
        public String threadSnowflake;
        public String auxMessageSnowflake;
        
        public GroupAuditLogEntry entry;
        
        public UniqueStrings entryTags = new UniqueStrings();
        public String entryDescription;
        public boolean entryRedacted;
        
        public String auxActorId;
        public String auxActorDisplayName;
        
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

        public boolean hasSomeUrl()
        { return this.hasThread() || this.hasMessage() || this.hasAuxMessage(); }
        public String getSomeUrl()
        { return this.hasThread() ? this.getThreadUrl() : this.hasMessage() ? this.getMessageUrl() : this.hasAuxMessage() ? this.getAuxMessageUrl() : null; }

        public boolean hasAuxActor()
        { return this.auxActorId != null; }

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
    public void linkIdToSnowflake(String userId, String userSnowflake)
    {
        this.userMetadata_setSnowflake(userId, userSnowflake);
        this.globalMetadata_setSnowflakeId(userSnowflake, userId);
    }

}
