package net.sybyline.scarlet.util;

import java.awt.GraphicsEnvironment;
import java.util.List;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.util.tts.LinuxPackageManagerDetector;
import net.sybyline.scarlet.ui.Swing;

/**
 * Manages installation prompts for xdg-utils (xdg-open) on Linux.
 *
 * Mirrors the pattern used by TtsPackageInstallDialogs: check if installed,
 * ask the user for consent, then install via the detected package manager.
 *
 * xdg-utils is the standard desktop integration toolkit on Linux and is
 * present on virtually all desktop distros, but may be missing on minimal
 * or server installs. Without it, "Browse data folder" and all hyperlink
 * menu items will silently fail.
 */
public class XdgOpenInstallDialogs
{

    private static final Logger LOG = LoggerFactory.getLogger("Scarlet/XdgOpen");

    public static final String PACKAGE_NAME         = "xdg-utils";
    public static final String PACKAGE_DISPLAY_NAME = "xdg-utils";
    public static final String PACKAGE_DESCRIPTION  =
        "xdg-utils provides the xdg-open command, which opens files and URLs\n" +
        "using the user's preferred desktop applications.\n\n" +
        "It is required for Scarlet's 'Browse data folder' and help-menu links\n" +
        "to work correctly on Linux.";

    /** Result of the install dialog flow. */
    public enum InstallDialogResult
    {
        ALREADY_INSTALLED,
        INSTALL_APPROVED_SUCCESS,
        INSTALL_APPROVED_FAILED,
        INSTALL_DECLINED,
        HEADLESS_MODE
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns true if xdg-open is available on PATH.
     */
    public static boolean isXdgOpenInstalled()
    {
        try
        {
            Process proc = new ProcessBuilder("which", "xdg-open")
                .redirectErrorStream(true)
                .start();
            return proc.waitFor() == 0;
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    /**
     * Runs the full check-and-prompt flow.
     * Call this once on Linux startup, or lazily the first time a browse/open
     * action is attempted and xdg-open is not found.
     *
     * @return the outcome of the flow
     */
    public static InstallDialogResult showInstallFlowIfNeeded()
    {
        if (isXdgOpenInstalled())
            return InstallDialogResult.ALREADY_INSTALLED;

        LOG.warn("xdg-open not found on this system");

        if (GraphicsEnvironment.isHeadless())
            return handleHeadlessMode();

        boolean consented = showDownloadConsentDialog();
        if (!consented)
        {
            showDeclineAcknowledgmentDialog();
            return InstallDialogResult.INSTALL_DECLINED;
        }

        boolean success = performInstallation();
        if (success)
        {
            showInstallationSuccessDialog();
            return InstallDialogResult.INSTALL_APPROVED_SUCCESS;
        }
        else
        {
            showInstallationFailedDialog();
            return InstallDialogResult.INSTALL_APPROVED_FAILED;
        }
    }

    // -------------------------------------------------------------------------
    // Installation
    // -------------------------------------------------------------------------

    private static boolean performInstallation()
    {
        LinuxPackageManagerDetector.PackageManager pm =
            LinuxPackageManagerDetector.getPrimaryPackageManager();

        if (pm == null)
        {
            LOG.error("No package manager detected — cannot install xdg-utils");
            return false;
        }

        // Build the install command, substituting xdg-utils for the espeak placeholder
        String cmd = pm.getFullInstallCommand().replace(pm.packageName, PACKAGE_NAME);
        LOG.info("Installing xdg-utils via: {}", cmd);

        try
        {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.inheritIO();
            Process proc = pb.start();
            int exit = proc.waitFor();
            boolean ok = exit == 0;
            if (ok)
                LOG.info("xdg-utils installed successfully");
            else
                LOG.error("xdg-utils installation exited with code {}", exit);
            return ok;
        }
        catch (Exception ex)
        {
            LOG.error("Exception during xdg-utils installation", ex);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Dialogs
    // -------------------------------------------------------------------------

    private static boolean showDownloadConsentDialog()
    {
        final boolean[] result = {false};
        Swing.invokeWait(() ->
        {
            String message =
                "<html><div style='width: 400px;'>" +
                "<h3>xdg-utils Not Found</h3>" +
                "<p>Scarlet needs <b>xdg-utils</b> to open folders and web links on Linux.</p>" +
                "<p style='margin-top:8px;'>" + PACKAGE_DESCRIPTION.replace("\n", "<br>") + "</p>" +
                "<p style='margin-top:8px;'>Would you like Scarlet to install it now using your package manager?</p>" +
                "</div></html>";

            int choice = JOptionPane.showConfirmDialog(
                null,
                message,
                "Install xdg-utils?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            result[0] = (choice == JOptionPane.YES_OPTION);
        });
        return result[0];
    }

    private static void showDeclineAcknowledgmentDialog()
    {
        Swing.invokeWait(() ->
        {
            String installCmd = getInstallCommand();
            JOptionPane.showMessageDialog(
                null,
                "<html><div style='width: 380px;'>" +
                "<p>xdg-utils will not be installed.</p>" +
                "<p style='margin-top:8px;'>Browsing folders and opening links from Scarlet will not work " +
                "until <b>xdg-utils</b> is installed.</p>" +
                "<p style='margin-top:8px;'>You can install it manually:</p>" +
                "<pre style='background:#2d2d2d;padding:6px;border-radius:4px;font-family:monospace;'>" +
                installCmd + "</pre>" +
                "</div></html>",
                "xdg-utils Not Installed",
                JOptionPane.WARNING_MESSAGE
            );
        });
    }

    private static void showInstallationSuccessDialog()
    {
        Swing.invokeWait(() ->
            JOptionPane.showMessageDialog(
                null,
                "<html><div style='width:320px;'>" +
                "<h3 style='color:#4CAF50;'>&#10003; Installation Successful</h3>" +
                "<p>xdg-utils has been installed. Folder and link browsing is now available.</p>" +
                "</div></html>",
                "xdg-utils Installed",
                JOptionPane.INFORMATION_MESSAGE
            )
        );
    }

    private static void showInstallationFailedDialog()
    {
        String installCmd = getInstallCommand();
        Swing.invokeWait(() ->
            JOptionPane.showMessageDialog(
                null,
                "<html><div style='width:400px;'>" +
                "<h3 style='color:#F44336;'>&#10007; Installation Failed</h3>" +
                "<p>Could not install xdg-utils automatically.</p>" +
                "<p style='margin-top:8px;'>Please install it manually:</p>" +
                "<pre style='background:#2d2d2d;padding:6px;border-radius:4px;font-family:monospace;'>" +
                installCmd + "</pre>" +
                "</div></html>",
                "xdg-utils Installation Failed",
                JOptionPane.ERROR_MESSAGE
            )
        );
    }

    // -------------------------------------------------------------------------
    // Headless / CLI fallback
    // -------------------------------------------------------------------------

    private static InstallDialogResult handleHeadlessMode()
    {
        System.out.println("\n========================================");
        System.out.println("[Scarlet] xdg-utils Required");
        System.out.println("========================================");
        System.out.println("Scarlet requires xdg-utils (xdg-open) to open folders and web links.");
        System.out.println("Install it manually using your package manager, e.g.:");
        System.out.println("  " + getInstallCommand());
        System.out.println("Folder/link browsing will be disabled until xdg-utils is installed.");
        System.out.println("========================================\n");
        return InstallDialogResult.HEADLESS_MODE;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String getInstallCommand()
    {
        LinuxPackageManagerDetector.PackageManager pm =
            LinuxPackageManagerDetector.getPrimaryPackageManager();
        if (pm == null)
            return "sudo apt-get install -y xdg-utils"; // safe default
        return pm.getFullInstallCommand().replace(pm.packageName, PACKAGE_NAME);
    }
}
