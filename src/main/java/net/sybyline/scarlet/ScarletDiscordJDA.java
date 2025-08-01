package net.sybyline.scarlet;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.JFileChooser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.vrchatapi.JSON;
import io.github.vrchatapi.api.GroupsApi;
import io.github.vrchatapi.model.CreateInstanceRequest;
import io.github.vrchatapi.model.GroupAccessType;
import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.GroupMemberStatus;
import io.github.vrchatapi.model.GroupPermissions;
import io.github.vrchatapi.model.GroupRole;
import io.github.vrchatapi.model.InstanceContentSettings;
import io.github.vrchatapi.model.InstanceRegion;
import io.github.vrchatapi.model.InstanceType;
import io.github.vrchatapi.model.LimitedUserGroups;
import io.github.vrchatapi.model.Print;
import io.github.vrchatapi.model.User;
import io.github.vrchatapi.model.World;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IncomingWebhookClient;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.ICommandReference;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.requests.FluentRestAction;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.AbstractWebhookMessageAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import net.sybyline.scarlet.ScarletData.AuditEntryMetadata;
import net.sybyline.scarlet.ScarletData.InstanceEmbedMessage;
import net.sybyline.scarlet.ext.AvatarSearch;
import net.sybyline.scarlet.ext.VrcLaunch;
import net.sybyline.scarlet.server.discord.DCommands;
import net.sybyline.scarlet.server.discord.DInteractions;
import net.sybyline.scarlet.server.discord.DPerms;
import net.sybyline.scarlet.util.LRUMap;
import net.sybyline.scarlet.util.Location;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.Pacer;
import net.sybyline.scarlet.util.UniqueStrings;
import net.sybyline.scarlet.util.VRChatHelpDeskURLs;
import net.sybyline.scarlet.util.VersionedFile;

public class ScarletDiscordJDA implements ScarletDiscord
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/JDA");

    public ScarletDiscordJDA(Scarlet scarlet, File discordBotFile, File permsFile)
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
        this.pingOnOutstandingModeration_instanceWarn = scarlet.ui.settingBool("discord_ping_outstanding_instance_warn", "Discord: Ping on outstanding Instance Warn", false);
        this.pingOnOutstandingModeration_instanceKick = scarlet.ui.settingBool("discord_ping_outstanding_instance_kick", "Discord: Ping on outstanding Instance Kick", true);
        this.pingOnOutstandingModeration_memberRemove = scarlet.ui.settingBool("discord_ping_outstanding_member_remove", "Discord: Ping on outstanding Member Remove", false);
        this.pingOnOutstandingModeration_userBan = scarlet.ui.settingBool("discord_ping_outstanding_user_ban", "Discord: Ping on outstanding User Ban", true);
        this.pingOnOutstandingModeration_userUnban = scarlet.ui.settingBool("discord_ping_outstanding_user_unban", "Discord: Ping on outstanding User Unban", false);
        this.vrchatClient_launchOnInstanceCreate = scarlet.ui.settingBool("vrchat_client_launch_on_instance_create", "VRChat Client: Launch on Instance Create", false);
        this.evidenceEnabled = scarlet.ui.settingBool("evidence_enabled", "Evidence submission", false);
        this.selectEvidenceRoot = scarlet.ui.settingVoid("Evidence root folder", "Select", this::selectEvidenceRoot);
        this.evidenceFilePathFormat = scarlet.ui.settingString("evidence_file_path_format", "Evidence file path format", "");
        this.avatarSearchProvidersEnabled = scarlet.ui.settingBool("custom_avatar_search_providers_enabled", "Use custom avatar search providers", false);
        this.avatarSearchProviders = scarlet.ui.settingStringArr("custom_avatar_search_providers", "VRCX-compatible avatar search providers", AvatarSearch.URL_ROOTS.clone());
        this.resetAvatarSearchProviders = scarlet.ui.settingVoid("Reset avatar search providers to default", "Reset", this::resetAvatarSearchProviders);
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
        this.perms = new DPerms(permsFile);
        this.perms.registerOther(ScarletPermission.GROUPEX_BANS_MANAGE.id);
        this.perms.load();
        this.interactions = new DInteractions(false, scarlet.exec);
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
                                     pingOnOutstandingModeration_instanceWarn,
                                     pingOnOutstandingModeration_instanceKick,
                                     pingOnOutstandingModeration_memberRemove,
                                     pingOnOutstandingModeration_userBan,
                                     pingOnOutstandingModeration_userUnban,
                                     vrchatClient_launchOnInstanceCreate,
                                     evidenceEnabled,
                                     avatarSearchProvidersEnabled;
    final ScarletUI.Setting<Void> selectEvidenceRoot,
                                  resetAvatarSearchProviders;
    final ScarletUI.Setting<String[]> avatarSearchProviders;
    final DInteractions interactions;
    final DPerms perms;
    final Map<String, InstanceCreation> instanceCreation = new ConcurrentHashMap<>();
    final Map<String, Command.Choice> userSf2lastEdited_groupId = new ConcurrentHashMap<>();
    List<Command> currentCommands = new ArrayList<>();
    Map<String, ICommandReference> currentSlashCommandsMap = new HashMap<>();
    Map<String, UniqueStrings> scarletPermission2roleSf = new HashMap<>();
    Map<String, String> scarletAuxWh2webhookUrl = new ConcurrentHashMap<>();
    Map<String, IncomingWebhookClient> scarletAuxWh2incomingWebhookClient = new ConcurrentHashMap<>();
    Map<String, String> auditType2channelSf = new HashMap<>();
    Map<String, String> auditExType2channelSf = new HashMap<>();
    Map<String, UniqueStrings> auditType2scarletAuxWh = new ConcurrentHashMap<>();
    Map<String, String> auditType2secretChannelSf = new HashMap<>();
    Map<String, String> auditExType2secretChannelSf = new HashMap<>();
    Map<String, Integer> auditType2color = new HashMap<>();
    List<Action> queuedActions = Collections.synchronizedList(new LinkedList<>());

    ScarletDiscordCommands discordCommands = null;
    ScarletDiscordUI discordUI = null;

    void init()
    {
        this.perms.getGuildSnowflakesMutable().add(this.guildSf);
        this.discordCommands = new ScarletDiscordCommands(this);
        this.discordUI = new ScarletDiscordUI(this);
        {
            this.perms.registerSuggestion(DPerms.PermType.BUTTON_PRESS, this.interactions.getButtonClickIds());
            this.perms.registerSuggestion(DPerms.PermType.STRING_SELECT, this.interactions.getStringSelectIds());
            this.perms.registerSuggestion(DPerms.PermType.ENTITY_SELECT, this.interactions.getEntitySelectIds());
            this.perms.registerSuggestion(DPerms.PermType.MODAL_SUBMIT, this.interactions.getModalSubmitIds());
        }
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
        
        this.jda.retrieveCommands().queue($ ->
        {
            this.setCurrentCommands($);
            if (this.scarlet.settings.checkHasVersionChangedSinceLastRun())
            {
                this.updateCommandList();
            }
        });
        this.jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.playing(Scarlet.NAME+", a VRChat group moderation tool").withState(Scarlet.GITHUB_URL));
        this.audio.init();
        this.scarlet.exec.scheduleAtFixedRate(this::clearDeadPagination, 30_000L, 30_000L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void updateCommandList()
    {
        try
        {
            List<CommandData> datas = new ArrayList<>();
            datas.addAll(this.interactions.getCommandDataMutable());
            datas.add(DPerms.generateCommand("scarlet-discord-permissions"));
            DCommands.delta(
                this.currentCommands,
                datas,
                (     data) -> this.jda.upsertCommand(data).queue($ ->                      LOG.info("Upserted "+ $.getType()+" command "+  $.getName())),
                (cmd      ) -> this.jda.deleteCommandById(cmd.getId()).queue($ ->           LOG.info("Deleted "+cmd.getType()+" command "+cmd.getName())), 
                (cmd, data) -> this.jda.editCommandById(cmd.getId()).apply(data).queue($ -> LOG.info("Edited "+   $.getType()+" command "+  $.getName())),
                (cmd, data) -> {}
            );
            LOG.info("Queued commands update "+datas);
        }
        catch (Exception ex)
        {
            LOG.error("Exception queuing commands update", ex);
        }
    }

    @Override
    public long pollQueuedAction()
    {
        try
        {
            if (!this.queuedActions.isEmpty())
            {
                Action queuedAction = this.queuedActions.remove(0);
                switch (queuedAction.action)
                {
                case "ban":
                    return this.pollQueuedAction_ban(queuedAction);
                case "unban":
                    return this.pollQueuedAction_unban(queuedAction);
                }
            }
        }
        catch (Exception ex)
        {
            LOG.error("Exception polling queued action", ex);
        }
        return 10L;
    }
    long pollQueuedAction_ban(Action queuedAction)
    {
        long within1day = System.currentTimeMillis() - 86400_000L;
        User sc = this.scarlet.vrc.getUser(queuedAction.targetId, within1day);
        if (sc == null)
        {
            this.emitActionFailure(this.scarlet, queuedAction, "Failed to ban `%s`: user does not appear to exist", queuedAction.targetId);
            return 30L;
        }
        GroupMemberStatus status = this.scarlet.vrc.getGroupMembershipStatus(this.scarlet.vrc.groupId, queuedAction.targetId);
        if (status == GroupMemberStatus.BANNED)
            return 30L;
        if (this.scarlet.pendingModActions.addPending(GroupAuditType.USER_BAN, queuedAction.targetId, queuedAction.actorId) != null)
            return 30L;
        if (!this.scarlet.vrc.banFromGroup(queuedAction.targetId))
        {
            this.scarlet.pendingModActions.pollPending(GroupAuditType.USER_BAN, queuedAction.targetId);
            this.emitActionFailure(this.scarlet, queuedAction, "Failed to ban `%s`: request failed");
            return 30L;
        }
        return 60L; // success
    }
    long pollQueuedAction_unban(Action queuedAction)
    {
        long within1day = System.currentTimeMillis() - 86400_000L;
        User sc = this.scarlet.vrc.getUser(queuedAction.targetId, within1day);
        if (sc == null)
        {
            this.emitActionFailure(this.scarlet, queuedAction, "Failed to unban `%s`: user does not appear to exist", queuedAction.targetId);
            return 30L;
        }
        GroupMemberStatus status = this.scarlet.vrc.getGroupMembershipStatus(this.scarlet.vrc.groupId, queuedAction.targetId);
        if (status != GroupMemberStatus.BANNED)
            return 30L;
        if (this.scarlet.pendingModActions.addPending(GroupAuditType.USER_UNBAN, queuedAction.targetId, queuedAction.actorId) != null)
            return 30L;
        if (!this.scarlet.vrc.unbanFromGroup(queuedAction.targetId))
        {
            this.scarlet.pendingModActions.pollPending(GroupAuditType.USER_UNBAN, queuedAction.targetId);
            this.emitActionFailure(this.scarlet, queuedAction, "Failed to unban `%s`: request failed", queuedAction.targetId);
            return 30L;
        }
        return 60L; // success
    }

    String help_staffListAdd_mention = "`/staff-list add`";
    String help_scarletDiscordPermissions_mention = "`/scarlet-discord-permissions`";
    String linkedIdsInfo(ScarletData.AuditEntryMetadata auditEntryMeta)
    {
        String id = auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorId : auditEntryMeta.entry.getActorId(),
               display = auditEntryMeta.hasAuxActor() ? auditEntryMeta.auxActorDisplayName : auditEntryMeta.entry.getActorDisplayName(),
               kind = auditEntryMeta.hasAuxActor() ? "aux actor" : "actor";
        return "Unknown Discord id for "+kind+" "+display+"\n(Use "+this.help_staffListAdd_mention+" `vrchat-user`:*"+id+"* `discord-user`:*<your Discord user>* to accomplish this)";
    }
    String linkedIdsReply(UserSnowflake member)
    {
        return "You must have linked ids to perform this action.\n(Use "+this.help_staffListAdd_mention+" `vrchat-user`:*<your VRChat user ID>* `discord-user`:"+member.getAsMention()+" to accomplish this)";
    }
    String permsReply(UserSnowflake member)
    {
        return "You do not have permission to perform this action.\n(Have your admin use "+this.help_scarletDiscordPermissions_mention+" to assign permissions)";
    }
    static final Predicate<ICommandReference> IS_SLASH = $ -> !($ instanceof Command) || Command.Type.SLASH == ((Command)$).getType();
    void setCurrentCommands(List<Command> currentCommands)
    {
        this.perms.buildSuggestions(currentCommands);
        this.currentCommands = currentCommands;
        this.currentSlashCommandsMap = this.streamCommandTree().filter(IS_SLASH).collect(Collectors.toMap(ICommandReference::getFullCommandName, Function.identity()));
        this.help_staffListAdd_mention = this.getCommandTreeSlash("staff-list add").map(ICommandReference::getAsMention).orElse("`/staff-list add`");
    }
    public Stream<ICommandReference> streamCommandTree()
    {
        return this.currentCommands.stream().flatMap($ -> Stream.concat(Stream.concat(Stream.of($), $.getSubcommands().stream()), $.getSubcommandGroups().stream().flatMap($$ -> Stream.concat(Stream.of($$), $$.getSubcommands().stream()))));
    }
    public Optional<ICommandReference> getCommandTreeSlash(String fullCommandName)
    {
        return Optional.ofNullable(this.currentSlashCommandsMap.get(fullCommandName));
    }

    void setStaffMode()
    {
        LOG.warn("No Discord bot token: entering staff mode");
        this.scarlet.staffMode = true;
        this.scarlet.ui.jframe.setTitle(Scarlet.NAME+" (staff mode)");
    }

    void resetAvatarSearchProviders()
    {
        this.avatarSearchProviders.set(this.avatarSearchProviders.getDefault());
    }

    String[] getAvatarSearchProviders()
    {
        return this.avatarSearchProvidersEnabled.get()
            ? this.avatarSearchProviders.get()
            : AvatarSearch.URL_ROOTS;
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
                this.evidenceRoot = chooser.getSelectedFile().getAbsolutePath();
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
            try (InputStream fis = new FileInputStream(file))
            {
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(fis)))
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
        public List<Action> queuedActions = new ArrayList<>();
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
        this.queuedActions = Collections.synchronizedList(spec.queuedActions == null ? new LinkedList<>() : new LinkedList<>(spec.queuedActions));
    }
    public void save()
    {
        this.perms.save();
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
        spec.queuedActions = new ArrayList<>(this.queuedActions);
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
        this.interactions.clearDeadPagination();
    }

    class InstanceCreation
    {
        InstanceCreation(String ictoken, String worldId, String groupId)
        {
            this.worldId = worldId;
            this.groupId = groupId;
        }
        final String worldId;
        final String groupId;
        InstanceRegion region = InstanceRegion.US;
        List<String> roleIds = null;
        GroupAccessType groupAccessType = GroupAccessType.PUBLIC;
        OffsetDateTime closedAt = null;
        Boolean queueEnabled = Boolean.TRUE;
        Boolean hardClose = Boolean.FALSE;
        Boolean ageGate = Boolean.FALSE;
        Boolean playerPersistenceEnabled = null;
        Boolean instancePersistenceEnabled = null;
        String displayName = null;
        
        boolean contentSettings_drones = true;
        boolean contentSettings_emoji = true;
        boolean contentSettings_pedestals = true;
        boolean contentSettings_prints = true;
        boolean contentSettings_stickers = true;
        CreateInstanceRequest createRequest()
        {
            CreateInstanceRequest cir = new CreateInstanceRequest();
            cir.setWorldId(this.worldId);
            cir.setOwnerId(this.groupId);
            cir.setType(InstanceType.GROUP);
            cir.setRegion(this.region);
            if (this.groupAccessType == GroupAccessType.MEMBERS && this.roleIds != null) cir.setRoleIds(this.roleIds);
            cir.setGroupAccessType(this.groupAccessType);
            cir.setClosedAt(this.closedAt);
            cir.setQueueEnabled(this.queueEnabled);
            cir.setHardClose(this.hardClose);
            cir.setInstancePersistenceEnabled(this.instancePersistenceEnabled);
            cir.setDisplayName(this.displayName);
            cir.setAgeGate(this.ageGate);
            InstanceContentSettings contentSettings = new InstanceContentSettings();
            contentSettings.setDrones(this.contentSettings_drones);
            contentSettings.setEmoji(this.contentSettings_emoji);
            contentSettings.setPedestals(this.contentSettings_pedestals);
            contentSettings.setPrints(this.contentSettings_prints);
            contentSettings.setStickers(this.contentSettings_stickers);
            cir.setContentSettings(contentSettings);
            return cir;
        }
        JsonObject createRequestEx()
        {
            JsonObject cir = JSON.getGson().toJsonTree(this.createRequest()).getAsJsonObject();
            if (this.playerPersistenceEnabled != null) cir.addProperty("playerPersistenceEnabled", this.playerPersistenceEnabled);
            return cir;
        }
    }

    class JDAEvents extends ListenerAdapter
    {
        JDAEvents()
        {
        }

        void interactionPerms(Interaction interaction)
        {
            if (interaction instanceof IReplyCallback)
            {
                IReplyCallback replyCallback = (IReplyCallback)interaction;
                String reply = ScarletDiscordJDA.this.permsReply(interaction.getMember());
                (replyCallback.isAcknowledged()
                    ? replyCallback.getHook().sendMessage(reply)
                    : replyCallback.reply(reply)
                ).queue();
            }
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
            if (!ScarletDiscordJDA.this.perms.check(event))
            {
                this.interactionPerms(event);
                return;
            }
            if (ScarletDiscordJDA.this.interactions.handle(event))
                return;
        }

        @Override
        public void onUserContextInteraction(UserContextInteractionEvent event)
        {
            if (!ScarletDiscordJDA.this.perms.check(event))
            {
                this.interactionPerms(event);
                return;
            }
            if (ScarletDiscordJDA.this.interactions.handle(event))
                return;
        }

        @Override
        public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event)
        {
            if (!ScarletDiscordJDA.this.perms.check(event))
            {
                this.interactionPerms(event);
                return;
            }
            if (ScarletDiscordJDA.this.interactions.handle(event))
                return;
            if ("scarlet-discord-permissions".equals(event.getName())) try
            {
                ScarletDiscordJDA.this.perms.internal_autocomplete(event);
                return;
            }
            catch (Exception ex)
            {
                this.interactionError(event, ex);
            }
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
        {
            if (!ScarletDiscordJDA.this.perms.check(event))
            {
                this.interactionPerms(event);
                return;
            }
            if (ScarletDiscordJDA.this.interactions.handle(event))
                return;
            if ("scarlet-discord-permissions".equals(event.getName())) try
            {
                ScarletDiscordJDA.this.perms.internal_handle(event);
                return;
            }
            catch (Exception ex)
            {
                this.interactionError(event, ex);
            }
        }

        @Override
        public void onButtonInteraction(ButtonInteractionEvent event)
        {
            if (!ScarletDiscordJDA.this.perms.check(event))
            {
                this.interactionPerms(event);
                return;
            }
            if (ScarletDiscordJDA.this.interactions.handle(event))
                return;
        }

        @Override
        public void onModalInteraction(ModalInteractionEvent event)
        {
            if (!ScarletDiscordJDA.this.perms.check(event))
            {
                this.interactionPerms(event);
                return;
            }
            if (ScarletDiscordJDA.this.interactions.handle(event))
                return;
        }

        @Override
        public void onStringSelectInteraction(StringSelectInteractionEvent event)
        {
            if (!ScarletDiscordJDA.this.perms.check(event))
            {
                this.interactionPerms(event);
                return;
            }
            if (ScarletDiscordJDA.this.interactions.handle(event))
                return;
        }

        @Override
        public void onEntitySelectInteraction(EntitySelectInteractionEvent event)
        {
            if (!ScarletDiscordJDA.this.perms.check(event))
            {
                this.interactionPerms(event);
                return;
            }
            if (ScarletDiscordJDA.this.interactions.handle(event))
                return;
        }

    }
    
    boolean shouldRedact(String infoId, String requesterSf)
    {
        return ScarletDiscordJDA.this.scarlet.secretStaffList.isSecretStaffId(infoId)
           && !ScarletDiscordJDA.this.scarlet.secretStaffList.isSecretStaffId(ScarletDiscordJDA.this.scarlet.data.globalMetadata_getSnowflakeId(requesterSf));
    }

    public void setAuditChannel(GroupAuditType auditType, Channel channel)
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

    public void setAuditExChannel(GroupAuditTypeEx auditExType, Channel channel)
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

    public void setAuditSecretChannel(GroupAuditType auditType, Channel channel)
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

    public void setAuditExSecretChannel(GroupAuditTypeEx auditExType, Channel channel)
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

    public boolean checkMemberHasVRChatPermission(GroupPermissions vrchatPermission, Member member)
    {
        if (vrchatPermission == null)
            return true;
        String userId = this.scarlet.data.globalMetadata_getSnowflakeId(member.getId());
        return this.scarlet.vrc.checkUserHasVRChatPermission(vrchatPermission, userId);
    }

    public boolean checkMemberHasScarletPermission(ScarletPermission scarletPermission, Member member, boolean fallback)
    {
        if (scarletPermission == null)
            return true;
        return this.perms.check(member, scarletPermission.id, fallback);
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
            LOG.info(String.format("%s (%s/%s/%s)", auditExType.id.replace('.', ' '), this.guildSf, channelSf, message == null ? null : message.getId()));
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
        if (title != null)
            embed.setTitle(title, url);
        if (condEmitEmbed != null)
            condEmitEmbed.accept(embed);
        this.condEmit(entryMeta, (channelSf, guild, channel) ->
        {
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
    @Override
    public void emitUserModeration(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, User actor, User target, ScarletData.UserMetadata actorMeta, ScarletData.UserMetadata targetMeta, String history, String recent, ScarletData.AuditEntryMetadata parentEntryMeta, boolean reactiveKickFromBan)
    {
        this.condEmit(entryMeta, (channelSf, guild, channel) ->
        {
            {
                OffsetDateTime targetJoined = this.scarlet.eventListener.getJoinedOrNull(target.getId());
                if (targetJoined != null)
                {
                    entryMeta.setAuxData("targetJoined", targetJoined.toEpochSecond());
                }
            }
            this.userModerationLimiter.await();
            
            if (reactiveKickFromBan && this.bundleModerations_instanceKick2userBan.get() && parentEntryMeta.threadSnowflake != null)
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
            
            List<LimitedUserGroups> lugs = this.scarlet.vrc.snapshot(entryMeta);
            
            if (target != null)
            {
                String epochJoined = Long.toUnsignedString(target.getDateJoined().toEpochDay() * 86400L);
                embed.addField("Account age", "<t:"+epochJoined+":D> (<t:"+epochJoined+":R>)", false);
                embed.addField("Age verification", "`"+target.getAgeVerificationStatus()+"`", false);
                embed.addField("Pronouns", "`"+MarkdownSanitizer.escape(target.getPronouns())+"`", false);
                embed.addField("Status description", "`"+MarkdownSanitizer.escape(target.getStatusDescription())+"`", false);
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
            
            this.scarlet.exec.execute(() -> this.emitUserModeration_thread(scarlet, entryMeta, actor.getId(), message));
            
            return message;
        });
        if ("group.instance.kick".equals(entryMeta.entry.getEventType()))
        {
            this.tryEmitExtendedSuggestedModeration(scarlet, target);
        }
    }
    ThreadChannel emitUserModeration_thread(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, String actorId, Message message)
    {
        ThreadChannel threadChannel = message.getStartedThread();
        if (threadChannel == null)
        {
            if (entryMeta.threadSnowflake != null)
            {
                threadChannel = message
                    .getGuild()
                    .getThreadChannelById(entryMeta.threadSnowflake);
            }
            if (threadChannel == null)
            {
                threadChannel = message
                    .getChannel()
                    .asTextChannel()
                    .createThreadChannel(entryMeta.entry.getDescription(), message.getId())
                    .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR)
                    .completeAfter(1500L, TimeUnit.MILLISECONDS);
            }
        }
        
        entryMeta.threadSnowflake = threadChannel.getId();
        scarlet.data.auditEntryMetadata(entryMeta.entry.getId(), entryMeta);
        
        ThreadChannel threadChannel0 = threadChannel;
        this.scarlet.exec.execute(() -> this.emitUserModeration_auxMessage(scarlet, entryMeta, actorId, threadChannel0));
        
        return threadChannel;
    }
    Message emitUserModeration_auxMessage(Scarlet scarlet, ScarletData.AuditEntryMetadata entryMeta, String actorId, ThreadChannel threadChannel)
    {
        if (entryMeta.auxMessageSnowflake != null)
        {
            Message auxMessage = threadChannel.retrieveMessageById(entryMeta.auxMessageSnowflake).complete();
            if (auxMessage != null)
                return auxMessage;
        }
        
        boolean mention;
        String contentExtra = "\nUnclaimed";
        switch (GroupAuditType.of(entryMeta.entry.getEventType()))
        {
        case INSTANCE_WARN: mention = this.pingOnModeration_instanceWarn.get(); break;
        case INSTANCE_KICK: mention = this.pingOnModeration_instanceKick.get(); break;
        case MEMBER_REMOVE: mention = this.pingOnModeration_memberRemove.get(); break;
        case USER_BAN:      mention = this.pingOnModeration_userBan     .get();
        ScarletPendingModActions.BanInfo pendingBan = this.scarlet.pendingModActions.pollBanInfo(entryMeta.entry.getTargetId());
        if (pendingBan != null)
        {
            contentExtra = "";
            if (pendingBan.tags != null && pendingBan.tags.length > 0)
            {
                entryMeta.entryTags.addAll(pendingBan.tags);
                contentExtra = contentExtra + "\n### Tags:\n" + Stream.of(pendingBan.tags).map(this.scarlet.moderationTags::getTagLabel).collect(Collectors.joining(", "));
            }
            if (pendingBan.description != null && !pendingBan.description.trim().isEmpty())
            {
                entryMeta.entryDescription = pendingBan.description;
                contentExtra = contentExtra + "\n### Description:\n" + pendingBan.description;
            }
        }
        break;
        case USER_UNBAN:    mention = this.pingOnModeration_userUnban   .get(); break;
        default:            mention = false;                                    break;
        }
        
        String timeext = entryMeta.getAuxData("targetJoined", "", $->':'+Long.toUnsignedString($.getAsLong()));
        
        ScarletData.UserMetadata actorMeta = scarlet.data.userMetadata(actorId);
        String content = actorMeta == null || actorMeta.userSnowflake == null
                ? ScarletDiscordJDA.this.linkedIdsInfo(entryMeta)
                : (mention
                    ? ("<@"+actorMeta.userSnowflake+">")
                    : (entryMeta.hasAuxActor() ? entryMeta.auxActorDisplayName : entryMeta.entry.getActorDisplayName()));
        Message auxMessage = threadChannel.sendMessage(content)
            .addContent(contentExtra)
            .addActionRow(Button.primary("edit-tags:"+entryMeta.entry.getId(), "Edit tags"),
                          Button.primary("edit-desc:"+entryMeta.entry.getId(), "Edit description"),
                          Button.primary("vrchat-report:"+entryMeta.entry.getId()+timeext, "Get report link"))
            .addActionRow(Button.secondary("view-snapshot-user:"+entryMeta.entry.getId(), "Snapshot: user"),
                          Button.secondary("view-snapshot-user-groups:"+entryMeta.entry.getId(), "Snapshot: user groups"),
                          Button.secondary("view-snapshot-user-represented-group:"+entryMeta.entry.getId(), "Snapshot: user represented group"))
            .addActionRow(Button.danger("vrchat-user-ban:"+entryMeta.entry.getTargetId(), "Ban user"),
                          Button.success("vrchat-user-unban:"+entryMeta.entry.getTargetId(), "Unban user"),
//                          Button.secondary("vrchat-user-edit-manager-notes:"+entryMeta.entry.getTargetId(), "Edit manager notes"),
                          Button.primary("event-redact:"+entryMeta.entry.getId(), "Redact event"),
                          Button.secondary("event-unredact:"+entryMeta.entry.getId(), "Unredact event"))
            .completeAfter(1500L, TimeUnit.MILLISECONDS);
        
        entryMeta.auxMessageSnowflake = auxMessage.getId();
        scarlet.data.auditEntryMetadata(entryMeta.entry.getId(), entryMeta);
        
        return auxMessage;
    }

    String getLocationImage(String location)
    {
        try
        {
            int colon = location.indexOf(':');
            String worldId = location.substring(0, colon >= 0 ? colon : location.length());
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
            int colon = location.indexOf(':');
            String worldId = location.substring(0, colon >= 0 ? colon : location.length());
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
        if (this.vrchatClient_launchOnInstanceCreate.get())
        {
            try
            {
                VrcLaunch.launch(this.scarlet.vrc.currentUserId, location);
            }
            catch (Exception ex)
            {
                LOG.error("Exception auto-launching VRChat", ex);
            }
        }
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
                    .addField("Location", "`"+location+"`", false)
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
                    .setDescription(String.format("### [%s](https://vrchat.com/home/user/%s) joined [a group instance](https://vrchat.com/home/launch?worldId=%s)",
                            MarkdownSanitizer.escape(displayName), userId, location.replace(":", "&instanceId=")))
//                    .setTitle(MarkdownSanitizer.escape(displayName)+" joined a group instance", "https://vrchat.com/home/user/"+userId)
//                    .addField("Location", "`"+location+"`", false)
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
                    .setDescription(String.format("### [%s](https://vrchat.com/home/user/%s) left [a group instance](https://vrchat.com/home/launch?worldId=%s)",
                            MarkdownSanitizer.escape(displayName), userId, location.replace(":", "&instanceId=")))
//                    .setTitle(MarkdownSanitizer.escape(displayName)+" left a group instance", "https://vrchat.com/home/user/"+userId)
//                    .addField("Location", "`"+location+"`", false)
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
                    .setDescription(String.format("### [%s](https://vrchat.com/home/user/%s) joined [a group instance](https://vrchat.com/home/launch?worldId=%s)",
                            MarkdownSanitizer.escape(displayName), userId, location.replace(":", "&instanceId=")))
                    .addField("User ID", "`"+userId+"`", false)
//                    .setTitle(MarkdownSanitizer.escape(displayName)+" joined a group instance", "https://vrchat.com/home/user/"+userId)
//                    .addField("Location", "`"+location+"`", false)
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
                    .setDescription(String.format("### [%s](https://vrchat.com/home/user/%s) left [a group instance](https://vrchat.com/home/launch?worldId=%s)",
                            MarkdownSanitizer.escape(displayName), userId, location.replace(":", "&instanceId=")))
                    .addField("User ID", "`"+userId+"`", false)
//                    .setTitle(MarkdownSanitizer.escape(displayName)+" left a group instance", "https://vrchat.com/home/user/"+userId)
//                    .addField("Location", "`"+location+"`", false)
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
            EmbedBuilder builder = new EmbedBuilder()
                    .setTitle(MarkdownSanitizer.escape(displayName)+" switched avatars", "https://vrchat.com/home/user/"+userId)
                    .addField("User ID", "`"+userId+"`", false)
                    .addField("Location", "`"+location+"`", false)
                    .addField("Avatar Name", MarkdownSanitizer.escape(avatarDisplayName), false)
                    .setDescription(Arrays.stream(potentialIds).limit(32).collect(Collectors.joining("`\n`", "Potential ids:\n`", "`")))
                    .setColor(GroupAuditTypeEx.USER_AVATAR.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
            ;
            User user = this.scarlet.vrc.getUser(userId);
            String aviThumbnail = user == null ? null : user.getCurrentAvatarThumbnailImageUrl();
            if (aviThumbnail != null && !aviThumbnail.isEmpty())
                builder.setThumbnail(aviThumbnail);
            VersionedFile versionedFile = this.avatarName2Bundle.get(avatarDisplayName);
            if (versionedFile != null)
                builder.addField("Bundle ID", "`"+versionedFile.id+"`", false);
            Message message = channel
                .sendMessageEmbeds(builder.build())
                .addActionRow(Button.secondary("view-potential-avatar-matches", "View avatars"))
                .complete();
            if (versionedFile != null)
                return message;
            List<AviSwitch> list = this.avatarName2Switches.get(avatarDisplayName);
            if (list == null)
                this.avatarName2Switches.put(avatarDisplayName, list = new ArrayList<>());
            list.add(new AviSwitch(message, builder));
            return message;
        });
    }

    @Override
    public void emitExtendedVtkInitiated(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName, String optActorId, String optActorDisplayName)
    {
        this.condEmitEx(GroupAuditTypeEx.VTK_START, true, false, location, (channelSf, guild, channel) ->
        {
            EmbedBuilder builder = new EmbedBuilder()
                    .setDescription(String.format("### [%s](https://vrchat.com/home/user/%s) was targeted by a vote-to-kick in [a group instance](https://vrchat.com/home/launch?worldId=%s)",
                            MarkdownSanitizer.escape(displayName), userId, location.replace(":", "&instanceId=")))
                    .addField("Target User ID", "`"+userId+"`", false)
//                    .setTitle(MarkdownSanitizer.escape(displayName)+" was targeted by a vote-to-kick", "https://vrchat.com/home/user/"+userId)
//                    .addField("Location", "`"+location+"`", false)
                    .setColor(GroupAuditTypeEx.VTK_START.color)
                    .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                    .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
            if (optActorId != null)
            {
                builder.setAuthor(MarkdownSanitizer.escape(optActorDisplayName), "https://vrchat.com/home/user/"+optActorId);
                builder.addField("Initiating User ID", "`"+optActorId+"`", false);
            }
            return channel.sendMessageEmbeds(builder
                    .build())
                .addActionRow(Button.danger("vrchat-user-ban:"+userId, "Ban user"),
                              Button.success("vrchat-user-unban:"+userId, "Unban user"))
                .complete();
        });
    }

    @Override
    public void emitExtendedUserSpawnPedestal(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName, String contentType, String contentId)
    {
        this.condEmitEx(GroupAuditTypeEx.SPAWN_PEDESTAL, false, false, location, (channelSf, guild, channel) ->
        {
            VRChatHelpDeskURLs.ModerationReportAccountContentType act = null;
            String contentTypeLowerCase = contentType.toLowerCase();
            switch (contentTypeLowerCase)
            {
            case "image": act = VRChatHelpDeskURLs.ModerationReportAccountContentType.GALLERY;
            case "emoji": act = VRChatHelpDeskURLs.ModerationReportAccountContentType.EMOJI;
            case "sticker": act = VRChatHelpDeskURLs.ModerationReportAccountContentType.STICKERS;
            case "palette": act = VRChatHelpDeskURLs.ModerationReportAccountContentType.OTHER;
            }
            return channel.sendMessageEmbeds(new EmbedBuilder()
                .setTitle(MarkdownSanitizer.escape(displayName)+("aeiou".indexOf(Character.toLowerCase(contentType.charAt(0)))<0?" spawned a ":" spawned an ")+contentTypeLowerCase+" pedestal", "https://vrchat.com/home/user/"+userId)
                .addField("User ID", "`"+userId+"`", false)
                .addField("Location", "`"+location+"`", false)
                .addField(contentType+" ID", "`"+contentId+"`", false)
                .addField("Report "+contentTypeLowerCase, MarkdownUtil.maskedLink("link", VRChatHelpDeskURLs.newModerationRequest_account(this.requestingEmail.get(), act, userId, contentType, contentId)), false)
                .setImage(!contentId.startsWith("file_") ? null : ("https://api.vrchat.cloud/api/1/file/"+contentId+"/1/file"))
                .setColor(GroupAuditTypeEx.SPAWN_PEDESTAL.color)
                .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .build())
            .complete();
        });
    }

    @Override
    public void emitExtendedUserSpawnSticker(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName, String stickerId)
    {
        this.condEmitEx(GroupAuditTypeEx.SPAWN_STICKER, false, false, location, (channelSf, guild, channel) ->
        {
            String stickerFileId = null;
            try
            {
                stickerFileId = this.scarlet.vrc.getStickerFileId(userId, stickerId);
            }
            catch (Exception ex)
            {
            }
            String desc = stickerId;
            if (stickerFileId != null)
                desc = desc + " (" + stickerFileId + ")";
            return channel.sendMessageEmbeds(new EmbedBuilder()
                .setTitle(MarkdownSanitizer.escape(displayName)+" spawned a sticker", "https://vrchat.com/home/user/"+userId)
                .addField("User ID", "`"+userId+"`", false)
                .addField("Location", "`"+location+"`", false)
                .addField("Sticker ID", "`"+stickerId+"`", false)
                .addField("File ID", "`"+stickerFileId+"`", false)
                .addField("Report sticker", MarkdownUtil.maskedLink("link", VRChatHelpDeskURLs.newModerationRequest_account_stickers(this.requestingEmail.get(), userId, "Sticker", desc)), false)
                .setImage(stickerFileId == null ? null : ("https://api.vrchat.cloud/api/1/file/"+stickerFileId+"/1/file"))
                .setColor(GroupAuditTypeEx.SPAWN_STICKER.color)
                .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .build())
            .complete();
        });
    }

    @Override
    public void emitExtendedUserSpawnPrint(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName, String printId, Print print)
    {
        this.condEmitEx(GroupAuditTypeEx.SPAWN_PRINT, false, false, location, (channelSf, guild, channel) ->
        {
            
            String fileId = print.getFiles().getFileId(),
                   image = print.getFiles().getImage();
            return channel.sendMessageEmbeds(new EmbedBuilder()
                .setTitle(MarkdownSanitizer.escape(displayName)+" spawned a print", "https://vrchat.com/home/user/"+userId)
                .addField("User ID", "`"+userId+"`", false)
                .addField("Location", "`"+location+"`", false)
                .addField("Print ID", "`"+printId+"`", false)
                .addField("File ID", "`"+fileId+"`", false)
                .addField("Report print", MarkdownUtil.maskedLink("link", VRChatHelpDeskURLs.newModerationRequest_account_prints(this.requestingEmail.get(), userId, "Print", printId)), false)
                .setImage(image != null ? image : ("https://api.vrchat.cloud/api/1/file/"+fileId+"/1/file"))
                .setColor(GroupAuditTypeEx.SPAWN_PRINT.color)
                .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .build())
            .complete();
        });
    }

    private final LRUMap<String, VersionedFile> avatarName2Bundle = LRUMap.of();
    private final LRUMap<String, List<AviSwitch>> avatarName2Switches = LRUMap.of();
    class AviSwitch
    {
        AviSwitch(Message message, EmbedBuilder builder)
        {
            this.message = message;
            this.builder = builder;
        }
        final Message message;
        final EmbedBuilder builder;
        void update(VersionedFile versionedFile)
        {
            try
            {
                this.message.editMessageEmbeds(this.builder.addField("Bundle ID", "`"+versionedFile.id+"`", false).build()).completeAfter(1_000L, TimeUnit.MILLISECONDS);
            }
            catch (RuntimeException rex)
            {
            }
        }
    }

    @Override
    public void tryEmitExtendedAvatarBundles(Scarlet scarlet, LocalDateTime timestamp, String location, String name, VersionedFile file)
    {
        this.avatarName2Bundle.put(name, file);
        List<AviSwitch> aviSwitchs = this.avatarName2Switches.remove(name);
        if (aviSwitchs != null)
        {
            this.scarlet.exec.execute(() -> aviSwitchs.forEach($ -> $.update(file)));
        }
    }

//    @Override
//    public void emitExtendedUserSpawnEmoji(Scarlet scarlet, LocalDateTime timestamp, String location, String userId, String displayName, String emojiId)
//    {
//        this.condEmitEx(GroupAuditTypeEx.SPAWN_EMOJI, false, false, location, (channelSf, guild, channel) ->
//        {
//            return channel.sendMessageEmbeds(new EmbedBuilder()
//                .setTitle(MarkdownSanitizer.escape(displayName)+" spawned an emoji", "https://vrchat.com/home/user/"+userId)
//                .addField("User ID", "`"+userId+"`", false)
//                .addField("Location", "`"+location+"`", false)
//                .addField("Emoji ID", "`"+emojiId+"`", false)
//                .setImage(!emojiId.startsWith("file_") ? null : ("https://api.vrchat.cloud/api/1/file/"+emojiId+"/1/file"))
//                .setColor(GroupAuditTypeEx.SPAWN_EMOJI.color)
//                .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
//                .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
//                .build())
//            .complete();
//        });
//    }

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
                
                if (sb.length() > 4096)
                {
                    channel.editMessageEmbedsById(instanceEmbedMessage.messageSnowflake, embed.setDescription("See `players.txt` for connected users").build()).setAttachments(AttachedFile.fromData(sb.toString().getBytes(StandardCharsets.UTF_8), "players.txt"));
                    return;
                }
                
                MessageEmbed[] embeds = {embed.setDescription(sb).build()};// Stream.concat(Stream.of(embed.build()), MiscUtils.paginateOnLines(sb, 4000).stream().limit(9L).map($ -> new EmbedBuilder().setDescription($).build())).toArray(MessageEmbed[]::new);
                
                channel.editMessageEmbedsById(instanceEmbedMessage.messageSnowflake, embeds).setAttachments().complete();
            }
        }
    }

    @Override
    public void emitModSummary(Scarlet scarlet, OffsetDateTime endOfDay)
    {
        this.condEmitEx(GroupAuditTypeEx.MOD_SUMMARY, true, false, null, (channelSf, guild, channel) -> this.emitModSummary(scarlet, endOfDay, this.scarlet.settings.heuristicPeriodDays.getOrSupply() * 24L, channel::sendMessageEmbeds));

    }
    <MCR extends MessageCreateRequest<MCR> & FluentRestAction<Message, MCR>> Message emitModSummary(Scarlet scarlet, OffsetDateTime endOfDay, long hoursBack, Function<MessageEmbed, MCR> mca)
    {
        OffsetDateTime startOfDay = endOfDay.minusHours(hoursBack);
        
        String epochStart = Long.toUnsignedString(startOfDay.toEpochSecond()),
               epochEnd = Long.toUnsignedString(endOfDay.toEpochSecond());
        
        List<GroupAuditLogEntry> entries = this.scarlet.vrc.auditQuery(startOfDay, endOfDay, null, "group.instance.kick,group.instance.warn,group.user.ban,group.invite.create", null);
        
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
            List<GroupAuditLogEntry> entries2 = this.scarlet.vrc.auditQuery(startOfDay, endOfDay, null, "group.member.join,group.member.leave,group.member.remove", null);
            if (entries2 != null)
            {
                long gjoins = 0L,
                     gleaves = 0L,
                     gremoves = 0L,
                     ginvites = 0L;
                for (GroupAuditLogEntry entry : entries2) switch (entry.getEventType())
                {
                case "group.member.join": {
                    gjoins++;
                } break;
                case "group.member.leave": {
                    gleaves++;
                } break;
                case "group.member.remove": {
                    gremoves++;
                } break;
                case "group.invite.create": {
                    ginvites++;
                } break;
                }
                sb.append("### "+Long.toUnsignedString(gjoins)+"/"+Long.toUnsignedString(gleaves)+"/"+Long.toUnsignedString(gremoves)+"/"+Long.toUnsignedString(ginvites)+" group joins/leaves/kicks/invites\n");
            }
        }
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
             tinvites = 0L,
             tjoins = 0L;
        sb.append("### Staff moderation (warns/kicks/bans/invites/joins)\n");
        boolean onlyActivity = this.moderationSummary_onlyActivity.get();
        int skippedForInactivity = 0;
        for (String staffUserId : this.scarlet.staffList.getStaffIds())
        {
            List<GroupAuditType> staffEntries = map.get(staffUserId);
            int[] staffJoin = staffJoins.get(staffUserId);
            long warns = 0L,
                 kicks = 0L,
                 bans = 0L,
                 invites = 0L,
                 joins = 0L;
            if (staffEntries != null)
            {
                warns = staffEntries.stream().filter(GroupAuditType.INSTANCE_WARN::equals).count();
                kicks = staffEntries.stream().filter(GroupAuditType.INSTANCE_KICK::equals).count();
                bans = staffEntries.stream().filter(GroupAuditType.USER_BAN::equals).count();
                invites = staffEntries.stream().filter(GroupAuditType.INVITE_CREATE::equals).count();
            }
            if (staffJoin != null)
            {
                joins = staffJoin[0];
            }
            twarns += warns;
            tkicks += kicks;
            tbans += bans;
            tinvites += invites;
            tjoins += joins;
            if (onlyActivity && warns == 0L && kicks == 0L && bans == 0L && invites == 0L && joins == 0L)
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
                sb.append(String.format("[%s](https://vrchat.com/home/user/%s): %d / %d / %d / %d / %d\n", displayName, staffUserId, warns, kicks, bans, invites, joins));
            }
        }
        if (onlyActivity)
        {
            sb.append(String.format("(skipped %d for inactivity)\n", skippedForInactivity));
        }
        sb.append(String.format("Staff totals: %d / %d / %d / %d / %d\n", twarns, tkicks, tbans, tinvites, tjoins));
        
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

    @Override
    public void emitOutstandingMod(Scarlet scarlet, OffsetDateTime endOfDay)
    {
        this.condEmitEx(GroupAuditTypeEx.OUTSTANDING_MODERATION, true, false, null, (channelSf, guild, channel) ->  this.emitOutstandingMod(scarlet, endOfDay, this.scarlet.settings.outstandingPeriodDays.getOrSupply() * 24L, channel::sendMessageEmbeds));
    }
    <MCR extends MessageCreateRequest<MCR> & FluentRestAction<Message, MCR>> Message emitOutstandingMod(Scarlet scarlet, OffsetDateTime endOfDay, long hoursBack, Function<MessageEmbed, MCR> mca)
    {
        List<String> list = new ArrayList<>();
        if (this.pingOnOutstandingModeration_instanceWarn.get()) list.add("group.instance.warn");
        if (this.pingOnOutstandingModeration_instanceKick.get()) list.add("group.instance.kick");
        if (this.pingOnOutstandingModeration_memberRemove.get()) list.add("group.member.remove");
        if (this.pingOnOutstandingModeration_userBan.get()) list.add("group.user.ban");
        if (this.pingOnOutstandingModeration_userUnban.get()) list.add("group.user.unban");
        if (list.isEmpty())
            return null;
        OffsetDateTime startOfDay = endOfDay.minusHours(hoursBack);
        
        String epochStart = Long.toUnsignedString(startOfDay.toEpochSecond()),
               epochEnd = Long.toUnsignedString(endOfDay.toEpochSecond());
        
        List<GroupAuditLogEntry> entries = this.scarlet.vrc.auditQuery(startOfDay, endOfDay, null, list.stream().collect(Collectors.joining(",")), null);
        
        if (entries == null)
            return null;
        
        Message message = mca.apply(new EmbedBuilder()
            .setTitle("Outstanding Moderation")
            .setDescription("Spanning <t:"+epochStart+":f> through <t:"+epochEnd+":f>")
            .setColor(GroupAuditTypeEx.OUTSTANDING_MODERATION.color)
            .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
            .setTimestamp(endOfDay)
            .build()).complete();
        
        this.scarlet.exec.submit(() ->
        {
            Map<String, List<ScarletData.AuditEntryMetadata>> map = new HashMap<>();
            Map<String, String> displayNames = new HashMap<>();
            for (GroupAuditLogEntry entry : entries)
            {
                if (this.scarlet.secretStaffList.isSecretStaffId(entry.getActorId()))
                    continue;
                ScarletData.AuditEntryMetadata entryMeta = this.scarlet.data.auditEntryMetadata(entry.getId());
                if (entryMeta.hasAuxActor() && this.scarlet.secretStaffList.isSecretStaffId(entryMeta.auxActorId))
                    continue;
                String actorId = entryMeta != null && entryMeta.hasAuxActor() ? entryMeta.auxActorId : entry.getActorId();
                String displayName = entryMeta != null && entryMeta.hasAuxActor() ? entryMeta.auxActorDisplayName : entry.getActorDisplayName();
                if (displayName != null)
                    displayNames.put(actorId, displayName);
                if (entryMeta != null && (!entryMeta.hasTags() || entryMeta.hasDescription()))
                    map.computeIfAbsent(actorId, $ -> new ArrayList<>()).add(entryMeta);
            }
            
            MessageChannel messageChannel = message.getChannel().getType().isThread()
                ? message.getChannel().asThreadChannel()
                : message.isEphemeral()
                    ? message.getChannel()
                    : message.createThreadChannel("Outstanding Moderation as of "+endOfDay).completeAfter(1000L, TimeUnit.MILLISECONDS);
            
            map.forEach((actorId, metas) ->
            {
                StringBuilder sb = new StringBuilder();
                String actorName = displayNames.getOrDefault(actorId, actorId);
                String actorSf = this.scarlet.data.userMetadata_getSnowflake(actorId);
                if (actorSf == null)
                {
                    sb.append("Unknown Discord id for [").append(actorName).append("](<https://vrchat.com/home/user/").append(actorId).append(">)\n");
                }
                else
                {
                    sb.append("<@").append(actorSf).append(">:\n");
                }
                for (ScarletData.AuditEntryMetadata entryMeta : metas)
                {
                    sb.append(entryMeta.hasTags() ? "[Description](<" : entryMeta.hasDescription() ? "[Tags](<" : "[Tags, Description](<")
                    .append(entryMeta.hasSomeUrl() ? entryMeta.getSomeUrl() : entryMeta.getChannelUrl())
                    .append(">): ")
                    .append(entryMeta.entry.getDescription())
                    .append("\n")
                    ;
                }
                List<String> pages = MiscUtils.paginateOnLines(sb, 2000);
                for (String page : pages)
                {
                    messageChannel.sendMessage(page).completeAfter(1500L, TimeUnit.MILLISECONDS);
                }
            });
        });
        
        return message;
    }

    @Override
    public void tryEmitExtendedSuggestedModeration(Scarlet scarlet, User target)
    {
        int periodDays = this.scarlet.settings.heuristicPeriodDays.getOrSupply(),
            kickThreshold = this.scarlet.settings.heuristicKickCount.getOrSupply();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC),
                       before = now.minusDays(periodDays);
        List<GroupAuditLogEntry> entries = this.scarlet.vrc.auditQuery(before, now, null, "group.instance.kick", target.getId());
        if (entries == null)
            return;
        int kicks = 0;
        for (GroupAuditLogEntry entry : entries)
        {
            ScarletData.AuditEntryMetadata entryMeta = this.scarlet.data.auditEntryMetadata(entry.getId());
            if (entryMeta == null || !entryMeta.entryRedacted)
            {
                kicks++;
            }
        }
        if (kicks < kickThreshold)
            return;
        int kicks0 = kicks;
        this.condEmitEx(GroupAuditTypeEx.SUGGESTED_MODERATION, false, false, null, (channelSf, guild, channel) ->
        {
            return channel.sendMessageEmbeds(new EmbedBuilder()
                .setTitle(MarkdownSanitizer.escape(target.getDisplayName()), "https://vrchat.com/home/user/"+target.getId())
                .setDescription(String.format("%s has been kicked from group instances %s time%s in the last %s hours", MarkdownSanitizer.escape(target.getDisplayName()), kicks0, kicks0==1?"":"s", kickThreshold*24))
                .addField("User ID", "`"+target.getId()+"`", false)
                .setColor(GroupAuditTypeEx.SUGGESTED_MODERATION.color)
                .setFooter(ScarletDiscord.FOOTER_PREFIX+"Extended event")
                .setTimestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .build())
            .addActionRow(Button.danger("vrchat-user-ban:"+target.getId(), "Ban user"),
                          Button.success("vrchat-user-unban:"+target.getId(), "Unban user"))
            .complete();
        });
    }

    @Override
    public void emitActionFailure(Scarlet scarlet, Action action, String format, Object... args)
    {
        this.condEmitEx(GroupAuditTypeEx.ACTION_FAIL, true, this.scarlet.secretStaffList.isSecretStaffId(action.actorId), action.location, (channelSf, guild, channel) -> channel.sendMessageFormat(format, args).complete());
    }

}
