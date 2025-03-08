package net.sybyline.scarlet;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;

import io.github.vrchatapi.ApiCallback;
import io.github.vrchatapi.ApiClient;
import io.github.vrchatapi.ApiException;
import io.github.vrchatapi.Configuration;
import io.github.vrchatapi.JSON;
import io.github.vrchatapi.ProgressResponseBody;
import io.github.vrchatapi.api.AuthenticationApi;
import io.github.vrchatapi.api.GroupsApi;
import io.github.vrchatapi.api.SystemApi;
import io.github.vrchatapi.api.UsersApi;
import io.github.vrchatapi.api.WorldsApi;
import io.github.vrchatapi.model.BanGroupMemberRequest;
import io.github.vrchatapi.model.Group;
import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.GroupLimitedMember;
import io.github.vrchatapi.model.GroupMemberStatus;
import io.github.vrchatapi.model.LimitedUserGroups;
import io.github.vrchatapi.model.PaginatedGroupAuditLogEntryList;
import io.github.vrchatapi.model.TwoFactorAuthCode;
import io.github.vrchatapi.model.TwoFactorEmailCode;
import io.github.vrchatapi.model.User;
import io.github.vrchatapi.model.World;

import net.sybyline.scarlet.util.MiscUtils;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ScarletVRChat implements Closeable
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/VRChat");

    public static final Comparator<GroupAuditLogEntry> OLDEST_TO_NEWEST = Comparator.comparing(GroupAuditLogEntry::getCreatedAt);

    static void initJson()
    {
        Gson prev = JSON.getGson();
        JSON.setGson(JSON.createGson().registerTypeAdapterFactory(new TypeAdapterFactory() {
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                TypeAdapter<T> fbTA = Scarlet.GSON.getAdapter(type);
                TypeAdapter<T> prevTA = prev.getAdapter(type);
                TypeAdapter<JsonElement> prevJE = prev.getAdapter(JsonElement.class);
                return new TypeAdapter<T>() {
                    @Override
                    public void write(JsonWriter out, T value) throws IOException {
                        if (value == null) {
                            out.nullValue();
                            return;
                        }
                        JsonElement je;
                        try {
                            je = prevTA.toJsonTree(value);
                        } catch (Exception ex) {
                            je = fbTA.toJsonTree(value);
                        }
                        prevJE.write(out, je);
                    }
                    @Override
                    public T read(JsonReader in) throws IOException {
                        if (in.peek() == JsonToken.NULL) {
                            in.nextNull();
                            return null;
                        }
                        JsonElement je = prevJE.read(in);
                        T value;
                        try {
                            value = prevTA.fromJsonTree(je);
                        } catch (Exception ex) {
                            value = fbTA.fromJsonTree(je);
                        }
                        return value;
                    }
                };
            }
        }).create());
    }

    public ScarletVRChat(Scarlet scarlet, File cookieFile)
    {
        scarlet.splash.splashSubtext("Configuring VRChat Api");
        // ensure default ApiClient initialized
        // the ApiClient constructor resets JSON.gson
        Configuration.getDefaultApiClient();
        
        this.scarlet = scarlet;
        this.cookies = new ScarletVRChatCookieJar(cookieFile);
        this.client = new ApiClient(new OkHttpClient.Builder()
                .addNetworkInterceptor(this::intercept)
                .cookieJar(this.cookies)
                .build())
            .setBasePath(Scarlet.API_BASE_2)
            .setUserAgent(Scarlet.USER_AGENT);
        // override default Gson
        initJson();
        // override default ApiClient
        Configuration.setDefaultApiClient(this.client);
        this.cookies.setup(this.client);
        this.cookies.load();
        this.groupId = scarlet.settings.getStringOrRequireInput("vrchat_group_id", "VRChat Group ID", false);
        this.groupOwnerId = null;
        this.currentUserId = null;
        scarlet.settings.setNamespace(this.groupId);
        this.cachedUsers = new ScarletJsonCache<>("usr", User.class);
        this.cachedWorlds = new ScarletJsonCache<>("wrld", World.class);
        this.cachedGroups = new ScarletJsonCache<>("grp", Group.class);
        this.cachedUserGroups = new ScarletJsonCache<>("gmem", new TypeToken<List<LimitedUserGroups>>(){});
    }

    final Scarlet scarlet;
    final ScarletVRChatCookieJar cookies;
    final ApiClient client;
    final String groupId;
    String groupOwnerId;
    String currentUserId;
    final ScarletJsonCache<User> cachedUsers;
    final ScarletJsonCache<World> cachedWorlds;
    final ScarletJsonCache<Group> cachedGroups;
    final ScarletJsonCache<List<LimitedUserGroups>> cachedUserGroups;
    long localDriftMillis = 0L;

    public ApiClient getClient()
    {
        return this.client;
    }

    private Response intercept(Interceptor.Chain chain) throws IOException
    {
        Request request = chain.request();
        Response originalResponse = chain.proceed(request);
        Object tag = request.tag();
        return tag instanceof ApiCallback
            ? originalResponse.newBuilder().body(new ProgressResponseBody(originalResponse.body(), (ApiCallback<?>)tag)).build()
            : originalResponse;
    }

    public long getLocalDriftMillis()
    {
        return this.localDriftMillis;
    }

    public void login()
    {
        this.login(false);
    }

    private void login(boolean isRefresh)
    {
        try
        {
            long timePre = System.currentTimeMillis(),
                 timeServer = new SystemApi(this.client).getSystemTime().toInstant().toEpochMilli(),
                 timePost = System.currentTimeMillis(),
                 drift = (timePre + timePost) / 2 - timeServer,
                 driftAbs = Math.abs(drift);
            if (driftAbs >= 1_000L)
            {
                LOG.warn("Local system time is "+driftAbs+(drift > 0 ? "ms ahead of VRChat servers" : "ms behind VRChat servers"));
            }
            this.localDriftMillis = drift;
        }
        catch (ApiException apiex)
        {
            LOG.error("Exception calculating drift", apiex);
        }
        try
        {
            AuthenticationApi auth = new AuthenticationApi(this.client);
            try
            {
                if (auth.verifyAuthToken().getOk().booleanValue())
                {
                    LOG.info("Logged in (cached-verified)");
                    try
                    {
                        this.currentUserId = auth.getCurrentUser().getId();
                    }
                    catch (ApiException apiex)
                    {
                        LOG.info("Exception getting current user even though cached auth should be valid", apiex);
                    }
                    return;
                }
                else
                {
                    if (!isRefresh) LOG.info("Auth declared invalid");
                }
            }
            catch (ApiException apiex)
            {
                if (!isRefresh) LOG.info("Auth discovered invalid", apiex);
            }
            JsonObject data;
            try
            {
                data = this.client.<JsonObject>execute(auth.getCurrentUserCall(null), JsonObject.class).getData();
                if (data.has("id"))
                {
                    LOG.info("Logged in (cached)");
                    this.currentUserId = data.get("id").getAsString();
                    return;
                }
            }
            catch (Exception ex)
            {
                if (!isRefresh) LOG.info("Cached auth not valid", ex);
                do try
                {
                    this.client.setUsername(this.scarlet.settings.getStringOrRequireInput("vrc_username", "VRChat Username", false));
                    this.client.setPassword(this.scarlet.settings.getStringOrRequireInput("vrc_password", "VRChat Password", true));
                    data = this.client.<JsonObject>execute(auth.getCurrentUserCall(null), JsonObject.class).getData();
                    if (data.has("id"))
                    {
                        LOG.info("Logged in (credentials)");
                        this.currentUserId = data.get("id").getAsString();
                        return;
                    }
                }
                catch (ApiException apiex)
                {
                    this.scarlet.settings.getJson().remove("vrc_username");
                    this.scarlet.settings.getJson().remove("vrc_password");
                    LOG.error("Invalid credentials");
                    data = null;
                }
                finally
                {
                    this.client.setUsername(null);
                    this.client.setPassword(null);
                }
                while (data == null);
            }
            
            List<String> twoFactorMethods = data.get("requiresTwoFactorAuth")
                    .getAsJsonArray()
                    .asList()
                    .stream()
                    .map(JsonElement::getAsJsonPrimitive)
                    .map(JsonPrimitive::getAsString)
                    .collect(Collectors.toList());
            if (twoFactorMethods.contains("totp"))
            {
                String secret = this.scarlet.settings.getString("vrc_secret");
                if (secret != null && (secret = secret.replaceAll("[^A-Za-z2-7=]", "")).length() == 32)
                {
                    boolean authed = false;
                    for (int tries = 2; !authed && tries --> 0; MiscUtils.sleep(3_000L)) try
                    {
                        // use VRChatAPI time to work around potential local system time drift
                        long now = new SystemApi(this.client).getSystemTime().toInstant().toEpochMilli();
                        String code = TimeBasedOneTimePasswordUtil.generateNumberString(secret, now, TimeBasedOneTimePasswordUtil.DEFAULT_TIME_STEP_SECONDS, TimeBasedOneTimePasswordUtil.DEFAULT_OTP_LENGTH);
                        authed = auth.verify2FA(new TwoFactorAuthCode().code(code)).getVerified().booleanValue();
                    }
                    catch (GeneralSecurityException gsex)
                    {
                        LOG.error("Exception generating totp secret", gsex);
                    }
                    catch (ApiException apiex)
                    {
                        LOG.error("Exception using totp secret", apiex);
                    }
                }
                
                for (boolean needsTotp = true; needsTotp && this.scarlet.running;) try
                {
                    if (needsTotp = !auth.verify2FA(new TwoFactorAuthCode().code(this.scarlet.settings.requireInput("Totp code", true))).getVerified().booleanValue())
                        LOG.error("Invalid totp code");
                }
                catch (ApiException apiex)
                {
                    LOG.error("Exception using totp", apiex);
                }
                
                LOG.info("Logged in (2fa-totp)");
            }
            else if (twoFactorMethods.contains("emailotp"))
            {
                for (boolean needsTotp = true; needsTotp && this.scarlet.running;) try
                {
                    if (needsTotp = !auth.verify2FAEmailCode(new TwoFactorEmailCode().code(this.scarlet.settings.requireInput("Emailotp code", true))).getVerified().booleanValue())
                        LOG.error("Invalid emailotp code");
                }
                catch (ApiException apiex)
                {
                    LOG.error("Exception using emailotp", apiex);
                }
                
                LOG.info("Logged in (2fa-emailotp)");
            }
            else
            {
                String message = "Unsupported 2fa methods: "+twoFactorMethods;
                LOG.error(message);
                throw new UnsupportedOperationException(message);
            }

            try
            {
                this.currentUserId = auth.getCurrentUser().getId();
            }
            catch (ApiException apiex)
            {
                LOG.info("Exception getting current user even though current auth should be valid", apiex);
            }
            
            return;
        }
        finally
        {
            this.save();
            {
                Group group = this.getGroup(this.groupId);
                if (group != null)
                {
                    this.groupOwnerId = group.getOwnerId();
                }
            }
        }
    }

    public boolean logout()
    {
        return this.logout(false);
    }

    private boolean logout(boolean isRefresh)
    {
        try
        {
            AuthenticationApi auth = new AuthenticationApi(this.client);
            if (!isRefresh) LOG.info("Log out: "+auth.logout().getSuccess().getMessage());
            return true;
        }
        catch (ApiException apiex)
        {
            LOG.error("Error logging out", apiex);
            return false;
        }
    }

    public void refresh()
    {
        LOG.info("Refreshing auth");
        this.logout(true);
        this.login(true);
    }

    public List<GroupAuditLogEntry> auditQuery(OffsetDateTime from, OffsetDateTime to)
    {
        return this.auditQuery(from, to, null, null, null);
    }
    public List<GroupAuditLogEntry> auditQueryTargeting(String targetIds, int daysBack)
    {
        OffsetDateTime to = OffsetDateTime.now(),
                       from = to.minusDays(daysBack);
        return this.auditQuery(from, to, null, null, targetIds);
    }
    public List<GroupAuditLogEntry> auditQueryActored(String actorIds, int daysBack)
    {
        OffsetDateTime to = OffsetDateTime.now(),
                       from = to.minusDays(daysBack);
        return this.auditQuery(from, to, actorIds, null, null);
    }
    public List<GroupAuditLogEntry> auditQuery(OffsetDateTime from, OffsetDateTime to, String actorIds, String eventTypes, String targetIds)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            List<GroupAuditLogEntry> audits = new ArrayList<>();
            int offset = 0, batchSize = 100;
            PaginatedGroupAuditLogEntryList pgalel;
            pgalel = groups.getGroupAuditLogs(this.groupId, batchSize, offset, from, to, actorIds, eventTypes, targetIds);
            while (pgalel.getHasNext().booleanValue())
            {
                audits.addAll(pgalel.getResults());
                offset += batchSize;
                MiscUtils.sleep(250L);
                pgalel = groups.getGroupAuditLogs(this.groupId, batchSize, offset, from, to, actorIds, eventTypes, targetIds);
            }
            audits.addAll(pgalel.getResults());
            audits.sort(OLDEST_TO_NEWEST);
            return audits;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error during audit query: "+apiex.getMessage());
            return null;
        }
    }

    public User getUser(String userId)
    {
        return this.getUser(userId, Long.MIN_VALUE);
    }
    public User getUser(String userId, long minEpoch)
    {
        User user = this.cachedUsers.get(userId, minEpoch);
        if (user != null)
            return user;
        if (this.cachedUsers.is404(userId))
            return null;
        UsersApi users = new UsersApi(this.client);
        try
        {
            user = users.getUser(userId);
            this.cachedUsers.put(userId, user);
            return user;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedUsers.add404(userId);
            else
                LOG.error("Error during get user: "+apiex.getMessage());
            return null;
        }
    }

    public World getWorld(String worldId)
    {
        return this.getWorld(worldId, Long.MIN_VALUE);
    }
    public World getWorld(String worldId, long minEpoch)
    {
        World world = this.cachedWorlds.get(worldId, minEpoch);
        if (world != null)
            return world;
        if (this.cachedWorlds.is404(worldId))
            return null;
        WorldsApi worlds = new WorldsApi(this.client);
        try
        {
            world = worlds.getWorld(worldId);
            this.cachedWorlds.put(worldId, world);
            return world;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedUsers.add404(worldId);
            else
                LOG.error("Error during get world: "+apiex.getMessage());
            return null;
        }
    }

    public Group getGroup(String groupId)
    {
        return this.getGroup(groupId, Long.MIN_VALUE);
    }
    public Group getGroup(String groupId, long minEpoch)
    {
        Group group = this.cachedGroups.get(groupId, minEpoch);
        if (group != null)
            return group;
        if (this.cachedGroups.is404(groupId))
            return null;
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            group = groups.getGroup(groupId, null);
            this.cachedGroups.put(groupId, group);
            return group;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedGroups.add404(groupId);
            else
                LOG.error("Error during get group: "+apiex.getMessage());
            return null;
        }
    }

    public List<LimitedUserGroups> getUserGroups(String userId)
    {
        return this.getUserGroups(userId, Long.MIN_VALUE);
    }
    public List<LimitedUserGroups> getUserGroups(String userId, long minEpoch)
    {
        List<LimitedUserGroups> userGroups = this.cachedUserGroups.get(userId, minEpoch);
        if (userGroups != null)
            return userGroups;
        if (this.cachedUserGroups.is404(userId))
            return null;
        UsersApi users = new UsersApi(this.client);
        try
        {
            userGroups = users.getUserGroups(userId);
            this.cachedUserGroups.put(userId, userGroups);
            return userGroups;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedUserGroups.add404(userId);
            else
                LOG.error("Error during get user groups: "+apiex.getMessage());
            return null;
        }
    }

    public GroupMemberStatus getMembership(String targetUserId)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            GroupLimitedMember member = groups.getGroupMember(this.groupId, targetUserId);
            if (member == null)
                return null;
            return member.getMembershipStatus();
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error during ban from group: "+apiex.getMessage());
            return null;
        }
    }

    public boolean banFromGroup(String targetUserId)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            groups.banGroupMember(this.groupId, new BanGroupMemberRequest().userId(targetUserId));
            return true;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error during ban from group: "+apiex.getMessage());
            return false;
        }
    }

    public boolean unbanFromGroup(String targetUserId)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            groups.unbanGroupMember(this.groupId, targetUserId);
            return true;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error during ban from group: "+apiex.getMessage());
            return false;
        }
    }

    public void save()
    {
        this.cookies.save();
    }

    @Override
    public void close() throws IOException
    {
        this.cookies.close();
    }

}
