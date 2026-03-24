package org.cef.browser.ext;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import javax.swing.SwingUtilities;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.OS;
import org.cef.browser.CefBrowserBase;
import org.cef.browser.CefBrowserOsrProxy;
import org.cef.browser.CefRequestContext;
import org.cef.handler.CefRequestContextHandler;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;

@SuppressWarnings("static-access")
public class CefBrowserExtGL extends CefBrowserExt<GLCanvas>
{

    public CefBrowserExtGL(CefClientExt clientExt, String url)
    {
        this(clientExt, url, null, null, null, null);
    }
    public CefBrowserExtGL(CefClientExt clientExt, String url, CefRequestContextHandler handler)
    {
        this(clientExt, url, handler == null ? null : CefRequestContext.createContext(handler), null, null, null);
    }
    public CefBrowserExtGL(CefClientExt clientExt, String url, CefRequestContext context)
    {
        this(clientExt, url, context, null, null, null);
    }
    public CefBrowserExtGL(CefClientExt clientExt, String url, CefRequestContext context, CefBrowserExtGL parent, Point inspectAt, CefBrowserSettings settings)
    {
        super(clientExt, url, context, parent, inspectAt, settings);
    }

    @Override
    protected GLCanvas createComponent()
    {
        GLCanvas canvas = new GLCanvas(CefBrowserOsrProxy.GLCAPS) {
            private static final long serialVersionUID = 4997712301127883477L;
            private Method scaleFactorAccessor = null;
            private boolean removed = true;
            @Override
            public void paint(Graphics g) {
                CefBrowserExtGL.this.createBrowserIfRequired(true);
                if (g instanceof Graphics2D)
                {
                    Graphics2D g2d = (Graphics2D)g;
                    GraphicsConfiguration config = g2d.getDeviceConfiguration();
                    CefBrowserExtGL.this.depth = config.getColorModel().getPixelSize();
                    CefBrowserExtGL.this.depth_per_component = config.getColorModel().getComponentSize()[0];
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
                            CefBrowserExtGL.this.scaleFactor = factor instanceof Integer
                                ? ((Integer)factor).doubleValue()
                                : 1.0;
                        }
                        catch (ReflectiveOperationException | IllegalArgumentException | SecurityException exc)
                        {
                            CefBrowserExtGL.this.scaleFactor = 1.0;
                        }
                    }
                    else
                        CefBrowserExtGL.this.scaleFactor = g2d.getTransform().getScaleX();
                }
                super.paint(g);
            }
            @Override
            public void addNotify() {
                super.addNotify();
                if (!this.removed)
                    return;
                CefBrowserExtGL.this.notifyAfterParentChanged();
                this.removed = false;
            }
            @Override
            public void removeNotify() {
                if (!this.removed) {
                    if (!CefBrowserExtGL.this.isClosed())
                        CefBrowserExtGL.this.notifyAfterParentChanged();
                    this.removed = true;
                }
                super.removeNotify();
            }
        };
        // The GLContext will be re-initialized when changing displays, resulting in calls to dispose/init/reshape.
        canvas.addGLEventListener(new GLEventListener() {
            public void reshape(GLAutoDrawable glautodrawable, int x, int y, int width, int height) {
                int newWidth = width;
                int newHeight = height;
                if (OS.isMacintosh()) {
                    // HiDPI display scale correction support code. For some reason this does seem to be necessary on MacOS only.
                    // If doing this correction on Windows, the browser content would be too small and in the lower left corner of the canvas only.
                    newWidth = (int) (width / CefBrowserExtGL.this.scaleFactor);
                    newHeight = (int) (height / CefBrowserExtGL.this.scaleFactor);
                }
                CefBrowserExtGL.this.browser_rect.setBounds(x, y, newWidth, newHeight);
                CefBrowserExtGL.this.screenPoint = canvas.getLocationOnScreen();
                CefBrowserExtGL.this.wasResized(newWidth, newHeight);
            }
            public void init(GLAutoDrawable glautodrawable) {
                CefBrowserExtGL.this.renderer_initialize(glautodrawable.getGL().getGL2());
            }
            public void dispose(GLAutoDrawable glautodrawable) {
                CefBrowserExtGL.this.renderer_cleanup(glautodrawable.getGL().getGL2());
            }
            public void display(GLAutoDrawable glautodrawable) {
                CefBrowserExtGL.this.renderer_render(glautodrawable.getGL().getGL2());
            }
        });
        return canvas;
    }

    @Override
    public void onPaint(boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height)
    {   // if window is closing, canvas_ or opengl context could be null
        final GLContext context = this.component != null ? this.component.getContext() : null;
        if (context == null)
            return;
        if (context.makeCurrent() == GLContext.CONTEXT_NOT_CURRENT)
            return; // This result can occur due to GLContext re-initialization when changing displays.
        this.renderer_onPaint(this.component.getGL().getGL2(), popup, dirtyRects, buffer, width, height);
        context.release();
        SwingUtilities.invokeLater(this.component::display);
    }

    // Renderer

    protected void renderer_postPaint(int glTex) {}

    protected int[] texture_id = new int[1];
    protected GL2 initialized_context = null;
    protected boolean use_draw_pixels = false;

    public int getTextureID()
    {
        return this.texture_id[0];
    }

    protected void renderer_initialize(GL2 gl2)
    {
        if (this.initialized_context == gl2)
            return;
        this.initialized_context = gl2;
        if (!gl2.getContext().isHardwareRasterizer())
        {   // Workaround for Windows Remote Desktop which requires pot textures.
            this.use_draw_pixels = true;
            return;
        }
        gl2.glHint(gl2.GL_POLYGON_SMOOTH_HINT, gl2.GL_NICEST);
        gl2.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        gl2.glPixelStorei(gl2.GL_UNPACK_ALIGNMENT, 1); // Necessary for non-power-of-2 textures to render correctly.
        gl2.glGenTextures(1, this.texture_id, 0); // Create the texture.
        assert (this.texture_id[0] != 0);
        gl2.glBindTexture(gl2.GL_TEXTURE_2D, this.texture_id[0]);
        gl2.glTexParameteri(gl2.GL_TEXTURE_2D, gl2.GL_TEXTURE_MIN_FILTER, gl2.GL_NEAREST);
        gl2.glTexParameteri(gl2.GL_TEXTURE_2D, gl2.GL_TEXTURE_MAG_FILTER, gl2.GL_NEAREST);
        gl2.glTexEnvf(gl2.GL_TEXTURE_ENV, gl2.GL_TEXTURE_ENV_MODE, gl2.GL_MODULATE);
    }

    protected void renderer_cleanup(GL2 gl2)
    {
        if (this.texture_id[0] != 0)
        {
            gl2.glDeleteTextures(1, this.texture_id, 0);
        }
        this.view_width = this.view_height = 0;
    }

    protected void renderer_render(GL2 gl2)
    {
        if (this.use_draw_pixels || this.view_width == 0 || this.view_height == 0)
            return;
        assert (this.initialized_context != null);
        final float[] vertex_data =
        {// tu,   tv,     x,     y,    z
            0.0f, 1.0f,-1.0f,-1.0f, 0.0f,
            1.0f, 1.0f, 1.0f,-1.0f, 0.0f,
            1.0f, 0.0f, 1.0f, 1.0f, 0.0f,
            0.0f, 0.0f,-1.0f, 1.0f, 0.0f,
        };
        FloatBuffer vertices = FloatBuffer.wrap(vertex_data);

        gl2.glClear(gl2.GL_COLOR_BUFFER_BIT | gl2.GL_DEPTH_BUFFER_BIT);
        gl2.glMatrixMode(gl2.GL_MODELVIEW);
        gl2.glLoadIdentity();
        gl2.glViewport(0, 0, this.view_width, this.view_height); // Match GL units to screen coordinates.
        gl2.glMatrixMode(gl2.GL_PROJECTION);
        gl2.glBlendFunc(gl2.GL_ONE, gl2.GL_ONE_MINUS_SRC_ALPHA); // Alpha blending style. Texture values have premultiplied alpha.
        gl2.glEnable(gl2.GL_BLEND); // Enable alpha blending.
        gl2.glEnable(gl2.GL_TEXTURE_2D); // Enable 2D textures.
        assert (this.texture_id[0] != 0); // Draw the facets with the texture.
        gl2.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl2.glBindTexture(gl2.GL_TEXTURE_2D, this.texture_id[0]);
        gl2.glInterleavedArrays(gl2.GL_T2F_V3F, 0, vertices);
        gl2.glDrawArrays(gl2.GL_QUADS, 0, 4);
        gl2.glDisable(gl2.GL_TEXTURE_2D); // Disable 2D textures.
        gl2.glDisable(gl2.GL_BLEND); // Disable alpha blending.
    }

    protected void renderer_onPaint(GL2 gl2, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height)
    {
        this.renderer_initialize(gl2);
        if (this.use_draw_pixels)
        {
            gl2.glRasterPos2f(-1, 1);
            gl2.glPixelZoom(1, -1);
            gl2.glDrawPixels(width, height, GL2.GL_BGRA, GL2.GL_UNSIGNED_BYTE, buffer);
            return;
        }
        
        gl2.glEnable(gl2.GL_BLEND); // Enable alpha blending.
        gl2.glEnable(gl2.GL_TEXTURE_2D); // Enable 2D textures.
        assert (this.texture_id[0] != 0);
        gl2.glBindTexture(gl2.GL_TEXTURE_2D, this.texture_id[0]);

        if (!popup)
        {
            int old_width = this.view_width;
            int old_height = this.view_height;
            this.view_width = width;
            this.view_height = height;
            gl2.glPixelStorei(gl2.GL_UNPACK_ROW_LENGTH, width);
            if (width != old_width || height != old_height)
            {   // Update/resize the whole texture.
                gl2.glPixelStorei(gl2.GL_UNPACK_SKIP_PIXELS, 0);
                gl2.glPixelStorei(gl2.GL_UNPACK_SKIP_ROWS, 0);
                gl2.glTexImage2D(gl2.GL_TEXTURE_2D, 0, gl2.GL_RGBA, width, height, 0, gl2.GL_BGRA, gl2.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            }
            else for (int i = 0; i < dirtyRects.length; ++i)
            {   // Update just the dirty rectangles.
                Rectangle rect = dirtyRects[i];
                gl2.glPixelStorei(gl2.GL_UNPACK_SKIP_PIXELS, rect.x);
                gl2.glPixelStorei(gl2.GL_UNPACK_SKIP_ROWS, rect.y);
                gl2.glTexSubImage2D(gl2.GL_TEXTURE_2D, 0, rect.x, rect.y, rect.width, rect.height, gl2.GL_BGRA, gl2.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            }
        }
        else if (popup && this.popup_rect.width > 0 && this.popup_rect.height > 0)
        {
            int skip_pixels = 0,
                x = this.popup_rect.x,
                skip_rows = 0,
                y = this.popup_rect.y,
                w = width,
                h = height;
            // Adjust the popup to fit inside the view.
            if (x < 0)
            {
                skip_pixels = -x;
                x = 0;
            }
            if (y < 0)
            {
                skip_rows = -y;
                y = 0;
            }
            if (x + w > this.view_width)
                w -= x + w - this.view_width;
            if (y + h > this.view_height)
                h -= y + h - this.view_height;
            // Update the popup rectangle.
            gl2.glPixelStorei(gl2.GL_UNPACK_ROW_LENGTH, width);
            gl2.glPixelStorei(gl2.GL_UNPACK_SKIP_PIXELS, skip_pixels);
            gl2.glPixelStorei(gl2.GL_UNPACK_SKIP_ROWS, skip_rows);
            gl2.glTexSubImage2D(gl2.GL_TEXTURE_2D, 0, x, y, w, h, gl2.GL_BGRA, gl2.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        }
        this.renderer_postPaint(this.texture_id[0]);
        // Disable 2D textures.
        gl2.glDisable(gl2.GL_TEXTURE_2D);
        gl2.glDisable(gl2.GL_BLEND); // Disable alpha blending.
    }

    @Override
    protected CefBrowserBase createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowserBase parent, Point inspectAt)
    {
        return new CefBrowserExtGL(this.clientExt, url, context, this, inspectAt, this.settings);
    }

}
