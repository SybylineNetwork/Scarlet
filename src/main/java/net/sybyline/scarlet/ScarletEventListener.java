package net.sybyline.scarlet;

import java.awt.Color;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.github.vrchatapi.model.LimitedUserGroups;
import io.github.vrchatapi.model.User;

import net.sybyline.scarlet.util.TTSService;

public class ScarletEventListener implements ScarletVRChatLogs.Listener, TTSService.Listener
{

    public ScarletEventListener(Scarlet scarlet)
    {
        this.scarlet = scarlet;
        
        this.clientUserDisplayName = null;
        this.clientUserId = null;
        this.clientLocation = null;
        this.clientLocationPrev = null;
        this.clientLocation_userId2userDisplayName = new ConcurrentHashMap<>();
        this.clientLocation_userDisplayName2userId = new ConcurrentHashMap<>();
        this.clientLocation_userDisplayName2avatarDisplayName = new ConcurrentHashMap<>();
        this.clientLocationPrev_userIds = new HashSet<>();
        
        this.isInGroupInstance = false;
        this.isSameAsPreviousInstance = false;
        
        this.ttsVoiceName = scarlet.ui.settingString("tts_voice_name", "TTS: Voice name", "", $ -> scarlet.ttsService != null && scarlet.ttsService.getInstalledVoices().contains($));
        this.ttsUseDefaultAudioDevice = scarlet.ui.settingBool("tts_use_default_audio_device", "TTS: Use default system audio device", false);
        this.announceWatchedGroups = scarlet.ui.settingBool("tts_announce_watched_groups", "TTS: Announce watched groups", true);
        this.announceNewPlayers = scarlet.ui.settingBool("tts_announce_new_players", "TTS: Announce new players", true);
        this.announcePlayersNewerThan = scarlet.ui.settingInt("tts_announce_players_newer_than_days", "TTS: Announce players newer than (days)", 30, 1, 365);
    }

    final Scarlet scarlet;

    String clientUserDisplayName,
           clientUserId,
           clientLocation,
           clientLocationPrev;
    final Map<String, String> clientLocation_userId2userDisplayName,
                              clientLocation_userDisplayName2userId,
                              clientLocation_userDisplayName2avatarDisplayName;
    Set<String> clientLocationPrev_userIds;
    boolean isInGroupInstance,
            isSameAsPreviousInstance;

    final ScarletUI.Setting<String> ttsVoiceName;
    final ScarletUI.Setting<Boolean> ttsUseDefaultAudioDevice,
                                     announceWatchedGroups,
                                     announceNewPlayers;
    final ScarletUI.Setting<Integer> announcePlayersNewerThan;

    void settingsLoaded()
    {
        this.scarlet.exec.scheduleAtFixedRate(() ->
        {
            String voiceName = this.ttsVoiceName.get();
            if (voiceName.trim().isEmpty())
            {
                this.scarlet.ttsService.getInstalledVoices().stream().findFirst().ifPresent(this.ttsVoiceName::set);
            }
            else
            {
                this.scarlet.ttsService.selectVoiceLater(voiceName);
            }
        }, 0_000L, 60_000L, TimeUnit.MILLISECONDS);
    }

    // ScarletVRChatLogs.Listener

    @Override
    public void log_init(File file)
    {
    }  

    @Override
    public void log_userAuthenticated(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId)
    {
        this.clientUserDisplayName = userDisplayName;
        this.clientUserId = userId;
    }

    @Override
    public void log_userQuit(boolean preamble, LocalDateTime timestamp, double lifetimeSeconds)
    {
    }

    @Override
    public void log_userJoined(boolean preamble, LocalDateTime timestamp, String location)
    {
        if (preamble)
        {
            this.scarlet.splash.splashText("Preamble...");
            this.scarlet.splash.splashSubtext(location);
        }
        this.clientLocation = location;
        this.isInGroupInstance = location.contains("~group("+this.scarlet.vrc.groupId+")");
        this.isSameAsPreviousInstance = Objects.equals(this.clientLocationPrev, location);
        if (!this.isSameAsPreviousInstance)
        {
            this.scarlet.ui.clearInstance();
        }
    }

    @Override
    public void log_userLeft(boolean preamble, LocalDateTime timestamp)
    {
        this.clientLocationPrev_userIds.clear();
        this.clientLocationPrev_userIds.addAll(this.clientLocation_userId2userDisplayName.keySet());
        this.clientLocationPrev = this.clientLocation;
        this.clientLocation = null;
        this.isInGroupInstance = false;
    }

    @Override
    public void log_playerJoined(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId)
    {
        boolean isRejoinFromPrev = this.clientLocationPrev_userIds.remove(userId) && this.isSameAsPreviousInstance;
        this.clientLocation_userId2userDisplayName.put(userId, userDisplayName);
        this.clientLocation_userDisplayName2userId.put(userDisplayName, userId);
        List<String> advisories = new ArrayList<>();
        int[] priority = new int[]{Integer.MIN_VALUE+1};
        Color text_color = this.checkPlayer(advisories, priority, preamble, userDisplayName, userId);
        String advisory = advisories == null || advisories.isEmpty() ? null : advisories.stream().collect(Collectors.joining("\n"));
        this.scarlet.ui.playerJoin(userId, userDisplayName, timestamp, advisory, text_color, priority[0], isRejoinFromPrev);
        if (Objects.equals(this.clientUserId, userId))
            this.clientLocationPrev_userIds.clear();
    }

    @Override
    public void log_playerLeft(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId)
    {
        this.clientLocation_userId2userDisplayName.remove(userId);
        this.clientLocation_userDisplayName2avatarDisplayName.remove(userDisplayName);
        this.scarlet.ui.playerLeave(userId, userDisplayName, timestamp);
    }

    @Override
    public void log_playerSwitchAvatar(boolean preamble, LocalDateTime timestamp, String userDisplayName, String avatarDisplayName)
    {
        this.clientLocation_userDisplayName2avatarDisplayName.put(userDisplayName, avatarDisplayName);
    }

    Color checkPlayer(List<String> advisories, int[] priority, boolean preamble, String userDisplayName, String userId)
    {
        ScarletWatchedGroups.WatchedGroup.Type overall_type = null;
        long minEpoch = preamble ? Long.MIN_VALUE : System.currentTimeMillis() - 86400_000L; // pull if not preamble and cache more than 1 day old
        User user = this.scarlet.vrc.getUser(userId, minEpoch);
        List<LimitedUserGroups> lugs = this.scarlet.vrc.getUserGroups(userId, minEpoch);
        // check groups
        if (lugs != null)
        {
            List<ScarletWatchedGroups.WatchedGroup> wgs = lugs.stream()
                .map(LimitedUserGroups::getGroupId)
                .map(this.scarlet.watchedGroups::getWatchedGroup)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            ScarletWatchedGroups.WatchedGroup wg = wgs.stream()
                .max(Comparator.naturalOrder())
                .orElse(null)
                ;
            if (wg != null && !wg.silent)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("User ").append(userDisplayName).append(" joined the lobby.");
                if (!preamble && this.announceWatchedGroups.get())
                {
                    if (wg.message != null)
                        sb.append(' ').append(wg.message);
                    this.scarlet.ttsService.setOutputToDefaultAudioDevice(this.ttsUseDefaultAudioDevice.get());
                    this.scarlet.ttsService.submit(sb.toString());
                }
                overall_type = wg.type;
                priority[0] = wg.priority;
            }
            wgs.forEach($ -> advisories.add($.message));
        }
        if (!preamble && user != null && this.announceNewPlayers.get())
        {
            long acctAgeDays = LocalDate.now().toEpochDay() - user.getDateJoined().toEpochDay();
            if (acctAgeDays <= this.announcePlayersNewerThan.get().longValue())
            {
                this.scarlet.ttsService.setOutputToDefaultAudioDevice(this.ttsUseDefaultAudioDevice.get());
                this.scarlet.ttsService.submit("User "+userDisplayName+" is new to VRChat, joined "+acctAgeDays+" days ago.");
            }
        }
        // check staff
        // check avatar
        if (overall_type != null)
            return overall_type.text_color;
        return null;
    }

    // TTSService.Listener

    @Override
    public void tts_init(TTSService tts)
    {
    }

    @Override
    public void tts_ready(int job, File file)
    {
        Scarlet.LOG.info("TTS Job "+job+": "+this.scarlet.discord.submitAudio(file));
        file.delete();
    }

}
