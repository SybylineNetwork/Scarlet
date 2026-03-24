package org.cef.browser;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.OS;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

import com.jogamp.nativewindow.NativeSurface;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.GLBuffers;

public class CefBrowserOsrProxy extends CefBrowser_N implements CefRenderHandler
{

    public static final GLCapabilities GLCAPS = new GLCapabilities(GLProfile.getMaxFixedFunc(true));

    protected final CefRendererProxy renderer;
    public final GLCanvas canvas;
    private long window_handle = 0;
    private boolean justCreated = false;
    private Rectangle browser_rect = new Rectangle(0, 0, 1, 1); // Work around CEF issue #1437.
    private Point screenPoint = new Point(0, 0);
    private double scaleFactor = 1.0;
    private int depth = 32,
                depth_per_component = 8;
    private CefBrowserSettings settings;
    private List<Consumer<CefPaintEvent>> onPaintListeners = new CopyOnWriteArrayList<>();

    public CefBrowserOsrProxy(CefClient client, String url, boolean transparent, CefRequestContext context, CefBrowserSettings settings)
    {
        this(client, url, transparent, context, null, null, settings);
    }
    private CefBrowserOsrProxy(CefClient client, String url, boolean transparent, CefRequestContext context, CefBrowserOsrProxy parent, Point inspectAt, CefBrowserSettings settings)
    {
        super(client, url, context, parent, inspectAt, settings);
        this.settings = settings;
        this.renderer = new CefRendererProxy(transparent, this::postPaint);
        this.canvas = this.createGLCanvas();
    }

    // Custom

    public void sendMouseEvent(int id, long when, int mods, int x, int y, int xabs, int yabs, int clickCount, boolean popupTrigger, int button)
    {
        this.sendMouseEvent(new MouseEvent(this.canvas, id, when, mods, x, y, xabs, yabs, clickCount, popupTrigger, button));
    }
    public void sendMouseWheelEvent(int id, long when, int mods, int x, int y, int xabs, int yabs, int clickCount, boolean popupTrigger, int scrollType, int scrollAmount, int wheelRotation, double preciseWheelRotation)
    {
        this.sendMouseWheelEvent(new MouseWheelEvent(this.canvas, id, when, mods, x, y, xabs, yabs, clickCount, popupTrigger, scrollType, scrollAmount, wheelRotation, preciseWheelRotation));
    }
    public void sendKeyEvent(int id, long when, int mods, int keyCode, char keyChar, int keyLocation)
    {
        this.sendKeyEvent(new KeyEvent(this.canvas, id, when, mods, keyCode, keyChar, keyLocation));
    }
    protected void postPaint(CefRendererProxy renderer)
    {
    }

    // Copied

    @Override
    public void createImmediately()
    {
        this.justCreated = true;
        this.createBrowserIfRequired(false); // Create the browser immediately.
    }

    @Override
    public Component getUIComponent()
    {
        return this.canvas;
    }

    @Override
    public CefRenderHandler getRenderHandler()
    {
        return this;
    }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser_N parent, Point inspectAt)
    {
        return new CefBrowserOsrProxy(client, url, this.renderer.isTransparent(), context, this, inspectAt, this.settings);
    }

    private synchronized long getWindowHandle()
    {
        if (this.window_handle == 0)
        {
            NativeSurface surface = this.canvas.getNativeSurface();
            if (surface != null)
            {
                surface.lockSurface();
                this.window_handle = this.getWindowHandle(surface.getSurfaceHandle());
                surface.unlockSurface();
                assert (this.window_handle != 0);
            }
        }
        return this.window_handle;
    }

    @SuppressWarnings("serial")
    private GLCanvas createGLCanvas()
    {
        GLCanvas canvas = new GLCanvas(GLCAPS)
        {
            private Method scaleFactorAccessor = null;
            private boolean removed = true;
            @Override
            public void paint(Graphics g)
            {
                CefBrowserOsrProxy.this.createBrowserIfRequired(true);
                if (g instanceof Graphics2D)
                {
                    Graphics2D g2d = (Graphics2D)g;
                    GraphicsConfiguration config = g2d.getDeviceConfiguration();
                    CefBrowserOsrProxy.this.depth = config.getColorModel().getPixelSize();
                    CefBrowserOsrProxy.this.depth_per_component = config.getColorModel().getComponentSize()[0];
                    if (OS.isMacintosh() && System.getProperty("java.runtime.version").startsWith("1.8"))
                    {   // This fixes a weird thing on MacOS: the scale factor being read from getTransform().getScaleX() is incorrect for Java 8 VMs; it is always
                        // 1, even though Retina display scaling of window sizes etc. is definitely ongoing somewhere in the lower levels of AWT. This isn't too big
                        // of a problem for us, because the transparent scaling handles the situation, except for one thing: the screenshot-grabbing code below, which
                        // reads from the OpenGL context, must know the real scale factor, because the image to be read is larger by that factor and thus a bigger
                        // buffer is required. This is why there's some admittedly-ugly reflection magic going on below that's able to get the real scale factor.
                        // All of this is not relevant for either Windows or MacOS JDKs > 8, for which the official "getScaleX()" approach works fine.
                        try
                        {
                            if (this.scaleFactorAccessor == null)
                                this.scaleFactorAccessor = this.getClass().getClassLoader().loadClass("sun.awt.CGraphicsDevice").getDeclaredMethod("getScaleFactor");
                            Object factor = this.scaleFactorAccessor.invoke(config.getDevice());
                            CefBrowserOsrProxy.this.scaleFactor = factor instanceof Integer
                                ? ((Integer)factor).doubleValue()
                                : 1.0;
                        }
                        catch (ReflectiveOperationException | IllegalArgumentException | SecurityException exc)
                        {
                            CefBrowserOsrProxy.this.scaleFactor = 1.0;
                        }
                    }
                    else
                        CefBrowserOsrProxy.this.scaleFactor = g2d.getTransform().getScaleX();
                }
                super.paint(g);
            }
            @Override
            public void addNotify()
            {
                super.addNotify();
                if (!this.removed)
                    return;
                CefBrowserOsrProxy.this.notifyAfterParentChanged();
                this.removed = false;
            }
            @Override
            public void removeNotify()
            {
                if (!this.removed)
                {
                    if (!CefBrowserOsrProxy.this.isClosed())
                        CefBrowserOsrProxy.this.notifyAfterParentChanged();
                    this.removed = true;
                }
                super.removeNotify();
            }
        };
        // The GLContext will be re-initialized when changing displays, resulting in calls to dispose/init/reshape.
        canvas.addGLEventListener(new GLEventListener()
        {
            @Override
            public void reshape(GLAutoDrawable glautodrawable, int x, int y, int width, int height)
            {
                int newWidth = width;
                int newHeight = height;
                if (OS.isMacintosh())
                {
                    // HiDPI display scale correction support code. For some reason this does seem to be necessary on MacOS only.
                    // If doing this correction on Windows, the browser content would be too small and in the lower left corner of the canvas only.
                    newWidth = (int) (width / CefBrowserOsrProxy.this.scaleFactor);
                    newHeight = (int) (height / CefBrowserOsrProxy.this.scaleFactor);
                }
                CefBrowserOsrProxy.this.browser_rect.setBounds(x, y, newWidth, newHeight);
                CefBrowserOsrProxy.this.screenPoint = canvas.getLocationOnScreen();
                CefBrowserOsrProxy.this.wasResized(newWidth, newHeight);
            }
            @Override
            public void init(GLAutoDrawable glautodrawable)
            {
                CefBrowserOsrProxy.this.renderer.initialize(glautodrawable.getGL().getGL2());
            }
            @Override
            public void dispose(GLAutoDrawable glautodrawable)
            {
                CefBrowserOsrProxy.this.renderer.cleanup(glautodrawable.getGL().getGL2());
            }
            @Override
            public void display(GLAutoDrawable glautodrawable)
            {
                CefBrowserOsrProxy.this.renderer.render(glautodrawable.getGL().getGL2());
            }
        });
        canvas.addMouseListener(new MouseListener()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                CefBrowserOsrProxy.this.sendMouseEvent(e);
            }
            @Override
            public void mouseReleased(MouseEvent e)
            {
                CefBrowserOsrProxy.this.sendMouseEvent(e);
            }
            @Override
            public void mouseEntered(MouseEvent e)
            {
                CefBrowserOsrProxy.this.sendMouseEvent(e);
            }
            @Override
            public void mouseExited(MouseEvent e)
            {
                CefBrowserOsrProxy.this.sendMouseEvent(e);
            }
            @Override
            public void mouseClicked(MouseEvent e)
            {
                CefBrowserOsrProxy.this.sendMouseEvent(e);
            }
        });
        canvas.addMouseMotionListener(new MouseMotionListener()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                CefBrowserOsrProxy.this.sendMouseEvent(e);
            }
            @Override
            public void mouseDragged(MouseEvent e)
            {
                CefBrowserOsrProxy.this.sendMouseEvent(e);
            }
        });
        canvas.addMouseWheelListener(new MouseWheelListener()
        {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                CefBrowserOsrProxy.this.sendMouseWheelEvent(e);
            }
        });
        canvas.addKeyListener(new KeyListener()
        {
            @Override
            public void keyTyped(KeyEvent e)
            {
                CefBrowserOsrProxy.this.sendKeyEvent(e);
            }
            @Override
            public void keyPressed(KeyEvent e)
            {
                CefBrowserOsrProxy.this.sendKeyEvent(e);
            }
            @Override
            public void keyReleased(KeyEvent e)
            {
                CefBrowserOsrProxy.this.sendKeyEvent(e);
            }
        });
        canvas.setFocusable(true);
        canvas.addFocusListener(new FocusListener()
        {
            @Override
            public void focusLost(FocusEvent e)
            {
                CefBrowserOsrProxy.this.setFocus(false);
            }
            @Override
            public void focusGained(FocusEvent e)
            {   // Dismiss any Java menus that are currently displayed.
                MenuSelectionManager.defaultManager().clearSelectedPath();
                CefBrowserOsrProxy.this.setFocus(true);
            }
        });
        // Connect the Canvas with a drag and drop listener.
        new DropTarget(canvas, new CefDropTargetListener(this));
        return canvas;
    }

    @Override
    public Rectangle getViewRect(CefBrowser browser)
    {
        return this.browser_rect;
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint)
    {
        Point screenPoint = new Point(this.screenPoint);
        screenPoint.translate(viewPoint.x, viewPoint.y);
        return screenPoint;
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show)
    {
        if (!show)
        {
            this.renderer.clearPopupRects();
            this.invalidate();
        }
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size)
    {
        this.renderer.onPopupSize(size);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height)
    {   // if window is closing, canvas_ or opengl context could be null
        final GLContext context = this.canvas != null ? this.canvas.getContext() : null;
        if (context == null)
            return;
        if (context.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
            return; // This result can occur due to GLContext re-initialization when changing displays.
        this.renderer.onPaint(this.canvas.getGL().getGL2(), popup, dirtyRects, buffer, width, height);
        context.release();
        SwingUtilities.invokeLater(this.canvas::display);
        if (!this.onPaintListeners.isEmpty())
        {
            CefPaintEvent paintEvent = new CefPaintEvent(browser, popup, dirtyRects, buffer, width, height);
            for (Consumer<CefPaintEvent> onPaintListener : this.onPaintListeners)
            {
                onPaintListener.accept(paintEvent);
            }
        }
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, final int cursorType)
    {
        SwingUtilities.invokeLater(() -> this.canvas.setCursor(new Cursor(cursorType)));
        return true; // OSR always handles the cursor change.
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        int action = (mask & CefDragData.DragOperations.DRAG_OPERATION_MOVE) == 0
            ? DnDConstants.ACTION_COPY
            : DnDConstants.ACTION_MOVE;
        MouseEvent triggerEvent = new MouseEvent(this.canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, x, y, 0, false);
        DragGestureEvent ev = new DragGestureEvent(
            new CefDragGestureRecognizer(this.canvas, action, triggerEvent),
            action,
            new Point(x, y),
            new ArrayList<>(Arrays.asList(triggerEvent))
        );
        DragSource.getDefaultDragSource().startDrag(
            ev,
            /*dragCursor=*/null,
            new StringSelection(dragData.getFragmentText()), new DragSourceAdapter()
            {
                @Override
                public void dragDropEnd(DragSourceDropEvent dsde)
                {
                    CefBrowserOsrProxy.this.dragSourceEndedAt(dsde.getLocation(), mask);
                    CefBrowserOsrProxy.this.dragSourceSystemDragEnded();
                }
            }
        );
        return true;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation)
    {
        // TODO: Consider calling onCursorChange() if we want different cursors based on |operation|.
    }

    private void createBrowserIfRequired(boolean hasParent)
    {
        long windowHandle = 0;
        if (hasParent)
            windowHandle = getWindowHandle();
        if (getNativeRef("CefBrowser") == 0)
            if (getParentBrowser() != null)
                this.createDevTools(this.getParentBrowser(), this.getClient(), windowHandle, true, this.renderer.isTransparent(), null, this.getInspectAt());
            else
                this.createBrowser(this.getClient(), windowHandle, this.getUrl(), true, this.renderer.isTransparent(), null, this.getRequestContext());
        else if (hasParent && this.justCreated)
        {
            this.notifyAfterParentChanged();
            this.setFocus(true);
            this.justCreated = false;
        }
    }

    private void notifyAfterParentChanged()
    {   // With OSR there is no native window to reparent but we still need to send the notification.
        this.getClient().onAfterParentChanged(this);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo)
    {
        screenInfo.Set(this.scaleFactor, this.depth, this.depth_per_component, false, this.browser_rect.getBounds(), this.browser_rect.getBounds());
        return true;
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution)
    {
        int width = (int)Math.ceil(this.canvas.getWidth() * scaleFactor),
            height = (int)Math.ceil(this.canvas.getHeight() * scaleFactor),
        // In order to grab a screenshot of the browser window, we need to get the OpenGL internals from the GLCanvas that displays the browser.
            textureId = this.renderer.getTextureID();
        GL2 gl = this.canvas.getGL().getGL2();
        // This mirrors the two ways in which CefRenderer may render images internally - either via an incrementally updated texture that is the same size as the window and simply rendered
        // onto a textured quad by graphics hardware, in which case we capture the data directly from this texture, or by directly writing pixels into the OpenGL framebuffer, in which
        // case we directly read those pixels back. The latter is the way chosen if there is no hardware rasterizer capability detected. We can simply distinguish both approaches by
        // looking whether the textureId of the renderer is a valid (non-zero) one.
        boolean useReadPixels = (textureId == 0);
        // This Callable encapsulates the pixel-reading code. After running it, the screenshot BufferedImage contains the grabbed image.
        final Callable<BufferedImage> pixelGrabberCallable = () ->
        {
            BufferedImage screenshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            ByteBuffer buffer = GLBuffers.newDirectByteBuffer(width * height * 4);
            gl.getContext().makeCurrent();
            try
            {
                if (useReadPixels)
                {   // If pixels are copied directly to the framebuffer, we also directly read them back.
                    gl.glReadPixels(0, 0, width, height, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, buffer);
                }
                else
                {   // In this case, read the texture pixel data from the previously-retrieved texture ID
                    gl.glEnable(GL.GL_TEXTURE_2D);
                    gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
                    gl.glGetTexImage(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, buffer);
                    gl.glDisable(GL.GL_TEXTURE_2D);
                }
            }
            finally
            {
                gl.getContext().release();
            }
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                {   // The OpenGL functions only support RGBA, while Java BufferedImage uses ARGB. We must convert.
                    int r = (buffer.get() & 0xff);
                    int g = (buffer.get() & 0xff);
                    int b = (buffer.get() & 0xff);
                    int a = (buffer.get() & 0xff);
                    int argb = (a << 24) | (r << 16) | (g << 8) | (b << 0);
                    // If pixels were read from the framebuffer, we have to flip the resulting image on the Y axis, as the OpenGL framebuffer's y axis starts at the bottom of
                    // the image pointing "upwards", while BufferedImage has the origin in the upper left corner. This flipping is done when drawing into the BufferedImage.
                    screenshot.setRGB(x, useReadPixels ? (height - y - 1) : y, argb);
                }

            if (!nativeResolution && this.scaleFactor != 1.0)
            {   // HiDPI images should be resized down to "normal" levels
                BufferedImage resized = new BufferedImage((int) (screenshot.getWidth() / this.scaleFactor), (int) (screenshot.getHeight() / this.scaleFactor), BufferedImage.TYPE_INT_ARGB);
                AffineTransform tempTransform = new AffineTransform();
                tempTransform.scale(1.0 / this.scaleFactor, 1.0 / this.scaleFactor);
                AffineTransformOp tempScaleOperation = new AffineTransformOp(tempTransform, AffineTransformOp.TYPE_BILINEAR);
                return tempScaleOperation.filter(screenshot, resized);
            }
            else
            {
                return screenshot;
            }
        };
        if (SwingUtilities.isEventDispatchThread())
        {   // If called on the AWT event thread, just access the GL API
            try
            {
                return CompletableFuture.completedFuture(pixelGrabberCallable.call());
            }
            catch (Exception ex)
            {
                CompletableFuture<BufferedImage> future = new CompletableFuture<>();
                future.completeExceptionally(ex);
                return future;
            }
        }
        else
        {   // If called from another thread, register a GLEventListener and trigger an async redraw, during which we use the GL API to grab the pixel data. An unresolved Future is returned, on which the
            // caller can wait for a result (but not with the Event Thread, as we need that for pixel grabbing, which is why there's a safeguard in place to catch that situation if it accidentally happens).
            CompletableFuture<BufferedImage> future = new CompletableFuture<BufferedImage>()
            {
                private void safeguardGet()
                {
                    if (SwingUtilities.isEventDispatchThread())
                        throw new RuntimeException("Waiting on this Future using the AWT Event Thread is illegal, because it can potentially deadlock the thread.");
                }
                @Override
                public BufferedImage get() throws InterruptedException, ExecutionException
                {
                    this.safeguardGet();
                    return super.get();
                }
                @Override
                public BufferedImage get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
                {
                    this.safeguardGet();
                    return super.get(timeout, unit);
                }
            };
            this.canvas.addGLEventListener(new GLEventListener()
            {
                @Override
                public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height)
                {
                    // ignore
                }
                @Override
                public void init(GLAutoDrawable drawable)
                {
                    // ignore
                }
                @Override
                public void dispose(GLAutoDrawable drawable)
                {
                    // ignore
                }
                @Override
                public void display(GLAutoDrawable drawable)
                {
                    CefBrowserOsrProxy.this.canvas.removeGLEventListener(this);
                    try
                    {
                        future.complete(pixelGrabberCallable.call());
                    }
                    catch (Exception ex)
                    {
                        future.completeExceptionally(ex);
                    }
                }
            });
            // This repaint triggers an indirect call to the listeners' display method above, which ultimately completes the future that we return immediately.
            this.canvas.repaint();
            return future;
        }
    }
    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener)
    {
        if (listener != null && !this.onPaintListeners.contains(listener))
            this.onPaintListeners.add(listener);
    }
    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener)
    {
        this.onPaintListeners.clear();
        if (listener != null)
            this.onPaintListeners.add(listener);
    }
    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener)
    {
        this.onPaintListeners.remove(listener);
    }

}
