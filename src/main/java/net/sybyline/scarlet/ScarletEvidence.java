package net.sybyline.scarlet;

import java.io.File;

import io.github.vrchatapi.model.Group;
import io.github.vrchatapi.model.User;

import net.sybyline.scarlet.util.TemplateStrings;

public class ScarletEvidence
{

    ScarletEvidence(Scarlet scarlet)
    {
        this.scarlet = scarlet;
    }

    private final Scarlet scarlet;

    public class FormatParams
    {
        public String groupId = "groupId", groupName = "groupName", groupCode = "groupCode";
        public String actorId = "actorId", actorName = "actorName";
        public String targetId = "targetId", targetName = "targetName";
        public String fileName = "fileName", fileExt = "fileExt";
        public String auditId = "auditId";
        public String index = "0";
        private String prevFormat = null;
        public FormatParams incrementIndex()
        {
            this.index = Integer.toUnsignedString(Integer.parseUnsignedInt(this.index) + 1);
            return this;
        }
        public FormatParams group(String groupId, Group group)
        {
            this.groupId = groupId;
            if (group == null)
                group = ScarletEvidence.this.scarlet.vrc.getGroup(groupId);
            if (group != null)
            {
                this.groupName = group.getName();
                this.groupCode = group.getShortCode()+"."+group.getDiscriminator();
            }
            return this;
        }
        public FormatParams actor(String actorId, String actorDisplayName)
        {
            this.actorId = actorId;
            if (actorDisplayName == null)
            {
                User actor = ScarletEvidence.this.scarlet.vrc.getUser(actorId);
                actorDisplayName = actor == null ? actorId : actor.getDisplayName();
            }
            this.actorName = actorDisplayName;
            return this;
        }
        public FormatParams target(String targetId, String targetDisplayName)
        {
            this.targetId = targetId;
            if (targetDisplayName == null)
            {
                User target = ScarletEvidence.this.scarlet.vrc.getUser(targetId);
                targetDisplayName = target == null ? targetId : target.getDisplayName();
            }
            this.targetName = targetDisplayName;
            return this;
        }
        public FormatParams file(String fileNameExt)
        {
            if (fileNameExt != null)
            {
                int period = fileNameExt.lastIndexOf('.');
                this.fileName = period < 0 ? fileNameExt : fileNameExt.substring(0, period);
                this.fileExt = period < 0 ? "" : fileNameExt.substring(period + 1);
            }
            return this;
        }
        public FormatParams file(String fileName, String fileExt)
        {
            if (fileName != null)
                this.fileName = fileName;
            if (fileExt != null)
                this.fileExt = fileExt;
            return this;
        }
        public FormatParams audit(String auditId)
        {
            if (auditId != null)
                this.auditId = auditId;
            return this;
        }
        public FormatParams index(int index)
        {
            this.index = Integer.toUnsignedString(index);
            return this;
        }
        public String format(String format)
        {
            if (format == null || format.trim().isEmpty())
                format = "{targetId}/{fileName}.{fileExt}";
            String value = TemplateStrings.interpolateTemplate(format, this);
            this.prevFormat = value;
            return value;
        }
        public File nextFile(String evidenceRoot, String format)
        {
            File file = new File(evidenceRoot, this.format(format));
            if (format.contains("{index}"))
            {
                while (file.isFile())
                {
                    file = new File(evidenceRoot, this.format(format));
                }
            }
            return file;
        }
        public String prevFormat()
        {
            return this.prevFormat;
        }
    }

}
