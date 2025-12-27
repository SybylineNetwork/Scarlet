package net.sybyline.scarlet.server.discord;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

public class DConfig
{

    static final Logger LOG = LoggerFactory.getLogger("DConfig");

    public interface Value<T> extends Supplier<T>
    {
        boolean set(T value);
        void subscribe(BiConsumer<T, T> listener);
        default void subscribe(Consumer<T> listener)
        {
            this.subscribe((prev, next) -> listener.accept(next));
        }
        static Value<Boolean> ofBool(boolean defaultValue)
        {
            return new BoolValue(defaultValue);
        }
        static Value<Integer> ofInt(int defaultValue)
        {
            return ofInt(defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        static Value<Integer> ofInt(int defaultValue, int min, int max)
        {
            return new IntValue(defaultValue, min, max);
        }
        static Value<String> ofString(String defaultValue)
        {
            return ofString(defaultValue, null, null);
        }
        static Value<String> ofString(String defaultValue, String pattern)
        {
            return ofString(defaultValue, pattern, null);
        }
        static Value<String> ofString(String defaultValue, Function<String, Stream<Command.Choice>> autocomplete)
        {
            return ofString(defaultValue, null, autocomplete);
        }
        static Value<String> ofString(String defaultValue, String pattern, Function<String, Stream<Command.Choice>> autocomplete)
        {
            return new StringValue(defaultValue, pattern, autocomplete);
        }
        static <E extends Enum<E>> Value<E> ofEnum(E defaultValue, Class<E> type, EnumSpec<E> spec)
        {
            return new EnumValue<>(defaultValue, type, spec);
        }
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    static abstract class BaseValue<T> implements Value<T>
    {
        static final BiConsumer NOOP_LISTENER = (prev, next) -> {};
        protected BaseValue(T defaultValue, Function<String, Stream<Command.Choice>> autocomplete)
        {
            this.group = null;
            this.subgroup = null;
            this.name = null;
            this.description = null;
            this.defaultValue = defaultValue;
            this.autocomplete = autocomplete;
            this.value = null;
            this.internalListener = NOOP_LISTENER;
            this.uiListener = NOOP_LISTENER;
            this.listeners = new CopyOnWriteArrayList<>();
        }
        String group, subgroup, name, description;
        final T defaultValue;
        final Function<String, Stream<Command.Choice>> autocomplete;
        T value;
        BiConsumer<T, T> internalListener, uiListener;
        final List<BiConsumer<T, T>> listeners;
        @Override
        public T get()
        {
            T value = this.value;
            return value != null ? value : this.defaultValue;
        }
        @Override
        public boolean set(T value)
        {
            return this.set(value, false, false);
        }
        final boolean set(T value, boolean internal, boolean ui)
        {
            if (value != null && !this.isValid(value))
                return false;
            T prev = this.value;
            if (this.eq(prev, value))
                return true;
            prev = this.get();
            this.value = value;
            value = this.get();
            if (!internal)
                this.internalListener.accept(prev, value);
            if (!ui)
                this.uiListener.accept(prev, value);
            for (BiConsumer<T, T> listener : this.listeners)
                listener.accept(prev, value);
            return true;
        }
        @Override
        public void subscribe(BiConsumer<T, T> listener)
        {
            this.listeners.add(listener);
        }
        abstract boolean isValid(T value);
        boolean areEqual(T prev, T next)
        {
            return prev.equals(next);
        }
        final boolean eq(T prev, T next)
        {
            if (prev == next)
                return true;
            if (prev == null || next == null)
                return false;
            return this.areEqual(prev, next);
        }
        final void read(JsonReader json) throws IOException
        {
            T value;
            if (json.peek() == JsonToken.NULL)
            {
                value = null;
                json.nextNull();
            }
            else
            {
                value = this.readNonNull(json);
            }
            this.set(value, true, false);
        }
        final void write(JsonWriter json) throws IOException
        {
            T value = this.get();
            if (value == null)
            {
                json.nullValue();
                return;
            }
            this.writeNonNull(json, value);
        }
        abstract T readNonNull(JsonReader json) throws IOException;
        abstract void writeNonNull(JsonWriter json, T value) throws IOException;
        abstract OptionData buildCommandOption();
        SubcommandData[] buildCommand()
        {
            return new SubcommandData[]{new SubcommandData(this.name, this.description).addOptions(this.buildCommandOption().setRequired(true))};
        }
        abstract boolean handleCommand(OptionMapping mapping);
        Consumer<SlashCommandInteractionEvent> handleCommand(String name)
        {
            throw new UnsupportedOperationException();
        }
        Function<String, Stream<Command.Choice>> handleCommandAutocomplete(String name)
        {
            throw new UnsupportedOperationException();
        }
    }

    static class BoolValue extends BaseValue<Boolean>
    {
        BoolValue(boolean defaultValue)
        {
            super(defaultValue, null);
        }
        @Override
        boolean isValid(Boolean value)
        {
            return true;
        }
        @Override
        Boolean readNonNull(JsonReader json) throws IOException
        {
            return json.nextBoolean();
        }
        @Override
        void writeNonNull(JsonWriter json, Boolean value) throws IOException
        {
            json.value(value);
        }
        @Override
        OptionData buildCommandOption()
        {
            return new OptionData(OptionType.BOOLEAN, this.name, this.description, false, this.autocomplete != null);
        }
        @Override
        boolean handleCommand(OptionMapping mapping)
        {
            return this.set(mapping.getAsBoolean());
        }
    }

    static class IntValue extends BaseValue<Integer>
    {
        IntValue(int defaultValue, int min, int max)
        {
            super(defaultValue, null);
            this.min = min;
            this.max = max;
        }
        final int min, max;
        @Override
        boolean isValid(Integer value)
        {
            return this.min <= value && value <= this.max;
        }
        @Override
        Integer readNonNull(JsonReader json) throws IOException
        {
            return json.nextInt();
        }
        @Override
        void writeNonNull(JsonWriter json, Integer value) throws IOException
        {
            json.value(value);
        }
        @Override
        OptionData buildCommandOption()
        {
            return new OptionData(OptionType.INTEGER, this.name, this.description, false, this.autocomplete != null).setRequiredRange(this.min, this.max);
        }
        @Override
        boolean handleCommand(OptionMapping mapping)
        {
            return this.set(mapping.getAsInt());
        }
    }

    static class StringValue extends BaseValue<String>
    {
        StringValue(String defaultValue, String pattern, Function<String, Stream<Command.Choice>> autocomplete)
        {
            super(defaultValue, autocomplete);
            this.pattern = pattern == null ? null : Pattern.compile(pattern);
        }
        final Pattern pattern;
        @Override
        boolean isValid(String value)
        {
            return this.pattern == null || this.pattern.matcher(value).matches();
        }
        @Override
        String readNonNull(JsonReader json) throws IOException
        {
            return json.nextString();
        }
        @Override
        void writeNonNull(JsonWriter json, String value) throws IOException
        {
            json.value(value);
        }
        @Override
        OptionData buildCommandOption()
        {
            return new OptionData(OptionType.STRING, this.name, this.description, false, this.autocomplete != null);
        }
        @Override
        boolean handleCommand(OptionMapping mapping)
        {
            return this.set(mapping.getAsString());
        }
    }

    static class StringArrayValue extends BaseValue<String[]>
    {
        StringArrayValue(String[] defaultValue, String pattern, Function<String, Stream<Command.Choice>> autocomplete)
        {
            super(defaultValue, autocomplete);
            this.pattern = pattern == null ? null : Pattern.compile(pattern);
        }
        final Pattern pattern;
        @Override
        boolean isValid(String[] value)
        {
            return this.pattern == null || Stream.of(value).map(this.pattern::matcher).allMatch(Matcher::matches);
        }
        @Override
        String[] readNonNull(JsonReader json) throws IOException
        {
            json.beginArray();
            List<String> list = new ArrayList<>();
            while (json.hasNext())
            {
                list.add(json.nextString());
            }
            json.endArray();
            return list.toArray(new String[list.size()]);
        }
        @Override
        void writeNonNull(JsonWriter json, String[] value) throws IOException
        {
            json.beginArray();
            for (String string : value)
                json.value(string);
            json.endArray();
        }
        @Override
        OptionData buildCommandOption()
        {
            throw new IllegalStateException("This config type can't be that deeply nested");
        }
        @Override
        boolean handleCommand(OptionMapping mapping)
        {
            throw new IllegalStateException("This config type can't be that deeply nested");
        }
        @Override
        SubcommandData[] buildCommand()
        {
            return new SubcommandData[]{
                new SubcommandData(this.name+"-add", this.description).addOptions(new OptionData(OptionType.STRING, this.name, this.description, true, this.autocomplete != null)),
                new SubcommandData(this.name+"-remove", this.description).addOptions(new OptionData(OptionType.STRING, this.name, this.description, true, this.autocomplete != null)),
            };
        }
        @Override
        Consumer<SlashCommandInteractionEvent> handleCommand(String name)
        {
            if (name.equals(this.name+"-add"))
            {
                return event ->
                {
                    this.set(ArrayUtils.add(this.get(), event.getOption(this.name).getAsString()));
                };
            }
            if (name.equals(this.name+"-remove"))
            {
                return event ->
                {
                    String[] array = this.get();
                    int index = ArrayUtils.indexOf(array, event.getOption(this.name).getAsString());
                    if (index >= 0)
                    {
                        this.set(ArrayUtils.remove(array, index));
                    }
                };
            }
            throw new IllegalStateException("Invalid name: "+name);
        }
        @Override
        Function<String, Stream<Command.Choice>> handleCommandAutocomplete(String name)
        {
            if (name.equals(this.name+"-add"))
            {
                return this.autocomplete;
            }
            if (name.equals(this.name+"-remove"))
            {
                return string ->
                {
                    List<String> list = Arrays.asList(this.get());
                    return this.autocomplete.apply(string).filter(choice -> list.contains(choice.getAsString()));
                };
            }
            throw new IllegalStateException("Invalid name: "+name);
        }
    }

    public interface EnumSpec<T>
    {
        T parse(String string);
        String stringify(T value);
        Command.Choice choice(T value);
    }

    static class EnumValue<E extends Enum<E>> extends BaseValue<E>
    {
        static <E extends Enum<E>> Function<String, Stream<Command.Choice>> maybeAutocomplete(E[] enumValues, EnumSpec<E> spec)
        {
            if (enumValues.length <= 25)
                return null;
            Command.Choice[] choices = new Command.Choice[enumValues.length];
            for (int ordinal = 0; ordinal < enumValues.length; ordinal++)
                choices[ordinal] = spec.choice(enumValues[ordinal]);
            return typing0 ->
            {
                String typing = typing0.trim();
                Stream<Command.Choice> stream = Stream.of(choices);
                if (!typing.isEmpty())
                    stream = stream.sorted(DInteractions.choicesByLevenshtein(typing));
                return stream;
            };
        }
        EnumValue(E defaultValue, Class<E> type, EnumSpec<E> spec)
        {
            super(defaultValue, null);
            this.type = type;
            this.spec = spec;
            this.values = type.getEnumConstants();
        }
        final Class<E> type;
        final EnumSpec<E> spec;
        final E[] values;
        @Override
        boolean isValid(E value)
        {
            return true;
        }
        @Override
        E readNonNull(JsonReader json) throws IOException
        {
            return this.spec.parse(json.nextString());
        }
        @Override
        void writeNonNull(JsonWriter json, E value) throws IOException
        {
            json.value(this.spec.stringify(value));
        }
        @Override
        OptionData buildCommandOption()
        {
            OptionData data = new OptionData(OptionType.STRING, this.name, this.description, false, this.autocomplete != null);
            if (this.autocomplete == null)
            {
                for (E enumValue : this.values)
                {
                    data.addChoices(this.spec.choice(enumValue));
                }
            }
            return data;
        }
        @Override
        boolean handleCommand(OptionMapping mapping)
        {
            return this.set(this.spec.parse(mapping.getAsString()));
        }
    }

    /** <code>Map&lt;String, BaseValue&lt;?&gt;|Map&lt;String, BaseValue&lt;?&gt;&gt;&gt;</code>**/
    final Map<String, Object> values = new LinkedHashMap<>();
    final Map<String, BaseValue<?>> flatValues = new LinkedHashMap<>();
    final Map<String, String> groupDescriptions = new HashMap<>();
    final Map<String, Consumer<SlashCommandInteractionEvent>> handlers = new HashMap<>();
    final Map<String, Consumer<CommandAutoCompleteInteractionEvent>> autocompletes = new HashMap<>();
    static String path(String group, String subgroup, String name)
    {
        return subgroup != null ? group+"/"+subgroup+"/"+name : group != null ? group+"/"+name : name;
    }
    boolean add(String group, String subgroup, String name, String desc, BaseValue<?> value)
    {
        if (group == null)
        {
            if (this.values.get(name) instanceof Map)
                throw new IllegalStateException("Identical group and key name: "+path(group, subgroup, name));
            if (this.values.putIfAbsent(name, value) != null)
                throw new IllegalStateException("Duplicate value name: "+path(group, subgroup, name));
            value.group = null;
            value.subgroup = null;
            value.name = name;
            value.description = desc;
            return true;
        }
        if (subgroup == null)
        {
            if (this.values.get(group) instanceof BaseValue)
                throw new IllegalStateException("Identical group and key name: "+path(group, subgroup, name));
            @SuppressWarnings("unchecked")
            Map<String, BaseValue<?>> map = (Map<String, BaseValue<?>>)this.values.computeIfAbsent(group, $ -> new LinkedHashMap<>());
            if (map.get(group) instanceof Map)
                throw new IllegalStateException("Identical subgroup and key name: "+path(group, subgroup, name));
            if (map.putIfAbsent(name, value) != null)
                throw new IllegalStateException("Duplicate value name: "+path(group, subgroup, name));
            value.group = group;
            value.subgroup = null;
            value.name = name;
            value.description = desc;
            return true;
        }
        if (this.values.get(group) instanceof BaseValue)
            throw new IllegalStateException("Identical group and key name: "+path(group, subgroup, name));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, BaseValue<?>>> map = (Map<String, Map<String, BaseValue<?>>>)this.values.computeIfAbsent(group, $ -> new LinkedHashMap<>());
        if (map.get(group) instanceof BaseValue)
            throw new IllegalStateException("Identical subgroup and key name: "+path(group, subgroup, name));
        Map<String, BaseValue<?>> mapInner = (Map<String, BaseValue<?>>)map.computeIfAbsent(group, $ -> new LinkedHashMap<>());
        if (mapInner.putIfAbsent(name, value) != null)
            throw new IllegalStateException("Duplicate value name: "+path(group, subgroup, name));
        value.group = group;
        value.subgroup = subgroup;
        value.name = name;
        value.description = desc;
        return true;
    }
    public boolean register(Object owner)
    {
        return this.register(null, null, null, owner);
    }
    boolean register(String group, String subgroup, Map<String, BaseValue<?>> subgroupHandler, Object owner)
    {
        if (owner == null)
            return false;
        boolean result = false;
        Class<?> clazz = owner.getClass();
        for (Field f : clazz.getFields())
        {
            String name = f.isAnnotationPresent(DInteractions.SlashCmd.class)
                ? f.getDeclaredAnnotation(DInteractions.SlashCmd.class).value()
                : f.getName(),
                   desc = f.isAnnotationPresent(DInteractions.Desc.class)
                ? f.getDeclaredAnnotation(DInteractions.Desc.class).value()
                : name;
            if (!Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())) try
            {
                Object value = f.get(owner);
                if (Value.class.isAssignableFrom(f.getType()))
                {
                    @SuppressWarnings("unchecked")
                    BaseValue<Object> baseValue = (BaseValue<Object>)value;
                    if (this.add(group, subgroup, name, desc, baseValue))
                    {
                        baseValue.internalListener = (prev, next) -> this.modified(group, subgroup, name, baseValue, prev, next);
                        result = true;
                        if (subgroupHandler != null)
                            subgroupHandler.put(name, baseValue);
                        else
                            this.handlers.put(path(group, subgroup, name), event ->
                            {
                                OptionMapping mapping = event.getOption(name);
                                if (mapping == null)
                                {
                                    LOG.error("Missing value "+path(group, subgroup, name)+": "+f);
                                    event.reply("Internal error: mapping null for "+path(group, subgroup, name)).setEphemeral(true).queue(hook -> hook.deleteOriginal().queueAfter(5_000L, TimeUnit.MILLISECONDS));
                                }
                                else if (!baseValue.handleCommand(mapping))
                                {
                                    event.reply("Invalid value for "+name).setEphemeral(true).queue(hook -> hook.deleteOriginal().queueAfter(5_000L, TimeUnit.MILLISECONDS));
                                }
                                else
                                {
                                    event.reply("Set value "+name).setEphemeral(true).queue(hook -> hook.deleteOriginal().queueAfter(5_000L, TimeUnit.MILLISECONDS));
                                }
                            });
                        if (baseValue.autocomplete != null)
                        {
                            this.autocompletes.put(path(group, subgroup, name), event ->
                            {
                                event.replyChoices(baseValue.autocomplete.apply(event.getFocusedOption().getValue()).limit(25L).toArray(Command.Choice[]::new)).queue();
                            });
                        }
                        if (f.isAnnotationPresent(SerializedName.class))
                        {
                            this.flatValues.put(f.getDeclaredAnnotation(SerializedName.class).value(), baseValue);
                        }
                    }
                }
                else if (group == null && f.getType().getEnclosingClass() == clazz)
                {
                    this.groupDescriptions.put(name, desc);
                    result |= this.register(name, null, null, value);
                }
                else if (subgroup == null && f.getType().getEnclosingClass() == clazz)
                {
                    this.groupDescriptions.put(path(group, null, name), desc);
                    Map<String, BaseValue<?>> subgroupHandlerInner = new HashMap<>();
                    result |= this.register(group, name, subgroupHandlerInner, value);
                    this.handlers.put(path(group, null, name), event ->
                    {
                        List<OptionMapping> mappings = event.getOptions();
                        if (mappings.isEmpty())
                        {
                            event.reply("Nothing set").setEphemeral(true).queue(hook -> hook.deleteOriginal().queueAfter(5_000L, TimeUnit.MILLISECONDS));
                            return;
                        }
                        List<String> succeeded = new ArrayList<>(),
                                     failed = new ArrayList<>(),
                                             errored = new ArrayList<>();
                        for (OptionMapping mapping : mappings)
                        {
                            BaseValue<?> baseValue = subgroupHandlerInner.get(mapping.getName());
                            if (baseValue == null)
                            {
                                LOG.error("Missing inner value "+path(group, name, mapping.getName())+": "+f);
                                errored.add(mapping.getName());
                            }
                            else if (!baseValue.handleCommand(mapping))
                            {
                                failed.add(mapping.getName());
                            }
                            else
                            {
                                succeeded.add(mapping.getName());
                            }
                        }
                        StringBuilder sb = new StringBuilder();
                        if (!succeeded.isEmpty())
                        {
                            for (String success : succeeded)
                            {
                                sb.append(sb.length() == 0 ? "Set values " : ", ").append(success);
                            }
                            sb.append('\n');
                        }
                        if (!failed.isEmpty())
                        {
                            for (String failure : failed)
                            {
                                sb.append((sb.length() == 0 || sb.charAt(sb.length() - 1) == '\n') ? "Invalid values for " : ", ").append(failure);
                            }
                            sb.append('\n');
                        }
                        if (!errored.isEmpty())
                        {
                            for (String error : errored)
                            {
                                sb.append((sb.length() == 0 || sb.charAt(sb.length() - 1) == '\n') ? "Internal errors for " : ", ").append(error);
                            }
                        }
                        if (sb.charAt(sb.length() - 1) == '\n')
                            sb.setLength(sb.length() - 1);
                        event.reply(sb.toString()).setEphemeral(true).queue(hook -> hook.deleteOriginal().queueAfter(5_000L, TimeUnit.MILLISECONDS));
                    });
                }
                else
                {
                    LOG.warn("Skipping non-conforming field "+path(group, subgroup, name)+": "+f);
                }
            }
            catch (Exception ex)
            {
                LOG.error("Exception registering field "+path(group, subgroup, name)+": "+f, ex);
                ex.printStackTrace();
            }
        }
        return result;
    }

    public SlashCommandData generateCommand(String name, String description)
    {
        SlashCommandData data = Commands.slash(name, description);
        if (this.values.size() > 25)
            throw new IllegalStateException("Root and grouped values combined are over limit of 25");
        for (Map.Entry<String, Object> entry : this.values.entrySet())
        {
            String group = entry.getKey();
            Object value = entry.getValue();
            if (value == null)
            {
                LOG.warn("Missing value in slot '"+group+"'");
            }
            else if (value instanceof BaseValue)
            {
                BaseValue<?> baseValue = (BaseValue<?>)value;
                SubcommandData[] builds = baseValue.buildCommand();
                if (builds.length == 1)
                {
                    data.addSubcommands(builds);
                }
                else for (SubcommandData build : builds)
                {
                    this.handlers.put(path(null, null, build.getName()), baseValue.handleCommand(build.getName()));
                    Function<String, Stream<Command.Choice>> hereAuto = baseValue.handleCommandAutocomplete(build.getName());
                    this.autocompletes.put(path(null, null, build.getName())+"/"+group, event ->
                    {
                        event.replyChoices(hereAuto.apply(event.getFocusedOption().getValue()).limit(25L).toArray(Command.Choice[]::new)).queue();
                    });
                }
            }
            else if (value instanceof Map)
            {
                String subDesc = this.groupDescriptions.getOrDefault(group, group);
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>)value;
                SubcommandGroupData subData = new SubcommandGroupData(group, subDesc);
                for (Map.Entry<String, Object> subEntry : map.entrySet())
                {
                    String subName = subEntry.getKey();
                    Object subValue = subEntry.getValue();
                    if (subValue == null)
                    {
                        LOG.warn("Missing value in slot "+path(group, null, subName));
                    }
                    else if (value instanceof BaseValue)
                    {
                        BaseValue<?> baseValue = (BaseValue<?>)subValue;
                        SubcommandData[] builds = baseValue.buildCommand();
                        if (builds.length == 1)
                        {
                            subData.addSubcommands(builds);
                        }
                        else for (SubcommandData build : builds)
                        {
                            this.handlers.put(path(group, null, build.getName()), baseValue.handleCommand(build.getName()));
                            Function<String, Stream<Command.Choice>> hereAuto = baseValue.handleCommandAutocomplete(build.getName());
                            this.autocompletes.put(path(group, null, build.getName())+"/"+subName, event ->
                            {
                                event.replyChoices(hereAuto.apply(event.getFocusedOption().getValue()).limit(25L).toArray(Command.Choice[]::new)).queue();
                            });
                        }
                    }
                    else if (subValue instanceof Map)
                    {
                        String subDescInner = this.groupDescriptions.getOrDefault(group+"/"+subName, group+"/"+subName);
                        @SuppressWarnings("unchecked")
                        Map<String, BaseValue<?>> mapInner = (Map<String, BaseValue<?>>)subValue;
                        SubcommandData subDataInner = new SubcommandData(subName, subDescInner);
                        for (Map.Entry<String, BaseValue<?>> subEntryInner : mapInner.entrySet())
                        {
                            String subNameInner = subEntryInner.getKey();
                            Object subValueInner = subEntryInner.getValue();
                            if (subValueInner == null)
                            {
                                LOG.warn("Missing value in slot "+path(group, subName, subNameInner));
                            }
                            else
                            {
                                BaseValue<?> baseValue = (BaseValue<?>)subValue;
                                subDataInner.addOptions(baseValue.buildCommandOption());
                            }
                        }
                        subData.addSubcommands(subDataInner);
                    }
                }
                data.addSubcommandGroups(subData);
            }
            else
            {
                LOG.error("Unknown value in slot "+path(null, null, group));
            }
        }
        return data;
    }

    void read(JsonReader reader) throws IOException
    {
        this.read(reader, !this.flatValues.isEmpty());
    }
    void write(JsonWriter writer) throws IOException
    {
        this.write(writer, !this.flatValues.isEmpty());
    }

    void read(JsonReader reader, boolean flat) throws IOException
    {
        if (flat)
            this.readFlat(reader);
        else
            this.readTree(reader);
    }
    void write(JsonWriter writer, boolean flat) throws IOException
    {
        if (flat)
            this.writeFlat(writer);
        else
            this.writeTree(writer);
    }

    void readFlat(JsonReader reader) throws IOException
    {
        reader.beginObject();
        while (reader.hasNext())
        {
            String name = reader.nextName();
            BaseValue<?> value = this.flatValues.get(name);
            if (value == null)
            {
                LOG.warn("Missing value for flat "+name);
                reader.skipValue();
            }
            else
            {
                value.read(reader);
            }
        }
        reader.endObject();
    }
    void writeFlat(JsonWriter writer) throws IOException
    {
        writer.beginObject();
        for (Map.Entry<String, BaseValue<?>> entry : this.flatValues.entrySet())
        {
            String name = entry.getKey();
            BaseValue<?> value = entry.getValue();
            writer.name(name);
            if (value == null)
            {
                LOG.warn("Missing value for flat "+name);
                writer.nullValue();
            }
            else
            {
                value.write(writer);
            }
        }
        writer.endObject();
    }

    void readTree(JsonReader reader) throws IOException
    {
        reader.beginObject();
        while (reader.hasNext())
        {
            String group = reader.nextName();
            Object value = this.values.get(group);
            if (value == null)
            {
                LOG.warn("Missing value in slot "+path(null, null, group));
                reader.skipValue();
            }
            else if (value instanceof BaseValue)
            {
                ((BaseValue<?>)value).read(reader);
            }
            else if (value instanceof Map)
            {
                if (reader.peek() != JsonToken.BEGIN_OBJECT)
                {
                    reader.skipValue();
                }
                else
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>)value;
                    reader.beginObject();
                    while (reader.hasNext())
                    {
                        String subgroup = reader.nextName();
                        Object inner = map.get(group);
                        if (inner == null)
                        {
                            LOG.warn("Missing value in slot "+path(group, null, subgroup));
                            reader.skipValue();
                        }
                        else if (inner instanceof BaseValue)
                        {
                            ((BaseValue<?>)value).read(reader);
                        }
                        else if (inner instanceof Map)
                        {
                            if (reader.peek() != JsonToken.BEGIN_OBJECT)
                            {
                                reader.skipValue();
                            }
                            else
                            {
                                @SuppressWarnings("unchecked")
                                Map<String, BaseValue<?>> mapInner = (Map<String, BaseValue<?>>)inner;
                                reader.beginObject();
                                while (reader.hasNext())
                                {
                                    String name = reader.nextName();
                                    BaseValue<?> innerValue = mapInner.get(name);
                                    if (innerValue == null)
                                    {
                                        LOG.warn("Missing value in slot "+path(group, subgroup, name));
                                        reader.skipValue();
                                    }
                                    else
                                    {
                                        innerValue.read(reader);
                                    }
                                }
                                reader.endObject();
                            }
                        }
                        else
                        {
                            LOG.error("Unknown value in slot "+path(group, null, subgroup));
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                }
            }
            else
            {
                LOG.error("Unknown value in slot "+path(null, null, group));
                reader.skipValue();
            }
        }
        reader.endObject();
    }
    void writeTree(JsonWriter writer) throws IOException
    {
        writer.beginObject();
        for (Map.Entry<String, Object> entry : this.values.entrySet())
        {
            String group = entry.getKey();
            Object value = entry.getValue();
            writer.name(group);
            if (value == null)
            {
                LOG.warn("Missing value in slot '"+group+"'");
                writer.nullValue();
            }
            else if (value instanceof BaseValue)
            {
                BaseValue<?> baseValue = (BaseValue<?>)value;
                baseValue.write(writer);
            }
            else
            {
                writer.beginObject();
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>)value;
                for (Map.Entry<String, Object> subEntry : map.entrySet())
                {
                    String subName = subEntry.getKey();
                    Object subValue = subEntry.getValue();
                    writer.name(subName);
                    if (subValue == null)
                    {
                        LOG.warn("Missing value in slot "+path(group, null, subName));
                        writer.nullValue();
                    }
                    else if (subValue instanceof BaseValue)
                    {
                        BaseValue<?> baseValue = (BaseValue<?>)subValue;
                        baseValue.write(writer);
                    }
                    else
                    {
                        writer.beginObject();
                        @SuppressWarnings("unchecked")
                        Map<String, BaseValue<?>> mapInner = (Map<String, BaseValue<?>>)subValue;
                        for (Map.Entry<String, BaseValue<?>> subEntryInner : mapInner.entrySet())
                        {
                            String subNameInner = subEntryInner.getKey();
                            Object subValueInner = subEntryInner.getValue();
                            writer.name(subNameInner);
                            if (subValueInner == null)
                            {
                                LOG.warn("Missing value in slot "+path(group, subName, subNameInner));
                            }
                            else
                            {
                                BaseValue<?> baseValue = (BaseValue<?>)subValue;
                                baseValue.write(writer);
                            }
                        }
                        writer.endObject();
                    }
                }
                writer.endObject();
            }
        }
        writer.endObject();
    }

    void handleSlashCommand(SlashCommandInteractionEvent event)
    {
        String path = path(event.getSubcommandGroup(), null, event.getSubcommandName());
        Consumer<SlashCommandInteractionEvent> handler = this.handlers.get(path);
        if (handler == null)
        {
            LOG.error("Missing handler for "+path);
        }
        else
        {
            handler.accept(event);
            if (!event.isAcknowledged())
            {
                LOG.error("Handler for "+path+" failed to acknowledge command");
                event.reply("OK").setEphemeral(true).queue(hook -> hook.deleteOriginal().queueAfter(5_000L, TimeUnit.MILLISECONDS));
            }
        }
    }

    void handleSlashCommandComplete(CommandAutoCompleteInteractionEvent event)
    {
        AutoCompleteQuery query = event.getFocusedOption();
        String path = path(event.getSubcommandGroup(), null, event.getSubcommandName())+"/"+query.getName();
        Consumer<CommandAutoCompleteInteractionEvent> autocomplete = this.autocompletes.get(path);
        if (autocomplete == null)
        {
            LOG.error("Missing autocomplete for "+path);
        }
        else
        {
            autocomplete.accept(event);
            if (!event.isAcknowledged())
            {
                LOG.error("Handler for "+path+" failed to acknowledge autocomplete");
                event.replyChoices().queue();
            }
        }
    }

    <T> void modified(String group, String subgroup, String name, BaseValue<T> value, T prev, T next)
    {
        
    }

}
