package net.sybyline.scarlet;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.JSON;
import io.github.vrchatapi.api.GroupsApi;
import io.github.vrchatapi.api.UsersApi;
import io.github.vrchatapi.model.Group;
import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.GroupMemberStatus;
import io.github.vrchatapi.model.GroupRole;
import io.github.vrchatapi.model.LimitedUserGroups;
import io.github.vrchatapi.model.RepresentedGroup;
import io.github.vrchatapi.model.User;
import io.github.vrchatapi.model.World;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IncomingWebhookClient;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.AbstractWebhookMessageAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.sybyline.scarlet.ScarletData.AuditEntryMetadata;
import net.sybyline.scarlet.log.ScarletLogger;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.Pacer;
import net.sybyline.scarlet.util.UniqueStrings;
import net.sybyline.scarlet.util.VRChatHelpDeskURLs;

public class ScarletDiscordJDA implements ScarletDiscord
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/JDA");

    public ScarletDiscordJDA(Scarlet scarlet, File discordBotFile)
    {
        scarlet.splash.splashSubtext("Configuring Discord");
        this.scarlet = scarlet;
        this.discordBotFile = discordBotFile;
        this.audio = new JDAAudioSendingHandler();
        this.requestingEmail = scarlet.ui.settingString("vrchat_report_email", "VRChat Help Desk report email", "");
        this.pingOnModeration_instanceWarn = scarlet.ui.settingBool("discord_ping_instance_warn", "Discord: Ping on Instance Warn", false);
        this.pingOnModeration_instanceKick = scarlet.ui.settingBool("discord_ping_instance_kick", "Discord: Ping on Instance Kick", false);
        this.pingOnModeration_memberRemove = scarlet.ui.settingBool("discord_ping_member_remove", "Discord: Ping on Member Remove", true);
        this.pingOnModeration_userBan = scarlet.ui.settingBool("discord_ping_user_ban", "Discord: Ping on User Ban", true);
        this.pingOnModeration_userUnban = scarlet.ui.settingBool("discord_ping_user_unban", "Discord: Ping on User Unban", false);
        this.load();
        JDA jda = null;
        if (this.token != null && !this.token.trim().isEmpty()) try
        {
            jda = JDABuilder
            .createDefault(this.token)
            .enableIntents(GatewayIntent.MESSAGE_CONTENT)
            .addEventListeners(new JDAEvents())
            .enableCache(CacheFlag.VOICE_STATE)
            .build();
        }
        catch (InvalidTokenException|IllegalArgumentException ex)
        {
            this.scarlet.ui.messageModalAsyncError(null, "You can reset the bot token in the Settings page.", "Invalid bot token");
        }
        this.jda = jda;
        this.init();
    }

    @Override
    public void close() throws IOException
    {
        this.save();
        if (this.jda == null)
            return;
        this.jda.shutdown();
        try
        {
            if (this.jda.awaitShutdown(10_000L, TimeUnit.MILLISECONDS))
            {
                this.jda.shutdownNow();
                this.jda.awaitShutdown();
            }
        }
        catch (InterruptedException iex)
        {
            LOG.error("Interrupted awaiting JDA shutdown");
            this.jda.shutdownNow();
        }
    }

    final Scarlet scarlet;
    final File discordBotFile;
    final JDAAudioSendingHandler audio;
    final JDA jda;
    String token, guildSf, audioChannelSf, evidenceRoot;
    final ScarletUI.Setting<String> requestingEmail;
    final ScarletUI.Setting<Boolean> pingOnModeration_instanceWarn,
                                     pingOnModeration_instanceKick,
                                     pingOnModeration_memberRemove,
                                     pingOnModeration_userBan,
                                     pingOnModeration_userUnban;
    final Map<String, Pagination> pagination = new ConcurrentHashMap<>();
    final Map<String, Command.Choice> userSf2lastEdited_groupId = new ConcurrentHashMap<>();
    Map<String, String> scarletPermission2roleSf = new HashMap<>();
    Map<String, String> scarletAuxWh2webhookUrl = new ConcurrentHashMap<>();
    Map<String, IncomingWebhookClient> scarletAuxWh2incomingWebhookClient = new ConcurrentHashMap<>();
    Map<String, String> auditType2channelSf = new HashMap<>();
    Map<String, UniqueStrings> auditType2scarletAuxWh = new ConcurrentHashMap<>();
    Map<String, Integer> auditType2color = new HashMap<>();
    @FunctionalInterface interface ImmediateHandler { void handle() throws Exception; }
    @FunctionalInterface interface DeferredHandler { void handle(InteractionHook hook) throws Exception; }

    static final String[] AUDIT_EVENT_IDS = MiscUtils.map(GroupAuditType.values(), String[]::new, GroupAuditType::id);
    static final String[] SCARLET_PERMISSION_IDS = MiscUtils.map(ScarletPermission.values(), String[]::new, ScarletPermission::id);

    void init()
    {
        if (this.jda == null)
        {
            this.setStaffMode();
            return;
        }
        try
        {
            this.jda.awaitReady();
        }
        catch (Exception ex)
        {
            if (ex instanceof IllegalStateException && JDA.Status.FAILED_TO_LOGIN == this.jda.getStatus() && "".equals(ScarletDiscordJDA.this.token))
            {
                this.setStaffMode();
                return;
            }
            throw new RuntimeException("Awaiting JDA", ex);
        }
        
        // Calling getGuildById fixes bot sometimes not rejoining voice channel on start
        Guild guild = ScarletDiscordJDA.this.jda.getGuildById(this.guildSf);
        if (guild == null)
        {
            LOG.error("Guild returned null for snowflake "+this.guildSf);
        }
        else
        {
            LOG.warn("Guild "+this.guildSf+": "+guild.getName());
        }
        if (this.scarlet.settings.checkHasVersionChangedSinceLastRun())
        {
            this.updateCommandList();
        }
        this.audio.init();
        this.scarlet.exec.scheduleAtFixedRate(this::clearDeadPagination, 30_000L, 30_000L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void updateCommandList()
    {
        DefaultMemberPermissions defaultCommandPerms = DefaultMemberPermissions.enabledFor(
            Permission.ADMINISTRATOR,
            Permission.MANAGE_SERVER,
            Permission.MANAGE_ROLES);
        this.jda.updateCommands()
            .addCommands(
                Commands.slash("create-or-update-moderation-tag", "Creates or updates a moderation tag")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "value", "The internal name of the tag", true, true)
                    .addOption(OptionType.STRING, "label", "The display name of the tag")
                    .addOption(OptionType.STRING, "description", "The description text of the tag"),
                Commands.slash("delete-moderation-tag", "Deletes a moderation tag")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "value", "The internal name of the tag", true, true),
                Commands.slash("watched-group", "Configures watched groups")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addSubcommands(
                        new SubcommandData("list", "Lists all watched groups")
                            .addOptions(new OptionData(OptionType.INTEGER, "entries-per-page", "The number of groups to show per page").setRequiredRange(1L, 10L)),
                        new SubcommandData("export", "Exports watched groups as a JSON file"),
                        new SubcommandData("import", "Imports watched groups from an attached file")
                            .addOption(OptionType.ATTACHMENT, "import-file", "Accepts: JSON, CSV", true),
                        new SubcommandData("add", "Adds a watched group")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "group-tags", "A list of tags, separated by one of ',', ';', '/'")
                            .addOptions(new OptionData(OptionType.INTEGER, "group-priority", "The priority of this group").setRequiredRange(-100, 100))
                            .addOption(OptionType.STRING, "message", "A message to announce with TTS"),
                        new SubcommandData("add-malicious", "Adds a watched group with a watch type of malicious")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "group-tags", "A list of tags, separated by one of ',', ';', '/'")
                            .addOptions(new OptionData(OptionType.INTEGER, "group-priority", "The priority of this group").setRequiredRange(-100, 100))
                            .addOption(OptionType.STRING, "message", "A message to announce with TTS"),
                        new SubcommandData("add-nuisance", "Adds a watched group with a watch type of nuisance")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "group-tags", "A list of tags, separated by one of ',', ';', '/'")
                            .addOptions(new OptionData(OptionType.INTEGER, "group-priority", "The priority of this group").setRequiredRange(-100, 100))
                            .addOption(OptionType.STRING, "message", "A message to announce with TTS"),
                        new SubcommandData("add-community", "Adds a watched group with a watch type of community")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "group-tags", "A list of tags, separated by one of ',', ';', '/'")
                            .addOptions(new OptionData(OptionType.INTEGER, "group-priority", "The priority of this group").setRequiredRange(-100, 100))
                            .addOption(OptionType.STRING, "message", "A message to announce with TTS"),
                        new SubcommandData("add-affiliated", "Adds a watched group with a watch type of affiliated")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "group-tags", "A list of tags, separated by one of ',', ';', '/'")
                            .addOptions(new OptionData(OptionType.INTEGER, "group-priority", "The priority of this group").setRequiredRange(-100, 100))
                            .addOption(OptionType.STRING, "message", "A message to announce with TTS"),
                        new SubcommandData("add-other", "Adds a watched group with a watch type of other")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "group-tags", "A list of tags, separated by one of ',', ';', '/'")
                            .addOptions(new OptionData(OptionType.INTEGER, "group-priority", "The priority of this group").setRequiredRange(-100, 100))
                            .addOption(OptionType.STRING, "message", "A message to announce with TTS"),
                        new SubcommandData("delete-watched-group", "Removes a watched group")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("view", "Views a group's watch information")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("set-critical", "Sets a group's critical status to true")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("unset-critical", "Sets a group's critical status to false")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("set-silent", "Sets a group's silent status to true")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("unset-silent", "Sets a group's silent status to false")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("set-type-malicious", "Sets a group's watch type to malicious")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("set-type-nuisance", "Sets a group's watch type to nuisance")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("set-type-community", "Sets a group's watch type to community")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("set-type-affiliated", "Sets a group's watch type to affiliated")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("set-type-other", "Sets a group's watch type to other")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true),
                        new SubcommandData("set-priority", "Sets a group's watch type to other")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOptions(new OptionData(OptionType.INTEGER, "group-priority", "The priority of this group").setRequiredRange(-100, 100).setRequired(true)),
                        new SubcommandData("set-tags", "Sets a group's tags")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "group-tags", "A list of tags, separated by one of ',', ';', '/'"),
                        new SubcommandData("add-tag", "Adds a tag for a group")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "group-tag", "A tag", true),
                        new SubcommandData("remove-tag", "Removes a tag from a group")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "group-tag", "A tag", true),
                        new SubcommandData("set-message", "Sets a group's TTS announcement message")
                            .addOption(OptionType.STRING, "vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                            .addOption(OptionType.STRING, "message", "A message to announce with TTS")
                    ),
                Commands.slash("aux-webhooks", "Configures auxiliary webhooks")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addSubcommands(
                        new SubcommandData("list", "Lists all auxiliary webhooks")
                            .addOptions(new OptionData(OptionType.INTEGER, "entries-per-page", "The number of webhooks to show per page").setRequiredRange(1L, 10L)),
                        new SubcommandData("add", "Adds an auxiliary webhook")
                            .addOption(OptionType.STRING, "aux-webhook-id", "The internal id to give the webhook", true)
                            .addOption(OptionType.STRING, "aux-webhook-url", "The url of the webhook", true),
                        new SubcommandData("delete", "Removes an auxiliary webhook")
                            .addOption(OptionType.STRING, "aux-webhook-id", "The internal id of the webhook", true, true)
                    ),
                Commands.slash("associate-ids", "Associates a specific Discord user with a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.USER, "discord-user", "The Discord user", true)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true),
                Commands.slash("vrchat-user-info", "Lists internal and audit information for a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true),
                Commands.slash("vrchat-user-ban", "Ban a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true),
                Commands.slash("vrchat-user-unban", "Unban a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true),
                Commands.slash("discord-user-info", "Lists internal information for a specific Discord user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.USER, "discord-user", "The Discord user", true),
                Commands.slash("query-target-history", "Queries audit events targeting a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true)
                    .addOption(OptionType.INTEGER, "days-back", "The number of days into the past to search for events"),
                Commands.slash("query-actor-history", "Queries audit events actored by a specific VRChat user")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true)
                    .addOption(OptionType.INTEGER, "days-back", "The number of days into the past to search for events"),
                Commands.slash("set-audit-channel", "Sets a given text channel as the channel certain audit event types use")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "audit-event-type", "The VRChat Group Audit Log event type", true, true)
                    .addOption(OptionType.CHANNEL, "discord-channel", "The Discord text channel to use, or omit to remove entry"),
                Commands.slash("set-audit-aux-webhooks", "Sets the given webhooks as the webhooks certain audit event types use")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "audit-event-type", "The VRChat Group Audit Log event type", true, true),
                Commands.slash("set-voice-channel", "Sets a given voice channel as the channel in which to announce TTS messages")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.CHANNEL, "discord-channel", "The Discord voice channel to use, or omit to remove entry"),
                Commands.slash("set-tts-voice", "Selects which TTS voice is used to make announcements")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "voice-name", "The name of the installed voice to use", true, true),
                Commands.slash("set-permission-role", "Sets a given Scarlet-specific permission to be associated with a given Discord role")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "scarlet-permission", "The Scarlet-specific permission", true, true)
                    .addOption(OptionType.ROLE, "discord-role", "The Discord role to use, or omit to remove entry"),
                Commands.slash("config-info", "Shows information about the current configuration")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms),
                Commands.slash("export-log", "Attaches a log file")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "file-name", "The name of the log file", false, true),
                Commands.message("submit-attachments")
                    .setGuildOnly(true)
                    .setNameLocalizations(MiscUtils.genLocalized($ -> "Submit Attachments"))
                    .setDefaultPermissions(defaultCommandPerms)
            )
            .complete();
    }

    void setStaffMode()
    {
        LOG.warn("No Discord bot token: entering staff mode");
        this.scarlet.staffMode = true;
        this.scarlet.ui.jframe.setTitle(Scarlet.NAME+" (staff mode)");
    }

    IncomingWebhookClient getOrCreateIWC(String scarletAuxWh)
    {
        if (scarletAuxWh == null)
            return null;
        IncomingWebhookClient iwc = this.scarletAuxWh2incomingWebhookClient.get(scarletAuxWh);
        if (iwc != null)
            return iwc;
        String iwcUrl = this.scarletAuxWh2webhookUrl.get(scarletAuxWh);
        if (iwcUrl != null) try
        {
            iwc = WebhookClient.createClient(this.jda, iwcUrl);
            this.scarletAuxWh2incomingWebhookClient.put(scarletAuxWh, iwc);
        }
        catch (Exception ex)
        {
            LOG.error("Invalid Discord webhook URL for `"+scarletAuxWh+"`: `"+iwcUrl+"`, removing");
            this.scarletAuxWh2webhookUrl.remove(scarletAuxWh);
        }
        return iwc;
    }

    @Override
    public boolean submitAudio(File file)
    {
        return this.audio.submitAudio(file);
    }

    static final int BYTES_PER_20MS = 3840; // 20ms * (48000 frames/second) * (2 samples/frame) * (2 bytes/sample)
    class JDAAudioSendingHandler implements AudioSendHandler
    {

        boolean submitAudio(File file)
        {
            AudioManager audioManager = this.audioManager;
            if (audioManager == null || !audioManager.isConnected())
                return false;
            List<byte[]> buffersToAdd = new ArrayList<>();
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(file))
            {
                AudioInputStream ais0 = null;
                try
                {
                    if (AudioSendHandler.INPUT_FORMAT.matches(ais.getFormat()))
                    {
                        ais0 = ais;
                    }
                    else if (AudioSystem.isConversionSupported(AudioSendHandler.INPUT_FORMAT, ais.getFormat()))
                    {
                        ais0 = AudioSystem.getAudioInputStream(AudioSendHandler.INPUT_FORMAT, ais);
                    }
                    else
                    {
                        return false;
                    }
                    while (ais0.available() > 0)
                    {
                        byte[] buffer = new byte[BYTES_PER_20MS];
                        for
                        (
                            int read = ais0.read(buffer),
                                total = read;
                            total < BYTES_PER_20MS && (read = ais0.read(buffer, total, BYTES_PER_20MS - total)) != -1;
                            total += read
                        );
                        buffersToAdd.add(buffer);
                    }
                }
                finally
                {
                    if (ais0 != ais)
                        MiscUtils.close(ais0);
                }
            }
            catch (Exception ex)
            {
                LOG.error("Exception loading TTS audio", ex);
                return false;
            }
            if (buffersToAdd.isEmpty())
                return true;
            synchronized (this)
            {
                this.buffers.addAll(buffersToAdd);
            }
            return true;
        }

        void init()
        {
            String guildSf = ScarletDiscordJDA.this.guildSf;
            Guild guild = ScarletDiscordJDA.this.jda.getGuildById(guildSf);
            if (guild == null)
            {
                LOG.warn("Audio: Guild returned null for snowflake "+guildSf);
                return;
            }
            AudioManager audioManager = guild.getAudioManager();
            audioManager.setAutoReconnect(true);
            audioManager.setConnectionListener(null);
            audioManager.setConnectTimeout(10_000L);
            audioManager.setReceivingHandler(null);
            audioManager.setSelfDeafened(true);
            audioManager.setSelfMuted(false);
            audioManager.setSendingHandler(this);
            // audioManager.setSpeakingMode(SpeakingMode.);
            this.audioManager = audioManager;
            this.updateChannel();
        }

        void updateChannel()
        {
            String guildSf = ScarletDiscordJDA.this.guildSf,
                   audioChannelSf = ScarletDiscordJDA.this.audioChannelSf;
            Guild guild = ScarletDiscordJDA.this.jda.getGuildById(guildSf);
            AudioManager audioManager = this.audioManager;
            if (audioChannelSf == null)
            {
                this.audioChannel = null;
                if (audioManager != null)
                {
                    audioManager.closeAudioConnection();
                    this.buffers.clear();
                }
            }
            else
            {
                AudioChannel audioChannel = guild.getVoiceChannelById(audioChannelSf);
                this.audioChannel = audioChannel;
                if (audioManager != null)
                {
                    if (audioChannel != null)
                    {
                        audioManager.openAudioConnection(audioChannel);
                        this.buffers.clear();
                    }
                    else
                    {
                        audioManager.closeAudioConnection();
                        this.buffers.clear();
                    }
                }
            }
        }

        AudioManager audioManager;
        AudioChannel audioChannel;
        final Queue<byte[]> buffers = new ConcurrentLinkedQueue<>();

        @Override
        public boolean canProvide()
        {
            return !this.buffers.isEmpty();
        }

        @Override
        public ByteBuffer provide20MsAudio()
        {
            byte[] data = this.buffers.poll();
            return data == null ? null : ByteBuffer.wrap(data);
        }

    }

    public static class JDASettingsSpec
    {
        public String token = null,
                      guildSf = null,
                      audioChannelSf = null,
                      evidenceRoot = null;
        public Map<String, String> scarletPermission2roleSf = new HashMap<>();
        public Map<String, String> scarletAuxWh2webhookUrl = new HashMap<>();
        public Map<String, String> auditType2channelSf = new HashMap<>();
        public Map<String, UniqueStrings> auditType2scarletAuxWh = new HashMap<>();
        public Map<String, String> auditType2color = new HashMap<>();
    }

    public void load()
    {
        JDASettingsSpec spec = null;
        if (this.discordBotFile.isFile())
        try (FileReader fr = new FileReader(this.discordBotFile))
        {
            spec = Scarlet.GSON_PRETTY.fromJson(fr, JDASettingsSpec.class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading discord bot settings", ex);
        }
        
        if (spec == null)
            spec = new JDASettingsSpec();
        
        boolean save = false;
        
        if (spec.token == null)
        {
            spec.token = this.scarlet.settings.requireInput("Discord bot token (leave empty for staff mode)", true);
            save = true;
        }
        this.scarlet.ui.settingVoid("Discord bot token", "Reset", () -> this.scarlet.execModal.execute(() ->
        {
            if (!this.scarlet.ui.confirmModal(null, "Are you sure you want to reset the bot token?", "Reset bot token"))
                return;
            this.token = this.scarlet.settings.requireInput("Discord bot token (leave empty for staff mode)", true);
            this.save();
        }));
        
        if (spec.guildSf == null)
        {
            spec.guildSf = this.scarlet.settings.requireInput("Discord guild snowflake (leave empty for staff mode)", false);
            save = true;
        }
        this.scarlet.ui.settingVoid("Discord guild snowflake", "Reset", () -> this.scarlet.execModal.execute(() ->
        {
            if (!this.scarlet.ui.confirmModal(null, "Are you sure you want to reset the guild snowflake?", "Reset guild snowflake"))
                return;
            this.guildSf = this.scarlet.settings.requireInput("Discord guild snowflake (leave empty for staff mode)", false);
            this.save();
        }));
        
        if (save) this.save(spec);
        
        this.token = spec.token;
        this.guildSf = spec.guildSf;
        this.audioChannelSf = spec.audioChannelSf;
        this.evidenceRoot = spec.evidenceRoot;
        this.scarletPermission2roleSf = spec.scarletPermission2roleSf == null ? new HashMap<>() : new HashMap<>(spec.scarletPermission2roleSf);
        this.scarletAuxWh2webhookUrl = spec.scarletAuxWh2webhookUrl == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(spec.scarletAuxWh2webhookUrl);
        this.auditType2channelSf = spec.auditType2channelSf == null ? new HashMap<>() : new HashMap<>(spec.auditType2channelSf);
        this.auditType2scarletAuxWh = spec.auditType2scarletAuxWh == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(spec.auditType2scarletAuxWh);
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
        spec.audioChannelSf = this.audioChannelSf;
        spec.evidenceRoot = this.evidenceRoot;
        spec.auditType2channelSf = new HashMap<>(this.auditType2channelSf);
        spec.auditType2scarletAuxWh = new HashMap<>(this.auditType2scarletAuxWh);
        spec.scarletPermission2roleSf = new HashMap<>(this.scarletPermission2roleSf);
        spec.scarletAuxWh2webhookUrl = new HashMap<>(this.scarletAuxWh2webhookUrl);
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
            LOG.error("Exception saving discord bot settings", ex);
        }
    }

    void clearDeadPagination()
    {
        new ArrayList<>(this.pagination.values()).forEach(Pagination::removeIfExpired);
    }
    @FunctionalInterface interface Paginator
    {
        WebhookMessageEditAction<Message> setContent(InteractionHook hook, @Nullable String messageId);
        static Paginator one(String string)
        {
            return (hook, messageId) -> messageId == null
                ? hook.editOriginal(string)
                : hook.editMessageById(messageId, string);
        }
        static Paginator one(MessageEmbed[] embeds)
        {
            return (hook, messageId) -> messageId == null
                ? hook.editOriginalEmbeds(embeds)
                : hook.editMessageEmbedsById(messageId, embeds);
        }
        static Paginator[] stringBuilder(StringBuilder sb)
        {
            List<Paginator> pages = new ArrayList<>();
            while (sb.length() > 2000)
            {
                int lastBreak = sb.lastIndexOf("\n", 2000);
                pages.add(one(sb.substring(0, lastBreak)));
                sb.delete(0, lastBreak + 1);
            }
            pages.add(one(sb.toString()));
            return pages.toArray(new Paginator[pages.size()]);
        }
        static Paginator[] embeds(MessageEmbed[] embeds, int perPage)
        {
            if (perPage < 1) perPage = 1;
            if (perPage > Message.MAX_EMBED_COUNT) perPage = Message.MAX_EMBED_COUNT;
            List<Paginator> pages = new ArrayList<>();
            for (int idx = 0; idx < embeds.length; idx += perPage)
                pages.add(one(Arrays.copyOfRange(embeds, idx, Math.min(idx + perPage, embeds.length))));
            return pages.toArray(new Paginator[pages.size()]);
        }
    }
    class Pagination
    {
        Pagination(String paginationId, StringBuilder pages)
        {
            this(paginationId, Paginator.stringBuilder(pages));
        }
        Pagination(String paginationId, MessageEmbed[] pages, int perPage)
        {
            this(paginationId, Paginator.embeds(pages, perPage));
        }
        Pagination(String paginationId, Paginator[] pages)
        {
            this.paginationId = paginationId;
            this.pages = pages;
        }
        final String paginationId;
        final Paginator[] pages;
        InteractionHook hook;
        String messageId;
        boolean removeIfExpired()
        {
            if (this.hook == null || !this.hook.isExpired())
                return false;
            ScarletDiscordJDA.this.pagination.remove(this.paginationId);
            return true;
        }
        WebhookMessageEditAction<Message> action(int pageOrdinal)
        {
            if (pageOrdinal < 1) pageOrdinal = 1;
            if (pageOrdinal > this.pages.length) pageOrdinal = this.pages.length;
            
            Button prev = Button.success("pagination:"+this.paginationId+":"+(pageOrdinal - 1), "Prev"),
                   self = Button.primary("pagination:"+this.paginationId+":"+pageOrdinal, pageOrdinal+"/"+pages.length).asDisabled(),
                   next = Button.success("pagination:"+this.paginationId+":"+(pageOrdinal + 1), "Next");
            
            if (pageOrdinal == 1) prev = prev.asDisabled();
            if (pageOrdinal == this.pages.length) next = next.asDisabled();
            
            int pageIndex = pageOrdinal - 1;
            return this
                .pages[pageIndex]
                .setContent(this.hook, this.messageId)
                .setActionRow(prev, self, next);
        }
        void queue(InteractionHook hook)
        {
            this.hook = hook;
            this.action(1).queue(message -> {
                this.messageId = message.getId();
                ScarletDiscordJDA.this.pagination.put(this.paginationId, this);
            });
        }
    }

    class JDAEvents extends ListenerAdapter
    {
        JDAEvents()
        {
        }

        boolean isInGuild(Interaction interaction)
        {
            return interaction.isFromGuild() && Objects.equals(ScarletDiscordJDA.this.guildSf, interaction.getGuild().getId());
        }

        void interactionError(Interaction interaction, Throwable err)
        {
            LOG.error("Error processing interaction of type "+interaction.getClass().getSimpleName(), err);
            if (interaction instanceof IReplyCallback)
            {
                IReplyCallback replyCallback = (IReplyCallback)interaction;
                String reply = "Error processing interaction:\n`"+err+"`";
                (replyCallback.isAcknowledged()
                    ? replyCallback.getHook().sendMessage(reply)
                    : replyCallback.reply(reply)
                ).queue();
            }
        }

        void handleInGuildSync(IReplyCallback event, boolean ephemeral, ImmediateHandler handler)
        {
            LOG.trace("Sync handling event of type "+event.getClass().getSimpleName());
            try
            {
                handler.handle();
            }
            catch (Exception ex)
            {
                LOG.error("Exception async handling event of type "+event.getClass().getSimpleName(), ex);
                event.reply("Exception async handling event:\n`"+ex+"`").setEphemeral(ephemeral).queue();
            }
        }

        void handleInGuildAsync(IReplyCallback event, boolean ephemeral, DeferredHandler handler)
        {
            InteractionHook hook = event.deferReply(ephemeral).complete();
            LOG.trace("Async handling event of type "+event.getClass().getSimpleName());
            ScarletDiscordJDA.this.scarlet.exec.execute(() ->
            {
                try
                {
                    handler.handle(hook);
                }
                catch (Exception ex)
                {
                    LOG.error("Exception async handling event of type "+event.getClass().getSimpleName(), ex);
                    hook.sendMessage("Exception async handling event:\n`"+ex+"`").setEphemeral(ephemeral).queue();
                }
            });
        }

        void handleInGuildAsync__000(IReplyCallback event, boolean ephemeral, DeferredHandler handler)
        {
            LOG.trace("Async handling event of type "+event.getClass().getSimpleName());
            event.deferReply(ephemeral).queue(hook ->
            {
                try
                {
                    handler.handle(hook);
                }
                catch (Exception ex)
                {
                    LOG.error("Exception async handling event of type "+event.getClass().getSimpleName(), ex);
                    hook.sendMessage("Exception async handling event:\n`"+ex+"`").setEphemeral(ephemeral).queue();
                }
            }, ex -> {
                LOG.error("Exception deferring reply to event of type "+event.getClass().getSimpleName(), ex);
            });
        }

        @Override
        public void onShutdown(ShutdownEvent event)
        {
            if (CloseCode.DISALLOWED_INTENTS == event.getCloseCode())
            {
                ScarletDiscordJDA.this.scarlet.stop();
                LOG.error("You must enable the `Message Content` intent in the `Privileged Gateway Intents` are of your application's `Bot` tab.");
            }
        }

        @Override
        public void onMessageContextInteraction(MessageContextInteractionEvent event)
        {
            if (!this.isInGuild(event))
                return;
            try
            {
                switch (event.getName())
                {
                case "submit-attachments": { // no need to defer
                    
                    Message message = event.getTarget();
                    
                    event.reply("Select submission type")
                        .addActionRow(
                            Button.primary("submit-evidence:"+message.getId(), "Submit moderation evidence"),
                            Button.primary("import-watched-groups:"+message.getId(), "Import watched groups"))
                        .setEphemeral(true)
                        .queue();
                    
                } break;
                }
            }
            catch (Exception ex)
            {
                this.interactionError(event, ex);
            }
        }

        @Override
        public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event)
        {
            if (!this.isInGuild(event))
                return;
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
                case "watched-group": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "vrchat-group": {
                        AutoCompleteCallbackAction action = event.replyChoices();
                        Command.Choice groupChoice = ScarletDiscordJDA.this.userSf2lastEdited_groupId.get(event.getUser().getId());
                        if (groupChoice != null)
                        {
                            action.addChoices(groupChoice);
                        }
                        String typing = event.getFocusedOption().getValue();
                        if (typing != null && !(typing = typing.trim()).isEmpty())
                        {
                            action.addChoiceStrings(typing);
                        }
                        action.queue();
                    } break;
                    }
                } break;
                case "aux-webhooks": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "aux-webhook-id": {
                        event.replyChoiceStrings(ScarletDiscordJDA.this.scarletAuxWh2webhookUrl.keySet().stream().limit(25L).toArray(String[]::new)).queue();
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
                case "set-audit-aux-webhooks": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "audit-event-type": {
                        event.replyChoiceStrings(AUDIT_EVENT_IDS).queue();
                    } break;
                    }
                } break;
                case "set-tts-voice": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "voice-name": {
                        event.replyChoiceStrings(ScarletDiscordJDA.this.scarlet.ttsService.getInstalledVoices()).queue();
                    } break;
                    }
                } break;
                case "export-log": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "file-name": {
                        event.replyChoiceStrings(ScarletDiscordJDA.this.scarlet.last25logs).queue();
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
                this.interactionError(event, ex);
            }
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
        {
            if (!this.isInGuild(event))
                return;
            try
            {
                switch (event.getName())
                {
                case "create-or-update-moderation-tag": this.handleInGuildAsync(event, true, hook -> {
                    
                    String value = event.getOption("value").getAsString(),
                           label = event.getOption("label", OptionMapping::getAsString),
                           description = event.getOption("description", OptionMapping::getAsString);
                    
                    int result = ScarletDiscordJDA.this.scarlet.moderationTags.addOrUpdateTag(value, label, description);
                    
                    switch (result)
                    {
                    default: {
                        hook.sendMessage("Failed to add moderation tag: list == null").setEphemeral(true).queue();
                    } break;
                    case -2: {
                        hook.sendMessage("Failed to add moderation tag: there are already the maximum of 25 moderation tags").setEphemeral(true).queue();
                    } break;
                    case -1: {
                        hook.sendMessage("Failed to add moderation tag: list.add returned false").setEphemeral(true).queue();
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
                        hook.sendMessage(msg).setEphemeral(true).queue();
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
                        hook.sendMessage(msg).setEphemeral(true).queue();
                    } break;
                    }
                    
                }); break;
                case "delete-moderation-tag": this.handleInGuildAsync(event, true, hook -> {
                    
                    String value = event.getOption("value").getAsString();
                    
                    int result = ScarletDiscordJDA.this.scarlet.moderationTags.removeTag(value);
                    
                    switch (result)
                    {
                    default: {
                        hook.sendMessage("Failed to delete moderation tag: list == null").setEphemeral(true).queue();
                    } break;
                    case -3: {
                        hook.sendMessageFormat("Failed to add the tag: the tag `%s` does not exist", value).setEphemeral(true).queue();
                    } break;
                    case -2: {
                        hook.sendMessage("Failed to delete moderation tag: there are no moderation tags").setEphemeral(true).queue();
                    } break;
                    case -1: {
                        hook.sendMessage("Failed to delete moderation tag: list.remove returned false").setEphemeral(true).queue();
                    } break;
                    case 0: {
                        String msg = "Deleted moderation tag `"+value+"`";
                        LOG.info(msg);
                        hook.sendMessage(msg).setEphemeral(true).queue();
                    } break;
                    }
                    
                }); break;
                case "watched-group": this.handleInGuildAsync(event, false, hook -> {
                    
                    int perPage = event.getOption("entries-per-page", 4, OptionMapping::getAsInt);
                    String groupId = event.getOption("vrchat-group", OptionMapping::getAsString);
                    Attachment importedFile = event.getOption("import-file", OptionMapping::getAsAttachment);
                    String groupTags = event.getOption("group-tags", OptionMapping::getAsString);
                    String groupTag = event.getOption("group-tag", OptionMapping::getAsString);
                    int priority = event.getOption("group-priority", 0, OptionMapping::getAsInt);
                    String message = event.getOption("message", OptionMapping::getAsString);
                    
                    if (groupId != null && !(groupId = groupId.trim()).isEmpty())
                    {
                        Group group = ScarletDiscordJDA.this.scarlet.vrc.getGroup(groupId);
                        ScarletDiscordJDA.this.userSf2lastEdited_groupId.put(event.getUser().getId(), new Command.Choice(group != null ? group.getName() : groupId, groupId));
                    }
                    
                    switch (event.getSubcommandName())
                    {
                    case "list": {
                        long within1day = System.currentTimeMillis() - 86400_000L;
                        MessageEmbed[] embeds = ScarletDiscordJDA.this.scarlet
                            .watchedGroups
                            .watchedGroups
                            .values()
                            .stream()
                            .map($ -> $.embed(ScarletDiscordJDA.this.scarlet.vrc.getGroup($.id, within1day)).build())
                            .toArray(MessageEmbed[]::new)
                        ;
                        
                        ScarletDiscordJDA.this.new Pagination(event.getId(), embeds, perPage).queue(hook);
                        
                    } break;
                    case "export": {
                        LOG.info("Exporting watched groups JSON");
                        hook.sendFiles(FileUpload.fromData(ScarletDiscordJDA.this.scarlet.watchedGroups.watchedGroupsFile)).setEphemeral(false).queue();
                    } break;
                    case "import": {
                        String fileName = importedFile.getFileName(),
                               attachmentUrl = importedFile.getUrl();
                        
                        if (fileName.endsWith(".csv"))
                        {
                            LOG.info("Importing watched groups legacy CSV from attachment: "+fileName);
                            try (Reader reader = new InputStreamReader(HttpURLInputStream.get(attachmentUrl)))
                            {
                                if (ScarletDiscordJDA.this.scarlet.watchedGroups.importLegacyCSV(reader, true))
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
                                if (ScarletDiscordJDA.this.scarlet.watchedGroups.importJson(reader, true))
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
                    } break;
                    case "add": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup != null)
                        {
                            hook.sendMessage("That group is already watched").setEphemeral(true).queue();
                            return;
                        }
                        Group group = ScarletDiscordJDA.this.scarlet.vrc.getGroup(groupId);
                        if (group == null)
                        {
                            hook.sendMessage("That group doesn't seem to exist").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup = new ScarletWatchedGroups.WatchedGroup();
                        watchedGroup.id = groupId;
                        if (groupTags != null)
                            watchedGroup.tags.clear().addAll(Arrays.stream(groupTags.split("[,;/]")).map(String::trim).toArray(String[]::new));
                        if (message != null)
                            watchedGroup.message = message;
                        watchedGroup.priority = priority;
                        ScarletDiscordJDA.this.scarlet.watchedGroups.addWatchedGroup(groupId, watchedGroup);
                        hook.sendMessageFormat("Added group [%s](https://vrchat.com/home/group/%s)", group.getName(), group.getId()).setEphemeral(true).queue();

                    } break;
                    case "add-malicious": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup != null)
                        {
                            hook.sendMessage("That group is already watched").setEphemeral(true).queue();
                            return;
                        }
                        Group group = ScarletDiscordJDA.this.scarlet.vrc.getGroup(groupId);
                        if (group == null)
                        {
                            hook.sendMessage("That group doesn't seem to exist").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup = new ScarletWatchedGroups.WatchedGroup();
                        watchedGroup.id = groupId;
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.MALICIOUS;
                        if (groupTags != null)
                            watchedGroup.tags.clear().addAll(Arrays.stream(groupTags.split("[,;/]")).map(String::trim).toArray(String[]::new));
                        if (message != null)
                            watchedGroup.message = message;
                        watchedGroup.priority = priority;
                        ScarletDiscordJDA.this.scarlet.watchedGroups.addWatchedGroup(groupId, watchedGroup);
                        hook.sendMessageFormat("Added malicious group [%s](https://vrchat.com/home/group/%s)", group.getName(), group.getId()).setEphemeral(true).queue();

                    } break;
                    case "add-nuisance": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup != null)
                        {
                            hook.sendMessage("That group is already watched").setEphemeral(true).queue();
                            return;
                        }
                        Group group = ScarletDiscordJDA.this.scarlet.vrc.getGroup(groupId);
                        if (group == null)
                        {
                            hook.sendMessage("That group doesn't seem to exist").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup = new ScarletWatchedGroups.WatchedGroup();
                        watchedGroup.id = groupId;
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.NUISANCE;
                        if (groupTags != null)
                            watchedGroup.tags.clear().addAll(Arrays.stream(groupTags.split("[,;/]")).map(String::trim).toArray(String[]::new));
                        if (message != null)
                            watchedGroup.message = message;
                        watchedGroup.priority = priority;
                        ScarletDiscordJDA.this.scarlet.watchedGroups.addWatchedGroup(groupId, watchedGroup);
                        hook.sendMessageFormat("Added nuisance group [%s](https://vrchat.com/home/group/%s)", group.getName(), group.getId()).setEphemeral(true).queue();

                    } break;
                    case "add-community": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup != null)
                        {
                            hook.sendMessage("That group is already watched").setEphemeral(true).queue();
                            return;
                        }
                        Group group = ScarletDiscordJDA.this.scarlet.vrc.getGroup(groupId);
                        if (group == null)
                        {
                            hook.sendMessage("That group doesn't seem to exist").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup = new ScarletWatchedGroups.WatchedGroup();
                        watchedGroup.id = groupId;
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.COMMUNITY;
                        if (groupTags != null)
                            watchedGroup.tags.clear().addAll(Arrays.stream(groupTags.split("[,;/]")).map(String::trim).toArray(String[]::new));
                        if (message != null)
                            watchedGroup.message = message;
                        watchedGroup.priority = priority;
                        ScarletDiscordJDA.this.scarlet.watchedGroups.addWatchedGroup(groupId, watchedGroup);
                        hook.sendMessageFormat("Added community group [%s](https://vrchat.com/home/group/%s)", group.getName(), group.getId()).setEphemeral(true).queue();

                    } break;
                    case "add-affiliated": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup != null)
                        {
                            hook.sendMessage("That group is already watched").setEphemeral(true).queue();
                            return;
                        }
                        Group group = ScarletDiscordJDA.this.scarlet.vrc.getGroup(groupId);
                        if (group == null)
                        {
                            hook.sendMessage("That group doesn't seem to exist").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup = new ScarletWatchedGroups.WatchedGroup();
                        watchedGroup.id = groupId;
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.AFFILIATED;
                        if (groupTags != null)
                            watchedGroup.tags.clear().addAll(Arrays.stream(groupTags.split("[,;/]")).map(String::trim).toArray(String[]::new));
                        if (message != null)
                            watchedGroup.message = message;
                        watchedGroup.priority = priority;
                        ScarletDiscordJDA.this.scarlet.watchedGroups.addWatchedGroup(groupId, watchedGroup);
                        hook.sendMessageFormat("Added affiliated group [%s](https://vrchat.com/home/group/%s)", group.getName(), group.getId()).setEphemeral(true).queue();

                    } break;
                    case "add-other": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup != null)
                        {
                            hook.sendMessage("That group is already watched").setEphemeral(true).queue();
                            return;
                        }
                        Group group = ScarletDiscordJDA.this.scarlet.vrc.getGroup(groupId);
                        if (group == null)
                        {
                            hook.sendMessage("That group doesn't seem to exist").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup = new ScarletWatchedGroups.WatchedGroup();
                        watchedGroup.id = groupId;
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.OTHER;
                        if (groupTags != null)
                            watchedGroup.tags.clear().addAll(Arrays.stream(groupTags.split("[,;/]")).map(String::trim).toArray(String[]::new));
                        if (message != null)
                            watchedGroup.message = message;
                        watchedGroup.priority = priority;
                        ScarletDiscordJDA.this.scarlet.watchedGroups.addWatchedGroup(groupId, watchedGroup);
                        hook.sendMessageFormat("Added other group [%s](https://vrchat.com/home/group/%s)", group.getName(), group.getId()).setEphemeral(true).queue();

                    } break;
                    case "delete-watched-group": {
                        if (ScarletDiscordJDA.this.scarlet.watchedGroups.removeWatchedGroup(groupId))
                        {
                            Group group = ScarletDiscordJDA.this.scarlet.vrc.getGroup(groupId);
                            hook.sendMessageFormat("Removed group [%s](https://vrchat.com/home/group/%s)", group == null ? groupId : group.getName(), groupId).setEphemeral(true).queue();
                        }
                        else
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                        }
                    } break;
                    case "view": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        long within1hour = System.currentTimeMillis() - 360_000L;
                        Group group = ScarletDiscordJDA.this.scarlet.vrc.getGroup(groupId, within1hour);
                        hook.sendMessageEmbeds(watchedGroup.embed(group).build()).setEphemeral(true).queue();
                    } break;
                    case "set-critical": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (watchedGroup.critical)
                        {
                            hook.sendMessage("That group is already flagged as critical").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.critical = true;
                        hook.sendMessage("Flagged group as critical").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "unset-critical": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (!watchedGroup.critical)
                        {
                            hook.sendMessage("That group is already not flagged as critical").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.critical = false;
                        hook.sendMessage("Unflagged group as critical").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "set-silent": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (watchedGroup.silent)
                        {
                            hook.sendMessage("That group is already flagged as silent").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.silent = true;
                        hook.sendMessage("Flagged group as silent").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "unset-silent": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (!watchedGroup.silent)
                        {
                            hook.sendMessage("That group is already not flagged as silent").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.silent = false;
                        hook.sendMessage("Unflagged group as silent").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "set-type-malicious": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (watchedGroup.type == ScarletWatchedGroups.WatchedGroup.Type.MALICIOUS)
                        {
                            hook.sendMessage("That group is already marked as malicious").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.MALICIOUS;
                        hook.sendMessage("Marking group as malicious").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "set-type-nuisance": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (watchedGroup.type == ScarletWatchedGroups.WatchedGroup.Type.NUISANCE)
                        {
                            hook.sendMessage("That group is already marked as nuisance").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.NUISANCE;
                        hook.sendMessage("Marking group as nuisance").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "set-type-community": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (watchedGroup.type == ScarletWatchedGroups.WatchedGroup.Type.COMMUNITY)
                        {
                            hook.sendMessage("That group is already marked as community").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.COMMUNITY;
                        hook.sendMessage("Marking group as community").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "set-type-affiliated": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (watchedGroup.type == ScarletWatchedGroups.WatchedGroup.Type.AFFILIATED)
                        {
                            hook.sendMessage("That group is already marked as affiliated").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.AFFILIATED;
                        hook.sendMessage("Marking group as affiliated").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "set-type-other": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (watchedGroup.type == ScarletWatchedGroups.WatchedGroup.Type.OTHER)
                        {
                            hook.sendMessage("That group is already marked as other").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.type = ScarletWatchedGroups.WatchedGroup.Type.OTHER;
                        hook.sendMessage("Marking group as other").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "set-priority": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        watchedGroup.priority = priority;
                        hook.sendMessage("Setting group priority to "+priority).setEphemeral(true).queue();
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "set-tags": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (groupTags == null)
                        {
                            if (watchedGroup.tags.isEmpty())
                            {
                                hook.sendMessage("That group already has no tags").setEphemeral(true).queue();
                                return;
                            }
                            else
                            {
                                hook.sendMessageFormat("Removing group's tags (was `%s`)",
                                        watchedGroup.tags.strings().stream().filter(Objects::nonNull).collect(Collectors.joining("`, `")))
                                    .setEphemeral(true)
                                    .queue();
                                watchedGroup.tags.clear();
                            }
                        }
                        else
                        {
                            String[] newTags = Arrays.stream(groupTags.split("[,;/]")).map(String::trim).distinct().toArray(String[]::new);
                            if (watchedGroup.tags.strings().equals(new HashSet<>(Arrays.asList(newTags))))
                            {
                                hook.sendMessage("That group already has those exact tags").setEphemeral(true).queue();
                                return;
                            }
                            else
                            {
                                hook.sendMessageFormat("Setting group tags to `%s` (was `%s`)",
                                        Arrays.stream(newTags).filter(Objects::nonNull).collect(Collectors.joining("`, `")),
                                        watchedGroup.tags.strings().stream().filter(Objects::nonNull).collect(Collectors.joining("`, `")))
                                    .setEphemeral(true)
                                    .queue();
                                watchedGroup.tags.clear().addAll(newTags);
                            }
                        }
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "add-tag": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        String groupTag0 = groupTag.trim();
                        if (watchedGroup.tags.isEmpty())
                        {
                            watchedGroup.tags.add(groupTag0);
                            hook.sendMessageFormat("Added tag `%s` (was empty)", groupTag0).setEphemeral(true).queue();
                        }
                        else if (watchedGroup.tags.add(groupTag0))
                        {
                            hook.sendMessageFormat("Added tag `%s` (was `%s`)",
                                    groupTag0,
                                    watchedGroup.tags.strings().stream().filter(Objects::nonNull).collect(Collectors.joining("`, `")))
                                .setEphemeral(true)
                                .queue();
                        }
                        else
                        {
                            hook.sendMessage("That group already has that").setEphemeral(true).queue();
                        }
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "remove-tag": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (watchedGroup.tags.isEmpty())
                        {
                            hook.sendMessage("That group already has no tags").setEphemeral(true).queue();
                            return;
                        }
                        if (watchedGroup.tags.remove(groupTag))
                        {
                            hook.sendMessageFormat("Removed group tag `%s`)", groupTag).setEphemeral(true).queue();
                        }
                        else
                        {
                            hook.sendMessage("That group doesn't have that tag").setEphemeral(true).queue();
                            return;
                        }
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    case "set-message": {
                        ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordJDA.this.scarlet.watchedGroups.getWatchedGroup(groupId);
                        if (watchedGroup == null)
                        {
                            hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                            return;
                        }
                        if (message == null)
                        {
                            if (watchedGroup.message == null)
                            {
                                hook.sendMessage("That group already has no message").setEphemeral(true).queue();
                                return;
                            }
                            else
                            {
                                hook.sendMessageFormat("Removing group's message (was `%s`)", message).setEphemeral(true).queue();
                            }
                        }
                        else
                        {
                            if (message.equals(watchedGroup.message))
                            {
                                hook.sendMessage("That group's message is already exactly that").setEphemeral(true).queue();
                                return;
                            }
                            else
                            {
                                hook.sendMessageFormat("Setting group TTS announcement to `%s` (was `%s`)", message, watchedGroup.message).setEphemeral(true).queue();
                            }
                        }
                        watchedGroup.message = message;
                        ScarletDiscordJDA.this.scarlet.watchedGroups.save();
                    } break;
                    }
                    
                }); break;
                case "aux-webhooks": this.handleInGuildAsync(event, true, hook -> {
                    
                    int perPage = event.getOption("entries-per-page", 4, OptionMapping::getAsInt);
                    String auxWebhookId = event.getOption("aux-webhook-id", OptionMapping::getAsString);
                    String auxWebhookUrl = event.getOption("aux-webhook-url", OptionMapping::getAsString);
                    
                    switch (event.getSubcommandName())
                    {
                    case "list": {
                        MessageEmbed[] embeds = ScarletDiscordJDA.this.scarletAuxWh2webhookUrl
                            .entrySet()
                            .stream()
                            .map($ -> new EmbedBuilder()
                                .setTitle($.getKey())
                                .addField("Url", "`"+$.getValue()+"`", false)
                                .build())
                            .toArray(MessageEmbed[]::new)
                        ;
                        
                        ScarletDiscordJDA.this.new Pagination(event.getId(), embeds, perPage).queue(hook);
                        
                    } break;
                    case "add": {
                        
                        String prevWebhookUrl = ScarletDiscordJDA.this.scarletAuxWh2webhookUrl.get(auxWebhookId);
                        if (prevWebhookUrl != null)
                        {
                            hook.sendMessage("There is already an auxiliary webhook with that id").setEphemeral(true).queue();
                            return;
                        }
                        
                        if (!Webhook.WEBHOOK_URL.matcher(auxWebhookUrl).matches())
                        {
                            hook.sendMessage("Invalid webhook url").setEphemeral(true).queue();
                            return;
                        }
                        
                        ScarletDiscordJDA.this.scarletAuxWh2webhookUrl.put(auxWebhookId, auxWebhookUrl);
                        hook.sendMessage("Added auxiliary webhook").setEphemeral(true).queue();

                    } break;
                    case "delete": {
                        
                        String prevWebhookUrl = ScarletDiscordJDA.this.scarletAuxWh2webhookUrl.remove(auxWebhookId);
                        if (prevWebhookUrl != null)
                        {
                            hook.sendMessage("There is no auxiliary webhook with that id").setEphemeral(true).queue();
                            return;
                        }
                        
                        hook.sendMessage("Removed auxiliary webhook").setEphemeral(true).queue();
                        
                    } break;
                    }
                    
                }); break;
                case "associate-ids": this.handleInGuildAsync(event, true, hook -> {
                    
                    net.dv8tion.jda.api.entities.User user = event.getOption("discord-user").getAsUser();
                    String vrcId = event.getOption("vrchat-user").getAsString();

                    long within1day = System.currentTimeMillis() - 86400_000L;
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
                    if (sc == null)
                    {
                        hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    ScarletDiscordJDA.this.scarlet.data.linkIdToSnowflake(vrcId, user.getId());
                    LOG.info(String.format("Linking VRChat user %s (%s) to Discord user %s (<@%s>)", sc.getDisplayName(), vrcId, user.getEffectiveName(), user.getId()));
                    hook.sendMessageFormat("Associating %s with VRChat user [%s](https://vrchat.com/home/user/%s)", user.getEffectiveName(), sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                    
                }); break;
                case "vrchat-user-ban": this.handleInGuildAsync(event, false, hook -> {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_BAN_USER);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            hook.sendMessage("You do not have permission to ban users.").setEphemeral(true).queue();
                            return;
                        }
                    }
                    
                    String vrcTargetId = event.getOption("vrchat-user").getAsString();
                    
                    String vrcActorId = ScarletDiscordJDA.this.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
                    if (vrcActorId == null)
                    {
                        hook.sendMessage("You must have linked ids to perform this action").setEphemeral(true).queue();
                        return;
                    }
                    
                    long within1day = System.currentTimeMillis() - 86400_000L;
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcTargetId, within1day);
                    if (sc == null)
                    {
                        hook.sendMessageFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
                        return;
                    }
                    
                    GroupMemberStatus status = ScarletDiscordJDA.this.scarlet.vrc.getMembership(vrcTargetId);
                    
                    if (status == GroupMemberStatus.BANNED)
                    {
                        hook.sendMessage("This VRChat user is already banned").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (ScarletDiscordJDA.this.scarlet.pendingModActions.addPending(GroupAuditType.USER_BAN, vrcTargetId, vrcActorId) != null)
                    {
                        hook.sendMessage("This VRChat user currently has automated/assisted moderation pending, please retry later").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (!ScarletDiscordJDA.this.scarlet.vrc.banFromGroup(vrcTargetId))
                    {
                        hook.sendMessageFormat("Failed to ban %s", sc.getDisplayName()).setEphemeral(true).queue();
                        return;
                    }
                    
                    hook.sendMessageFormat("Banned %s", sc.getDisplayName()).setEphemeral(false).queue();
                    
                }); break;
                case "vrchat-user-unban": this.handleInGuildAsync(event, false, hook -> {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_UNBAN_USER);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            hook.sendMessage("You do not have permission to unban users.").setEphemeral(true).queue();
                            return;
                        }
                    }
                    
                    String vrcTargetId = event.getOption("vrchat-user").getAsString();
                    
                    String vrcActorId = ScarletDiscordJDA.this.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
                    if (vrcActorId == null)
                    {
                        hook.sendMessage("You must have linked ids to perform this action").setEphemeral(true).queue();
                        return;
                    }
                    
                    long within1day = System.currentTimeMillis() - 86400_000L;
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcTargetId, within1day);
                    if (sc == null)
                    {
                        hook.sendMessageFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
                        return;
                    }
                    
                    GroupMemberStatus status = ScarletDiscordJDA.this.scarlet.vrc.getMembership(vrcTargetId);
                    
                    if (status != GroupMemberStatus.BANNED)
                    {
                        hook.sendMessage("This VRChat user is not banned").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (ScarletDiscordJDA.this.scarlet.pendingModActions.addPending(GroupAuditType.USER_UNBAN, vrcTargetId, vrcActorId) != null)
                    {
                        hook.sendMessage("This VRChat user currently has automated/assisted moderation pending, please retry later").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (!ScarletDiscordJDA.this.scarlet.vrc.unbanFromGroup(vrcTargetId))
                    {
                        hook.sendMessageFormat("Failed to unban %s", sc.getDisplayName()).setEphemeral(true).queue();
                        return;
                    }
                    
                    hook.sendMessageFormat("Unbanned %s", sc.getDisplayName()).setEphemeral(false).queue();
                    
                }); break;
                case "vrchat-user-info": this.handleInGuildAsync(event, true, hook -> {
                    
                    String vrcId = event.getOption("vrchat-user").getAsString();

                    long within1day = System.currentTimeMillis() - 86400_000L;
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
                    if (sc == null)
                    {
                        hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    ScarletData.UserMetadata userMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata(vrcId);
                    if (userMeta == null)
                    {
                        hook.sendMessageFormat("No VRChat user metadata found for [%s](https://vrchat.com/home/user/%s)", sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
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

                    ScarletDiscordJDA.this.new Pagination(event.getId(), sb).queue(hook);
                    
                }); break;
                case "discord-user-info": this.handleInGuildAsync(event, true, hook -> {
                    
                    net.dv8tion.jda.api.entities.User user = event.getOption("discord-user").getAsUser();
                    
                    StringBuilder sb = new StringBuilder();
                    
                    String vrcId = ScarletDiscordJDA.this.scarlet.data.globalMetadata_getSnowflakeId(user.getId());
                    if (vrcId != null)
                    {
                        long within1day = System.currentTimeMillis() - 86400_000L;
                        User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
                        if (sc != null)
                        {
                            
                            sb.append(String.format("Linked VRChat user: [%s](https://vrchat.com/home/user/%s) `%s`\n", sc.getDisplayName(), vrcId, vrcId));
                        }
                    }
                    
                    sb.append("Scarlet permissions: ");
                    boolean addedAny = false;
                    for (ScarletPermission permission : ScarletPermission.values())
                    {
                        String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(permission);
                        if (roleSnowflake == null)
                        {
                            sb.append(permission.title).append(" (due to unset role), ");
                            addedAny = true;
                        }
                        else if (event.getMember().getRoles().stream().map(Role::getId).anyMatch(roleSnowflake::equals))
                        {
                            sb.append(permission.title).append(", ");
                            addedAny = true;
                        }
                    }
                    if (addedAny) sb.setLength(sb.length() - 2);
                    sb.append('\n');

                    ScarletDiscordJDA.this.new Pagination(event.getId(), sb).queue(hook);
                    
                }); break;
                case "query-target-history": this.handleInGuildAsync(event, true, hook -> {
                    
                    String vrcId = event.getOption("vrchat-user").getAsString();
                    int daysBack = Math.max(1, Math.min(2048, event.getOption("days-back", 7, OptionMapping::getAsInt)));

                    long within1day = System.currentTimeMillis() - 86400_000L;
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
                    if (sc == null)
                    {
                        hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    List<GroupAuditLogEntry> entries = ScarletDiscordJDA.this.scarlet.vrc.auditQueryTargeting(vrcId, daysBack);
                    if (entries == null)
                    {
                        hook.sendMessageFormat("Error querying audit target history for [%s](<https://vrchat.com/home/user/%s>) (%s)", sc.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
                        return;
                    }
                    else if (entries.isEmpty())
                    {
                        hook.sendMessageFormat("No audit target history for [%s](<https://vrchat.com/home/user/%s>) (%s)", sc.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
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

                    ScarletDiscordJDA.this.new Pagination(event.getId(), sb).queue(hook);
                    
                }); break;
                case "query-actor-history": this.handleInGuildAsync(event, true, hook -> {
                    
                    String vrcId = event.getOption("vrchat-user").getAsString();
                    int daysBack = Math.max(1, Math.min(2048, event.getOption("days-back", 7, OptionMapping::getAsInt)));

                    long within1day = System.currentTimeMillis() - 86400_000L;
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
                    if (sc == null)
                    {
                        hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                        return;
                    }
                    
                    List<GroupAuditLogEntry> entries = ScarletDiscordJDA.this.scarlet.vrc.auditQueryActored(vrcId, daysBack);
                    if (entries == null)
                    {
                        hook.sendMessageFormat("Error querying audit actor history for [%s](<https://vrchat.com/home/user/%s>) (%s)", sc.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
                        return;
                    }
                    else if (entries.isEmpty())
                    {
                        hook.sendMessageFormat("No audit actor history for [%s](<https://vrchat.com/home/user/%s>) (%s)", sc.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
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

                    ScarletDiscordJDA.this.new Pagination(event.getId(), sb).queue(hook);
                    
                }); break;
                case "config-info": this.handleInGuildAsync(event, false, hook -> {
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
                    
                    hook.sendMessage(sb.toString()).setEphemeral(true).queue();
                }); break;
                case "export-log": this.handleInGuildAsync(event, false, hook -> {
                    
                    String fileName = event.getOption("file-name", OptionMapping::getAsString);
                    if (fileName == null)
                    {
                        String[] last25logs = ScarletDiscordJDA.this.scarlet.last25logs;
                        if (last25logs.length == 0)
                        {
                            hook.sendMessage("No log files detected, strange...").setEphemeral(false).queue();
                            return;
                        }
                        fileName = last25logs[0];
                    }
                    
                    if (!ScarletLogger.lfpattern.matcher(fileName).find())
                    {
                        hook.sendMessage("Invalid log file name").setEphemeral(false).queue();
                        return;
                    }
                    
                    File logs = new File(Scarlet.dir, "logs"),
                         target = new File(logs, fileName);
                    
                    if (!target.isFile())
                    {
                        hook.sendMessage("That log file does not exist").setEphemeral(false).queue();
                        return;
                    }
                    
                    hook.sendMessageFormat("`%s`", fileName).addFiles(FileUpload.fromData(target)).setEphemeral(false).queue();
                }); break;
                case "set-audit-channel": this.handleInGuildAsync(event, true, hook -> {
                    String auditType0 = event.getOption("audit-event-type").getAsString();
                    GroupAuditType auditType = GroupAuditType.of(auditType0);
                    if (auditType == null)
                    {
                        hook.sendMessageFormat("%s isn't a valid audit log event type", auditType0).setEphemeral(true).queue();
                        return;
                    }
                    OptionMapping channel0 = event.getOption("discord-channel");

                    if (channel0 == null)
                    {
                        ScarletDiscordJDA.this.setAuditChannel(auditType, null);
                        hook.sendMessageFormat("Unassociating VRChat group audit log event type %s (%s) from any channels", auditType.title, auditType.id).setEphemeral(true).queue();
                        return;
                    }
                    
                    GuildChannelUnion channel = channel0.getAsChannel();
                    if (!channel.getType().isMessage())
                    {
                        hook.sendMessageFormat("The channel %s doesn't support message sending", channel.getName()).setEphemeral(true).queue();
                    }
                    else
                    {
                        ScarletDiscordJDA.this.setAuditChannel(auditType, channel);
                        hook.sendMessageFormat("Associating VRChat group audit log event type %s (%s) with channel <#%s>", auditType.title, auditType.id, channel.getId()).setEphemeral(true).queue();
                    }
                    
                }); break;
                case "set-audit-aux-webhooks": this.handleInGuildAsync(event, true, hook -> {
                    String auditType0 = event.getOption("audit-event-type").getAsString();
                    GroupAuditType auditType = GroupAuditType.of(auditType0);
                    if (auditType == null)
                    {
                        hook.sendMessageFormat("%s isn't a valid audit log event type", auditType0).setEphemeral(true).queue();
                        return;
                    }
                    
                    SelectOption[] options = ScarletDiscordJDA.this.scarletAuxWh2webhookUrl
                            .keySet()
                            .stream()
                            .limit(25L)
                            .map($ -> SelectOption.of($, $))
                            .toArray(SelectOption[]::new);
                    UniqueStrings scarletAuxWhs = ScarletDiscordJDA.this.auditType2scarletAuxWh.get(auditType0);
                    String[] defaultOptions = scarletAuxWhs == null ? new String[0] : scarletAuxWhs
                            .strings()
                            .stream()
                            .filter(ScarletDiscordJDA.this.scarletAuxWh2webhookUrl::containsKey)
                            .toArray(String[]::new);
                    
                    hook.sendMessageComponents(ActionRow.of(StringSelectMenu.create("set-audit-aux-webhooks:"+auditType0)
                            .addOptions(options)
                            .setDefaultValues(defaultOptions)
                            .build())).setEphemeral(true).queue();
                    
                }); break;
                case "set-voice-channel": this.handleInGuildAsync(event, true, hook -> {
                    OptionMapping channel0 = event.getOption("discord-channel");

                    if (channel0 == null)
                    {
                        ScarletDiscordJDA.this.audioChannelSf = null;
                        hook.sendMessage("Disabling/disconnecting from voice channel").setEphemeral(true).queue();
                        ScarletDiscordJDA.this.audio.updateChannel();
                        ScarletDiscordJDA.this.save();
                        return;
                    }
                    
                    GuildChannelUnion channel = channel0.getAsChannel();
                    if (channel.getType() != ChannelType.VOICE)
                    {
                        hook.sendMessageFormat("The channel %s isn't a voice channel", channel.getName()).setEphemeral(true).queue();
                    }
                    else
                    {
                        ScarletDiscordJDA.this.audioChannelSf = channel.getId();
                        hook.sendMessageFormat("Enabling/connecting from voice channel <#%s>", channel.getId()).setEphemeral(true).queue();
                        ScarletDiscordJDA.this.audio.updateChannel();
                        ScarletDiscordJDA.this.save();
                    }
                    
                }); break;
                case "set-tts-voice": this.handleInGuildAsync(event, true, hook -> {
                    String voiceName = event.getOption("voice-name").getAsString();
                    
                    if (!ScarletDiscordJDA.this.scarlet.ttsService.selectVoice(voiceName))
                    {
                        hook.sendMessageFormat("Tried to set TTS voice to `%s` (subprocess not responsive)", voiceName).setEphemeral(true).queue();
                        return;
                    }
                    
                    if (!ScarletDiscordJDA.this.scarlet.ttsService.getInstalledVoices().contains(voiceName))
                    {
                        hook.sendMessageFormat("TTS voice `%s` is not installed on this system", voiceName).setEphemeral(true).queue();
                        return;
                    }
                    
                    ScarletDiscordJDA.this.scarlet.eventListener.ttsVoiceName.set(voiceName);
                    
                    hook.sendMessageFormat("Setting TTS voice to `%s`", voiceName).setEphemeral(true).queue();
                    
                }); break;
                case "set-permission-role": this.handleInGuildAsync(event, true, hook -> {
                    String scarletPermission0 = event.getOption("scarlet-permission").getAsString();
                    ScarletPermission scarletPermission = ScarletPermission.of(scarletPermission0);
                    if (scarletPermission == null)
                    {
                        hook.sendMessageFormat("%s isn't a valid Scarlet permission", scarletPermission0).setEphemeral(true).queue();
                        return;
                    }
                    OptionMapping role0 = event.getOption("discord-role");
                    
                    if (role0 == null)
                    {
                        ScarletDiscordJDA.this.setPermissionRole(scarletPermission, null);
                        hook.sendMessageFormat("Unassociating Scarlet permission %s (%s) from any roles", scarletPermission.title, scarletPermission.id).setEphemeral(true).queue();
                        return;
                    }
                    
                    Role role = role0.getAsRole();
                    
                    ScarletDiscordJDA.this.setPermissionRole(scarletPermission, role);
                    hook.sendMessageFormat("Associating Scarlet permission %s (%s) with role <@%s>", scarletPermission.title, scarletPermission.id, role.getId()).setEphemeral(true).queue();
                    
                }); break;
                }
            }
            catch (Exception ex)
            {
                this.interactionError(event, ex);
            }
        }

        @Override
        public void onButtonInteraction(ButtonInteractionEvent event)
        {
            if (!this.isInGuild(event))
                return;
            try
            {
                String[] parts = event.getButton().getId().split(":");
                switch (parts[0])
                {
                case "pagination": try
                {
                    String paginationId = parts[1];
                    int pageOrdinal = MiscUtils.parseIntElse(parts[2], 1);
                    Pagination pagination = ScarletDiscordJDA.this.pagination.get(paginationId);
                    if (pagination == null || pagination.removeIfExpired())
                    {
                        event.reply("Pagination interaction expired")
                            .setEphemeral(true)
                            .queue(m -> m.deleteOriginal().queueAfter(3L, TimeUnit.SECONDS));
                    }
                    else
                    {
                        pagination.action(pageOrdinal).queue();
                        event.deferEdit().queue();
                    }
                }
                catch (Exception ex)
                {
                    LOG.error("Exception sync handling of pagination", ex);
                    event.reply("Exception sync handling of pagination:\n`"+ex+"`").setEphemeral(true).queue();
                } break;
                case "edit-tags": this.handleInGuildAsync(event, true, hook -> {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_SET_TAGS);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            hook.sendMessage("You do not have permission to select event tags.").setEphemeral(true).queue();
                            return;
                        }
                    }
                    
                    // TODO : set default selected
                    
                    List<ScarletModerationTags.Tag> tags = ScarletDiscordJDA.this.scarlet.moderationTags.getTags();
                    
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
                    
                }); break;
                case "vrchat-user-ban": this.handleInGuildAsync(event, true, hook -> {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_BAN_USER);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            hook.sendMessage("You do not have permission to ban users.").setEphemeral(true).queue();
                            return;
                        }
                    }

                    String vrcTargetId = parts[1];
                    
                    String vrcActorId = ScarletDiscordJDA.this.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
                    if (vrcActorId == null)
                    {
                        hook.sendMessage("You must have linked ids to perform this action").setEphemeral(true).queue();
                        return;
                    }
                    
                    long within1day = System.currentTimeMillis() - 86400_000L;
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcTargetId, within1day);
                    if (sc == null)
                    {
                        hook.sendMessageFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
                        return;
                    }
                    
                    GroupMemberStatus status = ScarletDiscordJDA.this.scarlet.vrc.getMembership(vrcTargetId);
                    
                    if (status == GroupMemberStatus.BANNED)
                    {
                        hook.sendMessage("This VRChat user is already banned").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (ScarletDiscordJDA.this.scarlet.pendingModActions.addPending(GroupAuditType.USER_BAN, vrcTargetId, vrcActorId) != null)
                    {
                        hook.sendMessage("This VRChat user currently has automated/assisted moderation pending, please retry later").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (!ScarletDiscordJDA.this.scarlet.vrc.banFromGroup(vrcTargetId))
                    {
                        hook.sendMessageFormat("Failed to ban %s", sc.getDisplayName()).setEphemeral(true).queue();
                        return;
                    }
                    
                    hook.sendMessageFormat("Banned %s", sc.getDisplayName()).setEphemeral(false).queue();
                }); break;
                case "vrchat-user-unban": this.handleInGuildAsync(event, true, hook -> {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_UNBAN_USER);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            hook.sendMessage("You do not have permission to unban users.").setEphemeral(true).queue();
                            return;
                        }
                    }

                    String vrcTargetId = parts[1];
                    
                    String vrcActorId = ScarletDiscordJDA.this.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
                    if (vrcActorId == null)
                    {
                        hook.sendMessage("You must have linked ids to perform this action").setEphemeral(true).queue();
                        return;
                    }
                    
                    long within1day = System.currentTimeMillis() - 86400_000L;
                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcTargetId, within1day);
                    if (sc == null)
                    {
                        hook.sendMessageFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
                        return;
                    }
                    
                    GroupMemberStatus status = ScarletDiscordJDA.this.scarlet.vrc.getMembership(vrcTargetId);
                    
                    if (status != GroupMemberStatus.BANNED)
                    {
                        hook.sendMessage("This VRChat user is not banned").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (ScarletDiscordJDA.this.scarlet.pendingModActions.addPending(GroupAuditType.USER_UNBAN, vrcTargetId, vrcActorId) != null)
                    {
                        hook.sendMessage("This VRChat user currently has automated/assisted moderation pending, please retry later").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (!ScarletDiscordJDA.this.scarlet.vrc.unbanFromGroup(vrcTargetId))
                    {
                        hook.sendMessageFormat("Failed to unban %s", sc.getDisplayName()).setEphemeral(true).queue();
                        return;
                    }
                    
                    hook.sendMessageFormat("Unbanned %s", sc.getDisplayName()).setEphemeral(false).queue();
                }); break;
                case "edit-desc": { // FIXME : can't defer a modal
                    
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
                    
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
                    if (auditEntryMeta != null)
                        ti.setValue(auditEntryMeta.entryDescription);
                    
                    Modal.Builder m = Modal.create("edit-desc:"+auditEntryId, "Edit description")
                        .addActionRow(ti.build())
                        ;
                    
                    event.replyModal(m.build()).queue();
                    
                } break;
                case "vrchat-report": this.handleInGuildAsync(event, true, hook -> {
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_USE_REPORT_LINK);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            hook.sendMessage("You do not have permission to use the event report link.").setEphemeral(true).queue();
                            return;
                        }
                    }
                    
                    String auditEntryId = parts[1];
                    
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
                        metaTags = auditEntryMeta.entryTags.toArray();

                        long within1day = System.currentTimeMillis() - 86400_000L;
                        User targetUser = ScarletDiscordJDA.this.scarlet.vrc.getUser(targetUserId, within1day);
                        if (targetUser != null)
                        {
                            targetDisplayName = targetUser.getDisplayName();
                        }
                    }
                    
                    String reportSubject = targetDisplayName;
                    
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
                                      .append(ScarletDiscordJDA.this.scarlet.moderationTags.getTagLabel(metaTag))
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
                                  .append(ScarletDiscordJDA.this.scarlet.vrc.groupId)
                                  .append("<br>")
                                  .append("Audit ID: ")
                                  .append(auditEntryId)
                                  ;
                    }
                    
                    String requestingUserId = eventUserId != null ? eventUserId : actorUserId;
                    
                    String requestingEmail = ScarletDiscordJDA.this.requestingEmail.get();
                    
                    String link = VRChatHelpDeskURLs.newModerationRequest(
                        requestingEmail,
                        VRChatHelpDeskURLs.ModerationCategory.USER_REPORT,
                        requestingUserId,
                        targetUserId,
                        reportSubject,
                        reportDesc.length() == 0 ? null : reportDesc.toString()
                    );
                    
                    hook.sendMessageFormat("[Open new VRChat User Moderation Request](<%s>)", link).setEphemeral(true).queue();
                    
                    if (eventUserId == null)
                    {
                        hook.sendMessage("## WARNING\nThis link autofills the requesting user id of the **audit actor, not necessarily you**\nAssociate your Discord and VRChat ids with `/associate-ids`.\n\n").setEphemeral(true).queue();
                    }
                    
                }); break;
                case "submit-evidence": this.handleInGuildAsync(event, true, hook -> {
                    
                    String messageSnowflake = parts[1];
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.EVENT_SUBMIT_EVIDENCE);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            hook.sendMessage("You do not have permission to submit event attachments.").setEphemeral(true).queue();
                            return;
                        }
                    }
                    
                    String evidenceRoot = ScarletDiscordJDA.this.evidenceRoot;
                    
                    if (evidenceRoot == null || (evidenceRoot = evidenceRoot.trim()).isEmpty())
                    {
                        hook.sendMessage("This feature is not enabled.").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (!event.getChannelType().isThread())
                    {
                        hook.sendMessage("You must reply to an audit event message in the relevant thread.").setEphemeral(true).queue();
                        return;
                    }
                    Message message = event.getChannel().retrieveMessageById(messageSnowflake).complete();
                    
                    MessageReference ref = message.getMessageReference();
                    
                    if (ref == null)
                    {
                        hook.sendMessage("You must reply to an audit event message in the relevant thread.").setEphemeral(true).queue();
                        return;
                    }
                    
                    Message replyTarget = ref.getMessage();
                    
                    if (replyTarget == null)
                    {
                        replyTarget = ref.resolve().complete();
                    }
                    
                    if (replyTarget == null)
                    {
                        hook.sendMessage("The target message is no longer available.").setEphemeral(true).queue();
                        return;
                    }
                    
                    String[] partsRef = replyTarget
                        .getButtons()
                        .stream()
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
                    
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
                    if (auditEntryMeta == null || auditEntryMeta.entry == null)
                    {
                        hook.sendMessage("Could not load audit event.").setEphemeral(true).queue();
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
                                hook.sendMessageFormat("File '%s' already exists, skipping.", fileName).setEphemeral(true).queue();
                            }
                            else try
                            {
                                attachment.getProxy().downloadToFile(dest).join();
                                
                                auditEntryTargetUserMeta.addUserCaseEvidence(new ScarletData.EvidenceSubmission(auditEntryId, requesterSf, requesterDisplayName, timestamp, fileName, attachmentUrl, attachmentProxyUrl));
                                
                                hook.sendMessageFormat("Saved '%s/%s'.", auditEntryTargetId, fileName).setEphemeral(true).queue();
                                LOG.info(String.format("%s (<@%s>) saved evidence to '%s/%s'.", requesterDisplayName, requesterSf, auditEntryTargetId, fileName));
                            }
                            catch (Exception ex)
                            {
                                hook.sendMessageFormat("Exception saving '%s'.", attachment.getFileName()).setEphemeral(true).queue();
                                LOG.error("Exception whilst saving attachment", ex);
                            }
                        }
                        
                        ScarletDiscordJDA.this.scarlet.data.userMetadata(auditEntryTargetId, auditEntryTargetUserMeta);
                    
                    }
                    
                }); break;
                case "import-watched-groups": this.handleInGuildAsync(event, true, hook -> {
                    
                    String messageSnowflake = parts[1];
                    
                    String roleSnowflake = ScarletDiscordJDA.this.getPermissionRole(ScarletPermission.CONFIG_IMPORT_WATCHED_GROUPS);
                    if (roleSnowflake != null)
                    {
                        if (event.getMember().getRoles().stream().map(Role::getId).noneMatch(roleSnowflake::equals))
                        {
                            hook.sendMessage("You do not have permission to import watched groups.").setEphemeral(true).queue();
                            return;
                        }
                    }
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
                                if (ScarletDiscordJDA.this.scarlet.watchedGroups.importLegacyCSV(reader, true))
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
                                if (ScarletDiscordJDA.this.scarlet.watchedGroups.importJson(reader, true))
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
                
                }); break;
                }
            }
            catch (Exception ex)
            {
                this.interactionError(event, ex);
            }
        }

        @Override
        public void onModalInteraction(ModalInteractionEvent event)
        {
            if (!this.isInGuild(event))
                return;
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
                this.interactionError(event, ex);
            }
        }

        @Override
        public void onStringSelectInteraction(StringSelectInteractionEvent event)
        {
            if (!this.isInGuild(event))
                return;
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
                case "set-audit-aux-webhooks": {
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
                        ScarletDiscordJDA.this.auditType2scarletAuxWh.remove(auditType0);
                    }
                    else
                    {
                        event.replyFormat("Setting auxiliary webhooks for %s:\n%s", auditType0, event.getValues().stream().collect(Collectors.joining(", "))).setEphemeral(true).queue();
                        ScarletDiscordJDA.this.auditType2scarletAuxWh.put(auditType0, new UniqueStrings(event.getValues()));
                    }
                } break;
                }
            }
            catch (Exception ex)
            {
                this.interactionError(event, ex);
            }
        }
        
        void updateAuxMessage(MessageChannel channel, ScarletData.AuditEntryMetadata auditEntryMeta)
        {
            if (auditEntryMeta.hasMessage() && auditEntryMeta.auxMessageSnowflake != null && auditEntryMeta.entry != null)
            {
                ScarletData.UserMetadata actorMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata(auditEntryMeta.entry.getActorId());
                String content = actorMeta == null || actorMeta.userSnowflake == null ? ("Unknown Discord id for actor "+auditEntryMeta.entry.getActorDisplayName()) : ("<@"+actorMeta.userSnowflake+">");
                if (auditEntryMeta.entryTags != null && auditEntryMeta.entryTags.size() > 0)
                {
                    String joined = auditEntryMeta.entryTags.strings().stream().map(ScarletDiscordJDA.this.scarlet.moderationTags::getTagLabel).collect(Collectors.joining(", "));
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
        if (this.jda == null)
            return;
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
            .setColor(GroupAuditType.color(this.auditType2color, entry.getEventType()))
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

    void emitAuxWh(AuditEntryMetadata entryMeta, Function<IncomingWebhookClient, AbstractWebhookMessageAction<?, ?>> function)
    {
        this.emitAuxWh(entryMeta.entry.getEventType(), function);
    }
    void emitAuxWh(String type, Function<IncomingWebhookClient, AbstractWebhookMessageAction<?, ?>> function)
    {
        this.emitAuxWh(type, null, (client, state) -> function.apply(client));
    }
    <State> void emitAuxWh(AuditEntryMetadata entryMeta, Supplier<State> state, BiFunction<IncomingWebhookClient, State, AbstractWebhookMessageAction<?, ?>> bifunction)
    {
        this.emitAuxWh(entryMeta.entry.getEventType(), state, bifunction);
    }
    <State> void emitAuxWh(String type, Supplier<State> state, BiFunction<IncomingWebhookClient, State, AbstractWebhookMessageAction<?, ?>> bifunction)
    {
        UniqueStrings scarletAuxWhs = this.auditType2scarletAuxWh.get(type);
        if (scarletAuxWhs == null || scarletAuxWhs.isEmpty())
            return;
        List<IncomingWebhookClient> clients = new ArrayList<>();
        for (String scarletAuxWh : scarletAuxWhs.toArray())
        {
            IncomingWebhookClient client = this.getOrCreateIWC(scarletAuxWh);
            if (client == null)
            {
                LOG.warn("Aux webhook `"+scarletAuxWh+"` referenced by audit event type "+type+" does not exist, removing");
                scarletAuxWhs.remove(scarletAuxWh);
                if (scarletAuxWhs.isEmpty())
                {
                    this.auditType2scarletAuxWh.remove(type);
                }
            }
            else
            {
                clients.add(client);
            }
        }
        if (clients.isEmpty())
            return;
        this.scarlet.exec.submit(() ->
        {
            State state0 = state == null ? null : state.get();
            for (IncomingWebhookClient client : clients) try
            {
                AbstractWebhookMessageAction<?, ?> action = bifunction.apply(client, state0);
                if (action != null)
                {
                    action.completeAfter(3_000L, TimeUnit.MILLISECONDS);
                }
            }
            catch (Exception ex)
            {
                LOG.error("Exception emitting aux embed for "+type, ex);
            }
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
            List<LimitedUserGroups> lugs = null;
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
                jsonUserGroups = JSON.getGson().toJson(lugs = new UsersApi(this.scarlet.vrc.client).getUserGroups(entryMeta.entry.getTargetId()), LIST_LUGROUPS.getType());
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

            if (target != null)
            {
                String epochJoined = Long.toUnsignedString(target.getDateJoined().toEpochDay() * 86400L);
                embed.addField("Account age", "<t:"+epochJoined+":D> (<t:"+epochJoined+":R>)", false);
                embed.addField("Age verification", "`"+target.getAgeVerificationStatus()+"`", false);
            }
            if (history != null)
                embed.addField("History", history, false);
            if (recent != null)
                embed.addField("Most recent", recent, false);
            if (lugs != null && !lugs.isEmpty())
            {
                List<LimitedUserGroups> wgs = lugs.stream()
                    .filter(lug -> this.scarlet.watchedGroups.getWatchedGroup(lug.getGroupId()) != null)
                    .collect(Collectors.toList());
                if (!wgs.isEmpty())
                {
                    StringBuilder sb = new StringBuilder();
                    wgs.forEach(wg -> sb.append(wg.getName()).append('\n'));
                    embed.addField("Watched group membership", sb.toString(), false);
                }
            }
            if (entryMeta.hasAuxActor())
                embed.addField("Action taken through automation/assistance", "", false);
            
            Message message = channel
                .sendMessageEmbeds(embed.build())
                .complete();
            
            ThreadChannel threadChannel = channel
                .createThreadChannel(entryMeta.entry.getDescription(), message.getId())
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
                .completeAfter(1000L, TimeUnit.MILLISECONDS);
            
            entryMeta.threadSnowflake = threadChannel.getId();
            
            boolean mention;
            switch (GroupAuditType.of(entryMeta.entry.getEventType()))
            {
            case INSTANCE_WARN: mention = ScarletDiscordJDA.this.pingOnModeration_instanceWarn.get(); break;
            case INSTANCE_KICK: mention = ScarletDiscordJDA.this.pingOnModeration_instanceKick.get(); break;
            case MEMBER_REMOVE: mention = ScarletDiscordJDA.this.pingOnModeration_memberRemove.get(); break;
            case USER_BAN:      mention = ScarletDiscordJDA.this.pingOnModeration_userBan     .get(); break;
            case USER_UNBAN:    mention = ScarletDiscordJDA.this.pingOnModeration_userUnban   .get(); break;
            default:            mention = false;                                                      break;
            }
            
            String content = actorMeta == null || actorMeta.userSnowflake == null
                    ? ("Unknown Discord id for actor "+entryMeta.entry.getActorDisplayName())
                    : (mention ? ("<@"+actorMeta.userSnowflake+">") : (entryMeta.entry.getActorDisplayName()));
            Message auxMessage = threadChannel.sendMessage(content)
                .addContent("\nUnclaimed")
                .addFiles(FileUpload.fromData(fileData, entryMeta.entry.getTargetId()+".json").asSpoiler())
                .addActionRow(Button.primary("edit-tags:"+entryMeta.entry.getId(), "Edit tags"),
                              Button.primary("edit-desc:"+entryMeta.entry.getId(), "Edit description"),
                              Button.primary("vrchat-report:"+entryMeta.entry.getId(), "Get report link"))
                .addActionRow(Button.danger("vrchat-user-ban:"+entryMeta.entry.getTargetId(), "Ban user"),
                              Button.success("vrchat-user-unban:"+entryMeta.entry.getTargetId(), "Unban user"))
                .completeAfter(1000L, TimeUnit.MILLISECONDS);
            
            entryMeta.auxMessageSnowflake = auxMessage.getId();
            
            return message;
        });
        
    }

    String getLocationImage(String location)
    {
        try
        {
            String worldId = location.substring(0, location.indexOf(':'));
            long within1day = System.currentTimeMillis() - 86400_000L;
            World world = this.scarlet.vrc.getWorld(worldId, within1day);
            if (world != null)
                return world.getImageUrl();
        }
        catch (Exception ex)
        {
            LOG.error("Exception getting world image", ex);
        }
        return null;
    }

    @Override
    public void emitInstanceCreate(Scarlet scarlet, AuditEntryMetadata entryMeta, String location)
    {
        String worldImageUrl = this.getLocationImage(location);
        
        scarlet.data.liveInstancesMetadata_setLocationAudit(location, entryMeta.entry.getId());
        this.condEmitEmbed(entryMeta, true, "Instance Open", "https://vrchat.com/home/launch?worldId="+location.replaceFirst(":", "&instanceId="), null, (channelSf, guild, channel, embed) ->
        {
            embed.setImage(worldImageUrl);
            this.emitAuxWh(entryMeta, embed::build, IncomingWebhookClient::sendMessageEmbeds);
        });
    }

    @Override
    public void emitInstanceClose(Scarlet scarlet, AuditEntryMetadata entryMeta, String location)
    {
        String worldImageUrl = this.getLocationImage(location);
        
        String prevAuditEntryId = scarlet.data.liveInstancesMetadata_getLocationAudit(location, true);
        AuditEntryMetadata prevEntryMeta = prevAuditEntryId == null ? null : scarlet.data.auditEntryMetadata(prevAuditEntryId);
        this.condEmit(entryMeta, (channelSf, guild, channel) ->
        {
            boolean hasPrev = prevEntryMeta != null && prevEntryMeta.hasMessage();
            MessageCreateAction mca = channel
                .sendMessageEmbeds(this
                    .embed(entryMeta.entry, true)
                    .setImage(worldImageUrl)
                    .setTitle("Instance Close", hasPrev ? prevEntryMeta.getMessageUrl() : null)
                    .build());
            if (hasPrev && Objects.equals(channelSf, prevEntryMeta.channelSnowflake))
                mca.mentionRepliedUser(false).setMessageReference(prevEntryMeta.messageSnowflake);
            return mca.complete();
        });
        this.emitAuxWh(entryMeta,
            () -> this.embed(entryMeta.entry, true)
                      .setImage(worldImageUrl)
                      .setTitle("Instance Close")
                      .build(),
            IncomingWebhookClient::sendMessageEmbeds);
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
        {
            embed.setDescription(null) // override description
                .setDescription(post.text)
                .setImage(MiscUtils.latestContentUrlOrNull(post.imageId));
            this.emitAuxWh(entryMeta, embed::build, IncomingWebhookClient::sendMessageEmbeds);
        });
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
