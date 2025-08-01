package net.sybyline.scarlet.server.discord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationMap;

public interface DCommands
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Discord/DCommands");

    static void delta(List<Command> current, List<CommandData> data, Consumer<CommandData> needsAdd, Consumer<Command> needsRemove, BiConsumer<Command, CommandData> needsEdit, BiConsumer<Command, CommandData> identical)
    {
        current = new ArrayList<>(current);
        for (CommandData datum : data)
        {
            String name = datum.getType()+" command "+datum.getName();
            if (!current.removeIf($ ->
            {
                if (!same_command($, datum))
                    return false;
                if (equals_command($, datum))
                {
                    LOG.info("Skipping action for "+name+", identical already exists");
                    identical.accept($, datum);
                    return true;
                }
                LOG.info("Queuing edit for "+name);
                needsEdit.accept($, datum);
                return true;
            }))
            {
                LOG.info("Queuing upsert for "+name);
                needsAdd.accept(datum);
            }
        }
        for (Command cmd : current)
        {
            LOG.info("Queuing delete for "+cmd.getType()+" command "+cmd.getName());
            needsRemove.accept(cmd);
        }
    }

    static boolean neq(String path, Object current, Object data)
    {
        LOG.debug(String.format("  %s%s.%s: %s -> %s", DCommandsUtil.command, DCommandsUtil.option, path, current, data));
        return false;
    }

    static boolean same_command(Command current, CommandData data)
    {
        if (!Objects.equals(data.getType(), current.getType()))
            return false;
        if (!Objects.equals(data.getName(), current.getName()))
            return false;
        return true;
    }

    static boolean equals_command(Command current, CommandData data)
    {
String prevCommand = DCommandsUtil.command; DCommandsUtil.command = current.getFullCommandName()+' '; try {
        if (!Objects.equals(data.getType(), current.getType()))
            return neq("type", current.getType(), data.getType());
        if (!Objects.equals(data.getName(), current.getName()))
            return neq("name", current.getName(), data.getName());
        if (data.isGuildOnly() != current.isGuildOnly())
            return neq("guildOnly", current.isGuildOnly(), data.isGuildOnly());
        if (data.isNSFW() != current.isNSFW())
            return neq("NSFW", current.isNSFW(), data.isNSFW());
        if (!equals_locale(data.getNameLocalizations(), current.getNameLocalizations()))
            return neq("nameLocalizations", current.getNameLocalizations().toMap(), data.getNameLocalizations().toMap());
        if (data instanceof SlashCommandData)
        {
            SlashCommandData slash = (SlashCommandData)data;
            if (!Objects.equals(slash.getDescription(), current.getDescription()))
                return neq("description", current.getDescription(), slash.getDescription());
            if (!Objects.equals(slash.getDefaultPermissions().getPermissionsRaw(), current.getDefaultPermissions().getPermissionsRaw()))
                return neq("defaultPermissions", current.getDefaultPermissions().getPermissionsRaw(), slash.getDefaultPermissions().getPermissionsRaw());
            if (!equals_locale(slash.getDescriptionLocalizations(), current.getDescriptionLocalizations()))
                return neq("descriptionLocalizations", current.getDescriptionLocalizations().toMap(), slash.getDescriptionLocalizations().toMap());
            if (current.getType() == Command.Type.SLASH)
            {
                if (!equals_list(slash.getSubcommandGroups(), SubcommandGroupData::getName, current.getSubcommandGroups(), Command.SubcommandGroup::getName, DCommands::equals_group))
                    return false;
                if (!equals_list(slash.getSubcommands(), SubcommandData::getName, current.getSubcommands(), Command.Subcommand::getName, DCommands::equals_sub))
                    return false;
                if (!equals_list(slash.getOptions(), OptionData::getName, current.getOptions(), Command.Option::getName, DCommands::equals_option))
                    return false;
            }
        }
        return true;
} finally { DCommandsUtil.command = prevCommand; }
    }
    static boolean equals_group(SubcommandGroupData data, Command.SubcommandGroup current)
    {
String prevCommand = DCommandsUtil.command; DCommandsUtil.command = current.getFullCommandName()+' '; try {
        if (!Objects.equals(data.getName(), current.getName()))
            return neq("name", current.getName(), data.getName());
        if (!Objects.equals(data.getDescription(), current.getDescription()))
            return neq("description", current.getDescription(), data.getDescription());
        if (!equals_locale(data.getNameLocalizations(), current.getNameLocalizations()))
            return neq("dameLocalizations", current.getNameLocalizations().toMap(), data.getNameLocalizations().toMap());
        if (!equals_locale(data.getDescriptionLocalizations(), current.getDescriptionLocalizations()))
            return neq("descriptionLocalizations", current.getDescriptionLocalizations().toMap(), data.getDescriptionLocalizations().toMap());
        if (!equals_list(data.getSubcommands(), SubcommandData::getName, current.getSubcommands(), Command.Subcommand::getName, DCommands::equals_sub))
            return false;
        return true;
} finally { DCommandsUtil.command = prevCommand; }
    }
    static boolean equals_sub(SubcommandData data, Command.Subcommand current)
    {
String prevCommand = DCommandsUtil.command; DCommandsUtil.command = current.getFullCommandName()+' '; try {
        if (!Objects.equals(data.getName(), current.getName()))
            return neq("name", current.getName(), data.getName());
        if (!Objects.equals(data.getDescription(), current.getDescription()))
            return neq("description", current.getDescription(), data.getDescription());
        if (!equals_locale(data.getNameLocalizations(), current.getNameLocalizations()))
            return neq("nameLocalizations", current.getNameLocalizations().toMap(), data.getNameLocalizations().toMap());
        if (!equals_locale(data.getDescriptionLocalizations(), current.getDescriptionLocalizations()))
            return neq("descriptionLocalizations", current.getDescriptionLocalizations().toMap(), data.getDescriptionLocalizations().toMap());
        if (!equals_list(data.getOptions(), OptionData::getName, current.getOptions(), Command.Option::getName, DCommands::equals_option))
            return false;
        return true;
} finally { DCommandsUtil.command = prevCommand; }
    }
    static boolean equals_option(OptionData data, Command.Option current)
    {
        if (!Objects.equals(data.getType(), current.getType()))
            return neq("type", current.getName(), data.getName());
        if (!Objects.equals(data.getName(), current.getName()))
            return neq("name", current.getName(), data.getName());
        if (!Objects.equals(data.getDescription(), current.getDescription()))
            return neq("description", current.getDescription(), data.getDescription());
        if (!equals_num(data.getType(), data.getMinValue(), current.getMinValue()))
            return neq("minValue", current.getMinValue(), data.getMinValue());
        if (!equals_num(data.getType(), data.getMaxValue(), current.getMaxValue()))
            return neq("maxValue", current.getMaxValue(), data.getMaxValue());
        if (!equals_num(data.getType(), data.getMinLength(), current.getMinLength()))
            return neq("minLength", current.getMinLength(), data.getMinLength());
        if (!equals_num(data.getType(), data.getMaxLength(), current.getMaxLength()))
            return neq("maxLength", current.getMaxLength(), data.getMaxLength());
        if (!equals_locale(data.getNameLocalizations(), current.getNameLocalizations()))
            return neq("nameLocalizations", current.getNameLocalizations().toMap(), data.getNameLocalizations().toMap());
        if (!equals_locale(data.getDescriptionLocalizations(), current.getDescriptionLocalizations()))
            return neq("descriptionLocalizations", current.getDescriptionLocalizations().toMap(), data.getDescriptionLocalizations().toMap());
DCommandsUtil.option = "/ "+current.getName()+' '; try {
        if (!equals_list(data.getChoices(), Command.Choice::getName, current.getChoices(), Command.Choice::getName, DCommands::equals_choice))
            return false;
} finally { DCommandsUtil.option = ""; }
        return true;
    }
    static boolean equals_choice(Command.Choice data, Command.Choice current)
    {
        if (!Objects.equals(data.getType(), current.getType()))
            return neq("type", current.getType(), data.getType());
        if (!Objects.equals(data.getName(), current.getName()))
            return neq("name", current.getName(), data.getName());
        if (OptionType.NUMBER == data.getType() && current.getAsDouble() != data.getAsDouble())
            return neq("value.double", current.getAsDouble(), data.getAsDouble());
        else if (OptionType.INTEGER == data.getType() && current.getAsLong() != data.getAsLong())
            return neq("value.long", current.getAsLong(), data.getAsLong());
        else if (!Objects.equals(current.getAsString(), data.getAsString()))
            return neq("value.string", current.getAsString(), data.getAsString());
        return true;
    }
    static boolean equals_locale(LocalizationMap data, LocalizationMap current)
    {
        if ((data == null || data.toMap().isEmpty()) && (current == null || current.toMap().isEmpty()))
            return true;
        for (DiscordLocale locale : DiscordLocale.values())
            if (!Objects.equals(data.get(locale), current.get(locale)))
                return false;
        return true;
    }

    static boolean equals_num(OptionType type, Number data, Number current)
    {
        if (data == null && current == null)
            return true;
        if (data == null || current == null)
            return false;
        if (OptionType.NUMBER == type)
            return data.doubleValue() == current.doubleValue();
        return data.longValue() == current.longValue();
    }

    static <Current, Data> boolean equals_list(List<Current> data, Function<Current, String> dataName, List<Data> current, Function<Data, String> currentName, BiPredicate<Current, Data> equalifier)
    {
        if (data.size() != current.size())
            return false;
        data = new ArrayList<>(data);
        current = new ArrayList<>(current);
        data.sort(Comparator.comparing(dataName));
        current.sort(Comparator.comparing(currentName));
        for (int idx = 0, len = data.size(); idx < len; idx++)
            if (!equalifier.test(data.get(idx), current.get(idx)))
                return false;
        return true;
    }

}

class DCommandsUtil
{
    static String command = "";
    static String option = "";
    static String value(Object value)
    {
        return command + option + value;
    }
}

