package net.sybyline.scarlet.util.tts;


import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EspeakVoiceProvider implements VoiceProvider {

    private final Path outputDir;
    private final Path logDir;
    private final List<String> availableVoices;
    private final Instant initializedAt;

    // this expects the path to be a directory and be writable
    public EspeakVoiceProvider(Path outputDir) throws IOException {
       if (!Files.isDirectory(outputDir)) {
          throw new IllegalArgumentException("given path is not a directory: " + outputDir);
       }

       if (!Files.isWritable(outputDir)) {
           throw new RuntimeException("given path is not writable: " + outputDir);
       }
       this.initializedAt = Instant.now();

        this.outputDir = outputDir;
        Path logDir = Paths.get(outputDir.toString(), "espeak_logs");
        Files.createDirectories(logDir);
        this.logDir = logDir;

        try {
            File initLogFile = Paths.get(this.logDir.toString(), this.initializedAt.getNano() + "_espeak_init.log").toFile();
            ProcessBuilder pb = new ProcessBuilder().command("espeak", "--version").redirectError(initLogFile).redirectOutput(initLogFile);
            Process p = pb.start();
            if (p.waitFor() != 0) {
               throw new RuntimeException("espeak does not seem to be installed or is miss configured");
            }
            // This works as every linux machine should be POSIX compliant
            pb = new ProcessBuilder().command("sh", "-c", "espeak --voices=en | tail -n +2 | sed 's/^ //' | sed -e 's/  \\+/ /g' | cut -d' ' -f4 | sort -u").redirectErrorStream(true);
            p = pb.start();
            List<String> availableVoices = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
               String line;
               while((line = reader.readLine()) != null){
                   availableVoices.add(line);
               }
            }
            if (p.waitFor() != 0) {
                throw new RuntimeException("espeak does not seem to be installed or is miss configured");
            }
            this.availableVoices = availableVoices;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Path Synthesize(@NotNull String text, @NotNull String voice) throws VoiceProviderSynthException {
        UUID invocationID = UUID.randomUUID();
        Path outFilePath = Paths.get(this.outputDir.toString(), invocationID + ".wav");
        File logFile = Paths.get(this.logDir.toString(), invocationID + ".log").toFile();

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        ProcessBuilder pb = new ProcessBuilder().command("espeak", "-v", voice, "-w", outFilePath.toString(), normalized).redirectError(logFile).redirectOutput(logFile);
        try {
            Process p = pb.start();
            if (p.waitFor() != 0) {
                throw new VoiceProviderSynthException("espeak returned non zero error code. check the logfile: " + logFile.getAbsolutePath());
            }
        } catch (IOException | InterruptedException e) {
            throw new VoiceProviderSynthException("failed to execute espeak", e);
        }

        return outFilePath;
    }

    @Override
    public List<String> ListAvailableVoices() {
        return this.availableVoices;
    }
}
