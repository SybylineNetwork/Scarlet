package net.sybyline.scarlet.ui;

import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatSystemProperties;

import net.sybyline.scarlet.util.Box;
import net.sybyline.scarlet.util.Throwables;

public class Swing
{

    public static void init()
    {
    }

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Swing");
    public static final Font MONOSPACED = Font.decode(Font.MONOSPACED);

    static
    {
        try
        {
            FlatLaf.setPreferredFontFamily("Arial Unicode MS");
            UIManager.setLookAndFeel(new FlatDarkLaf());
        }
        catch (Exception ex)
        {
            LOG.error("Exception setting system look and feel", ex);
        }
    }

    public static void scaleAll(float scale)
    {
        scale = (float)Math.sqrt(scale);
        System.setProperty(FlatSystemProperties.UI_SCALE, Float.toString(scale));
        UIDefaults defaults = UIManager.getDefaults();
        for (Enumeration<Object> e = defaults.keys(); e.hasMoreElements();)
        {
            Object key = e.nextElement();
            Object value = defaults.get(key);
            if (value instanceof Font)
            {
                Font font = (Font)value;
                int newSize = Math.round(font.getSize2D() * scale);
                if (value instanceof FontUIResource)
                {
                    defaults.put(key, new FontUIResource(font.getName(), font.getStyle(), newSize));
                }
                else
                {
                    defaults.put(key, new Font(font.getName(), font.getStyle(), newSize));
                }
            }
        }
    }

    public static void invokeLater(Runnable func)
    {
        SwingUtilities.invokeLater(func);
    }
    public static void invoke(Runnable func) throws InterruptedException
    {
        if (SwingUtilities.isEventDispatchThread())
            func.run();
        else try
        {
            SwingUtilities.invokeAndWait(func);
        }
        catch (InvocationTargetException itex)
        {
            throw Throwables.yeetCause(itex);
        }
    }
    public static <T> T get(Supplier<T> func) throws InterruptedException
    {
        Box<T> box = new Box<>();
        invoke(() -> box.set(func.get()));
        return box.get();
    }
    public static void invokeWait(Runnable func)
    {
        try
        {
            invoke(func);
        }
        catch (InterruptedException iex)
        {
            Thread.currentThread().interrupt();
        }
    }
    public static <T> T getWait(Supplier<T> func)
    {
        Box<T> box = new Box<>();
        invokeWait(() -> box.set(func.get()));
        return box.get();
    }
    public static <T> T getWait_x(Supplier<T> func)
    {
        return func.get();
    }

    private Swing()
    {
        throw new UnsupportedOperationException();
    }

}
