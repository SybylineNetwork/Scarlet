package net.sybyline.scarlet.util.tts;

import net.sybyline.scarlet.ScarletDiscord;

import java.nio.file.Path;

public class DiscordAudioSink implements AudioSink {
    private final ScarletDiscord scarletDiscord;
    public DiscordAudioSink(ScarletDiscord scarletDiscord) {
       this.scarletDiscord =scarletDiscord;
    }

    @Override
    public void play(Path audioFile) {
        this.scarletDiscord.submitAudio(audioFile.toFile());
    }
}
