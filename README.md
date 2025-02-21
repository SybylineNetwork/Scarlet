[discord-invite]: https://discord.gg/x568Ph434w
[discord-widget]: https://discord.com/api/guilds/1342131776876838912/widget.png

[![discord-widget][]][discord-invite]

# Scarlet

A self-hostable VRChat Group management utility with Discord integration

[Changelog](https://github.com/SybylineNetwork/Scarlet/blob/main/CHANGELOG.md)

[Settings](https://github.com/SybylineNetwork/Scarlet/blob/main/SETTINGS.md)

[Installation](#installation)

## Features

### Discord commands

Discord slash commands:
  - `create-or-update-moderation-tag <value:string> <label:string?> <description:string?>`<br>
    Adds or updates a custom moderation tag (max of 25)<br>
    Example: `/create-or-update-moderation-tag "trolling" "Trolling" "Provocative or mocking behavior intended to antagonize someone"`
    - `value` The internal name of the tag
    - `label` The display name of the tag
    - `description` The description text of the tag
  - `delete-moderation-tag <value:string>`<br>
    Removes a custom moderation tag (max of 25)<br>
    Example: `/delete-moderation-tag "trolling"`
    - `value` The internal name of the tag
  - `associate-ids <discord-user:user> <vrchat-user:string>`<br>
    Associates a VRChat account with a Discord account<br>
    Example: `/associate-ids <@123456789123456789> "usr_00000000-0000-0000-0000-000000000000"`
    - `discord-user` The Discord user
    - `vrchat-user` The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)
  - `vrchat-user-info <vrchat-user:string>`<br>
    Lists internal and audit information for a specific VRChat user<br>
    Example: `/vrchat-user-info "usr_00000000-0000-0000-0000-000000000000"`
    - `vrchat-user` The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)
  - `query-target-history <vrchat-user:string> <days-back:int?>`<br>
    Queries audit events targeting a specific VRChat user<br>
    Example: `/vrchat-user-info "usr_00000000-0000-0000-0000-000000000000" "14"`
    - `vrchat-user` The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)
    - `days-back` The number of days into the past to search for events
  - `query-actor-history <vrchat-user:string> <days-back:int?>`<br>
    Queries audit events actored by a specific VRChat user<br>
    Example: `/vrchat-user-info "usr_00000000-0000-0000-0000-000000000000" "14"`
    - `vrchat-user` The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)
    - `days-back` The number of days into the past to search for events
  - `set-audit-channel <audit-event-type:string> <discord-channel:channel?>`<br>
    Sets a given text channel as the channel certain audit event types use<br>
    Example: `/set-audit-channel "group.instance.kick" <#log-instance-kicks>`
    - `audit-event-type` The VRChat Group Audit Log event type
    - `discord-channel` The Discord channel to use, or omit to remove entry
  - `set-voice-channel <discord-channel:channel?>`<br>
    Sets a given voice channel as the channel in which to announce TTS messages<br>
    Example: `/set-voice-channel <#staff-in-instance>`
    - `discord-channel` The Discord channel to use, or omit to remove entry
  - `set-permission-role <scarlet-permission:string> <discord-role:role?>`<br>
    Sets a given Scarlet-specific permission to be associated with a given Discord role<br>
    Example: `/set-permission-role "event.set_tags" <@123456789123456789>`
    - `scarlet-permission` The Scarlet-specific permission
    - `discord-role` The Discord role to use, or omit to remove entry
  - `config-info`<br>
    Shows information about the current configuration<br>
    Example: `/config-info`

Discord message command:
  - `submit-attachments`<br>
    Submits files attached to this message to Scarlet, such as moderation evidence

Attachments submitted to moderation events are stored in a subdirectory of the path specified by `evidenceRoot` in the `discord_bot.json` settings file.
If a submitted attachment has the same name as a file already submitted on the same target user, the submitting user is notified and the new attachment is not saved.
Example:
1. The `evidenceRoot` for submissions is `C:/Users/Scarlet/Desktop/evidence`
2. User submits image `screenshot.png` on audit event targeting `usr_00000000-0000-0000-0000-000000000000`
3. Image is saved to `C:/Users/Scarlet/Desktop/evidence/usr_00000000-0000-0000-0000-000000000000/screenshot.png`

### CLI commands

Scarlet has several commands you can enter via standard input:
  - `exit`, `halt`, `quit`, `stop`<br>
    Shuts down the application
  - `logout`<br>
    Logs out of the VRChat account and shuts down the application
  - `explore`<br>
    Browses to the folder Scarlet uses to store data
  - `tts <message...>`<br>
    Queues a TTS message to be read in the Discord Voice channel, if connected
  - `link <vrcUserId> <discordUserSnowflake>`<br>
    Associates a VRChat account with a Discord account
  - `importgroups <file|url...>`<br>
    Imports a legacy CSV list of watched groups from a file or url

## Installation

You will need:
  - A VRChat group you (or a dedicated bot account) have permissions
  - A Discord server (guild) in which you have permissions
  - A Discord bot account (application)
  - A Windows PC with Java 8 installed

If you wish to not install Java 8 for all users on the PC, if you would like to have several different Java installations, or if you would otherwise prefer to keep the Java 8 installation to Scarlet only, see the instructions further below.

### Setting up Discord application

It is recommended that you create a bot account dedicated specifically for running Scarlet.
Create a new application via the Discord developer dashboard: https://discord.com/developers/applications

Scarlet requires some permissions above the bare defaults:
1. In the `Installation` tab for your app:
    - Scarlet only supports installation for servers.<br>
      Ensure that only the `Guild Install` box is checked in the `Installation Contexts` area.<br>
      ![setup installation contexts](https://github.com/SybylineNetwork/Scarlet/blob/main/images/setup_installation_contexts.png?raw=true)
    - In the `Guild Install` part of the `Default Install Settings` area:
      - Add the `bot` scope.
      - Add the `Attach Files`, `Create Polls`, `Create Public Threads`, `Embed Links`, `Manage Webhooks`, `Read Message History`, `Send Messages`, `Send Messages in Threads`, `Speak`, and `View Channels` permissions.<br>
        ![setup default install settings](https://github.com/SybylineNetwork/Scarlet/blob/main/images/setup_default_install_settings.png?raw=true)
2. In the `Bot` tab:
    - Enable the `Server Members` and `Presence` intents in the `Privileged Gateway Intents` area.<br>
      ![setup privileged gateway intents](https://github.com/SybylineNetwork/Scarlet/blob/main/images/setup_privileged_gateway_intents.png?raw=true)

### Setting up the VRChat group

The VRChat account that Scarlet will use must have at least these permissions:
  - `Manage Group Member Data`
  - `View Audit log`
  - `View All Members`

At the moment, Scarlet does not enforce any moderation action against users.
All such actions (e.g., kicking or banning a user) must be performed via first-party methods.

### Installing the Scarlet desktop application

1. Download the latest release (`zip` is recommended): https://github.com/SybylineNetwork/Scarlet/releases/latest
2. Copy or extract the files into the directory of your choosing.
3. If you have Java 8 installed to the system PATH, skip this step.
    - Download and extract a Java 8 JDK, such as the file named like `OpenJDK8U-jdk_x64_windows_hotspot_8u???b??.zip` from https://github.com/adoptium/temurin8-binaries/releases/latest
    - Remove the `@rem ` comment syntax from the beginnings of lines 2 and 3 of `run.bat`
    - At the end of line 2, append the path of the directory of the JDK you extracted.<br>
      ![setup edit runner](https://github.com/SybylineNetwork/Scarlet/blob/main/images/setup_edit_runner.png?raw=true)
    - If you update Scarlet, remember to either update the version of the `.jar` on line 4:<br>
      `java -jar scarlet-?.?.?.jar`.
4. Run the `run.bat`

