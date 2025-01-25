package net.sybyline.scarlet;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

import io.github.vrchatapi.ApiCallback;
import io.github.vrchatapi.ApiClient;
import io.github.vrchatapi.ApiException;
import io.github.vrchatapi.Configuration;
import io.github.vrchatapi.JSON;
import io.github.vrchatapi.ProgressResponseBody;
import io.github.vrchatapi.api.AuthenticationApi;
import io.github.vrchatapi.api.GroupsApi;
import io.github.vrchatapi.api.UsersApi;
import io.github.vrchatapi.model.GroupAuditLogEntry;
import io.github.vrchatapi.model.PaginatedGroupAuditLogEntryList;
import io.github.vrchatapi.model.TwoFactorAuthCode;
import io.github.vrchatapi.model.User;

import net.sybyline.scarlet.util.LRUMap_1024;
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
        this.cachedUsers = Collections.synchronizedMap(new LRUMap_1024<>());
    }

    final Scarlet scarlet;
    final ScarletVRChatCookieJar cookies;
    final ApiClient client;
    final String groupId;
    final Map<String, User> cachedUsers;

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

    public void login() throws ApiException
    {
        try
        {
            AuthenticationApi auth = new AuthenticationApi(this.client);
            try
            {
                if (auth.verifyAuthToken().getOk().booleanValue())
                {
                    LOG.info("Logged in (cached-verified)");
                    return;
                }
                else
                {
                    LOG.info("Auth declared invalid");
                }
            }
            catch (Exception ex)
            {
                LOG.info("Auth discovered invalid", ex);
            }
            JsonObject data;
            try
            {
                data = this.client.<JsonObject>execute(auth.getCurrentUserCall(null), JsonObject.class).getData();
                if (data.has("id"))
                {
                    LOG.info("Logged in (cached)");
                    return;
                }
            }
            catch (Exception ex)
            {
                LOG.info("Cached auth not valid", ex);
                try
                {
                    this.client.setUsername(this.scarlet.settings.getStringOrRequireInput("vrc_username", "VRChat Username", false));
                    this.client.setPassword(this.scarlet.settings.getStringOrRequireInput("vrc_password", "VRChat Password", true));
                    data = this.client.<JsonObject>execute(auth.getCurrentUserCall(null), JsonObject.class).getData();
                    if (data.has("id"))
                    {
                        LOG.info("Logged in (credentials)");
                        return;
                    }
                }
                finally
                {
                    this.client.setUsername(null);
                    this.client.setPassword(null);
                }
            }
            
            if (data.get("requiresTwoFactorAuth")
                    .getAsJsonArray()
                    .asList()
                    .stream()
                    .map(JsonElement::getAsJsonPrimitive)
                    .map(JsonPrimitive::getAsString)
                    .noneMatch("totp"::equals))
                throw new RuntimeException("Totp-based 2fa is not enabled for this account");
            
            while (!auth.verify2FA(new TwoFactorAuthCode().code(this.scarlet.settings.requireInput("Totp code", true))).getVerified().booleanValue())
                LOG.error("Invalid totp code");
            
            LOG.info("Logged in (2fa)");
        }
        finally
        {
            this.save();
        }
    }

    public boolean logout()
    {
        try
        {
            AuthenticationApi auth = new AuthenticationApi(this.client);
            LOG.info("Log out: "+auth.logout().getSuccess().getMessage());
            return true;
        }
        catch (Exception ex)
        {
            LOG.info("Error logging out", ex);
            return false;
        }
    }

    public void refresh() throws ApiException
    {
        this.logout();
        this.login();
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
            LOG.error("Error during audit query: "+apiex.getMessage());
            return null;
        }
    }

    public User getUser(String userId)
    {
        User user = this.cachedUsers.get(userId);
        if (user != null)
            return user;
        UsersApi users = new UsersApi(this.client);
        try
        {
            user = users.getUser(userId);
            this.cachedUsers.put(userId, user);
            return user;
        }
        catch (ApiException apiex)
        {
            LOG.error("Error during get user: "+apiex.getMessage());
            return null;
        }
    }

    public void save()
    {
        try
        {
            this.cookies.save();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException
    {
        this.cookies.close();
    }

}
