package net.sybyline.scarlet;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.vrchatapi.ApiException;
import io.github.vrchatapi.JSON;
import io.github.vrchatapi.model.GroupAccessType;
import io.github.vrchatapi.model.GroupLimitedMember;
import io.github.vrchatapi.model.GroupMemberStatus;
import io.github.vrchatapi.model.GroupPermissions;
import io.github.vrchatapi.model.Instance;
import io.github.vrchatapi.model.InstanceRegion;
import io.github.vrchatapi.model.LimitedUserGroups;
import io.github.vrchatapi.model.User;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.sybyline.scarlet.ScarletDiscordJDA.InstanceCreation;
import net.sybyline.scarlet.server.discord.DInteractions.ButtonClk;
import net.sybyline.scarlet.server.discord.DInteractions.Ephemeral;
import net.sybyline.scarlet.server.discord.DInteractions.ModalSub;
import net.sybyline.scarlet.server.discord.DInteractions.StringSel;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.UniqueStrings;
import net.sybyline.scarlet.util.VRChatHelpDeskURLs;

public class ScarletDiscordUI
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/JDA/UI");

    public ScarletDiscordUI(ScarletDiscordJDA discord)
    {
        this.discord = discord;
        discord.interactions.register(this);
    }

    final ScarletDiscordJDA discord;

    @ButtonClk("edit-tags")
    @Ephemeral
    public void editTags(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        
        // TODO : set default selected
        
        List<ScarletModerationTags.Tag> tags = this.discord.scarlet.moderationTags.getTags();
        
        if (tags == null || tags.isEmpty())
        {
            hook.sendMessage("No moderation tags!").setEphemeral(true).queue();
            return;
        }
        
        String auditEntryId = parts[1];
        
        StringSelectMenu.Builder builder = StringSelectMenu
            .create("select-tags:"+auditEntryId)
            .setMinValues(0)
            .setMaxValues(tags.size())
            .setPlaceholder("Select tags")
            ;
        
        for (ScarletModerationTags.Tag tag : tags)
        {
            String value = tag.value,
                   label = tag.label != null ? tag.label : tag.value,
                   desc = tag.description;
            if (desc == null)
                builder.addOption(label, value);
            else
                builder.addOption(label, value, desc);
        }
        
        ActionRow ar = ActionRow.of(builder.build());
        
        hook.sendMessageComponents(ar)
            .setEphemeral(true)
            .queue();
    }

    @StringSel("select-tags")
    public void selectTags(StringSelectInteractionEvent event)
    {
        String[] parts = event.getSelectMenu().getId().split(":");
        String joined = event.getValues().stream().map(this.discord.scarlet.moderationTags::getTagLabel).collect(Collectors.joining(", "));
        event.replyFormat("### Setting tags:\n%s", joined).setEphemeral(true).queue();
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata_setTags(parts[1], event.getValues().toArray(new String[0]));
        this.updateAuxMessage(event.getChannel(), auditEntryMeta);
    }

    @ButtonClk("vrchat-user-edit-manager-notes")
    public void vrchatUserEditManagerNotes(ButtonInteractionEvent event)
    {
        String[] parts = event.getButton().getId().split(":");

        String vrcTargetId = parts[1];
        
        String vrcActorId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
        if (vrcActorId == null)
        {
            event.reply(this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
            return;
        }
        
        long within1day = System.currentTimeMillis() - 86400_000L;
        User sc = this.discord.scarlet.vrc.getUser(vrcTargetId, within1day);
        if (sc == null)
        {
            event.replyFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
            return;
        }
        
        GroupLimitedMember glm = this.discord.scarlet.vrc.getGroupMembership(this.discord.scarlet.vrc.groupId, vrcTargetId);
        String value = glm == null ? null : glm.getManagerNotes();
        
        event.replyModal(Modal.create("vrchat-user-edit-manager-notes:"+vrcTargetId, "Manager notes for "+MarkdownSanitizer.escape(sc.getDisplayName()))
                .addActionRow(TextInput.create("manager-notes:"+vrcTargetId, "Notes", TextInputStyle.PARAGRAPH)
                    .setValue(value).build())
            .build());
    }

    @ModalSub("vrchat-user-edit-manager-notes")
    public void vrchatUserEditManagerNotesModal(ModalInteractionEvent event)
    {
        String[] parts = event.getModalId().split(":");

        String vrcTargetId = parts[1];
        
        String vrcActorId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
        if (vrcActorId == null)
        {
            event.reply(this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
            return;
        }
        
        long within1day = System.currentTimeMillis() - 86400_000L;
        User sc = this.discord.scarlet.vrc.getUser(vrcTargetId, within1day);
        if (sc == null)
        {
            event.replyFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
            return;
        }
        
        String value = event.getValue("manager-notes:"+vrcTargetId).getAsString();
        this.discord.scarlet.vrc.updateGroupMembershipNotes(this.discord.scarlet.vrc.groupId, vrcTargetId, value);
        event.reply("Updated manager notes.").queue($ -> $.deleteOriginal().queueAfter(3_000L, TimeUnit.MILLISECONDS));
    }

    @ButtonClk("vrchat-user-ban")
    public void vrchatUserBan(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");

        String vrcTargetId = parts[1];
        
        String vrcActorId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
        if (vrcActorId == null)
        {
            hook.sendMessage(this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
            return;
        }
        
        long within1day = System.currentTimeMillis() - 86400_000L;
        User sc = this.discord.scarlet.vrc.getUser(vrcTargetId, within1day);
        if (sc == null)
        {
            hook.sendMessageFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
            return;
        }

        if (!this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_bans_manage, event.getMember()))
        {
            if (!this.discord.checkMemberHasScarletPermission(ScarletPermission.GROUPEX_BANS_MANAGE, event.getMember(), false))
            {
                hook.sendMessage("You do not have permission to ban users.\n||(Your admin can enable this by giving your associated VRChat user ban management permissions in the group or with the command `/scarlet-discord-permissions type:Other name:groupex-bans-manage value:Allow`)||").setEphemeral(true).queue();
                return;
            }
        }
        
        GroupMemberStatus status = this.discord.scarlet.vrc.getGroupMembershipStatus(this.discord.scarlet.vrc.groupId, vrcTargetId);
        
        if (status == GroupMemberStatus.BANNED)
        {
            hook.sendMessage("This VRChat user is already banned").setEphemeral(true).queue();
            return;
        }
        
        if (this.discord.scarlet.pendingModActions.addPending(GroupAuditType.USER_BAN, vrcTargetId, vrcActorId) != null)
        {
            hook.sendMessage("This VRChat user currently has automated/assisted moderation pending, please retry later").setEphemeral(true).queue();
            return;
        }
        
        if (!this.discord.scarlet.vrc.banFromGroup(vrcTargetId))
        {
            hook.sendMessageFormat("Failed to ban %s", sc.getDisplayName()).setEphemeral(true).queue();
            return;
        }
        
        hook.sendMessageFormat("Banned %s", sc.getDisplayName()).setEphemeral(false).queue();
    }

    @ButtonClk("vrchat-user-unban")
    @Ephemeral
    public void vrchatUserUnban(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");

        String vrcTargetId = parts[1];
        
        String vrcActorId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
        if (vrcActorId == null)
        {
            hook.sendMessage(this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
            return;
        }
        
        long within1day = System.currentTimeMillis() - 86400_000L;
        User sc = this.discord.scarlet.vrc.getUser(vrcTargetId, within1day);
        if (sc == null)
        {
            hook.sendMessageFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
            return;
        }
        
        if (!this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_bans_manage, event.getMember()))
        {
            if (!this.discord.checkMemberHasScarletPermission(ScarletPermission.GROUPEX_BANS_MANAGE, event.getMember(), false))
            {
                hook.sendMessage("You do not have permission to unban users.\n||(Your admin can enable this by giving your associated VRChat user ban management permissions in the group or with the command `/scarlet-discord-permissions type:Other name:groupex-bans-manage value:Allow`)||").setEphemeral(true).queue();
                return;
            }
        }
        
        GroupMemberStatus status = this.discord.scarlet.vrc.getGroupMembershipStatus(this.discord.scarlet.vrc.groupId, vrcTargetId);
        
        if (status != GroupMemberStatus.BANNED)
        {
            hook.sendMessage("This VRChat user is not banned").setEphemeral(true).queue();
            return;
        }
        
        if (this.discord.scarlet.pendingModActions.addPending(GroupAuditType.USER_UNBAN, vrcTargetId, vrcActorId) != null)
        {
            hook.sendMessage("This VRChat user currently has automated/assisted moderation pending, please retry later").setEphemeral(true).queue();
            return;
        }
        
        if (!this.discord.scarlet.vrc.unbanFromGroup(vrcTargetId))
        {
            hook.sendMessageFormat("Failed to unban %s", sc.getDisplayName()).setEphemeral(true).queue();
            return;
        }
        
        hook.sendMessageFormat("Unbanned %s", sc.getDisplayName()).setEphemeral(false).queue();
    }

    @ButtonClk("event-redact")
    @Ephemeral
    public void eventRedact(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        
        String auditEntryId = parts[1];
        
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta == null)
        {
            hook.sendMessage("Failed to find the audit event.").setEphemeral(true).queue();
            return;
        }
        
        if (auditEntryMeta.entryRedacted)
        {
            hook.sendMessage("Event is already redacted.").setEphemeral(true).queue();
            return;
        }
        
        auditEntryMeta.entryRedacted = true;
        
        hook.sendMessage("Event redacted.").setEphemeral(false).queue();
        this.discord.scarlet.data.auditEntryMetadata(auditEntryId, auditEntryMeta);
        this.updateAuxMessage(event.getChannel(), auditEntryMeta);
    }
    
    void updateAuxMessage(MessageChannel channel, ScarletData.AuditEntryMetadata auditEntryMeta)
    {
        if (auditEntryMeta.hasMessage() && auditEntryMeta.auxMessageSnowflake != null && auditEntryMeta.entry != null)
        {
            ScarletData.UserMetadata actorMeta = this.discord.scarlet.data.userMetadata(auditEntryMeta.entry.getActorId());
            String content = auditEntryMeta.entryRedacted ? "# **__REDACTED__**\n\n" : "";
            content = content + (actorMeta == null || actorMeta.userSnowflake == null ? ("Unknown Discord id for actor "+auditEntryMeta.entry.getActorDisplayName()) : ("<@"+actorMeta.userSnowflake+">"));
            if (auditEntryMeta.entryTags != null && auditEntryMeta.entryTags.size() > 0)
            {
                String joined = auditEntryMeta.entryTags.strings().stream().map(this.discord.scarlet.moderationTags::getTagLabel).collect(Collectors.joining(", "));
                content = content + "\n### Tags:\n" + joined;
            }
            if (auditEntryMeta.entryDescription != null && !auditEntryMeta.entryDescription.trim().isEmpty())
            {
                content = content + "\n### Description:\n" + auditEntryMeta.entryDescription;
            }
            channel.editMessageById(auditEntryMeta.auxMessageSnowflake, content).queue();
        }
    }

    @ButtonClk("event-unredact")
    @Ephemeral
    public void eventUnredact(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        
        String auditEntryId = parts[1];
        
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta == null)
        {
            hook.sendMessage("Failed to find the audit event.").setEphemeral(true).queue();
            return;
        }
        
        if (!auditEntryMeta.entryRedacted)
        {
            hook.sendMessage("Event isn't yet redacted.").setEphemeral(true).queue();
            return;
        }
        
        auditEntryMeta.entryRedacted = false;
        
        hook.sendMessage("Event unredacted.").setEphemeral(false).queue();
        this.discord.scarlet.data.auditEntryMetadata(auditEntryId, auditEntryMeta);
        this.updateAuxMessage(event.getChannel(), auditEntryMeta);
    }

    @ButtonClk("new-instance-create")
    @Ephemeral
    public void newInstanceCreate(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        long within1day = System.currentTimeMillis() - 86400_000L;
        
        String vrcActorId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
        if (vrcActorId == null)
        {
            hook.sendMessage(this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
            return;
        }
        User sc = this.discord.scarlet.vrc.getUser(vrcActorId, within1day);
        if (sc == null)
        {
            hook.sendMessageFormat("No VRChat user found with id %s", vrcActorId).setEphemeral(true).queue();
            return;
        }
        String vrcActorDisplayName = sc == null ? vrcActorId : sc.getDisplayName();
        
        String ictoken = parts[1];
        
        InstanceCreation ic = this.discord.instanceCreation.get(ictoken);
        
        if (ic == null)
        {
            hook.deleteMessageById(event.getMessageId()).queue();
            hook.sendMessage("This interaction timed out.").queue();
            hook.deleteOriginal().delay(5_000L, TimeUnit.MILLISECONDS).queue();
            return;
        }
        
        switch (ic.groupAccessType)
        {
        case PUBLIC: {
            if (!this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_instance_public_create, event.getMember()))
            {
                hook.sendMessage("You do not have permission to create group public instances.").setEphemeral(true).queue();
                return;
            }
        } break;
        case PLUS: {
            if (!this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_instance_plus_create, event.getMember()))
            {
                hook.sendMessage("You do not have permission to create group plus instances.").setEphemeral(true).queue();
                return;
            }
        } break;
        case MEMBERS: {
            if (!this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_instance_open_create, event.getMember()))
            {
                hook.sendMessage("You do not have permission to create group member-only instances.").setEphemeral(true).queue();
                return;
            }
            if (ic.roleIds != null && !this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_instance_restricted_create, event.getMember()))
            {
                hook.sendMessage("You do not have permission to create role-restricted instances.").setEphemeral(true).queue();
                return;
            }
        } break;
        }

        if (ic.ageGate != null && ic.ageGate.booleanValue() && !this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_instance_age_gated_create, event.getMember()))
        {
            hook.sendMessage("You do not have permission to create age gated instances.").setEphemeral(true).queue();
            return;
        }
        
        hook.deleteMessageById(event.getMessageId()).queue();

        switch (ic.groupAccessType)
        {
        case PUBLIC: {
            if (!this.discord.scarlet.vrc.checkSelfUserHasVRChatPermission(GroupPermissions.group_instance_public_create))
            {
                hook.sendMessage(this.discord.scarlet.vrc.messageNeedPerms(GroupPermissions.group_instance_public_create)).setEphemeral(true).queue();
            }
        } break;
        case PLUS: {
            if (!this.discord.scarlet.vrc.checkSelfUserHasVRChatPermission(GroupPermissions.group_instance_plus_create))
            {
                hook.sendMessage(this.discord.scarlet.vrc.messageNeedPerms(GroupPermissions.group_instance_plus_create)).setEphemeral(true).queue();
            }
        } break;
        case MEMBERS: {
            if (!this.discord.scarlet.vrc.checkSelfUserHasVRChatPermission(GroupPermissions.group_instance_open_create))
            {
                hook.sendMessage(this.discord.scarlet.vrc.messageNeedPerms(GroupPermissions.group_instance_open_create)).setEphemeral(true).queue();
            }
            if (ic.roleIds != null && !this.discord.scarlet.vrc.checkSelfUserHasVRChatPermission(GroupPermissions.group_instance_restricted_create))
            {
                hook.sendMessage(this.discord.scarlet.vrc.messageNeedPerms(GroupPermissions.group_instance_restricted_create)).setEphemeral(true).queue();
            }
        } break;
        }
        
        if (ic.ageGate != null && ic.ageGate.booleanValue() && !this.discord.scarlet.vrc.checkSelfUserHasVRChatPermission(GroupPermissions.group_instance_age_gated_create))
        {
            hook.sendMessage(this.discord.scarlet.vrc.messageNeedPerms(GroupPermissions.group_instance_age_gated_create)).setEphemeral(true).queue();
        }
        
        Instance instance;
        try
        {
//            instance = new InstancesApi(this.discord.scarlet.vrc.client).createInstance(ic.createRequest());
            instance = this.discord.scarlet.vrc.createInstanceEx(ic.createRequestEx());
        }
        catch (ApiException apiex)
        {
            String message = apiex.getResponseBody();
            try
            {
                message = JSON.<io.github.vrchatapi.model.Error>deserialize(message, io.github.vrchatapi.model.Error.class).getError().getMessage();
            }
            catch (Exception ex)
            {
            }
            hook.sendMessageFormat("Failed to create new instance: %s", message).setEphemeral(true).queue();
            return;
        }
        
        this.discord.scarlet.pendingModActions.addPending(GroupAuditType.INSTANCE_CREATE, instance.getId(), vrcActorId);
        String worldName = this.discord.getLocationName(instance.getWorldId());
        
        LOG.info(String.format("%s (%s) opened an instance of %s: %s", vrcActorDisplayName, vrcActorId, worldName, instance.getId()));
        hook.sendMessageFormat("Created [new %s instance](https://vrchat.com/home/launch?worldId=%s&instanceId=%s)", worldName, instance.getWorldId(), instance.getInstanceId()).setEphemeral(true).queue();
        
    }

    @ButtonClk("new-instance-cancel")
    @Ephemeral
    public void newInstanceCancel(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        String ictoken = parts[1];
        this.discord.instanceCreation.remove(ictoken);
        hook.deleteMessageById(event.getMessageId()).queue();
        hook.deleteOriginal().queue();
    }

    @ButtonClk("new-instance-modal")
    public void newInstanceModal(ButtonInteractionEvent event)
    {
        String[] parts = event.getButton().getId().split(":");
        String ictoken = parts[1];
        InstanceCreation ic = this.discord.instanceCreation.get(ictoken);
        event.replyModal(Modal.create("new-instance-modal:"+ictoken, "Additional options")
                .addActionRow(TextInput.create("display-name:"+ictoken, "Display name (unknown purpose)", TextInputStyle.SHORT)
                    .setValue(ic == null ? null : ic.displayName)
                    .build())
                .build())
            .queue();
    }

    @ModalSub("new-instance-modal")
    public void newInstanceModal(ModalInteractionEvent event)
    {
        String[] parts = event.getModalId().split(":");
        String ictoken = parts[1];
        String displayName = event.getValue("display-name:"+parts[1]).getAsString();
        InstanceCreation ic = this.discord.instanceCreation.get(ictoken);
        if (ic == null)
        {
            event.reply("This interaction has timed out.").queue($ -> $.deleteOriginal().queueAfter(3_000L, TimeUnit.MILLISECONDS));
            return;
        }
        ic.displayName = displayName;
        event.deferEdit().queue();
    }

    @StringSel("new-instance-region")
    public void newInstanceRegion(StringSelectInteractionEvent event)
    {
        String[] parts = event.getSelectMenu().getId().split(":");
        String ictoken = parts[1];
        InstanceCreation ic = this.discord.instanceCreation.get(ictoken);
        if (ic != null) try
        {
            ic.region = event.getValues().stream().findFirst().map(InstanceRegion::fromValue).get();
            event.deferEdit().queue();
            return;
        }
        catch (RuntimeException rex)
        {
        }
        event.reply("Interaction timed out").setEphemeral(true).queue($ -> $.deleteOriginal().queueAfter(3_000L, TimeUnit.MILLISECONDS));
    }

    @StringSel("new-instance-access-type")
    public void newInstanceAccessType(StringSelectInteractionEvent event)
    {
        String[] parts = event.getSelectMenu().getId().split(":");
        String ictoken = parts[1];
        InstanceCreation ic = this.discord.instanceCreation.get(ictoken);
        if (ic != null) try
        {
            ic.groupAccessType = event.getValues().stream().findFirst().map(GroupAccessType::fromValue).get();
            event.deferEdit().queue();
            return;
        }
        catch (RuntimeException rex)
        {
        }
        event.reply("Interaction timed out").setEphemeral(true).queue($ -> $.deleteOriginal().queueAfter(3_000L, TimeUnit.MILLISECONDS));
    }

    @StringSel("new-instance-roles")
    public void newInstanceRoles(StringSelectInteractionEvent event)
    {
        String[] parts = event.getSelectMenu().getId().split(":");
        String ictoken = parts[1];
        InstanceCreation ic = this.discord.instanceCreation.get(ictoken);
        if (ic != null) try
        {
            ic.roleIds = new ArrayList<>(event.getValues());
            event.deferEdit().queue();
            return;
        }
        catch (RuntimeException rex)
        {
        }
        event.reply("Interaction timed out").setEphemeral(true).queue($ -> $.deleteOriginal().queueAfter(3_000L, TimeUnit.MILLISECONDS));
    }

    @StringSel("new-instance-flags")
    public void newInstanceFlags(StringSelectInteractionEvent event)
    {
        String[] parts = event.getSelectMenu().getId().split(":");
        String ictoken = parts[1];
        InstanceCreation ic = this.discord.instanceCreation.get(ictoken);
        if (ic != null) try
        {
            ic.queueEnabled = event.getValues().contains("queueEnabled");
            ic.hardClose = event.getValues().contains("hardClose");
            ic.ageGate = event.getValues().contains("ageGate");
//            ic.playerPersistenceEnabled = event.getValues().contains("playerPersistenceEnabled");
//            ic.instancePersistenceEnabled = event.getValues().contains("instancePersistenceEnabled");
            ic.contentSettings_drones = event.getValues().contains("contentSettings.drones");
            ic.contentSettings_emoji = event.getValues().contains("contentSettings.emoji");
            ic.contentSettings_pedestals = event.getValues().contains("contentSettings.pedestals");
            ic.contentSettings_prints = event.getValues().contains("contentSettings.prints");
            ic.contentSettings_stickers = event.getValues().contains("contentSettings.stickers");
            event.deferEdit().queue();
            return;
        }
        catch (RuntimeException rex)
        {
        }
        event.reply("Interaction timed out").setEphemeral(true).queue($ -> $.deleteOriginal().queueAfter(3_000L, TimeUnit.MILLISECONDS));
    }

    @ButtonClk("edit-desc")
    public void editDesc(ButtonInteractionEvent event)
    {
        String[] parts = event.getButton().getId().split(":");
        String auditEntryId = parts[1];
        TextInput.Builder ti = TextInput
            .create("input-desc:"+auditEntryId, "Input description", TextInputStyle.PARAGRAPH)
            .setRequired(true)
            .setPlaceholder("Event description")
            ;
        
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta != null)
            ti.setValue(auditEntryMeta.entryDescription);
        
        Modal.Builder m = Modal.create("edit-desc:"+auditEntryId, "Edit description")
            .addActionRow(ti.build())
            ;
        
        event.replyModal(m.build()).queue();
    }

    @ModalSub("edit-desc")
    public void editDesc(ModalInteractionEvent event)
    {
        String[] parts = event.getModalId().split(":");
        String desc = event.getValue("input-desc:"+parts[1]).getAsString();
        event.replyFormat("### Setting description:\n%s", desc).setEphemeral(true).queue();
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata_setDescription(parts[1], desc);
        this.updateAuxMessage(event.getChannel(), auditEntryMeta);
    }

    @ButtonClk("vrchat-report")
    @Ephemeral
    public void vrchatReport(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        
        String auditEntryId = parts[1];
        
        String targetUserId = null,
               targetDisplayName = null,
               actorUserId = null,
               actorDisplayName = null,
               metaDescription = null,
               metaTags[] = null;
        
        String eventUserSnowflake = event.getUser().getId(),
               eventUserId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(eventUserSnowflake);
        
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta != null && auditEntryMeta.entry != null)
        {
            targetUserId = auditEntryMeta.entry.getTargetId();
            actorUserId = auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorId : auditEntryMeta.entry.getActorId();
            actorDisplayName = auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorDisplayName : auditEntryMeta.entry.getActorDisplayName();
            metaDescription = auditEntryMeta.entryDescription;
            metaTags = auditEntryMeta.entryTags.toArray();

            long within1day = System.currentTimeMillis() - 86400_000L;
            User targetUser = this.discord.scarlet.vrc.getUser(targetUserId, within1day);
            if (targetUser != null)
            {
                targetDisplayName = targetUser.getDisplayName();
            }
        }
        
        String reportSubject = targetDisplayName;
        Long targetJoined = parts.length < 3 ? (auditEntryMeta != null ? auditEntryMeta.getAuxData("targetJoined", JsonElement::getAsLong) : null) : Long.parseUnsignedLong(parts[2]);
        
        String location = auditEntryMeta == null ? null : auditEntryMeta.getData("location");
        
        ScarletVRChatReportTemplate.FormatParams params = this.discord.scarlet.vrcReport.new FormatParams()
            .group(this.discord.scarlet.vrc, this.discord.scarlet.vrc.groupId, this.discord.scarlet.vrc.group)
            .location(this.discord.scarlet.vrc, location)
            .actor(this.discord.scarlet.vrc, actorUserId, actorDisplayName)
            .target(this.discord.scarlet.vrc, targetUserId, targetDisplayName)
            .targetEx(
                targetJoined == null ? null : OffsetDateTime.ofInstant(Instant.ofEpochSecond(targetJoined.longValue()), ZoneOffset.UTC).format(ScarletVRChatReportTemplate.DTF),
                auditEntryMeta == null ? null : auditEntryMeta.entry.getCreatedAt().format(ScarletVRChatReportTemplate.DTF)
            )
            .audit(
                auditEntryId,
                metaDescription,
                metaTags == null ? null : Arrays.stream(metaTags).map(this.discord.scarlet.moderationTags::getTagLabel).filter(Objects::nonNull).toArray(String[]::new)
            )
            ;
        
        StringBuilder reportDesc = new StringBuilder();
        if (metaDescription != null)
        {
            reportDesc.append(metaDescription);
        }
        if (metaTags != null && metaTags.length > 0)
        {
            if (reportDesc.length() != 0)
            {
                reportDesc.append("<br><br>");
            }
            reportDesc.append("User's Group internal moderation tags:<ul>");
            for (String metaTag : metaTags)
            {
                reportDesc.append("<li>")
                          .append(this.discord.scarlet.moderationTags.getTagLabel(metaTag))
                          .append("</li>");
            }
            reportDesc.append("</ul>");
        }
        // Footer
        {
            if (reportDesc.length() != 0)
            {
                reportDesc.append("<br><br>");
            }
            reportDesc.append("Partially autofilled with ")
                      .append(Scarlet.NAME)
                      .append(" version ")
                      .append(Scarlet.VERSION)
                      .append("<br>")
                      .append("Group ID: ")
                      .append(this.discord.scarlet.vrc.groupId)
                      .append("<br>")
                      .append("Audit ID: ")
                      .append(auditEntryId)
                      ;
        }
        
        String requestingUserId = eventUserId != null ? eventUserId : actorUserId;
        
        String requestingEmail = this.discord.requestingEmail.get();
        
        String link = VRChatHelpDeskURLs.newModerationRequest_account_other(
            requestingEmail,
            targetUserId,
            reportSubject,
            reportDesc.length() == 0 ? null : reportDesc.toString()
        );
        
        
        hook.sendMessageEmbeds(new EmbedBuilder()
                .setTitle("Help desk link")
                .appendDescription("[Open new VRChat User Moderation Request](<")
                .appendDescription(link)
                .appendDescription(">)")
            .build(), new EmbedBuilder()
                .setTitle("Templated link")
                .appendDescription("[Templated link](<")
                .appendDescription(params.url(requestingEmail, requestingUserId, reportSubject, this.discord.appendTemplateFooter.get()))
                .appendDescription(">)")
            .build())
            .setContent(eventUserId != null ? null : "## WARNING\nThis link autofills the requesting user id of the **audit actor, not necessarily you**\nAssociate your Discord and VRChat ids with `/associate-ids`.\n\n")
            .setEphemeral(true)
            .queue();
    }

    @ButtonClk("view-potential-avatar-matches")
    @Ephemeral
    public void viewPotentialAvatarMatches(ButtonInteractionEvent event, InteractionHook hook)
    {
        MessageEmbed[] embeds = event
            .getMessage()
            .getEmbeds()
            .stream()
            .map(MessageEmbed::getDescription)
            .filter(Objects::nonNull)
            .map($ -> $.split("\\R"))
            .flatMap(Stream::of)
            .map(this.discord.scarlet.vrc::getAvatar)
            .filter(Objects::nonNull)
            .map($ -> 
                new EmbedBuilder()
                .setAuthor($.getAuthorName(), "https://vrchat.com/home/user/"+$.getAuthorId(), null)
                .setTitle($.getName(), "https://vrchat.com/home/avatar/"+$.getId())
                .setThumbnail($.getImageUrl() == null || $.getImageUrl().isEmpty() ? null : $.getImageUrl())
                .setDescription($.getDescription() == null || $.getDescription().isEmpty() ? null : $.getDescription())
                .addField("Report avatar", MarkdownUtil.maskedLink("link", VRChatHelpDeskURLs.newModerationRequest_content_avatar(this.discord.requestingEmail.get(), $.getId(), null, null)), false)
                .build()
            )
            .toArray(MessageEmbed[]::new)
            ;
        this.discord.interactions.new Pagination(event.getId(), embeds, 4).queue(hook);
    }

    @ButtonClk("view-snapshot-user")
    @Ephemeral
    public void viewSnapshotUser(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        String auditEntryId = parts[1];
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta == null)
        {
            hook.sendMessage("Failed to find the audit event.").setEphemeral(true).queue();
            return;
        }
        if (auditEntryMeta.snapshotTargetUser == null)
        {
            hook.sendMessage("This event has no user snapshot available.").setEphemeral(true).queue();
            return;
        }
        MessageEmbed[] embeds = auditEntryMeta.snapshotTargetUser.entrySet().stream().map($ ->
        {
            EmbedBuilder embed = new EmbedBuilder().setTitle($.getKey());
            JsonElement value = $.getValue();
            if (value.isJsonObject())
            {
                value.getAsJsonObject().entrySet().forEach($$ -> embed.addField($$.getKey(), String.valueOf($$.getValue()), false));
            }
            else if (value.isJsonArray())
            {
                value.getAsJsonArray().forEach($$ -> embed.addField("", String.valueOf($$), false));
            }
            else
            {
                embed.setDescription(String.valueOf(value));
            }
            return embed.build();
        }).toArray(MessageEmbed[]::new);
        this.discord.interactions.new Pagination(event.getId(), embeds, 10).queue(hook);
    }

    @ButtonClk("view-snapshot-user-groups")
    @Ephemeral
    public void viewSnapshotUserGroups(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        String auditEntryId = parts[1];
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta == null)
        {
            hook.sendMessage("Failed to find the audit event.").setEphemeral(true).queue();
            return;
        }
        if (auditEntryMeta.snapshotTargetUserGroups == null)
        {
            hook.sendMessage("This event has no user snapshot available.").setEphemeral(true).queue();
            return;
        }
        MessageEmbed[] embeds = auditEntryMeta.snapshotTargetUserGroups.asList().stream().map($ ->
        {
            JsonObject object = $.getAsJsonObject();
            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor(object.get(LimitedUserGroups.SERIALIZED_NAME_SHORT_CODE).getAsString()+"."+object.get(LimitedUserGroups.SERIALIZED_NAME_DISCRIMINATOR).getAsString(), null, object.get(LimitedUserGroups.SERIALIZED_NAME_ICON_URL).getAsString());
            embed.setTitle(MarkdownSanitizer.escape(object.get(LimitedUserGroups.SERIALIZED_NAME_NAME).getAsString()), "https://vrchat.com/home/groups/"+object.get(LimitedUserGroups.SERIALIZED_NAME_GROUP_ID).getAsString());
            embed.setDescription(object.get(LimitedUserGroups.SERIALIZED_NAME_DESCRIPTION).getAsString());
            embed.setThumbnail(object.get(LimitedUserGroups.SERIALIZED_NAME_BANNER_URL).getAsString());
            return embed.build();
        }).toArray(MessageEmbed[]::new);
        this.discord.interactions.new Pagination(event.getId(), embeds, 10).queue(hook);
    }

    @ButtonClk("view-snapshot-user-represented-group")
    @Ephemeral
    public void viewSnapshotUserRepresentedGroup(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        String auditEntryId = parts[1];
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta == null)
        {
            hook.sendMessage("Failed to find the audit event.").setEphemeral(true).queue();
            return;
        }
        if (auditEntryMeta.snapshotTargetUserRepresentedGroup == null)
        {
            hook.sendMessage("This event has no user represented group snapshot available.").setEphemeral(true).queue();
            return;
        }
        MessageEmbed[] embeds = auditEntryMeta.snapshotTargetUserRepresentedGroup.entrySet().stream().map($ ->
        {
            EmbedBuilder embed = new EmbedBuilder().setTitle($.getKey());
            JsonElement value = $.getValue();
            if (value.isJsonObject())
            {
                value.getAsJsonObject().entrySet().forEach($$ -> embed.addField($$.getKey(), String.valueOf($$.getValue()), false));
            }
            else if (value.isJsonArray())
            {
                value.getAsJsonArray().forEach($$ -> embed.addField("", String.valueOf($$), false));
            }
            else
            {
                embed.setDescription(String.valueOf(value));
            }
            return embed.build();
        }).toArray(MessageEmbed[]::new);
        this.discord.interactions.new Pagination(event.getId(), embeds, 10).queue(hook);
    }

    @ButtonClk("submit-evidence")
    @Ephemeral
    public void submitEvidence(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        String messageSnowflake = parts[1];
        
        if (!this.discord.evidenceEnabled.get())
        {
            hook.sendMessage("This feature is not enabled.").setEphemeral(true).queue();
            return;
        }
        
        String evidenceRoot = this.discord.evidenceRoot;
        
        if (evidenceRoot == null || (evidenceRoot = evidenceRoot.trim()).isEmpty())
        {
            hook.sendMessage("The evidence folder hasn't been specified.").setEphemeral(true).queue();
            return;
        }
        
        if (!event.getChannelType().isThread())
        {
            hook.sendMessage("You must reply to an audit event message in the relevant thread.").setEphemeral(true).queue();
            return;
        }
        Message message = event.getChannel().retrieveMessageById(messageSnowflake).complete();
        
        String[] partsRef = event.getChannel()
            .asThreadChannel()
            .getHistoryFromBeginning(2)
            .complete()
            .getRetrievedHistory()
            .stream()
            .map(Message::getButtons)
            .flatMap(List::stream)
            .filter($ -> $.getId().startsWith("edit-tags:"))
            .findFirst()
            .map($ -> $.getId().split(":"))
            .orElse(null)
            ;
        
        if (partsRef == null)
        {
            hook.sendMessage("Could not determine audit event id.").setEphemeral(true).queue();
            return;
        }
        
        List<Message.Attachment> attachments = message.getAttachments();
        
        if (attachments.isEmpty())
        {
            hook.sendMessage("No attachments.").setEphemeral(true).queue();
            return;
        }
        
        String auditEntryId = partsRef[1];
        
        ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
        if (auditEntryMeta == null || auditEntryMeta.entry == null)
        {
            hook.sendMessage("Could not load audit event.").setEphemeral(true).queue();
            return;
        }
        
        String auditEntryTargetId = auditEntryMeta.entry.getTargetId();
        
        String requesterSf = event.getUser().getId(),
               requesterDisplayName = event.getUser().getEffectiveName();
        OffsetDateTime timestamp = event.getTimeCreated();
        
        // TODO : better model for avoiding race conditions
        synchronized (auditEntryTargetId.intern())
        {
            
            ScarletData.UserMetadata auditEntryTargetUserMeta = this.discord.scarlet.data.userMetadata(auditEntryTargetId);
            if (auditEntryTargetUserMeta == null)
                auditEntryTargetUserMeta = new ScarletData.UserMetadata();
            
            // TODO : async ?
            
            for (Message.Attachment attachment : attachments)
            {
                String fileName = attachment.getFileName(),
                       attachmentUrl = attachment.getUrl(),
                       attachmentProxyUrl = attachment.getProxyUrl();
                
                ScarletEvidence.FormatParams filePath = new ScarletEvidence.FormatParams()
                    .group(this.discord.scarlet.vrc, auditEntryMeta.entry.getGroupId(), null)
                    .actor(this.discord.scarlet.vrc,
                           auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorId : auditEntryMeta.entry.getActorId(),
                           auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorDisplayName : auditEntryMeta.entry.getActorDisplayName())
                    .target(this.discord.scarlet.vrc, auditEntryMeta.entry.getTargetId(), null)
                    .file(fileName)
                    .audit(auditEntryId)
                    .index(auditEntryTargetUserMeta.getUserCaseEvidenceCount())
                    ;
                File dest = filePath.nextFile(evidenceRoot, this.discord.evidenceFilePathFormat.get());
                if (dest.isFile())
                {
                    hook.sendMessageFormat("File '%s' already exists, skipping.", fileName).setEphemeral(true).queue();
                }
                else try
                {
                    if (!dest.getParentFile().isDirectory())
                        dest.getParentFile().mkdirs();
                    attachment.getProxy().downloadToFile(dest).join();
                    
                    auditEntryTargetUserMeta.addUserCaseEvidence(new ScarletData.EvidenceSubmission(auditEntryId, requesterSf, requesterDisplayName, timestamp, fileName, filePath.prevFormat(), attachmentUrl, attachmentProxyUrl));
                    
                    hook.sendMessageFormat("Saved '%s'.", filePath.prevFormat()).setEphemeral(true).queue();
                    LOG.info(String.format("%s (<@%s>) saved evidence to '%s'.", requesterDisplayName, requesterSf, filePath.prevFormat()));
                }
                catch (Exception ex)
                {
                    hook.sendMessageFormat("Exception saving '%s' as '%s'.", attachment.getFileName(), filePath.prevFormat()).setEphemeral(true).queue();
                    LOG.error("Exception whilst saving attachment", ex);
                }
            }
            
            this.discord.scarlet.data.userMetadata(auditEntryTargetId, auditEntryTargetUserMeta);
        }
    }

    @ButtonClk("import-watched-groups")
    @Ephemeral
    public void importWatchedGroups(ButtonInteractionEvent event, InteractionHook hook)
    {
        String[] parts = event.getButton().getId().split(":");
        String messageSnowflake = parts[1];
        
        Message message = event.getChannel().retrieveMessageById(messageSnowflake).complete();
        
        List<Message.Attachment> attachments = message.getAttachments();
        
        if (attachments.isEmpty())
        {
            hook.sendMessage("No attachments.").setEphemeral(true).queue();
            return;
        }
        
        String requesterSf = event.getUser().getId(),
               requesterDisplayName = event.getUser().getEffectiveName();
        
        LOG.info(String.format("%s (<@%s>) Importing watched groups", requesterDisplayName, requesterSf));
        
        for (Message.Attachment attachment : attachments)
        {
            String fileName = attachment.getFileName(),
                   attachmentUrl = attachment.getUrl();
            
            if (fileName.endsWith(".csv"))
            {
                LOG.info("Importing watched groups legacy CSV from attachment: "+fileName);
                try (Reader reader = new InputStreamReader(HttpURLInputStream.get(attachmentUrl)))
                {
                    if (this.discord.scarlet.watchedGroups.importLegacyCSV(reader, true))
                    {
                        LOG.info("Successfully imported watched groups legacy CSV");
                        hook.sendMessageFormat("Successfully imported watched groups legacy CSV").setEphemeral(true).queue();
                    }
                    else
                    {
                        LOG.warn("Failed to import watched groups legacy CSV with unknown reason");
                        hook.sendMessageFormat("Failed to import watched groups legacy CSV with unknown reason").setEphemeral(true).queue();
                    }
                }
                catch (Exception ex)
                {
                    LOG.error("Exception importing watched groups legacy CSV from attachment: "+fileName, ex);
                    hook.sendMessageFormat("Exception while importing %s: %s", fileName, ex).setEphemeral(true).queue();
                }
            }
            else if (fileName.endsWith(".json"))
            {
                LOG.info("Importing watched groups JSON from attachment: "+fileName);
                try (Reader reader = new InputStreamReader(HttpURLInputStream.get(attachmentUrl)))
                {
                    if (this.discord.scarlet.watchedGroups.importJson(reader, true))
                    {
                        LOG.info("Successfully imported watched groups JSON");
                        hook.sendMessageFormat("Successfully imported watched groups JSON").setEphemeral(true).queue();
                    }
                    else
                    {
                        LOG.warn("Failed to import watched groups JSON with unknown reason");
                        hook.sendMessageFormat("Failed to import watched groups JSON with unknown reason").setEphemeral(true).queue();
                    }
                }
                catch (Exception ex)
                {
                    LOG.error("Exception importing watched groups JSON from attachment: "+fileName, ex);
                    hook.sendMessageFormat("Exception while importing %s: %s", fileName, ex).setEphemeral(true).queue();
                }
            }
            else
            {
                LOG.warn("Skipping attachment: "+fileName);
                hook.sendMessageFormat("File '%s' is not importable.", fileName).setEphemeral(true).queue();
            }
        }
    }

    @StringSel("set-audit-aux-webhooks")
    public void setAuditAuxWebhooks(StringSelectInteractionEvent event)
    {
        String[] parts = event.getSelectMenu().getId().split(":");
        String auditType0 = parts[1];
        GroupAuditType auditType = GroupAuditType.of(auditType0);
        if (auditType == null)
        {
            event.replyFormat("%s isn't a valid audit log event type", auditType0).setEphemeral(true).queue();
            return;
        }
        if (event.getValues().isEmpty())
        {
            event.replyFormat("Removing auxiliary webhooks for %s", auditType0).setEphemeral(true).queue();
            this.discord.auditType2scarletAuxWh.remove(auditType0);
        }
        else
        {
            event.replyFormat("Setting auxiliary webhooks for %s:\n%s", auditType0, event.getValues().stream().collect(Collectors.joining(", "))).setEphemeral(true).queue();
            this.discord.auditType2scarletAuxWh.put(auditType0, new UniqueStrings(event.getValues()));
        }
    }

}
