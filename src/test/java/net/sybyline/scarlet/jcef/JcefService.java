package net.sybyline.scarlet.jcef;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefFrame;
import org.cef.browser.ext.CefAppExt;
import org.cef.browser.ext.CefBrowserExtGL;
import org.cef.browser.ext.CefClientExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jogamp.opengl.awt.GLCanvas;

import net.sybyline.scarlet.ui.Swing;
import net.sybyline.scarlet.util.MiscUtils;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.EnumProgress;
import me.friwi.jcefmaven.UnsupportedPlatformException;

public class JcefService implements AutoCloseable
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/JcefService");

//    static final int DEFAULT_WIDTH = 1920, DEFAULT_HEIGHT = 1080;
    static final int DEFAULT_WIDTH = 1280, DEFAULT_HEIGHT = 768;

    public JcefService(File jcefDir) throws IOException, UnsupportedPlatformException, InterruptedException, CefInitializationException
    {
        this.browsers = new ArrayList<>();
        this.cef_app_ext = new CefAppExt() {
            @Override
            protected void setup(CefAppBuilder jcefbuilder, CefSettings jcefsettings) {
                jcefbuilder.setInstallDir(new File(jcefDir, "bundle"));
                jcefsettings.cache_path = new File(jcefDir, "cache").getAbsolutePath();
                jcefsettings.windowless_rendering_enabled = true;
            }
            @Override
            protected void stateHasChanged(CefAppState state) {
                if (state == CefAppState.TERMINATED)
                    JcefService.this.close();
            }
            @Override
            protected void handleProgress(EnumProgress state, float percent) {
                LOG.info(percent < 0 ? String.format("Jcef progress: %s", state) : String.format("Jcef progress: %s - %03.5f", state, percent));
            }
        };
        this.cef_client_ext = this.cef_app_ext.createClientExt();
        this.cef_client_ext.addMessageRouter("cefQuery", "cefQueryCancel");
        this.masterFrame = Swing.getWait(JFrame::new);
        this.masterScroll = Swing.getWait(JScrollPane::new);
        this.masterCanvas = Swing.getWait(() ->
        {
            GLCanvas canvas = new GLCanvas();
            canvas.setEnabled(true);
            canvas.setBounds(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            canvas.setVisible(true);
            this.masterScroll.setEnabled(true);
            this.masterScroll.setLayout(null);
            this.masterScroll.add(canvas);
            this.masterScroll.setBounds(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            this.masterScroll.setVisible(true);
            this.masterFrame.setEnabled(true);
            this.masterFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            this.masterFrame.setBounds(8192, 8192, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            this.masterFrame.setFocusable(false);
            this.masterFrame.setFocusableWindowState(false);
            this.masterFrame.setUndecorated(true);
            this.masterFrame.setOpacity(0.0F);
            this.masterFrame.setContentPane(this.masterScroll);
            this.masterFrame.setVisible(true);
            return canvas;
        });
    }

    final List<JcefBrowser> browsers;
    final CefAppExt cef_app_ext;
    final CefClientExt cef_client_ext;
    final JFrame masterFrame;
    final JScrollPane masterScroll;
    final GLCanvas masterCanvas;

    public final CefAppExt getAppExt()
    {
        return this.cef_app_ext;
    }
    public final CefApp getApp()
    {
        return this.cef_app_ext.getApp();
    }
    public final CefClientExt getClientExt()
    {
        return this.cef_client_ext;
    }
    public final CefClient getClient()
    {
        return this.cef_client_ext.getClient();
    }

    public synchronized JcefBrowser create(String startUrl, boolean hidden)
    {
        return new JcefBrowser(startUrl, hidden);
    }
    public class JcefBrowser extends CefBrowserExtGL implements AutoCloseable
    {
        protected boolean visible;
        protected Rectangle bounds;
        protected String title, url;
        protected JcefBrowserFrame jcefBrowserFrame;
        public class JcefBrowserFrame extends JFrame
        {
            private static final long serialVersionUID = 6649915484066859147L;
            public JcefBrowserFrame()
            {
                Rectangle bounds = new Rectangle(JcefBrowser.this.bounds);
//                JScrollPane scroll = this.scroll = new JScrollPane();
                JPanel panel = new JPanel();
                GLCanvas component = JcefBrowser.this.component;
                Color clear = new Color(0, 0, 0, 0);
                
                component.setSize(bounds.width, bounds.height);
                component.setBackground(clear);
                component.setEnabled(true);
                component.setVisible(true);

//                scroll.setLayout(null);
//                scroll.setSize(bounds.width, bounds.height);
//                scroll.setBackground(clear);
//                scroll.add(component);
//                scroll.setEnabled(true);
//                scroll.setVisible(true);
                panel.setLayout(new BorderLayout());
                panel.setSize(bounds.width, bounds.height);
                panel.setBackground(clear);
                panel.add(component, BorderLayout.CENTER);
                panel.setEnabled(true);
                panel.setVisible(true);
                
                this.setContentPane(panel);
                this.setEnabled(true);
                this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                
                if (JcefBrowser.this.visible)
                {
                    this.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {
//                            Swing.invokeLater(() -> JcefBrowser.this.setVisible(false));
                        }
                    });
                }
                else
                {
                    bounds.setLocation(8192, 8192);
                    this.setFocusable(false);
                    this.setFocusableWindowState(false);
                    this.setUndecorated(true);
                    this.setOpacity(0.0F);
                }
                this.setBounds(bounds);
                this.setVisible(true);
            }
//            protected final JScrollPane scroll;
            protected void setBrowserFrameSize(int width, int height)
            {
                JcefBrowser.this.bounds.setSize(width, height);
                this.setSize(width, height);
//                this.scroll.setSize(width, height);
            }
        }
        protected JcefBrowser(String startURL, boolean hidden)
        {
            super(JcefService.this.cef_client_ext, startURL);
            this.visible = !hidden;
            this.bounds = new Rectangle(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            this.title = "title";
            this.url = startURL;
            this.focused = true;
            this.component.setSharedAutoDrawable(JcefService.this.masterCanvas);
            this.jcefBrowserFrame = Swing.getWait(JcefBrowserFrame::new);
            
            this.updateTitle();
            synchronized (JcefService.this)
            {
                JcefService.this.browsers.add(this);
            }
        }
        @Override
        public void onPaint(boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height)
        {
            synchronized (JcefService.this)
            {
                super.onPaint(popup, dirtyRects, buffer, width, height);
            }
        }
        public synchronized void setVisible(boolean visible)
        {
            if (this.visible == visible)
                return;
            this.visible = !visible;
            JcefBrowserFrame jcefBrowserFramePrev = this.jcefBrowserFrame;
            try
            {
                jcefBrowserFramePrev.removeAll();
                this.jcefBrowserFrame = Swing.getWait(JcefBrowserFrame::new);
            }
            finally
            {
                jcefBrowserFramePrev.dispose();
            }
            this.updateTitle();
        }
        public JFrame getJFrame()
        {
            return this.jcefBrowserFrame;
        }
        public boolean isVisible()
        {
            return this.visible;
        }
        public void setBounds(Rectangle bounds)
        {
            this.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        public void setBounds(int x, int y, int width, int height)
        {
            this.setLocation(x, y);
            this.setSize(width, height);
        }
        public Rectangle getBounds()
        {
            return this.jcefBrowserFrame.getBounds();
        }
        public void setLocation(Point p)
        {
            this.setLocation(p.x, p.y);
        }
        public void setLocation(int x, int y)
        {
            this.jcefBrowserFrame.setLocation(x, y);
        }
        public void setX(int x)
        {
            this.setLocation(x, this.getY());
        }
        public void setY(int y)
        {
            this.setLocation(this.getX(), y);
        }
        public Point getLocation()
        {
            return this.jcefBrowserFrame.getLocation();
        }
        public int getX()
        {
            return this.jcefBrowserFrame.getX();
        }
        public int getY()
        {
            return this.jcefBrowserFrame.getY();
        }
        public void setSize(Dimension dim)
        {
            this.setSize(dim.width, dim.height);
        }
        public void setSize(int width, int height)
        {
            this.jcefBrowserFrame.setBrowserFrameSize(width, height);
            this.component.setSize(width, height);
        }
        public void setWidth(int width)
        {
            this.setSize(width, this.getHeight());
        }
        public void setHeight(int height)
        {
            this.setSize(this.getWidth(), height);
        }
        public Dimension getSize()
        {
            return this.jcefBrowserFrame.getSize();
        }
        public int getWidth()
        {
            return this.jcefBrowserFrame.getWidth();
        }
        public int getHeight()
        {
            return this.jcefBrowserFrame.getHeight();
        }
        // Display handler
        @Override
        public void onAddressChange(CefFrame frame, String url)
        {
            this.url = url;
            this.updateTitle();
        }
        @Override
        public void onTitleChange(String title)
        {
            this.title = title;
            this.updateTitle();
        }
        // misc
        protected String makeTitle()
        {
            return String.format("%s - %s", this.title, this.url);
        }
        protected final void updateTitle()
        {
            this.jcefBrowserFrame.setTitle(this.makeTitle());
        }
        @Override
        public synchronized void close()
        {
            this.jcefBrowserFrame.dispose();
            this.close(true);
            synchronized (JcefService.this)
            {
                JcefService.this.browsers.remove(this);
            }
        }
    }

    private boolean closed = false;
    @Override
    public synchronized void close()
    {
        if (this.closed)
            return;
        this.closed = true;
        MiscUtils.close(this.browsers);
        this.masterFrame.dispose();
        this.cef_client_ext.dispose();
        this.cef_app_ext.dispose();
    }

}
