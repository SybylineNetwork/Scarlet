package net.sybyline.scarlet.ext;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

import io.github.vrchatapi.JSON;
import io.github.vrchatapi.model.CalendarEvent;
import io.github.vrchatapi.model.FileVersion;
import io.github.vrchatapi.model.ModelFile;

import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.Hypertext;
import net.sybyline.scarlet.util.LRUMap;
import net.sybyline.scarlet.util.VersionedFile;

public interface VrcApiStatic
{

    static ModelFile file(String fileId)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://vrchat.com/api/1/file/"+fileId, ExtendedUserAgent.init_conn))
        {
            return in.readAsJson(null, JSON.getGson(), ModelFile.class);
        }
        catch (Exception ex)
        {
        }
        return null;
    }
    static int latestVersion(ModelFile file)
    {
        return Optional.ofNullable(file)
            .map(ModelFile::getVersions)
            .orElse(Collections.emptySet())
            .stream()
            .map(FileVersion::getVersion)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(VersionedFile.DEFAULT_FILE_VERSION);
    }
    static VersionedFile fileVersionObject(String fileId)
    {
        return VersionedFile.of(fileId, latestVersion(file(fileId)));
    }
    static CalendarEvent event(String groupId, String eventId)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://vrchat.com/api/1/calendar/"+groupId+"/"+eventId, ExtendedUserAgent.init_conn))
        {
            return in.readAsJson(null, JSON.getGson(), CalendarEvent.class);
        }
        catch (Exception ex)
        {
        }
        return null;
    }
    class StaticContent
    {
        static final Map<String, String> groupResolves = LRUMap.ofSynchronized(),
                                         locationSecureNameResolves = LRUMap.ofSynchronized();
        static final Map<String, StaticContent> users = LRUMap.ofSynchronized(),
                                                groups = LRUMap.ofSynchronized(),
                                                worlds = LRUMap.ofSynchronized(),
                                                avatars = LRUMap.ofSynchronized();
        public static enum Kind
        {
            USER("User", "https://vrchat.com/home/user/"),
            AVATAR("Avatar", "https://vrchat.com/home/avatar/"),
            GROUP("Group", "https://vrchat.com/home/group/"),
            WORLD("World", "https://vrchat.com/home/world/"),
            ;
            Kind(String prettyName, String urlHead)
            {
                this.prettyName = prettyName;
                this.urlHead = urlHead;
            }
            public final String prettyName, urlHead;
        }
        protected final Kind kind;
        protected final String id, name, description, ownerName, image;
        public StaticContent(Kind kind, String id, String name, String description, String ownerName, String image)
        {
            if (kind == null)
                throw new IllegalArgumentException("kind == null");
            if (id == null)
                throw new IllegalArgumentException("id == null");
            if (name == null)
                throw new IllegalArgumentException("name == null");
            if (image == null)
                throw new IllegalArgumentException("image == null");
            this.kind = kind;
            this.id = id;
            this.name = name;
            this.description = description;
            this.ownerName = ownerName;
            this.image = image;
        }
        public final   Kind kind       () { return this.kind;        }
        public final String id         () { return this.id;          }
        public final String name       () { return this.name;        }
        public final String description() { return this.description; }
        public final String ownerName  () { return this.ownerName;   }
        public final String image      () { return this.image;       }
        public String computeTitle()
        {
            return this.ownerName == null ? this.name : (this.name + " by " + this.ownerName);
        }
        public String computeInfo()
        {
            return this.kind.prettyName + ": " + this.computeTitle();
        }
        public String computeUrl()
        {
            return this.kind.urlHead + this.id;
        }
        public JsonObject computeJson()
        {
            JsonObject object = new JsonObject();
            object.addProperty("kind", this.kind.name());
            object.addProperty("id", this.id);
            object.addProperty("name", this.name);
            object.addProperty("description", this.description);
            object.addProperty("ownerName", this.ownerName);
            object.addProperty("image", this.image);
            return object;
        }
        @Override
        public int hashCode()
        {
            return this.id.hashCode();
        }
        @Override
        public boolean equals(Object o)
        {
            return o == this || (o instanceof StaticContent && this.id.equals(((StaticContent)o).id));
        }
        @Override
        public String toString()
        {
            return this.computeInfo();
        }
    }
    static StaticContent userCached(String userId)
    {
        return StaticContent.users.computeIfAbsent(userId, VrcApiStatic::user);
    }
    static StaticContent user(String userId)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://vrchat.com/home/user/"+userId, ExtendedUserAgent.init_conn))
        {
            Map<String, String> metas = Hypertext.scrapeMetaNameContent(in.asReader(null));
            String name = metas.get("og:title"),
                   image = metas.get("og:image");
            return new StaticContent(
                StaticContent.Kind.USER,
                userId,
                name,
                null,
                null,
                image
            );
        }
        catch (Exception ex)
        {
        }
        return null;
    }
    // https://vrchat.com/api/1/groups/redirect/SHORT6.DISC/settings
    static StaticContent groupCached(String groupId)
    {
        return StaticContent.groups.computeIfAbsent(groupId, VrcApiStatic::group);
    }
    static StaticContent group(String groupId)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://vrchat.com/home/group/"+groupId, ExtendedUserAgent.init_conn))
        {
            Map<String, String> metas = Hypertext.scrapeMetaNameContent(in.asReader(null));
            String name = metas.get("og:title"),
                   description = metas.get("og:description"),
                   image = metas.get("og:image");
            return new StaticContent(
                StaticContent.Kind.GROUP,
                groupId,
                name,
                description,
                null,
                image
            );
        }
        catch (Exception ex)
        {
        }
        return null;
    }
    static String groupResolveCached(String groupCode)
    {
        return StaticContent.groupResolves.computeIfAbsent(groupCode, VrcApiStatic::groupResolve);
    }
    static String groupResolve(String groupCode)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://api.vrchat.cloud/api/1/groups/redirect/"+groupCode, ExtendedUserAgent.init_conn_disable_redirects))
        {
            String location = in.connection().getHeaderField("location");
            if (location != null && location.startsWith("/home/group/"))
                return location.substring(12);
        }
        catch (Exception ex)
        {
        }
        return null;
    }
    static String locationSecureNameResolveCached(String secureName)
    {
        return StaticContent.locationSecureNameResolves.computeIfAbsent(secureName, VrcApiStatic::locationSecureNameResolve);
    }
    static String locationSecureNameResolve(String secureName)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://vrchat.com/i/"+secureName, ExtendedUserAgent.init_conn_disable_redirects))
        {
            String location = in.connection().getHeaderField("location");
            if (location != null && location.startsWith("/home/launch?worldId="))
                return location.substring(21).replace("&instanceId=", ":").replace("&shortName="+secureName, "");
        }
        catch (Exception ex)
        {
        }
        return null;
    }
    static StaticContent worldCached(String worldId)
    {
        return StaticContent.worlds.computeIfAbsent(worldId, VrcApiStatic::world);
    }
    static final Pattern OG_IMAGE_ALT = Pattern.compile("\\A\\QA preview image of VRChat \\E\\w+\\Q \"\\E(?<name>.+)\\Q\" by \\E(?<ownerName>.+)\\z");
    static StaticContent world(String worldId)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://vrchat.com/home/world/"+worldId, ExtendedUserAgent.init_conn))
        {
            Map<String, String> metas = Hypertext.scrapeMetaNameContent(in.asReader(null));
            Matcher matcher = OG_IMAGE_ALT.matcher(metas.get("og:image:alt"));
            String name, ownerName, description, image;
            if (matcher.matches())
            {
                name = matcher.group("name");
                ownerName = matcher.group("ownerName");
            }
            else
            {
                String ogtitlea[] = metas.get("og:title").split("\\s*by\\s*");
                name = ogtitlea[0].trim();
                ownerName = ogtitlea[1].trim();
            }
            description = metas.get("og:description");
            image = metas.get("og:image");
            return new StaticContent(
                StaticContent.Kind.WORLD,
                worldId,
                name,
                description,
                ownerName,
                image
            );
        }
        catch (Exception ex)
        {
        }
        return null;
    }
    static StaticContent avatarCached(String avatarId)
    {
        return StaticContent.avatars.computeIfAbsent(avatarId, VrcApiStatic::avatar);
    }
    static StaticContent avatar(String avatarId)
    {
        try (HttpURLInputStream in = HttpURLInputStream.get("https://vrchat.com/home/avatar/"+avatarId, ExtendedUserAgent.init_conn))
        {
            Map<String, String> metas = Hypertext.scrapeMetaNameContent(in.asReader(null));
            Matcher matcher = OG_IMAGE_ALT.matcher(metas.get("og:image:alt"));
            String name, description, ownerName, image;
            if (matcher.matches())
            {
                name = matcher.group("name");
                ownerName = matcher.group("ownerName");
            }
            else
            {
                String ogtitlea[] = metas.get("og:title").split("\\s*by\\s*");
                name = ogtitlea[0].trim();
                ownerName = ogtitlea[1].trim();
            }
            description = metas.get("og:description");
            image = metas.get("og:image");
            return new StaticContent(
                StaticContent.Kind.AVATAR,
                avatarId,
                name,
                description,
                ownerName,
                image
            );
        }
        catch (Exception ex)
        {
        }
        return null;
    }
}
