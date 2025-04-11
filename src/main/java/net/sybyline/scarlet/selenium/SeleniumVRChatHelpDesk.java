package net.sybyline.scarlet.selenium;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import net.sybyline.scarlet.ext.VRChatHelpDeskRequest;

public abstract class SeleniumVRChatHelpDesk<S extends SeleniumVRChatHelpDesk<S>> extends SeleniumInstance<S> implements VRChatHelpDeskRequest
{

    protected <C extends MutableCapabilities, D extends RemoteWebDriver> SeleniumVRChatHelpDesk(String ticketFormId, C options, Function<C, D> driver)
    {
        super(options, driver);
        this.driver.get(FORM_URL_PREFIX+ticketFormId);
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
        this.driver.executeScript("document.getElementsByClassName('ck-content')[0].ckeditorInstance.setData(arguments[0]);", description);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> addAttachments(String... absolutePaths)
    {
        WebElement attachments = this.getElementById(FORM_FIELD_ATTACHMENTS);
        for (String absolutePath : absolutePaths)
        {
            if (absolutePath != null)
            {
                attachments.sendKeys(absolutePath);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> submit()
    {
        this.getElementById(FORM).submit();
        return CompletableFuture.completedFuture(null);
    }

    public static class Moderation extends SeleniumVRChatHelpDesk<Moderation> implements VRChatHelpDeskRequest.ModerationRequest
    {
        public <C extends MutableCapabilities, D extends RemoteWebDriver> Moderation(C options, Function<C, D> driver)
        {
            super(FORM_TICKET_ID_MODERATION, options, driver);
        }
        public CompletableFuture<Void> setModerationCategory(String type)
        {
            return this.setElementByIdValue(FORM_FIELD_MODERATION_CATEGORY, type);
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

    public static class Support extends SeleniumVRChatHelpDesk<Support> implements VRChatHelpDeskRequest.SupportRequest
    {
        public <C extends MutableCapabilities, D extends RemoteWebDriver> Support(C options, Function<C, D> driver)
        {
            super(FORM_TICKET_ID_SUPPORT, options, driver);
        }
        public CompletableFuture<Void> setSupportCategory(String supportCategory)
        {
            return this.setElementByIdValue(FORM_FIELD_SUPPORT_CATEGORY, supportCategory);
        }
        public CompletableFuture<Void> setRequester(String requester)
        {
            return this.setElementByIdValue(FORM_FIELD_REQUESTER, requester);
        }
        public CompletableFuture<Void> setTargetPlatform(String targetPlatform)
        {
            return this.setElementByIdValue(FORM_FIELD_TARGET_PLATFORM, targetPlatform);
        }
    }

    public static class Security extends SeleniumVRChatHelpDesk<Security> implements VRChatHelpDeskRequest.SecurityRequest
    {
        public <C extends MutableCapabilities, D extends RemoteWebDriver> Security(C options, Function<C, D> driver)
        {
            super(FORM_TICKET_ID_SECURITY, options, driver);
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

    public static class Recovery extends SeleniumVRChatHelpDesk<Recovery> implements VRChatHelpDeskRequest.RecoveryRequest
    {
        public <C extends MutableCapabilities, D extends RemoteWebDriver> Recovery(C options, Function<C, D> driver)
        {
            super(FORM_TICKET_ID_RECOVERY, options, driver);
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
