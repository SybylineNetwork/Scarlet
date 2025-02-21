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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.Tail;

public class ScarletVRChatLogs implements Closeable
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/VRChat/Logs");

    public ScarletVRChatLogs(Scarlet scarlet, Listener listener)
    {
        this.scarlet = scarlet;
        this.listener = listener;
        
        this.tailThread = new Thread(this::thread);
        this.running = true;
        this.currentTarget = null;
        this.currentTail = null;
    }

    public interface Listener
    {
        
        void log_init(File file);
        
        void log_userAuthenticated(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId);
        void log_userQuit(boolean preamble, LocalDateTime timestamp, double lifetimeSeconds);
        
        void log_userJoined(boolean preamble, LocalDateTime timestamp, String location);
        void log_userLeft(boolean preamble, LocalDateTime timestamp);
        
        void log_playerJoined(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId);
        void log_playerLeft(boolean preamble, LocalDateTime timestamp, String userDisplayName, String userId);
        void log_playerSwitchAvatar(boolean preamble, LocalDateTime timestamp, String userDisplayName, String avatarDisplayName);
        
//        void log_explicitTokenRequest(boolean preamble, LocalDateTime timestamp, String fileId, int fileVersion, String fileVariant);
        
    }

    final Scarlet scarlet;
    final Listener listener;

    void handleEntry(File file, boolean preamble, LocalDateTime timestamp, String level, String text, List<String> lines)
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
//        else if (text.startsWith("[ATM] "))
//        {
//            if (text.startsWith("Explicit token request for '", 6))
//            {
//                int colon0 = text.indexOf(':', 34),
//                    colon1 = text.indexOf(':', colon0 + 1),
//                    endquote = text.indexOf('\'', colon1);
//                String fileId = text.substring(34, colon0);
//                int fileVersion = MiscUtils.parseIntElse(text.substring(colon0 + 1, colon1), 1);
//                String fileVariant = text.substring(colon1 + 1, endquote);
//                this.listener.log_explicitTokenRequest(preamble, timestamp, fileId, fileVersion, fileVariant);
//            }
//        }
        else if (text.startsWith("VRCApplication: HandleApplicationQuit at "))
        {
            double lifetimeSeconds = MiscUtils.parseDoubleElse(text.substring(41), Double.NaN);
            this.listener.log_userQuit(preamble, timestamp, lifetimeSeconds);
        }
    }

    final Thread tailThread;
    volatile boolean running;
    File currentTarget;
    VRChatLogTail currentTail;

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

    void thread()
    {
        if (Thread.currentThread() != this.tailThread)
            throw new IllegalStateException();
        this.running = true;
        this.currentTarget = null;
        this.currentTail = null;
        try
        {
            while (!this.pollTarget())
            {
                if (!MiscUtils.sleep(10_000L))
                    return;
                if (!this.running)
                    return;
                LOG.info("Waiting for log file to appear...");
            }
            while (this.running && !this.tailThread.isInterrupted())
            {
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
        finally
        {
            this.running = false;
            this.currentTarget = null;
            this.currentTail = null;
        }
    }

    boolean pollTarget()
    {
        File nextTarget = this.locateTarget();
        if (Objects.equals(this.currentTarget, nextTarget))
            return false;
        this.currentTarget = nextTarget;
        return true;
    }

    static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    File locateTarget()
    {
        return Arrays
            .stream(this.scarlet.dirVrc.listFiles())
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
            this.isInPreamble &= inPreamble;
        }
        @Override
        protected void on_eof()
        {
            this.handleCurrentEntry();
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
