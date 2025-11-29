package net.sybyline.scarlet.util.tts;

import java.io.IOException;
import java.nio.file.Path;

public class AplayAudioSink implements AudioSink{

    // TODO: Add constructor that check for the existence of the binary
    @Override
    public void play(Path audioFile) {
        ProcessBuilder pb = new ProcessBuilder().command("aplay", audioFile.toString());
        try {
            Process p = pb.start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
