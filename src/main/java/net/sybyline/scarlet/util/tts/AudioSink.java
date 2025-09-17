package net.sybyline.scarlet.util.tts;

import java.nio.file.Path;

public interface AudioSink {
    void play(Path audioFile);
}
