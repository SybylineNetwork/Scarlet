package net.sybyline.scarlet.util.tts;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import net.sybyline.scarlet.Scarlet;

public class LinuxEspeakTtsProvider implements TtsProvider
{

    public LinuxEspeakTtsProvider(Path dir) throws IOException, InterruptedException
    {
        this.dir = TtsProviderUtil.checkDir(dir);
        this.initVoices();
    }

    private final Path dir;
    private final List<String> voices = new ArrayList<>();
    private final AtomicReference<String> lastVoice = new AtomicReference<>();

    private void initVoices() throws IOException, InterruptedException
    {
        File initLog = this.dir.resolve(TtsProviderUtil.newRequestName()+"_init.log").toFile();
        Process proc = new ProcessBuilder()
            .command("espeak", "--version")
            .redirectError(initLog)
            .redirectOutput(initLog)
            .start();
        int exit = proc.waitFor();
        if (exit != 0)
        {
            Scarlet.LOG.error("eSpeak version check failed with exit code: {}", exit);
            throw new UnsupportedOperationException("eSpeak is not installed or is misconfigured: "+exit);
        }
        Scarlet.LOG.info("eSpeak initialized successfully");
        
        // This works as every linux machine should be POSIX compliant
        proc = new ProcessBuilder()
            .command("sh", "-c", "espeak --voices | awk 'NR>1 {print $2}'")
            .redirectErrorStream(true)
            .start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream())))
        {
            for (String line; (line = reader.readLine()) != null;)
                this.voices.add(line);
        }
        exit = proc.waitFor();
        if (exit != 0)
        {
            Scarlet.LOG.error("eSpeak voices enumeration failed with exit code: {}", exit);
            throw new UnsupportedOperationException("eSpeak is not installed or is misconfigured: "+exit);
        }
        
        if (this.voices.isEmpty())
        {
            Scarlet.LOG.warn("No voices found in eSpeak, using default");
            this.voices.add("default");
        }
        
        this.lastVoice.set(this.voices.get(0));
        Scarlet.LOG.info("eSpeak voices available: {}", this.voices);
    }

    private String updateOrLastVoice(String next)
    {
        return this.lastVoice.updateAndGet(prev -> next != null && this.voices.contains(next) ? next : prev);
    }

    @Override
    public List<String> voices()
    {
        return Collections.unmodifiableList(this.voices);
    }

    @Override
    public CompletableFuture<Path> speak(String text, String voiceId, float volume, float speed)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            // TtsProviderUtil.newRequestName() already includes .wav extension
            String request = TtsProviderUtil.newRequestName();
            Path outWav = this.dir.resolve(request);
            File outLog = this.dir.resolve(request.replaceAll("\\.wav$", "") + ".log").toFile();
            int amplitude = Math.max(0, Math.min(Math.round(volume * 100), 200)),
                wordsPerMinute = speed <= 1.0F
                ? Math.max(80, Math.min(80 + Math.round(speed * (175 - 80)), 175))
                : Math.max(175, Math.min(175 + Math.round((speed - 1.0F) * (450 - 175)), 175));
            String voice = this.updateOrLastVoice(voiceId);
            Scarlet.LOG.info("LinuxEspeakTtsProvider: Generating TTS - voice={}, amplitude={}, wpm={}, text='{}'", 
                voice, amplitude, wordsPerMinute, text);
            Scarlet.LOG.debug("LinuxEspeakTtsProvider: Output directory: {}, Output file: {}", this.dir, outWav);
            try
            {
                // Verify directory is writable before attempting
                if (!Files.isWritable(this.dir))
                {
                    Scarlet.LOG.error("LinuxEspeakTtsProvider: Output directory is not writable: {}", this.dir);
                    throw new RuntimeException("Output directory is not writable: " + this.dir);
                }
                
                Process proc = new ProcessBuilder()
                    .command("espeak", "-a", Integer.toUnsignedString(amplitude), "-s", Integer.toUnsignedString(wordsPerMinute), "-v", voice, "-w", outWav.toString(), text)
                    .redirectError(outLog)
                    .redirectOutput(outLog)
                    .start();
                int exit = proc.waitFor();
                if (exit != 0)
                {
                    Scarlet.LOG.error("LinuxEspeakTtsProvider: eSpeak failed with exit code {}, log file: {}", exit, outLog.getAbsolutePath());
                    throw new Exception("eSpeak returned a non-zero error code "+exit+" (log: "+outLog.getAbsolutePath()+")");
                }
                // Verify the file was actually created
                if (!Files.exists(outWav))
                {
                    Scarlet.LOG.error("LinuxEspeakTtsProvider: eSpeak reported success but file was not created: {}", outWav);
                    throw new RuntimeException("eSpeak failed to create output file: " + outWav);
                }
                Scarlet.LOG.info("LinuxEspeakTtsProvider: Successfully generated audio file {} ({} bytes)", outWav, Files.size(outWav));
            }
            catch (Exception ex)
            {
                Scarlet.LOG.error("LinuxEspeakTtsProvider: Failed to execute eSpeak", ex);
                throw new RuntimeException("Failed to execute eSpeak", ex);
            }
            return outWav;
        });
    }

    @Override
    public void close()
    {
    }

}