package net.sybyline.scarlet.ext;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import net.sybyline.scarlet.jcef.JcefService;
import net.sybyline.scarlet.jcef.JcefVRChatHelpDesk;
import net.sybyline.scarlet.selenium.SeleniumVRChatHelpDesk;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.VRChatHelpDeskURLs;

public interface VRChatHelpDeskRequest extends AutoCloseable
{

    public static final String
        FORM_URL_PREFIX = "https://help.vrchat.com/hc/en-us/requests/new?ticket_form_id=",
        FORM_TICKET_ID_MODERATION = "1500000182242",
        FORM_TICKET_ID_SUPPORT = "360006750513",
        FORM_TICKET_ID_SECURITY = "1500001130621",
        FORM_TICKET_ID_RECOVERY = "1900000725685",
        FORM = "new_request",
        
        // Common fields
        FORM_FIELD_EMAIL = "request_anonymous_requester_email",
        FORM_FIELD_SUBJECT = "request_subject",
        FORM_FIELD_DESCRIPTION = "request_description", // Not explicitly referenced
        FORM_FIELD_ATTACHMENTS = "request-attachments",
        
        // Moderation fields
        FORM_FIELD_MODERATION_CATEGORY = "request_custom_fields_360056455174",
        FORM_FIELD_REQUESTER = "request_custom_fields_360057451993",
        FORM_FIELD_TARGET = "request_custom_fields_1500001445142",
        
        // Support fields
        FORM_FIELD_SUPPORT_CATEGORY = "request_custom_fields_1500001394041",
//        FORM_FIELD_REQUESTER = "request_custom_fields_360057451993",
        FORM_FIELD_TARGET_PLATFORM = "request_custom_fields_1500001394021",
        
        // Security fields
        FORM_FIELD_WHAT_IS_VULNERABILITY = "request_custom_fields_14871541233043",
        FORM_FIELD_STEPS_TO_REPRODUCE = "request_custom_fields_14871567333267",
        FORM_FIELD_EXPECTED_IMPACT = "request_custom_fields_14871574761875",
        FORM_FIELD_EXPLOIT_CONFIRMATION = "request_custom_fields_1900000428585",
        
        // Recovery fields
        FORM_FIELD_LOCKED_ACCOUNT_CONFIRMATION = "request_custom_fields_1900003404965",
//        FORM_FIELD_REQUESTER = "request_custom_fields_360057451993",
        FORM_FIELD_ACCOUNT_RECOVERY_TOKEN = "request_custom_fields_1900004384185"
        ;

    public static interface Jcef
    {
        public static VRChatHelpDeskRequest.ModerationRequest moderation(JcefService jcef)
        {
            return new JcefVRChatHelpDesk.Moderation(jcef);
        }
        public static VRChatHelpDeskRequest.SupportRequest support(JcefService jcef)
        {
            return new JcefVRChatHelpDesk.Support(jcef);
        }
        public static VRChatHelpDeskRequest.SecurityRequest security(JcefService jcef)
        {
            return new JcefVRChatHelpDesk.Security(jcef);
        }
        public static VRChatHelpDeskRequest.RecoveryRequest recovery(JcefService jcef)
        {
            return new JcefVRChatHelpDesk.Recovery(jcef);
        }
    }
    public static interface Selenium
    {
        public static <C extends MutableCapabilities, D extends RemoteWebDriver> VRChatHelpDeskRequest.ModerationRequest moderation(C options, Function<C, D> driver)
        {
            return new SeleniumVRChatHelpDesk.Moderation(options, driver);
        }
        public static <C extends MutableCapabilities, D extends RemoteWebDriver> VRChatHelpDeskRequest.SupportRequest support(C options, Function<C, D> driver)
        {
            return new SeleniumVRChatHelpDesk.Support(options, driver);
        }
        public static <C extends MutableCapabilities, D extends RemoteWebDriver> VRChatHelpDeskRequest.SecurityRequest security(C options, Function<C, D> driver)
        {
            return new SeleniumVRChatHelpDesk.Security(options, driver);
        }
        public static <C extends MutableCapabilities, D extends RemoteWebDriver> VRChatHelpDeskRequest.RecoveryRequest recovery(C options, Function<C, D> driver)
        {
            return new SeleniumVRChatHelpDesk.Recovery(options, driver);
        }
    }

    public CompletableFuture<Void> setRequesterEmail(String email);

    public CompletableFuture<Void> setSubject(String subject);

    public CompletableFuture<Void> setDescription(String description);

    public CompletableFuture<Void> addAttachments(String... absolutePaths);
    public default CompletableFuture<Void> addAttachment(String absolutePath)
    {
        return this.addAttachments(absolutePath);
    }
    public default CompletableFuture<Void> addAttachment(File file)
    {
        return this.addAttachments(file.getAbsolutePath());
    }
    public default CompletableFuture<Void> addAttachment(Path path)
    {
        return this.addAttachments(path.toAbsolutePath().toString());
    }
    public default CompletableFuture<Void> addAttachments(File... files)
    {
        return this.addAttachments(Arrays.stream(files).map(File::getAbsolutePath).toArray(String[]::new));
    }
    public default CompletableFuture<Void> addAttachments(Path... paths)
    {
        return this.addAttachments(Arrays.stream(paths).map(Path::toAbsolutePath).map(Path::toString).toArray(String[]::new));
    }
    public default CompletableFuture<Void> addAttachmentsResolved(File tempDir, String... resources)
    {
        return this.addAttachments(Arrays.stream(resources).map($ -> resolve(tempDir, $)).toArray(String[]::new));
    }
    public default CompletableFuture<Void> addAttachmentsResolved(Path tempDir, String... resources)
    {
        return this.addAttachments(Arrays.stream(resources).map($ -> resolve(tempDir, $)).toArray(String[]::new));
    }

    public static String resolve(File tempDir, String resource)
    {
        if (!(resource.startsWith("http://") || resource.startsWith("https://")))
            return new File(resource).getAbsolutePath();
        try
        {
            if (!tempDir.isAbsolute())
                tempDir = tempDir.getAbsoluteFile();
            if (!tempDir.isDirectory())
                tempDir.mkdirs();
            URL url0 = new URL(resource);
            String name = url0.getPath().substring(url0.getPath().lastIndexOf('/'));
            File tgt = new File(tempDir, name);
            try (HttpURLInputStream in = HttpURLInputStream.get(resource))
            {
                Files.copy(in, tgt.toPath());
            }
            return tgt.getAbsolutePath();
        }
        catch (Exception ex)
        {
            return null;
        }
    }
    public static String resolve(Path tempDir, String resource)
    {
        if (!(resource.startsWith("http://") || resource.startsWith("https://")))
            return Paths.get(resource).toAbsolutePath().toString();
        try
        {
            if (!tempDir.isAbsolute())
                tempDir = tempDir.toAbsolutePath();
            if (!Files.isDirectory(tempDir))
                Files.createDirectories(tempDir);
            URL url0 = new URL(resource);
            String name = url0.getPath().substring(url0.getPath().lastIndexOf('/'));
            Path tgt = tempDir.resolve(name);
            try (HttpURLInputStream in = HttpURLInputStream.get(resource))
            {
                Files.copy(in, tgt);
            }
            return tgt.toString();
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    public CompletableFuture<Void> submit();

    public static interface ModerationRequest extends VRChatHelpDeskRequest
    {

        public CompletableFuture<Void> setModerationCategory(String moderationCategory);
        public default CompletableFuture<Void> setModerationCategory(VRChatHelpDeskURLs.ModerationCategory moderationCategory)
        {
            return this.setModerationCategory(moderationCategory == null ? null : moderationCategory.value);
        }

        public CompletableFuture<Void> setRequester(String requester);

        public CompletableFuture<Void> setTarget(String target);

    }

    public static interface SupportRequest extends VRChatHelpDeskRequest
    {

        public CompletableFuture<Void> setSupportCategory(String supportCategory);
        public default CompletableFuture<Void> setSupportCategory(VRChatHelpDeskURLs.SupportCategory supportCategory)
        {
            return this.setSupportCategory(supportCategory == null ? null : supportCategory.value);
        }

        public CompletableFuture<Void> setRequester(String requester);

        public CompletableFuture<Void> setTargetPlatform(String targetPlatform);
        public default CompletableFuture<Void> setTargetPlatform(VRChatHelpDeskURLs.SupportPlatform targetPlatform)
        {
            return this.setTargetPlatform(targetPlatform == null ? null : targetPlatform.value);
        }

    }

    public static interface SecurityRequest extends VRChatHelpDeskRequest
    {

        public CompletableFuture<Void> setWhatIsVulnerability(String whatIsVulnerability);

        public CompletableFuture<Void> setStepsToReproduce(String stepsToReproduce);

        public CompletableFuture<Void> setExpectedImpact(String expectedImpact);

        public CompletableFuture<Void> setExploitConfirmation(Boolean exploitConfirmation);

    }

    public static interface RecoveryRequest extends VRChatHelpDeskRequest
    {

        public CompletableFuture<Void> setLockedAccountConfirmation(Boolean lockedAccountConfirmation);

        public CompletableFuture<Void> setRequester(String requester);

        public CompletableFuture<Void> setAccountRecoveryToken(String accountRecoveryToken);

    }

}
