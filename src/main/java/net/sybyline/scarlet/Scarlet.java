package net.sybyline.scarlet;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.User;

import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.sybyline.scarlet.log.ScarletLogger;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.JsonAdapters;
import net.sybyline.scarlet.util.MavenDepsLoader;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.TTSService;

public class Scarlet implements Closeable
{

    static
    {
        String javaVersion = System.getProperty("java.specification.version");
        if (javaVersion == null)
            System.err.println("System property 'java.specification.version' is missing?!?!?!");
        else if (!"1.8".equals(javaVersion))
            System.err.println("This application was compiled to run on Java 8");
    }

    public static final String
        GROUP = "SybylineNetwork",
        NAME = "Scarlet",
        VERSION = "0.4.9-rc2",
        DEV_DISCORD = "Discord:@vinyarion/Vinyarion#0292/393412191547555841",
        USER_AGENT_NAME = "Sybyline-Network-"+NAME,
        USER_AGENT = USER_AGENT_NAME+"/"+VERSION+" "+DEV_DISCORD,
        USER_AGENT_STATIC = USER_AGENT_NAME+"-static/"+VERSION+" "+DEV_DISCORD,
        GITHUB_URL = "https://github.com/"+GROUP+"/"+NAME,
        LICENSE_URL = GITHUB_URL+"?tab=MIT-1-ov-file",
        META_URL = GITHUB_URL+"/blob/main/meta.json?raw=true",
        
        API_VERSION = "api/1",
        API_HOST_0 = "vrchat.com",
        API_URL_0  = "https://"+API_HOST_0+"/",
        API_BASE_0 = API_URL_0+API_VERSION,
        API_HOST_1 = "api.vrchat.com",
        API_URL_1  = "https://"+API_HOST_1+"/",
        API_BASE_1 = API_URL_1+API_VERSION,
        API_HOST_2 = "api.vrchat.cloud",
        API_URL_2  = "https://"+API_HOST_2+"/",
        API_BASE_2 = API_URL_2+API_VERSION;

    public static void main(String[] args) throws Exception
    {
        Thread.setDefaultUncaughtExceptionHandler(Scarlet::uncaughtException);
        try (Scarlet scarlet = new Scarlet())
        {
            scarlet.run();
        }
        System.exit(0);
    }

    public static final File dir;
    static
    {
        String scarletHome = System.getenv("SCARLET_HOME"),
               localappdata =  System.getenv("LOCALAPPDATA");
        scarletHome = System.getProperty("SCARLET_HOME", scarletHome);
        File dir0 = scarletHome != null
            ? ";".equals(scarletHome.trim()) && MavenDepsLoader.jarPath() != null
                ? MavenDepsLoader.jarPath().getParent().toFile()
                : new File(scarletHome).getAbsoluteFile()
            : localappdata != null
                ? new File(localappdata, GROUP+"/"+NAME)
                : new File(System.getProperty("user.home"), "AppData/Local/"+GROUP+"/"+NAME);
        if (!dir0.isDirectory())
            dir0.mkdirs();
        dir = dir0;
    }
    public static final Logger LOG = LoggerFactory.getLogger("Scarlet");

    static void uncaughtException(Thread t, Throwable e)
    {
        LOG.error("Uncaught exception in thread: "+t, e);
    }

    public static final Gson GSON, GSON_PRETTY;
    static
    {
        GsonBuilder gb = JsonAdapters.gson();
        GSON = gb.create();
        GSON_PRETTY = gb.setPrettyPrinting().create();
        LOG.info(String.format("App: %s %s", NAME, VERSION));
        LOG.info(String.format("OS: %s (%s)", System.getProperty("os.name"), System.getProperty("os.arch")));
        LOG.info(String.format("VM: %s %s (%s)", System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("java.vm.name")));
    }

    public Scarlet() throws IOException
    {
    }

    {
        // absolute initialization, even before explicit constructor body
    }

    public void stop()
    {
        if (!this.running)
            LOG.info("Queuing shutdown...");
        this.running = false;
    }

    @Override
    public void close() throws IOException
    {
        this.stop();
        this.exec.shutdown();
        this.execModal.shutdown();
        try
        {
            if (!this.exec.awaitTermination(3_000L, TimeUnit.MILLISECONDS))
            {
                int unstarted = this.exec.shutdownNow().size();
                LOG.error("Forcibly terminated executor service, "+unstarted+" unstarted task(s)");
            }
        }
        catch (InterruptedException iex)
        {
        }
        try
        {
            if (!this.execModal.awaitTermination(3_000L, TimeUnit.MILLISECONDS))
            {
                int unstarted = this.execModal.shutdownNow().size();
                LOG.error("Forcibly terminated modal executor service, "+unstarted+" unstarted task(s)");
            }
        }
        catch (InterruptedException iex)
        {
        }
        MiscUtils.close(this.ttsService);
        MiscUtils.close(this.discord);
        MiscUtils.close(this.logs);
        MiscUtils.close(this.ui);
        this.settings.updateRunVersionAndTime();
        LOG.info("Finished shutdown flow");
    }

    final ScarletUISplash splash = new ScarletUISplash(this);

    volatile boolean running = true;
    boolean staffMode = false;
    final AtomicInteger threadidx = new AtomicInteger();
    final ScheduledExecutorService exec = Executors.newScheduledThreadPool(4, runnable -> new Thread(runnable, "Scarlet Worker Thread "+this.threadidx.incrementAndGet()));
    final ScheduledExecutorService execModal = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "Scarlet Modal UI Thread "+this.threadidx.incrementAndGet()));
    
    final File dirVrc = new File(System.getProperty("user.home"), "AppData/LocalLow/VRChat/VRChat");
    
    final ScarletSettings settings = new ScarletSettings(new File(dir, "settings.json"));
    final ScarletUI ui = new ScarletUI(this);
    final ScarletEventListener eventListener = new ScarletEventListener(this);
    final ScarletPendingModActions pendingModActions = new ScarletPendingModActions(this, new File(dir, "pending_moderation_actions.json"));
    final ScarletModerationTags moderationTags = new ScarletModerationTags(new File(dir, "moderation_tags.json"));
    final ScarletWatchedGroups watchedGroups = new ScarletWatchedGroups(this, new File(dir, "watched_groups.json"));
    final ScarletStaffList staffList = new ScarletStaffList(this, new File(dir, "staff_list.json"));
    final ScarletData data = new ScarletData(new File(dir, "data"));
    final TTSService ttsService = new TTSService(new File(dir, "tts"), this.eventListener);
    final ScarletVRChat vrc = new ScarletVRChat(this, new File(dir, "store.bin"));
    final ScarletDiscord discord = new ScarletDiscordJDA(this, new File(dir, "discord_bot.json"));
    final ScarletVRChatLogs logs = new ScarletVRChatLogs(this, this.eventListener);
    String[] last25logs = new String[0];
    final ScarletUI.Setting<Boolean> alertForUpdates = this.ui.settingBool("ui_alert_update", "Notify for updates", true),
                                     alertForPreviewUpdates = this.ui.settingBool("ui_alert_update_preview", "Notify for preview updates", true),
                                     showUiDuringLoad = this.ui.settingBool("ui_show_during_load", "Show UI during load", false);


    public void run()
    {
        this.ui.loadSettings();
        this.eventListener.settingsLoaded();
        this.splash.splashSubtext("Logging in to VRChat Api");
        try
        {
            this.vrc.login();
        }
        catch (Exception ex)
        {
            LOG.error("Failed to authenticate with VRChat", ex);
            return;
        }
        this.splash.splashSubtext("Checking for updates");
        this.checkUpdate();
        this.logs.start();
        try
        {
            long filecheck = 3;
            for (long now, lastIter = 0L; this.running; lastIter = now)
            {
                // spin
                while ((now = System.currentTimeMillis()) - lastIter < 60_000L && this.running)
                    this.spin();
                // maybe refresh
                try
                {
                    this.maybeRefresh();
                }
                catch (Exception ex)
                {
                    this.running = false;
                    LOG.error("Exception maybe refreshing", ex);
                    return;
                }
                // query & emit
                try
                {
                    this.queryEmit();
                }
                catch (Exception ex)
                {
                    this.running = false;
                    LOG.error("Exception emitting query", ex);
                    return;
                }
                // log names
                if (filecheck --> 0L) try
                {
                    File logs = new File(dir, "logs");
                    String[] names = logs.list(($, name) -> ScarletLogger.lfpattern.matcher(name).find());
                    names = Arrays
                        .stream(names)
                        .sorted(Comparator.reverseOrder())
                        .limit(OptionData.MAX_CHOICES)
                        .toArray(String[]::new);
                    this.last25logs = names;
                }
                catch (Exception ex)
                {
                    this.running = false;
                    LOG.error("Exception enumerating log files", ex);
                    return;
                }
                // maybe check update
                try
                {
                    this.maybeCheckUpdate();
                }
                catch (Exception ex)
                {
                    LOG.error("Exception maybe checking for update", ex);
                }
            }
        }
        finally
        {
            ;
        }
    }

    void spin()
    {
        MiscUtils.sleep(100L);
        try
        {
            while (System.in.available() > 0)
            {
                @SuppressWarnings("resource")
                Scanner s = new Scanner(System.in);
                String line = s.nextLine().trim();
                if (line.isEmpty())
                    continue;
                Scanner ls = new Scanner(new StringReader(line));
                {
                    String op = ls.next();
                    switch (op)
                    {
                    case "logout": {
                        LOG.info("Logout success: "+this.vrc.logout());
                    } // fallthrough
                    case "exit":
                    case "halt":
                    case "quit":
                    case "stop": {
                        this.running = false;
                        LOG.info("Stopping");
                    } break;
                    case "explore": {
                        MiscUtils.AWTDesktop.browse(dir.toURI());
                    } break;
                    case "tts": {
                        String text = ls.nextLine().trim();
                        if (!text.isEmpty())
                        {
                            this.ttsService.setOutputToDefaultAudioDevice(this.eventListener.ttsUseDefaultAudioDevice.get());
                            LOG.info("Submitting TTS: `"+text+"`, success: "+this.ttsService.submit(text));
                        }
                    } break;
                    case "link": {
                        String userId = ls.next();
                        String userSnowflake = ls.next();
                        
                        User user = this.vrc.getUser(userId);
                        if (user == null)
                        {
                            LOG.warn("Unknown VRChat user: "+userId);
                        }
                        else
                        {
                            this.data.linkIdToSnowflake(userId, userSnowflake);
                            LOG.info("Linking VRChat user "+user.getDisplayName()+" ("+userId+") to Discord user <@"+userSnowflake+">");
                        }
                    } break;
                    case "importgroups": {
                        String from = ls.nextLine().trim();
                        boolean isUrl = from.startsWith("http://") || from.startsWith("https://");
                        
                        LOG.info("Importing watched groups legacy CSV from "+(isUrl ? "URL: " : "file: ")+from);
                        try (Reader reader = isUrl ? new InputStreamReader(HttpURLInputStream.get(from), StandardCharsets.UTF_8) : MiscUtils.reader(new File(from)))
                        {
                            if (this.watchedGroups.importLegacyCSV(reader, true))
                            {
                                LOG.info("Successfully imported watched groups legacy CSV");
                            }
                            else
                            {
                                LOG.warn("Failed to import watched groups legacy CSV with unknown reason");
                            }
                        }
                        catch (Exception ex)
                        {
                            LOG.error("Exception importing watched groups legacy CSV from "+(isUrl ? "URL: " : "file: ")+from, ex);
                        }
                    } break;
                    case "importgroupsjson": {
                        String from = ls.nextLine().trim();
                        boolean isUrl = from.startsWith("http://") || from.startsWith("https://");
                        
                        LOG.info("Importing watched groups JSON from "+(isUrl ? "URL: " : "file: ")+from);
                        try (Reader reader = isUrl ? new InputStreamReader(HttpURLInputStream.get(from), StandardCharsets.UTF_8) : MiscUtils.reader(new File(from)))
                        {
                            if (this.watchedGroups.importJson(reader, true))
                            {
                                LOG.info("Successfully imported watched groups JSON");
                            }
                            else
                            {
                                LOG.warn("Failed to import watched groups JSON with unknown reason");
                            }
                        }
                        catch (Exception ex)
                        {
                            LOG.error("Exception importing watched groups JSON from "+(isUrl ? "URL: " : "file: ")+from, ex);
                        }
                    } break;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            LOG.error("Exception in spin", ex);
        }
    }

    void maybeCheckUpdate()
    {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(this.settings.getLastUpdateCheck().plusHours(3)))
        {
            this.settings.setLastUpdateCheck(now);
            this.checkUpdate();
        }
    }

    String newerVersion = null;
    void checkUpdate()
    {
        try
        {
            ScarletMeta meta;
            try (HttpURLInputStream in = HttpURLInputStream.get(META_URL))
            {
                meta = GSON_PRETTY.fromJson(new InputStreamReader(in), ScarletMeta.class);
            }
            String cmp_version = this.alertForPreviewUpdates.get() || MiscUtils.isPreviewVersion(VERSION) ? meta.latest_build : meta.latest_release;
            if (!Objects.equals(this.newerVersion, cmp_version) && MiscUtils.compareSemVer(VERSION, cmp_version) < 0)
            {
                LOG.info(NAME+" version "+cmp_version+" available");
                if (this.alertForUpdates.get())
                {
                    this.execModal.execute(() -> JOptionPane.showMessageDialog(null, NAME+" version "+cmp_version+" available", "Update available", JOptionPane.INFORMATION_MESSAGE));
                }
                this.newerVersion = cmp_version;
            }
        }
        catch (Exception ex)
        {
            LOG.error("Failed to download meta", ex);
        }
    }

    void maybeRefresh()
    {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (this.wantsVrcRefresh || now.isAfter(this.settings.getLastAuthRefresh().plusHours(72)))
        {
            this.settings.setLastAuthRefresh(now);
            this.vrc.refresh();
            this.wantsVrcRefresh = false;
        }
    }

    static final int CATCH_UP_INSTANTANEOUS = 0,
                     CATCH_UP_SKIP_UNTIL_24 = 1,
                     CATCH_UP_LIMIT_NEXT_24 = 2;
    int catchupMode = CATCH_UP_LIMIT_NEXT_24;
    boolean wantsVrcRefresh = false;
    public void queueVrcRefresh()
    {
        this.wantsVrcRefresh = true;
    }
    public boolean checkVrcRefresh(Exception ex)
    {
        if (!ex.getMessage().contains("HTTP response code: 401"))
            return false;
        this.queueVrcRefresh();
        return true;
    }
    void queryEmit()
    {
        OffsetDateTime from = this.settings.getLastAuditQuery(),
                       to = OffsetDateTime.now(ZoneOffset.UTC);
        switch (this.catchupMode)
        {
        default:
        case CATCH_UP_INSTANTANEOUS: {
            // noop
        } break;
        case CATCH_UP_SKIP_UNTIL_24: {
            OffsetDateTime earliest = to.minusHours(24);
            if (from.isBefore(earliest))
            {
                LOG.info("Catching up: Skipping from "+from+" to "+earliest+" ("+Duration.between(from, earliest)+" total)");
                from = earliest;
            }
        } break;
        case CATCH_UP_LIMIT_NEXT_24: {
            OffsetDateTime latest = from.plusHours(24);
            if (latest.isBefore(to))
            {
                LOG.info("Catching up: Only querying a 24-hour period");
                to = latest;
            }
        } break;
        }
        LOG.debug("Querying from "+from+" to "+to);
        List<GroupAuditLogEntry> entries = this.vrc.auditQuery(from, to);
        
        if (entries == null)
        {
            LOG.warn("Failed to get entries from "+from+" to "+to);
            return;
        }
        
        for (GroupAuditLogEntry entry : entries) try
        {
            this.discord.process(this, entry);
        }
        catch (Exception ex)
        {
            LOG.error("Exception processing audit entry "+entry.getId()+" of type "+entry.getEventType()+": `"+entry.toJson()+"`", ex);
        }
        
        this.settings.setLastAuditQuery(to);
    }

}
