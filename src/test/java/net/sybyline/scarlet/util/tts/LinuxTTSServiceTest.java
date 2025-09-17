package net.sybyline.scarlet.util.tts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class LinuxTTSServiceTest {
    @Test
    void demoTestMethod() throws IOException, VoiceProviderSynthException {
        Path tmp = Files.createTempDirectory("tmp");
        System.out.println(tmp.toString());
        VoiceProvider a = new EspeakVoiceProvider(tmp);
        a.Synthesize("hello world", "english");
    }

    @Test
    void testLinuxTTSService() throws IOException {
        Path tmp = Files.createTempDirectory("tmp");
        System.out.println(tmp.toString());
        TTSService a = TTSServiceFactory.build(tmp);
        a.submit("", "hello world");
    }
}