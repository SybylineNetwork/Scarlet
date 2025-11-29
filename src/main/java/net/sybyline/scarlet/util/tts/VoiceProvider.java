package net.sybyline.scarlet.util.tts;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

public interface VoiceProvider {
    /*
     Why is this so barebone?
     lexicons are never used + the information on what voice should be used is not owned by the executing
     service, but by the caller. This cleans up the interface and the responsibility of the services.
     */
    Path Synthesize(@NotNull String text, @NotNull String voice) throws VoiceProviderSynthException;
    List<String> ListAvailableVoices();



}
