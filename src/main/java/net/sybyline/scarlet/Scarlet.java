package net.sybyline.scarlet;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
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
import io.github.vrchatapi.model.GroupInstance;
import io.github.vrchatapi.model.GroupPermissions;
import io.github.vrchatapi.model.User;

import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.sybyline.scarlet.log.ScarletLogger;
import net.sybyline.scarlet.ui.Swing;
import net.sybyline.scarlet.util.GithubApi;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.JsonAdapters;
import net.sybyline.scarlet.util.MavenDepsLoader;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.ProcLock;
import net.sybyline.scarlet.util.TTSService;
import net.sybyline.scarlet.util.VrcIds;

public class Scarlet implements Closeable
{

    public static final int JAVA_SPEC;

    static
    {
        String javaVersion = System.getProperty("java.specification.version");
        if (javaVersion == null)
            System.err.println("System property 'java.specification.version' is missing?!?!?!");
        else if (!"1.8".equals(javaVersion))
            System.err.println("This application was compiled to run on Java 8");
        JAVA_SPEC = javaVersion == null ? 0 : Integer.parseInt(javaVersion.startsWith("1.") ? javaVersion.substring(2) : javaVersion);
    }

    public static final String
        GROUP = "SybylineNetwork",
        NAME = "Scarlet",
        VERSION = "0.4.12-rc1",
        DEV_DISCORD = "Discord:@vinyarion/Vinyarion#0292/393412191547555841",
        SCARLET_DISCORD_URL = "https://discord.gg/CP3AyhypBF",
        GITHUB_URL = "https://github.com/"+GROUP+"/"+NAME,
        USER_AGENT_NAME = "Sybyline-Network-"+NAME,
        USER_AGENT = USER_AGENT_NAME+"/"+VERSION+" "+DEV_DISCORD+"; "+SCARLET_DISCORD_URL+"; "+GITHUB_URL,
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
        int exitCode = 0;
        try
        {
            try (Scarlet scarlet = new Scarlet())
            {
                scarlet.run();
                exitCode = scarlet.exitCode;
            }
            catch (Throwable t)
            {
                exitCode = -1;
                LOG.error("Exception in main", t);
            }
        }
        finally
        {
            System.exit(exitCode);
        }
    }

    public static final File user_home = new File(System.getProperty("user.home"));
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
                : new File(user_home, "AppData/Local/"+GROUP+"/"+NAME);
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

    public boolean stop()
    {
        return this.stop("shutdown", 0);
    }
    public boolean restart()
    {
        try (FileWriter fw = new FileWriter("scarlet.version"))
        {
            fw.append(VERSION).flush();
        }
        catch (IOException ioex)
        {
            LOG.error("Exception writing to scarlet.version for version `"+VERSION+"`", ioex);
        }
        return this.stop("restart", 69);
    }
    public int update(String targetVersion)
    {
        if (!MiscUtils.isValidVersion(targetVersion))
            return 1;
        if (Arrays.stream(this.allVersions).noneMatch(targetVersion::equals))
            return 2;
        try (FileWriter fw = new FileWriter("scarlet.version.target"))
        {
            fw.append(targetVersion).flush();
        }
        catch (IOException ioex)
        {
            LOG.error("Exception writing to scarlet.version.target for target verstion `"+targetVersion+"`", ioex);
            return 3;
        }
        return this.stop("update", 70) ? 0 : -1;
    }
    public synchronized boolean stop(String kind, int exitCode)
    {
        if (!this.running)
            return false;
        LOG.info("Queuing "+kind+"...");
        this.running = false;
        this.exitCode = exitCode;
        return true;
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
        this.data.saveAll();
        this.settings.updateRunVersionAndTime();
        LOG.info("Finished shutdown flow");
    }

    final ScarletUISplash splash = new ScarletUISplash(this);

    volatile boolean running = true;
    volatile int exitCode = 0;
    boolean staffMode = false;
    final Runnable explicitGC = MiscUtils.withMinimumInterval(3600_000L, System::gc);
    final AtomicInteger threadidx = new AtomicInteger();
    final ScheduledExecutorService exec = Executors.newScheduledThreadPool(4, runnable -> new Thread(runnable, "Scarlet Worker Thread "+this.threadidx.incrementAndGet()));
    final ScheduledExecutorService execModal = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "Scarlet Modal UI Thread "+this.threadidx.incrementAndGet()));
    
    final ScarletSettings settings = new ScarletSettings(new File(dir, "settings.json"));
    {
        Float uiScale = this.settings.getObject("ui_scale", Float.class);
        if (uiScale != null) Swing.scaleAll(uiScale.floatValue());
        String groupId = this.settings.getString("vrchat_group_id");
        if (groupId != null && !(groupId = VrcIds.resolveGroupId(groupId)).isEmpty())
        {
            if (!ProcLock.tryLock(new File(user_home, ".scarlet."+groupId+".lock")))
                throw new IllegalStateException("Duplicate processes detected for group "+groupId);
        }
    }
    final ScarletUI ui = new ScarletUI(this);
    final ScarletEventListener eventListener = new ScarletEventListener(this);
    final ScarletPendingModActions pendingModActions = new ScarletPendingModActions(new File(dir, "pending_moderation_actions.json"));
    final ScarletModerationTags moderationTags = new ScarletModerationTags(new File(dir, "moderation_tags.json"));
    final ScarletWatchedGroups watchedGroups = new ScarletWatchedGroups(new File(dir, "watched_groups.json"));
    final ScarletStaffList staffList = new ScarletStaffList(new File(dir, "staff_list.json"));
    final ScarletSecretStaffList secretStaffList = new ScarletSecretStaffList(new File(dir, "secret_staff_list.json"));
    final ScarletVRChatReportTemplate vrcReport = new ScarletVRChatReportTemplate(new File(dir, "report_template.txt"));
    final ScarletData data = new ScarletData(new File(dir, "data"));
    final TTSService ttsService = new TTSService(new File(dir, "tts"), this.eventListener);
    final ScarletVRChat vrc = new ScarletVRChat(this, new File(dir, "store.bin"));
    final ScarletDiscord discord = new ScarletDiscordJDA(this, new File(dir, "discord_bot.json"), new File(dir, "discord_perms.json"));
    final ScarletVRChatLogs logs = new ScarletVRChatLogs(this.eventListener);
    String[] last25logs = new String[0];
    final ScarletUI.Setting<Boolean> alertForUpdates = this.ui.settingBool("ui_alert_update", "Notify for updates", true),
                                     alertForPreviewUpdates = this.ui.settingBool("ui_alert_update_preview", "Notify for preview updates", true),
                                     showUiDuringLoad = this.ui.settingBool("ui_show_during_load", "Show UI during load", false);
    final ScarletUI.Setting<Integer> auditPollingInterval = this.ui.settingInt("audit_polling_interval", "Audit polling interval seconds (10-300 inclusive)", 60, 10, 300);
    final ScarletUI.Setting<Void> uiScale = this.ui.settingVoid("UI scale", "Set", this.ui::setUIScale);

    public void run()
    {
        this.ui.loadSettings();
        this.eventListener.settingsLoaded();
        this.splash.splashSubtext("Logging in to VRChat Api");
        try
        {
            this.vrc.login();
            this.staffList.populateStaffNames(this.vrc);
            this.secretStaffList.populateSecretStaffNames(this.vrc);
        }
        catch (Exception ex)
        {
            LOG.error("Failed to authenticate with VRChat", ex);
            return;
        }
        if (!this.vrc.checkSelfUserHasVRChatPermission(GroupPermissions.group_audit_view))
        {
            this.vrc.modalNeedPerms(GroupPermissions.group_audit_view);
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
                long currentPollInterval = Math.min(Math.max(this.auditPollingInterval.get().longValue(), 10L), 300L) * 1_000L;
                while ((now = System.currentTimeMillis()) - lastIter < currentPollInterval && this.running)
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
                if (!this.staffMode) try
                {
                    this.queryEmit(currentPollInterval);
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
                // maybe check instances
                if (!this.staffMode) try
                {
                    this.maybeCheckInstances();
                }
                catch (Exception ex)
                {
                    LOG.error("Exception maybe checking instances", ex);
                }
                // maybe mod summary
                if (!this.staffMode) try
                {
                    this.maybeModSummary();
                }
                catch (Exception ex)
                {
                    LOG.error("Exception maybe mod summary", ex);
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
                // maybe save data
                try
                {
                    this.maybeSaveData();
                }
                catch (Exception ex)
                {
                    LOG.error("Exception maybe saving data", ex);
                }
                this.explicitGC.run();
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
                            LOG.info("Submitting TTS: `"+text+"`, success: "+this.ttsService.submit("cli-"+Long.toUnsignedString(System.nanoTime()), text));
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
        if (now.isAfter(this.settings.lastUpdateCheck.getOrSupply().plusHours(3)))
        {
            this.settings.lastUpdateCheck.set(now);
            this.checkUpdate();
        }
    }

    String newerVersion = null,
           allVersions[] = {};
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
        String[] release_names = GithubApi.release_names(GROUP, NAME);
        if (release_names == null)
        {
            LOG.error("Failed to fetch release names");
        }
        else
        {
            Arrays.sort(release_names, MiscUtils.SEMVER_CMP_NEWEST_FIRST);
            this.allVersions = release_names;
        }
    }

    void maybeCheckInstances()
    {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(this.settings.lastInstancesCheck.getOrSupply().plusMinutes(5L)))
        {
            this.settings.lastInstancesCheck.set(now);
            this.checkInstances();
        }
    }

    void checkInstances()
    {
        List<GroupInstance> groupInstances = this.vrc.getGroupInstances(this.vrc.groupId);
        if (groupInstances == null || groupInstances.isEmpty())
            return;
        Set<String> locations = this.data.liveInstancesMetadata_getLocations();
        if (locations == null || locations.isEmpty())
            return;
        locations = new HashSet<>(locations);
        for (GroupInstance groupInstance : groupInstances)
        {
            String location = groupInstance.getLocation();
            locations.remove(location);
            ScarletData.InstanceEmbedMessage instanceEmbedMessage = this.data.liveInstancesMetadata_getLocationInstanceEmbedMessage(location, false);
            this.discord.emitExtendedInstanceMonitor(this, location, instanceEmbedMessage);
        }
        if (locations.isEmpty())
            return;
        for (String location : locations)
        {
            String auditEntryId = this.data.liveInstancesMetadata_getLocationAudit(location, true);
            ScarletData.InstanceEmbedMessage instanceEmbedMessage = this.data.liveInstancesMetadata_getLocationInstanceEmbedMessage(location, true);
            this.discord.emitExtendedInstanceInactive(this, location, auditEntryId, instanceEmbedMessage);
        }
    }

    void maybeModSummary()
    {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC),
                       next = this.settings.nextModSummary.getOrNull();
        if (next == null)
        {
            next = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
            while (now.isBefore(next))
                next.minusHours(24L);
        }
        if (now.isAfter(next))
        {
            this.settings.nextModSummary.set(next.plusHours(24L));
            this.modSummary(next);
        }
    }

    void modSummary(OffsetDateTime endOfDay)
    {
        this.discord.emitModSummary(this, endOfDay);
    }

    void maybeSaveData()
    {
        this.data.saveDirty();
    }

    void maybeRefresh()
    {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (this.wantsVrcRefresh || now.isAfter(this.settings.lastAuthRefresh.getOrSupply().plusHours(72)))
        {
            this.settings.lastAuthRefresh.set(now);
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
    void queryEmit(long currentPollInterval)
    {
        long offsetMillis = this.vrc.getLocalDriftMillis();
        if (currentPollInterval < 30_000L)
        {
            offsetMillis += (30_000L - currentPollInterval) / 10L;
        }
        OffsetDateTime from = this.settings.lastAuditQuery.getOrSupply(),
                       to = OffsetDateTime.now(ZoneOffset.UTC).minusNanos(offsetMillis * 1_000_000L);
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
            ex.printStackTrace();
        }
        
        this.settings.lastAuditQuery.set(to);
    }

}
