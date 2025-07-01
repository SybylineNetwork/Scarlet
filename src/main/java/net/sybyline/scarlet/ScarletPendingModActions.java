package net.sybyline.scarlet;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.util.MiscUtils;

public class ScarletPendingModActions
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/PendingModActions");

    public ScarletPendingModActions(File pendingModActionsFile)
    {
        this.pendingModActionsFile = pendingModActionsFile;
        this.pendingModActions = new ConcurrentHashMap<>();
        this.pendingBanInfo = new ConcurrentHashMap<>();
        this.load();
    }

    final File pendingModActionsFile;
    final Map<String, String> pendingModActions;
    final Map<String, BanInfo> pendingBanInfo;

    public static class DataSpec
    {
        public Map<String, String> pendingModActions;
        public Map<String, BanInfo> pendingBanInfo;
    }
    public static class BanInfo
    {
        public BanInfo()
        {
        }
        public String description;
        public String[] tags;
    }

    public boolean hasPending(GroupAuditType type, String targetUserId)
    {
        return this.pendingModActions.containsKey(type.id+":"+targetUserId);
    }

    public String addPending(GroupAuditType type, String targetUserId, String actorUserId)
    {
        String prevActorUserId = this.pendingModActions.putIfAbsent(type.id+":"+targetUserId, actorUserId);
        if (prevActorUserId == null)
        {
            this.save();
        }
        return prevActorUserId;
    }

    public String getPending(GroupAuditType type, String targetUserId)
    {
        return this.pendingModActions.get(type.id+":"+targetUserId);
    }

    public String pollPending(GroupAuditType type, String targetUserId)
    {
        String prevActorUserId = this.pendingModActions.remove(type.id+":"+targetUserId);
        if (prevActorUserId != null)
        {
            this.save();
        }
        return prevActorUserId;
    }

    public boolean addBanInfo(String targetUserId)
    {
        if (this.pendingBanInfo.putIfAbsent(targetUserId, new BanInfo()) != null)
            return false;
        this.save();
        return true;
    }

    public boolean setBanInfoTags(String targetUserId, String[] tags)
    {
        BanInfo info = this.pendingBanInfo.get(targetUserId);
        if (info == null)
            return false;
        info.tags = tags;
        this.save();
        return true;
    }

    public boolean setBanInfoDescription(String targetUserId, String description)
    {
        BanInfo info = this.pendingBanInfo.get(targetUserId);
        if (info == null)
            return false;
        info.description = description;
        this.save();
        return true;
    }

    public BanInfo pollBanInfo(String targetUserId)
    {
        BanInfo info = this.pendingBanInfo.remove(targetUserId);
        if (info != null)
            this.save();
        return info;
    }

    public boolean load()
    {
        if (!this.pendingModActionsFile.isFile())
        {
            this.save();
            return true;
        }
        DataSpec spec;
        try (Reader r = MiscUtils.reader(this.pendingModActionsFile))
        {
            spec = Scarlet.GSON_PRETTY.fromJson(r, DataSpec.class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading pending moderation actions", ex);
            return false;
        }
        this.pendingModActions.clear();
        if (spec != null && spec.pendingModActions != null && !spec.pendingModActions.isEmpty())
        {
            this.pendingModActions.putAll(spec.pendingModActions);
        }
        this.pendingBanInfo.clear();
        if (spec != null && spec.pendingBanInfo != null && !spec.pendingBanInfo.isEmpty())
        {
            this.pendingBanInfo.putAll(spec.pendingBanInfo);
        }
        return true;
    }

    public boolean save()
    {
        DataSpec spec = new DataSpec();
        spec.pendingModActions = new HashMap<>(this.pendingModActions);
        spec.pendingBanInfo = new HashMap<>(this.pendingBanInfo);
        try (Writer w = MiscUtils.writer(this.pendingModActionsFile))
        {
            Scarlet.GSON_PRETTY.toJson(spec, DataSpec.class, w);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving pending moderation actions", ex);
            return false;
        }
        return true;
    }

}
