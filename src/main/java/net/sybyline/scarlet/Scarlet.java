package net.sybyline.scarlet;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
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

import net.sybyline.scarlet.util.JsonAdapters;
import net.sybyline.scarlet.util.MiscUtils;

public class Scarlet implements Closeable
{

    public static void main(String[] args) throws Exception
    {
        try (Scarlet scarlet = new Scarlet())
        {
            scarlet.run();
        }
    }

    public static final String
        GROUP = "SybylineNetwork",
        NAME = "Scarlet",
        VERSION = "0.3.1",
        DEV_DISCORD = "Discord:@vinyarion/Vinyarion#0292/393412191547555841",
        USER_AGENT_NAME = "Sybyline-Network-"+NAME,
        USER_AGENT = String.format("%s/%s %s", USER_AGENT_NAME, VERSION, DEV_DISCORD),
        USER_AGENT_STATIC = String.format("%s/%s-static %s", USER_AGENT_NAME, VERSION, DEV_DISCORD),
        
        API_URL = "https://%s/",
        API_BASE = "https://%s/api/1",
        API_HOST_0 = "vrchat.com",
        API_URL_0 = String.format(API_URL, API_HOST_0),
        API_BASE_0 = String.format(API_BASE, API_HOST_0),
        API_HOST_1 = "api.vrchat.com",
        API_URL_1 = String.format(API_URL, API_HOST_1),
        API_BASE_1 = String.format(API_BASE, API_HOST_1),
        API_HOST_2 = "api.vrchat.cloud",
        API_URL_2 = String.format(API_URL, API_HOST_2),
        API_BASE_2 = String.format(API_BASE, API_HOST_2);

    public static final Logger LOG = LoggerFactory.getLogger("Scarlet");

    public static final Gson GSON, GSON_PRETTY;
    static
    {
        GsonBuilder gb = JsonAdapters.gson();
        GSON = gb.create();
        GSON_PRETTY = gb.setPrettyPrinting().create();
    }

    public Scarlet()
    {
    }

    @Override
    public void close() throws IOException
    {
        this.running = false;
        this.discord.close();
    }

    final File dir = new File(System.getenv("LOCALAPPDATA"), GROUP+"/"+NAME);
    {
        if (!this.dir.isDirectory())
            this.dir.mkdirs();
    }
    final ScarletSettings settings = new ScarletSettings(new File(this.dir, "settings.json"));
    final ScarletModerationTags moderationTags = new ScarletModerationTags(new File(this.dir, "moderation_tags.json"));
    final ScarletData data = new ScarletData(new File(this.dir, "data"));
    final ScarletVRChat vrc = new ScarletVRChat(this, new File(this.dir, "store.bin"));
    final ScarletDiscord discord = new ScarletDiscordJDA(this, new File(this.dir, "discord_bot.json"));
    volatile boolean running = true;

    public void run()
    {
        try
        {
            this.vrc.login();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return;
        }
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
                    ex.printStackTrace();
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
                    ex.printStackTrace();
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
                            ScarletData.UserMetadata userMeta = this.data.userMetadata(userId);
                            if (userMeta == null)
                                userMeta = new ScarletData.UserMetadata();
                            userMeta.userSnowflake = userSnowflake;
                            this.data.userMetadata(userId, userMeta);
                            LOG.info("Linking VRChat user "+user.getDisplayName()+" ("+userId+") to Discord user <@"+userSnowflake+">");
                        }
                    } break;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
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
        LOG.info("Querying from "+from+" to "+to);
        List<GroupAuditLogEntry> entries = this.vrc.auditQuery(from, to);
        
        for (GroupAuditLogEntry entry : entries)
        {
            this.discord.process(this, entry);
        }
        
        this.settings.setLastAuditQuery(to);
    }

}
