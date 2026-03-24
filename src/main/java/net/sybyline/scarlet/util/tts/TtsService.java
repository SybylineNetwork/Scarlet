package net.sybyline.scarlet.util.tts;

import java.awt.Component;
import java.io.Closeable;
import java.io.File;
import java.text.Normalizer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

import net.sybyline.scarlet.Scarlet;
import net.sybyline.scarlet.ScarletDiscord;
import net.sybyline.scarlet.ScarletEventListener;

public class TtsService implements Closeable
{

    public TtsService(File dir, ScarletEventListener eventListener, ScarletDiscord discord)
    {
        this(dir, eventListener, discord, null);
    }

    public TtsService(File dir, ScarletEventListener eventListener, ScarletDiscord discord, Component parentComponent)
    {
        Scarlet.LOG.info("Initializing TTS service...");
        this.provider = TtsProvider.select(dir.toPath(), parentComponent);
        this.eventListener = eventListener;
        this.discord = discord;
        Scarlet.LOG.info("TTS service initialized with provider: {}", this.provider.getClass().getSimpleName());
    }

    final TtsProvider provider;
    final ScarletEventListener eventListener;
    final ScarletDiscord discord;

    /**
     * Single-threaded executor that serialises all audio playback.
     * Each TTS clip is played to completion before the next one starts,
     * preventing multiple clips from overlapping.
     */
    private final ExecutorService playbackExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TTS-Playback");
        t.setDaemon(true);
        return t;
    });

    public List<String> getInstalledVoices()
    {
        return this.provider.voices();
    }

    public CompletableFuture<Void> submit(String marker, String text)
    {
        text = Normalizer.normalize(text, Normalizer.Form.NFKC);
        final String voiceName = this.eventListener.getTtsVoiceName();
        Scarlet.LOG.info("TTS: Submitting text '{}' with voice '{}' for marker {}", text, voiceName, marker);
        final String finalText = text;
        return this.provider
            .speak(finalText, voiceName, 1.0F, 1.0F)
            .thenCompose(path ->
            {
                // A null path means the voice failed (e.g. an Online/Natural voice that
                // cannot write to a file stream). Trigger fallback voice selection and retry
                // the same text with that voice so the user's command isn't lost.
                if (path == null)
                {
                    String fallbackVoice = this.eventListener.fallbackTtsVoice(voiceName);
                    if (fallbackVoice != null)
                    {
                        Scarlet.LOG.info("TTS({}): Retrying with fallback voice '{}'", marker, fallbackVoice);
                        return this.provider.speak(finalText, fallbackVoice, 1.0F, 1.0F);
                    }
                    // No fallback available — complete with null, exceptionally handler logs it
                    return CompletableFuture.completedFuture(null);
                }
                return CompletableFuture.completedFuture(path);
            })
            .thenAcceptAsync(path ->
            {
                if (path == null)
                {
                    Scarlet.LOG.warn("TTS({}): Provider returned null path, audio generation may have failed", marker);
                    return;
                }
                if (this.eventListener.getTtsUseDefaultAudioDevice())
                {
                    // Route to system default audio output instead of Discord.
                    // playOnSystemAudio blocks until the clip finishes, so the
                    // single-threaded playbackExecutor guarantees clips play serially.
                    playOnSystemAudio(marker, path.toFile());
                }
                else
                {
                    boolean submitted = this.discord.submitAudio(path.toFile());
                    Scarlet.LOG.info("TTS({}): Audio submitted to Discord, success={}, file={}", marker, submitted, path);
                    // NOTE: The .wav file is intentionally kept on disk so that Discord and other
                    // consumers have time to finish reading/streaming it. Previously, Files.deleteIfExists(path)
                    // was called here immediately after submitAudio(), causing a race condition where the file
                    // was gone before Discord could access it.
                }
            }, this.playbackExecutor)
            .exceptionally(ex -> {
                Scarlet.LOG.error("TTS({}): Exception during TTS processing", marker, ex);
                return null;
            });
    }

    /**
     * Plays a WAV file through the system default audio output device using javax.sound.sampled.
     * Blocks the calling thread until playback is complete, so the serial playbackExecutor
     * prevents any two clips from overlapping.
     */
    private static void playOnSystemAudio(String marker, File file)
    {
        Scarlet.LOG.info("TTS({}): Playing on system audio device: {}", marker, file);
        try
        {
            // Open the AudioInputStream first, then open the Clip with it.
            // Clip.open() fully buffers the audio into memory, so the stream
            // can be safely closed immediately afterwards.
            AudioInputStream ais = AudioSystem.getAudioInputStream(file);
            Clip clip;
            try
            {
                clip = AudioSystem.getClip();
                clip.open(ais);
            }
            finally
            {
                // Safe to close now — Clip has already copied the data
                ais.close();
            }

            // Use a Semaphore to block until the STOP event fires, ensuring
            // this method does not return until the clip has finished playing.
            Semaphore done = new Semaphore(0);
            clip.addLineListener(event ->
            {
                if (event.getType() == LineEvent.Type.STOP)
                {
                    clip.close();
                    Scarlet.LOG.info("TTS({}): System audio playback complete", marker);
                    done.release();
                }
            });
            clip.start();

            // Block until playback finishes (or give up after a generous timeout).
            long timeoutSeconds = Math.max(30L, (clip.getMicrosecondLength() / 1_000_000L) + 10L);
            if (!done.tryAcquire(timeoutSeconds, TimeUnit.SECONDS))
            {
                Scarlet.LOG.warn("TTS({}): Playback timed out after {}s, forcing clip close", marker, timeoutSeconds);
                clip.close();
            }
        }
        catch (Exception ex)
        {
            Scarlet.LOG.error("TTS({}): Exception playing on system audio device", marker, ex);
        }
    }

    @Override
    public void close()
    {
        this.provider.close();
        this.playbackExecutor.shutdown();
        try
        {
            if (!this.playbackExecutor.awaitTermination(5, TimeUnit.SECONDS))
                this.playbackExecutor.shutdownNow();
        }
        catch (InterruptedException ex)
        {
            this.playbackExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}