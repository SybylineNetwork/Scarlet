package net.sybyline.scarlet;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.JFileChooser;

import org.apache.commons.text.similarity.FuzzyScore;
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
import io.github.vrchatapi.model.GroupLimitedMember;
import io.github.vrchatapi.model.GroupMemberStatus;
import io.github.vrchatapi.model.GroupRole;
import io.github.vrchatapi.model.LimitedUserGroups;
import io.github.vrchatapi.model.RepresentedGroup;
import io.github.vrchatapi.model.User;
import io.github.vrchatapi.model.World;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IncomingWebhookClient;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
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
import net.dv8tion.jda.api.requests.FluentRestAction;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.AbstractWebhookMessageAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import net.sybyline.scarlet.ScarletData.AuditEntryMetadata;
import net.sybyline.scarlet.ScarletData.InstanceEmbedMessage;
import net.sybyline.scarlet.log.ScarletLogger;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.Location;
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
        this.appendTemplateFooter = scarlet.ui.settingBool("vrchat_report_template_footer", "Append footer to template", true);
        this.bundleModerations_instanceKick2userBan = scarlet.ui.settingBool("discord_bundle_instance_kick_with_user_ban", "Discord: Bundle Instance Kicks with causing User Ban", true);
        this.moderationSummary_onlyActivity = scarlet.ui.settingBool("moderation_summary_only_activity", "Moderation summary: only list staff with activity", true);
        this.pingOnModeration_instanceWarn = scarlet.ui.settingBool("discord_ping_instance_warn", "Discord: Ping on Instance Warn", false);
        this.pingOnModeration_instanceKick = scarlet.ui.settingBool("discord_ping_instance_kick", "Discord: Ping on Instance Kick", false);
        this.pingOnModeration_memberRemove = scarlet.ui.settingBool("discord_ping_member_remove", "Discord: Ping on Member Remove", true);
        this.pingOnModeration_userBan = scarlet.ui.settingBool("discord_ping_user_ban", "Discord: Ping on User Ban", true);
        this.pingOnModeration_userUnban = scarlet.ui.settingBool("discord_ping_user_unban", "Discord: Ping on User Unban", false);
        this.evidenceEnabled = scarlet.ui.settingBool("evidence_enabled", "Evidence submission", false);
        this.selectEvidenceRoot = scarlet.ui.settingVoid("Evidence root folder", "Select", this::selectEvidenceRoot);
        this.evidenceFilePathFormat = scarlet.ui.settingString("evidence_file_path_format", "Evidence file path format", "");
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
    final ScarletUI.Setting<String> requestingEmail,
                                    evidenceFilePathFormat;
    final ScarletUI.Setting<Boolean> appendTemplateFooter,
                                     bundleModerations_instanceKick2userBan,
                                     moderationSummary_onlyActivity,
                                     pingOnModeration_instanceWarn,
                                     pingOnModeration_instanceKick,
                                     pingOnModeration_memberRemove,
                                     pingOnModeration_userBan,
                                     pingOnModeration_userUnban,
                                     evidenceEnabled;
    final ScarletUI.Setting<Void> selectEvidenceRoot;
    final Map<String, Pagination> pagination = new ConcurrentHashMap<>();
    final Map<String, Command.Choice> userSf2lastEdited_groupId = new ConcurrentHashMap<>();
    Map<String, UniqueStrings> scarletPermission2roleSf = new HashMap<>();
    Map<String, String> scarletAuxWh2webhookUrl = new ConcurrentHashMap<>();
    Map<String, IncomingWebhookClient> scarletAuxWh2incomingWebhookClient = new ConcurrentHashMap<>();
    Map<String, String> auditType2channelSf = new HashMap<>();
    Map<String, String> auditExType2channelSf = new HashMap<>();
    Map<String, UniqueStrings> auditType2scarletAuxWh = new ConcurrentHashMap<>();
    Map<String, String> auditType2secretChannelSf = new HashMap<>();
    Map<String, String> auditExType2secretChannelSf = new HashMap<>();
    Map<String, Integer> auditType2color = new HashMap<>();
    @FunctionalInterface interface ImmediateHandler { void handle() throws Exception; }
    @FunctionalInterface interface DeferredHandler { void handle(InteractionHook hook) throws Exception; }

    static final String[] AUDIT_EVENT_IDS = MiscUtils.map(GroupAuditType.values(), String[]::new, GroupAuditType::id);
    static final Command.Choice[] AUDIT_EVENT_CHOICES = MiscUtils.map(GroupAuditType.values(), Command.Choice[]::new, $ -> new Command.Choice($.title, $.id));

    static final String[] AUDIT_EX_EVENT_IDS = MiscUtils.map(GroupAuditTypeEx.values(), String[]::new, GroupAuditTypeEx::id);
    static final Command.Choice[] AUDIT_EX_EVENT_CHOICES = MiscUtils.map(GroupAuditTypeEx.values(), Command.Choice[]::new, $ -> new Command.Choice($.title, $.id));

    static final String[] SCARLET_PERMISSION_IDS = MiscUtils.map(ScarletPermission.values(), String[]::new, ScarletPermission::id);
    static final Command.Choice[] SCARLET_PERMISSION_CHOICES = MiscUtils.map(ScarletPermission.values(), Command.Choice[]::new, $ -> new Command.Choice($.title, $.id));

    static final Command.Choice[] UTC_OFFSET_HOURS_CHOICES =
    {
        new Command.Choice("Midnight for UTC-10:00 (HST)", "-10:00"),
        new Command.Choice("Midnight for UTC-09:00 (HDT)", "-09:00"),
        new Command.Choice("Midnight for UTC-08:00 (PST)", "-08:00"),
        new Command.Choice("Midnight for UTC-07:00 (MST) [PDT]", "-07:00"),
        new Command.Choice("Midnight for UTC-06:00 (CST) [MDT]", "-06:00"),
        new Command.Choice("Midnight for UTC-05:00 (EST) [CDT]", "-05:00"),
        new Command.Choice("Midnight for UTC-04:00 (AST) [EDT]", "-04:00"),
        new Command.Choice("Midnight for UTC-03:00 (BRT, ART) [ADT]", "-03:00"),
        new Command.Choice("Midnight for UTC-02:00 [BRST]", "-02:00"),
        new Command.Choice("Midnight for UTC+00:00 (GMT, WET)", "+00:00"),
        new Command.Choice("Midnight for UTC+01:00 (CET, WAT, [BST, WEST]", "+01:00"),
        new Command.Choice("Midnight for UTC+02:00 (EET, CAT, WAST) [CEST]", "+02:00"),
        new Command.Choice("Midnight for UTC+03:00 (FET, EAT, MSK) [EEST]", "+03:00"),
        new Command.Choice("Midnight for UTC+04:00 (GST) [MSD]", "+04:00"),
        new Command.Choice("Midnight for UTC+05:30 (IST)", "+05:30"),
        new Command.Choice("Midnight for UTC+07:00 (ICT, WIB)", "+07:00"),
        new Command.Choice("Midnight for UTC+08:00 (HKT, CST, WIT, MYT, PHT, SGT, AWST)", "+08:00"),
        new Command.Choice("Midnight for UTC+08:30 (PYT)", "+08:30"),
        new Command.Choice("Midnight for UTC+09:00 (JST, KST, WITA) [AWDT]", "+09:00"),
        new Command.Choice("Midnight for UTC+09:30 (ACST)", "+09:30"),
        new Command.Choice("Midnight for UTC+10:00 (AEST)", "+10:00"),
        new Command.Choice("Midnight for UTC+10:30 [ACDT]", "+10:30"),
        new Command.Choice("Midnight for UTC+11:00 [AEDT]", "+11:00"),
        new Command.Choice("Midnight for UTC+12:00 (NZST)", "+12:00"),
        new Command.Choice("Midnight for UTC+13:00 [NZDT]", "+13:00"),
    };

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
        this.jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.playing(Scarlet.NAME+", a VRChat group moderation tool").withState(Scarlet.GITHUB_URL));
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
//                Commands.slash("associate-ids", "Associates a specific Discord user with a specific VRChat user")
//                    .setGuildOnly(true)
//                    .setDefaultPermissions(defaultCommandPerms)
//                    .addOption(OptionType.USER, "discord-user", "The Discord user", true)
//                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true),
                Commands.slash("staff-list", "Configures the staff list")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addSubcommands(
                        new SubcommandData("list", "Lists all staff users")
                            .addOptions(new OptionData(OptionType.INTEGER, "entries-per-page", "The number of users to show per page").setRequiredRange(1L, 10L)),
                        new SubcommandData("add", "Adds a user to the staff list")
                            .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true)
                            .addOption(OptionType.USER, "discord-user", "The Discord user"),
                        new SubcommandData("delete", "Removes a user from the staff list")
                            .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true)
                    ),
                Commands.slash("secret-staff-list", "Configures the secret staff list")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addSubcommands(
                        new SubcommandData("list", "Lists all secret staff users")
                            .addOptions(new OptionData(OptionType.INTEGER, "entries-per-page", "The number of users to show per page").setRequiredRange(1L, 10L)),
                        new SubcommandData("add", "Adds a user to the secret staff list")
                            .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true)
                            .addOption(OptionType.USER, "discord-user", "The Discord user"),
                        new SubcommandData("delete", "Removes a user from the secret staff list")
                            .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true)
                    ),
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
                    .addOption(OptionType.STRING, "vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, true)
                    .addOption(OptionType.INTEGER, "days-back", "The number of days into the past to search for events"),
                Commands.slash("set-audit-channel", "Sets a given text channel as the channel certain audit event types use")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "audit-event-type", "The VRChat Group Audit Log event type", true, true)
                    .addOption(OptionType.CHANNEL, "discord-channel", "The Discord text channel to use, or omit to remove entry"),
                Commands.slash("set-audit-aux-webhooks", "Sets the given webhooks as the webhooks certain audit event types use")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "audit-event-type", "The extended audit event type", true, true),
                Commands.slash("set-audit-ex-channel", "Sets a given text channel as the channel certain extended event types use")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "audit-ex-event-type", "The extended audit event type", true, true)
                    .addOption(OptionType.CHANNEL, "discord-channel", "The Discord text channel to use, or omit to remove entry"),
                Commands.slash("set-audit-secret-channel", "Sets a given text channel as the secret channel certain audit event types use")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "audit-event-type", "The VRChat Group Audit Log event type", true, true)
                    .addOption(OptionType.CHANNEL, "discord-channel", "The Discord text channel to use, or omit to remove entry"),
                Commands.slash("set-audit-ex-secret-channel", "Sets a given text channel as the secret channel certain extended event types use")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "audit-ex-event-type", "The extended audit event type", true, true)
                    .addOption(OptionType.CHANNEL, "discord-channel", "The Discord text channel to use, or omit to remove entry"),
                Commands.slash("set-voice-channel", "Sets a given voice channel as the channel in which to announce TTS messages")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.CHANNEL, "discord-channel", "The Discord voice channel to use, or omit to remove entry"),
                Commands.slash("set-tts-voice", "Selects which TTS voice is used to make announcements")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "voice-name", "The name of the installed voice to use", true, true),
                Commands.slash("scarlet-permission", "Sets a given Scarlet-specific permission to be associated with certain Discord roles")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addSubcommands(
                        new SubcommandData("list", "Lists all Scarlet permissions and associated roles")
                            .addOptions(new OptionData(OptionType.INTEGER, "entries-per-page", "The number of permissions to show per page").setRequiredRange(1L, 10L)),
                        new SubcommandData("add-to-role", "Grants the Scarlet-specific permission to the given role")
                            .addOption(OptionType.STRING, "scarlet-permission", "The Scarlet-specific permission", true, true)
                            .addOption(OptionType.ROLE, "discord-role", "The Discord role to which to grant the Scarlet-specific permission", true),
                        new SubcommandData("delete-from-role", "Revokes the Scarlet-specific permission for the given role")
                            .addOption(OptionType.STRING, "scarlet-permission", "The Scarlet-specific permission", true, true)
                            .addOption(OptionType.ROLE, "discord-role", "The Discord role for which to revoke the Scarlet-specific permission", true),
                        new SubcommandData("edit-role", "Editd the Scarlet-specific permissions for the given role")
                            .addOption(OptionType.ROLE, "discord-role", "The Discord role for which to edit the Scarlet-specific permissions", true)
                    ),
                Commands.slash("config-info", "Shows information about the current configuration")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms),
                Commands.slash("config-set", "Configures miscellaneous settings")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addSubcommands(
                        new SubcommandData("mod-summary-time-of-day", "Set what time of day to generate a summary of the previous 24 hours of moderation activity")
                            .addOption(OptionType.STRING, "utc-offset-hours", "The hour of the day, relative to UTC, to generate a summary for the last 24 hours", true, true)
                    ),
                Commands.slash("moderation-summary", "Generated a summary of moderation actions")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.INTEGER, "hours-back", "The number of hours into the past to search for events"),
                Commands.slash("export-log", "Attaches a log file")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addOption(OptionType.STRING, "file-name", "The name of the log file", false, true),
                Commands.slash("server-restart", "Restarts the Scarlet application")
                    .setGuildOnly(true)
                    .setDefaultPermissions(defaultCommandPerms)
                    .addSubcommands(
                        new SubcommandData("restart-now", "Restarts now"),
                        new SubcommandData("update-now", "Updates the application now")
                            .addOption(OptionType.STRING, "target-version", "The version of Scarlet to which to update", false, true)
                    ),
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

    void selectEvidenceRoot()
    {
        this.scarlet.execModal.submit(() ->
        {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select a Folder");
            chooser.setDialogType(JFileChooser.SAVE_DIALOG);
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (this.evidenceRoot != null && !this.evidenceRoot.trim().isEmpty())
            {
                chooser.setSelectedFile(new File(this.evidenceRoot));
            }
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION)
            {
                
            }
        });
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
        public Map<String, UniqueStrings> scarletPermission2roleSf = new HashMap<>();
        public Map<String, String> scarletAuxWh2webhookUrl = new HashMap<>();
        public Map<String, String> auditType2channelSf = new HashMap<>();
        public Map<String, String> auditExType2channelSf = new HashMap<>();
        public Map<String, UniqueStrings> auditType2scarletAuxWh = new HashMap<>();
        public Map<String, String> auditType2secretChannelSf = new HashMap<>();
        public Map<String, String> auditExType2secretChannelSf = new HashMap<>();
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
        this.auditExType2channelSf = spec.auditExType2channelSf == null ? new HashMap<>() : new HashMap<>(spec.auditExType2channelSf);
        this.auditType2scarletAuxWh = spec.auditType2scarletAuxWh == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(spec.auditType2scarletAuxWh);
        this.auditType2secretChannelSf = spec.auditType2secretChannelSf == null ? new HashMap<>() : new HashMap<>(spec.auditType2secretChannelSf);
        this.auditExType2secretChannelSf = spec.auditExType2secretChannelSf == null ? new HashMap<>() : new HashMap<>(spec.auditExType2secretChannelSf);
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
        spec.auditExType2channelSf = new HashMap<>(this.auditExType2channelSf);
        spec.auditType2scarletAuxWh = new HashMap<>(this.auditType2scarletAuxWh);
        spec.auditType2secretChannelSf = new HashMap<>(this.auditType2secretChannelSf);
        spec.auditExType2secretChannelSf = new HashMap<>(this.auditExType2secretChannelSf);
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
                        event.replyChoices(ScarletDiscordJDA.this.scarlet.moderationTags.getTagChoices()).queue();
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
                case "query-actor-history": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "vrchat-user": {
                        String value = event.getFocusedOption().getValue();
                        FuzzyScore fuzzy = new FuzzyScore(Locale.getDefault());
                        event.replyChoices(Stream.concat(
                            value == null || value.trim().isEmpty() ? Stream.empty() : Stream.of(new Command.Choice(value, value)),
                            ScarletDiscordJDA.this.scarlet
                                .staffList
                                .getStaffNames(ScarletDiscordJDA.this.scarlet.vrc)
                                .entrySet()
                                .stream()
                                .sorted(Comparator.comparingInt((Map.Entry<String, String> $) -> Math.max(fuzzy.fuzzyScore($.getKey(), value), fuzzy.fuzzyScore($.getValue(), value))).reversed())
                                .limit(value == null || value.trim().isEmpty() ? 25L : 24L)
                                .map($ -> new Command.Choice($.getValue(), $.getKey())))
                            .collect(Collectors.toList())).queue();
                    } break;
                    }
                } break;
                case "set-audit-channel": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "audit-event-type": {
                        event.replyChoices(AUDIT_EVENT_CHOICES).queue();
                    } break;
                    }
                } break;
                case "set-audit-aux-webhooks": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "audit-event-type": {
                        event.replyChoices(AUDIT_EVENT_CHOICES).queue();
                    } break;
                    }
                } break;
                case "set-audit-ex-channel": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "audit-ex-event-type": {
                        event.replyChoices(AUDIT_EX_EVENT_CHOICES).queue();
                    } break;
                    }
                } break;
                case "set-audit-secret-channel": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "audit-event-type": {
                        event.replyChoices(AUDIT_EVENT_CHOICES).queue();
                    } break;
                    }
                } break;
                case "set-audit-ex-secret-channel": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "audit-ex-event-type": {
                        event.replyChoices(AUDIT_EX_EVENT_CHOICES).queue();
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
                case "config-set": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "utc-offset-hours": {
                        event.replyChoices(UTC_OFFSET_HOURS_CHOICES).queue();
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
                case "scarlet-permission": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "scarlet-permission": {
                        event.replyChoices(SCARLET_PERMISSION_CHOICES).queue();
                    } break;
                    }
                } break;
                case "server-restart": {
                    switch (event.getFocusedOption().getName())
                    {
                    case "target-version": {
                        String[] allVersions = ScarletDiscordJDA.this.scarlet.allVersions;
                        event.replyChoiceStrings(Arrays.copyOf(allVersions, Math.min(allVersions.length, 25))).queue();
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
//                case "associate-ids": this.handleInGuildAsync(event, true, hook -> {
//                    
//                    net.dv8tion.jda.api.entities.User user = event.getOption("discord-user").getAsUser();
//                    String vrcId = event.getOption("vrchat-user").getAsString();
//
//                    long within1day = System.currentTimeMillis() - 86400_000L;
//                    User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
//                    if (sc == null)
//                    {
//                        hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
//                        return;
//                    }
//                    
//                    ScarletDiscordJDA.this.scarlet.data.linkIdToSnowflake(vrcId, user.getId());
//                    LOG.info(String.format("Linking VRChat user %s (%s) to Discord user %s (<@%s>)", sc.getDisplayName(), vrcId, user.getEffectiveName(), user.getId()));
//                    hook.sendMessageFormat("Associating %s with VRChat user [%s](https://vrchat.com/home/user/%s)", user.getEffectiveName(), sc.getDisplayName(), vrcId).setEphemeral(true).queue();
//                    
//                }); break;
                case "staff-list": this.handleInGuildAsync(event, true, hook -> {
                    
                    int perPage = event.getOption("entries-per-page", 4, OptionMapping::getAsInt);
                    String vrcId = event.getOption("vrchat-user", null, OptionMapping::getAsString);
                    net.dv8tion.jda.api.entities.User user = event.getOption("discord-user", null, OptionMapping::getAsUser);
                    long within1day = System.currentTimeMillis() - 86400_000L;

                    switch (event.getSubcommandName())
                    {
                    case "list": {
                        List<GroupRole> roleList = ScarletDiscordJDA.this.scarlet.vrc.getGroupRoles(ScarletDiscordJDA.this.scarlet.vrc.groupId);
                        Map<String, GroupRole> roles = roleList == null || roleList.isEmpty() ? Collections.emptyMap() : roleList.stream().collect(Collectors.toMap(GroupRole::getId, Function.identity()));
                        MessageEmbed[] embeds = Arrays.stream(ScarletDiscordJDA.this.scarlet.staffList.getStaffIds())
                            .map($ -> {
                                User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser($, within1day);
                                GroupLimitedMember member = ScarletDiscordJDA.this.scarlet.vrc.getGroupMembership(ScarletDiscordJDA.this.scarlet.vrc.groupId, $);
                                ScarletData.UserMetadata userMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata($);
                                EmbedBuilder builder = new EmbedBuilder();
                                builder.setTitle(sc == null ? $ : sc.getDisplayName(), "https://vrchat.com/home/user/"+$);
                                if (sc != null)
                                {
                                    String thumb = sc.getProfilePicOverrideThumbnail();
                                    if (thumb == null || thumb.trim().isEmpty())
                                        thumb = sc.getUserIcon();
                                    if (thumb == null || thumb.trim().isEmpty())
                                        thumb = sc.getCurrentAvatarThumbnailImageUrl();
                                    if (thumb == null || thumb.trim().isEmpty())
                                        thumb = null;
                                    builder.setThumbnail(thumb);
                                    
                                    String statusDesc = sc.getStatusDescription();
                                    if (statusDesc == null || statusDesc.trim().isEmpty())
                                        statusDesc = null;
                                    builder.setDescription(statusDesc);
                                }
                                if (member != null)
                                {
                                    String epochJoined = Long.toUnsignedString(member.getJoinedAt().toEpochSecond());
                                    builder.addField("Joined", "<t:"+epochJoined+":D> (<t:"+epochJoined+":R>)", false);
                                    if (member.getRoleIds() != null && !member.getRoleIds().isEmpty())
                                    {
                                        builder.addField("Roles", member.getRoleIds().stream().map(roles::get).filter(Objects::nonNull).map(GroupRole::getName).collect(Collectors.joining(", ")), false);
                                    }
                                }
                                if (userMeta != null && userMeta.userSnowflake != null)
                                {
                                    builder.addField("Linked Discord", "<@"+userMeta.userSnowflake+">)", false);
                                }
                                return builder.build();
                            })
                            .toArray(MessageEmbed[]::new)
                        ;
                        
                        ScarletDiscordJDA.this.new Pagination(event.getId(), embeds, perPage).queue(hook);
                        
                    } break;
                    case "add": {
                        
                        User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
                        if (sc == null)
                        {
                            hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                            return;
                        }
                        
                        if (user != null)
                        {
                            ScarletDiscordJDA.this.scarlet.data.linkIdToSnowflake(vrcId, user.getId());
                            LOG.info(String.format("Linking VRChat user %s (%s) to Discord user %s (<@%s>)", sc.getDisplayName(), vrcId, user.getEffectiveName(), user.getId()));
                            hook.sendMessageFormat("Associating %s with VRChat user [%s](https://vrchat.com/home/user/%s)", user.getEffectiveName(), sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                        }
                        
                        if (!ScarletDiscordJDA.this.scarlet.staffList.addStaffId(vrcId))
                        {
                            hook.sendMessageFormat("VRChat user [%s](https://vrchat.com/home/user/%s) is already on the staff list", sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                            return;
                        }
                        
                        LOG.info(String.format("Adding VRChat user %s (%s) to the staff list", sc.getDisplayName(), vrcId));
                        hook.sendMessageFormat("Adding VRChat user [%s](https://vrchat.com/home/user/%s) to the staff list", sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                        
                    } break;
                    case "delete": {
                        
                        User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
                        String prefix = sc != null ? "" : ("No VRChat user found with id %s"+vrcId+"\n");
                        String displayName = sc != null ? sc.getDisplayName() : vrcId;
                        
                        if (!ScarletDiscordJDA.this.scarlet.staffList.removeStaffId(vrcId))
                        {
                            hook.sendMessageFormat("%sVRChat user [%s](https://vrchat.com/home/user/%s) is not on the staff list", prefix, displayName, vrcId).setEphemeral(true).queue();
                            return;
                        }
                        
                        LOG.info(String.format("Removing VRChat user %s (%s) from the staff list", displayName, vrcId));
                        hook.sendMessageFormat("Removing VRChat user [%s](https://vrchat.com/home/user/%s) from the staff list", displayName, vrcId).setEphemeral(true).queue();
                        
                    } break;
                    }
                    
                }); break;
                case "secret-staff-list": this.handleInGuildAsync(event, true, hook -> {
                    
                    int perPage = event.getOption("entries-per-page", 4, OptionMapping::getAsInt);
                    String vrcId = event.getOption("vrchat-user", null, OptionMapping::getAsString);
                    net.dv8tion.jda.api.entities.User user = event.getOption("discord-user", null, OptionMapping::getAsUser);
                    long within1day = System.currentTimeMillis() - 86400_000L;

                    switch (event.getSubcommandName())
                    {
                    case "list": {
                        List<GroupRole> roleList = ScarletDiscordJDA.this.scarlet.vrc.getGroupRoles(ScarletDiscordJDA.this.scarlet.vrc.groupId);
                        Map<String, GroupRole> roles = roleList == null || roleList.isEmpty() ? Collections.emptyMap() : roleList.stream().collect(Collectors.toMap(GroupRole::getId, Function.identity()));
                        MessageEmbed[] embeds = Arrays.stream(ScarletDiscordJDA.this.scarlet.secretStaffList.getSecretStaffIds())
                            .map($ -> {
                                User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser($, within1day);
                                GroupLimitedMember member = ScarletDiscordJDA.this.scarlet.vrc.getGroupMembership(ScarletDiscordJDA.this.scarlet.vrc.groupId, $);
                                ScarletData.UserMetadata userMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata($);
                                EmbedBuilder builder = new EmbedBuilder();
                                builder.setTitle(sc == null ? $ : sc.getDisplayName(), "https://vrchat.com/home/user/"+$);
                                if (sc != null)
                                {
                                    String thumb = sc.getProfilePicOverrideThumbnail();
                                    if (thumb == null || thumb.trim().isEmpty())
                                        thumb = sc.getUserIcon();
                                    if (thumb == null || thumb.trim().isEmpty())
                                        thumb = sc.getCurrentAvatarThumbnailImageUrl();
                                    if (thumb == null || thumb.trim().isEmpty())
                                        thumb = null;
                                    builder.setThumbnail(thumb);
                                    
                                    String statusDesc = sc.getStatusDescription();
                                    if (statusDesc == null || statusDesc.trim().isEmpty())
                                        statusDesc = null;
                                    builder.setDescription(statusDesc);
                                }
                                if (member != null)
                                {
                                    String epochJoined = Long.toUnsignedString(member.getJoinedAt().toEpochSecond());
                                    builder.addField("Joined", "<t:"+epochJoined+":D> (<t:"+epochJoined+":R>)", false);
                                    if (member.getRoleIds() != null && !member.getRoleIds().isEmpty())
                                    {
                                        builder.addField("Roles", member.getRoleIds().stream().map(roles::get).filter(Objects::nonNull).map(GroupRole::getName).collect(Collectors.joining(", ")), false);
                                    }
                                }
                                if (userMeta != null && userMeta.userSnowflake != null)
                                {
                                    builder.addField("Linked Discord", "<@"+userMeta.userSnowflake+">)", false);
                                }
                                return builder.build();
                            })
                            .toArray(MessageEmbed[]::new)
                        ;
                        
                        ScarletDiscordJDA.this.new Pagination(event.getId(), embeds, perPage).queue(hook);
                        
                    } break;
                    case "add": {
                        
                        User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
                        if (sc == null)
                        {
                            hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                            return;
                        }
                        
                        if (user != null)
                        {
                            ScarletDiscordJDA.this.scarlet.data.linkIdToSnowflake(vrcId, user.getId());
                            LOG.info(String.format("Linking VRChat user %s (%s) to Discord user %s (<@%s>)", sc.getDisplayName(), vrcId, user.getEffectiveName(), user.getId()));
                            hook.sendMessageFormat("Associating %s with VRChat user [%s](https://vrchat.com/home/user/%s)", user.getEffectiveName(), sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                        }
                        
                        if (!ScarletDiscordJDA.this.scarlet.secretStaffList.addSecretStaffId(vrcId))
                        {
                            hook.sendMessageFormat("VRChat user [%s](https://vrchat.com/home/user/%s) is already on the secret staff list", sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                            return;
                        }
                        
                        LOG.info(String.format("Adding VRChat user %s (%s) to the secret staff list", sc.getDisplayName(), vrcId));
                        hook.sendMessageFormat("Adding VRChat user [%s](https://vrchat.com/home/user/%s) to the secret staff list", sc.getDisplayName(), vrcId).setEphemeral(true).queue();
                        
                    } break;
                    case "delete": {
                        
                        User sc = ScarletDiscordJDA.this.scarlet.vrc.getUser(vrcId, within1day);
                        String prefix = sc != null ? "" : ("No VRChat user found with id %s"+vrcId+"\n");
                        String displayName = sc != null ? sc.getDisplayName() : vrcId;
                        
                        if (!ScarletDiscordJDA.this.scarlet.secretStaffList.removeSecretStaffId(vrcId))
                        {
                            hook.sendMessageFormat("%sVRChat user [%s](https://vrchat.com/home/user/%s) is not on the secret staff list", prefix, displayName, vrcId).setEphemeral(true).queue();
                            return;
                        }
                        
                        LOG.info(String.format("Removing VRChat user %s (%s) from the staff list", displayName, vrcId));
                        hook.sendMessageFormat("Removing VRChat user [%s](https://vrchat.com/home/user/%s) from the secret staff list", displayName, vrcId).setEphemeral(true).queue();
                        
                    } break;
                    }
                    
                }); break;
                case "vrchat-user-ban": this.handleInGuildAsync(event, false, hook -> {
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_BAN_USER, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to ban users.").setEphemeral(true).queue();
                        return;
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
                    
                    GroupMemberStatus status = ScarletDiscordJDA.this.scarlet.vrc.getGroupMembershipStatus(ScarletDiscordJDA.this.scarlet.vrc.groupId, vrcTargetId);
                    
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
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_UNBAN_USER, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to unban users.").setEphemeral(true).queue();
                        return;
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
                    
                    GroupMemberStatus status = ScarletDiscordJDA.this.scarlet.vrc.getGroupMembershipStatus(ScarletDiscordJDA.this.scarlet.vrc.groupId, vrcTargetId);
                    
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
                    
                    if (userMeta.auditEntryIds != null && userMeta.auditEntryIds.length > 0)
                    {
                        sb.append("\n### Moderation events:");
                        for (String auditEntryId : userMeta.auditEntryIds)
                        {
                            if (auditEntryId != null)
                            {
                                sb.append("\n`").append(auditEntryId).append("`");
                                ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
                                if (auditEntryMeta != null && auditEntryMeta.entry != null && !auditEntryMeta.entryRedacted && !auditEntryMeta.hasParentEvent())
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
                        if (sc != null && !ScarletDiscordJDA.this.shouldRedact(vrcId, event.getMember().getId()))
                        {
                            
                            sb.append(String.format("### Linked VRChat user:\n[%s](https://vrchat.com/home/user/%s) `%s`\n", sc.getDisplayName(), vrcId, vrcId));
                        }
                    }
                    
                    sb.append("### Scarlet permissions:");
                    for (ScarletPermission permission : ScarletPermission.values())
                    {
                        UniqueStrings roleSfs = ScarletDiscordJDA.this.scarletPermission2roleSf.get(permission.id);
                        sb.append('\n').append(permission.title).append(": from ");
                        if (roleSfs == null || roleSfs.isEmpty())
                        {
                            sb.append("no roles");
                        }
                        else
                        {
                            String roleMentions = event
                                .getMember()
                                .getRoles()
                                .stream()
                                .filter(role -> roleSfs.contains(role.getId()))
                                .map(Role::getAsMention)
                                .collect(Collectors.joining(", "));
                            if (roleMentions.isEmpty())
                            {
                                sb.append("no roles");
                            }
                            else
                            {
                                sb.append(roleMentions);
                            }
                        }
                    }
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
                    if (userMeta != null && userMeta.userSnowflake != null && !ScarletDiscordJDA.this.shouldRedact(vrcId, event.getMember().getId()))
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
                    else if (entries.isEmpty()
                        // Check is here to avoid side chain vulnerabilities
                        || ScarletDiscordJDA.this.shouldRedact(vrcId, event.getMember().getId()))
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
                    
                    String lastQueryEpoch = Long.toUnsignedString(ScarletDiscordJDA.this.scarlet.settings.lastAuditQuery.getOrSupply().toEpochSecond()),
                         lastRefreshEpoch = Long.toUnsignedString(ScarletDiscordJDA.this.scarlet.settings.lastAuthRefresh.getOrSupply().toEpochSecond());
                    
                    sb.append("\n### Last Audit Query:")
                        .append("\n<t:").append(lastQueryEpoch).append(":F>")
                        .append(" (<t:").append(lastQueryEpoch).append(":R>)");

                    sb.append("\n### Last Auth Refresh:")
                        .append("\n<t:").append(lastRefreshEpoch).append(":F>")
                        .append(" (<t:").append(lastRefreshEpoch).append(":R>)");
                    
                    OffsetDateTime nms = ScarletDiscordJDA.this.scarlet.settings.nextModSummary.getOrNull();
                    if (nms != null)
                    {
                        String nmsEpoch = Long.toUnsignedString(nms.toEpochSecond());
                        sb.append("\n### Next Moderation Summary:")
                            .append("\n<t:").append(nmsEpoch).append(":F>")
                            .append(" (<t:").append(nmsEpoch).append(":R>)");
                    }
                    
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
                        UniqueStrings auxWhs = ScarletDiscordJDA.this.auditType2scarletAuxWh.get(auditType.id);
                        if (auxWhs != null && !auxWhs.isEmpty())
                        {
                            sb.append(auxWhs.stream().collect(Collectors.joining(", ", " (Auxiliary webhooks: ", ")")));
                        }
                    }
                    
                    sb.append("\n### Extended auditing Channels:");
                    for (GroupAuditTypeEx auditTypeEx : GroupAuditTypeEx.values())
                    {
                        String channelSf = ScarletDiscordJDA.this.auditExType2channelSf.get(auditTypeEx.id);
                        sb.append("\n")
                            .append(auditTypeEx.title)
                            .append(" (")
                            .append(auditTypeEx.id)
                            .append("): ")
                            .append(channelSf == null ? "unassigned" : ("<#"+channelSf+">"));
                    }
                    
                    hook.sendMessage(sb.toString()).setEphemeral(true).queue();
                }); break;
                case "config-set": this.handleInGuildAsync(event, true, hook -> {
                    
                    switch (event.getSubcommandName())
                    {
                    case "mod-summary-time-of-day": {
                        String utcOffsetHours = event.getOption("utc-offset-hours", "+00:00:00", OptionMapping::getAsString);
                        ZoneOffset zoneOffset;
                        try
                        {
                            try
                            {
                                zoneOffset = ZoneOffset.ofHours(Integer.parseInt(utcOffsetHours));
                            }
                            catch (NumberFormatException nfex)
                            {
                                try
                                {
                                    zoneOffset = ZoneOffset.ofTotalSeconds((int)(Float.parseFloat(utcOffsetHours) * 3600.0F));
                                }
                                catch (NumberFormatException nfex0)
                                {
                                    zoneOffset = ZoneOffset.of(utcOffsetHours);
                                }
                            }
                        }
                        catch (DateTimeException dtex)
                        {
                            hook.sendMessageFormat("%s", dtex.getMessage()).setEphemeral(true).queue();
                            return;
                        }
                        
                        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC),
                                       offset = now.withHour(0).withMinute(0).withSecond(0).withNano(0).minusSeconds(zoneOffset.getTotalSeconds());
                        while (offset.isBefore(now))
                            offset = offset.plusHours(24L);
                        ScarletDiscordJDA.this.scarlet.settings.nextModSummary.set(offset);
                        String epochNext = Long.toUnsignedString(offset.plusHours(24L).toEpochSecond());
                        hook.sendMessageFormat("Set mod summary generation time to %s\nNext summary at <t:%s:f>", zoneOffset, epochNext).setEphemeral(true).queue();
                    } break;
                    }
                    
                }); break;
                case "moderation-summary": this.handleInGuildAsync(event, true, hook -> {
                    
                    int hoursBack = event.getOption("hours-back", 24, OptionMapping::getAsInt);
                    ScarletDiscordJDA.this.emitModSummary(ScarletDiscordJDA.this.scarlet, OffsetDateTime.now(ZoneOffset.UTC), hoursBack, hook::sendMessageEmbeds);
                    
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
                case "set-audit-ex-channel": this.handleInGuildAsync(event, true, hook -> {
                    String auditExType0 = event.getOption("audit-ex-event-type").getAsString();
                    GroupAuditTypeEx auditExType = GroupAuditTypeEx.of(auditExType0);
                    if (auditExType == null)
                    {
                        hook.sendMessageFormat("%s isn't a valid extended audit event type", auditExType0).setEphemeral(true).queue();
                        return;
                    }
                    OptionMapping channel0 = event.getOption("discord-channel");

                    if (channel0 == null)
                    {
                        ScarletDiscordJDA.this.setAuditExChannel(auditExType, null);
                        hook.sendMessageFormat("Unassociating VRChat extended group audit event type %s (%s) from any channels", auditExType.title, auditExType.id).setEphemeral(true).queue();
                        return;
                    }
                    
                    GuildChannelUnion channel = channel0.getAsChannel();
                    if (!channel.getType().isMessage())
                    {
                        hook.sendMessageFormat("The channel %s doesn't support message sending", channel.getName()).setEphemeral(true).queue();
                    }
                    else
                    {
                        ScarletDiscordJDA.this.setAuditExChannel(auditExType, channel);
                        hook.sendMessageFormat("Associating VRChat extended group audit event type %s (%s) with channel <#%s>", auditExType.title, auditExType.id, channel.getId()).setEphemeral(true).queue();
                    }
                    
                }); break;
                case "set-audit-secret-channel": this.handleInGuildAsync(event, true, hook -> {
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
                        hook.sendMessageFormat("Unassociating VRChat group audit log event type %s (%s) from any secret channels", auditType.title, auditType.id).setEphemeral(true).queue();
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
                        hook.sendMessageFormat("Associating VRChat group audit log event type %s (%s) with secret channel <#%s>", auditType.title, auditType.id, channel.getId()).setEphemeral(true).queue();
                    }
                    
                }); break;
                case "set-audit-ex-secret-channel": this.handleInGuildAsync(event, true, hook -> {
                    String auditExType0 = event.getOption("audit-ex-event-type").getAsString();
                    GroupAuditTypeEx auditExType = GroupAuditTypeEx.of(auditExType0);
                    if (auditExType == null)
                    {
                        hook.sendMessageFormat("%s isn't a valid extended audit event type", auditExType0).setEphemeral(true).queue();
                        return;
                    }
                    OptionMapping channel0 = event.getOption("discord-channel");

                    if (channel0 == null)
                    {
                        ScarletDiscordJDA.this.setAuditExChannel(auditExType, null);
                        hook.sendMessageFormat("Unassociating VRChat extended group audit event type %s (%s) from any secret channels", auditExType.title, auditExType.id).setEphemeral(true).queue();
                        return;
                    }
                    
                    GuildChannelUnion channel = channel0.getAsChannel();
                    if (!channel.getType().isMessage())
                    {
                        hook.sendMessageFormat("The channel %s doesn't support message sending", channel.getName()).setEphemeral(true).queue();
                    }
                    else
                    {
                        ScarletDiscordJDA.this.setAuditExChannel(auditExType, channel);
                        hook.sendMessageFormat("Associating VRChat extended group audit event type %s (%s) with secret channel <#%s>", auditExType.title, auditExType.id, channel.getId()).setEphemeral(true).queue();
                    }
                    
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
                case "scarlet-permission": this.handleInGuildAsync(event, true, hook -> {
                    int perPage = event.getOption("entries-per-page", 4, OptionMapping::getAsInt);
                    String scarletPermission0 = event.getOption("scarlet-permission", null, OptionMapping::getAsString);
                    Role role = event.getOption("discord-role", null, OptionMapping::getAsRole);

                    switch (event.getSubcommandName())
                    {
                    case "list": {
                        MessageEmbed[] embeds = Arrays
                            .stream(ScarletPermission.values())
                            .map(scarletPermission ->
                            {
                                UniqueStrings roleSfs = ScarletDiscordJDA.this.scarletPermission2roleSf.get(scarletPermission.id);
                                String roles = roleSfs == null || roleSfs.isEmpty() 
                                    ? "<none>"
                                    : roleSfs.stream().collect(Collectors.joining(">\n<@&", "<@&", ">"));
                                return new EmbedBuilder()
                                    .setTitle(scarletPermission.title)
                                    .addField("`"+scarletPermission.id+"`", roles, false)
                                    .build();
                            })
                            .toArray(MessageEmbed[]::new);
                        
                        ScarletDiscordJDA.this.new Pagination(event.getId(), embeds, perPage).queue(hook);
                        
                    } break;
                    case "add-to-role": {
                        ScarletPermission scarletPermission = ScarletPermission.of(scarletPermission0);
                        if (scarletPermission == null)
                        {
                            event.replyFormat("%s isn't a valid Scarlet permission", scarletPermission0).setEphemeral(true).queue();
                            return;
                        }
                        if (ScarletDiscordJDA.this.checkRoleHasPermission(scarletPermission, role))
                        {
                            event.replyFormat("That role already has that permission", scarletPermission0).setEphemeral(true).queue();
                            return;
                        }
                        
                        ScarletDiscordJDA.this.addPermissionRole(scarletPermission, role);
                        hook.sendMessageFormat("Adding Scarlet permission %s (%s) to %s", scarletPermission.title, scarletPermission.id, role.getAsMention()).setEphemeral(true).queue();
                        
                    } break;
                    case "delete-from-role": {
                        ScarletPermission scarletPermission = ScarletPermission.of(scarletPermission0);
                        if (scarletPermission == null)
                        {
                            event.replyFormat("%s isn't a valid Scarlet permission", scarletPermission0).setEphemeral(true).queue();
                            return;
                        }
                        if (!ScarletDiscordJDA.this.checkRoleHasPermission(scarletPermission, role))
                        {
                            event.replyFormat("That role already doesn't have that permission", scarletPermission0).setEphemeral(true).queue();
                            return;
                        }
                        
                        ScarletDiscordJDA.this.deletePermissionRole(scarletPermission, role);
                        hook.sendMessageFormat("Removing Scarlet permission %s (%s) from %s", scarletPermission.title, scarletPermission.id, role.getAsMention()).setEphemeral(true).queue();
                        
                    } break;
                    case "edit-role": {
                        LOG.error("edit-role: <@&"+role+">");
                        ScarletPermission[] scarletPermissions = ScarletPermission.values();
                        StringSelectMenu.Builder builder = StringSelectMenu
                            .create("select-role-scarlet-permissions:"+role.getId())
                            .setMinValues(0)
                            .setMaxValues(scarletPermissions.length)
                            .setPlaceholder("Select Scarlet permissions")
                            ;
                        
                        List<String> hadPerms = new ArrayList<>();
                        for (ScarletPermission scarletPermission : ScarletPermission.values())
                        {
                            builder.addOption(scarletPermission.title, scarletPermission.id);
                            if (ScarletDiscordJDA.this.checkRoleHasPermission(scarletPermission, role))
                            {
                                hadPerms.add(scarletPermission.id);
                            }
                        }
                        builder.setDefaultValues(hadPerms);
                        
                        hook.sendMessageComponents(ActionRow.of(builder.build())).setEphemeral(true).queue();
                    } break;
                    }
                    
                }); break;
                case "server-restart": this.handleInGuildAsync(event, false, hook -> {
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.CONFIG_SERVER_RESTART, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to restart the application server.").setEphemeral(true).queue();
                        return;
                    }
                    String targetVersion = event.getOption("target-version", OptionMapping::getAsString);
                    
                    switch (event.getSubcommandName())
                    {
                    case "restart-now": {
                        hook.sendMessage("Restarting...").setEphemeral(false).queue();
                        ScarletDiscordJDA.this.scarlet.restart();
                    } break;
                    case "update-now": {
                        
                        switch (ScarletDiscordJDA.this.scarlet.update(targetVersion))
                        {
                        case 1: {
                            hook.sendMessageFormat("Invalid version syntax `%s`", targetVersion).setEphemeral(false).queue();
                        } break;
                        case 2: {
                            hook.sendMessageFormat("Nonexistant version `%s`, manually set the contents of `scarlet.version` to this version if you want to actually do this", targetVersion).setEphemeral(false).queue();
                        } break;
                        case 3: {
                            hook.sendMessageFormat("Local IO failed for target version `%s`", targetVersion).setEphemeral(false).queue();
                        } break;
                        case 0: {
                            hook.sendMessage("Updating...").setEphemeral(false).queue();
                        } break;
                        case -1: {
                            hook.sendMessage("Shutdown already queued!").setEphemeral(false).queue();
                        } break;
                        }
                    } break;
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
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_SET_TAGS, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to select event tags.").setEphemeral(true).queue();
                        return;
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
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_BAN_USER, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to ban users.").setEphemeral(true).queue();
                        return;
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
                    
                    GroupMemberStatus status = ScarletDiscordJDA.this.scarlet.vrc.getGroupMembershipStatus(ScarletDiscordJDA.this.scarlet.vrc.groupId, vrcTargetId);
                    
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
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_UNBAN_USER, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to unban users.").setEphemeral(true).queue();
                        return;
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
                    
                    GroupMemberStatus status = ScarletDiscordJDA.this.scarlet.vrc.getGroupMembershipStatus(ScarletDiscordJDA.this.scarlet.vrc.groupId, vrcTargetId);
                    
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
                case "event-redact": this.handleInGuildAsync(event, true, hook -> {
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_REDACT, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to redact events.").setEphemeral(true).queue();
                        return;
                    }
                    
                    String auditEntryId = parts[1];
                    
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
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
                    ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId, auditEntryMeta);
                    this.updateAuxMessage(event.getChannel(), auditEntryMeta);
                }); break;
                case "event-unredact": this.handleInGuildAsync(event, true, hook -> {
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_UNREDACT, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to unredact events.").setEphemeral(true).queue();
                        return;
                    }
                    
                    String auditEntryId = parts[1];
                    
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
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
                    ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId, auditEntryMeta);
                    this.updateAuxMessage(event.getChannel(), auditEntryMeta);
                }); break;
                case "edit-desc": { // FIXME : can't defer a modal
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_SET_DESCRIPTION, event.getMember()))
                    {
                        event.reply("You do not have permission to set event descriptions.").setEphemeral(true).queue();
                        return;
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
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_USE_REPORT_LINK, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to use the event report link.").setEphemeral(true).queue();
                        return;
                    }
                    
                    String auditEntryId = parts[1];
                    
                    String targetUserId = null,
                           targetDisplayName = null,
                           actorUserId = null,
                           actorDisplayName = null,
                           metaDescription = null,
                           metaTags[] = null;
                    
                    String eventUserSnowflake = event.getUser().getId(),
                           eventUserId = ScarletDiscordJDA.this.scarlet.data.globalMetadata_getSnowflakeId(eventUserSnowflake);
                    
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
                    if (auditEntryMeta != null && auditEntryMeta.entry != null)
                    {
                        targetUserId = auditEntryMeta.entry.getTargetId();
                        actorUserId = auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorId : auditEntryMeta.entry.getActorId();
                        actorDisplayName = auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorDisplayName : auditEntryMeta.entry.getActorDisplayName();
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
                    
                    String location = auditEntryMeta == null ? null : auditEntryMeta.getData("location");
                    
                    ScarletVRChatReportTemplate.FormatParams params = ScarletDiscordJDA.this.scarlet.vrcReport.new FormatParams()
                        .group(ScarletDiscordJDA.this.scarlet.vrc, ScarletDiscordJDA.this.scarlet.vrc.groupId, ScarletDiscordJDA.this.scarlet.vrc.group)
                        .location(ScarletDiscordJDA.this.scarlet.vrc, location)
                        .actor(ScarletDiscordJDA.this.scarlet.vrc, actorUserId, actorDisplayName)
                        .target(ScarletDiscordJDA.this.scarlet.vrc, targetUserId, targetDisplayName)
                        .targetEx(
                            parts.length < 3 ? null : OffsetDateTime.ofInstant(Instant.ofEpochSecond(Long.parseUnsignedLong(parts[2])), ZoneOffset.UTC).format(ScarletVRChatReportTemplate.DTF),
                            auditEntryMeta == null ? null : auditEntryMeta.entry.getCreatedAt().format(ScarletVRChatReportTemplate.DTF)
                        )
                        .audit(
                            auditEntryId,
                            metaDescription,
                            metaTags == null ? null : Arrays.stream(metaTags).map(ScarletDiscordJDA.this.scarlet.moderationTags::getTagLabel).filter(Objects::nonNull).toArray(String[]::new)
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
                    
                    
                    hook.sendMessageEmbeds(new EmbedBuilder()
                            .setTitle("Help desk link")
                            .appendDescription("[Open new VRChat User Moderation Request](<")
                            .appendDescription(link)
                            .appendDescription(">)")
                        .build(), new EmbedBuilder()
                            .setTitle("Templated link")
                            .appendDescription("[Templated link](<")
                            .appendDescription(params.url(requestingEmail, requestingUserId, reportSubject, ScarletDiscordJDA.this.appendTemplateFooter.get()))
                            .appendDescription(">)")
                        .build())
                        .setContent(eventUserId != null ? null : "## WARNING\nThis link autofills the requesting user id of the **audit actor, not necessarily you**\nAssociate your Discord and VRChat ids with `/associate-ids`.\n\n")
                        .setEphemeral(true)
                        .queue();
                    
                }); break;
                case "submit-evidence": this.handleInGuildAsync(event, true, hook -> {
                    
                    String messageSnowflake = parts[1];
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.EVENT_SUBMIT_EVIDENCE, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to submit event attachments.").setEphemeral(true).queue();
                        return;
                    }
                    
                    if (!ScarletDiscordJDA.this.evidenceEnabled.get())
                    {
                        hook.sendMessage("This feature is not enabled.").setEphemeral(true).queue();
                        return;
                    }
                    
                    String evidenceRoot = ScarletDiscordJDA.this.evidenceRoot;
                    
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
                    
                    ScarletData.AuditEntryMetadata auditEntryMeta = ScarletDiscordJDA.this.scarlet.data.auditEntryMetadata(auditEntryId);
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
                        
                        ScarletData.UserMetadata auditEntryTargetUserMeta = ScarletDiscordJDA.this.scarlet.data.userMetadata(auditEntryTargetId);
                        if (auditEntryTargetUserMeta == null)
                            auditEntryTargetUserMeta = new ScarletData.UserMetadata();
                        
                        // TODO : async ?
                        
                        for (Message.Attachment attachment : attachments)
                        {
                            String fileName = attachment.getFileName(),
                                   attachmentUrl = attachment.getUrl(),
                                   attachmentProxyUrl = attachment.getProxyUrl();
                            
                            ScarletEvidence.FormatParams filePath = new ScarletEvidence.FormatParams()
                                .group(ScarletDiscordJDA.this.scarlet.vrc, auditEntryMeta.entry.getGroupId(), null)
                                .actor(ScarletDiscordJDA.this.scarlet.vrc,
                                       auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorId : auditEntryMeta.entry.getActorId(),
                                       auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorDisplayName : auditEntryMeta.entry.getActorDisplayName())
                                .target(ScarletDiscordJDA.this.scarlet.vrc, auditEntryMeta.entry.getTargetId(), null)
                                .file(fileName)
                                .audit(auditEntryId)
                                .index(auditEntryTargetUserMeta.getUserCaseEvidenceCount())
                                ;
                            File dest = filePath.nextFile(evidenceRoot, ScarletDiscordJDA.this.evidenceFilePathFormat.get());
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
                        
                        ScarletDiscordJDA.this.scarlet.data.userMetadata(auditEntryTargetId, auditEntryTargetUserMeta);
                    
                    }
                    
                }); break;
                case "import-watched-groups": this.handleInGuildAsync(event, true, hook -> {
                    
                    String messageSnowflake = parts[1];
                    
                    if (!ScarletDiscordJDA.this.checkMemberHasPermission(ScarletPermission.CONFIG_IMPORT_WATCHED_GROUPS, event.getMember()))
                    {
                        hook.sendMessage("You do not have permission to import watched groups.").setEphemeral(true).queue();
                        return;
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
                case "select-role-scarlet-permissions": {
                    String roleSf = parts[1];
                    Role role = event.getGuild().getRoleById(roleSf);
                    if (role == null)
                    {
                        event.replyFormat("<@&%s> isn't a valid role", roleSf).setEphemeral(true).queue();
                        return;
                    }
                    if (event.getValues().isEmpty())
                    {
                        event.replyFormat("Removing scarlet permissions for <@&%s>", roleSf).setEphemeral(true).queue();
                        for (ScarletPermission scarletPermission : ScarletPermission.values())
                        {
                            ScarletDiscordJDA.this.deletePermissionRole(scarletPermission, role);
                        }
                    }
                    else
                    {
                        List<ScarletPermission> scarletPermissions = event.getValues().stream().map(ScarletPermission::of).filter(Objects::nonNull).collect(Collectors.toList());
                        event.replyFormat("Setting scarlet permissions for <@&%s>:\n%s", roleSf, scarletPermissions.stream().map(ScarletPermission::title).collect(Collectors.joining(", "))).setEphemeral(true).queue();
                        for (ScarletPermission scarletPermission : scarletPermissions)
                        {
                            ScarletDiscordJDA.this.addPermissionRole(scarletPermission, role);
                        }
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
                String content = auditEntryMeta.entryRedacted ? "# **__REDACTED__**\n\n" : "";
                content = content + (actorMeta == null || actorMeta.userSnowflake == null ? ("Unknown Discord id for actor "+auditEntryMeta.entry.getActorDisplayName()) : ("<@"+actorMeta.userSnowflake+">"));
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
    
    boolean shouldRedact(String infoId, String requesterSf)
    {
        return ScarletDiscordJDA.this.scarlet.secretStaffList.isSecretStaffId(infoId)
           && !ScarletDiscordJDA.this.scarlet.secretStaffList.isSecretStaffId(ScarletDiscordJDA.this.scarlet.data.globalMetadata_getSnowflakeId(requesterSf));
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

    public void setAuditExChannel(GroupAuditTypeEx auditExType, GuildChannelUnion channel)
    {
        if (channel != null)
        {
            this.auditExType2channelSf.put(auditExType.id, channel.getId());
            LOG.info(String.format("Setting audit channel for %s (%s) to %s (%s)", auditExType.title, auditExType.id, channel.getName(), "<#"+channel.getId()+">"));
        }
        else
        {
            this.auditExType2channelSf.remove(auditExType.id);
            LOG.info(String.format("Unsetting audit channel for %s (%s)", auditExType.title, auditExType.id));
        }
        this.save();
    }

    public void setAuditSecretChannel(GroupAuditType auditType, GuildChannelUnion channel)
    {
        if (channel != null)
        {
            this.auditType2secretChannelSf.put(auditType.id, channel.getId());
            LOG.info(String.format("Setting audit secret channel for %s (%s) to %s (%s)", auditType.title, auditType.id, channel.getName(), "<#"+channel.getId()+">"));
        }
        else
        {
            this.auditType2secretChannelSf.remove(auditType.id);
            LOG.info(String.format("Unsetting audit secret channel for %s (%s)", auditType.title, auditType.id));
        }
        this.save();
    }

    public void setAuditExSecretChannel(GroupAuditTypeEx auditExType, GuildChannelUnion channel)
    {
        if (channel != null)
        {
            this.auditExType2secretChannelSf.put(auditExType.id, channel.getId());
            LOG.info(String.format("Setting audit secret channel for %s (%s) to %s (%s)", auditExType.title, auditExType.id, channel.getName(), "<#"+channel.getId()+">"));
        }
        else
        {
            this.auditExType2secretChannelSf.remove(auditExType.id);
            LOG.info(String.format("Unsetting audit secret channel for %s (%s)", auditExType.title, auditExType.id));
        }
        this.save();
    }

    public boolean deletePermissionRole(ScarletPermission scarletPermission, Role role)
    {
        if (scarletPermission == null || role == null)
            return false;
        UniqueStrings roleSfs = this.scarletPermission2roleSf.computeIfAbsent(scarletPermission.id, $ -> new UniqueStrings());
        if (roleSfs == null || !roleSfs.remove(role.getId()))
            return false;
        LOG.info(String.format("Removed role for Scarlet permission %s (%s): %s (%s)", scarletPermission.title, scarletPermission.id, role.getName(), "<@"+role.getId()+">"));
        this.save();
        return true;
    }

    public boolean addPermissionRole(ScarletPermission scarletPermission, Role role)
    {
        if (scarletPermission == null || role == null)
            return false;
        UniqueStrings roleSfs = this.scarletPermission2roleSf.computeIfAbsent(scarletPermission.id, $ -> new UniqueStrings());
        if (!roleSfs.add(role.getId()))
            return false;
        LOG.info(String.format("Adding role for Scarlet permission %s (%s): %s (%s)", scarletPermission.title, scarletPermission.id, role.getName(), "<@"+role.getId()+">"));
        this.save();
        return true;
    }

    public boolean checkMemberHasPermission(ScarletPermission scarletPermission, Member member)
    {
        {
            String userId = this.scarlet.data.globalMetadata_getSnowflakeId(member.getId());
            if (userId != null && this.scarlet.secretStaffList.isSecretStaffId(userId))
                return true;
        }
        return this.checkRolesHavePermission(scarletPermission, member.getRoles());
    }
    public boolean checkRolesHavePermission(ScarletPermission scarletPermission, List<Role> roles)
    {
        if (scarletPermission == null)
            return true;
        UniqueStrings roleSfs = this.scarletPermission2roleSf.get(scarletPermission.id);
        if (roleSfs == null || roleSfs.isEmpty())
            return false;
        return roles.stream().map(Role::getId).anyMatch(roleSfs::contains);
    }

    public boolean checkRoleHasPermission(ScarletPermission scarletPermission, Role role)
    {
        if (scarletPermission == null)
            return true;
        UniqueStrings roleSfs = this.scarletPermission2roleSf.get(scarletPermission.id);
        if (roleSfs == null || roleSfs.isEmpty())
            return false;
        return roleSfs.contains(role.getId());
    }

    @FunctionalInterface interface CondEmit { Message emit(String channelSf, Guild guild, GuildMessageChannel channel); }
    boolean condEmit(ScarletData.AuditEntryMetadata entryMeta, CondEmit condEmit)
    {
        if (this.jda == null)
            return false;
        boolean isSecretStaff = this.scarlet.secretStaffList.isSecretStaffId(entryMeta.entry.getActorId())
                || this.scarlet.secretStaffList.isSecretStaffId(entryMeta.auxActorId);
        String channelSf = isSecretStaff ? this.auditType2secretChannelSf.get(entryMeta.entry.getEventType())
                                         : this.auditType2channelSf.get(entryMeta.entry.getEventType());
        if (channelSf == null)
            return false;
        Guild guild = this.jda.getGuildById(this.guildSf);
        if (guild == null)
            return false;
        TextChannel channel = guild.getTextChannelById(channelSf);
        if (channel == null)
            return false;
        
        entryMeta.guildSnowflake = this.guildSf;
        entryMeta.channelSnowflake = channelSf;
        Message message = condEmit.emit(channelSf, guild, channel);
        entryMeta.messageSnowflake = message.getId();
        return true;
    }
    void condEmitEx(GroupAuditTypeEx auditExType, boolean log, boolean isSecretStaff, String location, CondEmit condEmit)
    {
        if (this.jda == null)
            return;
        if (auditExType == null)
            return;
        String channelSf = isSecretStaff ? this.auditExType2secretChannelSf.get(auditExType.id)
                                         : this.auditExType2channelSf.get(auditExType.id);
        String guildSf = this.guildSf;
        if (channelSf == null)
            return;
        String threadSf = null;
        if (location != null)
        {
            ScarletData.InstanceEmbedMessage instanceEmbedMessage = this.scarlet.data.liveInstancesMetadata_getLocationInstanceEmbedMessage(location, false);
            if (instanceEmbedMessage != null && Objects.equals(channelSf, instanceEmbedMessage.channelSnowflake))
            {
                guildSf = instanceEmbedMessage.guildSnowflake;
                threadSf = instanceEmbedMessage.threadSnowflake;
            }
        }
        Guild guild = this.jda.getGuildById(guildSf);
        if (guild == null)
            return;
        GuildMessageChannel channel = null;
        if (threadSf != null)
            channel = guild.getThreadChannelById(threadSf);
        if (channel == null)
            channel = guild.getTextChannelById(channelSf);
        else
            channelSf = threadSf;
        if (channel == null)
            return;
        
        Message message = condEmit.emit(channelSf, guild, channel);
        
        if (log)
        {
            LOG.info(String.format("%s (%s/%s/%s)", auditExType.id.replace('.', ' '), this.guildSf, channelSf, message.getId()));
        }
    }
    EmbedBuilder embed(ScarletData.AuditEntryMetadata entryMeta, boolean addTargetIdField)
    {
        EmbedBuilder embed = new EmbedBuilder()
            .setDescription(entryMeta.entry.getDescription())
            .setColor(GroupAuditType.color(this.auditType2color, entryMeta.entry.getEventType()))
            .setTimestamp(entryMeta.entry.getCreatedAt())
            .setAuthor(entryMeta.hasAuxActor() ? entryMeta.auxActorDisplayName : entryMeta.entry.getActorDisplayName(), "https://vrchat.com/home/user/"+(entryMeta.hasAuxActor() ? entryMeta.auxActorId : entryMeta.entry.getActorId()))
            .setFooter(ScarletDiscord.FOOTER_PREFIX+entryMeta.entry.getId())
        ;
        if (addTargetIdField)
            embed.addField("Target id", entryMeta.entry.getTargetId(), false);
        return embed;
    }
    @FunctionalInterface interface CondEmitEmbed { void emitEmbed(String channelSf, Guild guild, TextChannel channel, EmbedBuilder embed); }
    void condEmitEmbed(ScarletData.AuditEntryMetadata entryMeta, boolean addTargetIdField, String title, String url, Map<String, GroupAuditType.UpdateSubComponent> updates, Consumer<EmbedBuilder> condEmitEmbed)
    {
        EmbedBuilder embed = this.embed(entryMeta, addTargetIdField);
        if (condEmitEmbed != null)
            condEmitEmbed.accept(embed);
        this.condEmit(entryMeta, (channelSf, guild, channel) ->
        {
            if (title != null)
                embed.setTitle(title, url);
            if (updates != null && !updates.isEmpty())
                updates.forEach((key, sub) -> {
                    String oldValue = String.valueOf(sub.oldValue),
                           newValue = String.valueOf(sub.newValue);
                    if (oldValue.length() > 480) oldValue = oldValue.substring(0, 480).concat("...");
                    if (newValue.length() > 480) newValue = newValue.substring(0, 480).concat("...");
                    embed.addField("`"+key+"`", "old: `"+oldValue+"`\nnew :`"+newValue+"`", false);
                });
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
    public void emitUserModeration(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, User target, ScarletData.UserMetadata actorMeta, ScarletData.UserMetadata targetMeta, String history, String recent, ScarletData.AuditEntryMetadata parentEntryMeta, boolean reactiveKickFromBan)
    {
        this.condEmit(entryMeta, (channelSf, guild, channel) ->
        {
            String timeext = "";
            {
                OffsetDateTime targetJoined = this.scarlet.eventListener.getJoinedOrNull(target.getId());
                if (targetJoined != null)
                {
                    timeext = ":" + Long.toUnsignedString(targetJoined.toEpochSecond());
                }
            }
            this.userModerationLimiter.await();
            
            if (reactiveKickFromBan && this.bundleModerations_instanceKick2userBan.get())
            {
                ThreadChannel threadChannel = guild.getThreadChannelById(parentEntryMeta.threadSnowflake);
                if (threadChannel != null)
                {
                    entryMeta.parentEventId = parentEntryMeta.entry.getId();
                    entryMeta.auxActorId = parentEntryMeta.auxActorId;
                    entryMeta.auxActorDisplayName = parentEntryMeta.auxActorDisplayName;
                    entryMeta.guildSnowflake = parentEntryMeta.guildSnowflake;
                    entryMeta.channelSnowflake = parentEntryMeta.channelSnowflake;
                    entryMeta.threadSnowflake = parentEntryMeta.threadSnowflake;
                    Message message = threadChannel
                        .sendMessageEmbeds(this.embed(entryMeta, true)
                            .setTitle(MarkdownSanitizer.escape(target.getDisplayName()), "https://vrchat.com/home/user/"+target.getId())
                            .build())
                        .setMessageReference(parentEntryMeta.messageSnowflake)
                        .failOnInvalidReply(false)
                        .mentionRepliedUser(false)
                        .complete();
                    return message;
                }
            }
            
            EmbedBuilder embed = this.embed(entryMeta, true)
                .setTitle(MarkdownSanitizer.escape(target.getDisplayName()), "https://vrchat.com/home/user/"+target.getId())
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
            
            ThreadChannel threadChannel = ((TextChannel)channel)
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
                    ? (entryMeta.hasAuxActor()
                        ? ("Unknown Discord id for aux actor "+entryMeta.auxActorDisplayName+" (add with `/staff-list` or `/associate-ids`)")
                        : ("Unknown Discord id for actor "+entryMeta.entry.getActorDisplayName()+" (add with `/staff-list` or `/associate-ids`)"))
                    : (mention
                        ? ("<@"+actorMeta.userSnowflake+">")
                        : (entryMeta.hasAuxActor() ? entryMeta.auxActorDisplayName : entryMeta.entry.getActorDisplayName()));
            Message auxMessage = threadChannel.sendMessage(content)
                .addContent("\nUnclaimed")
                .addFiles(FileUpload.fromData(fileData, entryMeta.entry.getTargetId()+".json").asSpoiler())
                .addActionRow(Button.primary("edit-tags:"+entryMeta.entry.getId(), "Edit tags"),
                              Button.primary("edit-desc:"+entryMeta.entry.getId(), "Edit description"),
                              Button.primary("vrchat-report:"+entryMeta.entry.getId()+timeext, "Get report link"))
                .addActionRow(Button.danger("vrchat-user-ban:"+entryMeta.entry.getTargetId(), "Ban user"),
                              Button.success("vrchat-user-unban:"+entryMeta.entry.getTargetId(), "Unban user"),
                              Button.primary("event-redact:"+entryMeta.entry.getId(), "Redact event"),
                              Button.secondary("event-unredact:"+entryMeta.entry.getId(), "Unredact event"))
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
    String getLocationName(String location)
    {
        try
        {
            String worldId = location.substring(0, location.indexOf(':'));
            long within1day = System.currentTimeMillis() - 86400_000L;
            World world = this.scarlet.vrc.getWorld(worldId, within1day);
            if (world != null)
                return world.getName();
        }
        catch (Exception ex)
        {
            LOG.error("Exception getting world name", ex);
        }
        return null;
    }

    @Override
    public void emitInstanceCreate(Scarlet scarlet, AuditEntryMetadata entryMeta, String location)
    {
        String worldImageUrl = this.getLocationImage(location),
               worldName = this.getLocationName(location);
        Location locationModel = Location.of(location);
        
        scarlet.data.liveInstancesMetadata_setLocationAudit(location, entryMeta.entry.getId());
        this.condEmitEx(GroupAuditTypeEx.INSTANCE_MONITOR, false, false, null, (channelSf, guild, channel) ->
        {
            String title = MarkdownSanitizer.escape(worldName)+" ("+MarkdownSanitizer.escape(locationModel.name)+")";
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle(MarkdownSanitizer.escape(worldName)+" ("+MarkdownSanitizer.escape(locationModel.name)+")", "https://vrchat.com/home/launch?worldId="+location.replaceFirst(":", "&instanceId="))
                .setImage(worldImageUrl)
                .setColor(GroupAuditTypeEx.INSTANCE_MONITOR.color)
                .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
            Message message = channel
                .sendMessageEmbeds(embed.build())
                .complete();
            ThreadChannel threadChannel = ((TextChannel)channel).createThreadChannel(title, message.getId())
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
                .completeAfter(1_000L, TimeUnit.MILLISECONDS);
            Message auxMessage = threadChannel.sendMessage("### Instance log").completeAfter(1_000L, TimeUnit.MILLISECONDS);
            scarlet.data.liveInstancesMetadata_setLocationInstanceEmbedMessage(location, guild.getId(), channelSf, message.getId(), threadChannel.getId(), auxMessage.getId(), entryMeta.entry.getCreatedAt());
            return message;
        });
        this.condEmitEmbed(entryMeta, true, "Instance Open", "https://vrchat.com/home/launch?worldId="+location.replaceFirst(":", "&instanceId="), null, embed ->
        {
            this.emitAuxWh(entryMeta, embed.setImage(worldImageUrl)::build, IncomingWebhookClient::sendMessageEmbeds);
        });
    }

    @Override
    public void emitInstanceClose(Scarlet scarlet, AuditEntryMetadata entryMeta, String location)
    {
        String worldImageUrl = this.getLocationImage(location);
        
        String prevAuditEntryId = scarlet.data.liveInstancesMetadata_getLocationAudit(location, false);
        AuditEntryMetadata prevEntryMeta = prevAuditEntryId == null ? null : scarlet.data.auditEntryMetadata(prevAuditEntryId);
        ScarletData.InstanceEmbedMessage instanceEmbedMessage = scarlet.data.liveInstancesMetadata_getLocationInstanceEmbedMessage(location, false);
        if (instanceEmbedMessage != null)
        {
            instanceEmbedMessage.closedAt = entryMeta.entry.getCreatedAt();
        }
        
        this.condEmit(entryMeta, (channelSf, guild, channel) ->
        {
            boolean hasPrev = prevEntryMeta != null && prevEntryMeta.hasMessage();
            MessageCreateAction mca = channel
                .sendMessageEmbeds(this
                    .embed(entryMeta, true)
                    .setImage(worldImageUrl)
                    .setTitle("Instance Close", hasPrev ? prevEntryMeta.getMessageUrl() : null)
                    .build());
            if (hasPrev && Objects.equals(channelSf, prevEntryMeta.channelSnowflake))
                mca.mentionRepliedUser(false).setMessageReference(prevEntryMeta.messageSnowflake);
            return mca.complete();
        });
        this.emitAuxWh(entryMeta,
            () -> this.embed(entryMeta, true)
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
        this.condEmitEmbed(entryMeta, false, MarkdownSanitizer.escape(post.title), "https://vrchat.com/home/group/"+entryMeta.entry.getGroupId()+"/posts", null, embed ->
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
        this.condEmitEmbed(entryMeta, false, "Assigned Role to "+MarkdownSanitizer.escape(target.getDisplayName()), "https://vrchat.com/home/user/"+target.getId(), null, embed ->
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
        this.condEmitEmbed(entryMeta, false, "Unassigned Role from "+MarkdownSanitizer.escape(target.getDisplayName()), "https://vrchat.com/home/user/"+target.getId(), null, embed ->
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
        this.condEmitEmbed(entryMeta, false, "Created Role "+MarkdownSanitizer.escape(role.name), "https://vrchat.com/home/group/"+entryMeta.entry.getGroupId()+"/settings/roles/"+entryMeta.entry.getTargetId(), null, null);
    }

    @Override
    public void emitRoleDelete(Scarlet scarlet, AuditEntryMetadata entryMeta, GroupAuditType.RoleDeleteComponent role)
    {
        this.condEmitEmbed(entryMeta, false, "Deleted Role "+MarkdownSanitizer.escape(role.name), "https://vrchat.com/home/group/"+entryMeta.entry.getGroupId()+"/settings/roles/"+entryMeta.entry.getTargetId(), null, null);
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
        this.condEmitEmbed(entryMeta, false, "Updated Role "+MarkdownSanitizer.escape(roleName), "https://vrchat.com/home/group/"+entryMeta.entry.getGroupId()+"/settings/roles/"+entryMeta.entry.getTargetId(), updates, null);
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
        this.condEmitEmbed(entryMeta, true, GroupAuditType.title(entryMeta.entry.getEventType()), null, null, embed ->
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
                             String oldValue = String.valueOf(valueObject.get("old")),
                                    newValue = String.valueOf(valueObject.get("new"));
                             if (oldValue.length() > 480) oldValue = oldValue.substring(0, 480).concat("...");
                             if (newValue.length() > 480) newValue = newValue.substring(0, 480).concat("...");
                             valueString = new StringBuilder()
                                 .append("old: `").append(oldValue)
                                 .append("`\nnew: `").append(newValue).append("`")
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

    @Override
    public void emitExtendedInstanceInactive(Scarlet scarlet, String location, String auditEntryId, ScarletData.InstanceEmbedMessage instanceEmbedMessage)
    {
        this.condEmitEx(GroupAuditTypeEx.INSTANCE_INACTIVE, false, false, location, (channelSf, guild, channel) ->
        {
            return channel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Instance Inactive")
                    .setDescription("Location: `"+location+"`")
                    .setColor(GroupAuditTypeEx.INSTANCE_INACTIVE.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                    .build())
                .complete();
        });
    }

    @Override
    public void emitExtendedStaffJoin(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName)
    {
        this.condEmitEx(GroupAuditTypeEx.STAFF_JOIN, false, false, location, (channelSf, guild, channel) ->
        {
            return channel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle(MarkdownSanitizer.escape(displayName)+" joined a group instance", "https://vrchat.com/home/user/"+userId)
                    .setDescription("Location: `"+location+"`")
                    .setColor(GroupAuditTypeEx.STAFF_JOIN.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                    .build())
                .complete();
        });
    }

    @Override
    public void emitExtendedStaffLeave(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName)
    {
        this.condEmitEx(GroupAuditTypeEx.STAFF_LEAVE, false, false, location, (channelSf, guild, channel) ->
        {
            return channel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle(MarkdownSanitizer.escape(displayName)+" left a group instance", "https://vrchat.com/home/user/"+userId)
                    .setDescription("Location: `"+location+"`")
                    .setColor(GroupAuditTypeEx.STAFF_LEAVE.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                    .build())
                .complete();
        });
    }

    @Override
    public void emitExtendedUserJoin(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName)
    {
        this.condEmitEx(GroupAuditTypeEx.USER_JOIN, false, false, location, (channelSf, guild, channel) ->
        {
            return channel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle(MarkdownSanitizer.escape(displayName)+" joined a group instance", "https://vrchat.com/home/user/"+userId)
                    .setDescription("Location: `"+location+"`")
                    .setColor(GroupAuditTypeEx.USER_JOIN.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                    .build())
                .complete();
        });
    }

    @Override
    public void emitExtendedUserLeave(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName)
    {
        this.condEmitEx(GroupAuditTypeEx.USER_LEAVE, false, false, location, (channelSf, guild, channel) ->
        {
            return channel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle(MarkdownSanitizer.escape(displayName)+" left a group instance", "https://vrchat.com/home/user/"+userId)
                    .setDescription("Location: `"+location+"`")
                    .setColor(GroupAuditTypeEx.USER_LEAVE.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                    .build())
                .complete();
        });
    }

    @Override
    public void emitExtendedUserAvatar(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName, String avatarDisplayName, String[] potentialIds)
    {
        this.condEmitEx(GroupAuditTypeEx.USER_AVATAR, false, false, location, (channelSf, guild, channel) ->
        {
            return channel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle(MarkdownSanitizer.escape(displayName)+" switched avatars", "https://vrchat.com/home/user/"+userId)
                    .setDescription("Location: `"+location+"`")
                    .setColor(GroupAuditTypeEx.USER_AVATAR.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                    .addField(MarkdownSanitizer.escape(avatarDisplayName), Arrays.stream(potentialIds).limit(32).collect(Collectors.joining("`\n`", "`", "`")), false)
                    .build())
                .complete();
        });
    }

    @Override
    public void emitExtendedVtkInitiated(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName, String optActorId, String optActorDisplayName)
    {
        this.condEmitEx(GroupAuditTypeEx.VTK_START, true, false, location, (channelSf, guild, channel) ->
        {
            EmbedBuilder builder = new EmbedBuilder()
                    .setTitle(MarkdownSanitizer.escape(displayName)+" was targeted by a vote-to-kick", "https://vrchat.com/home/user/"+userId)
                    .addField("Location", "`"+location+"`", false)
                    .setColor(GroupAuditTypeEx.VTK_START.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
            if (optActorId != null)
            {
                builder.setAuthor(MarkdownSanitizer.escape(optActorDisplayName), "https://vrchat.com/home/user/"+optActorId);
            }
            return channel.sendMessageEmbeds(builder
                    .build())
                .complete();
        });
    }

    @Override
    public void emitExtendedInstanceMonitor(Scarlet scarlet, String location, InstanceEmbedMessage instanceEmbedMessage)
    {
        if (Objects.equals(location, scarlet.eventListener.clientLocation) && instanceEmbedMessage != null)
        {
            if (instanceEmbedMessage.guildSnowflake != null && instanceEmbedMessage.channelSnowflake != null && instanceEmbedMessage.messageSnowflake != null)
            {
                if (this.jda == null)
                    return;
                Guild guild = this.jda.getGuildById(instanceEmbedMessage.guildSnowflake);
                if (guild == null)
                    return;
                TextChannel channel = guild.getTextChannelById(instanceEmbedMessage.channelSnowflake);
                if (channel == null)
                    return;
                
                String worldImageUrl = this.getLocationImage(location),
                       worldName = this.getLocationName(location);
                Location locationModel = Location.of(location);
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(MarkdownSanitizer.escape(worldName)+" ("+MarkdownSanitizer.escape(locationModel.name)+")", "https://vrchat.com/home/launch?worldId="+location.replaceFirst(":", "&instanceId="))
                    .setImage(worldImageUrl)
                    .setColor(GroupAuditTypeEx.INSTANCE_MONITOR.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));

                StringBuilder sb = new StringBuilder();
                for (String userId : scarlet.eventListener.clientLocation_userIdsJoinOrder)
                {
                    String displayName = scarlet.eventListener.clientLocation_userId2userDisplayName.getOrDefault(userId, userId);
                    OffsetDateTime joinedAt = scarlet.eventListener.getJoinedOrNull(userId);
                    String joinedEpoch = joinedAt == null ? "0" : Long.toUnsignedString(joinedAt.toEpochSecond());
                    String avatarName = scarlet.eventListener.clientLocation_userDisplayName2avatarDisplayName.get(displayName);
                    sb.append(MarkdownUtil.maskedLink(MarkdownSanitizer.escape(displayName), "https://vrchat.com/home/user/"+userId))
                        .append("<t:").append(joinedEpoch).append(":D> (<t:").append(joinedEpoch).append(":R>): ")
                        .append(avatarName).append("\n");
                }
                
                MessageEmbed[] embeds = {embed.setDescription(sb).build()};// Stream.concat(Stream.of(embed.build()), MiscUtils.paginateOnLines(sb, 4000).stream().limit(9L).map($ -> new EmbedBuilder().setDescription($).build())).toArray(MessageEmbed[]::new);
                
                channel.editMessageEmbedsById(instanceEmbedMessage.messageSnowflake, embeds).complete();
            }
        }
    }

    @Override
    public void emitModSummary(Scarlet scarlet, OffsetDateTime endOfDay)
    {
        this.condEmitEx(GroupAuditTypeEx.MOD_SUMMARY, true, false, null, (channelSf, guild, channel) -> this.emitModSummary(scarlet, endOfDay, 24L, channel::sendMessageEmbeds));
    }
    <MCR extends MessageCreateRequest<MCR> & FluentRestAction<Message, MCR>> Message emitModSummary(Scarlet scarlet, OffsetDateTime endOfDay, long hoursBack, Function<MessageEmbed, MCR> mca)
    {
        OffsetDateTime startOfDay = endOfDay.minusHours(hoursBack);
        
        String epochStart = Long.toUnsignedString(startOfDay.toEpochSecond()),
               epochEnd = Long.toUnsignedString(endOfDay.toEpochSecond());
        
        List<GroupAuditLogEntry> entries = this.scarlet.vrc.auditQuery(startOfDay, endOfDay, null, "group.instance.kick,group.instance.warn,group.user.ban", null);
        
        if (entries == null)
        {
            return mca.apply(new EmbedBuilder()
                    .setTitle("Moderation Summary")
                    .setDescription("Spanning <t:"+epochStart+":f> through <t:"+epochEnd+":f>\nFailed to generate mod summary: could not query audit events")
                    .setColor(GroupAuditTypeEx.MOD_SUMMARY.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(endOfDay)
                    .build()).complete();
        }
        
        String[] exJoinEventIds = this.scarlet.data.customEvent_filter(GroupAuditTypeEx.STAFF_JOIN, startOfDay, endOfDay);

        
        Map<String, List<GroupAuditType>> map = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        Map<String, int[]> staffJoins = new HashMap<>();
        for (GroupAuditLogEntry entry : entries)
        {
            ScarletData.AuditEntryMetadata entryMeta = this.scarlet.data.auditEntryMetadata(entry.getId());
            String actorId = entryMeta != null && entryMeta.hasAuxActor() ? entryMeta.auxActorId : entry.getActorId();
            String displayName = entryMeta != null && entryMeta.hasAuxActor() ? entryMeta.auxActorDisplayName : entry.getActorDisplayName();
            map.computeIfAbsent(actorId, $ -> new ArrayList<>()).add(GroupAuditType.of(entry.getEventType()));
            if (displayName != null)
                displayNames.put(actorId, displayName);
        }
        if (exJoinEventIds != null)
        {
            for (String exJoinEventId : exJoinEventIds)
            {
                ScarletData.CustomEvent exJoinEvent = this.scarlet.data.customEvent(exJoinEventId);
                if (exJoinEvent != null)
                {
                    staffJoins.computeIfAbsent(exJoinEvent.actorId, $->new int[1])[0]++;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        {
            String[] exUJoinEventIds = this.scarlet.data.customEvent_filter(GroupAuditTypeEx.USER_JOIN, startOfDay, endOfDay);
            if (exUJoinEventIds != null)
                sb.append("### "+Integer.toUnsignedString(exUJoinEventIds.length)+" instance joins\n");
        }
        List<GroupAuditType> vtkEntries = map.get("vrc_admin");
        if (vtkEntries != null)
        {
            long vtks = vtkEntries.stream().filter(GroupAuditType.INSTANCE_KICK::equals).count();
            if (vtks > 0)
            {
                sb.append(String.format("### Successful Votes-to-Kick: %d\n", vtks));
            }
        }
        long twarns = 0L,
             tkicks = 0L,
             tbans = 0L,
             tjoins = 0L;
        sb.append("### Staff moderation (warns/kicks/bans/joins)\n");
        boolean onlyActivity = this.moderationSummary_onlyActivity.get();
        int skippedForInactivity = 0;
        for (String staffUserId : this.scarlet.staffList.getStaffIds())
        {
            List<GroupAuditType> staffEntries = map.get(staffUserId);
            int[] staffJoin = staffJoins.get(staffUserId);
            long warns = 0L,
                 kicks = 0L,
                 bans = 0L,
                 joins = 0L;
            if (staffEntries != null)
            {
                warns = staffEntries.stream().filter(GroupAuditType.INSTANCE_WARN::equals).count();
                kicks = staffEntries.stream().filter(GroupAuditType.INSTANCE_KICK::equals).count();
                bans = staffEntries.stream().filter(GroupAuditType.USER_BAN::equals).count();
            }
            if (staffJoin != null)
            {
                joins = staffJoin[0];
            }
            twarns += warns;
            tkicks += kicks;
            tbans += bans;
            tjoins += joins;
            if (onlyActivity && warns == 0L && kicks == 0L && bans == 0L && joins == 0L)
            {
                skippedForInactivity++;
            }
            else
            {
                String displayName = displayNames.get(staffUserId);
                if (displayName == null)
                {
                    User actor = this.scarlet.vrc.getUser(staffUserId);
                    displayName = actor != null ? actor.getDisplayName() : staffUserId;
                }
                sb.append(String.format("[%s](https://vrchat.com/home/user/%s): %d / %d / %d / %d\n", displayName, staffUserId, warns, kicks, bans, joins));
            }
        }
        if (onlyActivity)
        {
            sb.append(String.format("(skipped %d for inactivity)\n", skippedForInactivity));
        }
        sb.append(String.format("Staff totals: %d / %d / %d / %d\n", twarns, tkicks, tbans, tjoins));
        
        
        Message message = mca.apply(new EmbedBuilder()
            .setTitle("Moderation Summary")
            .setDescription("Spanning <t:"+epochStart+":f> through <t:"+epochEnd+":f>")
            .setColor(GroupAuditTypeEx.MOD_SUMMARY.color)
            .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
            .setTimestamp(endOfDay)
            .build()).complete();
        List<String> pages = MiscUtils.paginateOnLines(sb, 4096);
        for (String page : pages)
        {
            mca.apply(new EmbedBuilder().setDescription(page).build()).completeAfter(1000L, TimeUnit.MILLISECONDS);
        }
        return message;
    }

}
