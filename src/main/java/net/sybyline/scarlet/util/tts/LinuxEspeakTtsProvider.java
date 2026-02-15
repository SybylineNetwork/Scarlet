package net.sybyline.scarlet.util.tts;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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
            throw new UnsupportedOperationException("eSpeak is not installed or is misconfigured: "+exit);
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
            throw new UnsupportedOperationException("eSpeak is not installed or is misconfigured: "+exit);
        this.lastVoice.set(this.voices.get(0));
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
            String request = TtsProviderUtil.newRequestName();
            Path outWav = this.dir.resolve(request + ".wav");
            File outLog = this.dir.resolve(request + ".log").toFile();
            int amplitude = Math.max(0, Math.min(Math.round(volume * 100), 200)),
                wordsPerMinute = speed <= 1.0F
                ? Math.max(80, Math.min(80 + Math.round(speed * (175 - 80)), 175))
                : Math.max(175, Math.min(175 + Math.round((speed - 1.0F) * (450 - 175)), 175));
            try
            {
                Process proc = new ProcessBuilder()
                    .command("espeak", "-a", Integer.toUnsignedString(amplitude), "-s", Integer.toUnsignedString(wordsPerMinute), "-v", this.updateOrLastVoice(voiceId), "-w", outWav.toString(), text)
                    .redirectError(outLog)
                    .redirectOutput(outLog)
                    .start();
                int exit = proc.waitFor();
                if (exit != 0)
                    throw new Exception("eSpeak returned a non-zero error code "+exit+" (log: "+outLog.getAbsolutePath()+")");
            }
            catch (Exception ex)
            {
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
