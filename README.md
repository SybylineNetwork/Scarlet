[discord-invite]: https://discord.gg/x568Ph434w
[discord-widget]: https://discord.com/api/guilds/1342131776876838912/widget.png

[![discord-widget][]][discord-invite]

<img src="images/sybyline_scarlet.png?raw=true" alt="Scarlet logo" width="300" height="300"/>

# Scarlet

A self-hostable VRChat Group management utility with Discord integration

[Changelog](CHANGELOG.md)

[Settings](SETTINGS.md)

[Frequently Asked Questions](FAQ.md)

[Installation](#installation)

## Why Scarlet?

Scarlet is self-hosted, meaning that you have complete control of your own group's data.
There is no third party with access to your VRChat or Discord credentials or other sensitive information.
Since there is no automatic synchronization of data between groups running Scarlet, you don't have to worry about other groups seeing who your group has moderated or what groups you are tracking.

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
  - `watched-group`<br>
    Configures watched groups<br>
    Example: `/watched-group add-tag "grp_00000000-0000-0000-0000-000000000000" "trolling"`
  - `aux-webhooks`<br>
    Configures auxiliary webhooks<br>
    Example: `/aux-webhooks add "External Webhook One" "https://discord.com/api/webhooks/123456789123456789/IdzL4t3KvfQQ_CKYWd8Q2NM6Z7IaSOppoTTL4LS1Da_aMZ-eK1MbML-3ILnLjotWcDkp"`
  - `vrchat-user-ban <vrchat-user:string>`<br>
    Ban a specific VRChat user<br>
    Example: `/vrchat-user-ban "usr_00000000-0000-0000-0000-000000000000"`
    - `vrchat-user` The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)
  - `vrchat-user-unban <vrchat-user:string>`<br>
    Unban a specific VRChat user<br>
    Example: `/vrchat-user-unban "usr_00000000-0000-0000-0000-000000000000"`
    - `vrchat-user` The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)
  - `vrchat-user-info <vrchat-user:string>`<br>
    Lists internal and audit information for a specific VRChat user<br>
    Example: `/vrchat-user-info "usr_00000000-0000-0000-0000-000000000000"`
    - `vrchat-user` The VRChat user id (usr_XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX)
  - `discord-user-info <discord-user:user>`<br>
    Lists internal information for a specific Discord user<br>
    Example: `/discord-user-info <@123456789123456789>`
    - `discord-user` The Discord user
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
  - `set-audit-aux-webhooks <audit-event-type:string>`<br>
    Sets the given webhooks as the webhooks certain audit event types use<br>
    Example: `/set-audit-aux-webhooks "group.instance.kick"`
    - `audit-event-type` The VRChat Group Audit Log event type
  - `set-audit-ex-channel <audit-ex-event-type:string> <discord-channel:channel?>`<br>
    Sets a given text channel as the channel certain extended event types use<br>
    Example: `/set-audit-ex-channel "groupex.instance.vtk" <#log-instance-kicks>`
    - `audit-ex-event-type` The extended event type
    - `discord-channel` The Discord channel to use, or omit to remove entry
  - `set-audit-secret-channel <audit-event-type:string> <discord-channel:channel?>`<br>
    Sets a given text channel as the secret channel certain audit event types use<br>
    Example: `/set-audit-secret-channel "group.instance.kick" <#log-instance-kicks>`
    - `audit-event-type` The VRChat Group Audit Log event type
    - `discord-channel` The Discord channel to use, or omit to remove entry
  - `set-audit-ex-secret-channel <audit-ex-event-type:string> <discord-channel:channel?>`<br>
    Sets a given text channel as the secret channel certain extended event types use<br>
    Example: `/set-audit-ex-secret-channel "groupex.instance.vtk" <#log-instance-kicks>`
    - `audit-ex-event-type` The extended event type
    - `discord-channel` The Discord channel to use, or omit to remove entry
  - `set-voice-channel <discord-channel:channel?>`<br>
    Sets a given voice channel as the channel in which to announce TTS messages<br>
    Example: `/set-voice-channel <#staff-in-instance>`
    - `discord-channel` The Discord channel to use, or omit to remove entry
  - `set-tts-voice <voice-name:string>`<br>
    Sets the voice in which to announce TTS messages<br>
    Example: `/set-tts-voice "Microsoft David Desktop"`
    - `voice-name` The name of the voice to use
  - `scarlet-permission`<br> <scarlet-permission:string> <discord-role:role?>
    Sets a given Scarlet-specific permission to be associated with certain Discord roles<br>
    Example: `/scarlet-permission add-to-role "event.set_tags" <@123456789123456789>`
    - `scarlet-permission` The Scarlet-specific permission
    - `discord-role` The Discord role being granted or revoked permissions
  - `export-log <file-name:string?>`<br>
    Exports a Scarlet log file as an attachment<br>
    Example: `/export-log`
  - `config-info`<br>
    Shows information about the current configuration<br>
    Example: `/config-info`
  - `config-set`<br>
    Configures miscellaneous settings<br>
    Example: `/config-set mod-summary-time-of-day "-06:00"`
  - `server-restart`<br>
    Restarts the Scarlet server application<br>
    Example: `/server-restart`

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
  - `importgroupsjson <file|url...>`<br>
    Imports a JSON list of watched groups from a file or url

### Interoperability

Scarlet links to the official VRChat website whenever possible, including for user profiles, group posts, instances, and much more.
Scarlet can generate a link that will autopopulate the fields of the VRChat Help Desk report form based on custom tags assigned to moderation events, streamlining the reporting process and reducing mistakes from human error.

### About Extended Events

Extended audit events (Instance Inactive, Staff Join, Staff Leave, Vote-to-Kick Initiated) are logged with the Discord command `set-audit-ex-channel`.
Some of these require that a VRChat Client in a group instance must be running on the same machine in order for them to be logged in Discord channels.
This limitation exists because Scarlet reads the VRChat client log file as it gets updated with information.
At the moment, these events are not of the same degree as the canonical group audit events, as they do not have an , but similar functionality may hopefully be added to VRChat's first-party API in the future.

### About Actors and Targets

Actors are the users that *initiated or performed* an event, like a group staff member.
Targets are the users that had an event *performed on them*, like the user a group staff member kicks from an instance.
For some events, the target may not be a user, like for when a group instance is created.

## Installation

You will need:
  - A VRChat group you (or a dedicated bot account) have permissions
  - A Discord server (guild) in which you have permissions
  - A Discord bot account (application)
  - A Windows PC with Java 8 installed

If you wish to not install Java 8 for all users on the PC, if you would like to have several different Java installations, or if you would otherwise prefer to keep the Java 8 installation to Scarlet only, see the instructions further below.

Thanks to [@KozyBlake](https://github.com/KozyBlake) for making this video tutorial:

[![installation-tutorial-video](https://img.youtube.com/vi/JMJgMSThBac/0.jpg)](https://www.youtube.com/watch?v=JMJgMSThBac)

### Setting up Discord application

It is recommended that you create a bot account dedicated specifically for running Scarlet.
Create a new application via the Discord developer dashboard: https://discord.com/developers/applications

Scarlet requires some permissions above the bare defaults:
1. In the `Installation` tab for your app:
    - Scarlet only supports installation for servers.<br>
      Ensure that only the `Guild Install` box is checked in the `Installation Contexts` area.<br>
      ![setup installation contexts](images/setup_installation_contexts.png?raw=true)
    - In the `Guild Install` part of the `Default Install Settings` area:
      - Add the `bot` scope.
      - Add the `Attach Files`, `Create Polls`, `Create Public Threads`, `Embed Links`, `Manage Webhooks`, `Read Message History`, `Send Messages`, `Send Messages in Threads`, `Speak`, and `View Channels` permissions.<br>
        ![setup default install settings](images/setup_default_install_settings.png?raw=true)
2. In the `Bot` tab:
    - Enable the `Server Members` and `Message Content` intent in the `Privileged Gateway Intents` area.<br>
      ![setup privileged gateway intents](images/setup_privileged_gateway_intents.png?raw=true)
3. Invite the bot to your server
4. In the `Installation` tab for your app, select the `None` option for the `Install Link` area<br>
   ![setup install link](images/setup_install_link.png?raw=true)

### Setting up the VRChat group

The VRChat account that Scarlet will use must have at least these permissions:
  - `Manage Group Member Data`
  - `View Audit log`
  - `View All Members`

At the moment, Scarlet does not enforce any moderation action against users.
All such actions (e.g., kicking or banning a user) must be performed manually, but Scarlet provides some convenience methods like Discord commands and buttons.

### Installing the Scarlet desktop application

1. Download the latest release (`zip` is recommended): https://github.com/SybylineNetwork/Scarlet/releases/latest
2. Copy or extract the files into the directory of your choosing.
3. If you have Java 8 installed to the system PATH, skip this step.
    - Download and extract a Java 8 JDK, such as this one from [Adoptium](https://adoptium.net/temurin/releases/?os=windows&arch=x64&package=jdk&version=8).
    - Remove the `@rem ` comment syntax from the beginnings of lines 2 and 3 of `run.bat`
    - At the end of line 2, append the path of the directory of the JDK you extracted.<br>
      ![setup edit runner](images/setup_edit_runner.png?raw=true)
    - If you update Scarlet, remember to either update the version of the `.jar` on line 4:<br>
      `java -jar scarlet-?.?.?.jar`.
4. If you want Scarlet to store data in a specific folder:
    - Remove the `@rem ` comment syntax from the beginning of line 4 of `run.bat`
    - At the end of line 4, append the path of the directory in which you want Scarlet to store data
    - If you want Scarlet to store data right next to the `.jar` file, specify `;` as the path instead
5. Run the `run.bat`

