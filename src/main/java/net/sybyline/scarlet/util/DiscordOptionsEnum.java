package net.sybyline.scarlet.util;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.IntStream;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public final class DiscordOptionsEnum<E extends Enum<E>>
{

    public static <E extends Enum<E>> DiscordOptionsEnum<E> of(Class<E> type, Function<E, String> toId, String... names)
    {
        return of(type, type.getEnumConstants(), toId, names);
    }
    public static <E extends Enum<E>> DiscordOptionsEnum<E> of(Class<E> type, E[] enums, Function<E, String> toId, String... names)
    {
        if (enums.length != names.length)
            throw new IllegalArgumentException("Generated options for "+type+" must have "+enums.length+" names, only "+names.length+" provided");
        return of(type, enums, toId, $ -> names[MiscUtils.indexOf($, enums)]);
    }

    public static <E extends Enum<E>> DiscordOptionsEnum<E> of(Class<E> type, Function<E, String> toId, Function<E, String> toName)
    {
        return of(type, type.getEnumConstants(), toId, toName);
    }

    public static <E extends Enum<E>> DiscordOptionsEnum<E> of(Class<E> type, E[] enums, Function<E, String> toId, Function<E, String> toName)
    {
        return new DiscordOptionsEnum<>(type, enums, toId, toName);
    }

    public DiscordOptionsEnum(Class<E> type, Function<E, String> toId, String... names)
    {
        E[] enums = type.getEnumConstants();
        int len = enums.length;
        
        this.type = type;
        this.enums = enums;
        this.count = len;
        this.ids = new String[len];
        this.ids_san = new String[len];
        this.names = names;
        this.names_san = new String[len];
        this.choices = new Command.Choice[len];
        for (int idx = 0; idx < len; idx++)
        {
            String id = toId.apply(enums[idx]),
                   name = names[idx];
            this.ids[idx] = id;
            this.ids_san[idx] = id.toLowerCase().replaceAll("[^0-9a-z]", "");
            this.names_san[idx] = name.toLowerCase().replaceAll("[^0-9a-z]", "");
            this.choices[idx] = new Command.Choice(name, id);
        }
    }

    public DiscordOptionsEnum(Class<E> type, E[] enums, Function<E, String> toId, Function<E, String> toName)
    {
        int len = enums.length;
        
        this.type = type;
        this.enums = enums;
        this.count = len;
        this.ids = new String[len];
        this.ids_san = new String[len];
        this.names = new String[len];
        this.names_san = new String[len];
        this.choices = new Command.Choice[len];
        for (int idx = 0; idx < len; idx++)
        {
            String id = toId.apply(enums[idx]),
                   name = toName.apply(enums[idx]);
            this.ids[idx] = id;
            this.ids_san[idx] = id.toLowerCase().replaceAll("[^0-9a-z]", "");
            this.names[idx] = name;
            this.names_san[idx] = name.toLowerCase().replaceAll("[^0-9a-z]", "");
            this.choices[idx] = new Command.Choice(name, id);
        }
    }

    public final Class<E> type;
    private final E[] enums;
    public final int count;
    public final String[] ids, names;
    public final Command.Choice[] choices;
    private final String[] ids_san, names_san;

    public E byId(String id)
    {
        for (int idx = 0, len = this.count; idx < len; idx++)
        {
            if (this.ids[idx].equals(id))
            {
                return this.enums[idx];
            }
        }
        return null;
    }

    public IntStream indices(String typing)
    {
        if (typing == null || (typing = typing.trim()).isEmpty())
            return IntStream.range(0, Math.min(this.count, 25));
        String typing_san = typing.toLowerCase().replaceAll("[^0-9a-z]", "");
        return IntStream
            .range(0, this.count)
            .mapToObj(Integer::valueOf)
            .sorted(Comparator.<Integer>comparingInt($ ->
            {
                String id_san = this.ids_san[$],
                       name_san = this.names_san[$];
                return id_san.startsWith(typing_san) || name_san.startsWith(typing_san)
                        ? 0
                    : id_san.contains(typing_san) || name_san.contains(typing_san)
                        ? 1
                    : 2;
            }))
            .mapToInt(Integer::intValue)
            .limit(25L)
            ;
    }

    public String[] ids(String typing)
    {
        return this.indices(typing).mapToObj($ -> this.ids[$]).toArray(String[]::new);
    }

    public Command.Choice[] choices(String typing)
    {
        return this.indices(typing).mapToObj($ -> this.choices[$]).toArray(Command.Choice[]::new);
    }

    public void replyQueue(CommandAutoCompleteInteractionEvent event)
    {
        event.replyChoices(this.choices(event.getFocusedOption().getValue())).queue();
    }

    public E getAsEnum(OptionMapping mapping)
    {
        return mapping == null ? null : this.byId(mapping.getAsString());
    }

}
