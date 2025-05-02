
# Settings

All settings files for Scarlet can be found in the directory `%LOCALAPPDATA%\SybylineNetwork\Scarlet`.
Some internal volatile data can be found in the Windows registry.
These registry entries exist because of the use of classes in the [`java.util.prefs`](https://docs.oracle.com/javase/8/docs/api/java/util/prefs/package-summary.html) package.

If it isn't obvious, all nominally sensitive values listed are ficticious.

## file `logs/scarlet_log_0000-00-00_00-00-00.txt`

Files containing the output of the application log.

## file `caches/XXX/XXX_00000000-0000-0000-0000-000000000000.json`

Files containing cached objects from the VRChat API

## file `caches/XXX/known_404s.json`

List of ids known to return a `HTTP 404 Not Found` from the VRChat API
```json
[
    "XXX_00000000-0000-0000-0000-000000000000",
    "XXX_11111111-1111-1111-1111-111111111111"
]
```

## file `discord_bot.json`

Settings for the Discord bot account
```json
{
    // The Discord bot token
    "token": "MTIzNDU2Nzg5MTIzNDU2Nzg5.3q2-7w.VGhpcyB0ZXh0IGhhcyAyOCBjaGFyYWN0ZXJzLg", 
    
    // The Discord server snowflake id
    "guildSf": "123456789123456789",
    
    // Optional. The voice channel snowflake id
    "audioChannelSf": "123456789123456789",
    
    // Optional. Path to store evidence submitted for certain audit events
    "evidenceRoot": "C:/Users/Scarlet/Desktop/evidence",
    
    // Map of Scarlet-internal permission to Discord role snowflake id
    "scarletPermission2roleSf": {
        "event.set_tags": "123456789123456789",
        "event.submit_evidence": "123456789123456789"
    },
    
    // Map of auxiliary webhook internal ids to Discord webhook urls
    "scarletAuxWh2webhookUrl ": {
        "External Webhook One": "https://discord.com/api/webhooks/123456789123456789/IdzL4t3KvfQQ_CKYWd8Q2NM6Z7IaSOppoTTL4LS1Da_aMZ-eK1MbML-3ILnLjotWcDkp",
        "External Webhook Two": "https://discord.com/api/webhooks/123456789123456789/irNaGekj1zzccv__Uy4oC7d2GczNDbilnfBtcc5qL41bZ0Ikw1SXoYc7-bJST-yPdUQB"
    },
    
    // Map of audit entry event type to Discord channel snowflake id
    "auditType2channelSf": {
        "group.instance.kick": "123456789123456789",
        "group.user.ban": "123456789123456789",
        "group.instance.create": "123456789123456789"
    },
    
    // Map of extended entry event type to Discord channel snowflake id
    "auditTypeEx2channelSf": {
        "groupex.instance.staff.join": "123456789123456789",
        "groupex.instance.staff.leave": "123456789123456789",
        "groupex.instance.vtk": "123456789123456789"
    },
    
    // Map of audit entry event type to auxiliary webhook internal id(s)
    "auditType2scarletAuxWh": {
        "group.instance.kick": "External Webhook One",
        "group.instance.create": [ "External Webhook One", "External Webhook Two" ]
    },
    
    // Map of audit entry event type to Discord secret channel snowflake id
    "auditType2secretChannelSf": {
        "group.instance.kick": "123456789123456789",
        "group.user.ban": "123456789123456789",
        "group.instance.create": "123456789123456789"
    },
    
    // Map of extended entry event type to Discord secret channel snowflake id
    "auditTypeEx2secretChannelSf": {
    },
    
    // Map of audit entry event type to hex RGB
    "auditType2color": {
        "group.instance.kick": "FF7F00",
        "group.user.ban": "FF0000",
        "group.instance.create": "00FF00"
    }
}
```

## file `discord_perms.json`

Permissions for Discord users and roles
```json
{
    // Map of permission type to permission set
    "<PermissionType>": {
        "byUser": {
            "123456789123456789": {
                "<PermissionName>": true
            }
        },
        "byRole": {
            "123456789123456789": {
                "<PermissionName>": false
            }
        }
    },
    ...
}
```

## file `watched_groups.json`

Array of watched groups
```json
[
    {
        // The VRChat groupId
        "id": "grp_00000000-0000-0000-0000-000000000000",
        
        // The category of group activity, one of: "UNKNOWN", "MALICIOUS", "NUISANCE", "COMMUNITY", "AFFILIATED", or "OTHER"
        "type": "NUISANCE",
        
        // Array of custom moderation tags associated with group activity
        "tags": [
            "trolling"
        ],
        
        // Whether action must be taken rapidly
        "critical": true,
        
        // Message announced in the voice channel via TTS
        "message": "Anti-tupper group. Retaliate with documentation of custom tags."
    }
]
```

## file `moderation_tags.json`

Array of custom moderation tags
```json
[
    {
        // The internal name of the tag
        "value": "trolling",
        
        // The display name of the tag
        "label": "Trolling",
        
        // The description text of the tag
        "description": "Provocative or mocking behavior intended to antagonize someone"
    },
    {
        "value": "dislikes_tupper",
        "label": "Dislikes tupper",
        "description": "The most severe of all offences"
    }
]
```

## file `staff_list.json`

Array of VRChat userIds
```json
[
    "usr_00000000-0000-0000-0000-000000000000",
    "usr_11111111-1111-1111-1111-111111111111"
]
```

## file `secret_staff_list.json`

Array of VRChat userIds
```json
[
    "usr_00000000-0000-0000-0000-000000000000",
    "usr_11111111-1111-1111-1111-111111111111"
]
```

## file `report_template.txt`

Template for filling out the VRChat Help Desk report form
```txt
I am submitting a user report to be sent for {targetName} for {tags} in our group {groupCode}.
{targetName} entered our {instanceType} instance at {targetJoined}.
{targetName} was removed/left from our instance at {targetLeft}.
{description}

Thank you in advance,
{actorName}
```
Parameters are as such:
| Parameter | Description | Example value |
| --- | --- | --- |
| Group | | |
| `{groupId}` | The group ID | `grp_00000000-0000-0000-0000-000000000000` |
| `{groupName}` | The group name | `MyGroup` |
| `{groupUrl}` | The group URL | `https://vrchat.com/home/group/grp_00000000-0000-0000-0000-000000000000` |
| `{groupCode}` | The group short code | `GROUP.0000` |
| `{groupCodeUrl}` | The group URL via short code | `https://vrc.group/GROUP.0000` |
| Location | | |
| `{worldId}` | The world ID | `wrld_00000000-0000-0000-0000-000000000000` |
| `{worldName}` | The world name | `WorldName` |
| `{worldUrl}` | The world URL  | `https://vrchat.com/home/world/wrld_00000000-0000-0000-0000-000000000000` |
| `{location}` | The entire location string | `wrld_00000000-0000-0000-0000-000000000000:00000~group(grp_00000000-0000-0000-0000-00000000000)~groupAccessType(public)~region(us)` |
| `{instanceType}` | A formatted representation of the instance type | `18+ Group Public` |
| Actor | | |
| `{actorId}` | The actor user ID | `usr_00000000-0000-0000-0000-000000000000` |
| `{actorName}` | The actor user display name | `DisplayName` |
| `{actorUrl}` | The actor user URL | `https://vrchat.com/home/user/usr_00000000-0000-0000-0000-000000000000` |
| Target | | |
| `{targetId}` | The target user ID | `usr_00000000-0000-0000-0000-000000000000` |
| `{targetName}` | The target user display name | `DisplayName` |
| `{targetUrl}` | The target user URL | `https://vrchat.com/home/user/usr_00000000-0000-0000-0000-000000000000` |
| Target ext. | | |
| `{targetJoined}` | The formatted time the target joined the relevant instance | `2025-03-04 05:06:07 UTC` |
| `{targetLeft}` | The formatted time the target left the relevant instance | `2025-03-04 05:06:07 UTC` |
| Audit | | |
| `{tags}` | The custom moderation tags | `Trolling, Harassing, and Insufferable Attitude` |
| `{description}` | The custom moderation description | |
| `{auditId}` | The audit ID | `gaud_00000000-0000-0000-0000-000000000000` |
| Misc | | |
| `{appName}` | The name of this application | `Scarlet` |
| `{appVersion}` | The version of this application | `0.4.10` |

## file `settings.json`

General settings for Scarlet:
```json
{
    // The username used to authenticate with VRChat
    "vrc_username": "DefinitelyNOTtupper",
    
    // The password used to authenticate with VRChat
    "vrc_password": "password_cute_robot",
    
    // Optional. The TOTP secret used to two-factor authenticate with VRChat
    "vrc_secret": "abcd efgh ijkl mnop qrst uvxx yz23 4567",
    
    // The VRChat groupId of the relevant group
    "vrchat_group_id": "grp_00000000-0000-0000-0000-000000000000",
    
    // Whether the generated report link appends a footer containing the Group ID, Audit ID, and Scarlet version
    "vrchat_report_template_footer": "username@example.com",
    
    // Whether to announce watched groups with TTS
    "tts_announce_watched_groups": true,
    
    // The voice in which TTS speaks
    "tts_voice_name": "Microsoft Zira Desktop",
    
    // Whether to output TTS to the default system audio device
    "tts_use_default_audio_device": false,
    
    // Whether to announce watched groups with TTS
    "tts_announce_watched_groups": true,
    
    // Whether to announce new players with TTS
    "tts_announce_new_players": true,
    
    // Threshold for what account age qualifies as a 'new' player for the above (in days, 1 to 365, default 30)
    "tts_announce_players_newer_than_days": 30,
    
    // Whether to show a popup if a new version is availiable
    "ui_alert_update": true,
    
    // Whether still to show the above popup if a new preview version is availiable
    "ui_alert_update_preview": true,
    
    // Whether the UI becomes visible immediately or after loading is complete
    "ui_show_during_load": false,
    
    // The time, in seconds, between successive polls for audit events (10-300 inclusive)
    "audit_polling_interval": 60,
    
    // Whether to only list staff with activity on a Moderation Summary
    "moderation_summary_only_activity": false,
    
    // Whether to issue a ping to the Discord user whose associated VRChat user issues an Instance Warn
    "discord_ping_instance_warn": false,
    
    // Whether to issue a ping to the Discord user whose associated VRChat user issues an Instance Kick
    "discord_ping_instance_kick": false,
    
    // Whether to issue a ping to the Discord user whose associated VRChat user issues a Member Remove
    "discord_ping_member_remove": true,
    
    // Whether to issue a ping to the Discord user whose associated VRChat user issues a User Ban
    "discord_ping_user_ban": true,
    
    // Whether to issue a ping to the Discord user whose associated VRChat user issues a User Unban
    "discord_ping_user_unban": false,
    
    // Whether evidence submission is enabled
    "evidence_enabled": false
}
```

## file `pending_moderation_actions.json`

Saved pending moderation actions:
```json
{
    // Map of "{auditEntryType}:{targetUserId}" to actorUserIds
    "group.user.ban:usr_00000000-0000-0000-0000-000000000000": "usr_11111111-1111-1111-1111-111111111111"
}
```

## file `store.bin`

Saved cookies for authentication with VRChat, one cookie per line:
```txt
auth=authcookie_00000000-0000-0000-0000-000000000000; expires=Sun, 31 Feb 2069 12:34:56 GMT; path=/; httponly
twoFactorAuth=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ2RjhPd3NDRVRvIiwibWFjQWRkcmVzcyI6IjU2OjUyOjQzOjY4OjYxOjc0IiwidGltZXN0YW1wIjozMTI5NTYxMjk2MDAwLCJ2ZXJzaW9uIjoxLCJpYXQiOjMxMjk1NjEyOTYsImV4cCI6MzEyOTU2MTI5NiwiYXVkIjoiVlJDaGF0VHdvRmFjdG9yQXV0aCIsImlzcyI6IlZSQ2hhdCJ9.fdmmkOhjs0fas7DjizP_374ZNRuUpZcTPLVnx2vbfAk; expires=Sun, 31 Feb 2069 12:34:56 GMT; path=/; httponly
```

## file `data/global.json`

Saved global user information:
```json
{
    // Map of Discord user snowflake ids to VRChat userIds
    "userSnowflake2userId": {
        "123456789123456789": "usr_00000000-0000-0000-0000-000000000000"
    }
}
```

## file `data/live.json`

Current live instance information:
```json
{
    // Map of VRChat locations of live group instances to VRChat group audit entry ids
    "location2AuditEntryId": {
        "wrld_00000000-0000-0000-0000-000000000000:00000~group(grp_00000000-0000-0000-0000-000000000000)~groupAccessType(plus)~region(us)": "gaud_00000000-0000-0000-0000-000000000000"
    },
    
    // Map of VRChat locations of live group instances to Discord messages
    "location2instanceEmbedMessage": {
        "wrld_00000000-0000-0000-0000-000000000000:00000~group(grp_00000000-0000-0000-0000-000000000000)~groupAccessType(plus)~region(us)": {
            "guildSnowflake": "123456789123456789",
            "channelSnowflake": "123456789123456789",
            "messageSnowflake": "123456789123456789"
        }
    }
}
```

## file `data/usr/usr_00000000-0000-0000-0000-000000000000`

User specific information:
```json
{
    // Discord user snowflake id
    "userSnowflake": "123456789123456789",
    
    // Array of group audit entry ids targeting this user
    "auditEntryIds": [
        "gaud_00000000-0000-0000-0000-000000000000"
    ],
    
    // Array of submissions of evidence
    "evidenceSubmissions": [
        {
            // Audit entry id this evidence targets
            "auditEntryId": "gaud_00000000-0000-0000-0000-000000000000",
            
            // Discord snowflake id of the submitting user
            "submitterSnowflake": "123456789123456789",
            
            // Discord display name of the submitting user
            "submitterDisplayName": "ProbablyNotTupper",
            
            // When the file was submitted
            "submissionTime": "2069-02-31T12:34:56.789Z",
            
            // The name of the file
            "fileName": "screenshot.png",
            
            // The url of the attachment when uploaded
            "url": "https://cdn.discordapp.com/attachments/123456789123456789/123456789123456789/screenshot.png",
            
            // The proxy url of the attachment when uploaded
            "proxyUrl": "https://media.discordapp.net/attachments/123456789123456789/123456789123456789/screenshot.png"
        }
    ]
}
```

## file `data/gaud/gaud_00000000-0000-0000-0000-000000000000`

Group audit log event information:
```json
{
    // Discord server snowflake id
    "guildSnowflake": "123456789123456789",
    
    // Discord channel snowflake id
    "channelSnowflake": "123456789123456789",
    
    // Discord message snowflake id (This message has the embed)
    "messageSnowflake": "123456789123456789",
    
    // Discord thread snowflake id
    "threadSnowflake": "123456789123456789",
    
    // Discord channel snowflake id (This message has the interactions)
    "auxMessageSnowflake": "123456789123456789",
    
    // The saved entry itself
    "entry": {
        // <GroupAuditLogEntry Object>, See return data of Groups API:
        // https://vrchatapi.github.io/docs/api/#get-/groups/-groupId-/auditLogs
    },
    
    // Array of custom moderation tags
    "entryTags": [
        "trolling"
    ],
    
    // Array of submissions of evidence
    "entryDescription": "Myriad Cirnos fill the sky",
    
    // User id of the actor who used automation/assistance to effect this event
    "auxActorId": "usr_00000000-0000-0000-0000-000000000000",
    
    // Display name of the actor who used automation/assistance to effect this event
    "auxActorDisplayName": "Somebody",
    
    // Audit entry id of the event that directly and necessarily caused this event
    "parentEventId": "gaud_00000000-0000-0000-0000-000000000000",
}
```

## file `data/ex/00000000-0000-0000-0000-000000000000`

Extended audit event information:
```json
{
    // Extended event id
    "id": "00000000-0000-0000-0000-000000000000",
    
    // Extended event type
    "typeEx": "groupex.instance.vtk",
    
    // Actor id
    "actorId": "vrc_admin",
    
    // Actor display name
    "actorDisplayName": "VRChat Admin",
    
    // Target id
    "targetId": "usr_00000000-0000-0000-0000-000000000000",
    
    // When this extended event occurred
    "timestamp": "2069-02-31T12:34:56.789Z",
    
    // Data
    "data": {
    }
}
```

## file `./scarlet.version`

Contains th version of Scarlet to be run:
```txt
0.4.11-rc2
```

## file `./scarlet.version.target`

Contains the version of Scarlet to which to update (you can "update" backwards if necessary):
```txt
0.4.11-rc3
```

## file `./scarlet.home`

If present, specifies the directory in which to place the application's files:
```txt
C:\path\to\custom\data\dir
```

## file `./scarlet.home.java`

If present, specifies the root directory of the Java installation to use:
```txt
C:\path\to\jdk
```

## file `./java.options`

If present, prepends listed JVM options to the invoked command:
```txt
-Xms8G
-XX:+AggressiveOpts
-XX:+UnlockExperimentalVMOptions
-XX:+UnlockDiagnosticVMOptions
-XX:+AggressiveUnboxing
-XX:+AllowParallelDefineClass
-XX:+OptoBundling
-XX:+OptoScheduling
-XX:+UseFastAccessorMethods
-XX:+UseFastEmptyMethods
-XX:+ParallelRefProcEnabled
```

## registry

Volatile data:
```reg
[HKEY_CURRENT_USER\Software\JavaSoft\Prefs\net\sybyline\scarlet\grp_00000000-0000-0000-0000-000000000000]
; The version of Scarlet that had been run previously
"last/Run/Version"="0.4.9-rc2"
; The last time Scarlet queried the audit log
"last/Audit/Query"="2069-02-31/T12:34:56.789/Z"
; The last time Scarlet refreshed VRChat authentication
"last/Auth/Refresh"="2069-02-31/T12:34:56.789/Z"
; The last time Scarlet checked for updates
"last/Update/Check"="2069-02-31/T12:34:56.789/Z"
; The bounds of the UI window (x,y,width,height)
"ui/Bounds"="979,603,846,400"
```
As the Windows implementation of the `java.util.prefs` package uses the Windows registry, which is case-insensitive, `/` characters are used to "escape" uppercase characters to avoid clashes.
