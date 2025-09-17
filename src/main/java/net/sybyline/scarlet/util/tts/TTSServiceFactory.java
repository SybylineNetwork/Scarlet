package net.sybyline.scarlet.util.tts;

import net.sybyline.scarlet.ScarletDiscord;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TTSServiceFactory {
    public static TTSService build(Path dir) {
        return build(dir, null);
    }

    public static TTSService build(Path dir, ScarletDiscord scarletDiscord) {
        if (SystemUtils.IS_OS_WINDOWS) {
            // TODO: add a discord + general output sink
            return new TTSService(new WindowsVoiceProvider(), null, null);
        }

        if (SystemUtils.IS_OS_LINUX) {
            try {
                List<AudioSink> sinks = new ArrayList<>();
                sinks.add(new AplayAudioSink());
                if (scarletDiscord != null) {
                    sinks.add(new DiscordAudioSink(scarletDiscord));
                }

                return new TTSService(
                        new EspeakVoiceProvider(dir),
                        sinks,
                        "en-us"
                );
            } catch (IOException e) {
                throw new RuntimeException("failed to create LinuxTTSService", e);
            }
        }

        throw new UnsupportedOperationException("Unsupported Operating System for TTSService");
    }
}
