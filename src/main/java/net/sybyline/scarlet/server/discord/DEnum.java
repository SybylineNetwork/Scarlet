package net.sybyline.scarlet.server.discord;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

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

    interface DEnumString<DES extends Enum<DES> & DEnumString<DES>> extends DEnum<DES, String>
    {
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
    static <DE extends Enum<DE> & DEnum<DE, V>, V> DE of(Class<DE> type, V value)
    {
        return DEnumInfo.of(type).byValue.getOrDefault(value, null);
    }
    static <DE extends Enum<DE> & DEnum<DE, V>, V> DE of(DE type, V value)
    {
        return DEnumInfo.of(type.getDeclaringClass()).byValue.getOrDefault(value, type);
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
            this.choices = new Command.Choice[count];
            this.byValue = new HashMap<>(count);
            this.toValue = new HashMap<>(count);
            for (int ord = 0; ord < count; ord++)
            {
                DE de = enums[ord];
                this.choices[ord] = de.choice();
                this.byValue.put(de.value(), de);
                this.toValue.put(de, de.value());
            }
        }
        final Command.Choice[] choices;
        final Map<V, DE> byValue;
        final Map<DE, V> toValue;
        public Command.Choice[] choices()
        {
            return this.choices;
        }
    }

}
