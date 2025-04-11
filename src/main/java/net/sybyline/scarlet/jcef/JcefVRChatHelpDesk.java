package net.sybyline.scarlet.jcef;

import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.cef.browser.CefFrame;
import org.cef.callback.CefFileDialogCallback;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefDialogHandler.FileDialogMode;

import com.google.gson.JsonElement;

import net.sybyline.scarlet.Scarlet;
import net.sybyline.scarlet.ext.VRChatHelpDeskRequest;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.VRChatHelpDeskURLs;

public class JcefVRChatHelpDesk extends JcefInstance implements VRChatHelpDeskRequest
{

    protected JcefVRChatHelpDesk(JcefService jcef, String ticketFormId)
    {
        super(jcef, FORM_URL_PREFIX+ticketFormId, false);
    }

    private AtomicReference<PendingAttachments> pendingAttachments = new AtomicReference<>();
    static class PendingAttachments
    {
        PendingAttachments(String[] absolutePaths)
        {
            this.absolutePaths = new Vector<>(Arrays.stream(absolutePaths).filter(Objects::nonNull).collect(Collectors.toList()));
        }
        final Vector<String> absolutePaths;
        final CompletableFuture<Void> completableFuture = new CompletableFuture<>();
    }

    @Override
    public boolean onFileDialog(FileDialogMode mode, String title2, String defaultFilePath, Vector<String> acceptFilters, Vector<String> acceptExtensions, Vector<String> acceptDescriptions, CefFileDialogCallback callback)
    {
        PendingAttachments pendingAttachments = this.pendingAttachments.getAndSet(null);
        if (pendingAttachments != null)
        {
            callback.Continue(pendingAttachments.absolutePaths);
            pendingAttachments.completableFuture.complete(null);
            return true;
        }
        return super.onFileDialog(mode, title2, defaultFilePath, acceptFilters, acceptExtensions, acceptDescriptions, callback);
    }

    public static class InjectedRequest
    {
        public String type;
        public JsonElement content;
    }
    @Override
    public boolean onQuery(CefFrame frame, String jsFunction, long queryId, String request, boolean persistent, CefQueryCallback callback)
    {
        try
        {
            InjectedRequest req = Scarlet.GSON.fromJson(request, InjectedRequest.class);
            switch (req.type)
            {
            case "click": {
                int cx = Math.round(req.content.getAsJsonObject().getAsJsonPrimitive("x").getAsFloat()),
                    cy = Math.round(req.content.getAsJsonObject().getAsJsonPrimitive("y").getAsFloat());
                boolean is = this.isFocused();
                if (!is) this.setFocus(true);
                this.sendMouseEvent(new MouseEvent(this.component, MouseEvent.MOUSE_PRESSED, 0L, 0, cx, cy, cx, cy, 1, false, MouseEvent.BUTTON1));
                this.sendMouseEvent(new MouseEvent(this.component, MouseEvent.MOUSE_RELEASED, 0L, 0, cx, cy, cx, cy, 1, false, MouseEvent.BUTTON1));
                this.sendMouseEvent(new MouseEvent(this.component, MouseEvent.MOUSE_CLICKED, 0L, 0, cx, cy, cx, cy, 1, false, MouseEvent.BUTTON1));
                if (!is) this.setFocus(false);
            } break;
            }
            
        }
        catch (Exception ex)
        {
        }
        return super.onQuery(frame, jsFunction, queryId, request, persistent, callback);
    }

    public CompletableFuture<Void> setRequesterEmail(String email)
    {
        return this.setElementByIdValue(FORM_FIELD_EMAIL, email);
    }

    public CompletableFuture<Void> setSubject(String subject)
    {
        return this.setElementByIdValue(FORM_FIELD_SUBJECT, subject);
    }

    public CompletableFuture<Void> setDescription(String description)
    {
        this.executeJavaScript("document.getElementsByClassName('ck-content')[0].ckeditorInstance.setData("+Scarlet.GSON.toJson(description)+");", "setDescription", 1);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> addAttachments(String... absolutePaths)
    {
        PendingAttachments pa = new PendingAttachments(absolutePaths);
        while (!this.pendingAttachments.compareAndSet(null, pa))
            MiscUtils.sleep(100L);
        this.executeJavaScript("FI=document.getElementById('request-attachments');FI.scrollIntoView();BCR=FI.getBoundingClientRect();window.cefQuery({request:JSON.stringify({type:'click',content:{x:(BCR.x+BCR.width/2),y:(BCR.y+BCR.height/2)}})});", "addAttachments", 1);
        return pa.completableFuture;
    }

    public CompletableFuture<Void> submit()
    {
        this.executeJavaScript("document.getElementById('"+FORM+"').requestSubmit();", "submit", 1);
        return CompletableFuture.completedFuture(null);
    }

    public static class Moderation extends JcefVRChatHelpDesk implements VRChatHelpDeskRequest.ModerationRequest
    {
        public Moderation(JcefService jcef)
        {
            super(jcef, FORM_TICKET_ID_MODERATION);
        }
        public CompletableFuture<Void> setModerationCategory(String moderationCategory)
        {
            return this.setElementByIdValue(FORM_FIELD_MODERATION_CATEGORY, moderationCategory);
        }
        public CompletableFuture<Void> setModerationCategory(VRChatHelpDeskURLs.ModerationCategory moderationCategory)
        {
            return this.setModerationCategory(moderationCategory == null ? null : moderationCategory.value);
        }
        public CompletableFuture<Void> setRequester(String requester)
        {
            return this.setElementByIdValue(FORM_FIELD_REQUESTER, requester);
        }
        public CompletableFuture<Void> setTarget(String target)
        {
            return this.setElementByIdValue(FORM_FIELD_TARGET, target);
        }
    }

    public static class Support extends JcefVRChatHelpDesk implements VRChatHelpDeskRequest.SupportRequest
    {
        public Support(JcefService jcef)
        {
            super(jcef, FORM_TICKET_ID_SUPPORT);
        }
        public CompletableFuture<Void> setSupportCategory(String supportCategory)
        {
            return this.setElementByIdValue(FORM_FIELD_SUPPORT_CATEGORY, supportCategory);
        }
        public CompletableFuture<Void> setSupportCategory(VRChatHelpDeskURLs.SupportCategory supportCategory)
        {
            return this.setSupportCategory(supportCategory == null ? null : supportCategory.value);
        }
        public CompletableFuture<Void> setRequester(String requester)
        {
            return this.setElementByIdValue(FORM_FIELD_REQUESTER, requester);
        }
        public CompletableFuture<Void> setTargetPlatform(String targetPlatform)
        {
            return this.setElementByIdValue(FORM_FIELD_TARGET_PLATFORM, targetPlatform);
        }
        public CompletableFuture<Void> setTargetPlatform(VRChatHelpDeskURLs.SupportPlatform targetPlatform)
        {
            return this.setTargetPlatform(targetPlatform == null ? null : targetPlatform.value);
        }
    }

    public static class Security extends JcefVRChatHelpDesk implements VRChatHelpDeskRequest.SecurityRequest
    {
        public Security(JcefService jcef)
        {
            super(jcef, FORM_TICKET_ID_SECURITY);
        }
        public CompletableFuture<Void> setWhatIsVulnerability(String whatIsVulnerability)
        {
            return this.setElementByIdValue(FORM_FIELD_WHAT_IS_VULNERABILITY, whatIsVulnerability);
        }
        public CompletableFuture<Void> setStepsToReproduce(String stepsToReproduce)
        {
            return this.setElementByIdValue(FORM_FIELD_STEPS_TO_REPRODUCE, stepsToReproduce);
        }
        public CompletableFuture<Void> setExpectedImpact(String expectedImpact)
        {
            return this.setElementByIdValue(FORM_FIELD_EXPECTED_IMPACT, expectedImpact);
        }
        public CompletableFuture<Void> setExploitConfirmation(Boolean exploitConfirmation)
        {
            return this.setElementByIdValue(FORM_FIELD_EXPLOIT_CONFIRMATION, exploitConfirmation);
        }
    }

    public static class Recovery extends JcefVRChatHelpDesk implements VRChatHelpDeskRequest.RecoveryRequest
    {
        public Recovery(JcefService jcef)
        {
            super(jcef, FORM_TICKET_ID_RECOVERY);
        }
        public CompletableFuture<Void> setLockedAccountConfirmation(Boolean lockedAccountConfirmation)
        {
            return this.setElementByIdValue(FORM_FIELD_LOCKED_ACCOUNT_CONFIRMATION, lockedAccountConfirmation);
        }
        public CompletableFuture<Void> setRequester(String requester)
        {
            return this.setElementByIdValue(FORM_FIELD_REQUESTER, requester);
        }
        public CompletableFuture<Void> setAccountRecoveryToken(String accountRecoveryToken)
        {
            return this.setElementByIdValue(FORM_FIELD_ACCOUNT_RECOVERY_TOKEN, accountRecoveryToken);
        }
    }

}
