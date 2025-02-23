package net.sybyline.scarlet;

import java.awt.Desktop;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.vrchatapi.ApiException;
import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.User;

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
        VERSION = "0.4.4",
        DEV_DISCORD = "Discord:@vinyarion/Vinyarion#0292/393412191547555841",
        USER_AGENT_NAME = "Sybyline-Network-"+NAME,
        USER_AGENT = USER_AGENT_NAME+"/"+VERSION+" "+DEV_DISCORD,
        USER_AGENT_STATIC = USER_AGENT_NAME+"-static/"+VERSION+" "+DEV_DISCORD,
        META_URL = "https://github.com/"+GROUP+"/"+NAME+"/blob/main/meta.json?raw=true",
        
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

    static
    {
        MavenDepsLoader.init();
    }

    public static void main(String[] args) throws Exception
    {
        try (Scarlet scarlet = new Scarlet())
        {
            scarlet.run();
        }
        System.exit(0);
    }

    public static final Logger LOG = LoggerFactory.getLogger("Scarlet");

    public static final Gson GSON, GSON_PRETTY;
    static
    {
        GsonBuilder gb = JsonAdapters.gson();
        GSON = gb.create();
        GSON_PRETTY = gb.setPrettyPrinting().create();
    }

    public Scarlet() throws IOException
    {
    }

    {
        // absolute initialization, even before explicit constructor body
    }

    public void stop()
    {
        this.running = false;
    }

    @Override
    public void close() throws IOException
    {
        this.stop();
        MiscUtils.close(this.ttsService);
        MiscUtils.close(this.discord);
        MiscUtils.close(this.logs);
    }

    volatile boolean running = true;
    final File dir = new File(System.getenv("LOCALAPPDATA"), GROUP+"/"+NAME);
    {
        if (!this.dir.isDirectory())
            this.dir.mkdirs();
    }
    final File dirVrc = new File(System.getProperty("user.home"), "AppData/LocalLow/VRChat/VRChat");
    
    final ScarletEventListener eventListener = new ScarletEventListener(this);
    final ScarletSettings settings = new ScarletSettings(new File(this.dir, "settings.json"));
    final ScarletModerationTags moderationTags = new ScarletModerationTags(new File(this.dir, "moderation_tags.json"));
    final ScarletWatchedGroups watchedGroups = new ScarletWatchedGroups(this, new File(this.dir, "watched_groups.json"));
    final ScarletStaffList staffList = new ScarletStaffList(this, new File(this.dir, "staff_list.json"));
    final ScarletData data = new ScarletData(new File(this.dir, "data"));
    final TTSService ttsService = new TTSService(new File(this.dir, "tts"), this.eventListener);
    final ScarletVRChat vrc = new ScarletVRChat(this, new File(this.dir, "store.bin"));
    final ScarletDiscord discord = new ScarletDiscordJDA(this, new File(this.dir, "discord_bot.json"));
    final ScarletVRChatLogs logs = new ScarletVRChatLogs(this, this.eventListener);

    public void run()
    {
        this.checkUpdate();
        try
        {
            this.vrc.login();
        }
        catch (Exception ex)
        {
            LOG.error("Failed to authenticate with VRChat", ex);
            return;
        }
        this.logs.start();
        try
        {
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
                String line = s.nextLine();
                Scanner ls = new Scanner(new StringReader(line.trim()));
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
                        if (Desktop.isDesktopSupported())
                        {
                            Desktop.getDesktop().browse(this.dir.toURI());
                            LOG.info("Browsing to folder");
                        }
                    } break;
                    case "tts": {
                        String text = ls.nextLine().trim();
                        if (!text.isEmpty())
                        {
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
                        try (Reader reader = isUrl ? new InputStreamReader(HttpURLInputStream.get(from)) : new FileReader(from))
                        {
                            if (this.watchedGroups.importLegacyCSV(reader))
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
                    }
                }
            }
        }
        catch (Exception ex)
        {
            LOG.error("Exception in spin", ex);
        }
    }

    void checkUpdate()
    {
        try
        {
            ScarletMeta meta;
            try (HttpURLInputStream in = HttpURLInputStream.get(META_URL))
            {
                meta = GSON_PRETTY.fromJson(new InputStreamReader(in), ScarletMeta.class);
            }
            if (MiscUtils.compareSemVer(VERSION, meta.latest_release) < 0)
            {
                LOG.info("Newer version "+meta.latest_release+" available");
            }
        }
        catch (Exception ex)
        {
            LOG.error("Failed to download meta", ex);
        }
    }
    
    void maybeRefresh() throws ApiException
    {
        OffsetDateTime lastAuthRefresh = this.settings.getLastAuthRefresh(),
                       now = OffsetDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(lastAuthRefresh.plusHours(72)))
        {
            this.settings.setLastAuthRefresh(now);
            this.vrc.refresh();
        }
    }

    static final int CATCH_UP_INSTANTANEOUS = 0,
                     CATCH_UP_SKIP_UNTIL_24 = 1,
                     CATCH_UP_LIMIT_NEXT_24 = 2;
    int catchupMode = CATCH_UP_LIMIT_NEXT_24;
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
        
        for (GroupAuditLogEntry entry : entries)
        {
            this.discord.process(this, entry);
        }
        
        this.settings.setLastAuditQuery(to);
    }

}
