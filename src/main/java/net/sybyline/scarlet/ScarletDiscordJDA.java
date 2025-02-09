package net.sybyline.scarlet;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.JSON;
import io.github.vrchatapi.api.GroupsApi;
import io.github.vrchatapi.api.UsersApi;
import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.GroupRole;
import io.github.vrchatapi.model.LimitedUserGroups;
import io.github.vrchatapi.model.RepresentedGroup;
import io.github.vrchatapi.model.User;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.sybyline.scarlet.ScarletData.AuditEntryMetadata;
import net.sybyline.scarlet.util.Pacer;
import net.sybyline.scarlet.util.VRChatHelpDeskURLs;
import net.sybyline.scarlet.util.MiscUtils;

public class ScarletDiscordJDA implements ScarletDiscord
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/JDA");

    public ScarletDiscordJDA(Scarlet scarlet, File discordBotFile)
    {
        this.scarlet = scarlet;
        this.discordBotFile = discordBotFile;
        this.load();
        this.jda = JDABuilder
            .createDefault(this.token)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT)
            .addEventListeners(new JDAEvents())
            .build();
        this.init();
    }

    @Override
    public void close() throws IOException
    {
        this.jda.shutdown();
        try
        {
            if (this.jda.awaitShutdown(10_000L, TimeUnit.MILLISECONDS))
            {
                this.jda.shutdownNow();
                this.jda.awaitShutdown();
            }
        }
        catch (Exception ex)
        {
            this.jda.shutdownNow();
            ex.printStackTrace();
        }
    }

    final Scarlet scarlet;
    final File discordBotFile;
    final JDA jda;
    String token, guildSf, evidenceRoot;
    Map<String, String> scarletPermission2roleSf = new HashMap<>();
    Map<String, String> auditType2channelSf = new HashMap<>();
    Map<String, Integer> auditType2color = new HashMap<>();

    static final String[] AUDIT_EVENT_IDS = MiscUtils.map(GroupAuditType.values(), String[]::new, GroupAuditType::id);
    static final String[] SCARLET_PERMISSION_IDS = MiscUtils.map(ScarletPermission.values(), String[]::new, ScarletPermission::id);

    void init()
    {
        try
        {
            this.jda.awaitReady();
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Awaiting JDA", ex);
        }
        this.jda.updateCommands()
            .addCommands(
                Commands.slash("create-or-update-moderation-tag", "Creates or updates a moderation tag")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                    .addOption(OptionType.STRING, "value", "The internal name of the tag", true, true)
                    .addOption(OptionType.STRING, "label", "The VRChat Group Audit Log event type")
                    .addOption(OptionType.STRING, "description", "The VRChat Group Audit Log event type"),
                Commands.slash("delete-moderation-tag", "Deletes a moderation tag")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                    .addOption(OptionType.STRING, "value", "The internal name of the tag", true, true),
                Commands.slash("associate-ids", "Associates a specific Discord user with a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                    .addOption(OptionType.USER, "discord-user", "The Discord user", true)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true),
                Commands.slash("vrchat-user-info", "Lists internal and audit information for a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true),
                Commands.slash("query-target-history", "Queries audit events targeting a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true)
                    .addOption(OptionType.INTEGER, "days-back", "The number of days into the past to search for events"),
                Commands.slash("query-actor-history", "Queries audit events actored by a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true)
                    .addOption(OptionType.INTEGER, "days-back", "The number of days into the past to search for events"),
                Commands.slash("set-audit-channel", "Sets a given text channel as the channel certain audit event types use")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                    .addOption(OptionType.STRING, "audit-event-type", "The VRChat Group Audit Log event type", true, true)
                    .addOption(OptionType.CHANNEL, "discord-channel", "The Discord text channel to use, or omit to remove entry"),
                Commands.slash("set-permission-role", "Sets a given Scarlet-specific permission to be associated with a given Discord role")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
                    .addOption(OptionType.STRING, "scarlet-permission", "The Scarlet-specific permission", true, true)
                    .addOption(OptionType.ROLE, "discord-role", "The Discord role to use, or omit to remove entry"),
                Commands.slash("config-info", "Shows information about the current configurstion")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED),
                Commands.message("submit-evidence")
                    .setGuildOnly(true)
                    .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
            )
            .complete();
    }

    public static class JDASettingsSpec
    {
        public String token = null,
                      guildSf = null,
                      evidenceRoot = null;
        public Map<String, String> scarletPermission2roleSf = new HashMap<>();
        public Map<String, String> auditType2channelSf = new HashMap<>();
        public Map<String, String> auditType2color = new HashMap<>();
    }

    public void load()
    {
        JDASettingsSpec spec;
        try (FileReader fr = new FileReader(this.discordBotFile))
        {
            spec = Scarlet.GSON_PRETTY.fromJson(fr, JDASettingsSpec.class);
        }
        catch (Exception e)
        {
            spec = null;
        }
        
        if (spec == null)
            spec = new JDASettingsSpec();
        
        boolean save = false;
        
        if (spec.token == null)
        {
            spec.token = this.scarlet.settings.requireInput("Discord bot token", true);
            save = true;
        }
        
        if (spec.guildSf == null)
        {
            spec.guildSf = this.scarlet.settings.requireInput("Discord guild snowflake", false);
            save = true;
        }
        
        if (save) this.save(spec);
        
        this.token = spec.token;
        this.guildSf = spec.guildSf;
        this.evidenceRoot = spec.evidenceRoot;
        this.scarletPermission2roleSf = spec.scarletPermission2roleSf == null ? new HashMap<>() : new HashMap<>(spec.scarletPermission2roleSf);
        this.auditType2channelSf = spec.auditType2channelSf == null ? new HashMap<>() : new HashMap<>(spec.auditType2channelSf);
        Map<String, Integer> auditType2color = new HashMap<>();
        if (spec.auditType2color != null && !spec.auditType2color.isEmpty())
            spec.auditType2color.forEach((auditType, colorString) -> {
                try
                {
                    int argb = Integer.parseUnsignedInt(colorString, 16);
                    auditType2color.put(auditType, argb);
                }
                catch (Exception ex)
                {
                    LOG.warn("Exception whilst parsing colors", ex);
                }
            });
        this.auditType2color = auditType2color;
    }
    public void save()
    {
        JDASettingsSpec spec = new JDASettingsSpec();
        spec.token = this.token;
        spec.guildSf = this.guildSf;
        spec.evidenceRoot = this.evidenceRoot;
        spec.auditType2channelSf = new HashMap<>(this.auditType2channelSf);
        spec.scarletPermission2roleSf = new HashMap<>(this.scarletPermission2roleSf);
        Map<String, String> auditType2color = new HashMap<>();
        if (this.auditType2color != null && this.auditType2color.isEmpty())
            this.auditType2color.forEach((auditType, color) -> auditType2color.put(auditType, Integer.toHexString(color)));
        spec.auditType2color = auditType2color;
        this.save(spec);
    }
    void save(JDASettingsSpec spec)
    {
        try (FileWriter fw = new FileWriter(this.discordBotFile))
        {
            Scarlet.GSON_PRETTY.toJson(spec, JDASettingsSpec.class, fw);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    class JDAEvents extends ListenerAdapter
    {
        JDAEvents()
        {
        }

        @Override
        public void onMessageContextInteraction(MessageContextInteractionEvent event)
        {
            try
            {
                switch (event.getName())
                {
                case "submit-evidence": {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_SUBMIT_EVIDENCE);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            event.reply("You do not have permission to submit event evidence.").setEphemeral(true).queue();
                            return;
                        }
                    }
                    
                    String evidenceRoot = ScarletDiscordJDA.this.evidenceRoot;
                    
                    if (evidenceRoot == null || (evidenceRoot = evidenceRoot.trim()).isEmpty())
                    {
                        event.reply("This feature is not enabled.").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (!event.getChannelType().isThread())
                    {
                        event.reply("You must reply to an audit event message in the relevant thread.").setEphemeral(true).queue();
                        return;
                    }
                    Message message = event.getTarget();
                    
                    MessageReference ref = message.getMessageReference();
                    
                    if (ref == null)
                    {
                        event.reply("You must reply to an audit event message in the relevant thread.").setEphemeral(true).queue();
                        return;
                    }
                    
                    Message replyTarget = ref.getMessage();
                    
                    if (replyTarget == null)
                    {
                        replyTarget = ref.resolve().complete();
                    }
                    
                    if (replyTarget == null)
                    {
                        event.reply("The target message is no longer available.").setEphemeral(true).queue();
                        return;
                    }
                    
                    String[] parts = replyTarget
                        .getButtons()
                        .stream()
                        .filter($ -> $.getId().startsWith("edit-tags:"))
                        .findFirst()
                        .map($ -> $.getId().split(":"))
                        .orElse(null)
                        ;
                    
                    if (parts == null)
                    {
                        event.reply("Could not determine audit event id.").setEphemeral(true).queue();
                        return;
                    }
                    
                    List<Message.Attachment> attachments = message.getAttachments();
                    
                    if (attachments.isEmpty())
                    {
                        event.reply("No attachments.").setEphemeral(true).queue();
                        return;
                    }
                    
                    String auditEntryId = parts[1];
                    
                    event.deferReply(true).queue();
                    InteractionHook deferred = event.getHook();
                    
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
                    if (auditEntryMeta == null || auditEntryMeta.entry == null)
                    {
                        deferred.sendMessage("Could not load audit event.").setEphemeral(true).queue();
                        return;
                    }
                    
                    String auditEntryTargetId = auditEntryMeta.entry.getTargetId();
                    
                    File evidenceUserDir = new File(evidenceRoot, auditEntryTargetId);
                    if (!evidenceUserDir.isDirectory())
                        evidenceUserDir.mkdirs();
                    
                    String requesterSf = event.getUser().getId(),
                           requesterDisplayName = event.getUser().getEffectiveName();
                    OffsetDateTime timestamp = event.getTimeCreated();
                    
                    // TODO : better model for avoiding race conditions
                    synchronized (auditEntryTargetId.intern())
                    {
                        
                        ScarletData.UserMetadata auditEntryTargetUserMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata(auditEntryTargetId);
                        if (auditEntryTargetUserMeta == null)
                            auditEntryTargetUserMeta = new ScarletData.UserMetadata();
                        
                        // TODO : async ?
                        
                        for (Message.Attachment attachment : attachments)
                        {
                            String fileName = attachment.getFileName(),
                                   attachmentUrl = attachment.getUrl(),
                                   attachmentProxyUrl = attachment.getProxyUrl();
                            
                            File dest = new File(evidenceUserDir, fileName);
                            if (dest.isFile())
                            {
                                deferred.sendMessageFormat("File '%s' already exists, skipping.", fileName).setEphemeral(true).queue();
                            }
                            else try
                            {
                                attachment.getProxy().downloadToFile(dest).join();
                                
                                auditEntryTargetUserMeta.addUserCaseEvidence(new ScarletData.EvidenceSubmission(auditEntryId, requesterSf, requesterDisplayName, timestamp, fileName, attachmentUrl, attachmentProxyUrl));
                                
                                deferred.sendMessageFormat("Saved '%s/%s'.", auditEntryTargetId, fileName).setEphemeral(true).queue();
                                LOG.info(String.format("%s (<@%s>) saved evidence to '%s/%s'.", requesterDisplayName, requesterSf, auditEntryTargetId, fileName));
                            }
                            catch (Exception ex)
                            {
                                deferred.sendMessageFormat("Exception saving '%s'.", attachment.getFileName()).setEphemeral(true).queue();
                                LOG.error("Exception whilst saving attachment", ex);
                            }
                        }
                        
                        ScarletDiscordJDA.this.scarlet.data.userMetadata(auditEntryTargetId, auditEntryTargetUserMeta);
                    
                    }
                    
                } break;
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }

        @Override
        public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event)
        {
            try
            {
                switch (event.getName())
                {
                case "create-or-update-moderation-tag":
                case "delete-moderation-tag": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "value": {
                        event.replyChoiceStrings(ScarletDiscordJDA.this.scarlet.moderationTags.getTagValues()).queue();
                    } break;
                    }
                } break;
                case "set-audit-channel": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "audit-event-type": {
                        event.replyChoiceStrings(AUDIT_EVENT_IDS).queue();
                    } break;
                    }
                } break;
                case "set-permission-role": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "scarlet-permission": {
                        event.replyChoiceStrings(SCARLET_PERMISSION_IDS).queue();
                    } break;
                    }
                } break;
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
        {
            try
            {
                switch (event.getName())
                {
                case "create-or-update-moderation-tag": {
                    
                    String value = event.getOption("value").getAsString(),
                           label = event.getOption("label", OptionMapping::getAsString),
                           description = event.getOption("description", OptionMapping::getAsString);
                    
                    int result = ScarletDiscordJDA.this.scarlet.moderationTags.addOrUpdateTag(value, label, description);
                    
                    switch (result)
                    {
                    default: {
                        event.reply("Failed to add moderation tag: list == null").setEphemeral(true).queue();
                    } break;
                    case -2: {
                        event.reply("Failed to add moderation tag: there are already the maximum of 25 moderation tags").setEphemeral(true).queue();
                    } break;
                    case -1: {
                        event.reply("Failed to add moderation tag: list.add returned false").setEphemeral(true).queue();
                    } break;
                    case 0: {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Created moderation tag `").append(value).append("`:");
                        if (label != null)
                            sb.append(" label=`").append(label).append("`");
                        if (description != null)
                            sb.append(" description=`").append(description).append("`");
                        String msg = sb.toString();
                        LOG.info(msg);
                        event.reply(msg).setEphemeral(true).queue();
                    } break;
                    case 1: {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Updated moderation tag `").append(value).append("`:");
                        if (label != null)
                            sb.append(" label=`").append(label).append("`");
                        if (description != null)
                            sb.append(" description=`").append(description).append("`");
                        String msg = sb.toString();
                        LOG.info(msg);
                        event.reply(msg).setEphemeral(true).queue();
                    } break;
                    }
                    
                } break;
                case "delete-moderation-tag": {
                    
                    String value = event.getOption("value").getAsString();
                    
                    int result = ScarletDiscordJDA.this.scarlet.moderationTags.removeTag(value);
                    
                    switch (result)
                    {
                    default: {
                        event.reply("Failed to delete moderation tag: list == null").setEphemeral(true).queue();
                    } break;
                    case -3: {
                        event.replyFormat("Failed to add the tag: the tag `%s` does not exist", value).setEphemeral(true).queue();
                    } break;
                    case -2: {
                        event.reply("Failed to delete moderation tag: there are no moderation tags").setEphemeral(true).queue();
                    } break;
                    case -1: {
                        event.reply("Failed to delete moderation tag: list.remove returned false").setEphemeral(true).queue();
                    } break;
                    case 0: {
                        String msg = "Deleted moderation tag `"+value+"`";
                        LOG.info(msg);
                        event.reply(msg).setEphemeral(true).queue();
                    } break;
                    }
                    
                } break;
                case "associate-ids": {
                    
                    net.dv8tion.jda.api.entities.User user = event.getOption("discord-user").getAsUser();
                    String vrcId = event.getOption("vrchat-user").getAsString();
                    
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId);
                    if (sc == null)
                    {
                        event.replyFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    ScarletDiscordJDA.this.scarlet.data.linkIdToSnowflake(vrcId, user.getId());
                    LOG.info(String.format("Linking VRChat user %s (%s) to Discord user %s (<@%s>)", sc.getDisplayName(), vrcId, user.getEffectiveName(), user.getId()));
                    event.replyFormat("Associating %s with VRChat user [%s](https://vrchat.com/home/user/%s)", user.getEffectiveName(), sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                    
                } break;
                case "vrchat-user-info": {
                    
                    String vrcId = event.getOption("vrchat-user").getAsString();
                    
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId);
                    if (sc == null)
                    {
                        event.replyFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    ScarletData.UserMetadata userMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata(vrcId);
                    if (userMeta == null)
                    {
                        event.replyFormat("No VRChat user metadata found for [%s](https://vrchat.com/home/user/%s)", sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    event.deferReply(true).queue();
                    InteractionHook deferred = event.getHook();
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("VRChat user metadata for [").append(sc.getDisplayName()).append("](<https://vrchat.com/home/user/").append(vrcId).append(">):");
                    
                    if (userMeta.userSnowflake != null)
                    {
                        sb.append("\n### Linked Discord id:\n`").append(userMeta.userSnowflake).append("`");
                    }
                    
                    if (userMeta.auditEntryIds != null && userMeta.auditEntryIds.length > 0)
                    {
                        sb.append("\n### Moderation events:");
                        for (String auditEntryId : userMeta.auditEntryIds)
                        {
                            if (auditEntryId != null)
                            {
                                sb.append("\n`").append(auditEntryId).append("`");
                                ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
                                if (auditEntryMeta != null && auditEntryMeta.entry != null)
                                {
                                    String messageLink = auditEntryMeta.getMessageUrl();
                                    sb.append(" <t:").append(Long.toUnsignedString(auditEntryMeta.entry.getCreatedAt().toEpochSecond())).append(":f>: ");
                                    if (messageLink == null)
                                    {
                                        sb.append(auditEntryMeta.entry.getDescription());
                                    }
                                    else
                                    {
                                        sb.append("[").append(auditEntryMeta.entry.getDescription()).append("](").append(messageLink).append(")");
                                    }
                                }
                            }
                        }
                    }
                    
                    if (userMeta.evidenceSubmissions != null && userMeta.evidenceSubmissions.length > 0)
                    {
                        sb.append("\n### Evidence submissions:");
                        for (ScarletData.EvidenceSubmission evidenceSubmission : userMeta.evidenceSubmissions)
                        {
                            if (evidenceSubmission != null)
                            {
                                sb.append("\n[`").append(evidenceSubmission.fileName).append("`](<").append(evidenceSubmission.url).append(">)");
                                sb.append(" [(proxy)](<").append(evidenceSubmission.proxyUrl).append(">)");
                                
                                ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(evidenceSubmission.auditEntryId);
                                if (auditEntryMeta != null && auditEntryMeta.hasSomeUrl())
                                {
                                    sb.append(" for [`").append(evidenceSubmission.auditEntryId).append("`](<").append(auditEntryMeta.getSomeUrl()).append(">)");
                                }
                                else
                                {
                                    sb.append(" for `").append(evidenceSubmission.auditEntryId).append("`");
                                }
                                
                                sb.append(" from ").append(evidenceSubmission.submitterDisplayName).append(" (<@").append(evidenceSubmission.submitterSnowflake).append(">)");
                                sb.append(" at <t:").append(Long.toUnsignedString(evidenceSubmission.submissionTime.toEpochSecond())).append(":f>");
                            }
                        }
                    }
                    
                    while (sb.length() > 2000)
                    {
                        int lastBreak = sb.lastIndexOf("\n", 2000);
                        deferred.sendMessage(sb.substring(0, lastBreak)).setEphemeral(true).queue();
                        sb.delete(0, lastBreak + 1);
                    }
                    deferred.sendMessage(sb.toString()).setEphemeral(true).queue();
                    
                } break;
                case "query-target-history": {
                    
                    String vrcId = event.getOption("vrchat-user").getAsString();
                    int daysBack = Math.max(1, Math.min(2048, event.getOption("days-back", 7, OptionMapping::getAsInt)));
                    
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId);
                    if (sc == null)
                    {
                        event.replyFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    event.deferReply(true).queue();
                    InteractionHook deferred = event.getHook();
                    
                    List<GroupAuditLogEntry> entries = ScarletDiscordJDA.this.scarlet.vrc.auditQueryTargeting(vrcId, daysBack);
                    if (entries == null)
                    {
                        deferred.sendMessageFormat("Error querying audit target history for [%s](<https://vrchat.com/home/user/%s>) (%s)", sc.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
                        return;
                    }
                    else if (entries.isEmpty())
                    {
                        deferred.sendMessageFormat("No audit target history for [%s](<https://vrchat.com/home/user/%s>) (%s)", sc.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("VRChat audit target history for [").append(sc.getDisplayName()).append("](<https://vrchat.com/home/user/").append(vrcId).append(">):");
                    
                    ScarletData.UserMetadata userMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata(vrcId);
                    if (userMeta != null && userMeta.userSnowflake != null)
                    {
                        sb.append("\n### Linked Discord id:\n`").append(userMeta.userSnowflake).append("`");
                    }
                    
                    sb.append("\n### Audit target events:");
                    for (GroupAuditLogEntry entry : entries)
                    {
                        sb.append("\n`").append(entry.getId()).append("`").append(" <t:").append(Long.toUnsignedString(entry.getCreatedAt().toEpochSecond())).append(":f>: ");
                        ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(entry.getId());
                        if (auditEntryMeta != null && auditEntryMeta.hasMessage())
                            sb.append("[").append(entry.getDescription()).append("](").append(auditEntryMeta.getMessageUrl()).append(")");
                        else
                            sb.append(entry.getDescription());
                    }
                    
                    while (sb.length() > 2000)
                    {
                        int lastBreak = sb.lastIndexOf("\n", 2000);
                        deferred.sendMessage(sb.substring(0, lastBreak)).setEphemeral(true).queue();
                        sb.delete(0, lastBreak + 1);
                    }
                    deferred.sendMessage(sb.toString()).setEphemeral(true).queue();
                    
                } break;
                case "query-actor-history": {
                    
                    String vrcId = event.getOption("vrchat-user").getAsString();
                    int daysBack = Math.max(1, Math.min(2048, event.getOption("days-back", 7, OptionMapping::getAsInt)));

                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId);
                    if (sc == null)
                    {
                        event.replyFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    event.deferReply(true).queue();
                    InteractionHook deferred = event.getHook();
                    
                    List<GroupAuditLogEntry> entries = ScarletDiscordJDA.this.scarlet.vrc.auditQueryActored(vrcId, daysBack);
                    if (entries == null)
                    {
                        deferred.sendMessageFormat("Error querying audit actor history for [%s](<https://vrchat.com/home/user/%s>) (%s)", sc.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
                        return;
                    }
                    else if (entries.isEmpty())
                    {
                        deferred.sendMessageFormat("No audit actor history for [%s](<https://vrchat.com/home/user/%s>) (%s)", sc.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("VRChat audit actor history for [").append(sc.getDisplayName()).append("](<https://vrchat.com/home/user/").append(vrcId).append(">):");
                    
                    ScarletData.UserMetadata userMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata(vrcId);
                    if (userMeta != null && userMeta.userSnowflake != null)
                    {
                        sb.append("\n### Linked Discord id:\n`").append(userMeta.userSnowflake).append("`");
                    }
                    
                    sb.append("\n### Audit actor events:");
                    for (GroupAuditLogEntry entry : entries)
                    {
                        sb.append("\n`").append(entry.getId()).append("`").append(" <t:").append(Long.toUnsignedString(entry.getCreatedAt().toEpochSecond())).append(":f>: ");
                        ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(entry.getId());
                        if (auditEntryMeta != null && auditEntryMeta.hasMessage())
                            sb.append("[").append(entry.getDescription()).append("](").append(auditEntryMeta.getMessageUrl()).append(")");
                        else
                            sb.append(entry.getDescription());
                    }

                    while (sb.length() > 2000)
                    {
                        int lastBreak = sb.lastIndexOf("\n", 2000);
                        deferred.sendMessage(sb.substring(0, lastBreak)).setEphemeral(true).queue();
                        sb.delete(0, lastBreak + 1);
                    }
                    deferred.sendMessage(sb.toString()).setEphemeral(true).queue();
                    
                } break;
                case "config-info": {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Configuration Information:");
                    
                    String lastQueryEpoch = Long.toUnsignedString(ScarletDiscordJDA.this.scarlet.settings.getLastAuditQuery().toEpochSecond()),
                         lastRefreshEpoch = Long.toUnsignedString(ScarletDiscordJDA.this.scarlet.settings.getLastAuthRefresh().toEpochSecond());
                    
                    sb.append("\n### Last Audit Query:")
                        .append("\n<t:").append(lastQueryEpoch).append(":F>")
                        .append(" (<t:").append(lastQueryEpoch).append(":R>)");

                    sb.append("\n### Last Auth Refresh:")
                        .append("\n<t:").append(lastRefreshEpoch).append(":F>")
                        .append(" (<t:").append(lastRefreshEpoch).append(":R>)");
                    
                    sb.append("\n### Auditing Channels:");
                    for (GroupAuditType auditType : GroupAuditType.values())
                    {
                        String channelSf = ScarletDiscordJDA.this.auditType2channelSf.get(auditType.id);
                        sb.append("\n")
                            .append(auditType.title)
                            .append(" (")
                            .append(auditType.id)
                            .append("): ")
                            .append(channelSf == null ? "unassigned" : ("<#"+channelSf+">"));
                    }
                    
                    event.reply(sb.toString()).setEphemeral(true).queue();
                } break;
                case "set-audit-channel": {
                    String auditType0 = event.getOption("audit-event-type").getAsString();
                    GroupAuditType auditType = GroupAuditType.of(auditType0);
                    if (auditType == null)
                    {
                        event.replyFormat("%s isn't a valid audit log event type", auditType0).setEphemeral(true).queue();
                        return;
                    }
                    OptionMapping channel0 = event.getOption("discord-channel");

                    if (channel0 == null)
                    {
                        ScarletDiscordJDA.this.setAuditChannel(auditType, null);
                        event.replyFormat("Unassociating VRChat group audit log event type %s (%s) from any channels", auditType.title, auditType.id).setEphemeral(true).queue();
                        return;
                    }
                    
                    GuildChannelUnion channel = channel0.getAsChannel();
                    if (!channel.getType().isMessage())
                    {
                        event.replyFormat("The channel %s doesn't support message sending", channel.getName()).setEphemeral(true).queue();
                    }
                    else
                    {
                        ScarletDiscordJDA.this.setAuditChannel(auditType, channel);
                        event.replyFormat("Associating VRChat group audit log event type %s (%s) with channel <#%s>", auditType.title, auditType.id, channel.getId()).setEphemeral(true).queue();
                    }
                    
                } break;
                case "set-permission-role": {
                    String scarletPermission0 = event.getOption("scarlet-permission").getAsString();
                    ScarletPermission scarletPermission = ScarletPermission.of(scarletPermission0);
                    if (scarletPermission == null)
                    {
                        event.replyFormat("%s isn't a valid Scarlet permission", scarletPermission0).setEphemeral(true).queue();
                        return;
                    }
                    OptionMapping role0 = event.getOption("discord-role");
                    
                    if (role0 == null)
                    {
                        ScarletDiscordJDA.this.setPermissionRole(scarletPermission, null);
                        event.replyFormat("Unassociating Scarlet permission %s (%s) from any roles", scarletPermission.title, scarletPermission.id).setEphemeral(true).queue();
                        return;
                    }
                    
                    Role role = role0.getAsRole();
                    
                    ScarletDiscordJDA.this.setPermissionRole(scarletPermission, role);
                    event.replyFormat("Associating Scarlet permission %s (%s) with role <@%s>", scarletPermission.title, scarletPermission.id, role.getId()).setEphemeral(true).queue();
                    
                } break;
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                if (event.isAcknowledged())
                {
                    event.getHook().sendMessage("Internal error");
                }
                else
                {
                    event.reply("Internal error");
                }
            }
        }

        @Override
        public void onButtonInteraction(ButtonInteractionEvent event)
        {
            try
            {
                String[] parts = event.getButton().getId().split(":");
                switch (parts[0])
                {
                case "edit-tags": {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_SET_TAGS);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            event.reply("You do not have permission to select event tags.").setEphemeral(true).queue();
                            return;
                        }
                    }
                    
                    // TODO : set default selected
                    
                    List<ScarletModerationTags.Tag> tags = ScarletDiscordJDA.this.scarlet.moderationTags.getTags();
                    
                    if (tags == null || tags.isEmpty())
                    {
                        event.reply("No moderation tags!").setEphemeral(true).queue();
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
                        builder.addOption(tag.label, tag.value, tag.description);
                    
                    ActionRow ar = ActionRow.of(builder.build());
                    
                    event.replyComponents(ar)
                        .setEphemeral(true)
                        .queue();
                    
                } break;
                case "edit-desc": {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_SET_DESCRIPTION);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            event.reply("You do not have permission to set event descriptions.").setEphemeral(true).queue();
                            return;
                        }
                    }
                    
                    String auditEntryId = parts[1];
                    
                    TextInput.Builder ti = TextInput
                        .create("input-desc:"+auditEntryId, "Input description", TextInputStyle.PARAGRAPH)
                        .setRequired(true)
                        .setPlaceholder("Event description")
                        ;
                    
                    Modal.Builder m = Modal.create("edit-desc:"+auditEntryId, "Edit description")
                        .addActionRow(ti.build())
                        ;
                    
                    event.replyModal(m.build()).queue();
                    
                } break;
                case "vrchat-report": {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_USE_REPORT_LINK);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            event.reply("You do not have permission to use the event report link.").setEphemeral(true).queue();
                            return;
                        }
                    }
                    
                    String auditEntryId = parts[1];
                    
                    event.deferReply(true).queue();
                    InteractionHook deferred = event.getHook();
                    
                    String targetUserId = null,
                           targetDisplayName = null,
                           actorUserId = null,
                           metaDescription = null,
                           metaTags[] = null;
                    
                    String eventUserSnowflake = event.getUser().getId(),
                           eventUserId = ScarletDiscordJDA.this.scarlet.data.globalMetadata_getSnowflakeId(eventUserSnowflake);
                    
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
                    if (auditEntryMeta != null && auditEntryMeta.entry != null)
                    {
                        targetUserId = auditEntryMeta.entry.getTargetId();
                        actorUserId = auditEntryMeta.entry.getActorId();
                        metaDescription = auditEntryMeta.entryDescription;
                        metaTags = auditEntryMeta.entryTags;
                        
                        User targetUser = ScarletDiscordJDA.this.scarlet.vrc.getUser(targetUserId);
                        if (targetUser != null)
                        {
                            targetDisplayName = targetUser.getDisplayName();
                        }
                    }
                    
                    String reportSubject = targetDisplayName;
                    
                    String reportDesc = metaDescription;
                    if (metaTags != null && metaTags.length > 0)
                    {
                        if (reportDesc == null)
                            reportDesc = "";
                        else
                            reportDesc = reportDesc + "<br><br>";
                        reportDesc = reportDesc + "User's Group internal moderation tags:<ul>";
                        
                        for (String metaTag : metaTags)
                            reportDesc = reportDesc + "<li>" + ScarletDiscordJDA.this.scarlet.moderationTags.getTagLabel(metaTag) + "</li>";
                        reportDesc = reportDesc + "</ul>";
                    }
                    
                    String requestingUserId = eventUserId != null ? eventUserId : actorUserId;
                    
                    String link = VRChatHelpDeskURLs.newModerationRequest(
                        null,
                        VRChatHelpDeskURLs.ModerationCategory.USER_REPORT,
                        requestingUserId,
                        targetUserId,
                        reportSubject,
                        reportDesc
                    );
                    
                    StringBuilder msg = new StringBuilder();
                    
                    if (eventUserId == null)
                    {
                        msg.append("## WARNING\nThis link autofills the requesting user id of the **audit actor, not necessarily you**\nAssociate your Discord and VRChat ids with `/associate-ids`.\n\n");
                    }
                    
                    msg.append("[Open new VRChat User Moderation Request](<").append(link).append(">)");
                    
                    deferred.sendMessage(msg.toString()).setEphemeral(true).queue();
                    
                } break;
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                if (event.isAcknowledged())
                {
                    event.getHook().sendMessage("Internal error");
                }
                else
                {
                    event.reply("Internal error");
                }
            }
        }

        @Override
        public void onModalInteraction(ModalInteractionEvent event)
        {
            try
            {
                String[] parts = event.getModalId().split(":");
                switch (parts[0])
                {
                case "edit-desc": {
                    String desc = event.getValue("input-desc:"+parts[1]).getAsString();
                    event.replyFormat("### Setting description:\n%s", desc).setEphemeral(true).queue();
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata_setDescription(parts[1], desc);
                    this.updateAuxMessage(event.getChannel(), auditEntryMeta);
                } break;
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                if (event.isAcknowledged())
                {
                    event.getHook().sendMessage("Internal error");
                }
                else
                {
                    event.reply("Internal error");
                }
            }
        }

        @Override
        public void onStringSelectInteraction(StringSelectInteractionEvent event)
        {
            try
            {
                String[] parts = event.getSelectMenu().getId().split(":");
                switch (parts[0])
                {
                case "select-tags": {
                    String joined = event.getValues().stream().map(ScarletDiscordJDA.this.scarlet.moderationTags::getTagLabel).collect(Collectors.joining(", "));
                    event.replyFormat("### Setting tags:\n%s", joined).setEphemeral(true).queue();
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata_setTags(parts[1], event.getValues().toArray(new String[0]));
                    this.updateAuxMessage(event.getChannel(), auditEntryMeta);
                } break;
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                if (event.isAcknowledged())
                {
                    event.getHook().sendMessage("Internal error");
                }
                else
                {
                    event.reply("Internal error");
                }
            }
        }
        
        void updateAuxMessage(MessageChannel channel, ScarletData.AuditEntryMetadata auditEntryMeta)
        {
            if (auditEntryMeta.hasMessage() && auditEntryMeta.auxMessageSnowflake != null && auditEntryMeta.entry != null)
            {
                ScarletData.UserMetadata actorMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata(auditEntryMeta.entry.getActorId());
                String content = actorMeta == null || actorMeta.userSnowflake == null ? ("Unknown Discord id for actor "+auditEntryMeta.entry.getActorDisplayName()) : ("<@"+actorMeta.userSnowflake+">");
                if (auditEntryMeta.entryTags != null && auditEntryMeta.entryTags.length > 0)
                {
                    String joined = MiscUtils.stream(auditEntryMeta.entryTags).map(ScarletDiscordJDA.this.scarlet.moderationTags::getTagLabel).collect(Collectors.joining(", "));
                    content = content + "\n### Tags:\n" + joined;
                }
                if (auditEntryMeta.entryDescription != null && !auditEntryMeta.entryDescription.trim().isEmpty())
                {
                    content = content + "\n### Descripton:\n" + auditEntryMeta.entryDescription;
                }
                channel.editMessageById(auditEntryMeta.auxMessageSnowflake, content).queue();
            }
        }
        
    }

    public void setAuditChannel(GroupAuditType auditType, GuildChannelUnion channel)
    {
        if (channel != null)
        {
            this.auditType2channelSf.put(auditType.id, channel.getId());
            LOG.info(String.format("Setting audit channel for %s (%s) to %s (%s)", auditType.title, auditType.id, channel.getName(), "<#"+channel.getId()+">"));
        }
        else
        {
            this.auditType2channelSf.remove(auditType.id);
            LOG.info(String.format("Unsetting audit channel for %s (%s)", auditType.title, auditType.id));
        }
        this.save();
    }

    public void setPermissionRole(ScarletPermission scarletPermission, Role role)
    {
        if (role != null)
        {
            this.scarletPermission2roleSf.put(scarletPermission.id, role.getId());
            LOG.info(String.format("Setting role for Scarlet permission %s (%s) to %s (%s)", scarletPermission.title, scarletPermission.id, role.getName(), "<@"+role.getId()+">"));
        }
        else
        {
            this.scarletPermission2roleSf.remove(scarletPermission.id);
            LOG.info(String.format("Unsetting role for Scarlet permission %s (%s)", scarletPermission.title, scarletPermission.id));
        }
        this.save();
    }

    public String getPermissionRole(ScarletPermission scarletPermission)
    {
        if (scarletPermission == null)
            return null;
        return this.scarletPermission2roleSf.get(scarletPermission.id);
    }

    @FunctionalInterface interface CondEmit { Message emit(String channelSf, Guild guild, TextChannel channel); }
    void condEmit(ScarletData.AuditEntryMetadata entryMeta, CondEmit condEmit)
    {
        String channelSf = this.auditType2channelSf.get(entryMeta.entry.getEventType());
        if (channelSf == null)
            return;
        Guild guild = this.jda.getGuildById(this.guildSf);
        if (guild == null)
            return;
        TextChannel channel = guild.getTextChannelById(channelSf);
        if (channel == null)
            return;
        
        entryMeta.guildSnowflake = this.guildSf;
        entryMeta.channelSnowflake = channelSf;
        Message message = condEmit.emit(channelSf, guild, channel);
        entryMeta.messageSnowflake = message.getId();
    }
    EmbedBuilder embed(GroupAuditLogEntry entry, boolean addTargetIdField)
    {
        EmbedBuilder embed = new EmbedBuilder()
            .setDescription(entry.getDescription())
            .setColor(GroupAuditType.color(entry.getEventType()))
            .setTimestamp(entry.getCreatedAt())
            .setAuthor(entry.getActorDisplayName(), "https://vrchat.com/home/user/"+entry.getActorId())
            .setFooter(ScarletDiscord.FOOTER_PREFIX+entry.getId())
        ;
        if (addTargetIdField)
            embed.addField("Target id", entry.getTargetId(), false);
        return embed;
    }
    @FunctionalInterface interface CondEmitEmbed { void emitEmbed(String channelSf, Guild guild, TextChannel channel, EmbedBuilder embed); }
    void condEmitEmbed(ScarletData.AuditEntryMetadata entryMeta, boolean addTargetIdField, String title, String url, Map<String, GroupAuditType.UpdateSubComponent> updates, CondEmitEmbed condEmitEmbed)
    {
        this.condEmit(entryMeta, (channelSf, guild, channel) ->
        {
            EmbedBuilder embed = this.embed(entryMeta.entry, addTargetIdField);
            if (title != null)
                embed.setTitle(title, url);
            if (updates != null && !updates.isEmpty())
                updates.forEach((key, sub) -> embed.addField("`"+key+"`", "old: `"+sub.oldValue+"`\nnew :`"+sub.newValue+"`", false));
            if (condEmitEmbed != null)
                condEmitEmbed.emitEmbed(channelSf, guild, channel, embed);
            return channel.sendMessageEmbeds(embed.build()).complete();
        });
    }

    final Pacer userModerationLimiter = new Pacer(3_000L);
    static final TypeToken<List<LimitedUserGroups>> LIST_LUGROUPS = new TypeToken<List<LimitedUserGroups>>(){};
    @Override
    public void emitUserModeration(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, User target, ScarletData.UserMetadata actorMeta, ScarletData.UserMetadata targetMeta, String history, String recent)
    {
        this.condEmit(entryMeta, (channelSf, guild, channel) ->
        {
            this.userModerationLimiter.await();
            
            EmbedBuilder embed = this.embed(entryMeta.entry, true)
                .setTitle(target.getDisplayName(), "https://vrchat.com/home/user/"+target.getId())
                .setImage(MiscUtils.userImageUrl(target))
            ;
            
            String jsonUser;
            try
            {
                jsonUser = this.scarlet.vrc.getUser(entryMeta.entry.getTargetId()).toJson();
            }
            catch (Exception ex)
            {
                LOG.error("Exception whilst fetching user", ex);
                jsonUser = null;
            }
            String jsonUserGroups;
            try
            {
                jsonUserGroups = JSON.getGson().toJson(new UsersApi(this.scarlet.vrc.client).getUserGroups(entryMeta.entry.getTargetId()), LIST_LUGROUPS.getType());
            }
            catch (Exception ex)
            {
                LOG.error("Exception whilst fetching user groups", ex);
                jsonUserGroups = null;
            }
            String jsonUserRepGroup;
            try
            {
                jsonUserRepGroup = JSON.getGson().toJson(new UsersApi(this.scarlet.vrc.client).getUserRepresentedGroup(entryMeta.entry.getTargetId()), RepresentedGroup.class);
            }
            catch (Exception ex)
            {
                LOG.error("Exception whilst fetching user represented group", ex);
                jsonUserRepGroup = null;
            }
            
            byte[] fileData = String.format("{\n\"user\":%s,\n\"groups\":%s,\n\"represented\":%s\n}\n", jsonUser, jsonUserGroups, jsonUserRepGroup).getBytes(StandardCharsets.UTF_8);
            
            if (history != null)
                embed.addField("History", history, false);
            if (recent != null)
                embed.addField("Most recent", recent, false);
            
            Message message = channel
                .sendMessageEmbeds(embed.build())
                .complete();
            
            ThreadChannel threadChannel = channel
                .createThreadChannel(entryMeta.entry.getDescription(), message.getId())
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
                .completeAfter(1000L, TimeUnit.MILLISECONDS);
            
            entryMeta.threadSnowflake = threadChannel.getId();
            
            String content = actorMeta == null || actorMeta.userSnowflake == null ? ("Unknown Discord id for actor "+entryMeta.entry.getActorDisplayName()) : ("<@"+actorMeta.userSnowflake+">");
            Message auxMessage = threadChannel.sendMessage(content)
                .addFiles(FileUpload.fromData(fileData, entryMeta.entry.getTargetId()+".json").asSpoiler())
                .addActionRow(Button.primary("edit-tags:"+entryMeta.entry.getId(), "Edit tags"),
                              Button.primary("edit-desc:"+entryMeta.entry.getId(), "Edit description"),
                              Button.primary("vrchat-report:"+entryMeta.entry.getId(), "Get report link"))
                .completeAfter(1000L, TimeUnit.MILLISECONDS);
            
            entryMeta.auxMessageSnowflake = auxMessage.getId();
            
            return message;
        });
        
    }

    @Override
    public void emitInstanceCreate(Scarlet scarlet, AuditEntryMetadata entryMeta, String location)
    {
        this.condEmitEmbed(entryMeta, true, "Instance Open", "https://vrchat.com/home/launch?worldId="+location.replaceFirst(":", "&instanceId="), null, null);
    }

    @Override
    public void emitInstanceClose(Scarlet scarlet, AuditEntryMetadata entryMeta, String location)
    {
        this.condEmitEmbed(entryMeta, true, "Instance Close", null, null, null);
    }

    @Override
    public void emitMemberJoin(Scarlet scarlet, AuditEntryMetadata entryMeta)
    {
        this.condEmitEmbed(entryMeta, true, "Member Join", "https://vrchat.com/home/user/"+entryMeta.entry.getTargetId(), null, null);
    }

    @Override
    public void emitMemberLeave(Scarlet scarlet, AuditEntryMetadata entryMeta)
    {
        this.condEmitEmbed(entryMeta, true, "Member Leave", "https://vrchat.com/home/user/"+entryMeta.entry.getTargetId(), null, null);
    }

    @Override
    public void emitPostCreate(Scarlet scarlet, AuditEntryMetadata entryMeta, GroupAuditType.PostCreateComponent post)
    {
        this.condEmitEmbed(entryMeta, false, post.title, "https://vrchat.com/home/group/"+entryMeta.entry.getGroupId()+"/posts", null, (channelSf, guild, channel, embed) ->
            embed.setDescription(null) // override description
                .setDescription(post.text)
                .setImage(MiscUtils.latestContentUrlOrNull(post.imageId))
        );
    }

    @Override
    public void emitMemberRoleAssign(Scarlet scarlet, AuditEntryMetadata entryMeta, User target, GroupAuditType.RoleRefComponent role)
    {
        this.condEmitEmbed(entryMeta, false, "Assigned Role to "+target.getDisplayName(), "https://vrchat.com/home/user/"+target.getId(), null, (channelSf, guild, channel, embed) ->
            embed.setDescription(String.format(
                "[%s](%s) assigned the role \"[%s](https://vrchat.com/home/group/%s/settings/roles/%s)\" to [%s](https://vrchat.com/home/user/%s)",
                entryMeta.entry.getActorDisplayName(), entryMeta.entry.getActorId(),
                role.roleName, entryMeta.entry.getGroupId(), role.roleId,
                target.getDisplayName(), "https://vrchat.com/home/user/"+target.getId()
            )));
//        );
    }

    @Override
    public void emitMemberRoleUnassign(Scarlet scarlet, AuditEntryMetadata entryMeta, User target, GroupAuditType.RoleRefComponent role)
    {
        this.condEmitEmbed(entryMeta, false, "Unassigned Role from "+target.getDisplayName(), "https://vrchat.com/home/user/"+target.getId(), null, (channelSf, guild, channel, embed) ->
            embed.setDescription(String.format(
                "[%s](%s) unassigned the role \"[%s](https://vrchat.com/home/group/%s/settings/roles/%s)\" from [%s](https://vrchat.com/home/user/%s)",
                entryMeta.entry.getActorDisplayName(), entryMeta.entry.getActorId(),
                role.roleName, entryMeta.entry.getGroupId(), role.roleId,
                target.getDisplayName(), "https://vrchat.com/home/user/"+target.getId()
            )));
    }

    @Override
    public void emitMemberUserUpdate(Scarlet scarlet, AuditEntryMetadata entryMeta, Map<String, GroupAuditType.UpdateSubComponent> updates)
    {
        this.condEmitEmbed(entryMeta, true, "Updated Member User", null, updates, null);
    }

    @Override
    public void emitRoleCreate(Scarlet scarlet, AuditEntryMetadata entryMeta, GroupAuditType.RoleCreateComponent role)
    {
        this.condEmitEmbed(entryMeta, false, "Created Role "+role.name, "https://vrchat.com/home/group/"+entryMeta.entry.getGroupId()+"/settings/roles/"+entryMeta.entry.getTargetId(), null, null);
    }

    @Override
    public void emitRoleDelete(Scarlet scarlet, AuditEntryMetadata entryMeta, GroupAuditType.RoleDeleteComponent role)
    {
        this.condEmitEmbed(entryMeta, false, "Deleted Role "+role.name, "https://vrchat.com/home/group/"+entryMeta.entry.getGroupId()+"/settings/roles/"+entryMeta.entry.getTargetId(), null, null);
    }

    @Override
    public void emitRoleUpdate(Scarlet scarlet, AuditEntryMetadata entryMeta, Map<String, GroupAuditType.UpdateSubComponent> updates)
    {
        String roleId = entryMeta.entry.getTargetId();
        String roleName = roleId;
        try
        {
            roleName = new GroupsApi(this.scarlet.vrc.client).getGroupRoles(entryMeta.entry.getGroupId()).stream().filter($ -> roleId.equals($.getId())).findAny().map(GroupRole::getName).orElse(roleId);
        }
        catch (Exception ex)
        {
            roleName = roleId;
        }
        this.condEmitEmbed(entryMeta, false, "Updated Role "+roleName, "https://vrchat.com/home/group/"+entryMeta.entry.getGroupId()+"/settings/roles/"+entryMeta.entry.getTargetId(), updates, null);
    }

    @Override
    public void emitInviteCreate(Scarlet scarlet, AuditEntryMetadata entryMeta)
    {
        this.condEmitEmbed(entryMeta, false, "Invited User", "https://vrchat.com/home/user/"+entryMeta.entry.getTargetId(), null, null);
    }

    @Override
    public void emitRequestBlock(Scarlet scarlet, AuditEntryMetadata entryMeta)
    {
        this.condEmitEmbed(entryMeta, false, "Blocked Request", "https://vrchat.com/home/user/"+entryMeta.entry.getTargetId(), null, null);
    }

    @Override
    public void emitRequestCreate(Scarlet scarlet, AuditEntryMetadata entryMeta)
    {
        this.condEmitEmbed(entryMeta, false, "Created Request", "https://vrchat.com/home/user/"+entryMeta.entry.getTargetId(), null, null);
    }

    @Override
    public void emitRequestReject(Scarlet scarlet, AuditEntryMetadata entryMeta)
    {
        this.condEmitEmbed(entryMeta, false, "Rejected Request", "https://vrchat.com/home/user/"+entryMeta.entry.getTargetId(), null, null);
    }

    @Override
    public void emitUpdate(Scarlet scarlet, AuditEntryMetadata entryMeta, Map<String, GroupAuditType.UpdateSubComponent> updates)
    {
        this.condEmitEmbed(entryMeta, false, "Updated Group", "https://vrchat.com/home/group/"+entryMeta.entry.getGroupId(), updates, null);
    }

    @Override
    public void emitDefault(Scarlet scarlet, AuditEntryMetadata entryMeta)
    {
        this.condEmitEmbed(entryMeta, true, GroupAuditType.title(entryMeta.entry.getEventType()), null, null, (channelSf, guild, channel, embed) ->
        {
            if (entryMeta.hasNonEmptyData())
            {
                JsonObject data = entryMeta.getData(JsonObject.class);
                for (String key : data.keySet())
                {
                    String valueString = null;
                    JsonElement value = data.get(key);
                    if (value != null && value.isJsonObject())
                    {
                        JsonObject valueObject = value.getAsJsonObject();
                        if (valueObject.size() == 2 && valueObject.has("old") && valueObject.has("new"))
                        {
                            valueString = new StringBuilder()
                                .append("old: `").append(valueObject.get("old"))
                                .append("`\nnew: `").append(valueObject.get("new")).append("`")
                                .toString();
                        }
                    }
                    if (valueString == null)
                    {
                        valueString = "`"+value+"`";
                    }
                    embed.addField("`"+key+"`", valueString, false);
                }
            }
        });
    }

}
