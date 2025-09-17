package net.sybyline.scarlet.util.tts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class LinuxTTSServiceTest {
    @Test
    @EnabledOnOs(OS.LINUX)
    void e2e_espeak_and_aplay() throws IOException, VoiceProviderSynthException {
        Path tmp = Files.createTempDirectory("tmp");
        VoiceProvider a = new EspeakVoiceProvider(tmp);
        Path out = a.Synthesize("hello world", "default");
        AplayAudioSink b = new AplayAudioSink();
        b.play(out);
        //noinspection ResultOfMethodCallIgnored
        tmp.toFile().delete();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.WINDOWS})
    void test_e2e_TTSFactory() throws IOException {
        Path tmp = Files.createTempDirectory("tmp");
        System.out.println(tmp.toString());
        TTSService a = TTSServiceFactory.build(tmp);
        a.submit("", "hello world");
    }
}