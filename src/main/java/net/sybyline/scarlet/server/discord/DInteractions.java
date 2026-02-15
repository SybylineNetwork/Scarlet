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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.vrchatapi.model.GroupRole;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.label.LabelChildComponent;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
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
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.sybyline.scarlet.util.Func;
import net.sybyline.scarlet.util.MiscUtils;

public class DInteractions
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Discord/DCmd");

    public static final DefaultMemberPermissions DEFAULT_PERMISSIONS = DefaultMemberPermissions.enabledFor(Permission.USE_APPLICATION_COMMANDS);

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface SlashOpt { String value(); }
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Required { boolean value() default true; }
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
    @Target({ElementType.METHOD,ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface StringSel { String value(); }
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface EntitySel { String value(); }
    @Target({ElementType.METHOD,ElementType.TYPE})
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
        if (this.registerModalFlow(type, object, null, null))
            return;
        for (Field field : type.getFields())
        {
            if (this.registerSlashCmdOption(object, field, null, null)) continue;
            if (this.registerModalFlowOption(object, field, null, null)) continue;
        }
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
        {
            if (this.registerSlashCmd(type, object, clazz)) continue;
            if (this.registerModalFlow(clazz, object, null, null)) continue;
        }
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
    public boolean registerSlashCmd(Class<?> cclass, Object receiver)
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
        {
            if (this.registerSlashCmdOption(receiver, field, slash, null)) continue;
            if (this.registerModalFlowOption(receiver, field, slash, null)) continue;
        }
        for (Method method : cclass.getMethods())
        {
            if (this.registerSlashCmdHandler(receiver, method, slash, null)) continue;
        }
        for (Class<?> gclass : cclass.getClasses())
        {
            if (this.registerSlashCmdGrp(cclass, receiver, gclass, slash)) continue;
            if (this.registerModalFlow(gclass, receiver, slash, null)) continue;
        }
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
        {
            if (this.registerSlashCmdOption(sub, field, slash, group)) continue;
            if (this.registerModalFlowOption(sub, field, slash, group)) continue;
        }
        for (Method method : gclass.getMethods())
        {
            if (this.registerSlashCmdHandler(sub, method, slash, group)) continue;
        }
        for (Class<?> _class : gclass.getClasses())
        {
            if (this.registerModalFlow(_class, receiver, slash, group)) continue;
        }
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
    public boolean registerModalFlow(Class<?> cclass, Object receiver, Slash slash, Slash.Group group)
    {
        if (!ModalFlow.class.isAssignableFrom(cclass))
            return false;
        ModalSub ms = cclass.getDeclaredAnnotation(ModalSub.class);
        if (ms == null)
            return false;
        LOG.trace(String.format("Registering modal flow: %s", ms.value()));
        for (Field field : cclass.getFields())
            this.registerModalFlowOption(receiver, field, slash, group);
        @SuppressWarnings("unchecked")
        Class<? extends ModalFlow<?>> mfclass = (Class<? extends ModalFlow<?>>)cclass;
        @SuppressWarnings({ "unchecked", "rawtypes" })
        ModalFlowInfo<?> info = new ModalFlowInfo(mfclass, slash, group);
        if (this.modalFlowInfos.putIfAbsent(mfclass, info) != null)
            throw new IllegalStateException(String.format("Duplicate ModalFlowInfo for %s", mfclass));
        return this.modal(ms.value(), info);
    }
    public boolean registerModalFlowOption(Object receiver, Field field, Slash slash, Slash.Group group)
    {
        if (!ModalFlowOption.class.isAssignableFrom(field.getType()))
            return false;
        ModalFlowOption<?> mfo;
        try
        {
            mfo = (ModalFlowOption<?>)field.get(receiver);
        }
        catch (Exception ex)
        {
            throw new Error(ex);
        }
        if (mfo == null)
            return false;
        LOG.trace(String.format("Registering modal flow option: %s", mfo.name));
        if (this.registerModalFlowOption(slash, group, field.getName(), mfo))
            return true;
        LOG.debug(String.format("Duplicate/alternate modal flow option: %s (%s)", mfo.id, field));
        return false;
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
    public <T> boolean registerModalFlowOption(Slash slash, Slash.Group group, String altId, ModalFlowOption<T> option)
    {
        String id = option.id;
        if (group != null)
        {
            if (altId != null)
                group.opts_modal.putIfAbsent(altId, option);
            if (null != group.opts_modal.putIfAbsent(id, option))
                return false;
            return true;
        }
        if (slash != null)
        {
            if (altId != null)
                slash.opts_modal.putIfAbsent(altId, option);
            if (null != slash.opts_modal.putIfAbsent(id, option))
                return false;
            return true;
        }
        if (altId != null)
            this.modalFlowOptionsBound.putIfAbsent(altId, option);
        if (null != this.modalFlowOptionsBound.putIfAbsent(id, option))
            return false;
        return true;
    }
    public SlashOption<?> findOption(Slash slash, Slash.Group group, String id)
    {
        SlashOption<?> ret = null;
        if (group != null) ret = group.opts.get(id);
        if (ret == null && slash != null) ret = slash.opts.get(id);
        if (ret == null) ret = this.slashOptionsBound.get(id);
        return ret;
    }
    public ModalFlowOption<?> findModalFlowOption(Slash slash, Slash.Group group, String id)
    {
        ModalFlowOption<?> ret = null;
        if (group != null) ret = group.opts_modal.get(id);
        if (ret == null && slash != null) ret = slash.opts_modal.get(id);
        if (ret == null) ret = this.modalFlowOptionsBound.get(id);
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
            Required req = parameter.getDeclaredAnnotation(Required.class);
            
            if (so != null && !so.value().isEmpty()) ret = this.findOption(slash, group, so.value());
            if (ret == null && parameter.isNamePresent()) ret = this.findOption(slash, group, parameter.getName());
            
            if (ret == null) LOG.error(String.format("No option found for %s:%d (%s) {%s}", method, idx, parameter, so));
            
            if (ret != null && req != null && req.value() != ret.data.isRequired()) ret = ret.with($ -> $.setRequired(req.value()));
            
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

    public static final LevenshteinDistance LD = LevenshteinDistance.getDefaultInstance();
    public static Comparator<String> stringsByLevenshtein(String typing)
    {
        return Comparator.comparingInt($ -> LD.apply(typing, $));
    }
    public static Comparator<Command.Choice> choicesByLevenshtein(String typing)
    {
        return Comparator.comparingInt($ -> Math.min(LD.apply(typing, $.getName()), LD.apply(typing, $.getAsString())));
    }
    public static enum SlashOptionLocalTime implements SlashCompleteHandler
    {
        INSTANCE;
        @Override
        public void autocomplete(CommandAutoCompleteInteractionEvent event)
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
        public static LocalTime localTime(CharSequence charSequence)
        {
            return LocalTime.parse(charSequence);
        }
        public static Duration duration(CharSequence charSequence)
        {
            LocalTime time = localTime(charSequence);
            return Duration.ofHours(time.getHour()).plusMinutes(time.getMinute());
        }
    }
    public static enum SlashOptionLocalDate implements SlashCompleteHandler
    {
        INSTANCE;
        @Override
        public void autocomplete(CommandAutoCompleteInteractionEvent event)
        {
            LocalDate nowDate = LocalDate.now(ZoneOffset.UTC);
            String value = event.getFocusedOption().getValue().trim();
            if (value.isEmpty())
            {
                event.replyChoiceStrings(IntStream.range(0, 25).mapToObj($ -> nowDate.plusDays($).toString()).toArray(String[]::new)).queue();
                return;
            }
            Matcher m = Pattern.compile("(?<y>\\+?\\d\\d\\d\\d+)(-(?<m>\\d\\d?)?(-(?<d>\\d\\d?))?)?").matcher(value);
            if (m.matches())
            {
                String y = m.group("y"),
                       M = m.group("m"),
                       d = m.group("d");
                if (M == null)
                {
                    List<String> strings = IntStream.range(1, 13).mapToObj($ -> String.format("%s:%02d", y, $*5)).collect(Collectors.toList());
                    if (!strings.contains(value))
                        strings.add(0, value);
                    event.replyChoiceStrings(strings).queue();
                }
                else if (d == null)
                {
                    YearMonth ym = YearMonth.of(Integer.parseUnsignedInt(y, 10), Integer.parseUnsignedInt(M, 10));
                    if (nowDate.getYear() == ym.getYear() && nowDate.getMonthValue() == ym.getMonthValue())
                    {
                        event.replyChoiceStrings(IntStream.range(0, 24).mapToObj($ -> nowDate.plusDays($).toString()).toArray(String[]::new)).queue();
                    }
                    else
                    {
                        event.replyChoiceStrings(IntStream.range(1, 25).mapToObj($ -> ym.atDay($).toString()).toArray(String[]::new)).queue();
                    }
                }
                else
                {
                    event.replyChoiceStrings(value).queue();
                }
                return;
            }
            event.replyChoiceStrings().queue();
        }
        public static LocalDate localDate(CharSequence charSequence)
        {
            return LocalDate.parse(charSequence);
        }
    }
    public static class SlashOptionStrings implements SlashCompleteHandler
    {
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
                values = (Stream.concat(includeTyping ? Stream.of(typing) : Stream.empty(), Arrays.stream(values).sorted(stringsByLevenshtein(typing)))).limit(25L).toArray(String[]::new);
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
                values = (Stream.concat(includeTyping ? Stream.of(new Command.Choice(typing, typing)) : Stream.empty(), Arrays.stream(values).sorted(choicesByLevenshtein(typing)))).limit(25L).toArray(Command.Choice[]::new);
            }
            else if (values.length > 25)
            {
                values = Arrays.copyOf(values, 25);
            }
            event.replyChoices(values).queue();
        }
    }
    public static class SlashOptionsMultiChoicesUnsanitized<DE extends Enum<DE> & DEnum<DE, String>> implements SlashCompleteHandler
    {
        public SlashOptionsMultiChoicesUnsanitized(Supplier<Command.Choice[]> values, boolean includeTyping)
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
                values = (Stream.concat(includeTyping ? Stream.of(new Command.Choice(typing, typing)) : Stream.empty(), Arrays.stream(values).sorted(choicesByLevenshtein(typing)))).limit(25L).toArray(Command.Choice[]::new);
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
        public static SlashOption<Integer> ofInt(String name, String desc, boolean required, Integer fallback, int minimum, int maximum)
        {
            return ofInt(name, desc, required, fallback).with(data -> data.setRequiredRange(minimum, maximum));
        }
        public static SlashOption<Integer> ofInt(String name, String desc, boolean required, Integer fallback)
        {
            return ofInt(name, desc, required, fallback, null);
        }
        public static SlashOption<Integer> ofInt(String name, String desc, boolean required, Integer fallback, SlashCompleteHandler autocomplete)
        {
            return new SlashOption<>(OptionType.INTEGER, name, desc, required, fallback, OptionMapping::getAsInt, autocomplete);
        }
        public static SlashOption<Long> ofLong(String name, String desc, boolean required, Long fallback, long minimum, long maximum)
        {
            return ofLong(name, desc, required, fallback).with(data -> data.setRequiredRange(minimum, maximum));
        }
        public static SlashOption<Long> ofLong(String name, String desc, boolean required, Long fallback)
        {
            return ofLong(name, desc, required, fallback, null);
        }
        public static SlashOption<Long> ofLong(String name, String desc, boolean required, Long fallback, SlashCompleteHandler autocomplete)
        {
            return new SlashOption<>(OptionType.INTEGER, name, desc, required, fallback, OptionMapping::getAsLong, autocomplete);
        }
        public static SlashOption<Double> ofDouble(String name, String desc, boolean required, Double fallback, double minimum, double maximum)
        {
            return ofDouble(name, desc, required, fallback).with(data -> data.setRequiredRange(minimum, maximum));
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
        public static SlashOption<ZoneId> ofZoneId(String name, String desc, boolean required, ZoneId fallback)
        {
            return SlashOption.ofString(name, desc, true, fallback, ZoneId::of, true, new SlashOptionStrings(ZoneId.getAvailableZoneIds().stream().sorted(), true));
        }
        public static SlashOption<LocalDate> ofLocalDate(String name, String desc, boolean required, LocalDate fallback)
        {
            return ofString(name, desc, required, fallback, SlashOptionLocalDate::localDate, false, SlashOptionLocalDate.INSTANCE);
        }
        public static SlashOption<LocalTime> ofLocalTime(String name, String desc, boolean required, LocalTime fallback)
        {
            return ofString(name, desc, required, fallback, SlashOptionLocalTime::localTime, false, SlashOptionLocalTime.INSTANCE);
        }
        public static SlashOption<Duration> ofDuration(String name, String desc, boolean required, Duration fallback)
        {
            return ofString(name, desc, required, fallback, SlashOptionLocalTime::duration, false, SlashOptionLocalTime.INSTANCE);
        }
        public static <DES extends Enum<DES> & DEnum.DEnumString<DES>> SlashOption<DES[]> ofUniqueEnums(String name, String desc, boolean required, String delimiter, int minChoices, int maxChoices, DES _instance, DES[] fallback)
        {
            return new SlashOption<>(new OptionData(OptionType.STRING, name, desc, required), fallback, _instance.mapEnums(delimiter, minChoices, maxChoices), _instance.autocomplete(delimiter, minChoices, maxChoices));
        }
        public static <DES extends Enum<DES> & DEnum.DEnumString<DES>> SlashOption<DES[]> ofUniqueEnums(String name, String desc, boolean required, String delimiter, int minChoices, int maxChoices, DES[] fallback)
        {
            return ofUniqueEnums(name, desc, required, delimiter, minChoices, maxChoices, fallback[0], fallback);
        }
        public static <DES extends Enum<DES> & DEnum.DEnumString<DES>> SlashOption<DES[]> ofUniqueEnums(String name, String desc, boolean required, String delimiter, int minChoices, int maxChoices, Class<DES> clazz)
        {
            return ofUniqueEnums(name, desc, required, delimiter, minChoices, maxChoices, clazz.getEnumConstants()[0], Arrays.copyOf(clazz.getEnumConstants(), 0));
        }
        public static <E extends Enum<E>> SlashOption<E> ofUniqueDOptionEnums(DOptionEnum<E> dOptionEnum, boolean required, String delimiter, int minChoices, int maxChoices)
        {
            return new SlashOption<E>(OptionType.STRING, dOptionEnum.name, dOptionEnum.description, required, null, dOptionEnum::getAsEnum, dOptionEnum.choices, delimiter, minChoices, maxChoices);
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
        SlashOption(OptionType type, String name, String desc, boolean required, T fallback, Function<? super OptionMapping, ? extends T> resolver, Command.Choice[] choices, String delimiter, int minChoices, int maxChoices)
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
            this(data.setAutoComplete(choices != null && choices.length > 25), fallback, resolver, choices == null || choices.length <= 25 ? (SlashCompleteHandler)null : new SlashOptionsChoicesUnsanitized(() -> choices, true));
            if (choices != null && choices.length <= 25)
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
    public static class ModalFlowOption<T>
    {
        public static ModalFlowOption<String> ofString(String id, String name, String desc, boolean required, String placeholder, int minLength, int maxLength, TextInputStyle style, String defaultPopulate)
        {
            TextInput.Builder builder = TextInput
                .create(id, style)
                .setRequired(required)
                .setPlaceholder(placeholder)
                .setValue(defaultPopulate)
                .setRequiredRange(minLength, maxLength)
            ;
            TextInput base = builder.build();
            Function<? super String, ? extends LabelChildComponent> populator = populateValue -> {
                if (populateValue == null)
                    return base;
                synchronized (builder) // Avoid concurrent invocations setting builder value
                {
                    return builder.setValue(populateValue).build();
                }
            };
            return new ModalFlowOption<>(id, name, desc, ModalMapping::getAsString, populator);
        }
        public static <DE extends Enum<DE> & DEnum<DE, ?>> ModalFlowOption<DE> ofEnum(String id, String name, String desc, boolean required, String placeholder, Class<DE> clazz, DE defaultPopulate)
        {
            DE _const = defaultPopulate != null ? defaultPopulate : clazz.getEnumConstants()[0];
            StringSelectMenu.Builder builder = StringSelectMenu
                .create(id)
                .setRequired(required)
                .setPlaceholder(placeholder)
                .addOptions(_const.options())
                ;
            if (defaultPopulate != null)
                builder = builder.setDefaultValues(defaultPopulate.value().toString());
            StringSelectMenu base = builder.build();
            Function<? super DE, ? extends LabelChildComponent> populator = populateValue -> populateValue == null ? base : base.createCopy().setDefaultValues(populateValue.value().toString()).build();
            return new ModalFlowOption<>(id, name, desc, ((DEnum<DE, ?>)_const)::mapEnum, populator);
        }
        public static <DES extends Enum<DES> & DEnum.DEnumString<DES>> ModalFlowOption<DES[]> ofUniqueEnums(String id, String name, String desc, boolean required, String placeholder, int minChoices, int maxChoices, Class<DES> clazz, DES[] defaultPopulate)
        {
            DES _const = (defaultPopulate != null && defaultPopulate.length > 0 && defaultPopulate[0] != null ? defaultPopulate : clazz.getEnumConstants())[0];
            StringSelectMenu.Builder builder = StringSelectMenu.create(id)
                .setRequired(required)
                .setPlaceholder(placeholder)
                .addOptions(clazz.getEnumConstants()[0].options())
                .setRequiredRange(minChoices, maxChoices)
                ;
            if (defaultPopulate != null)
                builder = builder.setDefaultValues(MiscUtils.map(defaultPopulate, String[]::new, DES::value));
            StringSelectMenu base = builder.build();
            Function<? super DES[], ? extends LabelChildComponent> populator = populateValue -> populateValue == null ? base : base.createCopy().setDefaultValues(MiscUtils.map(defaultPopulate, String[]::new, DES::value)).build();
            return new ModalFlowOption<DES[]>(id, name, desc, ((DEnum.DEnumString<DES>)_const)::mapEnums, populator);
        }
        public static ModalFlowOption<GroupRole[]> ofUniqueGroupRoles(String id, String name, String desc, boolean required, String placeholder, int minChoices, int maxChoices, Map<String, GroupRole> map)
        {
            StringSelectMenu.Builder builder = StringSelectMenu.create(id)
                .setRequired(required)
                .setPlaceholder(placeholder)
                ;
            Function<? super ModalMapping, ? extends GroupRole[]> resolver = mapping -> mapping.getAsStringList().stream().map(map::get).filter(Objects::nonNull).toArray(GroupRole[]::new);
            Function<? super GroupRole[], ? extends LabelChildComponent> populator = populateValue -> builder.setRequiredRange(minChoices, Math.min(maxChoices, Math.min(map.size(), 25))).addOptions(map.entrySet().stream().limit(25L).map($ -> SelectOption.of($.getValue().getName(), $.getKey())).toArray(SelectOption[]::new)).setDefaultValues(populateValue == null ? new String[0] : MiscUtils.map(populateValue, String[]::new, GroupRole::getId)).build();
            return new ModalFlowOption<GroupRole[]>(id, name, desc, resolver, populator);
        }
        public static ModalFlowOption<GroupRole[]> ofUniqueGroupRolesNext25(String id, String name, String desc, boolean required, String placeholder, int minChoices, int maxChoices, Map<String, GroupRole> map)
        {
            StringSelectMenu.Builder builder = StringSelectMenu.create(id)
                .setRequired(required)
                .setPlaceholder(placeholder)
                .setRequiredRange(minChoices, maxChoices)
                ;
            Function<? super ModalMapping, ? extends GroupRole[]> resolver = mapping -> mapping.getAsStringList().stream().map(map::get).filter(Objects::nonNull).toArray(GroupRole[]::new);
            Function<? super GroupRole[], ? extends LabelChildComponent> populator = populateValue -> builder.setRequiredRange(minChoices, Math.min(maxChoices, Math.min(map.size() - 25, 25))).addOptions(map.entrySet().stream().skip(25L).limit(25L).map($ -> SelectOption.of($.getValue().getName(), $.getKey())).toArray(SelectOption[]::new)).setDefaultValues(populateValue == null ? new String[0] : MiscUtils.map(populateValue, String[]::new, GroupRole::getId)).build();
            return new ModalFlowOption<GroupRole[]>(id, name, desc, resolver, populator);
        }
        public static ModalFlowOption<List<User>> ofDiscordUsers(String id, String name, String desc, boolean required, String placeholder, int minChoices, int maxChoices)
        {
            EntitySelectMenu.Builder builder = EntitySelectMenu.create(id, EntitySelectMenu.SelectTarget.USER)
                .setRequired(required)
                .setPlaceholder(placeholder)
                .setRequiredRange(minChoices, maxChoices)
                ;
            Function<? super ModalMapping, ? extends List<User>> resolver = mapping -> mapping.getAsMentions().getUsers();
            Function<? super List<User>, ? extends LabelChildComponent> populator = populateValue -> builder.build();
            return new ModalFlowOption<List<User>>(id, name, desc, resolver, populator);
        }
        public static ModalFlowOption<List<Role>> ofDiscordRoles(String id, String name, String desc, boolean required, String placeholder, int minChoices, int maxChoices)
        {
            EntitySelectMenu.Builder builder = EntitySelectMenu.create(id, EntitySelectMenu.SelectTarget.ROLE)
                .setRequired(required)
                .setPlaceholder(placeholder)
                .setRequiredRange(minChoices, maxChoices)
                ;
            Function<? super ModalMapping, ? extends List<Role>> resolver = mapping -> mapping.getAsMentions().getRoles();
            Function<? super List<Role>, ? extends LabelChildComponent> populator = populateValue -> builder.build();
            return new ModalFlowOption<List<Role>>(id, name, desc, resolver, populator);
        }
        public static ModalFlowOption<Mentions> ofDiscordUsersOrRoles(String id, String name, String desc, boolean required, String placeholder, int minChoices, int maxChoices)
        {
            EntitySelectMenu.Builder builder = EntitySelectMenu.create(id, EntitySelectMenu.SelectTarget.USER, EntitySelectMenu.SelectTarget.ROLE)
                .setRequired(required)
                .setPlaceholder(placeholder)
                .setRequiredRange(minChoices, maxChoices)
                ;
            Function<? super ModalMapping, ? extends Mentions> resolver = mapping -> mapping.getAsMentions();
            Function<? super Mentions, ? extends LabelChildComponent> populator = populateValue -> builder.build();
            return new ModalFlowOption<Mentions>(id, name, desc, resolver, populator);
        }
        public static ModalFlowOption<List<GuildChannel>> ofDiscordChannels(String id, String name, String desc, boolean required, String placeholder, int minChoices, int maxChoices)
        {
            EntitySelectMenu.Builder builder = EntitySelectMenu.create(id, EntitySelectMenu.SelectTarget.CHANNEL)
                .setRequired(required)
                .setPlaceholder(placeholder)
                .setRequiredRange(minChoices, maxChoices)
                ;
            Function<? super ModalMapping, ? extends List<GuildChannel>> resolver = mapping -> mapping.getAsMentions().getChannels();
            Function<? super List<GuildChannel>, ? extends LabelChildComponent> populator = populateValue -> builder.build();
            return new ModalFlowOption<List<GuildChannel>>(id, name, desc, resolver, populator);
        }
        public static ModalFlowOption<String> ofTextDisplay(String id, String content)
        {
            return new ModalFlowOption<String>(id, id, id, $ -> content, TextDisplay::of, null);
        }
        public static ModalFlowOption<List<Message.Attachment>> ofAttachmentUpload(String id, String name, String desc, boolean required, int minAttachments, int maxAttachments)
        {
            AttachmentUpload.Builder builder = AttachmentUpload.create(id)
                .setRequired(required)
                .setRequiredRange(minAttachments, maxAttachments)
                ;
            Function<? super ModalMapping, ? extends List<Message.Attachment>> resolver = mapping -> mapping.getAsAttachmentList();
            Function<? super List<Message.Attachment>, ? extends LabelChildComponent> populator = populateValue -> builder.build();
            return new ModalFlowOption<List<Message.Attachment>>(id, name, desc, resolver, populator);
        }
//        public static <T> ModalFlowOption<T> ofRadioGroup(String id, String name, String desc, boolean required, int minAttachments, int maxAttachments, T[] options)
//        {
//            return new ModalFlowOption<T>(id, name, desc, null, null);
//        }
//        public static <T> ModalFlowOption<T[]> ofCheckboxGroup(String id, String name, String desc, boolean required, int minSelected, int maxSelected, T[] options)
//        {
//            return new ModalFlowOption<T[]>(id, name, desc, null, null);
//        }
//        public static ModalFlowOption<Boolean> ofCheckbox(String id, String name, String desc, boolean defaultValue)
//        {
//            return new ModalFlowOption<Boolean>(id, name, desc, null, null);
//        }
        ModalFlowOption(String id, String name, String desc, Function<? super ModalMapping, ? extends T> resolver, Function<? super T, ? extends LabelChildComponent> populator)
        {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.resolver = resolver;
            this.populator = $ -> Label.of(this.name, this.desc, populator.apply($));
        }
        ModalFlowOption(String id, String name, String desc, Function<? super ModalMapping, ? extends T> resolver, Function<? super T, ? extends ModalTopLevelComponent> populator, Void ignored)
        {
            this.id = id;
            this.name = name;
            this.desc = name;
            this.resolver = resolver;
            this.populator = populator;
        }
        final String id, name, desc;
        final Function<? super ModalMapping, ? extends T> resolver;
        final Function<? super T, ? extends ModalTopLevelComponent> populator;
        Modal.Builder append(Modal.Builder builder, T populateValue)
        {
            return builder.addComponents(this.populator.apply(populateValue));
        }
        T get(ModalInteractionEvent event)
        {
            return this.resolver.apply(event.getValue(this.id));
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
        interface Selector
        {
            SelectOption[] applyContent();
            static Selector[] embeds(SelectOption[] options, int perPage)
            {
                if (perPage < 1) perPage = 1;
                if (perPage > Message.MAX_EMBED_COUNT) perPage = Message.MAX_EMBED_COUNT;
                List<Paginator.Selector> pages = new ArrayList<>();
                for (int idx = 0; idx < options.length; idx += perPage)
                {
                    SelectOption[] page = Arrays.copyOfRange(options, idx, Math.min(idx + perPage, options.length));
                    pages.add(() -> page);
                }
                return pages.toArray(new Paginator.Selector[pages.size()]);
            }
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
        public <T> Pagination(String paginationId, T[] values, Function<T, MessageEmbed> embed, Function<T, SelectOption> option, int perPage, BiConsumer<ButtonInteractionEvent, String> submitter)
        {
            this(paginationId, Arrays.asList(values), embed, option, perPage, submitter);
        }
        public <T> Pagination(String paginationId, List<T> values, Function<T, MessageEmbed> embed, Function<T, SelectOption> option, int perPage, BiConsumer<ButtonInteractionEvent, String> submitter)
        {
            this(paginationId, values.stream().map(embed).toArray(MessageEmbed[]::new), values.stream().map(option).toArray(SelectOption[]::new), perPage, submitter);
        }
        public Pagination(String paginationId, MessageEmbed[] pages, SelectOption[] options, int perPage, BiConsumer<ButtonInteractionEvent, String> submitter)
        {
            this(paginationId, Paginator.embeds(pages, perPage), Paginator.Selector.embeds(options, perPage), submitter);
        }
        public Pagination(String paginationId, Paginator[] pages)
        {
            this(paginationId, pages, null, null);
        }
        public Pagination(String paginationId, Paginator[] pages, Paginator.Selector[] pageSelectors, BiConsumer<ButtonInteractionEvent, String> submitter)
        {
            this.paginationId = paginationId;
            this.pages = pages;
            this.pageSelectors = pageSelectors;
            this.submitter = submitter == null ? (e, v) -> e.deferEdit().queue() : submitter;
            this.pendingSubmission = null;
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
        final Paginator.Selector[] pageSelectors;
        final BiConsumer<ButtonInteractionEvent, String> submitter;
        String pendingSubmission;
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
                        .setComponents(ActionRow.of(first.asDisabled(), prev.asDisabled(), self.asDisabled(), next.asDisabled(), last.asDisabled()));
            }
            List<MessageTopLevelComponent> components = new ArrayList<>();
            components.add(ActionRow.of(first, prev, self, next, last));
            if (this.pageSelectors != null)
            {
                components.add(ActionRow.of(StringSelectMenu
                    .create("pagination-submission:"+this.paginationId+":"+pageOrdinal)
                    .addOptions(this.pageSelectors[pageIndex].applyContent())
                    .setPlaceholder("Select value...")
                    .build()));
                components.add(ActionRow.of(
                    Button.primary("pagination-submit:"+this.paginationId+":"+pageOrdinal, "Submit"),
                    Button.danger("pagination-cancel:"+this.paginationId+":"+pageOrdinal, "Cancel")));
            }
            else
            {
                components.add(ActionRow.of(
                    Button.danger("pagination-cancel:"+this.paginationId+":"+pageOrdinal, "Close")));
            }
            return this
                .pages[pageIndex]
                .applyContent(this.hook, this.messageId)
                .setComponents(components);
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

    /**
     * Implement this type to make a valid modal flow.<br>
     * Must register to interaction.<br>
     * Public non-final fields are used as the modal elements.
     * @param <MF> self
     */
    public static @FunctionalInterface interface ModalFlow<MF extends ModalFlow<MF>> extends ModalSubmitHandler
    {
    }
    public <MF extends ModalFlow<MF>> ModalCallbackAction submitModalFlow(IModalCallback cb, MF mf)
    {
        @SuppressWarnings("unchecked")
        ModalFlowInfo<MF> info = (ModalFlowInfo<MF>)this.modalFlowInfos.get(mf.getClass());
        if (info == null)
            throw new IllegalStateException("ModalFlowInfo missing or unregistered for "+mf.getClass()+" in "+this);
        if (!info.type.isInstance(mf))
            throw new IllegalStateException("ModalFlowInfo of "+info.type+", but ModelFlow is of "+mf.getClass()+" in "+this);
        ModalCallbackAction action = cb.replyModal(info.modal(mf));
        this.modalFlows.put(cb.getUser().getId(), mf);
        return action;
    }
    public static @FunctionalInterface interface ModalFlowImmediate extends ModalSubmitHandler
    {
        class Holder
        {
            Holder(String modalId, ModalFlowImmediate immediate)
            {
                this.modalId = modalId;
                this.immediate = immediate;
            }
            final String modalId;
            final ModalFlowImmediate immediate;
        }
    }
    public <MF extends ModalFlow<MF>> ModalCallbackAction submitModalFlow(IModalCallback cb, Modal modal, ModalFlowImmediate mfi)
    {
        ModalCallbackAction action = cb.replyModal(modal);
        this.modalFlowImmediates.put(cb.getUser().getId(), new ModalFlowImmediate.Holder(modal.getId(), mfi));
        return action;
    }
    class ModalFlowInfo<MF extends ModalFlow<MF>> implements ModalSubmitHandler
    {
        ModalFlowInfo(Class<MF> type, Slash slash, Slash.Group group)
        {
            this.type = type;
            ModalSub ms = type.getDeclaredAnnotation(ModalSub.class);
            if (ms == null)
                throw new IllegalStateException("Expected "+type+" to have ModalSub annotation for its id!");
            Desc desc = type.getDeclaredAnnotation(Desc.class);
            if (desc == null)
                throw new IllegalStateException("Expected "+type+" to have Desc annotation for its title!");
            this.id = ms.value();
            this.title = desc.value();
            this.options = new LinkedHashMap<>(); // preserve sequential order
            for (Field field : type.getFields())
            {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()))
                {
                    if (this.tryStringSel(field, slash, group)) continue;
                }
            }
        }
        final Class<MF> type;
        final String id, title;
        final Map<Field, ModalFlowOption<?>> options;
        boolean tryStringSel(Field field, Slash slash, Slash.Group group)
        {
            StringSel ss = field.getDeclaredAnnotation(StringSel.class);
            if (ss == null)
                return false;
            ModalFlowOption<?> mfo = DInteractions.this.findModalFlowOption(slash, group, ss.value());
            if (mfo == null) LOG.error(String.format("No option found for %s {%s}", field, ss.value()));
            if (mfo != null) this.options.put(field, mfo);
            return true;
        }
        @SuppressWarnings({ "unchecked" })
        Modal modal(MF mf)
        {
            Modal.Builder builder = Modal.create(this.id, this.title);
            this.options.forEach((field, option) ->
            {
                try
                {
                    ((ModalFlowOption<Object>)option).append(builder, field.get(mf));
                }
                catch (Exception ex)
                {
                    LOG.error("modal flow `"+this.id+"`: Exception populating option `"+option.id+"`", ex);
                }
            });
            return builder.build();
        }
        @Override
        public void handle(ModalInteractionEvent event)
        {
            String userSf = event.getUser().getId();
            ModalFlow<?> mf_ = DInteractions.this.modalFlows.get(userSf);
            if (mf_ == null)
            {
                LOG.error("modal flow `"+this.id+"` == null for "+event.getUser().getName()+" ("+userSf+")");
                return;
            }
            if (!this.type.isInstance(mf_))
            {
                LOG.error("modal flow `"+this.id+"` == wrong "+mf_.getClass()+" for "+event.getUser().getName()+" ("+event.getUser().getId()+")");
                return;
            }
            DInteractions.this.modalFlows.remove(userSf, mf_);
            MF mf = this.type.cast(mf_); // should always be OK, since it was just checked above
            this.options.forEach((field, option) ->
            {
                try
                {
                    field.set(mf, option.get(event));
                }
                catch (Exception ex)
                {
                    LOG.error("modal flow `"+this.id+"`: Exception setting option `"+option.id+"`", ex);
                }
            });
            mf.handle(event);
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
    final Map<Class<? extends ModalFlow<?>>, ModalFlowInfo<?>> modalFlowInfos = new HashMap<>();
    final Map<String, ModalFlowOption<?>> modalFlowOptionsBound = new HashMap<>();
    final Map<String, Pagination> pagination = new ConcurrentHashMap<>();
    final Map<String, ModalFlow<?>> modalFlows = new ConcurrentHashMap<>();
    final Map<String, ModalFlowImmediate.Holder> modalFlowImmediates = new ConcurrentHashMap<>();

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
            this.data = Commands.slash(name, desc).setContexts(InteractionContextType.GUILD);
            this.subs = new HashMap<>();
            this.opts = new HashMap<>();
            this.opts_modal = new HashMap<>();
            DInteractions.this.data.add(this.data);
            DInteractions.this.slashBuilders.put(name, this);
        }
        final SlashCommandData data;
        final Map<String, Group> subs;
        final Map<String, SlashOption<?>> opts;
        final Map<String, ModalFlowOption<?>> opts_modal;
        public class Group
        {
            Group(String name, String desc)
            {
                this.data = new SubcommandGroupData(name, desc);
                this.opts = new HashMap<>();
                this.opts_modal = new HashMap<>();
                Slash.this.data.addSubcommandGroups(this.data);
                Slash.this.subs.put(name, this);
            }
            final SubcommandGroupData data;
            final Map<String, SlashOption<?>> opts;
            final Map<String, ModalFlowOption<?>> opts_modal;
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
        CommandData data = Commands.message(name).setContexts(InteractionContextType.GUILD).setDefaultPermissions(DEFAULT_PERMISSIONS);
        this.messageCmds.put(name, handler);
        this.data.add(data);
        return data;
    }

    public CommandData user(String name, UserCommandHandler handler)
    {
        CommandData data = Commands.user(name).setContexts(InteractionContextType.GUILD).setDefaultPermissions(DEFAULT_PERMISSIONS);
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
        case "pagination-cancel":
        case "pagination-submit":
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
                event.replyModal(Modal.create(event.getButton().getCustomId(), "Pagination")
                    .addComponents(Label.of("Page (1 to "+pagination.pages.length+", inclusive)", TextInput.create("pagination-ordinal", TextInputStyle.SHORT)
                        .setRequiredRange(1, Integer.toString(pagination.pages.length).length())
                        .setValue(Integer.toString(pageOrdinal))
                        .build()))
                    .build()).queue();
            } break;
            case "pagination-cancel": {
                if (pagination.messageId != null)
                    pagination.hook.deleteOriginal().queue();
            } break;
            case "pagination-submit": {
                if (pagination.pendingSubmission == null)
                {
                    event.reply("No value selected")
                        .setEphemeral(true)
                        .queue(m -> m.deleteOriginal().queueAfter(3L, TimeUnit.SECONDS));
                    break;
                }
                pagination.submitter.accept(event, pagination.pendingSubmission);
                if (pagination.messageId != null)
                    pagination.hook.deleteOriginal().queue();
                if (!event.isAcknowledged())
                    event.deferEdit().queue();
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
    boolean handlePagination(StringSelectInteractionEvent event, String id, String[] parts, String op)
    {
        switch (op)
        {
        default:
            return false;
        case "pagination-submission":
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
            case "pagination-submission": {
                List<String> submitted = event.getValues();
                if (submitted.size() > 1)
                    throw new Exception("Too many pagination submission values for "+id+": "+submitted);
                pagination.pendingSubmission = submitted.stream().findFirst().orElse(null);
                event.deferEdit().queue();
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

    boolean handleFlowImmediate(ModalInteractionEvent event, String id, String[] parts, String op)
    {
        String userSf = event.getUser().getId();
        ModalFlowImmediate.Holder holder = this.modalFlowImmediates.get(userSf);
        if (holder == null)
            return false;
        if (!Objects.equals(holder.modalId, id))
            return false;
        this.modalFlowImmediates.remove(userSf, holder);
        try
        {
            holder.immediate.handle(event);
        }
        catch (Exception ex)
        {
            LOG.error("Exception sync handling immediate modal flow of "+id, ex);
            event.reply("Exception sync handling immediate modal flow of "+id+":\n`"+ex+"`").setEphemeral(true).queue();
        }
        if (!event.isAcknowledged())
        {
            LOG.warn("immediate modal flow for `"+id+"` did not acknowledge");
            event.deferReply(true).queue();
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
        String id = event.getButton().getCustomId(),
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
        String id = event.getSelectMenu().getCustomId(),
               parts[] = id.split(":"),
               op = parts[0];
        if (this.handlePagination(event, id, parts, op))
            return true;
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
        String id = event.getSelectMenu().getCustomId(),
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
        if (this.handleFlowImmediate(event, id, parts, op))
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
