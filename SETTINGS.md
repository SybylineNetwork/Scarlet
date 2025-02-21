
# Settings

All settings files for Scarlet can be found in the directory `%LOCALAPPDATA%\SybylineNetwork\Scarlet`.
Some internal volatile data can be found in the Windows registry.
These registry entries exist because of the use of classes in the [`java.util.prefs`](https://docs.oracle.com/javase/8/docs/api/java/util/prefs/package-summary.html) package.

If it isn't obvious, all nominally sensitive values listed are ficticious.

## file `logs/scarlet_log_0000-00-00_00-00-00.txt`

Files containing the output of the application log.

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
    
    // Map of audit entry event type to Discord channel snowflake id
    "auditType2channelSf": {
        "group.instance.kick": "123456789123456789",
        "group.user.ban": "123456789123456789",
        "group.instance.create": "123456789123456789"
    },
    
    // Map of audit entry event type to hex RGB
    "auditType2color": {
        "group.instance.kick": "FF7F00",
        "group.user.ban": "FF0000",
        "group.instance.create": "00FF00"
    }
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
    "vrchat_group_id": "grp_00000000-0000-0000-0000-000000000000"  
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
    "entryDescription": "Myriad Cirnos fill the sky"
}
```

## registry

Volatile data:
```reg
[HKEY_CURRENT_USER\Software\JavaSoft\Prefs\net\sybyline\scarlet]
; The last time Scarlet queried the audit log
"last/Audit/Query"="2069-02-31/T12:34:56.789/Z"
; The last time Scarlet refreshed VRChat authentication
"last/Auth/Refresh"="2069-02-31/T12:34:56.789/Z"
```
As the Windows implementation of the `java.util.prefs` package uses the Windows registry, which is case-insensitive, `/` characters are used to "escape" uppercase characters to avoid clashes.
