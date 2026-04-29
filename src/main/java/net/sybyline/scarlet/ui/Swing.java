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
import com.formdev.flatlaf.FlatSystemProperties;

import net.sybyline.scarlet.util.Box;
import net.sybyline.scarlet.util.Platform;
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
            UIManager.setLookAndFeel(new FlatDarkLaf());
        }
        catch (Exception ex)
        {
            LOG.error("Exception setting system look and feel", ex);
        }
    }

    /**
     * Detects the appropriate UI scale on Linux by reading environment variables
     * set by the desktop environment. Returns null if no scaling hint is found
     * (i.e. the caller should not apply any automatic scaling).
     *
     * Priority order:
     *   1. GDK_SCALE          — set by GTK/GNOME desktop environments
     *   2. GDK_DPI_SCALE       — fractional DPI scaling (e.g. 1.5 for 144dpi on 96dpi base)
     *   3. QT_SCALE_FACTOR     — set by KDE/Qt desktop environments
     *   4. QT_AUTO_SCREEN_SCALE_FACTOR — KDE HiDPI enable flag (returns 2.0 if set to "1")
     */
    public static Float detectLinuxUIScale()
    {
        if (!Platform.CURRENT.is$nix())
            return null;

        // GDK_SCALE — integer multiplier (e.g. "2")
        String gdkScale = System.getenv("GDK_SCALE");
        if (gdkScale != null && !gdkScale.isEmpty())
        {
            try
            {
                float scale = Float.parseFloat(gdkScale);
                LOG.info("Detected HiDPI scale from GDK_SCALE={}: {}", gdkScale, scale);
                return scale;
            }
            catch (NumberFormatException ex)
            {
                LOG.warn("Could not parse GDK_SCALE='{}': {}", gdkScale, ex.getMessage());
            }
        }

        // GDK_DPI_SCALE — fractional multiplier (e.g. "1.5")
        String gdkDpiScale = System.getenv("GDK_DPI_SCALE");
        if (gdkDpiScale != null && !gdkDpiScale.isEmpty())
        {
            try
            {
                float scale = Float.parseFloat(gdkDpiScale);
                LOG.info("Detected HiDPI scale from GDK_DPI_SCALE={}: {}", gdkDpiScale, scale);
                return scale;
            }
            catch (NumberFormatException ex)
            {
                LOG.warn("Could not parse GDK_DPI_SCALE='{}': {}", gdkDpiScale, ex.getMessage());
            }
        }

        // QT_SCALE_FACTOR — KDE/Qt fractional multiplier (e.g. "1.5")
        String qtScale = System.getenv("QT_SCALE_FACTOR");
        if (qtScale != null && !qtScale.isEmpty())
        {
            try
            {
                float scale = Float.parseFloat(qtScale);
                LOG.info("Detected HiDPI scale from QT_SCALE_FACTOR={}: {}", qtScale, scale);
                return scale;
            }
            catch (NumberFormatException ex)
            {
                LOG.warn("Could not parse QT_SCALE_FACTOR='{}': {}", qtScale, ex.getMessage());
            }
        }

        // QT_AUTO_SCREEN_SCALE_FACTOR — KDE HiDPI enable flag; "1" means 2x
        String qtAuto = System.getenv("QT_AUTO_SCREEN_SCALE_FACTOR");
        if ("1".equals(qtAuto))
        {
            LOG.info("Detected HiDPI from QT_AUTO_SCREEN_SCALE_FACTOR=1, applying 2.0x scale");
            return 2.0f;
        }

        return null;
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
    public static void invokeOrLater(Runnable func)
    {
        if (SwingUtilities.isEventDispatchThread())
            func.run();
        else
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
