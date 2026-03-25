package net.sybyline.scarlet;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import javax.swing.JFrame;

import net.sybyline.scarlet.ScarletUI.ConnectedPlayer;
import net.sybyline.scarlet.util.Func;
import net.sybyline.scarlet.util.Func.V1.NE;

public interface IScarletUI extends Closeable
{

    static IScarletUI create(Scarlet scarlet)
    {
        return GraphicsEnvironment.isHeadless() ? new ScarletUIHeadless() : new ScarletUI(scarlet);
    }

    void jframe(Consumer<JFrame> edit);
    void setUIScale();
    void loadSettings();

    void fireSort();
    void clearInstance();
    void playerJoin(boolean initialPreamble, String id, String name, LocalDateTime joined, String advisory, Color text_color, int priority, boolean isRejoinFromPrev);
    void playerUpdate(boolean initialPreamble, String id, Func.V1.NE<ConnectedPlayer> update);
    void playerLeave(boolean initialPreamble, String id, String name, LocalDateTime left);

}

class ScarletUIHeadless implements IScarletUI
{
    public void close() {}
    public void jframe(Consumer<JFrame> edit) {}
    public void setUIScale() {}
    public void loadSettings() {}
    public void fireSort() {}
    public void clearInstance() {}
    public void playerJoin(boolean initialPreamble, String id, String name, LocalDateTime joined, String advisory, Color text_color, int priority, boolean isRejoinFromPrev) {}
    public void playerUpdate(boolean initialPreamble, String id, NE<ConnectedPlayer> update) {}
    public void playerLeave(boolean initialPreamble, String id, String name, LocalDateTime left) {}
}
