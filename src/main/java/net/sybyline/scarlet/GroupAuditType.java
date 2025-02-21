package net.sybyline.scarlet;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.model.GroupAccessType;
import io.github.vrchatapi.model.GroupPostVisibility;

public enum GroupAuditType
{
    INSTANCE_CLOSE      ("group.instance.close"      , "Instance Close"      , 0x00_FF0000), // InstanceComponent
    INSTANCE_CREATE     ("group.instance.create"     , "Instance Create"     , 0x00_00FF00), // InstanceComponent
    /** If the kick is issued via majority vote: actorId:"vrc_admin", actorDisplayName:"VRChat Admin" **/
    INSTANCE_KICK       ("group.instance.kick"       , "Instance Kick"       , 0x00_FF7F00), // LocationComponent
//    INSTANCE_MUTE       ("group.instance.mute"       , "Instance Mute"       , 0x00_FF007F), // ? LocationComponent
    INSTANCE_WARN       ("group.instance.warn"       , "Instance Warn"       , 0x00_FFFF00), // LocationComponent
    INVITE_CREATE       ("group.invite.create"       , "Invite Create"       , 0x00_00FF00), // <empty object>
//    INVITE_CANCEL       ("group.invite.cancel"       , "Invite Cancel"       , 0x00_000000), // ?
    MEMBER_JOIN         ("group.member.join"         , "Member Join"         , 0x00_00FF00), // <empty object>
    MEMBER_LEAVE        ("group.member.leave"        , "Member Leave"        , 0x00_FF007F), // <empty object>
    MEMBER_REMOVE       ("group.member.remove"       , "Member Remove"       , 0x00_FF0000), // <empty object>
    /** If the role is assigned via economy purchase: actorId:"vrc_system", actorDisplayName:"VRChat System" **/
    MEMBER_ROLE_ASSIGN  ("group.member.role.assign"  , "Member Role Assign"  , 0x00_00FF7F), // RoleRefComponent
    MEMBER_ROLE_UNASSIGN("group.member.role.unassign", "Member Role Unassign", 0x00_FF007F), // RoleRefComponent
//    MEMBER_UPDATE       ("group.member.update"       , "Member Update"       , 0x00_007F7F), // ? Map<String, UpdateSubComponent>
    MEMBER_USER_UPDATE  ("group.member.user.update"  , "Member User Update"  , 0x00_007F7F), // Map<String, UpdateSubComponent>
//    ANNOUNCEMENT        ("group.announcement"        , "Announcement"        , 0x00_000000), // ?
    POST_CREATE         ("group.post.create"         , "Post Create"         , 0x00_00FF7F), // PostCreateComponent
    POST_DELETE         ("group.post.delete"         , "Post Delete"         , 0x00_FF0000), // PostDeleteComponent
//    POST_UPDATE         ("group.post.update"         , "Post Update"         , 0x00_FF0000), // ? Map<String, UpdateSubComponent>
    REQUEST_BLOCK       ("group.request.block"       , "Request Block"       , 0x00_FF0000), // <empty object>
    REQUEST_CREATE      ("group.request.create"      , "Request Create"      , 0x00_007F00), // <empty object>
    REQUEST_REJECT      ("group.request.reject"      , "Request Reject"      , 0x00_FF007F), // <empty object>
    ROLE_CREATE         ("group.role.create"         , "Role Create"         , 0x00_00FF00), // RoleCreateComponent
    ROLE_DELETE         ("group.role.delete"         , "Role Delete"         , 0x00_FF0000), // RoleDeleteComponent
    ROLE_UPDATE         ("group.role.update"         , "Role Update"         , 0x00_007F7F), // Map<String, UpdateSubComponent>
//    GALLERY_CREATE      ("group.gallery.create"      , "Gallery Create"      , 0x00_000000), // ?
//    GALLERY_DELETE      ("group.gallery.delete"      , "Gallery Delete"      , 0x00_000000), // ?
//    GALLERY_UPDATE      ("group.gallery.update"      , "Gallery Update"      , 0x00_000000), // ?
    TRANSFER_ACCEPT     ("group.transfer.accept"     , "Transfer Accept"     , 0x00_0000FF), // TransferAcceptComponent
    TRANSFER_START      ("group.transfer.start"      , "Transfer Start"      , 0x00_00FF00), // TransferStartComponent
//    CREATE              ("group.create"              , "Create"              , 0x00_000000), // ?
    UPDATE              ("group.update"              , "Update"              , 0x00_007F7F), // Map<String, UpdateSubComponent>
    USER_BAN            ("group.user.ban"            , "User Ban"            , 0x00_FF0000), // <empty object>
    USER_UNBAN          ("group.user.unban"          , "User Unban"          , 0x00_007F00), // <empty object>
    ;

    GroupAuditType(String id, String title, int color)
    {
        this.id = id;
        this.title = title;
        this.color = color;
    }

    public final String id, title;
    public final int color;

    public String id()
    {
        return this.id;
    }

    public String title()
    {
        return this.title;
    }

    public int color()
    {
        return this.color;
    }

    static final Map<String, GroupAuditType> BY_ID = new HashMap<>();
    static
    {
        for (GroupAuditType type : values())
            BY_ID.put(type.id, type);
    }

    public static GroupAuditType of(String id)
    {
        return id == null ? null : BY_ID.get(id.toLowerCase());
    }

    public static int color(Map<String, Integer> overrides, String id)
    {
        Integer override = overrides.get(id);
        return override != null ? override.intValue() : color(id);
    }

    public static int color(String id)
    {
        GroupAuditType gat = of(id);
        if (gat != null)
            return gat.color;
        return 0x00_000000;
    }

    public static String title(String id)
    {
        GroupAuditType gat = of(id);
        if (gat != null)
            return gat.title;
        String title = id.toLowerCase();
        if (title.startsWith("group."))
            title = title.substring(6);
        title = title.replace('.', ' ');
        title = simpleTitleCase(title);
        return title;
    }

    public static class InstanceComponent
    {
        public GroupAccessType groupAccessType;
        public List<String> roleIds;
    }
    public static class LocationComponent
    {
        public String location;
    }
    public static class RoleRefComponent
    {
        public boolean isPurchase; // Only present on economy-purchased roles
        public String roleId;
        public String roleName;
    }
    public static class PostBaseComponent
    {
        public String authorId;
        public String imageId;
        public List<String> roleIds;
        public String text;
        public String title;
        public GroupPostVisibility visibility;
    }
    public static class PostCreateComponent extends PostBaseComponent
    {
        public boolean sendNotification;
    }
    public static class PostDeleteComponent extends PostBaseComponent
    {
        public OffsetDateTime createdAt;
        public String editorId;
        public String imageUrl;
        public OffsetDateTime updatedAt;
    }
    public static class RoleBaseComponent
    {
        public String description;
        public boolean isAddedOnJoin;
        public boolean isSelfAssignable;
        public String name;
        public List<String> permissions;
        public boolean requiresPurchase;
        public boolean requiresTwoFactor;
    }
    public static class RoleCreateComponent extends RoleBaseComponent
    {
        public String groupId;
    }
    public static class RoleDeleteComponent extends RoleBaseComponent
    {
        public boolean defaultRole;
        public boolean isManagementRole;
        public int order;
    }
    public static class TransferStartComponent
    {
        public String transferTargetId;
    }
    public static class TransferAcceptComponent
    {
        public String oldOwnerId;
    }
    public static class UpdateSubComponent
    {
        public static final TypeToken<Map<String, UpdateSubComponent>> TYPE_TOKEN = new TypeToken<Map<String, UpdateSubComponent>>(){};
        @SerializedName("new")
        public JsonElement newValue;
        @SerializedName("old")
        public JsonElement oldValue;
    }

    static String simpleTitleCase(CharSequence chars)
    {
        int length = chars.length();
        char[] ret = new char[length];
        boolean nextTitle = true;
        for (int index = 0; index < length; index++)
        {
            char ch = chars.charAt(index);
            if (Character.isWhitespace(ch))
                nextTitle = true;
            else
            {
                ch = nextTitle ? Character.toTitleCase(ch) : Character.toLowerCase(ch);
                nextTitle = false;
            }
            ret[index] = ch;
        }
        return ret.toString();
    }

}
