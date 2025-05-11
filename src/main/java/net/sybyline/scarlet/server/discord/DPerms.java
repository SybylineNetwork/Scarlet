package net.sybyline.scarlet.server.discord;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.gson.reflect.TypeToken;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.sybyline.scarlet.Scarlet;
import net.sybyline.scarlet.util.MiscUtils;

public class DPerms
{

    static final OptionData
        TARGET_OPT_R = new OptionData(OptionType.MENTIONABLE, "target", "The target of the permission")
            .setRequired(true),
        TYPE_OPT = new OptionData(OptionType.STRING, "type", "The type of the permission")
            .addChoices(PermType.CHOICES),
        TYPE_OPT_R = new OptionData(OptionType.STRING, "type", "The type of the permission")
            .setRequired(true)
            .addChoices(PermType.CHOICES),
        NAME_OPT_R = new OptionData(OptionType.STRING, "name", "The name of the permission")
            .setRequired(true)
            .setAutoComplete(true),
        VALUE_OPT_R = new OptionData(OptionType.STRING, "value", "The value of the permission")
            .setRequired(true)
            .addChoice("Default (fall through)", "null")
            .addChoice("Allow", "true")
            .addChoice("Deny", "false");
    static final SubcommandData
        SET_SUB = new SubcommandData("set", "Sets the value of the permission for a specific target")
            .addOptions(TARGET_OPT_R, TYPE_OPT_R, NAME_OPT_R, VALUE_OPT_R),
        LIST_SUB = new SubcommandData("list", "Lists the permissions for a specific target")
            .addOptions(TARGET_OPT_R, TYPE_OPT);
    public static SlashCommandData generateCommand(String name)
    {
        return Commands.slash(name, "View and edit permissions")
            .addSubcommands(SET_SUB, LIST_SUB);
    }

    public DPerms(File file)
    {
        this.file = file;
    }

    final File file;
    final Set<String> guildSfs = new HashSet<>();

    public Set<String> getGuildSnowflakesMutable()
    {
        return this.guildSfs;
    }

    public boolean isInGuild(Interaction interaction)
    {
        return interaction.isFromGuild() && this.guildSfs.contains(interaction.getGuild().getId());
    }

    public boolean isInGuild(Member member)
    {
        return this.guildSfs.contains(member.getGuild().getId());
    }

    public void buildSuggestionsFromBuilders(List<CommandData> commands)
    {
        for (CommandData cd : commands) switch (cd.getType())
        {
        default: throw new IllegalStateException();
        case MESSAGE: DPerms.this.permissions.get(PermType.MESSAGE_COMMAND).addSuggestion(cd.getName()); break;
        case USER: DPerms.this.permissions.get(PermType.USER_COMMAND).addSuggestion(cd.getName()); break;
        case SLASH:
            SlashCommandData c = (SlashCommandData)cd;
            PermSet ps = DPerms.this.permissions.get(PermType.SLASH_COMMAND);
            ps.addSuggestion(c.getName());
            c.getSubcommands().forEach(sc -> ps.addSuggestion(c.getName()+" "+sc.getName()));
            c.getSubcommandGroups().forEach(sg ->
            {
                ps.addSuggestion(c.getName()+" "+sg.getName());
                sg.getSubcommands().forEach(sc -> ps.addSuggestion(c.getName()+" "+sg.getName()+" "+sc.getName()));
            });
        }
    }
    public void buildSuggestions(List<Command> commands)
    {
        for (Command c : commands) switch (c.getType())
        {
        default: throw new IllegalStateException();
        case MESSAGE: DPerms.this.permissions.get(PermType.MESSAGE_COMMAND).addSuggestion(c.getName()); break;
        case USER: DPerms.this.permissions.get(PermType.USER_COMMAND).addSuggestion(c.getName()); break;
        case SLASH:
            PermSet ps = DPerms.this.permissions.get(PermType.SLASH_COMMAND);
            ps.addSuggestion(c.getName());
            c.getSubcommands().forEach(sc -> ps.addSuggestion(c.getName()+" "+sc.getName()));
            c.getSubcommandGroups().forEach(scg ->
            {
                ps.addSuggestion(c.getName()+" "+scg.getName());
                scg.getSubcommands().forEach(sc -> ps.addSuggestion(c.getName()+" "+scg.getName()+" "+sc.getName()));
            });
        }
    }

    public void registerOther(String... perms)
    {
        Arrays.stream(perms).forEachOrdered(this.permissions.get(PermType.OTHER)::addSuggestion);
    }

    static final Pattern SAN = Pattern.compile("[^0-9a-z]");
    public void internal_autocomplete(CommandAutoCompleteInteractionEvent event)
    {
        try
        {
            if (!"name".equals(event.getFocusedOption().getName()))
                return;
            PermType type = event.getOption("type", PermType::of);
            if (type == null)
                return;
            String value = event.getFocusedOption().getValue();
            PermSet ps = this.permissions.get(type);
            Stream<String> completes;
            if (value == null || (value = value.trim()).isEmpty())
                completes = ps.suggestions.stream();
            else
            {
                String san = SAN.matcher(value.toLowerCase()).replaceAll("");
                completes = IntStream
                    .range(0, ps.suggestions.size())
                    .mapToObj(Integer::valueOf)
                    .sorted(Comparator.<Integer>comparingInt($ ->
                    {
                        String ssan = ps.san_suggestions.get($);
                        return ssan.length() * (ssan.startsWith(san) ? 1 : ssan.contains(san) ? 2 : 3);
                    }))
                    .mapToInt(Integer::intValue)
                    .mapToObj(ps.suggestions::get);
            }
            event.replyChoiceStrings(completes.limit(25L).toArray(String[]::new)).queue();
        }
        finally
        {
            if (!event.isAcknowledged())
            {
                event.replyChoices().queue();
            }
        }
    }
    public void internal_handle(SlashCommandInteractionEvent event)
    {
        try
        {
            PermType type = event.getOption("type", PermType::of);
            switch (event.getSubcommandName())
            {
            case "set": {
                String name = event.getOption("name", OptionMapping::getAsString);
                Boolean value = event.getOption("value", DPerms::bool);
                
                Member member = event.getOption("target").getAsMember();
                boolean isMember = member != null;
                Role role = isMember ? null : event.getOption("target").getAsRole();
                
                String mention = isMember ? member.getAsMention() : role.getAsMention();
                Boolean prev = isMember ? this.set(type, member, name, value) : this.set(type, role, name, value);

                event.replyFormat(Objects.equals(prev, value)
                        ? "The %s permission `%s` for %s is already %s" // Mismatched format args count is intentional
                        : "Set %s permission `%s` for %s to %s (was %s)",
                    type.display, name, mention, boolStr(value), boolStr(prev)
                ).setEphemeral(true).queue();
            } break;
            case "list": {
                Member member = event.getOption("target").getAsMember();
                boolean isMember = member != null;
                Role role = member != null ? null : event.getOption("target").getAsRole();

                String mention = isMember ? member.getAsMention() : role.getAsMention();
                
                StringBuilder sb = new StringBuilder();
                if (type != null)
                    this.listPerms(sb, isMember, type, member, role, mention);
                else for (PermType type0 : PermType.values())
                    this.listPerms(sb, isMember, type0, member, role, mention);
                
                event.reply(sb.toString().trim()).setEphemeral(true).queue();
                
            } break;
            }
        }
        finally
        {
            if (!event.isAcknowledged())
            {
                event.reply("Internal error: command unacknowledged").setEphemeral(true).queue();
            }
        }
    }
    void listPerms(StringBuilder sb, boolean isMember, PermType type, Member member, Role role, String mention)
    {
        Map<String, Boolean> perms = isMember ? this.list(type, member) : this.list(type, role);
        String rsp = perms.entrySet().stream().map($ -> "`"+$.getKey()+"`: "+boolStr($.getValue())).collect(Collectors.joining("\n"));
        sb.append(String.format("%s permissions for %s:\n%s\n\n", type.display, mention, rsp));
    }

    public static class PermSetSpec
    {
        static final TypeToken<Map<PermType, PermSetSpec>> TYPE_TOKEN = new TypeToken<Map<PermType, PermSetSpec>>(){};
        public Map<String, Map<String, Boolean>> byUser, byRole;
    }
    public void load()
    {
        if (!this.file.isFile())
            return;
        Map<PermType, PermSetSpec> specs;
        try (Reader in = MiscUtils.reader(this.file))
        {
            specs = Scarlet.GSON.fromJson(in, PermSetSpec.TYPE_TOKEN);
        }
        catch (Exception ex)
        {
            return;
        }
        specs.forEach((type, spec) -> this.permissions.get(type).load(spec));
    }

    public void save()
    {
        Map<PermType, PermSetSpec> specs = new HashMap<>();
        for (PermType permType : PermType.values())
            specs.put(permType, this.permissions.get(permType).store(new PermSetSpec()));
        if (!this.file.getParentFile().isDirectory())
            this.file.getParentFile().mkdirs();
        try (Writer out = MiscUtils.writer(this.file))
        {
            Scarlet.GSON.toJson(specs, PermSetSpec.TYPE_TOKEN.getType(), out);
        }
        catch (Exception ex)
        {
        }
    }

    final EnumMap<PermType, PermSet> permissions = new EnumMap<>(PermType.class);
    {
        for (PermType permType : PermType.values())
            this.permissions.put(permType, new PermSet());
    }
    public static enum PermType implements DEnum.DEnumString<PermType>
    {
        SLASH_COMMAND("slash-command", "Slash Command"),
        MESSAGE_COMMAND("message-command", "Message Command"),
        USER_COMMAND("user-command", "User Command"),
        BUTTON_PRESS("button-press", "Button Press"),
        STRING_SELECT("string-select", "String Select"),
        ENTITY_SELECT("entity-select", "Entity Select"),
        MODAL_SUBMIT("modal-submit", "Modal Submit"),
        OTHER("other", "Other"),
        ;
        final String value, display;
        private PermType(String value, String display)
        {
            this.value = value;
            this.display = display;
        }
        @Override
        public String value()
        {
            return this.value;
        }
        @Override
        public String display()
        {
            return this.display;
        }
        public static PermType of(OptionMapping om)
        {
            if (om != null) switch (om.getAsString())
            {
            case "slash-command": return SLASH_COMMAND;
            case "message-command": return MESSAGE_COMMAND;
            case "user-command": return USER_COMMAND;
            case "button-press": return BUTTON_PRESS;
            case "string-select": return STRING_SELECT;
            case "entity-select": return ENTITY_SELECT;
            case "modal-submit": return MODAL_SUBMIT;
            case "other": return OTHER;
            }
            return null;
        }
        static final Command.Choice[] CHOICES = Arrays.stream(PermType.values()).map($ -> new Command.Choice($.display, $.value)).toArray(Command.Choice[]::new);
    }
    public static class PermSet
    {
        void load(PermSetSpec spec)
        {
            if (spec.byUser != null)
            {
                this.byUser.clear();
                spec.byUser.forEach((sf, map) -> map.forEach((perm, value) -> this.set(this.byUser, sf, perm, value)));
            }
            if (spec.byRole != null)
            {
                this.byRole.clear();
                spec.byRole.forEach((sf, map) -> map.forEach((perm, value) -> this.set(this.byRole, sf, perm, value)));
            }
        }
        PermSetSpec store(PermSetSpec spec)
        {
            spec.byUser = new HashMap<>();
            this.byUser.forEach((sf, map) -> spec.byUser.put(sf, new HashMap<>(map)));
            spec.byRole = new HashMap<>();
            this.byRole.forEach((sf, map) -> spec.byRole.put(sf, new HashMap<>(map)));
            return spec;
        }
        void addSuggestion(String suggestion)
        {
            this.suggestions.add(suggestion);
            this.san_suggestions.add(SAN.matcher(suggestion.toLowerCase()).replaceAll(""));
        }
        final List<String> suggestions = new ArrayList<>(), san_suggestions = new ArrayList<>();
        final Map<String, Map<String, Boolean>> byUser = new ConcurrentHashMap<>(),
                                                byRole = new ConcurrentHashMap<>();

        Map<String, Boolean> list(Map<String, Map<String, Boolean>> by, String sf)
        {
            return by.getOrDefault(sf, Collections.emptyMap());
        }
        Boolean get(Map<String, Map<String, Boolean>> by, String sf, String perm)
        {
            Map<String, Boolean> map = by.get(sf);
            return map == null ? null : map.get(perm);
        }
        Boolean set(Map<String, Map<String, Boolean>> by, String sf, String perm, Boolean value)
        {
            Map<String, Boolean> map = by.get(sf);
            if (map == null)
            {
                if (value == null)
                    return null;
                by.put(sf, map = new ConcurrentHashMap<>());
            }
            return value == null ? map.remove(perm) : map.put(perm, value);
        }
        public Boolean get(Member member, String perm)
        {
            Boolean value = this.get(this.byUser, member.getId(), perm);
            if (value != null)
                return value;
            for (Role role : member.getRoles())
                if ((value = this.get(this.byRole, role.getId(), perm)) != null)
                    return value;
            return null;
        }
        public Boolean get(Role role, String perm)
        {
            return this.get(this.byRole, role.getId(), perm);
        }
        public Boolean set(Member member, String perm, Boolean value)
        {
            return this.set(this.byUser, member.getId(), perm, value);
        }
        public Boolean set(Role role, String perm, Boolean value)
        {
            return this.set(this.byRole, role.getId(), perm, value);
        }
        public Map<String, Boolean> list(Member member)
        {
            Map<String, Boolean> map = new HashMap<>(this.list(this.byUser, member.getId()));
            for (Role role : member.getRoles())
                this.list(this.byRole, role.getId()).forEach(map::putIfAbsent);
            return map;
        }
        public Map<String, Boolean> list(Role role)
        {
            return new HashMap<>(this.list(this.byRole, role.getId()));
        }
    }

    public Boolean get(PermType kind, Member member, String perm)
    {
        if (member.isOwner())
            return Boolean.TRUE;
        if (member.hasPermission(Permission.ADMINISTRATOR))
            return Boolean.TRUE;
        return this.permissions.get(kind).get(member, perm);
    }
    public Boolean get(PermType kind, Role role, String perm)
    {
        if (role.hasPermission(Permission.ADMINISTRATOR))
            return Boolean.TRUE;
        return this.permissions.get(kind).get(role, perm);
    }
    public Boolean set(PermType kind, Member member, String perm, Boolean value)
    {
        return this.permissions.get(kind).set(member, perm, value);
    }
    public Boolean set(PermType kind, Role role, String perm, Boolean value)
    {
        return this.permissions.get(kind).set(role, perm, value);
    }
    public Map<String, Boolean> list(PermType kind, Member member)
    {
        return this.permissions.get(kind).list(member);
    }
    public Map<String, Boolean> list(PermType kind, Role role)
    {
        return this.permissions.get(kind).list(role);
    }

    static String boolStr(Boolean value)
    {
        return value == null ? "Default" : value.booleanValue() ? "Allow" : "Deny";
    }
    static Boolean bool(OptionMapping om)
    {
        if (om != null) switch (om.getAsString().toLowerCase())
        {
        case "true": return Boolean.TRUE;
        case "false": return Boolean.FALSE;
        case "null":
        default:
        }
        return null;
    }
    static String op(String id)
    {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(0, colon);
    }
    boolean check(PermType permType, Member member, String perm, boolean fallback)
    {
        Boolean value = this.get(permType, member, perm);
        return value == null ? fallback : value.booleanValue();
    }
    boolean checkSlashCommand(Member member, String name, String group, String sub, boolean fallback)
    {
        if (sub != null)
        {
            Boolean value;
            if (group != null)
            {
                if ((value = this.get(PermType.SLASH_COMMAND, member, name+" "+group+" "+sub)) != null)
                    return value.booleanValue();
                if ((value = this.get(PermType.SLASH_COMMAND, member, name+" "+group)) != null)
                    return value.booleanValue();
            }
            else if ((value = this.get(PermType.SLASH_COMMAND, member, name+" "+sub)) != null)
                return value.booleanValue();
        }
        return this.check(PermType.SLASH_COMMAND, member, name, fallback);
    }

    public boolean check(SlashCommandInteractionEvent event)
    {
        return this.isInGuild(event) && this.checkSlashCommand(event.getMember(), event.getName(), event.getSubcommandGroup(), event.getSubcommandName(), false);
    }
    public boolean check(CommandAutoCompleteInteractionEvent event)
    {
        return this.isInGuild(event) && this.checkSlashCommand(event.getMember(), event.getName(), event.getSubcommandGroup(), event.getSubcommandName(), false);
    }
    public boolean check(MessageContextInteractionEvent event)
    {
        return this.isInGuild(event) && this.check(PermType.MESSAGE_COMMAND, event.getMember(), event.getName(), false);
    }
    public boolean check(UserContextInteractionEvent event)
    {
        return this.isInGuild(event) && this.check(PermType.USER_COMMAND, event.getMember(), event.getName(), false);
    }
    public boolean check(ButtonInteractionEvent event)
    {
        return this.isInGuild(event) && this.check(PermType.BUTTON_PRESS, event.getMember(), op(event.getButton().getId()), true);
    }
    public boolean check(StringSelectInteractionEvent event)
    {
        return this.isInGuild(event) && this.check(PermType.STRING_SELECT, event.getMember(), op(event.getSelectMenu().getId()), true);
    }
    public boolean check(EntitySelectInteractionEvent event)
    {
        return this.isInGuild(event) && this.check(PermType.ENTITY_SELECT, event.getMember(), op(event.getSelectMenu().getId()), true);
    }
    public boolean check(ModalInteractionEvent event)
    {
        return this.isInGuild(event) && this.check(PermType.MODAL_SUBMIT, event.getMember(), op(event.getModalId()), true);
    }
    public boolean check(Member member, String perm, boolean fallback)
    {
        return this.isInGuild(member) && this.check(PermType.OTHER, member, perm, fallback);
    }

}
