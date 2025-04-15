package net.sybyline.scarlet.server.discord;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.sybyline.scarlet.util.Func;

public class DInteractions
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Discord/DCmd");

    public static final DefaultMemberPermissions DEFAULT_PERMISSIONS = DefaultMemberPermissions.enabledFor(
        Permission.ADMINISTRATOR,
        Permission.MANAGE_SERVER,
        Permission.MANAGE_ROLES);

    @Target({ElementType.TYPE,ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface SlashCmd
    {
        public abstract String name();
        public abstract String description() default "description";
        public abstract String[] options() default {};
        public abstract Permission[] defaultPerms() default {};
    }
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface MsgCmd
    {
        public abstract String name();
        public abstract Permission[] defaultPerms() default {};
    }
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface UserCmd
    {
        public abstract String name();
        public abstract Permission[] defaultPerms() default {};
    }
 /*
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface SlashOpt
    {
        public abstract String name();
        public abstract String description() default "";
        public abstract String min() default "";
        public abstract String max() default "";
    }
//*/

    public void register(Object object)
    {
        this.register(object.getClass(), object);
    }
    public void register(Class<?> type)
    {
        try
        {
            this.register(type, type.getDeclaredConstructor().newInstance());
        }
        catch (Exception ex)
        {
            LOG.error("Exception registering "+type, ex);
        }
    }
    public void register(Class<?> type, Object object)
    {
        for (Field field : type.getFields())
            this.registerSlashCmdOption(object, field);
        for (Method method : type.getMethods())
        {
            if (this.registerSlashCmdHandler(object, method, null, null)) continue;
            if (this.registerMsgCmdHandler(object, method)) continue;
            if (this.registerUserCmdHandler(object, method)) continue;
        }
        for (Class<?> clazz : type.getClasses())
            this.registerSlashCmd(type, object, clazz);
        
    }
    Object ctxNew(Class<?> outer, Object receiver, Class<?> type)
    {
        try
        {
            return type.getDeclaredConstructor().newInstance();
        }
        catch (Exception ex)
        {
            try
            {
                return type.getDeclaredConstructor(outer).newInstance(receiver);
            }
            catch (Exception ex0)
            {
                RuntimeException re = new RuntimeException();
                re.addSuppressed(ex);
                re.addSuppressed(ex0);
                LOG.error("Exceptions creating context "+type, re);
                return null;
            }
        }
    }
    public boolean registerSlashCmd(Class<?> outer, Object receiver, Class<?> cclass)
    {
        SlashCmd sc = cclass.getDeclaredAnnotation(SlashCmd.class);
        if (sc == null)
            return false;
        LOG.debug(String.format("Registering slash command: %s", sc.name()));
        Object sub = this.ctxNew(outer, receiver, cclass);
        Slash slash = this.slash(sc.name(), sc.description());
        slash.data.setDefaultPermissions(sc.defaultPerms().length == 0
            ? DEFAULT_PERMISSIONS
            : DefaultMemberPermissions.enabledFor(sc.defaultPerms()));        
        for (Field field : cclass.getFields())
            this.registerSlashCmdOption(sub, field);
        for (Method method : cclass.getMethods())
            this.registerSlashCmdHandler(sub, method, slash, null);
        for (Class<?> gclass : cclass.getClasses())
            this.registerSlashCmdGrp(cclass, sub, gclass, slash);
        return true;
    }
    public boolean registerSlashCmdGrp(Class<?> cclass, Object receiver, Class<?> gclass, Slash slash)
    {
        SlashCmd sc = gclass.getDeclaredAnnotation(SlashCmd.class);
        if (sc == null)
            return false;
        LOG.debug(String.format("Registering slash command group: %s %s", slash.data.getName(), sc.name()));
        Object sub = this.ctxNew(cclass, receiver, gclass);
        Slash.Group group = slash.group(sc.name(), sc.description());
        for (Field field : gclass.getFields())
            this.registerSlashCmdOption(sub, field);
        for (Method method : gclass.getMethods())
            this.registerSlashCmdHandler(sub, method, slash, group);
        return true;
    }
    public boolean registerSlashCmdHandler(Object receiver, Method method, Slash slash, Slash.Group group)
    {
        SlashCmd sc = method.getDeclaredAnnotation(SlashCmd.class);
        if (sc == null)
            return false;
        LOG.debug(String.format("Registering slash command handler: %s%s%s", slash==null?"":(slash.data.getName()+" "), group==null?"":(group.data.getName()+" "), sc.name()));
        SlashOption<?>[] options = this.findOptions(sc.options());
        Permission[] defaultPerms = sc.defaultPerms();
        int params = method.getParameterCount() - 1;
        Class<?>[] paramTypes = method.getParameterTypes();
        if (!paramTypes[0].isAssignableFrom(SlashCommandInteractionEvent.class))
            throw new IllegalArgumentException("!method.getParameterTypes()[0].isAssignableFrom(SlashCommandInteractionEvent.class)");
        SlashCommandHandler handler;
        try
        {
            int argidx = Modifier.isStatic(method.getModifiers()) ? 1 : 2;
            MethodHandle mh = MethodHandles.publicLookup().unreflect(method);
            mh = MethodHandles.filterArguments(mh,
                    argidx,
                    IntStream.range(0, params)
                    .mapToObj($ -> options[$].mh(paramTypes[$ + 1], paramTypes[0]))
                    .toArray(MethodHandle[]::new));
            mh = argidx == 2 ? MethodHandles.permuteArguments(mh,
                MethodType.methodType(
                    mh.type().returnType(), mh.type().parameterType(0), mh.type().parameterType(1)),
                IntStream.concat(IntStream.of(0, 1), IntStream.range(0, params).map($ -> 1)).toArray()
            ) : MethodHandles.permuteArguments(mh,
                MethodType.methodType(
                    mh.type().returnType(), mh.type().parameterType(0)),
                IntStream.concat(IntStream.of(0), IntStream.range(0, params).map($ -> 0)).toArray()
            );
            if (mh.type().returnType() == void.class) mh = MethodHandles.filterReturnValue(mh, MethodHandles.constant(Object.class, null));
            if (argidx == 2) mh = mh.bindTo(receiver);
            Func.F1<Throwable, Object, CommandInteractionPayload> f1 = mh::invoke;
            handler = f1.asUnchecked()::invoke;
        }
        catch (Exception ex)
        {
            throw new Error(ex);
        }
        if (slash == null)
        {
            DefaultMemberPermissions defaultMemberPerms = defaultPerms.length == 0 ? DEFAULT_PERMISSIONS : DefaultMemberPermissions.enabledFor(defaultPerms);
            this.slash(sc.name(), sc.description(), handler, defaultMemberPerms, options);
        }
        else if (group == null)
            slash.sub(sc.name(), sc.description(), handler, options);
        else
            group.sub(sc.name(), sc.description(), handler, options);
        return true;
    }
    public boolean registerSlashCmdOption(Object receiver, Field field)
    {
        if (!SlashOption.class.isAssignableFrom(field.getType()))
            return false;
        SlashOption<?> so;
        try
        {
            so = (SlashOption<?>)field.get(receiver);
        }
        catch (Exception ex)
        {
            throw new Error(ex);
        }
        if (so == null)
            return false;
        LOG.debug(String.format("Registering slash command option: %s", so.data.getName()));
        return this.registerOption(null, so);
    }
    public boolean registerMsgCmdHandler(Object receiver, Method method)
    {
        MsgCmd mc = method.getDeclaredAnnotation(MsgCmd.class);
        if (mc == null)
            return false;
        LOG.debug(String.format("Registering message command: %s", mc.name()));
        Permission[] defaultPerms = mc.defaultPerms();
        int params = method.getParameterCount();
        if (params != 1)
            throw new IllegalArgumentException("method.getParameterCount() != 1");
        if (!method.getParameterTypes()[0].isAssignableFrom(MessageContextInteractionEvent.class))
            throw new IllegalArgumentException("!method.getParameterTypes()[0].isAssignableFrom(MessageContextInteractionEvent.class)");
        MessageCommandHandler handler;
        try
        {
            int argidx = Modifier.isStatic(method.getModifiers()) ? 0 : 1;
            MethodHandle mh = MethodHandles.publicLookup().unreflect(method);
            if (mh.type().returnType() == void.class) mh = MethodHandles.filterReturnValue(mh, MethodHandles.constant(Object.class, null));
            if (argidx == 1) mh = mh.bindTo(receiver);
            Func.F1<Throwable, Object, MessageContextInteractionEvent> f1 = mh::invoke;
            handler = f1.asUnchecked()::invoke;
        }
        catch (Exception ex)
        {
            throw new Error(ex);
        }
        this.message(mc.name(), handler).setDefaultPermissions(defaultPerms.length == 0 ? DEFAULT_PERMISSIONS : DefaultMemberPermissions.enabledFor(defaultPerms));
        return true;
    }
    public boolean registerUserCmdHandler(Object receiver, Method method)
    {
        UserCmd uc = method.getDeclaredAnnotation(UserCmd.class);
        if (uc == null)
            return false;
        LOG.debug(String.format("Registering user command: %s", uc.name()));
        Permission[] defaultPerms = uc.defaultPerms();
        int params = method.getParameterCount();
        if (params != 1)
            throw new IllegalArgumentException("method.getParameterCount() != 1");
        if (!method.getParameterTypes()[0].isAssignableFrom(UserContextInteractionEvent.class))
            throw new IllegalArgumentException("!method.getParameterTypes()[0].isAssignableFrom(UserContextInteractionEvent.class)");
        UserCommandHandler handler;
        try
        {
            int argidx = Modifier.isStatic(method.getModifiers()) ? 0 : 1;
            MethodHandle mh = MethodHandles.publicLookup().unreflect(method);
            if (mh.type().returnType() == void.class) mh = MethodHandles.filterReturnValue(mh, MethodHandles.constant(Object.class, null));
            if (argidx == 1) mh = mh.bindTo(receiver);
            Func.F1<Throwable, Object, UserContextInteractionEvent> f1 = mh::invoke;
            handler = f1.asUnchecked()::invoke;
        }
        catch (Exception ex)
        {
            throw new Error(ex);
        }
        this.user(uc.name(), handler).setDefaultPermissions(defaultPerms.length == 0 ? DEFAULT_PERMISSIONS : DefaultMemberPermissions.enabledFor(defaultPerms));
        return true;
    }
    public <T> boolean registerOption(String id, SlashOption<T> option)
    {
        return null == this.slashOptionsBound.putIfAbsent(id != null ? id : option.data.getName(), option);
    }
    public SlashOption<?> findOption(String id)
    {
        return this.slashOptionsBound.get(id);
    }
    public SlashOption<?>[] findOptions(String... ids)
    {
        return Arrays.stream(ids).map(this.slashOptionsBound::get).toArray(SlashOption[]::new);
    }

    public static @FunctionalInterface interface SlashCommandHandler { void handle(SlashCommandInteractionEvent event); }
    public static @FunctionalInterface interface SlashCompleteHandler { void autocomplete(CommandAutoCompleteInteractionEvent event); }
    public static @FunctionalInterface interface MessageCommandHandler { void handle(MessageContextInteractionEvent event); }
    public static @FunctionalInterface interface UserCommandHandler { void handle(UserContextInteractionEvent event); }
    public static @FunctionalInterface interface ButtonClickHandler { void handle(ButtonInteractionEvent event); }
    public static @FunctionalInterface interface StringSelectHandler { void handle(StringSelectInteractionEvent event); }
    public static @FunctionalInterface interface EntitySelectHandler { void handle(EntitySelectInteractionEvent event); }
    public static @FunctionalInterface interface ModalSubmitHandler { void handle(ModalInteractionEvent event); }
    public static @FunctionalInterface interface DeferredHandler { void handle(InteractionHook hook) throws Exception; }

    public static class SlashOption<T>
    {
        public static SlashOption<Boolean> ofBool(String name, String desc, boolean required, boolean fallback)
        {
            return new SlashOption<>(OptionType.BOOLEAN, name, desc, required, fallback, OptionMapping::getAsBoolean);
        }
        public static SlashOption<Integer> ofInt(String name, String desc, boolean required, int fallback)
        {
            return new SlashOption<>(OptionType.INTEGER, name, desc, required, fallback, OptionMapping::getAsInt);
        }
        public static SlashOption<Long> ofLong(String name, String desc, boolean required, long fallback)
        {
            return new SlashOption<>(OptionType.INTEGER, name, desc, required, fallback, OptionMapping::getAsLong);
        }
        public static SlashOption<Double> ofDouble(String name, String desc, boolean required, double fallback)
        {
            return new SlashOption<>(OptionType.INTEGER, name, desc, required, fallback, OptionMapping::getAsDouble);
        }
        public static SlashOption<String> ofString(String name, String desc, boolean required, String fallback)
        {
            return new SlashOption<>(OptionType.STRING, name, desc, required, fallback, OptionMapping::getAsString);
        }
        public static SlashOption<Message.Attachment> ofAttachment(String name, String desc, boolean required)
        {
            return new SlashOption<>(OptionType.ATTACHMENT, name, desc, required, null, OptionMapping::getAsAttachment);
        }
        public static SlashOption<User> ofUser(String name, String desc, boolean required)
        {
            return new SlashOption<>(OptionType.USER, name, desc, required, null, OptionMapping::getAsUser);
        }
        public static SlashOption<Channel> ofChannel(String name, String desc, boolean required)
        {
            return new SlashOption<>(OptionType.CHANNEL, name, desc, required, null, OptionMapping::getAsChannel);
        }
        public static SlashOption<Role> ofRole(String name, String desc, boolean required)
        {
            return new SlashOption<>(OptionType.ROLE, name, desc, required, null, OptionMapping::getAsRole);
        }
        public static SlashOption<Member> ofMember(String name, String desc, boolean required)
        {
            return new SlashOption<>(OptionType.USER, name, desc, required, null, OptionMapping::getAsMember);
        }
        public static SlashOption<IMentionable> ofMentionable(String name, String desc, boolean required)
        {
            return new SlashOption<>(OptionType.MENTIONABLE, name, desc, required, null, OptionMapping::getAsMentionable);
        }
        public static <DE extends Enum<DE> & DEnum<DE, ?>> SlashOption<DE> ofEnum(String name, String desc, boolean required, DE fallback)
        {
            return new SlashOption<>(fallback.type(), name, desc, required, fallback, ((DEnum<DE, ?>)fallback)::mapEnum, fallback.choices());
        }
        public static <E extends Enum<E>> SlashOption<E> ofDOptionEnum(DOptionEnum<E> dOptionEnum, boolean required)
        {
            return new SlashOption<E>(OptionType.STRING, dOptionEnum.name, dOptionEnum.description, required, null, dOptionEnum::getAsEnum, dOptionEnum.choices);
        }
        SlashOption(OptionType type, String name, String desc, boolean required, T fallback, Function<? super OptionMapping, ? extends T> resolver)
        {
            this(type, name, desc, required, fallback, resolver, (SlashCompleteHandler)null);
        }
        SlashOption(OptionType type, String name, String desc, boolean required, T fallback, Function<? super OptionMapping, ? extends T> resolver, SlashCompleteHandler autocomplete)
        {
            this(new OptionData(type, name, desc, required, autocomplete != null), fallback, resolver, autocomplete);
        }
        SlashOption(OptionType type, String name, String desc, boolean required, T fallback, Function<? super OptionMapping, ? extends T> resolver, Command.Choice[] choices)
        {
            this(new OptionData(type, name, desc, required, false), fallback, resolver, choices);
        }
        SlashOption(OptionData data, T fallback, Function<? super OptionMapping, ? extends T> resolver, SlashCompleteHandler autocomplete)
        {
            this.data = data;
            this.fallback = fallback;
            this.resolver = resolver;
            this.autocomplete = autocomplete;
        }
        SlashOption(OptionData data, T fallback, Function<? super OptionMapping, ? extends T> resolver, Command.Choice[] choices)
        {
            this(data, fallback, resolver, (SlashCompleteHandler)null);
            if (choices != null)
                this.data.addChoices(choices);
        }
        final OptionData data;
        final T fallback;
        final Function<? super OptionMapping, ? extends T> resolver;
        final SlashCompleteHandler autocomplete;
        static final MethodHandle mh_get;
        static
        {
            try
            {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                mh_get = lookup.findVirtual(SlashOption.class, "get", MethodType.methodType(Object.class, CommandInteractionPayload.class));
            }
            catch (Exception ex)
            {
                throw new Error(ex);
            }
        }
        public OptionData data()
        {
            return this.data;
        }
        public SlashOption<T> with(UnaryOperator<OptionData> data)
        {
            return new SlashOption<>(data.apply(OptionData.fromData(this.data.toData())), this.fallback, this.resolver, this.autocomplete);
        }
        public T get(CommandInteractionPayload command)
        {
            return command.getOption(this.data.getName(), this.fallback, this.resolver);
        }
        public MethodHandle mh(Class<?> rt, Class<?> cipt)
        {
            return mh_get.bindTo(this).asType(MethodType.methodType(rt, cipt));
        }
        public void autocomplete(CommandAutoCompleteInteractionEvent command)
        {
            if (this.autocomplete == null)
            {
                LOG.error("slash autocomplete impl == null for `"+command.getFullCommandName()+"`");
                return;
            }
            this.autocomplete.autocomplete(command);
        }
    }

    public static @FunctionalInterface interface Paginator
    {
        WebhookMessageEditAction<Message> applyContent(InteractionHook hook, @Nullable String messageId);
        
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
    public class Pagination
    {
        public Pagination(String paginationId, StringBuilder pages)
        {
            this(paginationId, Paginator.stringBuilder(pages));
        }
        public Pagination(String paginationId, MessageEmbed[] pages, int perPage)
        {
            this(paginationId, Paginator.embeds(pages, perPage));
        }
        public Pagination(String paginationId, Paginator[] pages)
        {
            this.paginationId = paginationId;
            this.pages = pages;
            this.hook = null;
            this.messageId = null; //"@original";
        }
        final String paginationId;
        final Paginator[] pages;
        InteractionHook hook;
        String messageId;
        boolean removeIfExpired()
        {
            if (this.hook == null || !this.hook.isExpired())
                return false;
            DInteractions.this.pagination.remove(this.paginationId);
            return true;
        }
        WebhookMessageEditAction<Message> action(int pageOrdinal)
        {
            if (pageOrdinal < 1) pageOrdinal = 1;
            if (pageOrdinal > this.pages.length) pageOrdinal = this.pages.length;
            
            Button prev = Button.primary("pagination:"+this.paginationId+":"+(pageOrdinal - 1), "Prev"),
                   self = Button.secondary("pagination:"+this.paginationId+":"+pageOrdinal, pageOrdinal+"/"+pages.length).asDisabled(),
                   next = Button.primary("pagination:"+this.paginationId+":"+(pageOrdinal + 1), "Next");
            
            if (pageOrdinal == 1) prev = prev.asDisabled();
            if (pageOrdinal == this.pages.length) next = next.asDisabled();
            
            int pageIndex = pageOrdinal - 1;
            return this
                .pages[pageIndex]
                .applyContent(this.hook, this.messageId)
                .setActionRow(prev, self, next);
        }
        void queue(InteractionHook hook)
        {
            this.hook = hook;
            this.action(1).queue(message -> {
                this.messageId = message.getId();
                DInteractions.this.pagination.put(this.paginationId, this);
            });
        }
    }

    public DInteractions(boolean warnMissing)
    {
        this.warnMissing = warnMissing;
    }

    final List<CommandData> data = new ArrayList<>();
    final Map<String, Slash> slashBuilders = new HashMap<>();
    final Map<String, SlashCommandHandler> slashCmds = new HashMap<>();
    final Map<String, SlashOption<?>> slashOptions = new HashMap<>(),
                                      slashOptionsBound = new HashMap<>();
    final Map<String, MessageCommandHandler> messageCmds = new HashMap<>();
    final Map<String, UserCommandHandler> userCmds = new HashMap<>();
    final Map<String, ButtonClickHandler> buttonClicks = new HashMap<>();
    final Map<String, StringSelectHandler> stringSelects = new HashMap<>();
    final Map<String, EntitySelectHandler> entitySelects = new HashMap<>();
    final Map<String, ModalSubmitHandler> modalSubmits = new HashMap<>();
    final Map<String, Pagination> pagination = new ConcurrentHashMap<>();

    final Set<String> guildSfs = new HashSet<>();

    final boolean warnMissing;

    public List<CommandData> getCommandDataMutable()
    {
        return this.data;
    }

    public Set<String> getGuildSnowflakesMutable()
    {
        return this.guildSfs;
    }

    public SlashCommandData slash(String name, String desc, SlashCommandHandler handler, DefaultMemberPermissions defaultMemberPerms, SlashOption<?>... options)
    {
        return new Slash(name, desc, handler, defaultMemberPerms, options).end();
    }
    public Slash slash(String name, String desc)
    {
        return new Slash(name, desc);
    }
    public class Slash
    {
        Slash(String name, String desc)
        {
            this.data = Commands.slash(name, desc);
            this.subs = new HashMap<>();
            DInteractions.this.data.add(this.data);
            DInteractions.this.slashBuilders.put(name, this);
        }
        Slash(String name, String desc, SlashCommandHandler handler, DefaultMemberPermissions defaultMemberPerms, SlashOption<?>... options)
        {
            this(name, desc);
            this.data.setDefaultPermissions(defaultMemberPerms);
            DInteractions.this.data.add(this.data);
            DInteractions.this.slashCmds.put(name, handler);
            for (SlashOption<?> option : options)
            {
                this.data.addOptions(option.data);
                DInteractions.this.slashOptions.put(name + " " + option.data.getName(), option);
            }
        }
        final SlashCommandData data;
        final Map<String, Group> subs;
        public class Group
        {
            Group(String name, String desc)
            {
                this.data = new SubcommandGroupData(name, desc);
                Slash.this.data.addSubcommandGroups(this.data);
                Slash.this.subs.put(name, this);
            }
            final SubcommandGroupData data;
            public Group sub(String name, String desc, SlashCommandHandler handler, SlashOption<?>... options)
            {
                String subName = Slash.this.data.getName() + " " + this.data.getName() + " " + name;
                DInteractions.this.slashCmds.put(subName, handler);
                SubcommandData subData = new SubcommandData(name, desc);
                this.data.addSubcommands(subData);
                for (SlashOption<?> option : options)
                {
                    subData.addOptions(option.data);
                    DInteractions.this.slashOptions.put(subName + " " + option.data.getName(), option);
                }
                return this;
            }
            public Slash end()
            {
                return Slash.this;
            }
        }
        public Group group(String name, String desc)
        {
            return new Group(name, desc);
        }
        public Slash sub(String name, String desc, SlashCommandHandler handler, SlashOption<?>... options)
        {
            String subName = this.data.getName() + " " + name;
            DInteractions.this.slashCmds.put(subName, handler);
            this.data.addSubcommands(new SubcommandData(name, desc));
            for (SlashOption<?> option : options)
            {
                this.data.addOptions(option.data);
                DInteractions.this.slashOptions.put(subName + " " + option.data.getName(), option);
            }
            return this;
        }
        public SlashCommandData end()
        {
            return this.data;
        }
    }

    public CommandData message(String name, MessageCommandHandler handler)
    {
        CommandData data = Commands.message(name).setGuildOnly(true).setDefaultPermissions(DEFAULT_PERMISSIONS);
        this.messageCmds.put(name, handler);
        this.data.add(data);
        return data;
    }

    public CommandData user(String name, UserCommandHandler handler)
    {
        CommandData data = Commands.user(name).setGuildOnly(true).setDefaultPermissions(DEFAULT_PERMISSIONS);
        this.userCmds.put(name, handler);
        this.data.add(data);
        return data;
    }

    boolean isInGuild(Interaction interaction)
    {
        return interaction.isFromGuild() && this.guildSfs.contains(interaction.getGuild().getId());
    }

    void handleAsync(ExecutorService exec, IReplyCallback event, boolean ephemeral, DeferredHandler handler)
    {
        event.deferReply().queue(hook -> exec.execute(() ->
        {
            try
            {
                LOG.trace("Async handling event of type "+event.getClass().getSimpleName());
                handler.handle(hook);
            }
            catch (Exception ex)
            {
                LOG.error("Exception async handling event of type "+event.getClass().getSimpleName(), ex);
                hook.sendMessage("Exception async handling event:\n`"+ex+"`").setEphemeral(ephemeral).queue();
            }
        }), throwable -> LOG.error("Exception queuing async handling event of type "+event.getClass().getSimpleName(), throwable));
    }

    void interactionError(Interaction interaction, Throwable err)
    {
        LOG.error("Error processing interaction of type "+interaction.getClass().getSimpleName(), err);
        String reply = "Error processing interaction:\n`"+err+"`";
        if (interaction instanceof IReplyCallback)
        {
            IReplyCallback replyCallback = (IReplyCallback)interaction;
            if (!replyCallback.isAcknowledged())
            {
                replyCallback.reply(reply).queue();
                return;
            }
        }
        if (interaction instanceof IDeferrableCallback)
        {
            IDeferrableCallback replyCallback = (IDeferrableCallback)interaction;
            replyCallback.getHook().sendMessage(reply).queue();
            return;
        }
    }

    public void handle(SlashCommandInteractionEvent event)
    {
        if (!this.isInGuild(event))
            return;
        String fullName = event.getFullCommandName();
        SlashCommandHandler slash = this.slashCmds.get(fullName);
        if (slash == null)
        {
            if (this.warnMissing) LOG.error("slash command handler == null for `"+fullName+"`");
        }
        else try
        {
            slash.handle(event);
            if (!event.isAcknowledged())
            {
                LOG.warn("slash command handler for `"+fullName+"` did not acknowledge");
                event.deferReply(true).queue();
            }
        }
        catch (Exception ex)
        {
            this.interactionError(event, ex);
        }
    }

    public void handle(CommandAutoCompleteInteractionEvent event)
    {
        if (!this.isInGuild(event))
            return;
        String fullName = event.getFullCommandName() + " " + event.getFocusedOption().getName();
        SlashOption<?> option = this.slashOptions.get(fullName);
        if (option == null)
        {
            if (this.warnMissing) LOG.error("slash command option == null for `"+fullName+"`");
        }
        else try
        {
            option.autocomplete(event);
            if (!event.isAcknowledged())
            {
                LOG.warn("slash command option for `"+fullName+"` did not acknowledge");
            }
        }
        catch (Exception ex)
        {
            this.interactionError(event, ex);
        }
    }

    public void handle(MessageContextInteractionEvent event)
    {
        if (!this.isInGuild(event))
            return;
        String fullName = event.getFullCommandName();
        MessageCommandHandler message = this.messageCmds.get(fullName);
        if (message == null)
        {
            if (this.warnMissing) LOG.error("message command handler == null for `"+fullName+"`");
        }
        else try
        {
            message.handle(event);
            if (!event.isAcknowledged())
            {
                LOG.warn("message command handler for `"+fullName+"` did not acknowledge");
                event.deferReply(true).queue();
            }
        }
        catch (Exception ex)
        {
            this.interactionError(event, ex);
        }
    }

    public void handle(UserContextInteractionEvent event)
    {
        if (!this.isInGuild(event))
            return;
        String fullName = event.getFullCommandName();
        UserCommandHandler user = this.userCmds.get(fullName);
        if (user == null)
        {
            if (this.warnMissing) LOG.error("user command handler == null for `"+fullName+"`");
        }
        else try
        {
            user.handle(event);
            if (!event.isAcknowledged())
            {
                LOG.warn("user command handler for `"+fullName+"` did not acknowledge");
                event.deferReply(true).queue();
            }
        }
        catch (Exception ex)
        {
            this.interactionError(event, ex);
        }
    }

    public void handle(ButtonInteractionEvent event)
    {
        if (!this.isInGuild(event))
            return;
        String id = event.getButton().getId(),
               parts[] = id.split(":");
        ButtonClickHandler button = this.buttonClicks.get(parts[0]);
        if (button == null)
        {
            if (this.warnMissing) LOG.error("button click handler == null for `"+id+"`");
        }
        else try
        {
            button.handle(event);
            if (!event.isAcknowledged())
            {
                LOG.warn("button click handler for `"+id+"` did not acknowledge");
                event.deferEdit().queue();
            }
        }
        catch (Exception ex)
        {
            this.interactionError(event, ex);
        }
    }

    public void handle(StringSelectInteractionEvent event)
    {
        if (!this.isInGuild(event))
            return;
        String id = event.getSelectMenu().getId(),
               parts[] = id.split(":");
        StringSelectHandler select = this.stringSelects.get(parts[0]);
        if (select == null)
        {
            if (this.warnMissing) LOG.error("string select handler == null for `"+id+"`");
        }
        else try
        {
            select.handle(event);
            if (!event.isAcknowledged())
            {
                LOG.warn("string select handler for `"+id+"` did not acknowledge");
                event.deferEdit().queue();
            }
        }
        catch (Exception ex)
        {
            this.interactionError(event, ex);
        }
    }

    public void handle(EntitySelectInteractionEvent event)
    {
        if (!this.isInGuild(event))
            return;
        String id = event.getSelectMenu().getId(),
               parts[] = id.split(":");
        EntitySelectHandler select = this.entitySelects.get(parts[0]);
        if (select == null)
        {
            if (this.warnMissing) LOG.error("entity select handler == null for `"+id+"`");
        }
        else try
        {
            select.handle(event);
            if (!event.isAcknowledged())
            {
                LOG.warn("entity select handler for `"+id+"` did not acknowledge");
                event.deferEdit().queue();
            }
        }
        catch (Exception ex)
        {
            this.interactionError(event, ex);
        }
    }

    public void handle(ModalInteractionEvent event)
    {
        if (!this.isInGuild(event))
            return;
        String id = event.getModalId(),
               parts[] = id.split(":");
        ModalSubmitHandler submit = this.modalSubmits.get(parts[0]);
        if (submit == null)
        {
            if (this.warnMissing) LOG.error("modal submit handler == null for `"+id+"`");
        }
        else try
        {
            submit.handle(event);
            if (!event.isAcknowledged())
            {
                LOG.warn("modal submit handler for `"+id+"` did not acknowledge");
                event.deferEdit().queue();
            }
        }
        catch (Exception ex)
        {
            this.interactionError(event, ex);
        }
    }

}
