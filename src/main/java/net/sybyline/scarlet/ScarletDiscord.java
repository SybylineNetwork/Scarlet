package net.sybyline.scarlet;

import java.io.Closeable;
import java.io.File;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.User;

public interface ScarletDiscord extends Closeable
{

    // U+2727 White Four Pointed Star (the character separating the footer and timestamp differs by client platform...)
    String FOOTER_PREFIX = String.format("%s %s \u2727 ", Scarlet.NAME, Scarlet.VERSION);

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Discord");

    public void updateCommandList();

    public boolean submitAudio(File file);

    public default void process(Scarlet scarlet, GroupAuditLogEntry entry)
    {
        GroupAuditType latype = GroupAuditType.of(entry.getEventType());
        if (latype == null)
            return;
        ScarletData.AuditEntryMetadata entryMeta = new ScarletData.AuditEntryMetadata();
        entryMeta.entry = entry;
        if (entry.getActorDisplayName() == null)
        {
            User actor = scarlet.vrc.getUser(entry.getActorId());
            if (actor != null)
            {
                entry.setActorDisplayName(actor.getDisplayName());
            }
        }
        
        if (scarlet.vrc.currentUserId == null || Objects.equals(scarlet.vrc.currentUserId, entry.getActorId()))
        {
            String pendingActorId = scarlet.pendingModActions.pollPending(latype, entry.getTargetId());
            if (pendingActorId != null)
            {
                entryMeta.auxActorId = pendingActorId;
                User auxActor = scarlet.vrc.getUser(pendingActorId);
                if (auxActor != null)
                {
                    entryMeta.auxActorDisplayName = auxActor.getDisplayName();
                }
                else
                {
                    entryMeta.auxActorDisplayName = pendingActorId;
                }
            }
        }
        
        try
        {
            switch (latype)
            {
            case INSTANCE_WARN:
//            case INSTANCE_MUTE:
            case INSTANCE_KICK:
            case MEMBER_REMOVE:
            case USER_BAN: 
            case USER_UNBAN: {
                LOG.info(String.format("Moderation event %s: %s (%s)", entry.getEventType(), entry.getDescription(), entry.getCreatedAt()));
                this.processUserModeration(scarlet, entryMeta);
            } break;
            case INSTANCE_CREATE: {
                this.processInstanceCreate(scarlet, entryMeta);
            } break;
            case INSTANCE_CLOSE: {
                this.processInstanceClose(scarlet, entryMeta);
            } break;
            case MEMBER_JOIN: {
                this.processMemberJoin(scarlet, entryMeta);
            } break;
            case MEMBER_LEAVE: {
                this.processMemberLeave(scarlet, entryMeta);
            } break;
            case MEMBER_USER_UPDATE: {
                this.processMemberUserUpdate(scarlet, entryMeta);
            } break;
            case POST_CREATE: {
                this.processPostCreate(scarlet, entryMeta);
            } break;
            case MEMBER_ROLE_ASSIGN: {
                this.processMemberRoleAssign(scarlet, entryMeta);
            } break;
            case MEMBER_ROLE_UNASSIGN: {
                this.processMemberRoleUnassign(scarlet, entryMeta);
            } break;
            case ROLE_CREATE: {
                this.processRoleCreate(scarlet, entryMeta);
            } break;
            case ROLE_DELETE: {
                this.processRoleDelete(scarlet, entryMeta);
            } break;
            case ROLE_UPDATE: {
                this.processRoleUpdate(scarlet, entryMeta);
            } break;
            case INVITE_CREATE: {
                this.processInviteCreate(scarlet, entryMeta);
            } break;
            case REQUEST_BLOCK: {
                this.processRequestBlock(scarlet, entryMeta);
            } break;
            case REQUEST_CREATE: {
                this.processRequestCreate(scarlet, entryMeta);
            } break;
            case REQUEST_REJECT: {
                this.processRequestReject(scarlet, entryMeta);
            } break;
            case UPDATE: {
                this.processUpdate(scarlet, entryMeta);
            } break;
            default: {
                this.processDefault(scarlet, entryMeta);
            } break;
            }
        }
        finally
        {
            scarlet.data.auditEntryMetadata(entry.getId(), entryMeta);
            if (entryMeta.hasMessage())
            {
                LOG.info(entry.getEventType().replace('.', ' ')+" ("+entryMeta.getMessageDelineated()+")");
            }
        }
    }

    public void emitUserModeration(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, User target, ScarletData.UserMetadata actorMeta, ScarletData.UserMetadata targetMeta, String history, String recent, ScarletData.AuditEntryMetadata parentEntryMeta, boolean reactiveKickFromBan);
    public default void processUserModeration(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        String //type = entryMeta.entry.getEventType(),
               auditId = entryMeta.entry.getId(),
               //description = entryMeta.entry.getDescription(),
               actorId = entryMeta.entry.getActorId(),
               //actorDisplayName = entryMeta.entry.getActorDisplayName(),
               targetId = entryMeta.entry.getTargetId();
        OffsetDateTime createdAt = entryMeta.entry.getCreatedAt();
        
        if (entryMeta.hasAuxActor())
        {
            actorId = entryMeta.auxActorId;
        }
        
        ScarletData.UserMetadata actorMeta = scarlet.data.userMetadata(actorId),
                                 targetMeta = scarlet.data.userMetadata(targetId);
        User target = scarlet.vrc.getUser(targetId);
        
        if (targetMeta == null)
            targetMeta = new ScarletData.UserMetadata();
        List<ScarletData.AuditEntryMetadata> auditEntryMetas = null;
        String[] auditEntryIds = targetMeta.auditEntryIds;
        targetMeta.addAuditEntryId(auditId);
        scarlet.data.userMetadata(targetId, targetMeta);
        
        if (auditEntryIds != null)
        {
            for (String auditEntryId : auditEntryIds)
            {
                if (auditEntryId != null)
                {
                    ScarletData.AuditEntryMetadata found = scarlet.data.auditEntryMetadata(auditEntryId);
                    if (found != null)
                    {
                        if (auditEntryMetas == null)
                            auditEntryMetas = new ArrayList<>();
                        auditEntryMetas.add(found);
                    }
                }
            }
        }
        
        String history = null, recent = null;
        boolean reactiveKickFromBan = false;
        ScarletData.AuditEntryMetadata parentEntryMeta = null;
        if (auditEntryMetas != null)
        {
            int iwarns = 0, imutes = 0, ikicks = 0, gkicks = 0, gbans = 0;
            int iwarnsR = 0, imutesR = 0, ikicksR = 0, gkicksR = 0, gbansR = 0;
            OffsetDateTime prevR = createdAt.minusHours(24);
            ScarletData.AuditEntryMetadata mostRecent = auditEntryMetas.get(0);
            for (ScarletData.AuditEntryMetadata auditEntryMeta : auditEntryMetas)
            {
                boolean isRecent = prevR.isBefore(auditEntryMeta.entry.getCreatedAt());
                if (auditEntryMeta.entry.getCreatedAt().isBefore(mostRecent.entry.getCreatedAt()))
                {
                    mostRecent = auditEntryMeta;
                }
                if (auditEntryMeta.entryRedacted || auditEntryMeta.hasParentEvent())
                    continue;
                switch (auditEntryMeta.entry.getEventType())
                {
                case "group.instance.warn": {
                    iwarns++;
                    if (isRecent)
                        iwarnsR++;
                } break;
                case "group.instance.mute": {
                    imutes++;
                    if (isRecent)
                        imutesR++;
                } break;
                case "group.instance.kick": {
                    ikicks++;
                    if (isRecent)
                        ikicksR++;
                } break;
                case "group.member.remove": {
                    gkicks++;
                    if (isRecent)
                        gkicksR++;
                } break;
                case "group.user.ban": {
                    gbans++;
                    if (isRecent)
                        gbansR++;
                } break;
                }
            }
            {
                StringBuilder allModStr = new StringBuilder();
                if (iwarns > 0)
                {
                    allModStr.append(iwarns).append(" instance warn(s)");
                    if (iwarnsR > 0)
                        allModStr.append(" (").append(iwarnsR).append(" recent)");
                    allModStr.append('\n');
                }
                if (imutes > 0)
                {
                    allModStr.append(imutes).append(" instance mute(s)");
                    if (imutesR > 0)
                        allModStr.append(" (").append(imutesR).append(" recent)");
                    allModStr.append('\n');
                }
                if (ikicks > 0)
                {
                    allModStr.append(ikicks).append(" instance kick(s)");
                    if (ikicksR > 0)
                        allModStr.append(" (").append(ikicksR).append(" recent)");
                    allModStr.append('\n');
                }
                if (gkicks > 0)
                {
                    allModStr.append(gkicks).append(" group kick(s)");
                    if (gkicksR > 0)
                        allModStr.append(" (").append(gkicksR).append(" recent)");
                    allModStr.append('\n');
                }
                if (gbans > 0)
                {
                    allModStr.append(gbans).append(" group ban(s)");
                    if (gbansR > 0)
                        allModStr.append(" (").append(gbansR).append(" recent)");
                    allModStr.append('\n');
                }
                history = allModStr.toString();
            }
            {
                String prevModStr = String.format("%s by %s", GroupAuditType.of(mostRecent.entry.getEventType()).title, mostRecent.entry.getActorDisplayName()),
                       prevModUrl = mostRecent.getMessageUrl();
                if (prevModUrl != null)
                    prevModStr = String.format("[%s](%s)", prevModStr, prevModUrl);
                recent = prevModStr;
            }
            if (Objects.equals(GroupAuditType.INSTANCE_KICK.id, entryMeta.entry.getEventType())
             && Objects.equals(GroupAuditType.USER_BAN.id, mostRecent.entry.getEventType())
             && Objects.equals(entryMeta.entry.getActorId(), mostRecent.entry.getActorId())
             && entryMeta.entry.getCreatedAt().minusMinutes(5L).isBefore(mostRecent.entry.getCreatedAt()))
            {
                parentEntryMeta = mostRecent;
                reactiveKickFromBan = true;
            }
        }
        this.emitUserModeration(scarlet, entryMeta, target, actorMeta, targetMeta, history, recent, parentEntryMeta, reactiveKickFromBan);
    }

    public void emitInstanceCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, String location);
    public default void processInstanceCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        this.emitInstanceCreate(scarlet, entryMeta, entryMeta.entry.getTargetId());
    }

    public void emitInstanceClose(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, String location);
    public default void processInstanceClose(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        this.emitInstanceClose(scarlet, entryMeta, entryMeta.entry.getTargetId());
    }

    public void emitMemberJoin(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta);
    public default void processMemberJoin(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        this.emitMemberJoin(scarlet, entryMeta);
    }

    public void emitMemberLeave(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta);
    public default void processMemberLeave(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        this.emitMemberLeave(scarlet, entryMeta);
    }

    public void emitPostCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, GroupAuditType.PostCreateComponent post);
    public default void processPostCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        GroupAuditType.PostCreateComponent post = entryMeta.getData(GroupAuditType.PostCreateComponent.class);
        this.emitPostCreate(scarlet, entryMeta, post);
    }

    public void emitMemberRoleAssign(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, User target, GroupAuditType.RoleRefComponent role);
    public default void processMemberRoleAssign(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        GroupAuditType.RoleRefComponent role = entryMeta.getData(GroupAuditType.RoleRefComponent.class);
        User target = scarlet.vrc.getUser(entryMeta.entry.getTargetId());
        this.emitMemberRoleAssign(scarlet, entryMeta, target, role);
    }

    public void emitMemberRoleUnassign(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, User target, GroupAuditType.RoleRefComponent role);
    public default void processMemberRoleUnassign(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        GroupAuditType.RoleRefComponent role = entryMeta.getData(GroupAuditType.RoleRefComponent.class);
        User target = scarlet.vrc.getUser(entryMeta.entry.getTargetId());
        this.emitMemberRoleUnassign(scarlet, entryMeta, target, role);
    }

    public void emitMemberUserUpdate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, Map<String, GroupAuditType.UpdateSubComponent> updates);
    public default void processMemberUserUpdate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        Map<String, GroupAuditType.UpdateSubComponent> updates = entryMeta.getData(GroupAuditType.UpdateSubComponent.TYPE_TOKEN);
        this.emitMemberUserUpdate(scarlet, entryMeta, updates);
    }

    public void emitRoleCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, GroupAuditType.RoleCreateComponent role);
    public default void processRoleCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        GroupAuditType.RoleCreateComponent role = entryMeta.getData(GroupAuditType.RoleCreateComponent.class);
        this.emitRoleCreate(scarlet, entryMeta, role);
    }

    public void emitRoleDelete(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, GroupAuditType.RoleDeleteComponent role);
    public default void processRoleDelete(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        GroupAuditType.RoleDeleteComponent role = entryMeta.getData(GroupAuditType.RoleDeleteComponent.class);
        this.emitRoleDelete(scarlet, entryMeta, role);
    }

    public void emitRoleUpdate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, Map<String, GroupAuditType.UpdateSubComponent> updates);
    public default void processRoleUpdate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        Map<String, GroupAuditType.UpdateSubComponent> updates = entryMeta.getData(GroupAuditType.UpdateSubComponent.TYPE_TOKEN);
        this.emitRoleUpdate(scarlet, entryMeta, updates);
    }

    public void emitInviteCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta);
    public default void processInviteCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        this.emitInviteCreate(scarlet, entryMeta);
    }

    public void emitRequestBlock(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta);
    public default void processRequestBlock(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        this.emitRequestBlock(scarlet, entryMeta);
    }

    public void emitRequestCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta);
    public default void processRequestCreate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        this.emitRequestCreate(scarlet, entryMeta);
    }

    public void emitRequestReject(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta);
    public default void processRequestReject(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        this.emitRequestReject(scarlet, entryMeta);
    }

    public void emitUpdate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, Map<String, GroupAuditType.UpdateSubComponent> updates);
    public default void processUpdate(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        Map<String, GroupAuditType.UpdateSubComponent> updates = entryMeta.getData(GroupAuditType.UpdateSubComponent.TYPE_TOKEN);
        this.emitUpdate(scarlet, entryMeta, updates);
    }

    public void emitDefault(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta);
    public default void processDefault(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta)
    {
        this.emitDefault(scarlet, entryMeta);
    }

    public void emitExtendedInstanceInactive(Scarlet scarlet, String location, String auditEntryId, ScarletData.InstanceEmbedMessage instanceEmbedMessage);
    public void emitExtendedStaffJoin(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName);
    public void emitExtendedStaffLeave(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName);
    public void emitExtendedVtkInitiated(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName);
    public void emitExtendedInstanceMonitor(Scarlet scarlet, String location, ScarletData.InstanceEmbedMessage instanceEmbedMessage);
    public void emitModSummary(Scarlet scarlet, OffsetDateTime endOfDay);

}
