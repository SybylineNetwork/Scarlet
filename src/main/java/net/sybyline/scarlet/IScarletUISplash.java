package net.sybyline.scarlet;

import java.awt.Color;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.io.Closeable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface IScarletUISplash extends Closeable
{

    static IScarletUISplash create(Scarlet scarlet)
    {
        return GraphicsEnvironment.isHeadless() ? new ScarletUISplashHeadless() : new ScarletUISplash(scarlet);
    }

    void splashText(String text);
    void splashSubtext(String text);
    default void queueFeedbackPopup(Component component, long durationMillis, String text)
    { this.queueFeedbackPopup(component, durationMillis, text, "", null, null); }
    default void queueFeedbackPopup(Component component, long durationMillis, String text, Color color)
    { this.queueFeedbackPopup(component, durationMillis, text, "", color, color); }
    default void queueFeedbackPopup(Component component, long durationMillis, String text, String subtext)
    { this.queueFeedbackPopup(component, durationMillis, text, subtext, null, null); }
    default void queueFeedbackPopup(Component component, long durationMillis, String text, String subtext, Color color)
    { this.queueFeedbackPopup(component, durationMillis, text, subtext, color, color); }
    void queueFeedbackPopup(Component component, long durationMillis, String text, String subtext, Color textcolor, Color subtextcolor);
}

class ScarletUISplashHeadless implements IScarletUISplash
{
    static final Logger LOG = LoggerFactory.getLogger("Splash");
    public void close() {}
    public void splashText(String text)
    {
        LOG.info("Primary: "+text);
    }
    public void splashSubtext(String text)
    {
        LOG.info("Secondary: "+text);
    }
    public void queueFeedbackPopup(Component component, long durationMillis, String text, String subtext, Color textcolor, Color subtextcolor)
    {
        LOG.info("Feedback: "+text+" ("+subtext+")");
    }
}
