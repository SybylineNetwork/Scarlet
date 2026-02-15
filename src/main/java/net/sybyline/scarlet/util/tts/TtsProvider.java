package net.sybyline.scarlet.util.tts;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import net.sybyline.scarlet.Scarlet;
import net.sybyline.scarlet.util.Platform;

public interface TtsProvider extends Closeable
{

    static TtsProvider select(Path dir)
    {
        try
        {
            switch (Platform.CURRENT)
            {
            case $NIX: return new LinuxEspeakTtsProvider(dir);
            case   NT: return new WinSapiTtsProvider(dir);
              default: break;
            }
        }
        catch (Exception ex)
        {
            Scarlet.LOG.error("Exception initializing TtsProvider for "+Platform.CURRENT+" platform, fallback to no-op implementation", ex);
        }
        return new NullTtsProvider(dir);
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
     * @param volume Optional, from {@code 0.0} (slow) through {@code 2.0} (fast), default {@code 1.0} <b>Not necessarily <i>linear</i></b>
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
