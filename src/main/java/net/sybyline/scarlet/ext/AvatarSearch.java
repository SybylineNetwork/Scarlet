package net.sybyline.scarlet.ext;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.JsonAdapters;
import net.sybyline.scarlet.util.LRUMap;
import net.sybyline.scarlet.util.URLs;

public interface AvatarSearch
{

    int SEARCH_N = 5000;
    String
        URL_ROOT_AVATARRECOVERY = "https://api.avatarrecovery.com/Avatar/vrcx",
        URL_ROOT_AVTRDB = AvatarSearch_AvtrDB.API_ROOT+"/avatar/search/vrcx",
        URL_ROOT_NEKOSUNEVR = AvatarSearch_VRCDS.API_ROOT+"/vrcx_search",
        URL_ROOT_VRCDB = "https://vrcx.vrcdb.com/avatars/Avatar/VRCX",
        URL_ROOT_WORLDBALANCER = AvatarSearch_WorldBalancer.API_ROOT+"/vrcx_search.php",
        URL_ROOTS[] =
        {
            URL_ROOT_AVATARRECOVERY,
            URL_ROOT_AVTRDB,
            URL_ROOT_NEKOSUNEVR,
            URL_ROOT_VRCDB,
            URL_ROOT_WORLDBALANCER,
        };

    class VrcxAvatar
    {
        static final Map<String, Map<String, VrcxAvatar[]>> searchCacheByUrlRoot = new ConcurrentHashMap<>();
        
        @SerializedName(value = "id", alternate = { "avatarId", "avatar_id", "vrcId", "vrc_id" })
        public String id;
        public String id() { return this.id; }
        
        @SerializedName(value = "name", alternate = { "display", "displayName", "display_name", "avatarName", "avatar_name", "avatarDisplay", "avatar_display", "avatarDisplayName", "avatar_display_name" })
        public String name;
        public String name() { return this.name; }
        
        @SerializedName(value = "authorId", alternate = { "author_id" }) @Nullable
        public String authorId;
        public String authorId() { return this.authorId; }
        
        @SerializedName(value = "authorName", alternate = { "author_name", "authorDisplay", "author_display", "authorDisplayName", "author_display_name" })
        public String authorName;
        public String authorName() { return this.authorName; }
        
        @SerializedName(value = "description", alternate = { "desc", "avatarDesc", "avatar_desc", "avatarDescription", "avatar_description" }) @Nullable
        public String description;
        public String description() { return this.description; }
        
        @SerializedName(value = "imageUrl", alternate = { "image_url", "image", "avatarImage", "avatar_image", "avatarImageUrl", "avatar_image_url" })
        public String imageUrl;
        public String imageUrl() { return this.imageUrl; }
        
        @SerializedName(value = "thumbnailImageUrl", alternate = { "thumbnail_image_url", "thumbnailImage", "thumbnail_image", "avatarThumbnail", "avatar_thumbnail", "avatarThumbnailImage", "avatar_thumbnail_image", "avatarThumbnailUrl", "avatar_thumbnail_url", "avatarThumbnailImageUrl", "avatar_thumbnail_image_url" }) @Nullable 
        public String thumbnailImageUrl;
        public String thumbnailImageUrl() { return this.thumbnailImageUrl; }
        
        @SerializedName(value = "releaseStatus", alternate = { "release_status", "release", "status", "avatarRelease", "avatar_release", "avatarStatus", "avatar_status", "avatarReleaseStatus", "avatar_release_status" }) @Nullable
        public String releaseStatus;
        public String releaseStatus() { return this.releaseStatus; }
        
        @SerializedName(value = "created_at", alternate = { "createdAt", "created", "avatarCreated", "avatar_created", "avatarCreatedAt", "avatar_created_at" }) @Nullable
        public OffsetDateTime createdAt;
        public OffsetDateTime createdAt() { return this.createdAt; }
        
        @SerializedName(value = "updated_at", alternate = { "updatedAt", "updated", "avatarUpdated", "avatar_updated", "avatarUpdatedAt", "avatar_updated_at" }) @Nullable
        public OffsetDateTime updatedAt;
        public OffsetDateTime updatedAt() { return this.updatedAt; }
        
        @SerializedName(value = "performance", alternate = { "perf", "avatarPerf", "avatar_perf", "avatarPerformance", "avatar_performance" }) @Nullable
        public Performance performance;
        public Performance performance() { return this.performance; }
        public static class Performance
        {
            @SerializedName(value = "pc_rating", alternate = { "pcRating", "pc" }) @Nullable
            public String pcRating;
            public String pcRating() { return this.pcRating; }
            
            @SerializedName(value = "android_rating", alternate = { "quest_rating", "androidRating", "questRating", "android", "quest" }) @Nullable
            public String androidRating;
            public String androidRating() { return this.androidRating; }
            
            @SerializedName(value = "ios_rating", alternate = { "iosRating", "ios" }) @Nullable
            public String iosRating;
            public String iosRating() { return this.iosRating; }
            
            @SerializedName(value = "has_impostor", alternate = { "hasImpostor", "impostor" })
            public boolean hasImpostor;
            public boolean hasImpostor() { return this.hasImpostor; }
            
            @SerializedName(value = "has_security_variant", alternate = { "hasSecurityVariant", "securityVariant", "security_variant", "security", "hasSecurity", "has_security" }) @Nullable
            public Boolean hasSecurityVariant;
            public Boolean hasSecurityVariant() { return this.hasSecurityVariant; }
        }
        
        public VrcxAvatar merge(VrcxAvatar found)
        {
            if (found == null)
                return this;
            if (!Objects.equals(this.id, found.id))
                return this;
            
            if (this.authorId == null)
                this.authorId = found.authorId;
            
            if (this.authorName == null)
                this.authorName = found.authorName;
            
            if (this.description == null)
                this.description = found.description;
            
            if (this.imageUrl == null)
                this.imageUrl = found.imageUrl;
            
            if (this.thumbnailImageUrl == null)
                this.thumbnailImageUrl = found.thumbnailImageUrl;
            
            if (this.createdAt == null)
                this.createdAt = found.createdAt;
            
            if (this.updatedAt == null)
                this.updatedAt = found.updatedAt;
            
            if (this.releaseStatus == null)
                this.releaseStatus = found.releaseStatus;
            
            if (this.performance == null)
                this.performance = found.performance;
            else if (found.performance != null)
            {
                if (this.performance.pcRating == null)
                    this.performance.pcRating = found.performance.pcRating;
                
                if (this.performance.androidRating == null)
                    this.performance.androidRating = found.performance.androidRating;
                
                if (this.performance.iosRating == null)
                    this.performance.iosRating = found.performance.iosRating;
                
                this.performance.hasImpostor |= found.performance.hasImpostor;
                
                if (this.performance.hasSecurityVariant == null)
                    this.performance.hasSecurityVariant = found.performance.hasSecurityVariant;
            }
            
            return this;
        }
        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.id);
        }
        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof VrcxAvatar && Objects.equals(this.id, ((VrcxAvatar)obj).id);
        }
        private String toString;
        @Override
        public String toString()
        {
            String toString = this.toString;
            if (toString == null)
            {
                StringBuilder sb = new StringBuilder();
                
                sb.append(this.name).append(" (").append(this.id)
                .append(") by ").append(this.authorName);
                if (this.authorId != null)
                {
                    sb.append(" (").append(this.authorId).append(")");
                }
                
                if (this.description != null && !this.description.trim().isEmpty())
                {
                    sb.append(": ").append(this.description);
                }
                
                this.toString = toString = sb.toString();
            }
            return toString;
        }
    }
    public static class VrcxAvatarTypeAdapter extends TypeAdapter<VrcxAvatar>
    {
        @Override
        public void write(JsonWriter out, VrcxAvatar value) throws IOException
        {
            if (value == null)
            {
                out.nullValue();
                return;
            }
            out.beginObject();
            {
                out.endObject();
            }
        }
        @Override
        public VrcxAvatar read(JsonReader in) throws IOException
        {
            // handles nulls implicitly
            if (in.peek() != JsonToken.BEGIN_OBJECT)
            {
                in.skipValue();
                return null;
            }
            in.beginObject();
            VrcxAvatar ret = new VrcxAvatar();
            while (in.peek() != JsonToken.END_OBJECT)
            {
                String prop = in.nextName();
                switch (prop.toLowerCase().replace("_", ""))
                {
                case "id":
                case "avatarid":
                case "vrcid": {
                    ret.id = in.nextString();
                } break;
                case "name":
                case "display":
                case "displayname":
                case "avatarname":
                case "avatardisplay":
                case "avatardisplayname": {
                    ret.name = in.nextString();
                } break;
                case "userid":
                case "authorid":{
                    ret.authorId = in.nextString();
                } break;
                case "username":
                case "userdisplay":
                case "userdisplayname":
                case "authorname":
                case "authordisplay":
                case "authordisplayname": {
                    ret.authorName = in.nextString();
                } break;
                case "user":
                case "author": {
                    if (in.peek() == JsonToken.BEGIN_OBJECT)
                    {
                        in.beginObject();
                        while (in.peek() != JsonToken.END_OBJECT)
                        {
                            String authorProp = in.nextName();
                            switch (authorProp.toLowerCase().replace("_", ""))
                            {
                            case "id":
                            case "vrcid":
                            case "userid":
                            case "authorid": {
                                ret.authorId = in.nextString();
                            } break;
                            case "name":
                            case "display":
                            case "displayname":
                            case "username":
                            case "userdisplay":
                            case "userdisplayname":
                            case "authorname":
                            case "authordisplay":
                            case "authordisplayname": {
                                ret.authorName = in.nextString();
                            } break;
                            default: {
                                in.skipValue();
                            }
                            }
                        }
                        in.endObject();
                    }
                    else if (in.peek() == JsonToken.STRING)
                    {
                        ret.authorName = in.nextString();
                    }
                } break;
                case "desc":
                case "description":
                case "avatardesc":
                case "avatardescription": {
                    ret.description = in.nextString();
                } break;
                case "image":
                case "imageurl":
                case "avatarimage":
                case "avatarimageurl": {
                    ret.imageUrl = in.nextString();
                } break;
                case "thumbnail":
                case "thumbnailimage":
                case "thumbnailurl":
                case "thumbnailimageurl":
                case "avatarthumbnail":
                case "avatarthumbnailimage":
                case "avatarthumbnailurl":
                case "avatarthumbnailimageurl": {
                    ret.imageUrl = in.nextString();
                } break;
                case "created":
                case "createdat":
                case "avatarcreated":
                case "avatarcreatedat": {
                    ret.createdAt = JsonAdapters.json2offsetDateTime(in.nextString());
                } break;
                case "updated":
                case "updatedat":
                case "avatarupdated":
                case "avatarupdatedat": {
                    ret.updatedAt = JsonAdapters.json2offsetDateTime(in.nextString());
                } break;
                case "release":
                case "status":
                case "releasestatus":
                case "avatarrelease":
                case "avatarstatus":
                case "avatarreleasestatus": {
                    ret.releaseStatus = in.nextString();
                } break;
                case "performance":
                case "perf":
                case "avatarperformance":
                case "avatarperf": {
                    if (in.peek() == JsonToken.BEGIN_OBJECT)
                    {
                        in.beginObject();
                        if (ret.performance == null)
                            ret.performance = new VrcxAvatar.Performance();
                        while (in.peek() != JsonToken.END_OBJECT)
                        {
                            String authorProp = in.nextName();
                            switch (authorProp.toLowerCase().replace("_", ""))
                            {
                            case "pc":
                            case "pcrating": {
                                ret.performance.pcRating = in.nextString();
                            } break;
                            case "android":
                            case "androidrating":
                            case "quest":
                            case "questrating": {
                                ret.performance.androidRating = in.nextString();
                            } break;
                            case "ios":
                            case "iosrating": {
                                ret.performance.pcRating = in.nextString();
                            } break;
                            case "hasimpostor":
                            case "impostor": {
                                if (in.peek() == JsonToken.BOOLEAN)
                                {
                                    ret.performance.hasImpostor = in.nextBoolean();
                                }
                                else if (in.peek() == JsonToken.STRING)
                                {
                                    String hasImpostor = in.nextString();
                                    if (!"null".equalsIgnoreCase(hasImpostor) && !"none".equalsIgnoreCase(hasImpostor))
                                    {
                                        ret.performance.hasImpostor = !hasImpostor.isEmpty() && !"false".equalsIgnoreCase(hasImpostor);
                                    }
                                }
                                else
                                {
                                    in.skipValue();
                                }
                            } break;
                            case "hassecurityvariant":
                            case "securityvariant":
                            case "hassecurity":
                            case "security": {
                                if (in.peek() == JsonToken.BOOLEAN)
                                {
                                    ret.performance.hasSecurityVariant = in.nextBoolean();
                                }
                                else if (in.peek() == JsonToken.STRING)
                                {
                                    String hasSecurityVariant = in.nextString();
                                    if (!"null".equalsIgnoreCase(hasSecurityVariant) && !"none".equalsIgnoreCase(hasSecurityVariant))
                                    {
                                        ret.performance.hasSecurityVariant = !hasSecurityVariant.isEmpty() && !"false".equalsIgnoreCase(hasSecurityVariant);
                                    }
                                }
                                else
                                {
                                    in.skipValue();
                                }
                            } break;
                            default: {
                                in.skipValue();
                            }
                            }
                        }
                        in.endObject();
                    }
                    else if (in.peek() == JsonToken.STRING)
                    {
                        ret.authorName = in.nextString();
                    }
                } break;
                default: {
                    in.skipValue();
                }
                }
            }
            in.endObject();
            return ret;
        }
    }

    static VrcxAvatar[] vrcxSearch0(String urlRoot, int n, String search)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get(urlRoot + "?n=" + Integer.toUnsignedString(n) + "&search=" + URLs.encode(search)))
        {
            return in.readAsJson(null, null, VrcxAvatar[].class);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return null;
        }
    }
    static VrcxAvatar[] vrcxFindInCache(String urlRoot, String search)
    {
        return VrcxAvatar.searchCacheByUrlRoot.getOrDefault(urlRoot, Collections.emptyMap()).get(search);
    }
    static VrcxAvatar[] vrcxPutInCache(String urlRoot, String search, VrcxAvatar[] results)
    {
        VrcxAvatar.searchCacheByUrlRoot.computeIfAbsent(urlRoot, urlRoot0 -> LRUMap.ofSynchronized()).put(search, results);
        return results;
    }
    static VrcxAvatar[] vrcxSearchCached(String urlRoot, String search)
    {
        return vrcxSearchCached(urlRoot, SEARCH_N, search);
    }
    static VrcxAvatar[] vrcxSearchCached(String urlRoot, int n, String search)
    {
        Map<String, VrcxAvatar[]> urlCache;
        synchronized (VrcxAvatar.searchCacheByUrlRoot) {
            urlCache = VrcxAvatar.searchCacheByUrlRoot.computeIfAbsent(urlRoot, urlRoot0 -> LRUMap.ofSynchronized());
        }
        
        synchronized (urlCache) {
            VrcxAvatar[] cached = urlCache.get(search);
            if (cached == null) {
                cached = AvatarSearch.vrcxSearch(urlRoot, n, search);
                if (cached != null) {
                    urlCache.put(search, cached);
                }
            }
            return cached;
        }
    }
    static VrcxAvatar[] vrcxSearch(String urlRoot, String search)
    {
        return vrcxSearch(urlRoot, SEARCH_N, search);
    }
    static VrcxAvatar[] vrcxSearch(String urlRoot, int n, String search)
    {
        VrcxAvatar[] results = vrcxSearch0(urlRoot, n, search);
        if (n == SEARCH_N)
            vrcxPutInCache(urlRoot, search, results);
        return results;
    }
    static Stream<VrcxAvatar> vrcxSearchAllCached(String search)
    {
        return vrcxSearchAllCached(URL_ROOTS, search);
    }
    static Stream<VrcxAvatar> vrcxSearchAllCached(String[] urlRoots, String search)
    {
        return Arrays.stream(urlRoots)
            .filter(Objects::nonNull)
            .map($ -> vrcxSearchCached($, SEARCH_N, search))
            .filter(Objects::nonNull)
            .flatMap(Arrays::stream)
            .filter(Objects::nonNull)
        ;
    }
    static Stream<VrcxAvatar> vrcxSearchAll(String search)
    {
        return vrcxSearchAll(URL_ROOTS, search);
    }
    static Stream<VrcxAvatar> vrcxSearchAll(String[] urlRoots, String search)
    {
        return Arrays.stream(urlRoots)
            .filter(Objects::nonNull)
            .map($ -> vrcxSearch($, SEARCH_N, search))
            .filter(Objects::nonNull)
            .flatMap(Arrays::stream)
            .filter(Objects::nonNull)
        ;
    }

}
