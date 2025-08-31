package net.sybyline.scarlet;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.vrchatapi.api.GroupsApi;
import io.github.vrchatapi.api.InstancesApi;
import io.github.vrchatapi.api.UsersApi;
import io.github.vrchatapi.api.WorldsApi;
import io.github.vrchatapi.model.Avatar;
import io.github.vrchatapi.model.Group;
import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.GroupLimitedMember;
import io.github.vrchatapi.model.GroupMemberStatus;
import io.github.vrchatapi.model.GroupPermissions;
import io.github.vrchatapi.model.GroupRole;
import io.github.vrchatapi.model.Instance;
import io.github.vrchatapi.model.LimitedUserSearch;
import io.github.vrchatapi.model.LimitedWorld;
import io.github.vrchatapi.model.User;
import io.github.vrchatapi.model.World;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.sybyline.scarlet.ext.AvatarSearch;
import net.sybyline.scarlet.log.ScarletLogger;
import net.sybyline.scarlet.server.discord.DInteractions;
import net.sybyline.scarlet.server.discord.DInteractions.DefaultPerms;
import net.sybyline.scarlet.server.discord.DInteractions.Desc;
import net.sybyline.scarlet.server.discord.DInteractions.MsgCmd;
import net.sybyline.scarlet.server.discord.DInteractions.Required;
import net.sybyline.scarlet.server.discord.DInteractions.SlashCmd;
import net.sybyline.scarlet.server.discord.DInteractions.SlashOpt;
import net.sybyline.scarlet.server.discord.DInteractions.SlashOption;
import net.sybyline.scarlet.server.discord.DInteractions.SlashOptionStrings;
import net.sybyline.scarlet.util.Func.F1;
import net.sybyline.scarlet.util.Gifs;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.Location;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.UniqueStrings;
import net.sybyline.scarlet.util.VRChatHelpDeskURLs;
import net.sybyline.scarlet.util.VrcIds;

public class ScarletDiscordCommands
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/JDA/CMDS");

    public ScarletDiscordCommands(ScarletDiscordJDA discord)
    {
        this.discord = discord;
        discord.interactions.register(this);
    }

    final ScarletDiscordJDA discord;

    // Commands

    public final SlashOption<io.github.vrchatapi.model.User> _vrchatUser = SlashOption.ofString("vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, null, this::_vrchatUser, true, this::_vrchatUser);
    io.github.vrchatapi.model.User _vrchatUser(String id) { return this.discord.scarlet.vrc.getUser(id); }
    void _vrchatUser(CommandAutoCompleteInteractionEvent event) {
        String value = VrcIds.resolveUserId(event.getFocusedOption().getValue());
        if (value.isEmpty())
        {
            event.replyChoices().queue();
            return;
        }
        if (VrcIds.id_user.matcher(value).matches())
        {
            User user = this.discord.scarlet.vrc.getUser(value, Long.MIN_VALUE);
            if (user != null)
            {
                event.replyChoice(user.getId()+": "+user.getDisplayName(), user.getId()).queue();
                return;
            }
        }
        List<LimitedUserSearch> lusers = this.discord.scarlet.vrc.searchUsers(value, 25, 0);
        if (lusers == null)
        {
            event.replyChoices().queue();
            return;
        }
        event.replyChoices(lusers.stream().map($ -> new Command.Choice($.getId()+": "+$.getDisplayName(), $.getId())).limit(25L).toArray(Command.Choice[]::new)).queue();
    }
    public final SlashOption<io.github.vrchatapi.model.World> _vrchatWorld = SlashOption.ofString("vrchat-world", "The VRChat world id (wrld_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, null, this::_vrchatWorld, true, this::_vrchatWorld);
    io.github.vrchatapi.model.World _vrchatWorld(String id) { return this.discord.scarlet.vrc.getWorld(id); }
    void _vrchatWorld(CommandAutoCompleteInteractionEvent event) {
        String value = VrcIds.resolveWorldId(event.getFocusedOption().getValue());
        if (value.isEmpty())
        {
            event.replyChoices().queue();
            return;
        }
        if (VrcIds.id_world.matcher(value).matches())
        {
            World world = this.discord.scarlet.vrc.getWorld(value, Long.MIN_VALUE);
            if (world != null)
            {
                event.replyChoice(world.getId()+": "+world.getName()+" by "+world.getAuthorName(), world.getId()).queue();
                return;
            }
        }
        List<LimitedWorld> lworlds = this.discord.scarlet.vrc.searchWorlds(value, 25, 0);
        if (lworlds == null)
        {
            event.replyChoices().queue();
            return;
        }
        event.replyChoices(lworlds.stream().map($ -> new Command.Choice($.getId()+": "+$.getName()+" by "+$.getAuthorName(), $.getId())).limit(25L).toArray(Command.Choice[]::new)).queue();
    }
    public final SlashOption<io.github.vrchatapi.model.GroupRole> _vrchatRoleOpt = SlashOption.ofString("vrchat-role", "The VRChat role id (grol_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", false, null, this::_vrchatRole, true, this::_vrchatRole);
    public final SlashOption<io.github.vrchatapi.model.GroupRole> _vrchatRole = SlashOption.ofString("vrchat-role", "The VRChat role id (grol_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, null, this::_vrchatRole, true, this::_vrchatRole);
    io.github.vrchatapi.model.GroupRole _vrchatRole(String id) { return this.discord.scarlet.vrc.group.getRoles().stream().filter($ -> Objects.equals(id, $.getId())).findFirst().orElse(null); }
    void _vrchatRole(CommandAutoCompleteInteractionEvent event) {
        DInteractions.SlashOptionsChoicesUnsanitized.autocomplete(event, ScarletDiscordCommands.this.discord.scarlet.vrc.group.getRoles().stream().map($ -> new Command.Choice($.getName(), $.getId())).toArray(Command.Choice[]::new), false);
    }
    public final SlashOption<GroupAuditType> _auditEventType = SlashOption.ofEnum("audit-event-type", "The VRChat Group Audit Log event type", true, GroupAuditType.class);
    public final SlashOption<GroupAuditTypeEx> _auditExEventType = SlashOption.ofEnum("audit-ex-event-type", "The extended audit event type", true, GroupAuditTypeEx.class);
    public final SlashOption<Member> _discordUser = SlashOption.ofMember("discord-user", "The Discord user", true);
    public final SlashOption<Role> _discordRole = SlashOption.ofRole("discord-role", "The Discord role", true);
    public final SlashOption<Channel> _textChannel = SlashOption.ofChannel("discord-text-channel", "The Discord text channel to use, or omit to remove entry", false, ChannelType.TEXT);
    public final SlashOption<Channel> _voiceChannel = SlashOption.ofChannel("discord-voice-channel", "The Discord voice channel to use, or omit to remove entry", false, ChannelType.VOICE);
    public final SlashOption<Integer> _daysBack = SlashOption.ofInt("days-back", "The number of days into the past to search for events", false, 4).with($->$.setRequiredRange(1L, 1000L));
    public final SlashOption<Integer> _hoursBack = SlashOption.ofInt("hours-back", "The number of hours into the past to search for events", false, 24).with($->$.setRequiredRange(1L, 24_000L));
    public final SlashOption<Integer> _pagination = SlashOption.ofInt("entries-per-page", "The number of entries to show per page", false, 4).with($->$.setRequiredRange(1L, 10L));
    public final SlashOption<Boolean> _tagImmediately = SlashOption.ofBool("tag-immediately", "Whether to submit tags and description now", false, false);
    public final SlashOption<Message.Attachment> _importedFile = SlashOption.ofAttachment("import-file", "Accepts: JSON, CSV", true);

    public final SlashOption<ScarletWatchedEntities.WatchedEntity.Type> _entityType = SlashOption.ofEnum("entity-type", "The type of watched entity", true, ScarletWatchedEntities.WatchedEntity.Type.UNKNOWN);
    public final SlashOption<String> _entityTags = SlashOption.ofString("entity-tags", "A list of tags, separated by one of ',', ';', '/'", false, null);
    public final SlashOption<String> _entityTag = SlashOption.ofString("entity-tag", "A tag", true, null);
    public final SlashOption<Integer> _entityPriority = SlashOption.ofInt("entity-priority", "The priority of this entity", false, 0).with($->$.setRequiredRange(-100L, 100L));
    public final SlashOption<String> _entityMessage = SlashOption.ofString("entity-message", "A message to announce with TTS", false, null);
    public final SlashOption<Boolean> _entityCritical = SlashOption.ofBool("entity-critical", "The critical status of the entity", true, null);
    public final SlashOption<Boolean> _entitySilent = SlashOption.ofBool("entity-silent", "The silent status of the entity", true, null);

    // vrchat-search

    @SlashCmd("vrchat-search")
    @Desc("Searches for VRChat things")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class VrchatSearch
    {
        public final SlashOption<String> _searchQuery = SlashOption.ofString("search-query", "The search query", true, null);
        @SlashCmd("world")
        @Desc("Searches for worlds")
        public void world(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("search-query") String searchQuery, @SlashOpt("entries-per-page") int entriesPerPage) throws Exception
        {
            MessageEmbed[] embeds = new WorldsApi(ScarletDiscordCommands.this.discord.scarlet.vrc.client)
                .searchWorlds(null, null, null, null, 50, null, 0, searchQuery, null, null, null, null, null, null, null)
                .stream()
                .map($ -> new EmbedBuilder()
                    .setAuthor($.getAuthorName(), "https://vrchat.com/home/user/"+$.getAuthorId(), null)
                    .setTitle($.getName(), "https://vrchat.com/home/world/"+$.getId())
                    .setThumbnail($.getThumbnailImageUrl() == null || $.getThumbnailImageUrl().isEmpty() ? null : $.getThumbnailImageUrl())
                    .addField("Report world", MarkdownUtil.maskedLink("link", VRChatHelpDeskURLs.newModerationRequest_content_world(ScarletDiscordCommands.this.discord.requestingEmail.get(), $.getId(), "World", null)), false)
                    .build())
                .toArray(MessageEmbed[]::new);
            ScarletDiscordCommands.this.discord.interactions.new Pagination(event.getId(), embeds, entriesPerPage).queue(hook);
        }
        @SlashCmd("user")
        @Desc("Searches for users")
        public void user(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("search-query") String searchQuery, @SlashOpt("entries-per-page") int entriesPerPage) throws Exception
        {
            MessageEmbed[] embeds = new UsersApi(ScarletDiscordCommands.this.discord.scarlet.vrc.client)
                .searchUsers(searchQuery, null, 50, 0)
                .stream()
                .map($ -> new EmbedBuilder()
//                    .setAuthor($.getAuthorName(), "https://vrchat.com/home/user/"+$.getAuthorId(), null)
                    .setTitle($.getDisplayName(), "https://vrchat.com/home/user/"+$.getId())
                    .setThumbnail($.getProfilePicOverride() == null || $.getProfilePicOverride().isEmpty() ? ($.getCurrentAvatarImageUrl() == null || $.getCurrentAvatarImageUrl().isEmpty() ? null : $.getCurrentAvatarImageUrl()) : $.getProfilePicOverride())
                    .addField("Report account", MarkdownUtil.maskedLink("link", VRChatHelpDeskURLs.newModerationRequest_account(ScarletDiscordCommands.this.discord.requestingEmail.get(), null, $.getId(), "Account", null)), false)
                    .build())
                .toArray(MessageEmbed[]::new);
            ScarletDiscordCommands.this.discord.interactions.new Pagination(event.getId(), embeds, entriesPerPage).queue(hook);
        }
        @SlashCmd("group")
        @Desc("Searches for groups")
        public void group(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("search-query") String searchQuery, @SlashOpt("entries-per-page") int entriesPerPage) throws Exception
        {
            MessageEmbed[] embeds = new GroupsApi(ScarletDiscordCommands.this.discord.scarlet.vrc.client)
                .searchGroups(searchQuery, 0, 50)
                .stream()
                .map($ -> new EmbedBuilder()
                    .setAuthor($.getShortCode()+"."+$.getDiscriminator(), null, $.getIconUrl())
                    .setTitle($.getName(), "https://vrchat.com/home/group/"+$.getId())
                    .setThumbnail($.getBannerUrl() == null || $.getBannerUrl().isEmpty() ? null : $.getBannerUrl())
                    .setDescription($.getDescription() == null || $.getDescription().isEmpty() ? null : $.getDescription())
                    .addField("Report group", MarkdownUtil.maskedLink("link", VRChatHelpDeskURLs.newModerationRequest_content_group(ScarletDiscordCommands.this.discord.requestingEmail.get(), $.getId(), "Group", null)), false)
                    .build())
                .toArray(MessageEmbed[]::new);
            ScarletDiscordCommands.this.discord.interactions.new Pagination(event.getId(), embeds, entriesPerPage).queue(hook);
        }
        @SlashCmd("avatar")
        @Desc("Searches for avatars")
        public void avatar(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("search-query") String searchQuery, @SlashOpt("entries-per-page") int entriesPerPage) throws Exception
        {
            MessageEmbed[] embeds = AvatarSearch.vrcxSearchAllCached(ScarletDiscordCommands.this.discord.getAvatarSearchProviders(), searchQuery)
                .map($ -> new EmbedBuilder()
                    .setAuthor($.authorName, "https://vrchat.com/home/user/"+$.authorId, null)
                    .setTitle($.name, "https://vrchat.com/home/avatar/"+$.id)
                    .setThumbnail($.imageUrl == null || $.imageUrl.isEmpty() ? null : $.imageUrl)
                    .setDescription($.description == null || $.description.isEmpty() ? null : $.description)
                    .addField("Report avatar", MarkdownUtil.maskedLink("link", VRChatHelpDeskURLs.newModerationRequest_content_avatar(ScarletDiscordCommands.this.discord.requestingEmail.get(), $.id, "Avatar", null)), false)
                    .build())
                .toArray(MessageEmbed[]::new);
            ScarletDiscordCommands.this.discord.interactions.new Pagination(event.getId(), embeds, entriesPerPage).queue(hook);
        }
    }
    // moderation-tags

    @SlashCmd("moderation-tags")
    @Desc("Configures custom moderation tags")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class ModerationTags
    {
        @SlashCmd("list")
        @Desc("Lists all custom moderation tags")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("entries-per-page") int entriesPerPage)
        {
            MessageEmbed[] embeds = ScarletDiscordCommands.this.discord.scarlet.moderationTags.getTags().stream().map(tag -> new EmbedBuilder().setAuthor(tag.value).setTitle(MiscUtils.maybeEllipsis(256, tag.label)).setDescription(MiscUtils.maybeEllipsis(4096, tag.description)).build()).toArray(MessageEmbed[]::new);
            ScarletDiscordCommands.this.discord.interactions.new Pagination(event.getId(), embeds, entriesPerPage).queue(hook);
        }
        public final SlashOption<String> _valueNA = SlashOption.ofString("value", "The internal id of the tag (prefer only `a-zA-Z`, `0-9`, `-`, `_`)", true, null).with($ -> $.setRequiredLength(1, 100));
        public final SlashOption<String> _label = SlashOption.ofString("label", "The internal display name of the tag", true, null).with($ -> $.setRequiredLength(1, 100));
        public final SlashOption<String> _description = SlashOption.ofString("description", "The internal description of the tag", false, null);
        @SlashCmd("add")
        @Desc("Adds a custom moderation tag")
        public void add(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("_valueNA") String value, @SlashOpt("label") String label, @SlashOpt("description") String description)
        {
            switch (ScarletDiscordCommands.this.discord.scarlet.moderationTags.addOrUpdateTag(value, label, description))
            {
            default: {
                hook.sendMessage("Failed to add moderation tag: list == null").setEphemeral(true).queue();
            } break;
            case -2: {
                hook.sendMessage("Failed to add moderation tag: there are already the maximum of 125 moderation tags").setEphemeral(true).queue();
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
        }
        public final SlashOption<String> _value = SlashOption.ofString("value", "The internal id of the tag", true, null, this::_value);
        void _value(CommandAutoCompleteInteractionEvent event) {
            DInteractions.SlashOptionsChoicesUnsanitized.autocomplete(event, ScarletDiscordCommands.this.discord.scarlet.moderationTags.getTagChoices().toArray(new Command.Choice[0]), false);
        }
        @SlashCmd("delete")
        @Desc("Removes a custom moderation tag")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("_value") String value)
        {
            switch (ScarletDiscordCommands.this.discord.scarlet.moderationTags.removeTag(value))
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
        }
    }

    // watched-group

    @SlashCmd("watched-group")
    @Desc("Configures watched groups")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class WatchedGroup_
    {
        @SlashCmd("list")
        @Desc("Lists all watched groups")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("entries-per-page") int entriesPerPage)
        {
            MessageEmbed[] embeds = ScarletDiscordCommands.this.discord.scarlet
                .watchedGroups
                .watchedGroups
                .values()
                .stream()
                .map($ -> $.embed(ScarletDiscordCommands.this.discord.scarlet.vrc.getGroup($.id)).build())
                .toArray(MessageEmbed[]::new)
            ;
            
            ScarletDiscordCommands.this.discord.interactions.new Pagination(event.getId(), embeds, entriesPerPage).queue(hook);
        }
        @SlashCmd("export")
        @Desc("Exports watched groups as a JSON file")
        public void export(SlashCommandInteractionEvent event, InteractionHook hook)
        {
            LOG.info("Exporting watched groups JSON");
            hook.sendFiles(FileUpload.fromData(ScarletDiscordCommands.this.discord.scarlet.watchedGroups.watchedGroupsFile)).setEphemeral(false).queue();
        }
        @SlashCmd("import")
        @Desc("Imports watched groups from an attached file")
        public void import_(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("import-file") Message.Attachment importedFile)
        {
            String fileName = importedFile.getFileName(),
                    attachmentUrl = importedFile.getUrl();
             
             if (fileName.endsWith(".csv"))
             {
                 LOG.info("Importing watched groups legacy CSV from attachment: "+fileName);
                 try (Reader reader = new InputStreamReader(HttpURLInputStream.get(attachmentUrl)))
                 {
                     if (ScarletDiscordCommands.this.discord.scarlet.watchedGroups.importLegacyCSV(reader, true))
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
                     if (ScarletDiscordCommands.this.discord.scarlet.watchedGroups.importJson(reader, true))
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
        public final SlashOption<String> _vrchatGroup = SlashOption.ofString("vrchat-group", "The VRChat group id (grp_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, null, VrcIds::resolveGroupId, true, this::_vrchatGroup);
        void _vrchatGroup(CommandAutoCompleteInteractionEvent event) {
            AutoCompleteCallbackAction action = event.replyChoices();
            Command.Choice groupChoice = ScarletDiscordCommands.this.discord.userSf2lastEdited_groupId.get(event.getUser().getId());
            if (groupChoice != null)
            {
                action.addChoices(groupChoice);
            }
            String typing = event.getFocusedOption().getValue();
            long choicesLeft = 25L;
            if (typing != null && !(typing = typing.trim()).isEmpty())
            {
                if (VrcIds.id_group.matcher(typing).matches() || VrcIds.id_group_code.matcher(typing).matches())
                {
                    String groupId = VrcIds.resolveGroupId(typing);
                    Group group = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroup(groupId, ScarletJsonCache.ALWAYS_PREFER_CACHED);
                    if (group != null)
                    {
                        action.addChoice(group.getName(), typing);
                        choicesLeft--;
                    }
                }
                else
                {
                    action.addChoices(ScarletDiscordCommands.this.discord.scarlet.watchedGroups
                        .watchedGroups
                        .keySet()
                        .stream()
                        .map(id -> {
                            Group group0 = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroup(id, ScarletJsonCache.ALWAYS_PREFER_CACHED);
                            return new Command.Choice(group0 != null ? group0.getName() : id, id);
                        })
                        .sorted(DInteractions.choicesByLevenshtein(typing))
                        .limit(choicesLeft)
                        .collect(Collectors.toList())
                    );
                }
            }
            else
            {
                action.addChoices(ScarletDiscordCommands.this.discord.scarlet.watchedGroups
                    .watchedGroups
                    .keySet()
                    .stream()
                    .map(id -> {
                        Group group0 = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroup(id, ScarletJsonCache.ALWAYS_PREFER_CACHED);
                        return new Command.Choice(group0 != null ? group0.getName() : id, id);
                    })
                    .limit(choicesLeft)
                    .collect(Collectors.toList())
                );
            }
            action.queue();
        }
        io.github.vrchatapi.model.Group vrchatGroup(SlashCommandInteractionEvent event, String groupId)
        {
            if (groupId != null && !(groupId = groupId.trim()).isEmpty())
            {
                Group group = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroup(groupId);
                ScarletDiscordCommands.this.discord.userSf2lastEdited_groupId.put(event.getUser().getId(), new Command.Choice(group != null ? group.getName() : groupId, groupId));
                return group;
            }
            return null;
        }
        
        public final SlashOption<ScarletWatchedGroups.WatchedGroup.Type> _groupType = SlashOption.ofEnum("group-type", "The type of watched group", true, ScarletWatchedGroups.WatchedGroup.Type.UNKNOWN);
        public final SlashOption<String> _groupTags = SlashOption.ofString("group-tags", "A list of tags, separated by one of ',', ';', '/'", false, null);
        public final SlashOption<Integer> _groupPriority = SlashOption.ofInt("group-priority", "The priority of this group", false, 0).with($->$.setRequiredRange(-100L, 100L));
        public final SlashOption<String> _message = SlashOption.ofString("message", "A message to announce with TTS", false, null);
        
        @SlashCmd("add")
        @Desc("Adds a watched group")
        public void add(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId,
            @SlashOpt("group-type") ScarletWatchedGroups.WatchedGroup.Type groupType,
            @SlashOpt("group-tags") String groupTags,
            @SlashOpt("group-priority") int groupPriority,
            @SlashOpt("message") String message)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup != null)
            {
                hook.sendMessage("That group is already watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
            Group group = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroup(groupId);
            if (group == null)
            {
                hook.sendMessage("That group doesn't seem to exist").setEphemeral(true).queue();
                return;
            }
            watchedGroup = new ScarletWatchedGroups.WatchedGroup();
            watchedGroup.id = groupId;
            watchedGroup.type = groupType;
            if (groupTags != null)
                watchedGroup.tags.clear().addAll(Arrays.stream(groupTags.split("[,;/]")).map(String::trim).toArray(String[]::new));
            if (message != null)
                watchedGroup.message = message;
            watchedGroup.priority = groupPriority;
            ScarletDiscordCommands.this.discord.scarlet.watchedGroups.addWatchedGroup(groupId, watchedGroup);
            hook.sendMessageFormat("Added group [%s](https://vrchat.com/home/group/%s)", group.getName(), group.getId()).setEphemeral(true).queue();
        }
        @SlashCmd("delete-watched-group")
        @Desc("Removes a watched group")
        public void deleteWatchedGroup(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId)
        {
            if (ScarletDiscordCommands.this.discord.scarlet.watchedGroups.removeWatchedGroup(groupId))
            {
                Group group = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroup(groupId);
                hook.sendMessageFormat("Removed group [%s](https://vrchat.com/home/group/%s)", group == null ? groupId : group.getName(), groupId).setEphemeral(true).queue();
            }
            else
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
            }
        }
        @SlashCmd("view")
        @Desc("Views a group's watch information")
        public void view(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
            Group group = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroup(groupId);
            hook.sendMessageEmbeds(watchedGroup.embed(group).build()).setEphemeral(true).queue();
        }
        public final SlashOption<String> _groupTags_R = SlashOption.ofString("group-tags", "A list of tags, separated by one of ',', ';', '/'", true, null);
        public final SlashOption<String> _groupTag_R = SlashOption.ofString("group-tag", "A tag", true, null);
        public final SlashOption<Integer> _groupPriority_R = SlashOption.ofInt("group-priority", "The priority of this group", true, 0).with($->$.setRequiredRange(-100L, 100L));
        public final SlashOption<Boolean> _critical_R = SlashOption.ofBool("critical", "The critical status of the group", true, null);
        public final SlashOption<Boolean> _silent_R = SlashOption.ofBool("silent", "The silent status of the group", true, null);
        public final SlashOption<String> _message_R = SlashOption.ofString("message", "A message to announce with TTS", true, null);
        @SlashCmd("set-critical")
        @Desc("Sets a group's critical status")
        public void setCritical(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId, @SlashOpt("_critical_R") boolean critical)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
            if (critical)
            {
                if (watchedGroup.critical)
                {
                    hook.sendMessage("That group is already flagged as critical").setEphemeral(true).queue();
                    return;
                }
                hook.sendMessage("Flagged group as critical").setEphemeral(true).queue();
            }
            else
            {
                if (!watchedGroup.critical)
                {
                    hook.sendMessage("That group is already not flagged as critical").setEphemeral(true).queue();
                    return;
                }
                hook.sendMessage("Unflagged group as critical").setEphemeral(true).queue();
            }
            watchedGroup.critical = critical;
            ScarletDiscordCommands.this.discord.scarlet.watchedGroups.save();
        }
        @SlashCmd("set-silent")
        @Desc("Sets a group's silent status")
        public void setSilent(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId, @SlashOpt("_silent_R") boolean silent)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
            if (silent)
            {
                if (watchedGroup.silent)
                {
                    hook.sendMessage("That group is already flagged as silent").setEphemeral(true).queue();
                    return;
                }
                hook.sendMessage("Flagged group as silent").setEphemeral(true).queue();
            }
            else
            {
                if (!watchedGroup.silent)
                {
                    hook.sendMessage("That group is already not flagged as silent").setEphemeral(true).queue();
                    return;
                }
                hook.sendMessage("Unflagged group as silent").setEphemeral(true).queue();
            }
            watchedGroup.silent = silent;
            ScarletDiscordCommands.this.discord.scarlet.watchedGroups.save();
        }
        @SlashCmd("set-type")
        @Desc("Sets a group's watch type")
        public void setType(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId, @SlashOpt("group-type") ScarletWatchedGroups.WatchedGroup.Type groupType)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
            if (watchedGroup.type == groupType)
            {
                hook.sendMessage("That group is already marked as "+groupType).setEphemeral(true).queue();
                return;
            }
            watchedGroup.type = groupType;
            hook.sendMessage("Marking group as "+groupType).setEphemeral(true).queue();
            ScarletDiscordCommands.this.discord.scarlet.watchedGroups.save();
        }
        @SlashCmd("set-priority")
        @Desc("Sets a group's priority")
        public void setPriority(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId, @SlashOpt("_groupPriority_R") int groupPriority)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
            watchedGroup.priority = groupPriority;
            hook.sendMessage("Setting group priority to "+groupPriority).setEphemeral(true).queue();
            ScarletDiscordCommands.this.discord.scarlet.watchedGroups.save();
        }
        @SlashCmd("set-message")
        @Desc("Sets a group's TTS announcement message")
        public void setMessage(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId, @SlashOpt("_message_R") String message)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
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
            ScarletDiscordCommands.this.discord.scarlet.watchedGroups.save();
        }
        @SlashCmd("set-notes")
        @Desc("Sets a group's notes")
        public void setNotes(SlashCommandInteractionEvent event, @SlashOpt("vrchat-group") String groupId)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                event.reply("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
            event.replyModal(Modal.create("watched-group-set-notes:"+groupId, "Edit notes")
                .addActionRow(TextInput.create("notes", "Notes", TextInputStyle.PARAGRAPH)
                    .setValue(MiscUtils.blank(watchedGroup.notes) ? null : watchedGroup.notes)
                    .setRequiredRange(0, 1024)
                    .build())
                .build())
            .queue();
        }
        @SlashCmd("set-tags")
        @Desc("Sets a group's tags")
        public void setTags(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId, @SlashOpt("_groupTags_R") String groupTags)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
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
            ScarletDiscordCommands.this.discord.scarlet.watchedGroups.save();
        }
        @SlashCmd("add-tag")
        @Desc("Adds a tag for a group")
        public void addTag(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId, @SlashOpt("_groupTag_R") String groupTag)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
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
            ScarletDiscordCommands.this.discord.scarlet.watchedGroups.save();
        }
        @SlashCmd("remove-tag")
        @Desc("Removes a tag from a group")
        public void removeTag(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-group") String groupId, @SlashOpt("_groupTag_R") String groupTag)
        {
            ScarletWatchedGroups.WatchedGroup watchedGroup = ScarletDiscordCommands.this.discord.scarlet.watchedGroups.getWatchedGroup(groupId);
            if (watchedGroup == null)
            {
                hook.sendMessage("That group is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatGroup(event, groupId);
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
            ScarletDiscordCommands.this.discord.scarlet.watchedGroups.save();
        }
    }

    final Map<String, WatchedEntity_<?>> watchedEntityCommands = new HashMap<>();
    protected abstract class WatchedEntity_<E>
    {
        {
            ScarletDiscordCommands.this.watchedEntityCommands.put(this._singular(), this);
        }
        protected abstract String _singular();
        protected abstract String _plural();
        protected abstract ScarletWatchedEntities<E> _watchedEntities();
        protected abstract E _getEntity(String id, long minEpoch);
        protected abstract String _getEntityName(E entity);
        protected abstract boolean _isValidEntityId(String id);
        final Map<String, Command.Choice> userSf2lastEdited_entityId = new ConcurrentHashMap<>();
//        @SlashCmd("list")
//        @Desc("Lists all watched groups")
        protected void _list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("entries-per-page") int entriesPerPage)
        {
            MessageEmbed[] embeds = this._watchedEntities()
                .watchedEntities
                .values()
                .stream()
                .map($ -> $.embed(this._watchedEntities(), this._getEntity($.id, ScarletJsonCache.ALWAYS_PREFER_CACHED)).build())
                .toArray(MessageEmbed[]::new)
            ;
            
            ScarletDiscordCommands.this.discord.interactions.new Pagination(event.getId(), embeds, entriesPerPage).queue(hook);
        }
//        @SlashCmd("export")
//        @Desc("Exports watched groups as a JSON file")
        protected void _export(SlashCommandInteractionEvent event, InteractionHook hook)
        {
            LOG.info("Exporting watched "+this._plural()+" JSON");
            hook.sendFiles(FileUpload.fromData(this._watchedEntities().watchedEntitiesFile)).setEphemeral(false).queue();
        }
//        @SlashCmd("import")
//        @Desc("Imports watched groups from an attached file")
        protected void _import_(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("import-file") Message.Attachment importedFile)
        {
            String fileName = importedFile.getFileName(),
                   attachmentUrl = importedFile.getUrl();
             
             if (fileName.endsWith(".csv"))
             {
                 LOG.info("Importing watched "+this._plural()+" legacy CSV from attachment: "+fileName);
                 try (Reader reader = new InputStreamReader(HttpURLInputStream.get(attachmentUrl)))
                 {
                     if (this._watchedEntities().importLegacyCSV(reader, true))
                     {
                         LOG.info("Successfully imported watched "+this._plural()+" legacy CSV");
                         hook.sendMessageFormat("Successfully imported watched "+this._plural()+" legacy CSV").setEphemeral(true).queue();
                     }
                     else
                     {
                         LOG.warn("Failed to import watched "+this._plural()+" legacy CSV with unknown reason");
                         hook.sendMessageFormat("Failed to import watched "+this._plural()+" legacy CSV with unknown reason").setEphemeral(true).queue();
                     }
                 }
                 catch (Exception ex)
                 {
                     LOG.error("Exception importing watched "+this._plural()+" legacy CSV from attachment: "+fileName, ex);
                     hook.sendMessageFormat("Exception while importing %s: %s", fileName, ex).setEphemeral(true).queue();
                 }
             }
             else if (fileName.endsWith(".json"))
             {
                 LOG.info("Importing watched "+this._plural()+" JSON from attachment: "+fileName);
                 try (Reader reader = new InputStreamReader(HttpURLInputStream.get(attachmentUrl)))
                 {
                     if (this._watchedEntities().importJson(reader, true))
                     {
                         LOG.info("Successfully imported watched "+this._plural()+" JSON");
                         hook.sendMessageFormat("Successfully imported watched "+this._plural()+" JSON").setEphemeral(true).queue();
                     }
                     else
                     {
                         LOG.warn("Failed to import watched "+this._plural()+" JSON with unknown reason");
                         hook.sendMessageFormat("Failed to import watched "+this._plural()+" JSON with unknown reason").setEphemeral(true).queue();
                     }
                 }
                 catch (Exception ex)
                 {
                     LOG.error("Exception importing watched "+this._plural()+" JSON from attachment: "+fileName, ex);
                     hook.sendMessageFormat("Exception while importing %s: %s", fileName, ex).setEphemeral(true).queue();
                 }
             }
             else
             {
                 LOG.warn("Skipping attachment: "+fileName);
                 hook.sendMessageFormat("File '%s' is not importable.", fileName).setEphemeral(true).queue();
             }
        }
        void _vrchatEntity(CommandAutoCompleteInteractionEvent event) {
            AutoCompleteCallbackAction action = event.replyChoices();
            Command.Choice entityChoice = this.userSf2lastEdited_entityId.get(event.getUser().getId());
            long choicesLeft = 25L;
            if (entityChoice != null)
            {
                action.addChoices(entityChoice);
                choicesLeft--;
            }
            String typing = event.getFocusedOption().getValue();
            if (typing != null && !(typing = typing.trim()).isEmpty())
            {
                if (this._isValidEntityId(typing))
                {
                    if (this._watchedEntities().getWatchedEntity(typing) != null)
                    {
                        E entity = this._getEntity(typing, ScarletJsonCache.ALWAYS_PREFER_CACHED);
                        String name;
                        if (entity != null && (name = this._getEntityName(entity)) != null)
                        {
                            action.addChoice(name, typing);
                        }
                        else
                        {
                            action.addChoiceStrings(typing);
                        }
                        choicesLeft--;
                    }
                }
                else
                {
                    action.addChoices(this._watchedEntities()
                        .watchedEntities
                        .keySet()
                        .stream()
                        .map(id -> {
                            E entity0 = this._getEntity(id, ScarletJsonCache.ALWAYS_PREFER_CACHED);
                            return new Command.Choice(entity0 != null ? this._getEntityName(entity0) : id, id);
                        })
                        .sorted(DInteractions.choicesByLevenshtein(typing))
                        .limit(choicesLeft)
                        .collect(Collectors.toList())
                    );
                }
            }
            else
            {
                action.addChoices(this._watchedEntities()
                    .watchedEntities
                    .keySet()
                    .stream()
                    .map(id -> {
                        E entity0 = this._getEntity(id, ScarletJsonCache.ALWAYS_PREFER_CACHED);
                        return new Command.Choice(entity0 != null ? this._getEntityName(entity0) : id, id);
                    })
                    .limit(choicesLeft)
                    .collect(Collectors.toList())
                );
            }
            action.queue();
        }
        E vrchatEntity(SlashCommandInteractionEvent event, String entityId)
        {
            if (entityId != null && !(entityId = entityId.trim()).isEmpty())
            {
                E entity = this._getEntity(entityId, ScarletJsonCache.ALWAYS_PREFER_CACHED);
                this.userSf2lastEdited_entityId.put(event.getUser().getId(), new Command.Choice(entity != null ? this._getEntityName(entity) : entityId, entityId));
                return entity;
            }
            return null;
        }
        
//        @SlashCmd("add")
//        @Desc("Exports watched groups as a JSON file")
        protected void _add(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId,
            @SlashOpt("entity-type") ScarletWatchedEntities.WatchedEntity.Type entityType,
            @SlashOpt("entity-tags") String entityTags,
            @SlashOpt("entity-priority") int entityPriority,
            @SlashOpt("message") String message)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity != null)
            {
                hook.sendMessage("That "+this._singular()+" is already watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            E entity = this._getEntity(entityId, ScarletJsonCache.ALWAYS_FETCH);
            if (entity == null)
            {
                hook.sendMessage("That "+this._singular()+" doesn't seem to exist").setEphemeral(true).queue();
                return;
            }
            watchedEntity = new ScarletWatchedEntities.WatchedEntity();
            watchedEntity.id = entityId;
            watchedEntity.type = entityType;
            if (entityTags != null)
                watchedEntity.tags.clear().addAll(Arrays.stream(entityTags.split("[,;/]")).map(String::trim).toArray(String[]::new));
            if (message != null)
                watchedEntity.message = message;
            watchedEntity.priority = entityPriority;
            this._watchedEntities().addWatchedEntity(entityId, watchedEntity);
            hook.sendMessageFormat("Added %s [%s](https://vrchat.com/home/%s/%s)", this._singular(), this._getEntityName(entity), this._singular(), entityId).setEphemeral(true).queue();
        }
//        @SlashCmd("remove")
//        @Desc("Removes a watched group")
        protected void _remove(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId)
        {
            if (this._watchedEntities().removeWatchedEntity(entityId))
            {
                E entity = this._getEntity(entityId, ScarletJsonCache.ALWAYS_PREFER_CACHED);
                hook.sendMessageFormat("Removed %s [%s](https://vrchat.com/home/%s/%s)", this._singular(), entity == null ? entityId : this._getEntityName(entity), this._singular(), entityId).setEphemeral(true).queue();
            }
            else
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
            }
        }
//        @SlashCmd("view")
//        @Desc("Views a group's watch information")
        protected void _view(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity == null)
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            E entity = this._getEntity(entityId, ScarletJsonCache.ALWAYS_PREFER_CACHED);
            hook.sendMessageEmbeds(watchedEntity.embed(this._watchedEntities(), entity).build()).setEphemeral(true).queue();
        }
//        @SlashCmd("set-critical")
//        @Desc("Sets a group's critical status")
        protected void _setCritical(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId, @SlashOpt("_critical_R") boolean critical)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity == null)
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            if (critical)
            {
                if (watchedEntity.critical)
                {
                    hook.sendMessage("That "+this._singular()+" is already flagged as critical").setEphemeral(true).queue();
                    return;
                }
                hook.sendMessage("Flagged "+this._singular()+" as critical").setEphemeral(true).queue();
            }
            else
            {
                if (!watchedEntity.critical)
                {
                    hook.sendMessage("That "+this._singular()+" is already not flagged as critical").setEphemeral(true).queue();
                    return;
                }
                hook.sendMessage("Unflagged "+this._singular()+" as critical").setEphemeral(true).queue();
            }
            watchedEntity.critical = critical;
            this._watchedEntities().save();
        }
//        @SlashCmd("set-silent")
//        @Desc("Sets a group's silent status")
        protected void _setSilent(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId, @SlashOpt("_silent_R") boolean silent)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity == null)
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            if (silent)
            {
                if (watchedEntity.silent)
                {
                    hook.sendMessage("That "+this._singular()+" is already flagged as silent").setEphemeral(true).queue();
                    return;
                }
                hook.sendMessage("Flagged "+this._singular()+" as silent").setEphemeral(true).queue();
            }
            else
            {
                if (!watchedEntity.silent)
                {
                    hook.sendMessage("That "+this._singular()+" is already not flagged as silent").setEphemeral(true).queue();
                    return;
                }
                hook.sendMessage("Unflagged "+this._singular()+" as silent").setEphemeral(true).queue();
            }
            watchedEntity.silent = silent;
            this._watchedEntities().save();
        }
//        @SlashCmd("set-type")
//        @Desc("Sets a group's watch type")
        protected void _setType(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId, @SlashOpt("entity-type") ScarletWatchedEntities.WatchedEntity.Type entityType)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity == null)
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            if (watchedEntity.type == entityType)
            {
                hook.sendMessage("That "+this._singular()+" is already marked as "+entityType).setEphemeral(true).queue();
                return;
            }
            watchedEntity.type = entityType;
            hook.sendMessage("Marking "+this._singular()+" as "+entityType).setEphemeral(true).queue();
            this._watchedEntities().save();
        }
//        @SlashCmd("set-priority")
//        @Desc("Sets a group's priority")
        protected void _setPriority(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId, @SlashOpt("_entityPriority_R") int entityPriority)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity == null)
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            watchedEntity.priority = entityPriority;
            hook.sendMessage("Setting "+this._singular()+" priority to "+entityPriority).setEphemeral(true).queue();
            this._watchedEntities().save();
        }
//        @SlashCmd("set-message")
//        @Desc("Sets a group's TTS announcement message")
        protected void _setMessage(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId, @SlashOpt("_message_R") String message)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity == null)
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            if (message == null)
            {
                if (watchedEntity.message == null)
                {
                    hook.sendMessage("That "+this._singular()+" already has no message").setEphemeral(true).queue();
                    return;
                }
                else
                {
                    hook.sendMessageFormat("Removing "+this._singular()+"'s message (was `%s`)", message).setEphemeral(true).queue();
                }
            }
            else
            {
                if (message.equals(watchedEntity.message))
                {
                    hook.sendMessage("That "+this._singular()+"'s message is already exactly that").setEphemeral(true).queue();
                    return;
                }
                else
                {
                    hook.sendMessageFormat("Setting "+this._singular()+" TTS announcement to `%s` (was `%s`)", message, watchedEntity.message).setEphemeral(true).queue();
                }
            }
            watchedEntity.message = message;
            this._watchedEntities().save();
        }
//      @SlashCmd("set-notes")
//      @Desc("Sets a group's notes")
      protected void _setNotes(SlashCommandInteractionEvent event, @SlashOpt("vrchat-entity") String entityId)
      {
          ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
          if (watchedEntity == null)
          {
              event.reply("That "+this._singular()+" is not watched").setEphemeral(true).queue();
              return;
          }
          this.vrchatEntity(event, entityId);
          event.replyModal(Modal.create("watched-entity-set-notes:"+this._singular()+":"+entityId, "Edit notes")
              .addActionRow(TextInput.create("notes", "Notes", TextInputStyle.PARAGRAPH)
                  .setValue(MiscUtils.blank(watchedEntity.notes) ? null : watchedEntity.notes)
                  .setRequiredRange(0, 1024)
                  .build())
              .build())
          .queue();
      }
      protected void _setNotes(ModalInteractionEvent event, @SlashOpt("vrchat-entity") String entityId)
      {
          ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
          if (watchedEntity == null)
          {
              event.reply("That "+this._singular()+" is not watched").setEphemeral(true).queue();
              return;
          }
          event.reply("Set notes for "+this._singular()).setEphemeral(true).queue();
          watchedEntity.notes = event.getValue("notes").getAsString();
          this._watchedEntities().save();
      }
//        @SlashCmd("set-tags")
//        @Desc("Sets a group's tags")
        protected void _setTags(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId, @SlashOpt("_entityTags_R") String entityTags)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity == null)
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            if (entityTags == null)
            {
                if (watchedEntity.tags.isEmpty())
                {
                    hook.sendMessage("That "+this._singular()+" already has no tags").setEphemeral(true).queue();
                    return;
                }
                else
                {
                    hook.sendMessageFormat("Removing "+this._singular()+"'s tags (was `%s`)",
                            watchedEntity.tags.strings().stream().filter(Objects::nonNull).collect(Collectors.joining("`, `")))
                        .setEphemeral(true)
                        .queue();
                    watchedEntity.tags.clear();
                }
            }
            else
            {
                String[] newTags = Arrays.stream(entityTags.split("[,;/]")).map(String::trim).distinct().toArray(String[]::new);
                if (watchedEntity.tags.strings().equals(new HashSet<>(Arrays.asList(newTags))))
                {
                    hook.sendMessage("That "+this._singular()+" already has those exact tags").setEphemeral(true).queue();
                    return;
                }
                else
                {
                    hook.sendMessageFormat("Setting "+this._singular()+" tags to `%s` (was `%s`)",
                            Arrays.stream(newTags).filter(Objects::nonNull).collect(Collectors.joining("`, `")),
                            watchedEntity.tags.strings().stream().filter(Objects::nonNull).collect(Collectors.joining("`, `")))
                        .setEphemeral(true)
                        .queue();
                    watchedEntity.tags.clear().addAll(newTags);
                }
            }
            this._watchedEntities().save();
        }
//        @SlashCmd("add-tag")
//        @Desc("Adds a tag for a group")
        protected void _addTag(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId, @SlashOpt("_entityTag_R") String entityTag)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity == null)
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            String entityTag0 = entityTag.trim();
            if (watchedEntity.tags.isEmpty())
            {
                watchedEntity.tags.add(entityTag0);
                hook.sendMessageFormat("Added tag `%s` (was empty)", entityTag0).setEphemeral(true).queue();
            }
            else if (watchedEntity.tags.add(entityTag0))
            {
                hook.sendMessageFormat("Added tag `%s` (was `%s`)",
                        entityTag0,
                        watchedEntity.tags.strings().stream().filter(Objects::nonNull).collect(Collectors.joining("`, `")))
                    .setEphemeral(true)
                    .queue();
            }
            else
            {
                hook.sendMessage("That "+this._singular()+" already has that tag").setEphemeral(true).queue();
            }
            this._watchedEntities().save();
        }
//        @SlashCmd("remove-tag")
//        @Desc("Removes a tag from a group")
        protected void _removeTag(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-entity") String entityId, @SlashOpt("_entityTag_R") String entityTag)
        {
            ScarletWatchedEntities.WatchedEntity watchedEntity = this._watchedEntities().getWatchedEntity(entityId);
            if (watchedEntity == null)
            {
                hook.sendMessage("That "+this._singular()+" is not watched").setEphemeral(true).queue();
                return;
            }
            this.vrchatEntity(event, entityId);
            if (watchedEntity.tags.isEmpty())
            {
                hook.sendMessage("That "+this._singular()+" already has no tags").setEphemeral(true).queue();
                return;
            }
            if (watchedEntity.tags.remove(entityTag))
            {
                hook.sendMessageFormat("Removed "+this._singular()+" tag `%s`)", entityTag).setEphemeral(true).queue();
            }
            else
            {
                hook.sendMessage("That "+this._singular()+" doesn't have that tag").setEphemeral(true).queue();
                return;
            }
            this._watchedEntities().save();
        }
    }

    // watched-user

    @SlashCmd("watched-user")
    @Desc("Configures watched users")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class WatchedUser_ extends WatchedEntity_<User>
    {
        @Override
        protected String _singular()
        { return "user"; }
        @Override
        protected String _plural()
        { return "users"; }
        @Override
        protected ScarletWatchedEntities<User> _watchedEntities()
        { return ScarletDiscordCommands.this.discord.scarlet.watchedUsers; }
        @Override
        protected User _getEntity(String id, long minEpoch)
        { return ScarletDiscordCommands.this.discord.scarlet.vrc.getUser(id, minEpoch); }
        @Override
        protected String _getEntityName(User entity)
        { return entity.getDisplayName(); }
        @Override
        protected boolean _isValidEntityId(String id)
        { return VrcIds.id_user.matcher(id).matches(); }
        @SlashCmd("list")
        @Desc("Lists all watched users")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("entries-per-page") int entriesPerPage)
        { super._list(event, hook, entriesPerPage); }
        @SlashCmd("export")
        @Desc("Exports watched users as a JSON file")
        public void export(SlashCommandInteractionEvent event, InteractionHook hook)
        { super._export(event, hook); }
        @SlashCmd("import")
        @Desc("Imports watched users from an attached file")
        public void import_(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("import-file") Message.Attachment importedFile)
        { super._import_(event, hook, importedFile); }
        public final SlashOption<String> _vrchatUser = SlashOption.ofString("vrchat-user", "The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, null, VrcIds::resolveUserId, true, this::_vrchatEntity);
        @SlashCmd("add")
        @Desc("Adds a watched user")
        public void add(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId,
            @SlashOpt("entity-type") ScarletWatchedEntities.WatchedEntity.Type entityType,
            @SlashOpt("entity-tags") String entityTags,
            @SlashOpt("entity-priority") int entityPriority,
            @SlashOpt("entity-message") String message)
        { super._add(event, hook, userId, entityType, entityTags, entityPriority, message); }
        @SlashCmd("remove")
        @Desc("Removes a watched user")
        public void remove(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId)
        { super._remove(event, hook, userId); }
        @SlashCmd("view")
        @Desc("Views a user's watch information")
        public void view(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId)
        { super._view(event, hook, userId); }
        @SlashCmd("set-critical")
        @Desc("Sets a user's critical status")
        public void setCritical(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId, @Required@SlashOpt("entity-critical") boolean critical)
        { super._setCritical(event, hook, userId, critical); }
        @SlashCmd("set-silent")
        @Desc("Sets a user's silent status")
        public void setSilent(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId, @Required@SlashOpt("entity-silent") boolean silent)
        { super._setSilent(event, hook, userId, silent); }
        @SlashCmd("set-type")
        @Desc("Sets a user's watch type")
        public void setType(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId, @Required@SlashOpt("entity-type") ScarletWatchedEntities.WatchedEntity.Type entityType)
        { super._setType(event, hook, userId, entityType); }
        @SlashCmd("set-priority")
        @Desc("Sets a user's priority")
        public void setPriority(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId, @Required@SlashOpt("entity-priority") int entityPriority)
        { super._setPriority(event, hook, userId, entityPriority); }
        @SlashCmd("set-message")
        @Desc("Sets a user's TTS announcement message")
        public void setMessage(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId, @Required@SlashOpt("entity-message") String message)
        { super._setMessage(event, hook, userId, message); }
        @SlashCmd("set-notes")
        @Desc("Sets a user's notes")
        public void setNotes(SlashCommandInteractionEvent event, @SlashOpt("vrchat-user") String userId)
        { super._setNotes(event, userId); }
        @SlashCmd("set-tags")
        @Desc("Sets a user's tags")
        public void setTags(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId, @Required@SlashOpt("entity-tags") String entityTags)
        { super._setTags(event, hook, userId, entityTags); }
        @SlashCmd("add-tag")
        @Desc("Adds a tag for a user")
        public void addTag(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId, @Required@SlashOpt("entity-tag") String entityTag)
        { super._addTag(event, hook, userId, entityTag); }
        @SlashCmd("remove-tag")
        @Desc("Removes a tag from a user")
        public void removeTag(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") String userId, @Required@SlashOpt("entity-tag") String entityTag)
        { super._removeTag(event, hook, userId, entityTag); }
    }

    // watched-avatar

    @SlashCmd("watched-avatar")
    @Desc("Configures watched avatars")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class WatchedAvatar_ extends WatchedEntity_<Avatar>
    {
        @Override
        protected String _singular()
        { return "avatar"; }
        @Override
        protected String _plural()
        { return "avatars"; }
        @Override
        protected ScarletWatchedEntities<Avatar> _watchedEntities()
        { return ScarletDiscordCommands.this.discord.scarlet.watchedAvatars; }
        @Override
        protected Avatar _getEntity(String id, long minEpoch)
        { return ScarletDiscordCommands.this.discord.scarlet.vrc.getAvatar(id, minEpoch); }
        @Override
        protected String _getEntityName(Avatar entity)
        { return entity.getName(); }
        @Override
        protected boolean _isValidEntityId(String id)
        { return VrcIds.id_avatar.matcher(id).matches(); }
        @SlashCmd("list")
        @Desc("Lists all watched avatars")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("entries-per-page") int entriesPerPage)
        { super._list(event, hook, entriesPerPage); }
        @SlashCmd("export")
        @Desc("Exports watched avatars as a JSON file")
        public void export(SlashCommandInteractionEvent event, InteractionHook hook)
        { super._export(event, hook); }
        @SlashCmd("import")
        @Desc("Imports watched avatars from an attached file")
        public void import_(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("import-file") Message.Attachment importedFile)
        { super._import_(event, hook, importedFile); }
        public final SlashOption<String> _vrchatAvatar = SlashOption.ofString("vrchat-avatar", "The VRChat avatar id (avtr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)", true, null, VrcIds::resolveAvatarId, true, this::_vrchatEntity);
        @SlashCmd("add")
        @Desc("Adds a watched avatar")
        public void add(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId,
            @SlashOpt("entity-type") ScarletWatchedEntities.WatchedEntity.Type entityType,
            @SlashOpt("entity-tags") String entityTags,
            @SlashOpt("entity-priority") int entityPriority,
            @SlashOpt("entity-message") String message)
        { super._add(event, hook, avatarId, entityType, entityTags, entityPriority, message); }
        @SlashCmd("remove")
        @Desc("Removes a watched avatar")
        public void remove(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId)
        { super._remove(event, hook, avatarId); }
        @SlashCmd("view")
        @Desc("Views an avatar's watch information")
        public void view(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId)
        { super._view(event, hook, avatarId); }
        @SlashCmd("set-critical")
        @Desc("Sets an avatar's critical status")
        public void setCritical(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId, @Required@SlashOpt("entity-critical") boolean critical)
        { super._setCritical(event, hook, avatarId, critical); }
        @SlashCmd("set-silent")
        @Desc("Sets an avatar's silent status")
        public void setSilent(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId, @Required@SlashOpt("entity-silent") boolean silent)
        { super._setSilent(event, hook, avatarId, silent); }
        @SlashCmd("set-type")
        @Desc("Sets an avatar's watch type")
        public void setType(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId, @Required@SlashOpt("entity-type") ScarletWatchedEntities.WatchedEntity.Type entityType)
        { super._setType(event, hook, avatarId, entityType); }
        @SlashCmd("set-priority")
        @Desc("Sets an avatar's priority")
        public void setPriority(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId, @Required@SlashOpt("entity-priority") int entityPriority)
        { super._setPriority(event, hook, avatarId, entityPriority); }
        @SlashCmd("set-message")
        @Desc("Sets an avatar's TTS announcement message")
        public void setMessage(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId, @Required@SlashOpt("entity-message") String message)
        { super._setMessage(event, hook, avatarId, message); }
        @SlashCmd("set-notes")
        @Desc("Sets an avatar's notes")
        public void setNotes(SlashCommandInteractionEvent event, @SlashOpt("vrchat-avatar") String avatarId)
        { super._setNotes(event, avatarId); }
        @SlashCmd("set-tags")
        @Desc("Sets an avatar's tags")
        public void setTags(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId, @Required@SlashOpt("entity-tags") String entityTags)
        { super._setTags(event, hook, avatarId, entityTags); }
        @SlashCmd("add-tag")
        @Desc("Adds a tag for an avatar")
        public void addTag(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId, @Required@SlashOpt("entity-tag") String entityTag)
        { super._addTag(event, hook, avatarId, entityTag); }
        @SlashCmd("remove-tag")
        @Desc("Removes a tag from an avatar")
        public void removeTag(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-avatar") String avatarId, @Required@SlashOpt("entity-tag") String entityTag)
        { super._removeTag(event, hook, avatarId, entityTag); }
    }
    
    // staff-list

    @SlashCmd("staff-list")
    @Desc("Configures the staff list")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class StaffList extends _StaffList
    {
        @Override
        protected String _infoName()
        {
            return "staff";
        }
        @Override
        protected String[] _getIds()
        {
            return ScarletDiscordCommands.this.discord.scarlet.staffList.getStaffIds();
        }
        @Override
        protected boolean _addId(String vrcId)
        {
            return ScarletDiscordCommands.this.discord.scarlet.staffList.addStaffId(vrcId);
        }
        @Override
        protected boolean _removeId(String vrcId)
        {
            return ScarletDiscordCommands.this.discord.scarlet.staffList.removeStaffId(vrcId);
        }
        @SlashCmd("list")
        @Desc("Lists all staff users")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("entries-per-page") int entriesPerPage)
        {
            this._list(event, hook, entriesPerPage);
        }
        @SlashCmd("add")
        @Desc("Adds a user to the staff list")
        public void add(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser, @SlashOpt("discord-user") Member discordUser, @SlashOpt("_vrchatRoleOpt") io.github.vrchatapi.model.GroupRole vrchatRoleOpt)
        {
            this._add(event, hook, vrchatUser, discordUser, vrchatRoleOpt);
        }
        @SlashCmd("delete")
        @Desc("Removes a user from the staff list")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser, @SlashOpt("_vrchatRoleOpt") io.github.vrchatapi.model.GroupRole vrchatRoleOpt)
        {
            this._remove(event, hook, vrchatUser, vrchatRoleOpt);
        }
    }

    abstract class _StaffList
    {
        protected abstract String _infoName();
        protected abstract String[] _getIds();
        protected abstract boolean _addId(String vrcId);
        protected abstract boolean _removeId(String vrcId);
        protected void _list(SlashCommandInteractionEvent event, InteractionHook hook, int entriesPerPage)
        {
            long within1day = System.currentTimeMillis() - 86400_000L;
            List<GroupRole> roleList = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroupRoles(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId);
            Map<String, GroupRole> roles = roleList == null || roleList.isEmpty() ? Collections.emptyMap() : roleList.stream().collect(Collectors.toMap(GroupRole::getId, Function.identity()));
            MessageEmbed[] embeds = Arrays.stream(this._getIds())
                .map($ -> {
                    User sc = ScarletDiscordCommands.this.discord.scarlet.vrc.getUser($, within1day);
                    GroupLimitedMember member = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroupMembership(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, $);
                    ScarletData.UserMetadata userMeta = ScarletDiscordCommands.this.discord.scarlet.data.userMetadata($);
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
                        if (member.getJoinedAt() != null)
                        {
                            String epochJoined = Long.toUnsignedString(member.getJoinedAt().toEpochSecond());
                            builder.addField("Joined", "<t:"+epochJoined+":D> (<t:"+epochJoined+":R>)", false);
                        }
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
            ScarletDiscordCommands.this.discord.interactions.new Pagination(event.getId(), embeds, entriesPerPage).queue(hook);
        }
        protected void _add(SlashCommandInteractionEvent event, InteractionHook hook, io.github.vrchatapi.model.User vrchatUser, Member discordUser, io.github.vrchatapi.model.GroupRole vrchatRoleOpt)
        {
            String vrcId = VrcIds.getAsString_user(event.getOption("vrchat-user"));
            if (vrchatUser == null)
            {
                hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
                return;
            }
            
            if (discordUser != null)
            {
                ScarletDiscordCommands.this.discord.scarlet.data.linkIdToSnowflake(vrcId, discordUser.getId());
                LOG.info(String.format("Linking VRChat user %s (%s) to Discord user %s (<@%s>)", vrchatUser.getDisplayName(), vrcId, discordUser.getEffectiveName(), discordUser.getId()));
                hook.sendMessageFormat("Associating %s with VRChat user [%s](https://vrchat.com/home/user/%s)", discordUser.getEffectiveName(), vrchatUser.getDisplayName(), vrcId).setEphemeral(true).queue();
            }
            
            if (!this._addId(vrcId))
            {
                hook.sendMessageFormat("VRChat user [%s](https://vrchat.com/home/user/%s) is already on the "+this._infoName()+" list", vrchatUser.getDisplayName(), vrcId).setEphemeral(true).queue();
            }
            else
            {
                LOG.info(String.format("Adding VRChat user %s (%s) to the "+this._infoName()+" list", vrchatUser.getDisplayName(), vrcId));
                hook.sendMessageFormat("Adding VRChat user [%s](https://vrchat.com/home/user/%s) to the "+this._infoName()+" list", vrchatUser.getDisplayName(), vrcId).setEphemeral(true).queue();
            }
            
            if (vrchatRoleOpt == null)
                return;
            GroupLimitedMember glm = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroupMembership(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrcId);
            if (glm != null)
            {
                List<String> roleIds = glm.getRoleIds();
                if (roleIds != null && roleIds.contains(vrchatRoleOpt.getId()))
                {
                    hook.sendMessageFormat("VRChat user [%s](https://vrchat.com/home/user/%s) is already has the role [%s](https://vrchat.com/home/group/%s/settings/roles/%s)", vrchatUser.getDisplayName(), vrcId, vrchatRoleOpt.getName(), ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatRoleOpt.getId()).setEphemeral(true).queue();
                    return;
                }
            }
            
            ScarletDiscordCommands.this.discord.scarlet.vrc.addGroupRole(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrcId, vrchatRoleOpt.getId());
            hook.sendMessageFormat("Adding the role [%s](https://vrchat.com/home/group/%s/settings/roles/%s) to [%s](https://vrchat.com/home/user/%s)", vrchatRoleOpt.getName(), ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatRoleOpt.getId(), vrchatUser.getDisplayName(), vrcId).setEphemeral(true).queue();
        }
        protected void _remove(SlashCommandInteractionEvent event, InteractionHook hook, io.github.vrchatapi.model.User vrchatUser, io.github.vrchatapi.model.GroupRole vrchatRoleOpt)
        {
            String vrcId = VrcIds.getAsString_user(event.getOption("vrchat-user"));
            String prefix = vrchatUser != null ? "" : ("No VRChat user found with id %s"+vrcId+"\n");
            String displayName = vrchatUser != null ? vrchatUser.getDisplayName() : vrcId;
            
            if (!this._removeId(vrcId))
            {
                hook.sendMessageFormat("%sVRChat user [%s](https://vrchat.com/home/user/%s) is not on the "+this._infoName()+" list", prefix, displayName, vrcId).setEphemeral(true).queue();
            }
            else
            {
                LOG.info(String.format("Removing VRChat user %s (%s) from the "+this._infoName()+" list", displayName, vrcId));
                hook.sendMessageFormat("Removing VRChat user [%s](https://vrchat.com/home/user/%s) from the "+this._infoName()+" list", displayName, vrcId).setEphemeral(true).queue();
            }

            if (vrchatRoleOpt == null)
                return;
            GroupLimitedMember glm = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroupMembership(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrcId);
            if (glm != null)
            {
                List<String> roleIds = glm.getRoleIds();
                if (roleIds != null && !roleIds.contains(vrchatRoleOpt.getId()))
                {
                    hook.sendMessageFormat("VRChat user [%s](https://vrchat.com/home/user/%s) is already lacks the role [%s](https://vrchat.com/home/group/%s/settings/roles/%s)", vrchatUser.getDisplayName(), vrcId, vrchatRoleOpt.getName(), ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatRoleOpt.getId()).setEphemeral(true).queue();
                    return;
                }
            }
            
            ScarletDiscordCommands.this.discord.scarlet.vrc.removeGroupRole(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrcId, vrchatRoleOpt.getId());
            hook.sendMessageFormat("Removing the role [%s](https://vrchat.com/home/group/%s/settings/roles/%s) from [%s](https://vrchat.com/home/user/%s)", vrchatRoleOpt.getName(), ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatRoleOpt.getId(), vrchatUser.getDisplayName(), vrcId).setEphemeral(true).queue();
        }
    }

    // secret-staff-list

    @SlashCmd("secret-staff-list")
    @Desc("Configures the secret staff list")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class SecretStaffList extends _StaffList
    {
        @Override
        protected String _infoName()
        {
            return "secret staff";
        }
        @Override
        protected String[] _getIds()
        {
            return ScarletDiscordCommands.this.discord.scarlet.secretStaffList.getSecretStaffIds();
        }
        @Override
        protected boolean _addId(String vrcId)
        {
            return ScarletDiscordCommands.this.discord.scarlet.secretStaffList.addSecretStaffId(vrcId);
        }
        @Override
        protected boolean _removeId(String vrcId)
        {
            return ScarletDiscordCommands.this.discord.scarlet.secretStaffList.removeSecretStaffId(vrcId);
        }
        @SlashCmd("list")
        @Desc("Lists all secret staff users")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("entries-per-page") int entriesPerPage)
        {
            this._list(event, hook, entriesPerPage);
        }
        @SlashCmd("add")
        @Desc("Adds a user to the secret staff list")
        public void add(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser, @SlashOpt("discord-user") Member discordUser, @SlashOpt("_vrchatRoleOpt") io.github.vrchatapi.model.GroupRole vrchatRoleOpt)
        {
            this._add(event, hook, vrchatUser, discordUser, vrchatRoleOpt);
        }
        @SlashCmd("delete")
        @Desc("Removes a user from the secret staff list")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser, @SlashOpt("_vrchatRoleOpt") io.github.vrchatapi.model.GroupRole vrchatRoleOpt)
        {
            this._remove(event, hook, vrchatUser, vrchatRoleOpt);
        }
    }

    // vrchat-user-info

    @SlashCmd("vrchat-user-info")
    @Desc("Lists internal and audit information for a specific VRChat user")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void vrchatUserInfo(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser) throws Exception
    {
        String vrcId = VrcIds.getAsString_user(event.getOption("vrchat-user"));
        if (vrchatUser == null)
        {
            hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
            return;
        }

        ScarletData.UserMetadata userMeta = this.discord.scarlet.data.userMetadata(vrcId);
        if (userMeta == null)
        {
            hook.sendMessageFormat("No VRChat user metadata found for [%s](https://vrchat.com/home/user/%s)", vrchatUser.getDisplayName(), vrcId).setEphemeral(true).queue();
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("VRChat user metadata for [").append(vrchatUser.getDisplayName()).append("](<https://vrchat.com/home/user/").append(vrcId).append(">):");
        
        if (userMeta.auditEntryIds != null && userMeta.auditEntryIds.length > 0)
        {
            sb.append("\n### Moderation events:");
            for (String auditEntryId : userMeta.auditEntryIds)
            {
                if (auditEntryId != null)
                {
                    sb.append("\n`").append(auditEntryId).append("`");
                    ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
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
                    
                    ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(evidenceSubmission.auditEntryId);
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

        this.discord.interactions.new Pagination(event.getId(), sb).queue(hook);
    }

    // vrchat-user-ban

    @SlashCmd("vrchat-user-ban")
    @Desc("Ban a specific VRChat user")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void vrchatUserBan(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser, @SlashOpt("tag-immediately") boolean tagImmediately) throws Exception
    {
        String vrcTargetId = VrcIds.getAsString_user(event.getOption("vrchat-user"));
        if (vrchatUser == null)
        {
            hook.sendMessageFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
            return;
        }
        
        String vrcActorId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
        if (vrcActorId == null)
        {
            hook.sendMessage(this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
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
        }
        else if (tagImmediately)
        {
            if (!this.discord.scarlet.pendingModActions.addBanInfo(vrcTargetId))
            {
                hook.sendMessage("This VRChat user currently has automated/assisted moderation pending, please retry later").setEphemeral(true).queue();
                return;
            }
            
            List<ScarletModerationTags.Tag> tags = this.discord.scarlet.moderationTags.getTags();
            
            if (tags == null || tags.isEmpty())
            {
                hook.sendMessage("No moderation tags!").setEphemeral(true).queue();
            }
            StringSelectMenu.Builder builder = StringSelectMenu
                .create("immediate-ban-select-tags:"+vrcTargetId)
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
                    builder.addOption(label, MiscUtils.maybeEllipsis(100, value));
                else
                    builder.addOption(label, MiscUtils.maybeEllipsis(100, value), MiscUtils.maybeEllipsis(50, desc));
            }
            
            hook.sendMessageComponents(
                    ActionRow.of(builder.build()),
                    ActionRow.of(Button.primary("immediate-ban-edit-desc:"+vrcTargetId, "Set description"),
                                 Button.secondary("immediate-ban-cancel:"+vrcTargetId, "Cancel"),
                                 Button.danger("immediate-ban-confirm:"+vrcTargetId, "Ban"))
                )
                .setEphemeral(true)
                .queue();
        }
        else if (this.discord.scarlet.pendingModActions.addPending(GroupAuditType.USER_BAN, vrcTargetId, vrcActorId) != null)
        {
            hook.sendMessage("This VRChat user currently has automated/assisted moderation pending, please retry later").setEphemeral(true).queue();
        }
        else if (!this.discord.scarlet.vrc.banFromGroup(vrcTargetId))
        {
            this.discord.scarlet.pendingModActions.pollPending(GroupAuditType.USER_BAN, vrcTargetId);
            hook.sendMessageFormat("Failed to ban %s", vrchatUser.getDisplayName()).setEphemeral(true).queue();
        }
        else
        {
            hook.sendMessageFormat("Banned %s", vrchatUser.getDisplayName()).setEphemeral(false).queue();
        }
        
    }

    // vrchat-user-ban-multi

    @SlashCmd("vrchat-user-ban-multi")
    @Desc("Ban several VRChat users")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void vrchatUserBanMulti(SlashCommandInteractionEvent event) throws Exception
    {
        String vrcActorId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
        if (vrcActorId == null)
        {
            event.reply(this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
            return;
        }
        
        if (!this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_bans_manage, event.getMember()))
        {
            if (!this.discord.checkMemberHasScarletPermission(ScarletPermission.GROUPEX_BANS_MANAGE, event.getMember(), false))
            {
                event.reply("You do not have permission to ban users.\n||(Your admin can enable this by giving your associated VRChat user ban management permissions in the group or with the command `/scarlet-discord-permissions type:Other name:groupex-bans-manage value:Allow`)||").setEphemeral(true).queue();
                return;
            }
        }
        
        event.replyModal(Modal.create("vrchat-user-ban-multi", "Ban Multiple VRChat Users")
            .addActionRow(TextInput.create("target-ids", "Target VRChat User IDs", TextInputStyle.PARAGRAPH)
                .setPlaceholder("User IDs separated by something that isn't 0-9, a-z, A-Z, '-', or '_' (e.g., newline, space, comma)")
                .setRequiredRange(10, -1)
                .build())
            .build())
        .queue();
    }

    // vrchat-user-unban

    @SlashCmd("vrchat-user-unban")
    @Desc("Unban a specific VRChat user")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void vrchatUserUnban(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser) throws Exception
    {
        String vrcTargetId = VrcIds.getAsString_user(event.getOption("vrchat-user"));
        if (vrchatUser == null)
        {
            hook.sendMessageFormat("No VRChat user found with id %s", vrcTargetId).setEphemeral(true).queue();
            return;
        }
        
        String vrcActorId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
        if (vrcActorId == null)
        {
            hook.sendMessage(this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
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
        }
        else if (this.discord.scarlet.pendingModActions.addPending(GroupAuditType.USER_UNBAN, vrcTargetId, vrcActorId) != null)
        {
            hook.sendMessage("This VRChat user currently has automated/assisted moderation pending, please retry later").setEphemeral(true).queue();
        }
        else if (!this.discord.scarlet.vrc.unbanFromGroup(vrcTargetId))
        {
            this.discord.scarlet.pendingModActions.pollPending(GroupAuditType.USER_UNBAN, vrcTargetId);
            hook.sendMessageFormat("Failed to unban %s", vrchatUser.getDisplayName()).setEphemeral(true).queue();
        }
        else
        {
            hook.sendMessageFormat("Unbanned %s", vrchatUser.getDisplayName()).setEphemeral(false).queue();
        }
        
    }

    // vrchat-user-unban-multi

    @SlashCmd("vrchat-user-unban-multi")
    @Desc("Unban several VRChat users")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void vrchatUserUnbanMulti(SlashCommandInteractionEvent event) throws Exception
    {
        String vrcActorId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
        if (vrcActorId == null)
        {
            event.reply(this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
            return;
        }
        
        if (!this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_bans_manage, event.getMember()))
        {
            if (!this.discord.checkMemberHasScarletPermission(ScarletPermission.GROUPEX_BANS_MANAGE, event.getMember(), false))
            {
                event.reply("You do not have permission to unban users.\n||(Your admin can enable this by giving your associated VRChat user ban management permissions in the group or with the command `/scarlet-discord-permissions type:Other name:groupex-bans-manage value:Allow`)||").setEphemeral(true).queue();
                return;
            }
        }
        
        event.replyModal(Modal.create("vrchat-user-unban-multi", "Unban Multiple VRChat Users")
            .addActionRow(TextInput.create("target-ids", "Target VRChat User IDs", TextInputStyle.PARAGRAPH)
                .setPlaceholder("User IDs separated by something that isn't 0-9, a-z, A-Z, '-', or '_' (e.g., newline, space, comma)")
                .setRequiredRange(10, -1)
                .build())
            .build())
        .queue();
    }

    // vrchat-group

    @SlashCmd("vrchat-group")
    @Desc("Manages groups")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class VrchatGroups
    {
        private io.github.vrchatapi.model.User actor(SlashCommandInteractionEvent event, InteractionHook hook)
        {
            long within1day = System.currentTimeMillis() - 86400_000L;
            
            String vrcActorId = ScarletDiscordCommands.this.discord.scarlet.data.globalMetadata_getSnowflakeId(event.getUser().getId());
            if (vrcActorId == null)
            {
                hook.sendMessage(ScarletDiscordCommands.this.discord.linkedIdsReply(event.getUser())).setEphemeral(true).queue();
                return null;
            }
            io.github.vrchatapi.model.User sc = ScarletDiscordCommands.this.discord.scarlet.vrc.getUser(vrcActorId, within1day);
            if (sc == null)
            {
                hook.sendMessageFormat("No VRChat user found with id %s", vrcActorId).setEphemeral(true).queue();
                return null;
            }
            return sc;
        }
        @SlashCmd("open-instance")
        @Desc("Opens an instance")
        public void openInstance(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-world") io.github.vrchatapi.model.World vrchatWorld) throws Exception
        {
            io.github.vrchatapi.model.User vrcActor = this.actor(event, hook);
            if (vrcActor == null)
                return;
            String groupId = ScarletDiscordCommands.this.discord.scarlet.vrc.groupId;

            if (vrchatWorld == null)
            {
                hook.sendMessageFormat("No VRChat world found with id %s", event.getOption("vrchat-world", OptionMapping::getAsString)).setEphemeral(true).queue();
                return;
            }
            
            String ictoken;
            do ictoken = UUID.randomUUID().toString();
            while (ScarletDiscordCommands.this.discord.instanceCreation.putIfAbsent(ictoken, ScarletDiscordCommands.this.discord.new InstanceCreation(ictoken, vrchatWorld.getId(), groupId)) != null);
            
            List<GroupRole> roles = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroupRoles(groupId);
            
            hook.sendMessageFormat("Create a new [%s](https://vrchat.com/home/world/%s) instance:", vrchatWorld.getName(), vrchatWorld.getId())
                .addActionRow(StringSelectMenu.create("new-instance-access-type:"+ictoken)
                    .addOption("Group Public", "public")
                    .addOption("Group Plus", "plus")
                    .addOption("Group Members", "members")
                    .setDefaultValues("public")
                    .build())
                .addActionRow(StringSelectMenu.create("new-instance-roles:"+ictoken)
                    .setPlaceholder("Select roles (Group Members)")
                    .addOptions(roles.stream().map($ -> SelectOption.of($.getName(), $.getId())).limit(25L).toArray(SelectOption[]::new))
                    .setRequiredRange(1, Math.min(25, roles.size()))
                    .build())
                .addActionRow(StringSelectMenu.create("new-instance-region:"+ictoken)
                    .addOption("Region: US", "us")
                    .addOption("Region: US East", "use")
                    .addOption("Region: Europe", "eu")
                    .addOption("Region: Japan", "jp")
                    .setDefaultValues("us")
                    .build())
                .addActionRow(StringSelectMenu.create("new-instance-flags:"+ictoken)
                    .setPlaceholder("Options & Content Settings")
                    .addOption("Join queue (users wait to connect when full)", "queueEnabled")
                    .addOption("Hard close (kick connected users on close)", "hardClose")
                    .addOption("Age gate (Age Verified 18+ users only)", "ageGate")
//                    .addOption("Player persistence (data persists between instances)", "playerPersistenceEnabled")
//                    .addOption("Instance persistence (state persists when empty)", "instancePersistenceEnabled")
                    .addOption("Content: drones", "contentSettings.drones")
                    .addOption("Content: emoji", "contentSettings.emoji")
                    .addOption("Content: pedestals", "contentSettings.pedestals")
                    .addOption("Content: prints", "contentSettings.prints")
                    .addOption("Content: stickers", "contentSettings.stickers")
                    .setRequiredRange(0, 8)
                    .setDefaultValues("queueEnabled", "contentSettings.drones", "contentSettings.emoji", "contentSettings.pedestals", "contentSettings.prints", "contentSettings.stickers")
                    .build())
                .addActionRow(Button.success("new-instance-create:"+ictoken, "Create"),
                              Button.danger("new-instance-cancel:"+ictoken, "Cancel"),
                              Button.secondary("new-instance-modal:"+ictoken, "Additional options..."))
                .setEphemeral(true)
                .queue();
        }
        public final SlashOption<String> _vrchatLocation = SlashOption.ofString("vrchat-location", "The instance to close", true, null, VrcIds::resolveLocation, false, null);
        public final SlashOption<Boolean> _closeHard = SlashOption.ofBool("close-hard", "Whether to kick users currently in the instance", false, null);
        public final SlashOption<Integer> _closeInMinutes = SlashOption.ofInt("close-in-minutes", "The number of minutes to wait before closing the instance", false, null).with($->$.setRequiredRange(1L, 600L));
        @SlashCmd("close-instance")
        @Desc("Closes an instance")
        public void closeInstance(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-location") String vrchatLocation, @SlashOpt("close-hard") Boolean closeHard, @SlashOpt("close-in-minutes") Integer closeInMinutes) throws Exception
        {
            io.github.vrchatapi.model.User vrcActor = this.actor(event, hook);
            if (vrcActor == null)
                return;
            
            if (!ScarletDiscordCommands.this.discord.checkMemberHasVRChatPermission(GroupPermissions.group_instance_manage, event.getMember()))
            {
                hook.sendMessage("You do not have permission to close instances").setEphemeral(true).queue();
                return;
            }
            
            Location closeLocationModel = Location.of(vrchatLocation);
            OffsetDateTime closeAt = closeInMinutes == null ? null : OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(closeInMinutes.longValue());
            
            InstancesApi instances = new InstancesApi(ScarletDiscordCommands.this.discord.scarlet.vrc.client);
            Instance instance = instances.closeInstance(closeLocationModel.world, closeLocationModel.instance, closeHard, closeAt);
            
            LOG.info(String.format("%s (%s) closed an instance: %s", vrcActor.getDisplayName(), vrcActor.getId(), instance.getId()));
            hook.sendMessageFormat("Closed instance `%s`", instance.getId()).setEphemeral(true).queue();
        }
        @SlashCmd("add-role")
        @Desc("Adds a VRChat Group Role")
        public void addRole(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser, @SlashOpt("vrchat-role") io.github.vrchatapi.model.GroupRole vrchatRole) throws Exception
        {
            GroupLimitedMember glm = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroupMembership(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatUser.getId());
            if (glm != null)
            {
                List<String> roleIds = glm.getRoleIds();
                if (roleIds != null && roleIds.contains(vrchatRole.getId()))
                {
                    hook.sendMessageFormat("VRChat user [%s](https://vrchat.com/home/user/%s) is already has the role [%s](https://vrchat.com/home/group/%s/settings/roles/%s)", vrchatUser.getDisplayName(), vrchatUser.getId(), vrchatRole.getName(), ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatRole.getId()).setEphemeral(true).queue();
                    return;
                }
            }
            
            ScarletDiscordCommands.this.discord.scarlet.vrc.addGroupRole(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatUser.getId(), vrchatRole.getId());
            hook.sendMessageFormat("Adding the role [%s](https://vrchat.com/home/group/%s/settings/roles/%s) to [%s](https://vrchat.com/home/user/%s)", vrchatRole.getName(), ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatRole.getId(), vrchatUser.getDisplayName(), vrchatUser.getId()).setEphemeral(true).queue();
        }
        @SlashCmd("remove-role")
        @Desc("Removes a VRChat Group Role")
        public void removeRole(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser, @SlashOpt("vrchat-role") io.github.vrchatapi.model.GroupRole vrchatRole) throws Exception
        {
            GroupLimitedMember glm = ScarletDiscordCommands.this.discord.scarlet.vrc.getGroupMembership(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatUser.getId());
            if (glm != null)
            {
                List<String> roleIds = glm.getRoleIds();
                if (roleIds != null && !roleIds.contains(vrchatRole.getId()))
                {
                    hook.sendMessageFormat("VRChat user [%s](https://vrchat.com/home/user/%s) is already lacks the role [%s](https://vrchat.com/home/group/%s/settings/roles/%s)", vrchatUser.getDisplayName(), vrchatUser.getId(), vrchatRole.getName(), ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatRole.getId()).setEphemeral(true).queue();
                    return;
                }
            }
            
            ScarletDiscordCommands.this.discord.scarlet.vrc.removeGroupRole(ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatUser.getId(), vrchatRole.getId());
            hook.sendMessageFormat("Removing the role [%s](https://vrchat.com/home/group/%s/settings/roles/%s) from [%s](https://vrchat.com/home/user/%s)", vrchatRole.getName(), ScarletDiscordCommands.this.discord.scarlet.vrc.groupId, vrchatRole.getId(), vrchatUser.getDisplayName(), vrchatUser.getId()).setEphemeral(true).queue();
        }
    }

    // discord-user-info

    @SlashCmd("discord-user-info")
    @Desc("Lists internal information for a specific Discord user")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void discordUserInfo(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("discord-user") Member discordUser) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        
        String vrcId = this.discord.scarlet.data.globalMetadata_getSnowflakeId(discordUser.getId());
        if (vrcId != null)
        {
            long within1day = System.currentTimeMillis() - 86400_000L;
            User sc = this.discord.scarlet.vrc.getUser(vrcId, within1day);
            if (sc != null && !this.discord.shouldRedact(vrcId, event.getMember().getId()))
            {
                sb.append(String.format("### Linked VRChat user:\n[%s](https://vrchat.com/home/user/%s) `%s`\n", sc.getDisplayName(), vrcId, vrcId));
            }
        }
        
        sb.append("### Scarlet permissions:");
        for (ScarletPermission permission : ScarletPermission.values())
        {
            UniqueStrings roleSfs = this.discord.scarletPermission2roleSf.get(permission.id);
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

        this.discord.interactions.new Pagination(event.getId(), sb).queue(hook);
    }

    // query-target-history

    @SlashCmd("query-target-history")
    @Desc("Queries audit events targeting a specific VRChat user")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void queryTargetHistory(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser, @SlashOpt("days-back") int daysBack) throws Exception
    {
        String vrcId = VrcIds.getAsString_user(event.getOption("vrchat-user"));
        if (vrchatUser == null)
        {
            hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
            return;
        }
        
        List<GroupAuditLogEntry> entries = this.discord.scarlet.vrc.auditQueryTargeting(vrcId, daysBack);
        if (entries == null)
        {
            hook.sendMessageFormat("Error querying audit target history for [%s](<https://vrchat.com/home/user/%s>) (%s)", vrchatUser.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
            return;
        }
        else if (entries.isEmpty())
        {
            hook.sendMessageFormat("No audit target history for [%s](<https://vrchat.com/home/user/%s>) (%s)", vrchatUser.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("VRChat audit target history for [").append(vrchatUser.getDisplayName()).append("](<https://vrchat.com/home/user/").append(vrcId).append(">):");
        
        ScarletData.UserMetadata userMeta = this.discord.scarlet.data.userMetadata(vrcId);
        if (userMeta != null && userMeta.userSnowflake != null && !this.discord.shouldRedact(vrcId, event.getMember().getId()))
        {
            sb.append("\n### Linked Discord id:\n`").append(userMeta.userSnowflake).append("`");
        }
        
        sb.append("\n### Audit target events:");
        for (GroupAuditLogEntry entry : entries)
        {
            sb.append("\n`").append(entry.getId()).append("`").append(" <t:").append(Long.toUnsignedString(entry.getCreatedAt().toEpochSecond())).append(":f>: ");
            ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(entry.getId());
            if (this.discord.shouldRedact(entry.getActorId(), event.getMember().getId()))
            {
                String entryDescription = entry.getDescription();
                if (entryDescription.contains(entry.getActorDisplayName()))
                {
                    entryDescription = entryDescription.replace(entry.getActorDisplayName(), this.discord.scarlet.vrc.currentUser.getDisplayName());
                }
                else
                {
                    entryDescription = String.format("User %s was banned by %s.", vrchatUser.getDisplayName(), this.discord.scarlet.vrc.currentUser.getDisplayName());
                }
                sb.append(entryDescription);
            }
            else if (auditEntryMeta != null && auditEntryMeta.hasMessage())
                sb.append("[").append(entry.getDescription()).append("](").append(auditEntryMeta.getMessageUrl()).append(")");
            else
                sb.append(entry.getDescription());
        }

        this.discord.interactions.new Pagination(event.getId(), sb).queue(hook);
    }

    // query-actor-history

    @SlashCmd("query-actor-history")
    @Desc("Queries audit events actored by a specific VRChat user")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void queryActorHistory(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser, @SlashOpt("days-back") int daysBack) throws Exception
    {
        String vrcId = VrcIds.getAsString_user(event.getOption("vrchat-user"));
        if (vrchatUser == null)
        {
            hook.sendMessageFormat("No VRChat user found with id %s", vrcId).setEphemeral(true).queue();
            return;
        }
        
        List<GroupAuditLogEntry> entries = this.discord.scarlet.vrc.auditQueryActored(vrcId, daysBack);
        
        if (entries == null)
        {
            hook.sendMessageFormat("Error querying audit actor history for [%s](<https://vrchat.com/home/user/%s>) (%s)", vrchatUser.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
            return;
        }
        else if (entries.isEmpty()
            // Check is here to avoid side chain vulnerabilities
            || this.discord.shouldRedact(vrcId, event.getMember().getId()))
        {
            hook.sendMessageFormat("No audit actor history for [%s](<https://vrchat.com/home/user/%s>) (%s)", vrchatUser.getDisplayName(), vrcId, vrcId).setEphemeral(true).queue();
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("VRChat audit actor history for [").append(vrchatUser.getDisplayName()).append("](<https://vrchat.com/home/user/").append(vrcId).append(">):");
        
        ScarletData.UserMetadata userMeta = this.discord.scarlet.data.userMetadata(vrcId);
        if (userMeta != null && userMeta.userSnowflake != null)
        {
            sb.append("\n### Linked Discord id:\n`").append(userMeta.userSnowflake).append("`");
        }
        
        sb.append("\n### Audit actor events:");
        for (GroupAuditLogEntry entry : entries)
        {
            sb.append("\n`").append(entry.getId()).append("`").append(" <t:").append(Long.toUnsignedString(entry.getCreatedAt().toEpochSecond())).append(":f>: ");
            ScarletData.AuditEntryMetadata auditEntryMeta = this.discord.scarlet.data.auditEntryMetadata(entry.getId());
            if (auditEntryMeta != null && auditEntryMeta.hasMessage())
                sb.append("[").append(entry.getDescription()).append("](").append(auditEntryMeta.getMessageUrl()).append(")");
            else
                sb.append(entry.getDescription());
        }

        this.discord.interactions.new Pagination(event.getId(), sb).queue(hook);
    }

    // actor-moderation-summary

    @SlashCmd("actor-moderation-summary")
    @Desc("Generates a summary of certain moderation actions by a specific user")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void actorModerationSummary(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("vrchat-user") io.github.vrchatapi.model.User vrchatUser)
    {
        String vrcId = vrchatUser.getId();
        OffsetDateTime to = OffsetDateTime.now(),
                       from = to.minusYears(10L);
        
        Integer kicks = this.discord.scarlet.vrc.auditQueryCount(from, to, vrcId, "group.instance.kick", null),
                warns = this.discord.scarlet.vrc.auditQueryCount(from, to, vrcId, "group.instance.warn", null),
                bans = this.discord.scarlet.vrc.auditQueryCount(from, to, vrcId, "group.user.ban", null);
        
        StringBuilder sb = new StringBuilder();
        sb.append("VRChat audit actor summary for [").append(vrchatUser.getDisplayName()).append("](<https://vrchat.com/home/user/").append(vrcId).append(">):");
        
        ScarletData.UserMetadata userMeta = this.discord.scarlet.data.userMetadata(vrcId);
        if (userMeta != null && userMeta.userSnowflake != null)
        {
            sb.append("\n### Linked Discord id:\n`").append(userMeta.userSnowflake).append("`");
        }
        
        sb.append("\nKicks: ").append(kicks == null ? "??? (query failed)" : kicks.toString())
            .append("\nWarns: ").append(warns == null ? "??? (query failed)" : warns.toString())
            .append("\nBans: ").append(bans == null ? "??? (query failed)" : bans.toString());
        
        hook.sendMessage(sb.toString()).setEphemeral(true).queue();
    }

    // moderation-summary

    @SlashCmd("moderation-summary")
    @Desc("Generates a summary of moderation actions")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void moderationSummary(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("hours-back") int hoursBack)
    {
        this.discord.emitModSummary(this.discord.scarlet, OffsetDateTime.now(ZoneOffset.UTC), hoursBack, hook::sendMessageEmbeds);
    }

    // outstanding-moderation

    @SlashCmd("outstanding-moderation")
    @Desc("Generates a list of outstanding moderation actions")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void outstandingModeration(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("hours-back") int hoursBack)
    {
        Message message = this.discord.emitOutstandingMod(this.discord.scarlet, OffsetDateTime.now(ZoneOffset.UTC), hoursBack, hook::sendMessageEmbeds);
        if (message == null)
            hook.sendMessage("No moderation events watched").queue();
    }

    // submit-attachments

    @MsgCmd("submit-attachments")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void submitAttachments(MessageContextInteractionEvent event)
    {
        Message message = event.getTarget();
        
        event.reply("Select submission type")
            .addActionRow(
                Button.primary("submit-evidence:"+message.getId(), "Submit moderation evidence"),
                Button.primary("import-watched-groups:"+message.getId(), "Import watched groups"))
            .setEphemeral(true)
            .queue();
    }

    public final SlashOption<Message.Attachment>
        _evidenceSubmission = SlashOption.ofAttachment("evidence-submission", "The submitted file", true),
        _evidenceSubmission2 = SlashOption.ofAttachment("evidence-submission-2", "Another submitted file", false),
        _evidenceSubmission3 = SlashOption.ofAttachment("evidence-submission-3", "Another submitted file", false),
        _evidenceSubmission4 = SlashOption.ofAttachment("evidence-submission-4", "Another submitted file", false),
        _evidenceSubmission5 = SlashOption.ofAttachment("evidence-submission-5", "Another submitted file", false);
    @SlashCmd("submit-evidence")
    @Desc("Submit attachments for evidence")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void submitEvidence(SlashCommandInteractionEvent event, InteractionHook hook,
        @SlashOpt("evidence-submission") Message.Attachment evidenceSubmission,
        @SlashOpt("evidence-submission-2") Message.Attachment evidenceSubmission2,
        @SlashOpt("evidence-submission-3") Message.Attachment evidenceSubmission3,
        @SlashOpt("evidence-submission-4") Message.Attachment evidenceSubmission4,
        @SlashOpt("evidence-submission-5") Message.Attachment evidenceSubmission5
    )
    {
        List<Message.Attachment> submissions = new ArrayList<>();
                                         submissions.add(evidenceSubmission);
        if (evidenceSubmission2 != null) submissions.add(evidenceSubmission2);
        if (evidenceSubmission3 != null) submissions.add(evidenceSubmission3);
        if (evidenceSubmission4 != null) submissions.add(evidenceSubmission4);
        if (evidenceSubmission5 != null) submissions.add(evidenceSubmission5);
        this.discord.discordUI.submitEvidence(event, hook, () -> submissions, "You must run this command in the relevant thread of an audit event message.");
    }

    // fix-audit-thread

    @MsgCmd("fix-audit-thread")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void fixAuditThread(MessageContextInteractionEvent event)
    {
        Message message = event.getTarget();
        String auditEntryId = message
            .getEmbeds()
            .stream()
            .map(MessageEmbed::getFooter)
            .filter(Objects::nonNull)
            .map(MessageEmbed.Footer::getText)
            .map(VrcIds.id_groupaudit::matcher)
            .filter(Matcher::find)
            .map(Matcher::group)
            .findFirst()
            .orElse(null)
            ;
        if (auditEntryId == null)
        {
            event.reply("Message does not appear to be an audit entry")
                .setEphemeral(true)
                .queue($ -> $.deleteOriginal().queueAfter(5_000L, TimeUnit.MILLISECONDS));
            return;
        }
        
        ScarletData.AuditEntryMetadata entryMeta = this.discord.scarlet.data.auditEntryMetadata(auditEntryId);
        if (entryMeta == null)
        {
            event.replyFormat("Failed to find data for entry `%s`", auditEntryId)
                .setEphemeral(true)
                .queue($ -> $.deleteOriginal().queueAfter(5_000L, TimeUnit.MILLISECONDS));
            return;
        }
        
        String actorId = entryMeta.hasAuxActor() ? entryMeta.auxActorId : entryMeta.entry.getActorId();
        
        event.replyFormat("Attempting to fix entry `%s`", auditEntryId)
            .setEphemeral(true)
            .queue($ -> $.deleteOriginal().queueAfter(5_000L, TimeUnit.MILLISECONDS));
        this.discord.scarlet.exec.execute(() -> this.discord.emitUserModeration_thread(this.discord.scarlet, entryMeta, actorId, message));
    }

    // config-info

    @SlashCmd("config-info")
    @Desc("Shows information about the current configuration")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void configInfo(SlashCommandInteractionEvent event, InteractionHook hook) throws Exception
    {//event.deferReply().queue();
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration Information:");
        
        String lastQueryEpoch = Long.toUnsignedString(this.discord.scarlet.settings.lastAuditQuery.getOrSupply().toEpochSecond()),
             lastRefreshEpoch = Long.toUnsignedString(this.discord.scarlet.settings.lastAuthRefresh.getOrSupply().toEpochSecond());
        
        sb.append("\n### Last Audit Query:")
            .append("\n<t:").append(lastQueryEpoch).append(":F>")
            .append(" (<t:").append(lastQueryEpoch).append(":R>)");

        sb.append("\n### Last Auth Refresh:")
            .append("\n<t:").append(lastRefreshEpoch).append(":F>")
            .append(" (<t:").append(lastRefreshEpoch).append(":R>)");
        
        OffsetDateTime nms = this.discord.scarlet.settings.nextModSummary.getOrNull();
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
            String channelSf = this.discord.auditType2channelSf.get(auditType.id);
            sb.append("\n")
                .append(auditType.title)
                .append(" (")
                .append(auditType.id)
                .append("): ")
                .append(channelSf == null ? "unassigned" : ("<#"+channelSf+">"));
            UniqueStrings auxWhs = this.discord.auditType2scarletAuxWh.get(auditType.id);
            if (auxWhs != null && !auxWhs.isEmpty())
            {
                sb.append(auxWhs.stream().collect(Collectors.joining(", ", " (Auxiliary webhooks: ", ")")));
            }
        }
        
        sb.append("\n### Extended auditing Channels:");
        for (GroupAuditTypeEx auditTypeEx : GroupAuditTypeEx.values())
        {
            String channelSf = this.discord.auditExType2channelSf.get(auditTypeEx.id);
            sb.append("\n")
                .append(auditTypeEx.title)
                .append(" (")
                .append(auditTypeEx.id)
                .append("): ")
                .append(channelSf == null ? "unassigned" : ("<#"+channelSf+">"));
        }
        List<String> pages = MiscUtils.paginateOnLines(sb, 2000);
        for (String page : pages)
        {
//            event.getHook().sendMessage(page).setEphemeral(false).queue();
            hook.sendMessage(page).setEphemeral(false).queue();
        }
    }

    // config-set

    @SlashCmd("config-set")
    @Desc("Configures miscellaneous settings")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class ConfigSet
    {
        public final SlashOption<ZoneId> _timeZoneId = SlashOption.ofString("time-zone-id", "The time zone", true, null, ZoneId::of, true, new SlashOptionStrings(ZoneId.getAvailableZoneIds().stream().sorted(), true));
        public final SlashOption<String> _timeOfDay = SlashOption.ofString("time-of-day", "The time of day", true, null, this::_timeOfDay);
        void _timeOfDay(CommandAutoCompleteInteractionEvent event)
        {
            String value = event.getFocusedOption().getValue().trim();
            if (value.isEmpty())
            {
                event.replyChoiceStrings(IntStream.range(0, 24).mapToObj($ -> String.format("%02d:00", $)).toArray(String[]::new)).queue();
                return;
            }
            Matcher m = Pattern.compile("(?<h>\\d\\d)(:(?<m>\\d\\d)?)?").matcher(value);
            if (m.matches())
            {
                String h = m.group("h");
                List<String> strings = IntStream.range(0, 12).mapToObj($ -> String.format("%s:%02d", h, $*5)).collect(Collectors.toList());
                if (!strings.contains(value))
                    strings.add(0, value);
                event.replyChoiceStrings(strings).queue();
                return;
            }
            event.replyChoiceStrings().queue();
        }
        OffsetDateTime nextTimeOfDay(SlashCommandInteractionEvent event, @SlashOpt("time-zone-id") ZoneId timeZoneId, @SlashOpt("time-of-day") String timeOfDay)
        {

            if (timeZoneId == null)
                timeZoneId = ZoneOffset.of(event.getOption("time-zone-id", OptionMapping::getAsString));
            if (timeZoneId == null)
            {
                event.replyFormat("Invalid time zone/offset `%s`", event.getOption("time-zone-id", OptionMapping::getAsString)).queue();
                return null;
            }
            LocalTime time;
            try
            {
                time = LocalTime.parse(timeOfDay);
            }
            catch (DateTimeParseException dtpex)
            {
                event.replyFormat("Invalid time of day `%s`", timeOfDay).queue();
                return null;
            }
            long es = ZonedDateTime.now(timeZoneId).withHour(0).withMinute(0).withSecond(0).withNano(0).plusSeconds(time.toSecondOfDay()).toEpochSecond();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC),
                           offset = OffsetDateTime.ofInstant(Instant.ofEpochSecond(es), ZoneOffset.UTC);
            while (offset.isBefore(now))
                offset = offset.plusHours(24L);
            return offset;
        }
        @SlashCmd("moderation-summary")
        @Desc("Moderation summary settings")
        public class ModSummary
        {
            @SlashCmd("time-of-day")
            @Desc("Set what time of day to generate a summary of moderation activity")
            public void modSummaryTimeOfDay(SlashCommandInteractionEvent event, @SlashOpt("time-zone-id") ZoneId timeZoneId, @SlashOpt("time-of-day") String timeOfDay) throws Exception
            {
                OffsetDateTime offset = nextTimeOfDay(event, timeZoneId, timeOfDay);
                ScarletDiscordCommands.this.discord.scarlet.settings.nextModSummary.set(offset);
                String epochNext = Long.toUnsignedString(offset.plusHours(24L).toEpochSecond());
                event.replyFormat("Set mod summary generation time: next summary at <t:%s:f>", epochNext).setEphemeral(true).queue();
            }
        }
        @SlashCmd("suggested-moderation")
        @Desc("Suggested moderation settings")
        public class SuggestedMod
        {
            public final SlashOption<Integer> _kickCount = SlashOption.ofInt("kick-count", "The number of instance kicks to trigger suggested moderation", true, 3).with($->$.setRequiredRange(1L, 1000L));
            public final SlashOption<Integer> _periodDays = SlashOption.ofInt("period-days", "The number of days of history for which to consider instance kicks", true, 3).with($->$.setRequiredRange(1L, 1000L));
            @SlashCmd("kick-count")
            @Desc("The kick count")
            public void kickCount(SlashCommandInteractionEvent event, @SlashOpt("kick-count") int kickCount) throws Exception
            {
                ScarletDiscordCommands.this.discord.scarlet.settings.heuristicKickCount.set(kickCount);
                event.replyFormat("Set suggested moderation kick count: %d", kickCount).setEphemeral(true).queue();
            }
            @SlashCmd("period-days")
            @Desc("The period")
            public void periodDays(SlashCommandInteractionEvent event, @SlashOpt("period-days") int periodDays) throws Exception
            {
                ScarletDiscordCommands.this.discord.scarlet.settings.heuristicPeriodDays.set(periodDays);
                event.replyFormat("Set suggested moderation period: %d day%s", periodDays, periodDays==1?"":"s").setEphemeral(true).queue();
            }
        }
        @SlashCmd("outstanding-moderation")
        @Desc("Outstanding moderation settings")
        public class OutstandingMod
        {
            public final SlashOption<Integer> _periodDays = SlashOption.ofInt("period-days", "The number of days of history for which to consider moderation events", true, 3).with($->$.setRequiredRange(1L, 1000L));
            @SlashCmd("period-days")
            @Desc("The period")
            public void periodDays(SlashCommandInteractionEvent event, @SlashOpt("period-days") int periodDays) throws Exception
            {
                ScarletDiscordCommands.this.discord.scarlet.settings.outstandingPeriodDays.set(periodDays);
                event.replyFormat("Set outstanding moderation period: %d day%s", periodDays, periodDays==1?"":"s").setEphemeral(true).queue();
            }
            @SlashCmd("time-of-day")
            @Desc("Set what time of day to generate a summary of outstanding moderation activity")
            public void modSummaryTimeOfDay(SlashCommandInteractionEvent event, @SlashOpt("time-zone-id") ZoneId timeZoneId, @SlashOpt("time-of-day") String timeOfDay) throws Exception
            {
                OffsetDateTime offset = nextTimeOfDay(event, timeZoneId, timeOfDay);
                ScarletDiscordCommands.this.discord.scarlet.settings.nextOutstandingMod.set(offset);
                String epochNext = Long.toUnsignedString(offset.plusHours(24L).toEpochSecond());
                event.replyFormat("Set outstanding mod summary generation time: next summary at <t:%s:f>", epochNext).setEphemeral(true).queue();
            }
        }
    }

    // server-restart

    @SlashCmd("server-restart")
    @Desc("Restarts the Scarlet application")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class ServerRestart
    {
        @SlashCmd("restart-now")
        @Desc("Restarts the application now")
        public void restartNow(SlashCommandInteractionEvent event)
        {
            String feedback;
            if (ScarletDiscordCommands.this.discord.scarlet.restart())
            {
                feedback = "Restarting...";
            }
            else
            {
                feedback = "Shutdown already queued";
            }
            event.reply(feedback).setEphemeral(false).queue();
        }
        public final SlashOption<String> _targetVersion = SlashOption.ofString("target-version", "The version of Scarlet to which to update", true, null, this::_targetVersion);
        void _targetVersion(CommandAutoCompleteInteractionEvent event) {
            DInteractions.SlashOptionStringsUnsanitized.autocomplete(event, ScarletDiscordCommands.this.discord.scarlet.allVersions, true);
        }
        @SlashCmd("update-now")
        @Desc("Updates the application now")
        public void updateNow(SlashCommandInteractionEvent event, @SlashOpt("target-version") String targetVersion)
        {
            String feedback = "Unknown error updating";
            switch (ScarletDiscordCommands.this.discord.scarlet.update(targetVersion))
            {
            case 1: {
                feedback = String.format("Invalid version syntax `%s`", targetVersion);
            } break;
            case 2: {
                feedback = String.format("Nonexistant version `%s`, manually set the contents of `scarlet.version` to this version if you want to actually do this", targetVersion);
            } break;
            case 3: {
                feedback = String.format("Local IO failed for target version `%s`", targetVersion);
            } break;
            case 0: {
                feedback = "Updating...";
            } break;
            case -1: {
                feedback = "Shutdown already queued!";
            } break;
            }
            event.reply(feedback).setEphemeral(true).queue();
            
        }
    }

    // export-log

    public final SlashOption<String> _fileName = SlashOption.ofString("file-name", "The name of the log file", false, null, this::_fileName);
    void _fileName(CommandAutoCompleteInteractionEvent event) { event.replyChoiceStrings(this.discord.scarlet.last25logs).queue(); }
    @SlashCmd("export-log")
    @Desc("Attaches a log file")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void exportLog(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("file-name") String fileName) throws Exception
    {
        if (fileName == null)
        {
            String[] last25logs = this.discord.scarlet.last25logs;
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
    }

    // set-audit-channel

    @SlashCmd("set-audit-channel")
    @Desc("Sets a given text channel as the channel certain audit event types use")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void setAuditChannel(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("audit-event-type") GroupAuditType auditEventType, @SlashOpt("discord-text-channel") Channel textChannel) throws Exception
    {
        if (textChannel == null)
        {
            this.discord.setAuditChannel(auditEventType, null);
            hook.sendMessageFormat("Unassociating VRChat group audit log event type %s (%s) from any channels", auditEventType.title, auditEventType.id).setEphemeral(true).queue();
        }
        else if (!textChannel.getType().isMessage())
        {
            hook.sendMessageFormat("The channel %s doesn't support message sending", textChannel.getName()).setEphemeral(true).queue();
        }
        else
        {
            this.discord.setAuditChannel(auditEventType, textChannel);
            hook.sendMessageFormat("Associating VRChat group audit log event type %s (%s) with channel <#%s>", auditEventType.title, auditEventType.id, textChannel.getId()).setEphemeral(true).queue();
        }
    }

    // set-audit-aux-webhooks

    @SlashCmd("set-audit-aux-webhooks")
    @Desc("Sets the given webhooks as the webhooks certain audit event types use")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void setAuditAuxWebhooks(SlashCommandInteractionEvent event, @SlashOpt("audit-event-type") GroupAuditType auditEventType) throws Exception
    {
        SelectOption[] options = this.discord.scarletAuxWh2webhookUrl
            .keySet()
            .stream()
            .limit(25L)
            .map($ -> SelectOption.of($, $))
            .toArray(SelectOption[]::new);
        UniqueStrings scarletAuxWhs = this.discord.auditType2scarletAuxWh.get(auditEventType.id);
        String[] defaultOptions = scarletAuxWhs == null ? new String[0] : scarletAuxWhs
            .strings()
            .stream()
            .filter(this.discord.scarletAuxWh2webhookUrl::containsKey)
            .toArray(String[]::new);
        
        event.replyComponents(ActionRow.of(StringSelectMenu.create("set-audit-aux-webhooks:"+auditEventType.id)
            .addOptions(options)
            .setDefaultValues(defaultOptions)
            .build())).setEphemeral(true).queue();
    }

    // aux-webhooks

    @SlashCmd("aux-webhooks")
    @Desc("Configures auxiliary webhooks")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class AuxWebhooks
    {
        @SlashCmd("list")
        @Desc("Lists all auxiliary webhooks")
        public void list(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("entries-per-page") int entriesPerPage)
        {
            MessageEmbed[] embeds = ScarletDiscordCommands.this.discord.scarletAuxWh2webhookUrl
                .entrySet()
                .stream()
                .map($ -> new EmbedBuilder()
                    .setTitle($.getKey())
                    .addField("Url", "`"+$.getValue()+"`", false)
                    .build())
                .toArray(MessageEmbed[]::new)
            ;
            
            ScarletDiscordCommands.this.discord.interactions.new Pagination(event.getId(), embeds, entriesPerPage).queue(hook);
        }
        public final SlashOption<String> _auxWebhookIdNA = SlashOption.ofString("aux-webhook-id", "The internal id to give the webhook", true, null);
        public final SlashOption<String> _auxWebhookUrl = SlashOption.ofString("aux-webhook-url", "The url of the webhook", true, null);
        @SlashCmd("add")
        @Desc("Adds an auxiliary webhook")
        public void add(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("_auxWebhookIdNA") String auxWebhookId, @SlashOpt("aux-webhook-url") String auxWebhookUrl)
        {
            String prevWebhookUrl = ScarletDiscordCommands.this.discord.scarletAuxWh2webhookUrl.get(auxWebhookId);
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
            
            ScarletDiscordCommands.this.discord.scarletAuxWh2webhookUrl.put(auxWebhookId, auxWebhookUrl);
            hook.sendMessage("Added auxiliary webhook").setEphemeral(true).queue();
        }
        public final SlashOption<String> _auxWebhookId = SlashOption.ofString("aux-webhook-id", "The internal id of the webhook", true, null, this::_auxWebhookId);
        void _auxWebhookId(CommandAutoCompleteInteractionEvent event) {
            DInteractions.SlashOptionStringsUnsanitized.autocomplete(event, ScarletDiscordCommands.this.discord.scarletAuxWh2webhookUrl.keySet().toArray(new String[0]), true);
        }
        @SlashCmd("remove")
        @Desc("Removes an auxiliary webhook")
        public void remove(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("_auxWebhookId") String auxWebhookId)
        {
            String prevWebhookUrl = ScarletDiscordCommands.this.discord.scarletAuxWh2webhookUrl.remove(auxWebhookId);
            if (prevWebhookUrl != null)
            {
                hook.sendMessage("There is no auxiliary webhook with that id").setEphemeral(true).queue();
                return;
            }
            
            hook.sendMessage("Removed auxiliary webhook").setEphemeral(true).queue();
        }
    }

    // set-audit-ex-channel

    @SlashCmd("set-audit-ex-channel")
    @Desc("Sets a given text channel as the channel certain extended event types use")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void setAuditExChannel(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("audit-ex-event-type") GroupAuditTypeEx auditEventTypeEx, @SlashOpt("discord-text-channel") Channel textChannel) throws Exception
    {
        if (textChannel == null)
        {
            this.discord.setAuditExChannel(auditEventTypeEx, null);
            hook.sendMessageFormat("Unassociating VRChat extended group audit event type %s (%s) from any channels", auditEventTypeEx.title, auditEventTypeEx.id).setEphemeral(true).queue();
        }
        else if (!textChannel.getType().isMessage())
        {
            hook.sendMessageFormat("The channel %s doesn't support message sending", textChannel.getName()).setEphemeral(true).queue();
        }
        else
        {
            this.discord.setAuditExChannel(auditEventTypeEx, textChannel);
            hook.sendMessageFormat("Associating VRChat extended group audit event type %s (%s) with channel <#%s>", auditEventTypeEx.title, auditEventTypeEx.id, textChannel.getId()).setEphemeral(true).queue();
        }
    }

    // set-audit-secret-channel

    @SlashCmd("set-audit-secret-channel")
    @Desc("Sets a given text channel as the secret channel certain audit event types use")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void setAuditSecretChannel(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("audit-event-type") GroupAuditType auditEventType, @SlashOpt("discord-text-channel") Channel textChannel) throws Exception
    {
        if (textChannel == null)
        {
            this.discord.setAuditSecretChannel(auditEventType, null);
            hook.sendMessageFormat("Unassociating VRChat group audit log event type %s (%s) from any secret channels", auditEventType.title, auditEventType.id).setEphemeral(true).queue();
        }
        else if (!textChannel.getType().isMessage())
        {
            hook.sendMessageFormat("The channel %s doesn't support message sending", textChannel.getName()).setEphemeral(true).queue();
        }
        else
        {
            this.discord.setAuditSecretChannel(auditEventType, textChannel);
            hook.sendMessageFormat("Associating VRChat group audit log event type %s (%s) with secret channel <#%s>", auditEventType.title, auditEventType.id, textChannel.getId()).setEphemeral(true).queue();
        }
    }

    // set-audit-secret-ex-channel

    @SlashCmd("set-audit-secret-ex-channel")
    @Desc("Sets a given text channel as the secret channel certain extended event types use")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void setAuditSecretExChannel(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("audit-ex-event-type") GroupAuditTypeEx auditEventTypeEx, @SlashOpt("discord-text-channel") Channel textChannel) throws Exception
    {
        if (textChannel == null)
        {
            this.discord.setAuditExSecretChannel(auditEventTypeEx, null);
            hook.sendMessageFormat("Unassociating VRChat extended group audit event type %s (%s) from any secret channels", auditEventTypeEx.title, auditEventTypeEx.id).setEphemeral(true).queue();
        }
        else if (!textChannel.getType().isMessage())
        {
            hook.sendMessageFormat("The channel %s doesn't support message sending", textChannel.getName()).setEphemeral(true).queue();
        }
        else
        {
            this.discord.setAuditExSecretChannel(auditEventTypeEx, textChannel);
            hook.sendMessageFormat("Associating VRChat extended group audit event type %s (%s) with secret channel <#%s>", auditEventTypeEx.title, auditEventTypeEx.id, textChannel.getId()).setEphemeral(true).queue();
        }
    }

    // set-voice-channel

    @SlashCmd("set-voice-channel")
    @Desc("Sets a given voice channel as the channel in which to announce TTS messages")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void setVoiceChannel(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("discord-voice-channel") Channel voiceChannel) throws Exception
    {
        if (voiceChannel == null)
        {
            this.discord.audioChannelSf = null;
            hook.sendMessage("Disabling/disconnecting from voice channel").setEphemeral(true).queue();
            this.discord.audio.updateChannel();
            this.discord.save();
        }
        else if (voiceChannel.getType() != ChannelType.VOICE)
        {
            hook.sendMessageFormat("The channel %s isn't a voice channel", voiceChannel.getName()).setEphemeral(true).queue();
        }
        else
        {
            this.discord.audioChannelSf = voiceChannel.getId();
            hook.sendMessageFormat("Enabling/connecting from voice channel <#%s>", voiceChannel.getId()).setEphemeral(true).queue();
            this.discord.audio.updateChannel();
            this.discord.save();
        }
    }

    // set-tts-voice

    public final SlashOption<String> _voiceName = SlashOption.ofString("voice-name", "The name of the installed voice to use", true, null, this::_voiceName);
    void _voiceName(CommandAutoCompleteInteractionEvent event) { event.replyChoiceStrings(this.discord.scarlet.ttsService.getInstalledVoices()).queue(); }
    @SlashCmd("set-tts-voice")
    @Desc("Selects which TTS voice is used to make announcements")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void setTtsVoice(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("voice-name") String voiceName) throws Exception
    {
        if (!this.discord.scarlet.ttsService.selectVoice(voiceName))
        {
            hook.sendMessageFormat("Tried to set TTS voice to `%s` (subprocess not responsive)", voiceName).setEphemeral(true).queue();
            return;
        }
        
        if (!this.discord.scarlet.ttsService.getInstalledVoices().contains(voiceName))
        {
            hook.sendMessageFormat("TTS voice `%s` is not installed on this system", voiceName).setEphemeral(true).queue();
            return;
        }
        
        this.discord.scarlet.eventListener.ttsVoiceName.set(voiceName);
        
        hook.sendMessageFormat("Setting TTS voice to `%s`", voiceName).setEphemeral(true).queue();
    }

    // vrchat-animated-emoji

    @SlashCmd("vrchat-animated-emoji")
    @Desc("Generates a VRChat animated emoji spritesheet from a gif")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public class VrchatAnimatedEmoji
    {
        public final SlashOption<String> gifUrl = SlashOption.ofString("gif-url", "The URL of the gif", true, null);
        public final SlashOption<Message.Attachment> gifFile = SlashOption.ofAttachment("gif-file", "The gif file", true);
        @SlashCmd("from-url")
        @Desc("Generates a VRChat animated emoji spritesheet from the gif from this URL")
        public void fromUrl(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("gif-url") String gifUrl) throws Exception
        {
            this.emoji(hook, gifUrl, Gifs.VrcSpriteSheet::decode);
        }
        @SlashCmd("from-file")
        @Desc("Generates a VRChat animated emoji spritesheet from the gif from this file")
        public void fromFile(SlashCommandInteractionEvent event, InteractionHook hook, @SlashOpt("gif-file") Message.Attachment gifFile) throws Exception
        {
            this.emoji(hook, gifFile, $ -> Gifs.VrcSpriteSheet.decode($.getFileName(), $.getUrl()));
        }
        private <T> void emoji(InteractionHook hook, T ctx, F1<Exception, Gifs.VrcSpriteSheet, T> decoder)
        {
            String info;
            FileUpload fu;
            try
            {
                Gifs.VrcSpriteSheet vrcss = decoder.invoke(ctx);
                info = vrcss.getInfo();
                fu = vrcss.toDiscordAttachment();
            }
            catch (Exception ex)
            {
                hook.sendMessageFormat("Exception generating spritesheet: %s", ex.getMessage()).queue();
                LOG.warn("Exception generating spritesheet", ex);
                return;
            }
            hook.sendMessage(info).addFiles(fu).queue();
        }
    }

    @MsgCmd("vrchat-animated-emoji")
    @DefaultPerms(Permission.USE_APPLICATION_COMMANDS)
    public void vrchatAnimatedEmoji(MessageContextInteractionEvent event) throws Exception
    {
        String raw = event.getTarget().getContentRaw();
        if (!raw.startsWith("https://tenor.com/view/"))
        {
            event.reply("Message doesn't contain a Tenor gif.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply(false).queue(hook ->
        {
            try
            {
                Gifs.VrcSpriteSheet vrcss = Gifs.VrcSpriteSheet.decode(raw);
                hook.sendMessage(vrcss.getInfo()).addFiles(vrcss.toDiscordAttachment()).queue();
            }
            catch (Exception ex)
            {
                hook.sendMessageFormat("Exception generating spritesheet: %s", ex).queue();
            }
        });
    }

}
