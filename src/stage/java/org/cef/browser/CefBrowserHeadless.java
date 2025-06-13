package org.cef.browser;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.swing.JPanel;

@Deprecated
public class CefBrowserHeadless extends CefBrowser_N implements CefRenderHandler
{

    private boolean justCreated = false;
    private Rectangle browser_rect = new Rectangle(0, 0, 1, 1); // Work around CEF issue #1437.
    private Point screenPoint = new Point(0, 0);
    private double scaleFactor = 1.0;
    private int depth = 32,
                depth_per_component = 8;

    private boolean isTransparent;
    private JPanel canvas;
    private CefBrowserSettings settings;
    private List<Consumer<CefPaintEvent>> onPaintListeners = new CopyOnWriteArrayList<>();

    public CefBrowserHeadless(CefClient client, String url, boolean transparent, CefRequestContext context, CefBrowserSettings settings)
    {
        this(client, url, transparent, context, null, null, settings);
    }
    private CefBrowserHeadless(CefClient client, String url, boolean transparent, CefRequestContext context, CefBrowserHeadless parent, Point inspectAt, CefBrowserSettings settings)
    {
        super(client, url, context, parent, inspectAt, settings);
        this.isTransparent = transparent;
        this.canvas = new JPanel();
    }

    @Override
    public void createImmediately()
    {
        this.justCreated = true;
        this.createBrowserIfRequired(false); // Create the browser immediately.
    }

    @Override
    public Component getUIComponent()
    {
        return null;
    }

    @Override
    public CefRenderHandler getRenderHandler()
    {
        return this;
    }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser_N parent, Point inspectAt)
    {
        return new CefBrowserHeadless(client, url, this.isTransparent, context, this, inspectAt, this.settings);
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
            this.invalidate();
        }
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size)
    {
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height)
    {
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, final int cursorType)
    {
        return true; // OSR always handles the cursor change.
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        int action = (mask & CefDragData.DragOperations.DRAG_OPERATION_MOVE) == 0
            ? DnDConstants.ACTION_COPY
            : DnDConstants.ACTION_MOVE;
        MouseEvent triggerEvent = new MouseEvent(this.canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, x, y, 0, false);
        DragGestureEvent ev = new DragGestureEvent(
            new CefDragGestureRecognizer(this.canvas, action, triggerEvent), action,
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
                    CefBrowserHeadless.this.dragSourceEndedAt(dsde.getLocation(), mask);
                    CefBrowserHeadless.this.dragSourceSystemDragEnded();
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
        long windowHandle = 0L;
        if (getNativeRef("CefBrowser") == 0L)
            if (getParentBrowser() != null)
                this.createDevTools(this.getParentBrowser(), this.getClient(), windowHandle, true, this.isTransparent, null, this.getInspectAt());
            else
                this.createBrowser(this.getClient(), windowHandle, this.getUrl(), true, this.isTransparent, null, this.getRequestContext());
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
        return CompletableFuture.completedFuture(new BufferedImage(this.browser_rect.width, this.browser_rect.height, BufferedImage.TYPE_4BYTE_ABGR));
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
