package net.sybyline.scarlet.server.discord;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
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
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.sybyline.scarlet.util.Func;
import net.sybyline.scarlet.util.MiscUtils;

public class DInteractions
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Discord/DCmd");

    public static final DefaultMemberPermissions DEFAULT_PERMISSIONS = DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS);

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface SlashOpt { String value(); }
    @Target({ElementType.TYPE,ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface SlashCmd { String value(); }
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface MsgCmd { String value(); }
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface UserCmd { String value(); }
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface ButtonClk { String value(); }
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface StringSel { String value(); }
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface EntitySel { String value(); }
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface ModalSub { String value(); }

    @Target({ElementType.TYPE,ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Desc { String value(); }
    @Target({ElementType.TYPE,ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface DefaultPerms { Permission[] value() default {}; }
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Ephemeral { }

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
        if (this.registerSlashCmd(type, object))
            return;
        for (Field field : type.getFields())
            this.registerSlashCmdOption(object, field, null, null);
        for (Method method : type.getMethods())
        {
            if (this.registerSlashCmdHandler(object, method, null, null)) continue;
            if (this.registerMsgCmdHandler(object, method)) continue;
            if (this.registerUserCmdHandler(object, method)) continue;
            if (this.registerButtonClkHandler(object, method)) continue;
            if (this.registerStringSelHandler(object, method)) continue;
            if (this.registerEntitySelHandler(object, method)) continue;
            if (this.registerModalSubHandler(object, method)) continue;
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
    static Permission[] defaultPerms(AnnotatedElement e)
    {
        DefaultPerms dp = e.getDeclaredAnnotation(DefaultPerms.class);
        return dp == null ? Permission.EMPTY_PERMISSIONS : dp.value();
    }
    static String description(AnnotatedElement e)
    {
        Desc d = e.getDeclaredAnnotation(Desc.class);
        return d == null ? "description" : d.value();
    }
    public boolean registerSlashCmd(Class<?> outer, Object receiver, Class<?> cclass)
    {
        SlashCmd sc = cclass.getDeclaredAnnotation(SlashCmd.class);
        if (sc == null)
            return false;
        Object sub = this.ctxNew(outer, receiver, cclass);
        return this.registerSlashCmd(cclass, sub);
    }
    public boolean registerSlashCmd(Class<?> cclass, Object sub)
    {
        SlashCmd sc = cclass.getDeclaredAnnotation(SlashCmd.class);
        if (sc == null)
            return false;
        LOG.trace(String.format("Registering slash command: %s", sc.value()));
        Slash slash = this.slash(sc.value(), description(cclass));
        Permission[] defaultPerms = defaultPerms(cclass);
        slash.data.setDefaultPermissions(defaultPerms.length == 0
            ? DEFAULT_PERMISSIONS
            : DefaultMemberPermissions.enabledFor(defaultPerms));        
        for (Field field : cclass.getFields())
            this.registerSlashCmdOption(sub, field, slash, null);
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
        LOG.trace(String.format("Registering slash command group: %s %s", slash.data.getName(), sc.value()));
        Object sub = this.ctxNew(cclass, receiver, gclass);
        Slash.Group group = slash.group(sc.value(), description(gclass));
        for (Field field : gclass.getFields())
            this.registerSlashCmdOption(sub, field, slash, group);
        for (Method method : gclass.getMethods())
            this.registerSlashCmdHandler(sub, method, slash, group);
        return true;
    }
    public boolean registerSlashCmdHandler(Object receiver, Method method, Slash slash, Slash.Group group)
    {
        SlashCmd sc = method.getDeclaredAnnotation(SlashCmd.class);
        if (sc == null)
            return false;
        LOG.trace(String.format("Registering slash command handler: %s%s%s", slash==null?"":(slash.data.getName()+" "), group==null?"":(group.data.getName()+" "), sc.value()));
        Permission[] defaultPerms = defaultPerms(method);
        String description = description(method);
        int params = method.getParameterCount() - 1;
        Class<?>[] paramTypes = method.getParameterTypes();
        if (!paramTypes[0].isAssignableFrom(SlashCommandInteractionEvent.class))
            throw new IllegalArgumentException(method+": !method.getParameterTypes()[0].isAssignableFrom(SlashCommandInteractionEvent.class)");
        SlashCommandHandler handler;
        
        boolean onlyImpl = slash == null;
        if (onlyImpl) slash = this.slash(sc.value(), description);

        int hookParam;
        Boolean ephemeral = null;
        if (params >= 1 && paramTypes[1].isAssignableFrom(InteractionHook.class))
        {
            hookParam = 1;
            if (!IReplyCallback.class.isAssignableFrom(paramTypes[0]))
                throw new IllegalArgumentException(method+": !IReplyCallback.class.isAssignableFrom(method.getParameterTypes()[0])");
            ephemeral = null != method.getDeclaredAnnotation(Ephemeral.class);
            params--;
        }
        else
        {
            hookParam = 0;
        }
        SlashOption<?>[] options = this.findOptions(slash, group, method, params);
        
        int argidx = -1;
        try
        {
            argidx = Modifier.isStatic(method.getModifiers()) ? 1 : 2;
            MethodHandle mh = MethodHandles.publicLookup().unreflect(method);
            mh = MethodHandles.filterArguments(mh,
                    argidx + hookParam,
                    IntStream.range(0, params)
                    .mapToObj($ -> options[$].mh(paramTypes[$ + 1 + hookParam], paramTypes[0]))
                    .toArray(MethodHandle[]::new));
            
            mh = ephemeral == null ?
                (argidx == 2 ? MethodHandles.permuteArguments(mh,
                    MethodType.methodType(
                        mh.type().returnType(), mh.type().parameterType(0), mh.type().parameterType(1)),
                    IntStream.concat(IntStream.of(0, 1), IntStream.range(0, params).map($ -> 1)).toArray()
                ) : MethodHandles.permuteArguments(mh,
                    MethodType.methodType(
                        mh.type().returnType(), mh.type().parameterType(0)),
                    IntStream.concat(IntStream.of(0), IntStream.range(0, params).map($ -> 0)).toArray()
                )) :
                (argidx == 2 ? MethodHandles.permuteArguments(mh,
                    MethodType.methodType(
                        mh.type().returnType(), mh.type().parameterType(0), mh.type().parameterType(1), mh.type().parameterType(2)),
                    IntStream.concat(IntStream.of(0, 1, 2), IntStream.range(0, params).map($ -> 1)).toArray()
                ) : MethodHandles.permuteArguments(mh,
                    MethodType.methodType(
                        mh.type().returnType(), mh.type().parameterType(0), mh.type().parameterType(1)),
                    IntStream.concat(IntStream.of(0, 1), IntStream.range(0, params).map($ -> 0)).toArray()
                ));
            if (mh.type().returnType() == void.class) mh = MethodHandles.filterReturnValue(mh, MethodHandles.constant(Object.class, null));
            if (argidx == 2) mh = mh.bindTo(receiver);
            handler = this.tryHandleAsyncable(ephemeral, mh)::invoke;
        }
        catch (Exception ex)
        {
            LOG.error(String.format("%d %d %d %s", params, hookParam, argidx, method));
            throw new Error(ex);
        }
        if (onlyImpl)
        {
            DefaultMemberPermissions defaultMemberPerms = defaultPerms.length == 0 ? DEFAULT_PERMISSIONS : DefaultMemberPermissions.enabledFor(defaultPerms);
            slash.impl(handler, defaultMemberPerms, options);
        }
        else if (group == null)
            slash.sub(sc.value(), description, handler, options);
        else
            group.sub(sc.value(), description, handler, options);
        return true;
    }
    public boolean registerSlashCmdOption(Object receiver, Field field, Slash slash, Slash.Group group)
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
        LOG.trace(String.format("Registering slash command option: %s", so.data.getName()));
        if (this.registerOption(slash, group, field.getName(), so))
            return true;
        LOG.debug(String.format("Duplicate/alternate slash command option: %s (%s)", so.data.getName(), field));
        return false;
    }
    public boolean registerMsgCmdHandler(Object receiver, Method method)
    {
        MsgCmd mc = method.getDeclaredAnnotation(MsgCmd.class);
        if (mc == null)
            return false;
        LOG.trace(String.format("Registering message command: %s", mc.value()));
        Permission[] defaultPerms = defaultPerms(method);
        this.message(mc.value(), this.tryHandle(receiver, method, MessageContextInteractionEvent.class)::invoke).setDefaultPermissions(defaultPerms.length == 0 ? DEFAULT_PERMISSIONS : DefaultMemberPermissions.enabledFor(defaultPerms));
        return true;
    }
    public boolean registerUserCmdHandler(Object receiver, Method method)
    {
        UserCmd uc = method.getDeclaredAnnotation(UserCmd.class);
        if (uc == null)
            return false;
        LOG.trace(String.format("Registering user command: %s", uc.value()));
        Permission[] defaultPerms = defaultPerms(method);
        this.user(uc.value(), this.tryHandle(receiver, method, UserContextInteractionEvent.class)::invoke).setDefaultPermissions(defaultPerms.length == 0 ? DEFAULT_PERMISSIONS : DefaultMemberPermissions.enabledFor(defaultPerms));
        return true;
    }
    public boolean registerButtonClkHandler(Object receiver, Method method)
    {
        ButtonClk bc = method.getDeclaredAnnotation(ButtonClk.class);
        if (bc == null)
            return false;
        LOG.trace(String.format("Registering button click: %s", bc.value()));
        return this.button(bc.value(), this.tryHandle(receiver, method, ButtonInteractionEvent.class)::invoke);
    }
    public boolean registerStringSelHandler(Object receiver, Method method)
    {
        StringSel ss = method.getDeclaredAnnotation(StringSel.class);
        if (ss == null)
            return false;
        LOG.trace(String.format("Registering string select: %s", ss.value()));
        return this.string(ss.value(), this.tryHandle(receiver, method, StringSelectInteractionEvent.class)::invoke);
    }
    public boolean registerEntitySelHandler(Object receiver, Method method)
    {
        EntitySel es = method.getDeclaredAnnotation(EntitySel.class);
        if (es == null)
            return false;
        LOG.trace(String.format("Registering entity select: %s", es.value()));
        return this.entity(es.value(), this.tryHandle(receiver, method, EntitySelectInteractionEvent.class)::invoke);
    }
    public boolean registerModalSubHandler(Object receiver, Method method)
    {
        ModalSub ms = method.getDeclaredAnnotation(ModalSub.class);
        if (ms == null)
            return false;
        LOG.trace(String.format("Registering modal submit: %s", ms.value()));
        return this.modal(ms.value(), this.tryHandle(receiver, method, ModalInteractionEvent.class)::invoke);
    }
    public <E> Func.F1.NE<Object, E> tryHandle(Object receiver, Method method, Class<E> event)
    {
        Class<?>[] parameterTypes = method.getParameterTypes();
        int params = method.getParameterCount(),
            spreads = 0;
        if (ComponentInteraction.class.isAssignableFrom(event) || ModalInteraction.class.isAssignableFrom(event))
        {
            while (String.class.equals(parameterTypes[params - 1]))
            {
                params--;
                spreads++;
            }
        }
        if (params != 1 && params != 2)
            throw new IllegalArgumentException(method+": method.getParameterCount() != 1 && method.getParameterCount() != 2");
        if (!parameterTypes[0].isAssignableFrom(event))
            throw new IllegalArgumentException(method+": !method.getParameterTypes()[0].isAssignableFrom("+event.getSimpleName()+".class)");
        Boolean ephemeral = null;
        if (params == 2)
        {
            if (!parameterTypes[1].isAssignableFrom(InteractionHook.class))
                throw new IllegalArgumentException(method+": !method.getParameterTypes()[1].isAssignableFrom(InteractionHook.class)");
            if (!IReplyCallback.class.isAssignableFrom(parameterTypes[0]))
                throw new IllegalArgumentException(method+": !IReplyCallback.class.isAssignableFrom(method.getParameterTypes()[0])");
            ephemeral = null != method.getDeclaredAnnotation(Ephemeral.class);
        }
        try
        {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            int argidx = Modifier.isStatic(method.getModifiers()) ? 0 : 1;
            MethodHandle mh = lookup.unreflect(method);
            if (spreads <= 0)
            {
                ; // noop
            }
            else
            {
                MethodHandle getId,
                             get = MethodHandles.arrayElementGetter(String[].class),
                             split = MethodHandles.insertArguments(lookup.findVirtual(String.class, "split", MethodType.methodType(String[].class, String.class, String.class)), 1, ":");
                if (ComponentInteraction.class.isAssignableFrom(event))
                {
                    getId = lookup.findVirtual(ComponentInteraction.class, "getComponentId", MethodType.methodType(String.class, ComponentInteraction.class));
                }
                else if (ModalInteraction.class.isAssignableFrom(event))
                {
                    getId = lookup.findVirtual(ModalInteraction.class, "getModalId", MethodType.methodType(String.class, ModalInteraction.class));
                }
                else
                {
                    throw new IllegalArgumentException(method+": spreading event "+event+" is neither a ComponentInteraction nor a ModalInteraction");
                }
                int spc = mh.type().parameterCount() - spreads;
                mh = MethodHandles.filterArguments(mh, spc, IntStream.range(0, spreads).mapToObj($ -> MethodHandles.insertArguments(get, 1, $)).toArray(MethodHandle[]::new));
                mh = MethodHandles.permuteArguments(mh, mh.type().dropParameterTypes(spc + 1, mh.type().parameterCount()), IntStream.range(0, mh.type().parameterCount()).map($ -> Math.min($, spc + 1)).toArray());
                mh = MethodHandles.filterArguments(mh, mh.type().parameterCount() - 1, split);
                mh = MethodHandles.filterArguments(mh, mh.type().parameterCount() - 1, getId);
                mh = MethodHandles.permuteArguments(mh, mh.type().dropParameterTypes(mh.type().parameterCount() - 1, mh.type().parameterCount()), IntStream.concat(IntStream.range(0, mh.type().parameterCount() - 1), IntStream.of(argidx)).toArray());
            }
            if (mh.type().returnType() == void.class) mh = MethodHandles.filterReturnValue(mh, MethodHandles.constant(Object.class, null));
            if (argidx == 1) mh = mh.bindTo(receiver);
            return this.tryHandleAsyncable(ephemeral, mh);
        }
        catch (Exception ex)
        {
            throw new Error(ex);
        }
    }
    public <E> Func.F1.NE<Object, E> tryHandleAsyncable(Boolean ephemeral, MethodHandle mh)
    {
        if (ephemeral == null)
        {
            Func.F1<Throwable, Object, E> f1 = mh::invoke;
            return f1.asUnchecked();
        }
        else
        {
            boolean ephemeral0 = ephemeral.booleanValue();
            Func.F2<Throwable, Object, E, InteractionHook> f2 = mh::invoke;
            Func.F2.NE<Object, E, InteractionHook> ne = f2.asUnchecked();
            return $ -> this.handleAsync((IReplyCallback)$, ephemeral0, hook -> ne.invoke($, hook));
        }
    }
    public <T> boolean registerOption(Slash slash, Slash.Group group, String altId, SlashOption<T> option)
    {
        String id = option.data.getName();
        if (group != null)
        {
            if (altId != null)
                group.opts.putIfAbsent(altId, option);
            if (null != group.opts.putIfAbsent(id, option))
                return false;
            return true;
        }
        if (slash != null)
        {
            if (altId != null)
                slash.opts.putIfAbsent(altId, option);
            if (null != slash.opts.putIfAbsent(id, option))
                return false;
            return true;
        }
        if (altId != null)
            this.slashOptionsBound.putIfAbsent(altId, option);
        if (null != this.slashOptionsBound.putIfAbsent(id, option))
            return false;
        return true;
    }
    public SlashOption<?> findOption(Slash slash, Slash.Group group, String id)
    {
        SlashOption<?> ret = null;
        if (group != null) ret = group.opts.get(id);
        if (ret == null) ret = slash.opts.get(id);
        if (ret == null) ret = this.slashOptionsBound.get(id);
        return ret;
    }
    public SlashOption<?>[] findOptions(Slash slash, Slash.Group group, Method method, int params)
    {
        int off = method.getParameterCount() - params;
        Parameter[] parameters = method.getParameters();
        return IntStream.range(0, params).mapToObj(idx ->
        {
            SlashOption<?> ret = null;
            Parameter parameter = parameters[idx + off];
            SlashOpt so = parameter.getDeclaredAnnotation(SlashOpt.class);
            
            if (so != null && !so.value().isEmpty()) ret = this.findOption(slash, group, so.value());
            if (ret == null && parameter.isNamePresent()) ret = this.findOption(slash, group, parameter.getName());
            
            if (ret == null) LOG.error(String.format("No option found for %s:%d (%s) {%s}", method, idx, parameter, so));
            return ret;
        }).toArray(SlashOption[]::new);
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

    public static class SlashOptionStrings implements SlashCompleteHandler
    {
        static final LevenshteinDistance LD = LevenshteinDistance.getDefaultInstance();
        static final Pattern namer = Pattern.compile("[^0-9a-z]");
        static String normalize(String string)
        {
            return namer.matcher(string.toLowerCase()).replaceAll("");
        }
        public SlashOptionStrings(Stream<String> values, boolean includeTyping)
        {
            this(values, includeTyping, $->new String[]{$});
        }
        public SlashOptionStrings(Stream<String> values, boolean includeTyping, Function<String, String[]> names)
        {
            this.values = values.toArray(String[]::new);
            this.includeTyping = includeTyping;
            this.count = this.values.length;
            this.names = Arrays.stream(this.values).map($->Stream.concat(Stream.of($), Arrays.stream(names.apply($))).map(SlashOptionStrings::normalize).toArray(String[]::new)).toArray(String[][]::new);
        }
        final String[] values;
        final boolean includeTyping;
        final int count;
        final String[][] names;
        Comparator<Integer> cmp(String typing)
        {
            String typing_san = normalize(typing);
            return Comparator.<Integer>comparingLong($ ->
            {
                int order = Arrays.stream(this.names[$]).mapToInt($$ -> $$.startsWith(typing_san) ? 0 : $$.contains(typing_san) ? 1 : 2).min().getAsInt(),
                    dist = Arrays.stream(this.names[$]).mapToInt($$ -> LD.apply(typing_san, $$)).min().getAsInt();
                return ((long)order << 32) | ((long)dist & 0xFFFFFFFF);
            });
        }
        public IntStream indices(String typing)
        {
            if (typing == null || (typing = typing.trim()).isEmpty())
                return IntStream.range(0, Math.min(this.count, 25));
            return IntStream
                .range(0, this.count)
                .mapToObj(Integer::valueOf)
                .sorted(this.cmp(typing))
                .mapToInt(Integer::intValue)
                ;
        }
        public String[] values(String typing)
        {
            if (typing == null || (typing = typing.trim()).isEmpty())
                return this.count <= 25 ? this.values : Arrays.copyOf(this.values, 25);
            return Stream.concat(this.includeTyping ? Stream.of(typing) : Stream.empty(), this.indices(typing).mapToObj($ -> this.values[$])).limit(25L).toArray(String[]::new);
        }
        @Override
        public void autocomplete(CommandAutoCompleteInteractionEvent event)
        {
            event.replyChoiceStrings(this.values(event.getFocusedOption().getValue())).queue();
        }
    }
    public static class SlashOptionStringsUnsanitized implements SlashCompleteHandler
    {
        static final LevenshteinDistance LD = LevenshteinDistance.getDefaultInstance();
        public SlashOptionStringsUnsanitized(Supplier<String[]> values, boolean includeTyping)
        {
            this.values = values;
            this.includeTyping = includeTyping;
        }
        final Supplier<String[]> values;
        final boolean includeTyping;
        @Override
        public void autocomplete(CommandAutoCompleteInteractionEvent event)
        {
            autocomplete(event, this.values.get(), this.includeTyping);
        }
        public static void autocomplete(CommandAutoCompleteInteractionEvent event, String[] values, boolean includeTyping)
        {
            String typing = event.getFocusedOption().getValue().trim();
            if (!typing.isEmpty())
            {
                values = (Stream.concat(includeTyping ? Stream.of(typing) : Stream.empty(), Arrays.stream(values).sorted(Comparator.comparingInt($ -> LD.apply(typing, $))))).limit(25L).toArray(String[]::new);
            }
            else if (values.length > 25)
            {
                values = Arrays.copyOf(values, 25);
            }
            event.replyChoiceStrings(values).queue();
        }
    }
    public static class SlashOptionsChoicesUnsanitized implements SlashCompleteHandler
    {
        static final LevenshteinDistance LD = LevenshteinDistance.getDefaultInstance();
        public SlashOptionsChoicesUnsanitized(Supplier<Command.Choice[]> values, boolean includeTyping)
        {
            this.values = values;
            this.includeTyping = includeTyping;
        }
        final Supplier<Command.Choice[]> values;
        final boolean includeTyping;
        @Override
        public void autocomplete(CommandAutoCompleteInteractionEvent event)
        {
            autocomplete(event, this.values.get(), this.includeTyping);
        }
        public static void autocomplete(CommandAutoCompleteInteractionEvent event, Command.Choice[] values, boolean includeTyping)
        {
            String typing = event.getFocusedOption().getValue().trim();
            if (!typing.isEmpty())
            {
                values = (Stream.concat(includeTyping ? Stream.of(typing) : Stream.empty(), Arrays.stream(values).sorted(Comparator.comparingInt($ -> Math.min(LD.apply(typing, $.getName()), LD.apply(typing, $.getAsString())))))).limit(25L).toArray(Command.Choice[]::new);
            }
            else if (values.length > 25)
            {
                values = Arrays.copyOf(values, 25);
            }
            event.replyChoices(values).queue();
        }
    }
    public static class SlashOption<T>
    {
        public static SlashOption<Boolean> ofBool(String name, String desc, boolean required, Boolean fallback)
        {
            return new SlashOption<>(OptionType.BOOLEAN, name, desc, required, fallback, OptionMapping::getAsBoolean);
        }
        public static SlashOption<Integer> ofInt(String name, String desc, boolean required, Integer fallback)
        {
            return ofInt(name, desc, required, fallback, null);
        }
        public static SlashOption<Integer> ofInt(String name, String desc, boolean required, Integer fallback, SlashCompleteHandler autocomplete)
        {
            return new SlashOption<>(OptionType.INTEGER, name, desc, required, fallback, OptionMapping::getAsInt, autocomplete);
        }
        public static SlashOption<Long> ofLong(String name, String desc, boolean required, Long fallback)
        {
            return ofLong(name, desc, required, fallback, null);
        }
        public static SlashOption<Long> ofLong(String name, String desc, boolean required, Long fallback, SlashCompleteHandler autocomplete)
        {
            return new SlashOption<>(OptionType.INTEGER, name, desc, required, fallback, OptionMapping::getAsLong, autocomplete);
        }
        public static SlashOption<Double> ofDouble(String name, String desc, boolean required, Double fallback)
        {
            return ofDouble(name, desc, required, fallback, null);
        }
        public static SlashOption<Double> ofDouble(String name, String desc, boolean required, Double fallback, SlashCompleteHandler autocomplete)
        {
            return new SlashOption<>(OptionType.NUMBER, name, desc, required, fallback, OptionMapping::getAsDouble, autocomplete);
        }
        public static SlashOption<String> ofString(String name, String desc, boolean required, String fallback)
        {
            return ofString(name, desc, required, fallback, null);
        }
        public static SlashOption<String> ofString(String name, String desc, boolean required, String fallback, SlashCompleteHandler autocomplete)
        {
            return new SlashOption<>(OptionType.STRING, name, desc, required, fallback, OptionMapping::getAsString, autocomplete);
        }
        public static <T> SlashOption<T> ofString(String name, String desc, boolean required, T fallback, Function<String, T> parser, boolean trycatch, SlashCompleteHandler autocomplete)
        {
            return new SlashOption<>(OptionType.STRING, name, desc, required, fallback, (trycatch ? (Function<String, T>)($)->{try{return parser.apply($);}catch(Exception e){return fallback;}} : parser).compose(OptionMapping::getAsString), autocomplete);
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
        public static SlashOption<Channel> ofChannel(String name, String desc, boolean required, ChannelType... types)
        {
            return ofChannel(name, desc, required).with($ -> $.setChannelTypes(types));
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
        public static <DE extends Enum<DE> & DEnum<DE, ?>> SlashOption<DE> ofEnum(String name, String desc, boolean required, DE _instance, DE fallback)
        {
            return new SlashOption<>(_instance.type(), name, desc, required, fallback, ((DEnum<DE, ?>)_instance)::mapEnum, _instance.choices());
        }
        public static <DE extends Enum<DE> & DEnum<DE, ?>> SlashOption<DE> ofEnum(String name, String desc, boolean required, DE fallback)
        {
            return ofEnum(name, desc, required, fallback, fallback);
        }
        public static <DE extends Enum<DE> & DEnum<DE, ?>> SlashOption<DE> ofEnum(String name, String desc, boolean required, Class<DE> clazz)
        {
            return ofEnum(name, desc, required, clazz.getEnumConstants()[0], null);
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
            this.additional = null;
        }
        public Pagination withAdditional(ObjIntConsumer<WebhookMessageEditAction<Message>> additional)
        {
            this.additional = additional;
            return this;
        }
        final String paginationId;
        final Paginator[] pages;
        InteractionHook hook;
        String messageId;
        ObjIntConsumer<WebhookMessageEditAction<Message>> additional;
        boolean removeIfExpired()
        {
            if (this.hook == null || !this.hook.isExpired())
                return false;
            DInteractions.this.pagination.remove(this.paginationId);
            return true;
        }
        WebhookMessageEditAction<Message> action(int pageOrdinal)
        {
            WebhookMessageEditAction<Message> action = this._action(pageOrdinal);
            ObjIntConsumer<WebhookMessageEditAction<Message>> additional = this.additional;
            if (additional != null) try
            {
                additional.accept(action, pageOrdinal);
            }
            catch (Exception ex)
            {
                LOG.warn("Exception in pagination additional", ex);
            }
            return action;
        }
        WebhookMessageEditAction<Message> _action(int pageOrdinal)
        {
            if (pageOrdinal < 1) pageOrdinal = 1;
            if (pageOrdinal > this.pages.length) pageOrdinal = this.pages.length;
            
            Button first = Button.secondary("pagination-first:"+this.paginationId+":1", "First"),
                   prev = Button.success("pagination:"+this.paginationId+":"+(pageOrdinal - 1), "Prev"),
                   self = Button.primary("pagination-select:"+this.paginationId+":"+pageOrdinal, pageOrdinal+"/"+pages.length),
                   next = Button.success("pagination:"+this.paginationId+":"+(pageOrdinal + 1), "Next"),
                   last = Button.secondary("pagination-last:"+this.paginationId+":"+this.pages.length, "Last");
            
            if (pageOrdinal == 1)
            {
                first = first.asDisabled();
                prev = prev.asDisabled();
            }
            if (pageOrdinal == this.pages.length)
            {
                next = next.asDisabled();
                last = last.asDisabled();
            }
            
            int pageIndex = pageOrdinal - 1;
            if (pageIndex < 0)
            {
                MessageEmbed embed = new EmbedBuilder().setDescription("There's nothing to see here.").build();
                return (this.messageId == null
                    ? this.hook.editOriginalEmbeds(embed)
                    : this.hook.editMessageEmbedsById(this.messageId, embed))
                        .setActionRow(first.asDisabled(), prev.asDisabled(), self.asDisabled(), next.asDisabled(), last.asDisabled());
            }
            return this
                .pages[pageIndex]
                .applyContent(this.hook, this.messageId)
                .setActionRow(first, prev, self, next, last);
        }
        public void queue(InteractionHook hook)
        {
            this.hook = hook;
            this.action(1).queue(message -> {
                this.messageId = message.getId();
                DInteractions.this.pagination.put(this.paginationId, this);
            });
        }
    }

    public DInteractions(boolean warnMissing, ExecutorService exec)
    {
        this.warnMissing = warnMissing;
        this.exec = exec;
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

    final boolean warnMissing;
    final ExecutorService exec;

    public List<CommandData> getCommandDataMutable()
    {
        return this.data;
    }

    public String[] getSlashCmdIds()
    {
        return this.slashCmds.keySet().toArray(new String[0]);
    }
    public String[] getMessageCmdIds()
    {
        return this.messageCmds.keySet().toArray(new String[0]);
    }
    public String[] getUserCmdIds()
    {
        return this.userCmds.keySet().toArray(new String[0]);
    }
    public String[] getButtonClickIds()
    {
        return this.buttonClicks.keySet().toArray(new String[0]);
    }
    public String[] getStringSelectIds()
    {
        return this.stringSelects.keySet().toArray(new String[0]);
    }
    public String[] getEntitySelectIds()
    {
        return this.entitySelects.keySet().toArray(new String[0]);
    }
    public String[] getModalSubmitIds()
    {
        return this.modalSubmits.keySet().toArray(new String[0]);
    }

    public Slash slash(String name, String desc)
    {
        return new Slash(name, desc);
    }
    public class Slash
    {
        Slash(String name, String desc)
        {
            this.data = Commands.slash(name, desc).setGuildOnly(true);
            this.subs = new HashMap<>();
            this.opts = new HashMap<>();
            DInteractions.this.data.add(this.data);
            DInteractions.this.slashBuilders.put(name, this);
        }
        final SlashCommandData data;
        final Map<String, Group> subs;
        final Map<String, SlashOption<?>> opts;
        public class Group
        {
            Group(String name, String desc)
            {
                this.data = new SubcommandGroupData(name, desc);
                this.opts = new HashMap<>();
                Slash.this.data.addSubcommandGroups(this.data);
                Slash.this.subs.put(name, this);
            }
            final SubcommandGroupData data;
            final Map<String, SlashOption<?>> opts;
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
        public Slash impl(SlashCommandHandler handler, DefaultMemberPermissions defaultMemberPerms, SlashOption<?>... options)
        {
            String name = this.data.getName();
            this.data.setDefaultPermissions(defaultMemberPerms);
            DInteractions.this.slashCmds.put(name, handler);
            for (SlashOption<?> option : options)
            {
                this.data.addOptions(option.data);
                DInteractions.this.slashOptions.put(name + " " + option.data.getName(), option);
            }
            return this;
        }
        public Group group(String name, String desc)
        {
            return new Group(name, desc);
        }
        public Slash sub(String name, String desc, SlashCommandHandler handler, SlashOption<?>... options)
        {
            String subName = this.data.getName() + " " + name;
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

    public boolean button(String name, ButtonClickHandler handler)
    {
        return this.buttonClicks.putIfAbsent(name, handler) != null;
    }

    public boolean string(String name, StringSelectHandler handler)
    {
        return this.stringSelects.putIfAbsent(name, handler) != null;
    }

    public boolean entity(String name, EntitySelectHandler handler)
    {
        return this.entitySelects.putIfAbsent(name, handler) != null;
    }

    public boolean modal(String name, ModalSubmitHandler handler)
    {
        return this.modalSubmits.putIfAbsent(name, handler) != null;
    }

    public Object handleAsync(IReplyCallback event, boolean ephemeral, DeferredHandler handler)
    {
        event.deferReply(ephemeral).queue(hook -> this.exec.execute(() ->
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
        return null;
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

    boolean handlePagination(ButtonInteractionEvent event, String id, String[] parts, String op)
    {
        switch (op)
        {
        default:
            return false;
        case "pagination":
        case "pagination-first":
        case "pagination-last":
        case "pagination-select":
            break;
        }
        try
        {
            String paginationId = parts[1];
            int pageOrdinal = MiscUtils.parseIntElse(parts[2], 1);
            Pagination pagination = this.pagination.get(paginationId);
            if (pagination == null || pagination.removeIfExpired())
            {
                event.reply("Pagination interaction expired")
                    .setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(3L, TimeUnit.SECONDS));
            }
            else switch (op)
            {
            case "pagination": {
                pagination.action(pageOrdinal).queue();
                event.deferEdit().queue();
            } break;
            case "pagination-first": {
                pagination.action(1).queue();
                event.deferEdit().queue();
            } break;
            case "pagination-last": {
                pagination.action(pagination.pages.length).queue();
                event.deferEdit().queue();
            } break;
            case "pagination-select": {
                event.replyModal(Modal.create(event.getButton().getId(), "Pagination")
                    .addActionRow(TextInput.create("pagination-ordinal", "Page (1 to "+pagination.pages.length+", inclusive)", TextInputStyle.SHORT)
                        .setRequiredRange(1, Integer.toString(pagination.pages.length).length())
                        .setValue(Integer.toString(pageOrdinal))
                        .build())
                    .build()).queue();
            } break;
            default: throw new Exception("Unknown pagination action for "+id);
            }
        }
        catch (Exception ex)
        {
            LOG.error("Exception sync handling of "+id, ex);
            event.reply("Exception sync handling of "+id+":\n`"+ex+"`").setEphemeral(true).queue();
        }
        return true;
    }

    boolean handlePagination(ModalInteractionEvent event, String id, String[] parts, String op)
    {
        switch (op)
        {
        default:
            return false;
        case "pagination-select":
            break;
        }
        try
        {
            String paginationId = parts[1];
            Pagination pagination = this.pagination.get(paginationId);
            if (pagination == null || pagination.removeIfExpired())
            {
                event.reply("Pagination interaction expired")
                    .setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(3L, TimeUnit.SECONDS));
            }
            else
            {
                int pageOrdinal = Integer.parseInt(event.getValue("pagination-ordinal").getAsString());
                if (pageOrdinal < 1 || pageOrdinal > pagination.pages.length)
                {
                    event.reply("Invalid page, must be an integer from 1 to "+pagination.pages.length+", inclusive").setEphemeral(true).queue();
                }
                else
                {
                    pagination.action(pageOrdinal).queue();
                    event.deferEdit().queue();
                }
            }
        }
        catch (Exception ex)
        {
            LOG.error("Exception sync handling of "+id, ex);
            event.reply("Exception sync handling of "+id+":\n`"+ex+"`").setEphemeral(true).queue();
        }
        return true;
    }

    public void clearDeadPagination()
    {
        new ArrayList<>(this.pagination.values()).forEach(Pagination::removeIfExpired);
    }

    public boolean handle(SlashCommandInteractionEvent event)
    {
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
        return event.isAcknowledged();
    }

    public boolean handle(CommandAutoCompleteInteractionEvent event)
    {
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
        return event.isAcknowledged();
    }

    public boolean handle(MessageContextInteractionEvent event)
    {
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
        return event.isAcknowledged();
    }

    public boolean handle(UserContextInteractionEvent event)
    {
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
        return event.isAcknowledged();
    }

    public boolean handle(ButtonInteractionEvent event)
    {
        String id = event.getButton().getId(),
               parts[] = id.split(":"),
               op = parts[0];
        if (this.handlePagination(event, id, parts, op))
            return true;
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
        return event.isAcknowledged();
    }

    public boolean handle(StringSelectInteractionEvent event)
    {
        String id = event.getSelectMenu().getId(),
               parts[] = id.split(":"),
               op = parts[0];
        StringSelectHandler select = this.stringSelects.get(op);
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
        return event.isAcknowledged();
    }

    public boolean handle(EntitySelectInteractionEvent event)
    {
        String id = event.getSelectMenu().getId(),
               parts[] = id.split(":"),
               op = parts[0];
        EntitySelectHandler select = this.entitySelects.get(op);
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
        return event.isAcknowledged();
    }

    public boolean handle(ModalInteractionEvent event)
    {
        String id = event.getModalId(),
               parts[] = id.split(":"),
               op = parts[0];
        if (this.handlePagination(event, id, parts, op))
            return true;
        ModalSubmitHandler submit = this.modalSubmits.get(op);
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
        return event.isAcknowledged();
    }

}
