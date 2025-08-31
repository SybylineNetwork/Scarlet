
# Frequently Asked Questions

### Why does `/query-actor-history` not show anything for someone I banned?

The `/query-actor-history` command lists events that were *initiated or performed by* a user, e.g., a group staff user.
You should be using the `/query-target-history` command instead.

### How do I remove an audit channel?

If you want to stop logging a certain event to a channel, simply *omit* the channel parameter of the appropriate command, like this: `/set-audit-channel audit-event-type:'Instance Close'`

### Why is logging delayed by several minutes?

Scarlet polls VRChat at a configurable set interval (30 seconds by default)
The system clock of the computer running Scarlet might be behind by a few minutes; you may have to update the system clock.

### Why are some events not being logged?

Some extended events (e.g., User Join/Leave, Vote-to-Kick Initiated, User Spawn Sticker/Print/Pedestal/Emoji) require that Scarlet is running on the same system as a VRChat client in your group's instance.
You may also need to enable full logging, enable certain content visibility in the VRChat Settings, or launch VRChat with command-line arguments (i.e., specified via the Steam Client).

Additionally, to ensure the most reliable visibility for certain content:
- For most extended events:
  - VRChat -> Settings -> Debug -> Logging: `Full`
  - Steam -> Library -> VRChat -> Manage -> Properties... -> General -> Launch Options -> "Advanced users may choose...": `--enable-debug-gui --enable-sdk-log-levels --enable-udon-debug-logging --enable-verbose-logging --log-debug-levels="API;All;Always;AssetBundleDownloadManager;ContentCreator;Errors;ModerationManager;NetworkData;NetworkProcessing;NetworkTransport;Warnings"`
- For Emojis, Prints, Stickers, Pedestals:
  - VRChat -> Settings -> Shield Levels -> ensure Custom Images is `On` for each user Trust Rank
  - For Prints in particular:
    - VRChat -> Settings -> Comfort & Safety -> Sharing -> View Prints: `On`, View Prints From: `Everyone`
  - For Pedestals in particular:
    - VRChat -> Settings -> Comfort & Safety -> Sharing -> Allow Pedestal Sharing: `On`, View Pedestals From: `Everyone`
- For Avatars:
  - VRChat -> Settings -> Shield Levels -> ensure Avatars is `On` for each user Trust Rank
  - VRChat -> Settings -> Avatars -> Avatar Optimizations -> Block Poorly Optimized Avatars: `Don't Block`
  - VRChat -> Settings -> Avatars -> Avatar Culling -> Hide Avatars Beyond: `Off`, Maximum Shown Avatars
