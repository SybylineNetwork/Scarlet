package net.sybyline.scarlet;

import java.io.Closeable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.ext.VrcAppData;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.Tail;

public class ScarletVRChatLogs implements Closeable
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/VRChat/Logs");

    public ScarletVRChatLogs(Listener listener)
    {
        this.listener = listener;
        
        this.tailThread = new Thread(this::thread);
        this.running = true;
        this.currentTargetIndex = -1;
        this.currentTarget = null;
        this.currentTail = null;
    }

    public interface Listener
    {
        
        void log_init(File file);
        void log_catchUp(File file);
        
        void log_userAuthenticated(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId);
        void log_userQuit(boolean preamble, LocalDateTime timestamp, double lifetimeSeconds);
        
        void log_userJoined(boolean preamble, LocalDateTime timestamp, String location);
        void log_userLeft(boolean preamble, LocalDateTime timestamp);
        
        void log_playerJoined(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId);
        void log_playerLeft(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId);
        void log_playerSwitchAvatar(boolean preamble, LocalDateTime timestamp, String userDisplayName, String avatarDisplayName);
        
        void log_vtkInit(boolean preamble, LocalDateTime timestamp, String targetDisplayName, String nullable_actorDisplayName);
        
        void log_playerSpawnPedestal(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId, String contentType, String contentId);
        void log_playerSpawnSticker(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId, String stickerId);

        void log_apiRequest(boolean preamble, LocalDateTime timestamp, int index, String method, String url);
        
    }

    private final Listener listener;

    private void handleEntry(File file, boolean preamble, LocalDateTime timestamp, String level, String text, List<String> lines)
    {
        int cidx;
        if (text.startsWith("[Behaviour] "))
        {
            if (text.startsWith("[User Authenticated: "))
            {
                int nameStart = 21,
                    nameEnd = text.lastIndexOf(" ("),
                    idStart = nameEnd + 2,
                    idEnd = text.lastIndexOf(')');
                String name = text.substring(nameStart, nameEnd),
                       id = text.substring(idStart, idEnd);
                this.listener.log_userAuthenticated(preamble, timestamp, name, id);
            }
            else if (text.startsWith("Joining ", 12) && !text.startsWith("or Creating Room: ", 20))
            {
                String location = text.substring(20);
                this.listener.log_userJoined(preamble, timestamp, location);
            }
            else if (text.startsWith("OnLeftRoom", 12))
            {
                this.listener.log_userLeft(preamble, timestamp);
            }
            else if (text.startsWith("OnPlayerJoined ", 12))
            {
                int oparen = text.lastIndexOf(" ("),
                    cparen = text.lastIndexOf(')');
                String displayName = text.substring(27, oparen),
                       userId = text.substring(oparen + 2, cparen);
                this.listener.log_playerJoined(preamble, timestamp, displayName, userId);
            }
            else if (text.startsWith("OnPlayerLeft ", 12))
            {
                int oparen = text.lastIndexOf(" ("),
                    cparen = text.lastIndexOf(')');
                String displayName = text.substring(25, oparen),
                       userId = text.substring(oparen + 2, cparen);
                this.listener.log_playerLeft(preamble, timestamp, displayName, userId);
            }
            else if (text.startsWith("Switching ", 12) && (cidx = text.lastIndexOf(" to avatar ")) != -1)
            {
                String displayName = text.substring(22, cidx),
                       avatarDisplayName = text.substring(cidx + 11, text.length());
                this.listener.log_playerSwitchAvatar(preamble, timestamp, displayName, avatarDisplayName);
            }
        }
        else if (text.startsWith("[API] [") && (cidx = text.indexOf("] Sending ")) != -1)
        {
            int sep1 = text.indexOf(" request to ", cidx + 10),
                idx = MiscUtils.parseIntElse(text.substring(7, cidx), -1);
            String method = text.substring(cidx + 10, sep1).intern(), // intern because is constantly one of a few short strings
                   url = text.substring(sep1 + 12);
            this.listener.log_apiRequest(preamble, timestamp, idx, method, url);
        }
        else if (text.startsWith("[ModerationManager] "))
        {
            if (text.startsWith("A vote kick has been initiated against ", 20) && text.endsWith(", do you agree?"))
            {
                String displayName = text.substring(59, text.length() - 15);
                String targetDisplayName, nullable_actorDisplayName;
                int by = displayName.indexOf(" by ");
                if (by == -1)
                {
                    targetDisplayName = displayName;
                    nullable_actorDisplayName = null;
                }
                else
                {
                    targetDisplayName = displayName.substring(0, by);
                    nullable_actorDisplayName = displayName.substring(by + 4, displayName.length());
                }
                this.listener.log_vtkInit(preamble, timestamp, targetDisplayName, nullable_actorDisplayName);
            }
        }
        else if (text.startsWith("[SharingManager] User ") && (cidx = text.lastIndexOf(") created sharing pedestal for ")) != -1)
        {
            int oidx = text.indexOf(" ("),
                sidx = text.lastIndexOf(" ");
            String userId = text.substring(22, oidx),
                   userDisplayName = text.substring(oidx + 2, cidx),
                   contentType = text.substring(cidx + 31, sidx),
                   contentId = text.substring(sidx + 1);
            this.listener.log_playerSpawnPedestal(preamble, timestamp, userDisplayName, userId, contentType, contentId);
        }
        else if (text.startsWith("[StickersManager] User ") && (cidx = text.lastIndexOf(") spawned sticker ")) != -1)
        {
            int oidx = text.indexOf(" (");
            String userId = text.substring(23, oidx),
                   userDisplayName = text.substring(oidx + 2, cidx),
                   stickerId = text.substring(cidx + 18);
            this.listener.log_playerSpawnSticker(preamble, timestamp, userDisplayName, userId, stickerId);
        }
        else if (text.startsWith("VRCApplication: HandleApplicationQuit at "))
        {
            double lifetimeSeconds = MiscUtils.parseDoubleElse(text.substring(41), Double.NaN);
            this.listener.log_userQuit(preamble, timestamp, lifetimeSeconds);
        }
    }

    private final Thread tailThread;
    private volatile boolean running;
    private volatile int currentTargetIndex;
    private File currentTarget;
    private VRChatLogTail currentTail;

    public int currentTargetIndex()
    {
        return this.currentTargetIndex;
    }

    public File currentTarget()
    {
        return this.currentTarget;
    }

    public void start()
    {
        synchronized (this.tailThread)
        {
            if (this.running && !this.tailThread.isAlive())
            {
                this.tailThread.setName("Scarlet VRChat Log Tailer");
                this.tailThread.setDaemon(true);
                this.tailThread.start();
            }
        }
    }

    @Override
    public void close()
    {
        this.running = false;
        if (this.tailThread.isAlive())
        {
            VRChatLogTail currentTail = this.currentTail;
            if (currentTail != null)
                currentTail.stop();
            this.tailThread.interrupt();
            while (this.tailThread.isAlive()) try
            {
                LOG.info("Waiting for tailer thread...");
                this.tailThread.join(1_000L);
            }
            catch (InterruptedException iex)
            {
                break;
            }
        }
    }

    private void thread()
    {
        if (Thread.currentThread() != this.tailThread)
            throw new IllegalStateException();
        this.running = true;
        this.currentTarget = null;
        this.currentTail = null;
        try
        {
            if (!VrcAppData.DIR.isDirectory())
            {
                this.catchUp(null);
                LOG.warn("The VRChat Client does not seem to be installed on this machine, disabling log tailer-parser!");
                return;
            }
            while (!this.pollTarget())
            {
                this.catchUp(null);
                if (!MiscUtils.sleep(10_000L))
                    return;
                if (!this.running)
                    return;
                LOG.info("Waiting for log file to appear...");
            }
            while (this.running && !this.tailThread.isInterrupted())
            {
                this.currentTargetIndex++;
                LOG.info("Tailing " + this.currentTarget.getName());
                this.currentTail = new VRChatLogTail(this.currentTarget);
                try
                {
                    this.currentTail.run();
                }
                catch (Exception ex)
                {
                    LOG.error("Exception whilst tailing " + this.currentTarget.getName(), ex);
                }
            }
        }
        catch (Exception ex)
        {
            LOG.error("Exception in tailer thread", ex);
        }
        finally
        {
            this.running = false;
            this.currentTarget = null;
            this.currentTail = null;
        }
    }

    private void catchUp(File file)
    {
        if (file != null)
            LOG.info("Caught up with " + file.getName());
        this.listener.log_catchUp(file);
    }

    private boolean pollTarget()
    {
        File nextTarget = this.locateTarget();
        if (Objects.equals(this.currentTarget, nextTarget))
            return false;
        this.currentTarget = nextTarget;
        return true;
    }

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    File locateTarget()
    {
        return Optional
            .ofNullable(VrcAppData.DIR.listFiles())
            .map(Arrays::stream)
            .orElseGet(Stream::empty)
            .filter(file -> file.isFile() && file.getName().startsWith("output_log_") && file.getName().endsWith(".txt"))
            .max(Comparator.comparing($ -> LocalDateTime.parse($.getName().substring(11, 30), dtf)))
            .orElse(null);
    }

    static final Pattern entry = Pattern.compile("\\A(?<ldt>\\d{4}\\.\\d{2}\\.\\d{2}\\s\\d{2}:\\d{2}:\\d{2})\\s(?<lvl>\\w+)\\s+-\\s\\s(?<txt>.+)\\z");
    static final DateTimeFormatter entry_dtf = DateTimeFormatter.ofPattern("yyyy'.'MM'.'dd HH':'mm':'ss");
    class VRChatLogTail extends Tail
    {
        VRChatLogTail(File target)
        {
            super(target, StandardCharsets.UTF_8, 150L, false, false, 8192);
            this.buffer = new ArrayList<>();
            this.lastPolled = System.currentTimeMillis();
            this.isInPreamble = true;
        }
        final List<String> buffer;
        long lastPolled;
        boolean isInPreamble;
        LocalDateTime entryLdt;
        String entryLvl, entryTxt;
        @Override
        protected void on_line(boolean inPreamble, String line)
        {
            Matcher matcher = entry.matcher(line);
            if (matcher.find())
            {
                this.handleCurrentEntry();
                this.entryLdt = LocalDateTime.parse(matcher.group("ldt"), entry_dtf);
                this.entryLvl = matcher.group("lvl");
                this.entryTxt = matcher.group("txt");
            }
            this.buffer.add(line);
            if (this.isInPreamble && !inPreamble)
                ScarletVRChatLogs.this.catchUp(this.file);
            this.isInPreamble &= inPreamble;
        }
        @Override
        protected void on_eof()
        {
            this.handleCurrentEntry();
            if (this.isInPreamble)
                ScarletVRChatLogs.this.catchUp(this.file);
            this.isInPreamble = false;
        }
        @Override
        protected void on_loop()
        {
            long now = System.currentTimeMillis(),
                 diff = now - this.lastPolled;
            if (diff < 10_000L)
                return;
            this.lastPolled = now;
            if (ScarletVRChatLogs.this.pollTarget())
                this.run = false;
        }
        @Override
        protected boolean isRunning()
        {
            return super.isRunning() && ScarletVRChatLogs.this.running;
//            return super.isRunning() && ScarletVRChatLogs.this.running && !Thread.currentThread().isInterrupted();
        }
        private void handleCurrentEntry()
        {
            if (!this.buffer.isEmpty()) try
            {
                ScarletVRChatLogs.this.handleEntry(this.file, this.isInPreamble, this.entryLdt, this.entryLvl, this.entryTxt, this.buffer);
            }
            catch (Exception ex)
            {
                LOG.error("Error handling log line: " + this.entryTxt, ex);
            }
            finally
            {
                this.buffer.clear();
            }
        }
    }

}
