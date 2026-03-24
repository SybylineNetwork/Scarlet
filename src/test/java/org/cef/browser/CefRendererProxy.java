package org.cef.browser;

import com.jogamp.opengl.GL2;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.function.Consumer;

@SuppressWarnings("static-access")
public class CefRendererProxy
{

    protected final boolean transparent;
    protected final int texture_id[] = new int[1];
    protected GL2 initialized_context = null;
    protected int
        view_width = 0,
        view_height = 0;
    protected float
        spin_x = 0f,
        spin_y = 0f;
    protected Rectangle
        popup_rect = new Rectangle(0, 0, 0, 0),
        original_popup_rect = new Rectangle(0, 0, 0, 0);
    protected boolean use_draw_pixels = false;
    protected boolean draw_gradient_background = false;
    protected final Consumer<CefRendererProxy> postPainter;

    public CefRendererProxy(boolean transparent)
    {
        this(transparent, null);
    }

    public CefRendererProxy(boolean transparent, Consumer<CefRendererProxy> postPainter)
    {
        this.transparent = transparent;
        this.postPainter = postPainter != null ? postPainter : $ -> {};
    }

    public boolean isTransparent()
    {
        return this.transparent;
    }

    public int getTextureID()
    {
        return this.texture_id[0];
    }

    public void initialize(GL2 gl2)
    {
        if (this.initialized_context == gl2)
            return;
        this.initialized_context = gl2;
        if (!gl2.getContext().isHardwareRasterizer())
        {   // Workaround for Windows Remote Desktop which requires pot textures.
            CefLog.LOG.info("opengl rendering may be slow as hardware rendering isn't available");
            this.use_draw_pixels = true;
            return;
        }
        gl2.glHint(gl2.GL_POLYGON_SMOOTH_HINT, gl2.GL_NICEST);
        gl2.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        gl2.glPixelStorei(gl2.GL_UNPACK_ALIGNMENT, 1); // Necessary for non-power-of-2 textures to render correctly.
        gl2.glGenTextures(1, this.texture_id, 0); // Create the texture.
        CefLog.LOG.info("CefRendererProxy: glGenTextures %d", this.texture_id[0]);
        assert (this.texture_id[0] != 0);
        gl2.glBindTexture(gl2.GL_TEXTURE_2D, this.texture_id[0]);
        gl2.glTexParameteri(gl2.GL_TEXTURE_2D, gl2.GL_TEXTURE_MIN_FILTER, gl2.GL_NEAREST);
        gl2.glTexParameteri(gl2.GL_TEXTURE_2D, gl2.GL_TEXTURE_MAG_FILTER, gl2.GL_NEAREST);
        gl2.glTexEnvf(gl2.GL_TEXTURE_ENV, gl2.GL_TEXTURE_ENV_MODE, gl2.GL_MODULATE);
    }

    public void cleanup(GL2 gl2)
    {
        if (this.texture_id[0] != 0)
        {
            CefLog.LOG.info("CefRendererProxy: glDeleteTextures %d", this.texture_id[0]);
            gl2.glDeleteTextures(1, this.texture_id, 0);
        }
        this.view_width = this.view_height = 0;
    }

    public void render(GL2 gl2)
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
        if (this.draw_gradient_background)
        {
            gl2.glLoadIdentity();
            gl2.glPushAttrib(gl2.GL_ALL_ATTRIB_BITS); // Draw the background gradient.
            gl2.glBegin(gl2.GL_QUADS);
            gl2.glColor4f(1.0f, 0.0f, 0.0f, 1.0f); // red
            gl2.glVertex2f(-1.0f, -1.0f);
            gl2.glVertex2f(1.0f, -1.0f);
            gl2.glColor4f(0.0f, 0.0f, 1.0f, 1.0f); // blue
            gl2.glVertex2f(1.0f, 1.0f);
            gl2.glVertex2f(-1.0f, 1.0f);
            gl2.glEnd();
            gl2.glPopAttrib();
        }
        // Rotate the view based on the mouse spin.
        if (this.spin_x != 0)
            gl2.glRotatef(-this.spin_x, 1.0f, 0.0f, 0.0f);
        if (this.spin_y != 0)
            gl2.glRotatef(-this.spin_y, 0.0f, 1.0f, 0.0f);
        if (this.transparent)
        {
            gl2.glBlendFunc(gl2.GL_ONE, gl2.GL_ONE_MINUS_SRC_ALPHA); // Alpha blending style. Texture values have premultiplied alpha.
            gl2.glEnable(gl2.GL_BLEND); // Enable alpha blending.
        }
        gl2.glEnable(gl2.GL_TEXTURE_2D); // Enable 2D textures.
        assert (this.texture_id[0] != 0); // Draw the facets with the texture.
        gl2.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl2.glBindTexture(gl2.GL_TEXTURE_2D, this.texture_id[0]);
        gl2.glInterleavedArrays(gl2.GL_T2F_V3F, 0, vertices);
        gl2.glDrawArrays(gl2.GL_QUADS, 0, 4);
        gl2.glDisable(gl2.GL_TEXTURE_2D); // Disable 2D textures.
        if (this.transparent)
            gl2.glDisable(gl2.GL_BLEND); // Disable alpha blending.
    }

    public void onPopupSize(Rectangle rect)
    {
        if (rect.width <= 0 || rect.height <= 0)
            return;
        this.original_popup_rect = rect;
        this.popup_rect = this.getPopupRectInWebView(this.original_popup_rect);
    }

    public Rectangle getPopupRect()
    {
        return this.popup_rect.getBounds();
    }

    public Rectangle getPopupRectInWebView(Rectangle original_rect)
    {
        Rectangle rc = original_rect;
        // if x or y are negative, move them to 0.
        if (rc.x < 0)
            rc.x = 0;
        if (rc.y < 0)
            rc.y = 0;
        // if popup goes outside the view, try to reposition origin
        if (rc.x + rc.width > view_width)
            rc.x = view_width - rc.width;
        if (rc.y + rc.height > view_height)
            rc.y = view_height - rc.height;
        // if x or y became negative, move them to 0 again.
        if (rc.x < 0)
            rc.x = 0;
        if (rc.y < 0)
            rc.y = 0;
        return rc;
    }

    public void clearPopupRects()
    {
        this.popup_rect.setBounds(0, 0, 0, 0);
        this.original_popup_rect.setBounds(0, 0, 0, 0);
    }

    public void onPaint(GL2 gl2, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height)
    {
        this.initialize(gl2);
        if (this.use_draw_pixels)
        {
            gl2.glRasterPos2f(-1, 1);
            gl2.glPixelZoom(1, -1);
            gl2.glDrawPixels(width, height, GL2.GL_BGRA, GL2.GL_UNSIGNED_BYTE, buffer);
            return;
        }
        if (this.transparent)
            gl2.glEnable(gl2.GL_BLEND); // Enable alpha blending.
        gl2.glEnable(gl2.GL_TEXTURE_2D); // Enable 2D textures.
        assert (this.texture_id[0] != 0);
        gl2.glBindTexture(gl2.GL_TEXTURE_2D, this.texture_id[0]);
//        CefLog.LOG.info("CefRendererProxy: glBindTexture %d (onPaint %d %5s)", this.texture_id[0], dirtyRects.length, popup);

        if (!popup)
        {
            int old_width = this.view_width;
            int old_height = this.view_height;
            this.view_width = width;
            this.view_height = height;
            gl2.glPixelStorei(gl2.GL_UNPACK_ROW_LENGTH, this.view_width);
            if (old_width != this.view_width || old_height != this.view_height)
            {   // Update/resize the whole texture.
                gl2.glPixelStorei(gl2.GL_UNPACK_SKIP_PIXELS, 0);
                gl2.glPixelStorei(gl2.GL_UNPACK_SKIP_ROWS, 0);
                gl2.glTexImage2D(gl2.GL_TEXTURE_2D, 0, gl2.GL_RGBA, this.view_width, this.view_height, 0, gl2.GL_BGRA, gl2.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
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
        try
        {
            this.postPainter.accept(this);
        }
        catch (Exception ex)
        {
            CefLog.LOG.error("Exception in postPainter", ex);
        }
        // Disable 2D textures.
        gl2.glDisable(gl2.GL_TEXTURE_2D);
        if (this.transparent)
            gl2.glDisable(gl2.GL_BLEND); // Disable alpha blending.
    }

    public void setSpin(float spinX, float spinY)
    {
        this.spin_x = spinX;
        this.spin_y = spinY;
    }

    public void incrementSpin(float spinDX, float spinDY)
    {
        this.spin_x -= spinDX;
        this.spin_y -= spinDY;
    }

}
