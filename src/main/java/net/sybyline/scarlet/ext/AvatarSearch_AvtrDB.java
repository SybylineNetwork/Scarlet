package net.sybyline.scarlet.ext;

import java.io.IOException;
import java.time.OffsetDateTime;

import javax.annotation.Nullable;

import com.google.gson.JsonPrimitive;

import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.URLs;

/**
 * https://avtrdb.com/faq
 */
public interface AvatarSearch_AvtrDB
{

    public static int MAX_PAGE_SIZE = 50;
    public static final String API_ROOT = "https://api.avtrdb.com/v2";

    public static class StatisticsResponse
    {
        public Monthly[] monthly;
        public static class Monthly extends Total
        {
            public int month;
            public int year;
        }
        public Total total;
        public static class Total
        {
            public long total_avatars;
            public long searchable_count;
            public long deleted_count;
            public long unknown_count;
            public long blacklisted_count;
            public long attributed_count;
        }
    }

    public static class SearchResponse
    {
        public AvtrDBAvatar[] avatars;
        public boolean has_more;
    }
    public static class AvtrDBAvatar
    {
        public String vrc_id;
        public String name;
        public Author author;
        public static class Author
        {
            public String vrc_id;
            public String name;
        }
        public OffsetDateTime created_at;
        public OffsetDateTime updated_at;
        public String description;
        public String[] compatibility;
        @Nullable
        public String image_url;
        public Performance performance;
        public static class Performance
        {
            @Nullable
            public String pc_rating;
            @Nullable
            public String quest_rating;
            // V2 only
            @Nullable
            public String ios_rating;
            public boolean has_impostor;
            // V1 only
            @Nullable
            public Boolean has_security_variant;
        }
        public Tags tags;
        public static class Tags
        {
            @Nullable
            public String[] system_tags;
        }
    }

    public static class RefetchRequest
    {
        public RefetchRequest(String avatar_id, String token)
        {
            this.avatar_id = avatar_id;
            this.token = token;
        }
        public RefetchRequest()
        {
        }
        public String avatar_id;
        public String token;
    }
    public static class RefetchResponse
    {
        public String result;
    }

    static Long index()
    {
        try (HttpURLInputStream in = HttpURLInputStream.get(API_ROOT+"/avatar/index"))
        {
            return in.readAsJson(null, null, JsonPrimitive.class).getAsLong();
        }
        catch (IOException ioex)
        {
            ioex.printStackTrace();
            return null;
        }
    }

    static StatisticsResponse statistics()
    {
        try (HttpURLInputStream in = HttpURLInputStream.get(API_ROOT+"/avatar/statistics"))
        {
            return in.readAsJson(null, null, StatisticsResponse.class);
        }
        catch (IOException ioex)
        {
            ioex.printStackTrace();
            return null;
        }
    }

    static AvtrDBAvatar[] latest(boolean explicit)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get(API_ROOT+"/avatar/latest?explicit="+explicit))
        {
            return in.readAsJson(null, null, AvtrDBAvatar[].class);
        }
        catch (IOException ioex)
        {
            ioex.printStackTrace();
            return null;
        }
    }

    static SearchResponse search(int page_size, int page, String query)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get(API_ROOT+String.format("/avatar/search?page_size=%d&page=%d&query=%s", page_size, page, URLs.encode(query))))
        {
            return in.readAsJson(null, null, SearchResponse.class);
        }
        catch (IOException ioex)
        {
            ioex.printStackTrace();
            return null;
        }
    }

    static RefetchResponse request_refetch( String avatar_id, String token)
    {
        try (HttpURLInputStream in = HttpURLInputStream.post(API_ROOT+"/avatar/request_refetch", HttpURLInputStream.writeAsJson(null, null, RefetchRequest.class, new RefetchRequest(avatar_id, token))))
        {
            return in.readAsJson(null, null, RefetchResponse.class);
        }
        catch (IOException ioex)
        {
            ioex.printStackTrace();
            return null;
        }
    }

}
