package net.sybyline.scarlet.server.discord;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.sybyline.scarlet.util.MiscUtils;

public interface DEnum<DE extends Enum<DE> & DEnum<DE, V>, V>
{

    public default DE self()
    {
        @SuppressWarnings("unchecked")
        DE self = (DE)this;
        return self;
    }

    public abstract V value();

    public abstract String display();

    public abstract Command.Choice choice();

    public default Command.Choice[] choices()
    {
        return choices(this.self());
    }

    public abstract OptionType type();

    public abstract V map(OptionMapping mapping);

    public default DE mapEnum(OptionMapping mapping)
    {
        return of(this.self(), this.map(mapping));
    }

    public abstract SelectOption option();

    public default SelectOption[] options()
    {
        return options(this.self());
    }

    public abstract V map(ModalMapping mapping);

    public default DE mapEnum(ModalMapping mapping)
    {
        return of(this.self(), this.map(mapping));
    }

    interface DEnumString<DES extends Enum<DES> & DEnumString<DES>> extends DEnum<DES, String>
    {
        public static <DES extends Enum<DES> & DEnumString<DES>> List<String> evalList(DES[] array)
        {
            if (array == null || array.length == 0)
                return null;
            List<String> list = new ArrayList<>();
            for (DES des : array)
                if (des != null)
                    list.add(des.value());
            if (list.isEmpty())
                return null;
            return list;
        }
        public static <DES extends Enum<DES> & DEnumString<DES>> String[] evalArray(DES[] array)
        {
            return Optional.ofNullable(evalList(array)).map($ -> $.toArray(new String[$.size()])).orElse(null);
        }
        public default Function<? super OptionMapping, ? extends DES[]> mapEnums(String delimiter, int minChoices, int maxChoices)
        {
            return mapping -> this.mapEnums(mapping, delimiter, minChoices, maxChoices);
        }
        public default DInteractions.SlashCompleteHandler autocomplete(String delimiter, int minChoices, int maxChoices)
        {
            return event -> this.autocomplete(event, delimiter, minChoices, maxChoices);
        }
        @SuppressWarnings("unchecked")
        public default DES[] mapEnums(OptionMapping mapping, String delimiter, int minChoices, int maxChoices)
        {
            Class<DES> clazz = this.self().getDeclaringClass();
            DEnumInfo<DES, String> info = DEnumInfo.of(clazz);
            String[] strings = mapping.getAsString().split(delimiter);
            DES[] array = Stream.of(strings)
                    .map(string ->
                    {
                        try
                        {
                            return info.values[Integer.parseUnsignedInt(string, 10)];
                        }
                        catch (RuntimeException ex)
                        {
                            return info.byValue.get(string);
                        }
                    }).<DES>toArray($->(DES[])Array.newInstance(clazz, $));
            if (array.length < minChoices)
                throw new RuntimeException("Option "+mapping.getName()+" has too few values (min: "+minChoices+")");
            if (array.length > maxChoices)
                throw new RuntimeException("Option "+mapping.getName()+" has too many values (max: "+maxChoices+")");
            for (int i = 0; i < array.length; i++)
                if (array[i] == null)
                    throw new RuntimeException("Option "+mapping.getName()+" has an invalid value: "+strings[i]);
            for (int i = 0; i < array.length; i++)
                for (int j = 0; j < i; j++)
                    if (array[i] == array[j])
                        throw new RuntimeException("Option "+mapping.getName()+" has duplicate value: "+strings[i]);
            return array;
        }
        @SuppressWarnings("unchecked")
        public default DES[] mapEnums(ModalMapping mapping)
        {
            Class<DES> clazz = this.self().getDeclaringClass();
            return mapping.getAsStringList()
                .stream()
                .map(DEnumInfo.of(clazz).byValue::get)
                .filter(Objects::nonNull)
                .<DES>toArray($->(DES[])Array.newInstance(clazz, $));
        }
        public default void autocomplete(CommandAutoCompleteInteractionEvent event, String delimiter, int minChoices, int maxChoices)
        {
            String typings = event.getFocusedOption().getValue().trim();
            if (typings.isEmpty())
            {
                event.replyChoices(this.choices()).queue();
                return;
            }
            
            Class<DES> clazz = this.self().getDeclaringClass();
            DEnumInfo<DES, String> info = DEnumInfo.of(clazz);
            String[] typingsa = typings.split(delimiter);
            if (typingsa.length == 0)
            {
                event.replyChoices(this.choices()).queue();
                return;
            }
            if (typingsa.length == 1)
            {
                event.replyChoices(Arrays.stream(this.choices()).sorted(DInteractions.choicesByLevenshtein(typingsa[0])).limit(25L).collect(Collectors.toList())).queue();
                return;
            }

            StringBuilder nameBuf = new StringBuilder(),
                          valueBuf = new StringBuilder();
            Arrays.stream(typingsa, 0, typingsa.length - 1)
                .map(String::trim)
                .filter($ -> !$.isEmpty())
                .map(typing ->
                {
                    try
                    {
                        return info.values[Integer.parseUnsignedInt(typing, 10)];
                    }
                    catch (RuntimeException ex)
                    {
                        DES value = info.byValue.get(typing);
                        if (value == null) value = Stream
                            .of(info.choices)
                            .sorted(DInteractions.choicesByLevenshtein(typing))
                            .findFirst()
                            .map(Command.Choice::getAsString)
                            .map(info.byValue::get)
                            .orElse(null);
                        return value;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .forEachOrdered(value ->
                {
                    nameBuf.append(value.display()).append(delimiter);
                    valueBuf.append(value.ordinal()).append(delimiter);
                });
            String nameStr = nameBuf.toString(),
                   valueStr = valueBuf.toString();
            event.replyChoices(Arrays.stream(this.choices())
                .sorted(DInteractions.choicesByLevenshtein(typingsa[0]))
                .map(Command.Choice::getAsString)
                .map(string ->
                {
                    try
                    {
                        return info.values[Integer.parseUnsignedInt(string, 10)];
                    }
                    catch (RuntimeException ex)
                    {
                        return info.byValue.get(string);
                    }
                })
                .filter(Objects::nonNull)
                .limit(25L)
                .map(value -> new Command.Choice(MiscUtils.maybeEllipsisReverse(Command.Choice.MAX_NAME_LENGTH, nameStr+value.display()), valueStr+value.ordinal()))
                .collect(Collectors.toList()))
            .queue();
        }
        @Override
        default Command.Choice choice()
        {
            return new Command.Choice(this.display(), this.value().toString());
        }
        @Override
        default String map(OptionMapping mapping)
        {
            return mapping.getAsString();
        }
        @Override
        default SelectOption option()
        {
            return SelectOption.of(this.display(), this.value());
        }
        @Override
        default String map(ModalMapping mapping)
        {
            return mapping.getAsString();
        }
        @Override
        default OptionType type()
        {
            return OptionType.STRING;
        }
    }
    interface DEnumLong<DES extends Enum<DES> & DEnumLong<DES>> extends DEnum<DES, Long>
    {
        @Override
        default Command.Choice choice()
        {
            return new Command.Choice(this.display(), this.value().longValue());
        }
        @Override
        default Long map(OptionMapping mapping)
        {
            return mapping.getAsLong();
        }
        @Override
        default SelectOption option()
        {
            return SelectOption.of(this.display(), this.value().toString());
        }
        @Override
        default Long map(ModalMapping mapping)
        {
            return Long.parseLong(mapping.getAsString());
        }
        @Override
        default OptionType type()
        {
            return OptionType.INTEGER;
        }
    }
    interface DEnumDouble<DES extends Enum<DES> & DEnumDouble<DES>> extends DEnum<DES, Double>
    {
        @Override
        default Command.Choice choice()
        {
            return new Command.Choice(this.display(), this.value().doubleValue());
        }
        @Override
        default Double map(OptionMapping mapping)
        {
            return mapping.getAsDouble();
        }
        @Override
        default SelectOption option()
        {
            return SelectOption.of(this.display(), this.value().toString());
        }
        @Override
        default Double map(ModalMapping mapping)
        {
            return Double.parseDouble(mapping.getAsString());
        }
        @Override
        default OptionType type()
        {
            return OptionType.NUMBER;
        }
    }
    interface DEnumWrapper<DEW extends Enum<DEW> & DEnumWrapper<DEW, V>, V extends Enum<V>> extends DEnum<DEW, V>
    {
        @Override
        default Command.Choice choice()
        {
            return new Command.Choice(this.display(), this.value().toString());
        }
        @Override
        default V map(OptionMapping mapping)
        {
            return to(this.self(), mapping.getAsString());
        }
        @Override
        default SelectOption option()
        {
            return SelectOption.of(this.display(), this.value().toString());
        }
        @Override
        default V map(ModalMapping mapping)
        {
            return to(this.self(), mapping.getAsString());
        }
        @Override
        default OptionType type()
        {
            return OptionType.STRING;
        }
    }

    static <DE extends Enum<DE> & DEnum<DE, V>, V> Command.Choice[] choices(Class<DE> type)
    {
        return DEnumInfo.of(type).choices;
    }
    static <DE extends Enum<DE> & DEnum<DE, V>, V> Command.Choice[] choices(DE type)
    {
        return DEnumInfo.of(type.getDeclaringClass()).choices;
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static Command.Choice[] choicesRaw(Class type)
    {
        return DEnumInfo.of(type).choices;
    }
    static <DE extends Enum<DE> & DEnum<DE, V>, V> SelectOption[] options(Class<DE> type)
    {
        return DEnumInfo.of(type).options;
    }
    static <DE extends Enum<DE> & DEnum<DE, V>, V> SelectOption[] options(DE type)
    {
        return DEnumInfo.of(type.getDeclaringClass()).options;
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static SelectOption[] optionsRaw(Class type)
    {
        return DEnumInfo.of(type).options;
    }
    static <DE extends Enum<DE> & DEnum<DE, V>, V> DE of(Class<DE> type, V value)
    {
        return DEnumInfo.of(type).byValue.getOrDefault(value, null);
    }
    static <DE extends Enum<DE> & DEnum<DE, V>, V> DE of(DE type, V value)
    {
        return DEnumInfo.of(type.getDeclaringClass()).byValue.getOrDefault(value, type);
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static <T> T ofRaw(Class type, Object value)
    {
        return (T)DEnumInfo.of(type).byValue.getOrDefault(value, null);
    }
    static <DE extends Enum<DE> & DEnum<DE, V>, V> V to(Class<DE> type, Object value)
    {
        return DEnumInfo.of(type).byValue.getOrDefault(value, null).value();
    }
    static <DE extends Enum<DE> & DEnum<DE, V>, V> V to(DE type, Object value)
    {
        return DEnumInfo.of(type.getDeclaringClass()).byValue.getOrDefault(value, type).value();
    }

    class DEnumInfo<DE extends Enum<DE> & DEnum<DE, V>, V>
    {
        @SuppressWarnings("rawtypes")
        static final Map<Class/*<?>*/, DEnumInfo/*<?, ?>*/> cache = Collections.synchronizedMap(new WeakHashMap<>());
        @SuppressWarnings("unchecked")
        static <DE extends Enum<DE> & DEnum<DE, V>, V> DEnumInfo<DE, V> of(Class<DE> type)
        {
            return (DEnumInfo<DE, V>)cache.computeIfAbsent(type, DEnumInfo::new);
        }
        DEnumInfo(Class<DE> type)
        {
            DE[] enums = type.getEnumConstants();
            int count = enums.length;
            this.values = enums;
            this.choices = new Command.Choice[count];
            this.options = new SelectOption[count];
            this.byValue = new HashMap<>(count);
            this.toValue = new HashMap<>(count);
            for (int ord = 0; ord < count; ord++)
            {
                DE de = enums[ord];
                this.choices[ord] = de.choice();
                this.options[ord] = de.option();
                this.byValue.put(de.value(), de);
                this.toValue.put(de, de.value());
            }
        }
        final DE[] values;
        final Command.Choice[] choices;
        final SelectOption[] options;
        final Map<V, DE> byValue;
        final Map<DE, V> toValue;
        public Command.Choice[] choices()
        {
            return this.choices;
        }
        public SelectOption[] options()
        {
            return this.options;
        }
    }

}
