package net.sybyline.scarlet.util.tts;

import java.awt.Component;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import net.sybyline.scarlet.Scarlet;
import net.sybyline.scarlet.util.Platform;
import net.sybyline.scarlet.util.XdgOpenInstallDialogs;

public interface TtsProvider extends Closeable
{

    /**
     * Select and initialize the appropriate TTS provider for the current platform.
     * This method will:
     * 1. Check if the required TTS package is installed
     * 2. If not installed, show confirmation dialogs before attempting installation
     * 3. Block until the user responds to any dialogs
     * 
     * @param dir The directory for TTS output files
     * @return The appropriate TtsProvider instance
     */
    static TtsProvider select(Path dir)
    {
        return select(dir, null);
    }

    /**
     * Select and initialize the appropriate TTS provider for the current platform.
     * 
     * @param dir The directory for TTS output files
     * @param parentComponent Optional parent component for dialogs (can be null)
     * @return The appropriate TtsProvider instance
     */
    static TtsProvider select(Path dir, Component parentComponent)
    {
        try
        {
            switch (Platform.CURRENT)
            {
            case $NIX:
                return selectLinuxProvider(dir, parentComponent);
            case NT:
                return selectWindowsProvider(dir);
            default:
                Scarlet.LOG.info("TTS not supported on platform: {}", Platform.CURRENT);
                return new NullTtsProvider(dir);
            }
        }
        catch (Exception ex)
        {
            Scarlet.LOG.error("Exception initializing TtsProvider for "+Platform.CURRENT+" platform, fallback to no-op implementation", ex);
        }
        return new NullTtsProvider(dir);
    }

    /**
     * Select TTS provider for Linux with package installation confirmation.
     */
    static TtsProvider selectLinuxProvider(Path dir, Component parentComponent)
    {
        Scarlet.LOG.info("Initializing TTS for Linux platform...");
        
        // Create dialog manager
        TtsPackageInstallDialogs dialogs = new TtsPackageInstallDialogs(Platform.$NIX, parentComponent);

        // Check xdg-open availability (needed for folder/link browsing on Linux)
        Scarlet.LOG.info("Checking xdg-open availability...");
        if (XdgOpenInstallDialogs.isXdgOpenInstalled())
        {
            Scarlet.LOG.info("xdg-open is already installed, folder and link browsing is available.");
        }
        else
        {
            Scarlet.LOG.info("xdg-open is not installed. Showing installation confirmation dialogs...");
            XdgOpenInstallDialogs.InstallDialogResult xdgResult = XdgOpenInstallDialogs.showInstallFlowIfNeeded();
            switch (xdgResult)
            {
                case INSTALL_APPROVED_SUCCESS:
                    Scarlet.LOG.info("xdg-utils installed successfully, folder and link browsing is now available.");
                    break;
                case INSTALL_APPROVED_FAILED:
                    Scarlet.LOG.warn("xdg-utils installation failed. Folder and link browsing will not be available.");
                    break;
                case INSTALL_DECLINED:
                    Scarlet.LOG.info("xdg-utils installation declined by user. Folder and link browsing will not be available.");
                    break;
                case HEADLESS_MODE:
                    Scarlet.LOG.info("Running headless — xdg-utils check skipped.");
                    break;
                default:
                    break;
            }
        }
        
        // Check if espeak is already installed
        if (TtsPackageInstallDialogs.isPackageInstalled(Platform.$NIX))
        {
            Scarlet.LOG.info("eSpeak is already installed, initializing TTS provider...");

            try
            {
                return new LinuxEspeakTtsProvider(dir);
            }
            catch (Exception ex)
            {
                Scarlet.LOG.error("Failed to initialize LinuxEspeakTtsProvider even though espeak is installed", ex);
                return new NullTtsProvider(dir);
            }
        }
        
        // Package not installed - show confirmation dialogs
        Scarlet.LOG.info("eSpeak is not installed. Showing installation confirmation dialogs...");
        
        // Show the install flow (this blocks until user responds)
        TtsPackageInstallDialogs.InstallDialogResult result = dialogs.showInstallFlow();
        
        switch (result)
        {
            case ALREADY_INSTALLED:
                // Shouldn't happen since we checked above, but handle it
                try
                {
                    return new LinuxEspeakTtsProvider(dir);
                }
                catch (Exception ex)
                {
                    Scarlet.LOG.error("Failed to initialize LinuxEspeakTtsProvider", ex);
                    return new NullTtsProvider(dir);
                }
                
            case INSTALL_APPROVED_SUCCESS:
                // User approved and installation succeeded
                Scarlet.LOG.info("eSpeak installation successful, initializing TTS provider...");
                try
                {
                    return new LinuxEspeakTtsProvider(dir);
                }
                catch (Exception ex)
                {
                    Scarlet.LOG.error("Failed to initialize LinuxEspeakTtsProvider after successful installation", ex);
                    return new NullTtsProvider(dir);
                }
                
            case INSTALL_APPROVED_FAILED:
                // User approved but installation failed
                Scarlet.LOG.warn("eSpeak installation failed, TTS will be disabled");
                return new NullTtsProvider(dir);
                
            case INSTALL_DECLINED:
                // User declined installation
                Scarlet.LOG.info("User declined eSpeak installation, TTS will be disabled");
                return new NullTtsProvider(dir);
                
            case HEADLESS_MODE:
                // Running in headless mode - still try to initialize espeak if available
                Scarlet.LOG.info("Running in headless mode, attempting to initialize TTS without dialogs");
                try
                {
                    return new LinuxEspeakTtsProvider(dir);
                }
                catch (Exception ex)
                {
                    Scarlet.LOG.warn("TTS not available in headless mode: {}", ex.getMessage());
                    return new NullTtsProvider(dir);
                }
                
            default:
                Scarlet.LOG.warn("Unknown install dialog result: {}, TTS will be disabled", result);
                return new NullTtsProvider(dir);
        }
    }

    /**
     * Select TTS provider for Windows (uses built-in SAPI).
     */
    static TtsProvider selectWindowsProvider(Path dir)
    {
        Scarlet.LOG.info("Initializing TTS for Windows platform...");
        // Use reflection to load Windows-specific provider to avoid ClassNotFoundException on Linux
        // The WinSapiTtsProvider uses Windows-specific JNA classes that don't exist on other platforms
        try
        {
            Class<?> winSapiClass = Class.forName("net.sybyline.scarlet.util.tts.WinSapiTtsProvider");
            @SuppressWarnings("unchecked")
            java.lang.reflect.Constructor<? extends TtsProvider> constructor = 
                (java.lang.reflect.Constructor<? extends TtsProvider>) winSapiClass.getConstructor(Path.class);
            return constructor.newInstance(dir);
        }
        catch (Exception ex)
        {
            Scarlet.LOG.error("Failed to load WinSapiTtsProvider via reflection", ex);
            return new NullTtsProvider(dir);
        }
    }

    @Override
    void close();

    /**
     * @return A list of all the voices supported by this provider
     */
    List<String> voices();

    /**
     * @param text The text to speak
     * @param voiceId The id of the voice to use
     * @param volume Optional, from {@code 0.0} (silent) through {@code 1.0} (max), default {@code 1.0} <b>Not necessarily <i>linear</i></b>
     * @param speed Optional, from {@code 0.0} (slow) through {@code 2.0} (fast), default {@code 1.0} <b>Not necessarily <i>linear</i></b>
     * @return The path to the file containing the generated audio
     */
    CompletableFuture<Path> speak(String text, String voiceId, float volume, float speed);

}

interface TtsProviderUtil
{
    static Path checkDir(Path path)
    {
        if (!Files.isDirectory(path)) try
        {
            Files.createDirectories(path);
        }
        catch (IOException ioex)
        {
            throw new IllegalArgumentException("Failed to create directory: " + path, ioex);
        }
        if (!Files.isWritable(path))
            throw new RuntimeException("Directory not writable: " + path);
        return path;
    }
    AtomicLong requestCounter = new AtomicLong();
    static String newRequestName()
    {
        return String.format("tts_0x%016x_0x%016x.wav", System.currentTimeMillis(), requestCounter.getAndIncrement());
    }
}