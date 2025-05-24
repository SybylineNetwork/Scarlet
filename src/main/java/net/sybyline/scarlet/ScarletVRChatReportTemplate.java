package net.sybyline.scarlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import io.github.vrchatapi.model.Group;
import io.github.vrchatapi.model.User;
import io.github.vrchatapi.model.World;

import net.sybyline.scarlet.util.Location;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.TemplateStrings;
import net.sybyline.scarlet.util.VRChatHelpDeskURLs;

public class ScarletVRChatReportTemplate
{

    public ScarletVRChatReportTemplate(File templateFile)
    {
        this.templateFile = templateFile;
        this.lastModified = 0L;
        this.contents = "";
        this.load();
    }

    private final File templateFile;
    private long lastModified;
    private String contents;

    private void load()
    {
        if (this.templateFile.isFile()) try (BufferedReader in = new BufferedReader(MiscUtils.reader(this.templateFile)))
        {
            this.contents = in.lines().collect(Collectors.joining("\n"));
            this.lastModified = this.templateFile.lastModified();
        }
        catch (IOException ioex)
        {
            ScarletVRChat.LOG.error("Exception loading template", ioex);
        }
        else try
        {
            this.templateFile.getParentFile().mkdirs();
            this.templateFile.createNewFile();
            this.lastModified = this.templateFile.lastModified();
        }
        catch (IOException ioex)
        {
            ScarletVRChat.LOG.error("Exception creating template", ioex);
        }
    }

    private synchronized String check()
    {
        if (this.lastModified != this.templateFile.lastModified())
        {
            this.load();
        }
        return this.contents;
    }

    public static final String MISSING = "???????";
    public static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy'-'MM'-'dd HH':'mm':'ss 'UTC'");
    public class FormatParams
    {
        public String groupId = MISSING, groupName = MISSING, groupUrl = MISSING, groupCode = MISSING, groupCodeUrl = MISSING;
        public String worldId = MISSING, worldName = MISSING, worldUrl = MISSING;
        public String location = MISSING;
        public String instanceType = MISSING;
        public String actorId = MISSING, actorName = MISSING, actorUrl = MISSING;
        public String targetId = MISSING, targetName = MISSING, targetUrl = MISSING;
        public String targetJoined = MISSING, targetLeft = MISSING;
        public String tags = MISSING;
        public String description = MISSING;
        public String auditId = MISSING;
        public String appName = Scarlet.NAME;
        public String appVersion = Scarlet.VERSION;
        public FormatParams group(ScarletVRChat vrc, String groupId, Group group)
        {
            this.groupId = groupId;
            this.groupUrl = "https://vrchat.com/home/group/"+groupId;
            if (group == null && vrc != null)
                group = vrc.getGroup(groupId);
            if (group != null)
            {
                this.groupName = group.getName();
                this.groupCode = group.getShortCode()+"."+group.getDiscriminator();
                this.groupCodeUrl = "https://vrc.group/"+this.groupCode;
            }
            return this;
        }
        public FormatParams world(ScarletVRChat vrc, String worldId, World world)
        {
            this.worldId = worldId;
            this.worldUrl = "https://vrchat.com/home/world/"+worldId;
            if (world == null && vrc != null)
                world = vrc.getWorld(worldId);
            this.worldName = world == null ? worldId :  world.getName();
            return this;
        }
        public FormatParams location(ScarletVRChat vrc, String location)
        {
            this.location = location;
            Location locationModel = Location.of(location);
            if (locationModel.isConcrete())
            {
                this.instanceType = (locationModel.ageGate ? "18+ " : "") + locationModel.type.display;
                this.world(vrc, locationModel.world, null);
            }
            return this;
        }
        public FormatParams actor(ScarletVRChat vrc, String actorId, String actorDisplayName)
        {
            this.actorId = actorId;
            this.actorUrl = "https://vrchat.com/home/user/"+actorId;
            if (actorDisplayName == null && vrc != null)
            {
                User actor = vrc.getUser(actorId);
                actorDisplayName = actor == null ? actorId : actor.getDisplayName();
            }
            this.actorName = actorDisplayName;
            return this;
        }
        public FormatParams target(ScarletVRChat vrc, String targetId, String targetDisplayName)
        {
            this.targetId = targetId;
            this.targetUrl = "https://vrchat.com/home/user/"+targetId;
            if (targetDisplayName == null && vrc != null)
            {
                User target = vrc.getUser(targetId);
                targetDisplayName = target == null ? targetId : target.getDisplayName();
            }
            this.targetName = targetDisplayName;
            return this;
        }
        public FormatParams targetEx(String targetJoined, String targetLeft)
        {
            if (targetJoined != null)
                this.targetJoined = targetJoined;
            if (targetLeft != null)
                this.targetLeft = targetLeft;
            return this;
        }
        public FormatParams audit(String auditId, String description, String[] tags)
        {
            if (auditId != null)
                this.auditId = auditId;
            this.description = description == null ? "" : description;
            if (tags != null)
            {
                StringBuilder sb = new StringBuilder();
                for (int idx = 0, len = tags.length, last = len - 1; idx < len; idx++)
                {
                    if (idx > 0)
                        sb.append(", ");
                    if (idx == last)
                        sb.append("and ");
                    sb.append(tags[idx]);
                }
                this.tags = sb.toString();
            }
            else
            {
                this.tags = "";
            }
            return this;
        }
        public String format(boolean appendFooter)
        {
            String string = TemplateStrings.interpolateTemplate(ScarletVRChatReportTemplate.this.check(), this);
            if (appendFooter)
            {
                string = new StringBuilder(string)
                .append("Partially autofilled with ")
                .append(this.appName)
                .append(" version ")
                .append(this.appVersion)
                .append("<br>")
                .append("Group ID: ")
                .append(this.groupId)
                .append("<br>")
                .append("Audit ID: ")
                .append(this.auditId)
                .toString();
            }
            return string;
        }
        public String url(String requestingEmail, String requestingUserId, String reportSubject, boolean appendFooter)
        {
            return VRChatHelpDeskURLs.newModerationRequest_account_other(
                requestingEmail,
                this.targetId,
                reportSubject != null ? reportSubject : this.targetName,
                this.format(appendFooter)
            );
        }
    }

}
