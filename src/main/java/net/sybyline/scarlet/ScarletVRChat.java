package net.sybyline.scarlet;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import io.github.vrchatapi.ApiResponse;
import io.github.vrchatapi.Configuration;
import io.github.vrchatapi.JSON;
import io.github.vrchatapi.ProgressResponseBody;
import io.github.vrchatapi.api.AuthenticationApi;
import io.github.vrchatapi.api.AvatarsApi;
import io.github.vrchatapi.api.FilesApi;
import io.github.vrchatapi.api.GroupsApi;
import io.github.vrchatapi.api.MiscellaneousApi;
import io.github.vrchatapi.api.PrintsApi;
import io.github.vrchatapi.api.PropsApi;
import io.github.vrchatapi.api.UsersApi;
import io.github.vrchatapi.api.WorldsApi;
import io.github.vrchatapi.model.Avatar;
import io.github.vrchatapi.model.BanGroupMemberRequest;
import io.github.vrchatapi.model.CreateGroupInviteRequest;
import io.github.vrchatapi.model.CurrentUser;
import io.github.vrchatapi.model.Group;
import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.GroupInstance;
import io.github.vrchatapi.model.GroupJoinRequestAction;
import io.github.vrchatapi.model.GroupLimitedMember;
import io.github.vrchatapi.model.GroupMemberStatus;
import io.github.vrchatapi.model.GroupPermissions;
import io.github.vrchatapi.model.GroupRole;
import io.github.vrchatapi.model.Instance;
import io.github.vrchatapi.model.InventoryItem;
import io.github.vrchatapi.model.LimitedUserGroups;
import io.github.vrchatapi.model.LimitedUserSearch;
import io.github.vrchatapi.model.LimitedWorld;
import io.github.vrchatapi.model.ModelFile;
import io.github.vrchatapi.model.OrderOption;
import io.github.vrchatapi.model.PaginatedGroupAuditLogEntryList;
import io.github.vrchatapi.model.Print;
import io.github.vrchatapi.model.Prop;
import io.github.vrchatapi.model.RespondGroupJoinRequest;
import io.github.vrchatapi.model.SortOption;
import io.github.vrchatapi.model.TwoFactorAuthCode;
import io.github.vrchatapi.model.TwoFactorEmailCode;
import io.github.vrchatapi.model.UpdateGroupMemberRequest;
import io.github.vrchatapi.model.User;
import io.github.vrchatapi.model.World;

import net.sybyline.scarlet.util.EnumHelper;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.VersionedFile;

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

                        // TODO: update dependencies to support the api change

                        // Fix for VRChat API change: currentAvatarTags is now an array instead of string
                        // Strip it from presence object to avoid deserialization errors
                        if (je.isJsonObject()) {
                            JsonObject obj = je.getAsJsonObject();
                            if (obj.has("presence") && obj.get("presence").isJsonObject()) {
                                JsonObject presence = obj.getAsJsonObject("presence");
                                if (presence.has("currentAvatarTags")) {
                                    presence.remove("currentAvatarTags");
                                }
                            }
                        }
                        // TODO: update dependencies to support the api change
                        
                        T value;
                        try {
                            value = prevTA.fromJsonTree(je);
                        } catch (Exception ex) {
                            @SuppressWarnings("rawtypes")
                            Class enumType = type.getRawType();
                            if (enumType.isEnum()) try {
                                value = fbTA.fromJsonTree(je);
                            } catch (Exception ex1) {
                                if (enumType.isEnum() && enumType.getName().replace('/', '.').startsWith("io.github.vrchatapi.model.")) {
                                    String enumValue = je.getAsString();
                                    LOG.warn("Encountering new API enum value for "+enumType.getName()+": `"+enumValue+"`, implicitly instantiating instance...");
                                    @SuppressWarnings("unchecked")
                                    Object newValue = EnumHelper.addJsonStringEnum(enumType, enumValue);
                                    try {
                                        value = fbTA.fromJsonTree(je);
                                        if (newValue != value) {
                                            LOG.error("Mismatch in parsed values: expected `"+newValue+"`, got `"+value+"`");
                                        }
                                    } catch (Exception ex2) {
                                        LOG.error("Failed to instantiate encountered enum, returning null instead");
                                        value = null;
                                    }
                                } else {
                                    LOG.error("Unknown enum value for `"+je.getAsString()+"`, returning null instead");
                                    value = null;
                                }
                            } else {
                                value = fbTA.fromJsonTree(je);
                            }
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
        this.groupId = MiscUtils.extractTypedUuid("grp", "", scarlet.settings.getStringOrRequireInput("vrchat_group_id", "VRChat Group ID", false));
        this.groupOwnerId = null;
        this.group = null;
        this.groupLimitedMember = null;
        this.currentUser = null;
        this.currentUserId = null;
        scarlet.settings.setNamespace(this.groupId);
        this.cachedUsers = new ScarletJsonCache<>("usr", User.class);
        this.cachedWorlds = new ScarletJsonCache<>("wrld", World.class);
        this.cachedGroups = new ScarletJsonCache<>("grp", Group.class);
        this.cachedUserGroups = new ScarletJsonCache<>("gmem", new TypeToken<List<LimitedUserGroups>>(){});
        this.cachedAvatars = new ScarletJsonCache<>("avtr", Avatar.class);
        this.cachedPrints = new ScarletJsonCache<>("prnt", Print.class);
        this.cachedProps = new ScarletJsonCache<>("prop", Prop.class);
        this.cachedInventoryItems = new ScarletJsonCache<>("inv", InventoryItem.class);
        this.cachedModelFiles = new ScarletJsonCache<>("file", ModelFile.class);
    }

    final Scarlet scarlet;
    final ScarletVRChatCookieJar cookies;
    final ApiClient client;
    final String groupId;
    String groupOwnerId;
    Group group;
    GroupLimitedMember groupLimitedMember;
    CurrentUser currentUser;
    String currentUserId;
    final ScarletJsonCache<User> cachedUsers;
    final ScarletJsonCache<World> cachedWorlds;
    final ScarletJsonCache<Group> cachedGroups;
    final ScarletJsonCache<List<LimitedUserGroups>> cachedUserGroups;
    final ScarletJsonCache<Avatar> cachedAvatars;
    final ScarletJsonCache<Print> cachedPrints;
    final ScarletJsonCache<Prop> cachedProps;
    final ScarletJsonCache<InventoryItem> cachedInventoryItems;
    final ScarletJsonCache<ModelFile> cachedModelFiles;
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
                 timeServer = new MiscellaneousApi(this.client).getSystemTime().toInstant().toEpochMilli(),
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
                        this.currentUserId = (this.currentUser = auth.getCurrentUser()).getId();
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
                    this.currentUserId = (this.currentUser = JSON.getGson().fromJson(data, CurrentUser.class)).getId();
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
                        this.currentUserId = (this.currentUser = JSON.getGson().fromJson(data, CurrentUser.class)).getId();
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
                    .map(String::toLowerCase)
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
                        long now = new MiscellaneousApi(this.client).getSystemTime().toInstant().toEpochMilli();
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
                this.currentUserId = (this.currentUser = auth.getCurrentUser()).getId();
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
                Group group = this.getGroup(this.groupId, Boolean.TRUE);
                if (group != null)
                {
                    this.groupOwnerId = group.getOwnerId();
                    this.group = group;
                    this.groupLimitedMember = this.getGroupMembership(this.groupId, this.currentUserId);
                    if (group.getRoles() == null)
                    {
                        group.setRoles(this.getGroupRoles(this.groupId));
                    }
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
    public Integer auditQueryCount(OffsetDateTime from, OffsetDateTime to, String actorIds, String eventTypes, String targetIds)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            return groups.getGroupAuditLogs(this.groupId, 0, 0, from, to, actorIds, eventTypes, targetIds).getTotalCount();
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error during audit query: "+apiex.getMessage());
            return null;
        }
    }
    public List<LimitedUserSearch> searchUsers(String name, Integer n, Integer offset)
    {
        UsersApi users = new UsersApi(this.client);
        try
        {
            return users.searchUsers(name, null, n, offset);
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            return null;
        }
    }

    public User getUser(String userId)
    {
        return this.getUser(userId, Long.MAX_VALUE);
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

    public List<LimitedWorld> searchWorlds(String name, Integer n, Integer offset)
    {
        WorldsApi worlds = new WorldsApi(this.client);
        try
        {
            return worlds.searchWorlds(null, SortOption.RELEVANCE, null, null, n, OrderOption.DESCENDING, offset, null, null, null, null, null, null, null, null);
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            return null;
        }
    }
    public World getWorld(String worldId)
    {
        return this.getWorld(worldId, Long.MAX_VALUE);
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
                this.cachedWorlds.add404(worldId);
            else
                LOG.error("Error during get world: "+apiex.getMessage());
            return null;
        }
    }

    public Group getGroup(String groupId)
    {
        return this.getGroup(groupId, null, Long.MAX_VALUE);
    }
    public Group getGroup(String groupId, long minEpoch)
    {
        return this.getGroup(groupId, null, minEpoch);
    }
    public Group getGroup(String groupId, Boolean includeRoles)
    {
        return this.getGroup(groupId, includeRoles, Long.MAX_VALUE);
    }
    public Group getGroup(String groupId, Boolean includeRoles, long minEpoch)
    {
        Group group = this.cachedGroups.get(groupId, minEpoch);
        if (group != null)
            return group;
        if (this.cachedGroups.is404(groupId))
            return null;
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            group = groups.getGroup(groupId, includeRoles);
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

    public List<GroupInstance> getGroupInstances(String groupId)
    {
        if (this.cachedGroups.is404(groupId))
            return null;
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            return groups.getGroupInstances(groupId);
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedGroups.add404(groupId);
            else
                LOG.error("Error during get group instances: "+apiex.getMessage());
            return null;
        }
    }

    public List<LimitedUserGroups> getUserGroups(String userId)
    {
        return this.getUserGroups(userId, Long.MAX_VALUE);
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

    public GroupMemberStatus getGroupMembershipStatus(String groupId, String targetUserId)
    {
        GroupLimitedMember member = this.getGroupMembership(groupId, targetUserId);
        return member == null ? null : member.getMembershipStatus();
    }
    public GroupLimitedMember getGroupMembership(String groupId, String targetUserId)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            return groups.getGroupMember(groupId, targetUserId);
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error getting group member group: "+apiex.getMessage());
            return null;
        }
    }
    public GroupLimitedMember updateGroupMembershipNotes(String groupId, String targetUserId, String managerNotes)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            return groups.updateGroupMember(groupId, targetUserId, new UpdateGroupMemberRequest().managerNotes(managerNotes));
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error updating group member notes: "+apiex.getMessage());
            return null;
        }
    }
    public List<String> addGroupRole(String groupId, String targetUserId, String groupRoleId)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            return groups.addGroupMemberRole(groupId, targetUserId, groupRoleId);
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error updating group member notes: "+apiex.getMessage());
            return null;
        }
    }
    public List<String> removeGroupRole(String groupId, String targetUserId, String groupRoleId)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            return groups.removeGroupMemberRole(groupId, targetUserId, groupRoleId);
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error updating group member notes: "+apiex.getMessage());
            return null;
        }
    }
    public List<GroupRole> getGroupRoles(String groupId)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            return groups.getGroupRoles(groupId);
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error getting group roles: "+apiex.getMessage());
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
            LOG.error("Error during unban from group: "+apiex.getMessage());
            return false;
        }
    }

    public boolean inviteToGroup(String targetUserId, Boolean confirmOverrideBlock)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            groups.createGroupInvite(this.groupId, new CreateGroupInviteRequest().userId(targetUserId).confirmOverrideBlock(confirmOverrideBlock));
            return true;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error during invite to group: "+apiex.getMessage());
            return false;
        }
    }

    public boolean respondToGroupJoinRequest(String targetUserId, GroupJoinRequestAction action, Boolean block)
    {
        GroupsApi groups = new GroupsApi(this.client);
        try
        {
            groups.respondGroupJoinRequest(this.groupId, targetUserId, new RespondGroupJoinRequest().action(action).block(block));
            return true;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            LOG.error("Error during response to group join request: "+apiex.getMessage());
            return false;
        }
    }

    public boolean checkSelfUserHasVRChatPermission(GroupPermissions vrchatPermission)
    {
        GroupLimitedMember glm = this.groupLimitedMember;
        return this.checkUserHasVRChatPermission(glm, vrchatPermission);
    }
    public boolean checkUserHasVRChatPermission(GroupPermissions vrchatPermission, String userId)
    {
        if (userId == null)
            return false;
        GroupLimitedMember glm = this.getGroupMembership(this.groupId, userId);
        return this.checkUserHasVRChatPermission(glm, vrchatPermission);
    }
    public boolean checkUserHasVRChatPermission(GroupLimitedMember glm, GroupPermissions vrchatPermission)
    {
        if (glm == null)
            return false;
        List<GroupRole> grl = this.group.getRoles();
        return grl != null && grl.stream().filter(Objects::nonNull).filter($ -> glm.getRoleIds().contains($.getId())).map(GroupRole::getPermissions).filter(Objects::nonNull).anyMatch($ -> $.contains(GroupPermissions.group_all) || $.contains(vrchatPermission));
    }

    public Avatar getAvatar(String avatarId)
    {
        return this.getAvatar(avatarId, Long.MAX_VALUE);
    }
    public Avatar getAvatar(String avatarId, long minEpoch)
    {
        Avatar avatar = this.cachedAvatars.get(avatarId, minEpoch);
        if (avatar != null)
            return avatar;
        if (this.cachedAvatars.is404(avatarId))
            return null;
        AvatarsApi avatars = new AvatarsApi(this.client);
        try
        {
            avatar = avatars.getAvatar(avatarId);
            this.cachedAvatars.put(avatarId, avatar);
            return avatar;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedAvatars.add404(avatarId);
            else
                LOG.error("Error during get avatar: ", apiex);//LOG.error("Error during get avatar: "+apiex.getMessage());
            return null;
        }
    }

    public Print getPrint(String printId)
    {
        return this.getPrint(printId, Long.MAX_VALUE);
    }
    public Print getPrint(String printId, long minEpoch)
    {
        Print print = this.cachedPrints.get(printId, minEpoch);
        if (print != null)
            return print;
        if (this.cachedPrints.is404(printId))
            return null;
        PrintsApi prints = new PrintsApi(this.client);
        try
        {
            print = prints.getPrint(printId);
            this.cachedPrints.put(printId, print);
            return print;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedPrints.add404(printId);
            else
                LOG.error("Error during get print: "+apiex.getMessage());
            return null;
        }
    }

    public Prop getProp(String propId)
    {
        return this.getProp(propId, Long.MAX_VALUE);
    }
    public Prop getProp(String propId, long minEpoch)
    {
        Prop prop = this.cachedProps.get(propId, minEpoch);
        if (prop != null)
            return prop;
        if (this.cachedProps.is404(propId))
            return null;
        PropsApi props = new PropsApi(this.client);
        try
        {
            prop = props.getProp(propId);
            this.cachedProps.put(propId, prop);
            return prop;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedProps.add404(propId);
            else
                LOG.error("Error during get prop: "+apiex.getMessage());
            return null;
        }
    }

    public InventoryItem getInventoryItem(String userId, String invId)
    {
        return this.getInventoryItem(userId, invId, Long.MAX_VALUE);
    }
    public InventoryItem getInventoryItem(String userId, String invId, long minEpoch)
    {
        InventoryItem item = this.cachedInventoryItems.get(invId, minEpoch);
        if (item != null)
            return item;
        if (this.cachedInventoryItems.is404(invId))
            return null;
//        InventoryApi inventory = new InventoryApi(this.client);
        try
        {
//            item = inventory.getInventoryItem(userId, invId);
            item = this.getInventoryItemEx(userId, invId);
            this.cachedInventoryItems.put(invId, item);
            return item;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedInventoryItems.add404(invId);
            else
                LOG.error("Error during get inventory item: "+apiex.getMessage());
            return null;
        }
    }
    public InventoryItem getInventoryItemEx(String userId, String invId) throws ApiException
    {
        Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");
            headers.put("Content-Type", "application/json");
        okhttp3.Call localVarCall = this.client.buildCall(null, "/user/"+userId+"/inventory/"+invId, "GET", new ArrayList<>(), new ArrayList<>(), null, headers, new HashMap<>(), new HashMap<>(), new String[]{"authCookie"}, null);
        ApiResponse<InventoryItem> localVarResp = this.client.execute(localVarCall, InventoryItem.class);
        return localVarResp.getData();
    }

    public ModelFile getModelFile(String fileId)
    {
        return this.getModelFile(fileId, Long.MAX_VALUE);
    }
    public ModelFile getModelFile(String fileId, long minEpoch)
    {
        ModelFile file = this.cachedModelFiles.get(fileId, minEpoch);
        if (file != null)
            return file;
        if (this.cachedModelFiles.is404(fileId))
            return null;
        FilesApi files = new FilesApi(this.client);
        try
        {
            file = files.getFile(fileId);
            this.cachedModelFiles.put(fileId, file);
            return file;
        }
        catch (ApiException apiex)
        {
            this.scarlet.checkVrcRefresh(apiex);
            if (apiex.getMessage().contains("HTTP response code: 404"))
                this.cachedModelFiles.add404(fileId);
            else
                LOG.error("Error during get file: "+apiex.getMessage());
            return null;
        }
    }

    public Instance createInstanceEx(JsonObject createInstanceRequest) throws ApiException
    {
        Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");
            headers.put("Content-Type", "application/json");
        okhttp3.Call localVarCall = this.client.buildCall(null, "/instances", "POST", new ArrayList<>(), new ArrayList<>(), createInstanceRequest, headers, new HashMap<>(), new HashMap<>(), new String[]{"authCookie"}, null);
        ApiResponse<Instance> localVarResp = this.client.execute(localVarCall, Instance.class);
        return localVarResp.getData();
    }

    public String getStickerFileId(String userId, String stickerId) throws ApiException
    {
        if (stickerId.startsWith("file_"))
            return stickerId;
        if (!stickerId.startsWith("inv_"))
            return null;
        Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");
            headers.put("Content-Type", "application/json");
        okhttp3.Call localVarCall = this.client.buildCall(null, "/user/"+userId+"/inventory/"+stickerId, "GET", new ArrayList<>(), new ArrayList<>(), null, headers, new HashMap<>(), new HashMap<>(), new String[]{"authCookie"}, null);
        ApiResponse<JsonObject> localVarResp = this.client.execute(localVarCall, JsonObject.class);
        return VersionedFile.parse(localVarResp.getData().getAsJsonPrimitive("imageUrl").getAsString()).id;
    }

    static final TypeToken<List<LimitedUserGroups>> LIST_LUGROUPS = new TypeToken<List<LimitedUserGroups>>(){};
    public List<LimitedUserGroups> snapshot(ScarletData.AuditEntryMetadata entryMeta)
    {
        List<LimitedUserGroups> lugs = null;
        try
        {
            Map<String, String> localVarHeaderParams = new HashMap<>();
            localVarHeaderParams.put("Accept", "application/json");
            okhttp3.Call localVarCall = this.client.buildCall(null, "/users/"+entryMeta.entry.getTargetId(), "GET", new ArrayList<>(), new ArrayList<>(), null, localVarHeaderParams, new HashMap<>(), new HashMap<>(), new String[]{"authCookie"}, null);
            entryMeta.snapshotTargetUser = this.client.<JsonObject>execute(localVarCall, JsonObject.class).getData();
        }
        catch (Exception ex)
        {
            LOG.error("Exception whilst fetching user", ex);
        }
        try
        {
            Map<String, String> localVarHeaderParams = new HashMap<>();
            localVarHeaderParams.put("Accept", "application/json");
            okhttp3.Call localVarCall = this.client.buildCall(null, "/users/"+entryMeta.entry.getTargetId()+"/groups", "GET", new ArrayList<>(), new ArrayList<>(), null, localVarHeaderParams, new HashMap<>(), new HashMap<>(), new String[]{"authCookie"}, null);
            entryMeta.snapshotTargetUserGroups = this.client.<JsonArray>execute(localVarCall, JsonArray.class).getData();
            try
            {
                lugs = JSON.getGson().fromJson(entryMeta.snapshotTargetUserGroups, LIST_LUGROUPS);
            }
            catch (Exception ex)
            {
                LOG.error("Exception whilst deserializing user groups", ex);
            }
        }
        catch (Exception ex)
        {
            LOG.error("Exception whilst fetching user groups", ex);
        }
        try
        {
            Map<String, String> localVarHeaderParams = new HashMap<>();
            localVarHeaderParams.put("Accept", "application/json");
            okhttp3.Call localVarCall = this.client.buildCall(null, "/users/"+entryMeta.entry.getTargetId()+"/groups/represented", "GET", new ArrayList<>(), new ArrayList<>(), null, localVarHeaderParams, new HashMap<>(), new HashMap<>(), new String[]{"authCookie"}, null);
            entryMeta.snapshotTargetUserRepresentedGroup = this.client.<JsonObject>execute(localVarCall, JsonObject.class).getData();
        }
        catch (Exception ex)
        {
            LOG.error("Exception whilst fetching user represented group", ex);
        }
        return lugs;
    }

    public void modalNeedPerms(GroupPermissions perms)
    {
        this.scarlet.ui.submitModalAsync(
            null,
            "The bot VRChat account `"+this.currentUserId+"` is missing the necessary permission `"+perms.getValue()+"`",
            "Missing permissions",
            () -> MiscUtils.AWTDesktop.browse(URI.create("https://vrchat.com/home/group/"+this.groupId+"/settings")),
            null);
    }
    public String messageNeedPerms(GroupPermissions perms)
    {
        return String.format("The [bot VRChat account](<https://vrchat.com/home/user/%s>) is missing the [necessary permission `%s`](<https://vrchat.com/home/group/%s/settings>)", this.currentUserId, perms.getValue(), this.groupId);
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
