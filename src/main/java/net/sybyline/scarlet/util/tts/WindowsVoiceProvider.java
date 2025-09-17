package net.sybyline.scarlet.util.tts;

import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

public class WindowsVoiceProvider implements VoiceProvider {

    public WindowsVoiceProvider() {
        /*
         not booting into windows RN
         TODO:
         - install JACOB library
         - implement the speech API via COM https://learn.microsoft.com/en-us/previous-versions/windows/desktop/ms719576(v=vs.85)
         */
        throw new NotImplementedException();
    }
    @Override
    public Path Synthesize(@NotNull String text, @NotNull String voice) {
        return null;
    }

    @Override
    public List<String> ListAvailableVoices() {
        return List.of();
    }

}
