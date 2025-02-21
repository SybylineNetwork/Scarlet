package net.sybyline.scarlet;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.github.vrchatapi.model.LimitedUserGroups;

import net.sybyline.scarlet.util.TTSService;

public class ScarletEventListener implements ScarletVRChatLogs.Listener, TTSService.Listener
{

    public ScarletEventListener(Scarlet scarlet)
    {
        this.scarlet = scarlet;
        
        this.clientUserDisplayName = null;
        this.clientUserId = null;
        this.clientLocation = null;
        this.clientLocation_userId2userDisplayName = new ConcurrentHashMap<>();
        this.clientLocation_userDisplayName2userId = new ConcurrentHashMap<>();
        this.clientLocation_userDisplayName2avatarDisplayName = new ConcurrentHashMap<>();
        
        this.isInGroupInstance = false;
    }

    final Scarlet scarlet;

    String clientUserDisplayName,
           clientUserId,
           clientLocation;
    final Map<String, String> clientLocation_userId2userDisplayName,
                              clientLocation_userDisplayName2userId,
                              clientLocation_userDisplayName2avatarDisplayName;

    boolean isInGroupInstance;

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
        this.clientLocation = location;
        this.isInGroupInstance = location.contains("~group("+this.scarlet.vrc.groupId+")");
    }

    @Override
    public void log_userLeft(boolean preamble, LocalDateTime timestamp)
    {
        this.clientLocation = null;
        this.isInGroupInstance = false;
    }

    @Override
    public void log_playerJoined(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId)
    {
        this.clientLocation_userId2userDisplayName.put(userId, userDisplayName);
        this.clientLocation_userDisplayName2userId.put(userDisplayName, userId);
        if (this.isInGroupInstance)
        if (!preamble)
        {
            this.checkPlayer(userDisplayName, userId);
        }
    }

    @Override
    public void log_playerLeft(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId)
    {
        this.clientLocation_userId2userDisplayName.remove(userId);
        this.clientLocation_userDisplayName2avatarDisplayName.remove(userId);
    }

    @Override
    public void log_playerSwitchAvatar(boolean preamble, LocalDateTime timestamp, String userDisplayName, String avatarDisplayName)
    {
        this.clientLocation_userDisplayName2avatarDisplayName.put(userDisplayName, avatarDisplayName);
    }

    void checkPlayer(String userDisplayName, String userId)
    {
        // check groups
        List<LimitedUserGroups> lugs = this.scarlet.vrc.getUserGroups(userId);
        if (lugs != null)
        {
            List<ScarletWatchedGroups.WatchedGroup> wgs = lugs.stream()
                .map(LimitedUserGroups::getId)
                .map(this.scarlet.watchedGroups::getWatchedGroup)
                .collect(Collectors.toList());
            if (!wgs.isEmpty())
            {
                StringBuilder sb = new StringBuilder();
                sb.append("User ").append(userDisplayName).append(" joined the lobby.");
                wgs.forEach(wg -> sb.append(' ').append(wg.message));
                this.scarlet.ttsService.submit(sb.toString());
            }
        }
        // check staff
        // check avatar
    }

    // TTSService.Listener

    @Override
    public void tts_ready(int job, File file)
    {
        Scarlet.LOG.info("TTS Job "+job+": "+this.scarlet.discord.submitAudio(file));
        file.delete();
    }

}
