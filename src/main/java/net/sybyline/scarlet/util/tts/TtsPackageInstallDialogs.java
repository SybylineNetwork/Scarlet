package net.sybyline.scarlet.util.tts;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.util.Platform;
import net.sybyline.scarlet.ui.Swing;

/**
 * Manages TTS package installation dialogs with user consent.
 * Handles detection, prompting, and installation of eSpeak on Linux.
 */
public class TtsPackageInstallDialogs
{

    private static final Logger LOG = LoggerFactory.getLogger("Scarlet/TTS/Dialogs");

    public static final String LINUX_PACKAGE_NAME         = "espeak";
    public static final String LINUX_PACKAGE_DISPLAY_NAME = "eSpeak TTS";
    public static final String LINUX_PACKAGE_DESCRIPTION  =
        "eSpeak is a compact open source software speech synthesizer for English and other languages.\n\n" +
        "This package is required for Scarlet's text-to-speech functionality on Linux.";

    private final Platform  platform;
    private final Component parentComponent;

    public TtsPackageInstallDialogs(Platform platform, Component parentComponent)
    {
        this.platform        = platform;
        this.parentComponent = parentComponent;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static boolean isPackageInstalled(Platform platform)
    {
        if (platform == Platform.$NIX) return isEspeakInstalled();
        if (platform == Platform.NT)   return true; // Windows uses built-in SAPI
        return false;
    }

    public static String getLinuxInstallCommand()
    {
        return LinuxPackageManagerDetector.getEspeakInstallCommand();
    }

    public enum InstallDialogResult
    {
        ALREADY_INSTALLED,
        INSTALL_APPROVED_SUCCESS,
        INSTALL_APPROVED_FAILED,
        INSTALL_DECLINED,
        HEADLESS_MODE
    }

    public InstallDialogResult showInstallFlow()
    {
        if (isPackageInstalled(this.platform))
        {
            showInfo("TTS Package Status",
                "<h2 style='color:#4CAF50;'>&#10003; Package Already Installed</h2>" +
                "<p style='margin-top:10px;'>" + getPackageDisplayName() + " is already installed.</p>" +
                "<p style='margin-top:10px;color:#888;'>Scarlet's text-to-speech functionality is ready to use.</p>");
            return InstallDialogResult.ALREADY_INSTALLED;
        }

        if (GraphicsEnvironment.isHeadless())
            return handleHeadlessMode();

        if (!showDownloadConsentDialog())
        {
            showDeclineAcknowledgmentDialog();
            return InstallDialogResult.INSTALL_DECLINED;
        }

        if (performInstallationWithTerminal())
            return InstallDialogResult.INSTALL_APPROVED_SUCCESS;

        return handleInstallationFailure();
    }

    public void showPackageAlreadyInstalledDialog()
    {
        showInfo("TTS Package Status",
            "<h2 style='color:#4CAF50;'>&#10003; Package Already Installed</h2>" +
            "<p style='margin-top:10px;'>" + getPackageDisplayName() + " is already installed.</p>" +
            "<p style='margin-top:10px;color:#888;'>Scarlet's text-to-speech functionality is ready to use.</p>");
    }

    public boolean showDownloadConsentDialog()
    {
        if (GraphicsEnvironment.isHeadless())
            return handleHeadlessConsent();

        List<LinuxPackageManagerDetector.PackageManager> managers = LinuxPackageManagerDetector.detectAllPackageManagers();
        LinuxPackageManagerDetector.PackageManager primary = managers.isEmpty() ? null : managers.get(0);
        String installCmd = primary != null
            ? primary.getFullInstallCommand().replace("{pkg}", primary.packageName)
            : "sudo apt-get install -y espeak";

        StringBuilder pmInfo = new StringBuilder();
        if (managers.size() > 1)
        {
            pmInfo.append("<p style='margin-top:10px;'><b>Detected package managers:</b><br>");
            for (int i = 0; i < Math.min(5, managers.size()); i++)
                pmInfo.append("&bull; ").append(managers.get(i).displayName).append("<br>");
            if (managers.size() > 5)
                pmInfo.append("&bull; and ").append(managers.size() - 5).append(" more...<br>");
            pmInfo.append("</p>");
        }

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        JLabel icon = new JLabel("&#9888;");
        icon.setFont(icon.getFont().deriveFont(24f));
        header.add(icon, BorderLayout.WEST);
        header.add(new JLabel("<html><b style='font-size:14px;'>TTS Package Required</b></html>"), BorderLayout.CENTER);

        panel.add(header, BorderLayout.NORTH);
        panel.add(new JLabel(String.format(
            "<html><div style='width:450px;padding:5px;'>" +
            "<p style='margin-bottom:10px;'>Scarlet requires <b>%s</b> for text-to-speech functionality.</p>" +
            "<p style='margin-bottom:10px;'>%s</p>" +
            "<p style='margin-bottom:10px;'><b>The following command will be executed:</b></p>" +
            "<pre style='background-color:#2d2d2d;padding:10px;border-radius:5px;font-family:monospace;'>%s</pre>" +
            "%s" +
            "<p style='margin-top:10px;color:#FF9800;'>&#9888; This will download and install software from your system's package manager.</p>" +
            "</div></html>",
            getPackageDisplayName(), LINUX_PACKAGE_DESCRIPTION.replace("\n", "<br>"), installCmd, pmInfo
        )), BorderLayout.CENTER);
        panel.add(new JLabel("<html><div style='margin-top:10px;padding-top:10px;border-top:1px solid #444;'>" +
            "<b>Yes</b> - Install the package (a terminal window will open for sudo password)<br>" +
            "<b>No</b> - Skip installation (TTS features will be disabled)</div></html>"), BorderLayout.SOUTH);

        return Swing.getWait(() -> JOptionPane.showConfirmDialog(
            this.parentComponent, panel, "TTS Package Installation",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.YES_OPTION);
    }

    public void showDeclineAcknowledgmentDialog()
    {
        if (GraphicsEnvironment.isHeadless())
        {
            System.out.println("[Scarlet TTS] TTS package installation declined. TTS features will be disabled.");
            System.out.println("[Scarlet TTS] You can manually install " + getPackageDisplayName() + " later to enable TTS functionality.");
            return;
        }

        List<String> commands = LinuxPackageManagerDetector.getAllEspeakInstallCommands();
        StringBuilder cmdsHtml = new StringBuilder();
        if (!commands.isEmpty())
        {
            cmdsHtml.append("<p style='margin-top:15px;'><b>You can install the package later by running one of:</b></p>")
                    .append("<pre style='background-color:#2d2d2d;padding:8px;border-radius:5px;font-family:monospace;'>");
            for (int i = 0; i < Math.min(3, commands.size()); i++)
                cmdsHtml.append(commands.get(i)).append("\n");
            cmdsHtml.append("</pre>");
        }

        Swing.invokeWait(() ->
        {
            JLabel msg = new JLabel(String.format(
                "<html><div style='width:450px;padding:5px;'>" +
                "<h3 style='color:#FF9800;'>&#9888; TTS Features Disabled</h3>" +
                "<p style='margin-top:10px;'>You have chosen not to install <b>%s</b>.</p>" +
                "<ul style='margin-top:5px;'>" +
                "<li>Text-to-speech notifications will not be available</li>" +
                "<li>Audio announcements in Discord will be disabled</li>" +
                "<li>Other Scarlet features will continue to work normally</li>" +
                "</ul>%s</div></html>",
                getPackageDisplayName(), cmdsHtml
            ));
            JButton ok = new JButton("I understand");
            JOptionPane pane = new JOptionPane(msg, JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, new Object[]{ok});
            JDialog dialog = pane.createDialog(this.parentComponent, "TTS Installation Declined");
            ok.addActionListener(e -> dialog.dispose());
            dialog.setVisible(true);
        });
    }

    public boolean performInstallationWithTerminal()
    {
        if (this.platform != Platform.$NIX)
        {
            LOG.warn("Package installation not supported on platform: {}", this.platform);
            return false;
        }
        LinuxPackageManagerDetector.PackageManager pm = LinuxPackageManagerDetector.getPrimaryPackageManager();
        if (pm == null)
        {
            LOG.error("No package manager detected");
            showError("No Package Manager Detected",
                "<h3 style='color:#F44336;'>&#10007; No Package Manager Detected</h3>" +
                "<p style='margin-top:10px;'>Could not detect a package manager on your system.</p>" +
                "<p style='margin-top:10px;'>Please install <b>" + getPackageDisplayName() + "</b> manually.</p>");
            return false;
        }
        return performInstallationWithManager(pm);
    }

    // -------------------------------------------------------------------------
    // Installation failure handling
    // -------------------------------------------------------------------------

    private InstallDialogResult handleInstallationFailure()
    {
        List<LinuxPackageManagerDetector.PackageManager> managers = LinuxPackageManagerDetector.detectAllPackageManagers();
        if (managers.size() <= 1)
        {
            showInstallationFailedDialog();
            return InstallDialogResult.INSTALL_APPROVED_FAILED;
        }
        while (true)
        {
            LinuxPackageManagerDetector.PackageManager selected = showPackageManagerSelectionDialog();
            if (selected == null)
            {
                showDeclineAcknowledgmentDialog();
                return InstallDialogResult.INSTALL_DECLINED;
            }
            if (performInstallationWithManager(selected))
                return InstallDialogResult.INSTALL_APPROVED_SUCCESS;
            if (!showRetryDialog())
            {
                showDeclineAcknowledgmentDialog();
                return InstallDialogResult.INSTALL_APPROVED_FAILED;
            }
        }
    }

    private LinuxPackageManagerDetector.PackageManager showPackageManagerSelectionDialog()
    {
        List<LinuxPackageManagerDetector.PackageManager> managers = LinuxPackageManagerDetector.detectAllPackageManagers();
        AtomicReference<LinuxPackageManagerDetector.PackageManager> result = new AtomicReference<>(null);

        Swing.invokeWait(() ->
        {
            JComboBox<String> combo = new JComboBox<>();
            for (LinuxPackageManagerDetector.PackageManager pm : managers)
                combo.addItem(pm.displayName + ": " + pm.getFullInstallCommand().replace("{pkg}", pm.packageName));

            JTextArea preview = new JTextArea(3, 40);
            preview.setEditable(false);
            preview.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
            preview.setBackground(new java.awt.Color(45, 45, 45));
            preview.setForeground(java.awt.Color.WHITE);
            updatePreview(preview, managers.get(0));
            combo.addActionListener(e -> {
                int i = combo.getSelectedIndex();
                if (i >= 0 && i < managers.size()) updatePreview(preview, managers.get(i));
            });

            JPanel center = new JPanel(new BorderLayout(5, 5));
            center.add(new JLabel("<html><div style='width:450px;'>" +
                "<p style='margin-top:10px;'>The installation with the default package manager failed.</p>" +
                "<p style='margin-top:10px;'>Select an alternative package manager below:</p></div></html>"),
                BorderLayout.NORTH);
            center.add(combo, BorderLayout.CENTER);
            center.add(new JScrollPane(preview) {{ setPreferredSize(new Dimension(450, 80)); }}, BorderLayout.SOUTH);

            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            panel.add(new JLabel("<html><b style='font-size:14px;'>&#9888; Installation Failed - Select Alternative</b></html>"), BorderLayout.NORTH);
            panel.add(center, BorderLayout.CENTER);

            JButton install = new JButton("Install with Selected");
            JButton cancel  = new JButton("Cancel");
            JOptionPane pane = new JOptionPane(panel, JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, new Object[]{install, cancel});
            JDialog dialog = pane.createDialog(this.parentComponent, "Package Manager Selection");
            install.addActionListener(e -> {
                int i = combo.getSelectedIndex();
                if (i >= 0 && i < managers.size()) result.set(managers.get(i));
                dialog.dispose();
            });
            cancel.addActionListener(e -> dialog.dispose());
            dialog.setVisible(true);
        });

        return result.get();
    }

    private void updatePreview(JTextArea area, LinuxPackageManagerDetector.PackageManager pm)
    {
        area.setText(String.format("Package Manager: %s\nPackage Name: %s\nCommand: %s",
            pm.displayName, pm.packageName,
            pm.getFullInstallCommand().replace("{pkg}", pm.packageName)));
    }

    private boolean showRetryDialog()
    {
        if (GraphicsEnvironment.isHeadless()) return false;
        return Swing.getWait(() -> JOptionPane.showConfirmDialog(
            this.parentComponent,
            "<html><div style='width:400px;'><h3 style='color:#F44336;'>&#10007; Installation Failed</h3>" +
            "<p style='margin-top:10px;'>The installation did not complete successfully.</p>" +
            "<p style='margin-top:10px;'>Would you like to try a different package manager?</p></div></html>",
            "Installation Failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.YES_OPTION);
    }

    private boolean performInstallationWithManager(LinuxPackageManagerDetector.PackageManager pm)
    {
        String installCmd = pm.getFullInstallCommand().replace("{pkg}", pm.packageName);
        LOG.info("Attempting to install {} using {} via terminal", LINUX_PACKAGE_DISPLAY_NAME, pm.displayName);

        String[] terminal = detectAvailableTerminals();
        if (terminal == null)
        {
            LOG.error("No terminal emulator found for installation");
            showError("Installation Error",
                "<h3 style='color:#F44336;'>&#10007; No Terminal Found</h3>" +
                "<p style='margin-top:10px;'>Could not detect a terminal emulator on your system.</p>" +
                "<p style='margin-top:10px;'>Please install <b>" + getPackageDisplayName() + "</b> manually:</p>" +
                "<pre style='background-color:#2d2d2d;padding:8px;border-radius:5px;font-family:monospace;'>" + getLinuxInstallCommand() + "</pre>");
            return false;
        }
        try
        {
            ProcessBuilder pb = buildTerminalProcess(terminal, installCmd);
            int exit = pb.start().waitFor();
            LOG.info("Terminal process exited with code: {}", exit);
            if (isEspeakInstalled())
            {
                showInfo("Installation Complete",
                    "<h2 style='color:#4CAF50;'>&#10003; Installation Successful</h2>" +
                    "<p style='margin-top:10px;'>" + getPackageDisplayName() + " has been installed successfully!</p>" +
                    "<p style='margin-top:10px;'>Scarlet's text-to-speech functionality is now ready to use.</p>");
                return true;
            }
            showInstallationFailedDialog();
            return false;
        }
        catch (Exception ex)
        {
            LOG.error("Exception during terminal installation", ex);
            showError("Installation Error",
                "<h3 style='color:#F44336;'>&#10007; Installation Error</h3>" +
                "<p style='margin-top:10px;'>An error occurred during installation:</p>" +
                "<pre style='background-color:#2d2d2d;padding:8px;border-radius:5px;font-family:monospace;'>" + ex.getMessage() + "</pre>" +
                "<p style='margin-top:10px;'>Please try installing manually:</p>" +
                "<pre style='background-color:#2d2d2d;padding:8px;border-radius:5px;font-family:monospace;'>" + getLinuxInstallCommand() + "</pre>");
            return false;
        }
    }

    private void showInstallationFailedDialog()
    {
        List<String> commands = LinuxPackageManagerDetector.getAllEspeakInstallCommands();
        StringBuilder cmds = new StringBuilder();
        for (int i = 0; i < Math.min(3, commands.size()); i++)
            cmds.append(commands.get(i)).append("\n");
        showError("Installation Failed",
            "<h3 style='color:#F44336;'>&#10007; Installation Failed</h3>" +
            "<p style='margin-top:10px;'>The installation of <b>" + getPackageDisplayName() + "</b> did not complete successfully.</p>" +
            "<p style='margin-top:10px;'>Please try installing manually:</p>" +
            "<pre style='background-color:#2d2d2d;padding:8px;border-radius:5px;font-family:monospace;'>" + cmds + "</pre>");
    }

    // -------------------------------------------------------------------------
    // Terminal detection
    // -------------------------------------------------------------------------

    private String[] detectAvailableTerminals()
    {
        String[][] options = {
            {"gnome-terminal","--","sh","-c"}, {"kgx","--","sh","-c"}, {"ptyxis","--","sh","-c"},
            {"konsole","-e","sh","-c"}, {"yakuake","-e","sh","-c"},
            {"xfce4-terminal","-e"}, {"qterminal","-e"}, {"lxterminal","-e"},
            {"mate-terminal","-e"}, {"deepin-terminal","-e","sh","-c"},
            {"alacritty","-e","sh","-c"}, {"kitty","sh","-c"}, {"wezterm","start","sh","-c"},
            {"foot","sh","-c"}, {"tilix","-e","sh","-c"}, {"terminator","-e","sh","-c"},
            {"xterm","-e","sh","-c"}, {"rxvt","-e","sh","-c"}, {"urxvt","-e","sh","-c"},
            {"st","-e","sh","-c"},
        };
        for (String[] t : options)
        {
            try
            {
                if (new ProcessBuilder("which", t[0]).start().waitFor() == 0)
                {
                    LOG.info("Found terminal emulator: {}", t[0]);
                    return t;
                }
            }
            catch (Exception ex) { /* try next */ }
        }
        return null;
    }

    private ProcessBuilder buildTerminalProcess(String[] terminal, String installCmd)
    {
        String shellCmd = installCmd +
            " && echo '' && echo '\\u2713 Installation completed successfully!' && echo 'Press Enter to close...' && read line";
        String name = terminal[0];
        List<String> cmd = new ArrayList<>();

        boolean combined = name.equals("xfce4-terminal") || name.equals("qterminal")
            || name.equals("lxterminal") || name.equals("mate-terminal");

        if (combined)
        {
            cmd.add(name); cmd.add("-e"); cmd.add("sh"); cmd.add("-c"); cmd.add(shellCmd);
        }
        else
        {
            for (String a : terminal) cmd.add(a);
            cmd.add(shellCmd);
        }
        LOG.info("Terminal command: {}", cmd);
        return new ProcessBuilder(cmd);
    }

    // -------------------------------------------------------------------------
    // Headless
    // -------------------------------------------------------------------------

    private InstallDialogResult handleHeadlessMode()
    {
        List<LinuxPackageManagerDetector.PackageManager> managers = LinuxPackageManagerDetector.detectAllPackageManagers();
        System.out.println("\n========================================");
        System.out.println("[Scarlet TTS] TTS Package Required");
        System.out.println("========================================");
        System.out.println("Scarlet requires " + getPackageDisplayName() + " for text-to-speech functionality.");
        System.out.println("\nDetected package managers:");
        for (LinuxPackageManagerDetector.PackageManager pm : managers)
            System.out.println("  \u2022 " + pm.displayName + ": " + pm.getFullInstallCommand().replace("{pkg}", pm.packageName));
        System.out.println("\nTTS features will be disabled until the package is installed.");
        System.out.println("========================================\n");
        return InstallDialogResult.HEADLESS_MODE;
    }

    private boolean handleHeadlessConsent()
    {
        List<LinuxPackageManagerDetector.PackageManager> managers = LinuxPackageManagerDetector.detectAllPackageManagers();
        System.out.println("\n[Scarlet TTS] " + getPackageDisplayName() + " is not installed.");
        System.out.println("[Scarlet TTS] Detected package managers:");
        for (LinuxPackageManagerDetector.PackageManager pm : managers)
            System.out.println("  " + pm.displayName + ": " + pm.getFullInstallCommand().replace("{pkg}", pm.packageName));
        System.out.println("[Scarlet TTS] TTS features will be disabled until the package is installed.\n");
        return false;
    }

    // -------------------------------------------------------------------------
    // Shared dialog helpers
    // -------------------------------------------------------------------------

    /** Show an informational JOptionPane, or print to stdout in headless mode. */
    private void showInfo(String title, String bodyHtml)
    {
        if (GraphicsEnvironment.isHeadless())
        {
            System.out.println("[Scarlet TTS] " + title);
            return;
        }
        Swing.invokeWait(() -> JOptionPane.showMessageDialog(
            this.parentComponent,
            "<html><div style='width:350px;padding:5px;'>" + bodyHtml + "</div></html>",
            title, JOptionPane.INFORMATION_MESSAGE));
    }

    /** Show an error JOptionPane, or print to stderr in headless mode. */
    private void showError(String title, String bodyHtml)
    {
        if (GraphicsEnvironment.isHeadless())
        {
            System.err.println("[Scarlet TTS] " + title);
            return;
        }
        Swing.invokeWait(() -> JOptionPane.showMessageDialog(
            this.parentComponent,
            "<html><div style='width:350px;padding:5px;'>" + bodyHtml + "</div></html>",
            title, JOptionPane.ERROR_MESSAGE));
    }

    private String getPackageDisplayName()
    {
        return this.platform == Platform.$NIX ? LINUX_PACKAGE_DISPLAY_NAME : "TTS Package";
    }

    private static boolean isEspeakInstalled()
    {
        try
        {
            return new ProcessBuilder("espeak", "--version").redirectErrorStream(true).start().waitFor() == 0;
        }
        catch (Exception ex) { return false; }
    }

}
