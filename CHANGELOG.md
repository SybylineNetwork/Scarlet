
# Changelog

## Unreleased
  - Pending: Staff & Instance Analysis, (live infographic?)

## 0.4.10-rc4
  - Added location to extended event embeds
  - Changed evidence submission to not require a direct reply, but instead to just be in the same thread

## 0.4.10-rc3
  - Added settings for enabling and selecting the evidence submission folder
  - Added listing auxiliary webhooks to `config-info`
  - Fixed some settings not getting created as default if they didn't exist

## 0.4.10-rc2
  - Fixed Email OTP (again)
  - Added Instance Inactive extended event
  - Staff mode now properly ignores audit log polling and related systems
  - Fixed app hanging on startup if no VRChat logs were found
  - Fixed log parsing for Vote-to-Kick Initiated
  - Fixed data not getting saved
  - Fixed the `/set-audit-ex-channel` having a typo in one of its options

## 0.4.10-rc1
  - Added staff list command `/staff-list`
  - Added extended events: Staff Join, Staff Leave, and Vote-to-Kick Initiated
  - Added audit event bundling: if a User Ban results in a Instance Kick, then the latter is logged with the former by default
  - Changed the command suggestions to display more human-friendly names over internal identifiers
  - Fixed Discord roles being mistakenly formatted like Discord users
  - Fixed moderation events initiated with commands or buttons not properly referencing the initiating user

## 0.4.9
  - Added ability to redact audit events: they do not count in the running sum of moderation events for embeds
  - Fixed autocomplete for the `scarlet-permission` Discord command not working
  - Fixed manual command update button not working

## 0.4.9-rc3
  - Added ability to revert instance UI to default ordering by right clicking header
  - Changed Discord command `set-permission-role` to `scarlet-permission` and added support for multiple roles
  - Fixed bug (#9) that tried to set a Discord embed field value to more than 1024 characters

## 0.4.9-rc2
  - Added application version change detection
  - Changed Discord bot to only send command list if version changes
  - Changed instance UI to only sort once the log tailing/parsing has caught up 
  - Changed watched groups sort order to prioritize `priority` instead of the flags `critical` and `silent`
  - Fixed NPE when emitting aux webhooks for certain events
  - Fixed watched groups added via Discord commands missing the `id` field, causing them to magically disappear

## 0.4.9-rc1
  - Fixed bug: Discord bot responds properly to the `set-audit-aux-webhooks` command
  - Updated dependency: com.github.vrchatapi:vrchatapi-java:1.18.9 to version 1.19.1

## 0.4.8
  - Added ability to silence TTS messages for particular groups
  - Added ability to import watched groups from UI
  - Fixed bug: removed references to restricted/internal api
  - Fixed regression: switched emailotp and totp auth flows

## 0.4.8-rc1
  - Added experimental "staff mode" (discord integration disabled, data like watched groups must be synced manually)
  - Added ability to ban and unban VRChat users with desktop UI, Discord buttons, and Discord commands
  - Added ability to disable pings on Discord for moderation audit events
  - Added priority system to groups
  - Added default sorting for instance UI
  - Added colored text based on group watch type for instance UI
  - Added ability to forward audit event messages to other Discord servers via webhooks
  - Added AuditID to autopopulation of description field for VRChat Help Desk Links
  - Added support for Email OTP
  - Fixed TTS subprocess hang if selected TTS voice isn't installed on the system
  - Fixed regression: implicit class loading from main class happened before dependencies were downloaded

## 0.4.7
  - Fixed regression: TOTP codes incorrectly reporting as invalid

## 0.4.6
  - Added splash screen
  - Added update check popup
  - Added automatic disabling log parser if VRChat client not present
  - Fixed auth refresh not triggering
  - Fixed setting TTS voice not working or persisting
  - Changed UI theming

## 0.4.6-rc1
  - Added list of users in current instance to the UI
  - Added various links to the UI
  - Added ability for group to autopopulate email field for VRChat Help Desk Links
  - Fixed watched groups not being properly detected

## 0.4.5
  - Added caching layer for some VRChat API requests
  - Added some autocomplete for the `watched-group` command
  - Added initial text `Unclaimed` to new moderation events
  - Fixed missing reprompt on invalid credentials
  - Fixed incorrect gateway intent in instructions

## 0.4.5-rc4
  - Added ability to specify where Scarlet stores data
  - Added ability to select a TTS voice
  - Added Discord commands to configure watched groups
  - Added Discord command to export Scarlet logs
  - Added dummy UI for ease of exiting the application
  - Added default command permissions for Discord commands
  - Changed log name format for JDA classes

## 0.4.5-rc1
  - Fixed NPE when checking if a joining player has watched groups

## 0.4.4
  - Fixed importing legacy CSV list of watched groups (Excel format, instead of RFC4180)

## 0.4.3
  - Added step to installation instructions to make discord bot private
  - Added newer version check
  - Added more build automation
  - Fixed discord bot responding to interactions from servers other than the server specified in configuration
  - Fixed discord bot sometimes not automatically rejoining voice channel
  - Fixed some errors not printing to log file
  - Changed link to download a JDK from potentially confusing Github link to Adoptium website proper

## 0.4.2
  - Added important step to installation instructions
  - Added `pause` line to runner
  - Removed input prompt for Discord audio channel snowflake
  - Fixed bug in dependency downloader not properly unescaping characters
  - Changed Java version check to warn instead of terminating application

## 0.4.1
  - Added logger thread and log files
  - Added importing watched groups legacy CSV via attachment submissions
  - Fixed bug where the process could hang upon quitting
  - Fixed typos and missing comments
  - Changed some logging levels
  - Changed handling of tags for importing watched groups legacy CSV

## 0.4.0
  - Added installation and usage instructions to README.md
  - Added CHANGELOG.md
  - Added SETTINGS.md
  - Added automation for building a zip
  - Added runtime check for JVM's Java version
  - Added ability to automatically generate TOTP code from secret
  - Added link and message reference to group audit event `group.instance.close`
  - Added TTSService to announce events in a Discord voice channel
  - Added VRChat log tailing/parsing
  - Added Watched Groups, which generate TTS announcements when members' joining is detected
  - Removed unused code and resources from automatic dependency downloader
  - Renamed `submit-evidence` Discord message command to `submit-attachments`
  - Fixed audit event embed color overrides being ignored
  - Fixed typos

## 0.3.3
  - Fixed escaping for VRChat Help Desk autofill link generation
  - Added role-associated permission system for interactions
  - Added archiver for evidence submission

## 0.3.2
  - Added button to autofill VRChat Help Desk report form
  - Added autodownloader for dependencies
  - Added builder for Windows

## 0.3.1
  - Initial public commit
