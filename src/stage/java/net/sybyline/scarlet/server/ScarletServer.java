package net.sybyline.scarlet.server;

import java.io.File;
import java.io.IOException;

import net.sybyline.scarlet.ScarletAssociationData;
import net.sybyline.scarlet.ScarletData;
import net.sybyline.scarlet.ScarletLiveInstanceData;
import net.sybyline.scarlet.ScarletModerationTags;
import net.sybyline.scarlet.ScarletPendingModActions;
import net.sybyline.scarlet.ScarletStaffList;
import net.sybyline.scarlet.ScarletVRChatReportTemplate;
import net.sybyline.scarlet.ScarletWatchedGroups;
import net.sybyline.scarlet.util.tts.TtsService;

public class ScarletServer extends ScarletApp
{

    public ScarletServer(File dir) throws IOException
    {
        super();
        this.serverDir = new File(dir, "server");
        this.associations = new ScarletAssociationData(new File(this.serverDir, "associations.json"));
        this.moderationTags = new ScarletModerationTags(new File(this.serverDir, "moderation_tags.json"));
        this.watchedGroups = new ScarletWatchedGroups(new File(this.serverDir, "watched_groups.json"));
        this.vrcReport = new ScarletVRChatReportTemplate(new File(this.serverDir, "report_template.txt"));
        this.data = new ScarletData(new File(this.serverDir, "data"));
        this.ttsService = new TtsService(new File(this.serverDir, "tts"), null, null);
    }

    final File serverDir;
    final ScarletAssociationData associations;
    final ScarletModerationTags moderationTags;
    final ScarletWatchedGroups watchedGroups;
    final ScarletVRChatReportTemplate vrcReport;
    final ScarletData data;
    final TtsService ttsService;

    public class VRCGroupContext
    {
        VRCGroupContext(String groupId)
        {
            this.groupId = groupId;
            this.groupDir = new File(ScarletServer.this.serverDir, "groups/"+groupId);
            this.pendingModActions = new ScarletPendingModActions(new File(this.groupDir, "pending_moderation_actions.json"));
            this.staffList = new ScarletStaffList(new File(this.groupDir, "staff_list.json"));
            this.liveInstances = new ScarletLiveInstanceData(new File(this.groupDir, "live_instances.json"));
        }
        final String groupId;
        final File groupDir;
        final ScarletPendingModActions pendingModActions;
        final ScarletStaffList staffList;
        final ScarletLiveInstanceData liveInstances;
    }

}
