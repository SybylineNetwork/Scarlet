package net.sybyline.scarlet.ext;

import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import net.sybyline.scarlet.Scarlet;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.URLs;

public interface AvatarSearch
{

    int SEARCH_N = 5000;
    String
        URL_ROOT_AVATARRECOVERY = "https://api.avatarrecovery.com/Avatar/vrcx",
        URL_ROOT_AVTRDB = "https://api.avtrdb.com/v2/avatar/search/vrcx",
        URL_ROOT_NEKOSUNEVR = "https://avtr.nekosunevr.co.uk/vrcx_search.php",
        URL_ROOT_VRCDB = "https://vrcx.vrcdb.com/avatars/Avatar/VRCX",
        URL_ROOT_WORLDBALANCER = "https://avatarwb.worldbalancer.duia.us/vrcx_search.php",
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
        @SerializedName(value = "id", alternate = { "avatarId", "avatar_id", "vrc_id" })
        public String id;
        public String id() { return this.id; }
        
        @SerializedName(value = "name", alternate = { "avatarName", "avatar_name" })
        public String name;
        public String name() { return this.name; }
        
        @SerializedName(value = "authorId", alternate = { "authod_id" }) @Nullable
        public String authorId;
        public String authorId() { return this.authorId; }
        
        @SerializedName(value = "authorName", alternate = { "author_name" })
        public String authorName;
        public String authorName() { return this.authorName; }
        
        @SerializedName(value = "description", alternate = { "avatarDescription", "avatar_description" }) @Nullable
        public String description;
        public String description() { return this.description; }
        
        @SerializedName(value = "imageUrl", alternate = { "image_url" })
        public String imageUrl;
        public String imageUrl() { return this.imageUrl; }
        
        @SerializedName(value = "thumbnailImageUrl", alternate = { "thumbnail_image_url" }) @Nullable 
        public String thumbnailImageUrl;
        public String thumbnailImageUrl() { return this.thumbnailImageUrl; }
        
        @SerializedName(value = "releaseStatus", alternate = { "release_status" }) @Nullable
        public String releaseStatus;
        public String releaseStatus() { return this.releaseStatus; }
        
        @SerializedName(value = "created_at", alternate = { "createdAt" }) @Nullable
        public OffsetDateTime createdAt;
        public OffsetDateTime createdAt() { return this.createdAt; }
        
        @SerializedName(value = "updated_at", alternate = { "updatedAt" }) @Nullable
        public OffsetDateTime updatedAt;
        public OffsetDateTime updatedAt() { return this.updatedAt; }
        
        @SerializedName(value = "compatibility", alternate = { }) @Nullable
        public String[] compatibility;
        public String[] compatibility() { return this.compatibility; }
        
        @SerializedName(value = "performance", alternate = { }) @Nullable
        public Performance performance;
        public Performance performance() { return this.performance; }
        public static class Performance
        {
            @SerializedName(value = "pc_rating", alternate = { "pcRating" }) @Nullable
            public String pcRating;
            public String pcRating() { return this.pcRating; }
            
            @SerializedName(value = "android_rating", alternate = { "quest_rating", "androidRating", "questRating" }) @Nullable
            public String androidRating;
            public String androidRating() { return this.androidRating; }
            
            @SerializedName(value = "ios_rating", alternate = { "iosRating" }) @Nullable
            public String iosRating;
            public String iosRating() { return this.iosRating; }
            
            @SerializedName(value = "has_impostor", alternate = { "hasImpostor" })
            public boolean hasImpostor;
            public boolean hasImpostor() { return this.hasImpostor; }
            
            @SerializedName(value = "has_security_variant", alternate = { "hasSecurityVariant" }) @Nullable
            public Boolean hasSecurityVariant;
            public Boolean hasSecurityVariant() { return this.hasSecurityVariant; }
        }
        
        @SerializedName(value = "tags", alternate = { }) @Nullable
        public String[] tags;
        public String[] tags() { return this.tags; }
        
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
            
            if (this.compatibility == null)
                this.compatibility = found.compatibility;
            
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
    static VrcxAvatar[] vrcxSearch(String urlRoot, String search)
    {
        return vrcxSearch0(urlRoot, vrcxQuery(SEARCH_N, search));
    }
    static VrcxAvatar[] vrcxSearch(String urlRoot, int n, String search)
    {
        return vrcxSearch0(urlRoot, vrcxQuery(n, search));
    }
    static String vrcxQuery(int n, String search)
    {
        return "?n=" + Integer.toUnsignedString(n) + "&search=" + URLs.encode(search);
    }
    static VrcxAvatar[] vrcxSearch0(String urlRoot, String query)
    {
        try
        {
            return Scarlet.GSON.fromJson(new InputStreamReader(HttpURLInputStream.get(urlRoot+query)), VrcxAvatar[].class);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return null;
        }
    }
    static Stream<VrcxAvatar> vrcxSearchAll(String search)
    {
        return vrcxSearchAll(URL_ROOTS, search);
    }
    static Stream<VrcxAvatar> vrcxSearchAll(String[] urlRoots, String search)
    {
        String query = vrcxQuery(SEARCH_N, search);
        return Arrays.stream(urlRoots)
            .filter(Objects::nonNull)
            .map($ -> vrcxSearch0($, query))
            .filter(Objects::nonNull)
            .flatMap(Arrays::stream)
            .filter(Objects::nonNull)
        ;
    }

}
