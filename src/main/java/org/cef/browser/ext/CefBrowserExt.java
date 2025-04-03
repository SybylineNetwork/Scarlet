package org.cef.browser.ext;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.KeyboardFocusManager;
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
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserBase;
import org.cef.browser.CefDragGestureRecognizer;
import org.cef.browser.CefDropTargetListenerBase;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;
import org.cef.handler.CefWindowHandler;

public abstract class CefBrowserExt<C extends Component> extends CefBrowserBase implements CefBrowserExtHandler
{

    public CefBrowserExt(CefClientExt clientExt, String url, CefRequestContext context, CefBrowserExt<?> parent, Point inspectAt, CefBrowserSettings settings)
    {
        super(clientExt.client, url, context, parent, inspectAt, settings);
        this.clientExt = clientExt;
        this.settings = settings;
        this.component = this.createComponent();

        this.component.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                CefBrowserExt.this.sendMouseEvent(e);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                CefBrowserExt.this.sendMouseEvent(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                CefBrowserExt.this.sendMouseEvent(e);
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                CefBrowserExt.this.sendMouseEvent(e);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                CefBrowserExt.this.sendMouseEvent(e);
            }
        });
        this.component.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {
                CefBrowserExt.this.sendMouseEvent(e);
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                CefBrowserExt.this.sendMouseEvent(e);
            }
        });
        this.component.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                e = new MouseWheelEvent(
                    e.getComponent(), 
                    e.getID(),
                    e.getWhen(),
                    e.getModifiers(),
                    e.getX(),
                    e.getY(),
                    e.getXOnScreen(),
                    e.getYOnScreen(),
                    e.getClickCount(),
                    e.isPopupTrigger(),
                    e.getScrollType(),
                    e.getScrollAmount() * -32,
                    e.getWheelRotation(),
                    e.getPreciseWheelRotation()
                );
                CefBrowserExt.this.sendMouseWheelEvent(e);
            }
        });
        this.component.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                CefBrowserExt.this.sendKeyEvent(e);
            }
            @Override
            public void keyPressed(KeyEvent e) {
                CefBrowserExt.this.sendKeyEvent(e);
            }
            @Override
            public void keyReleased(KeyEvent e) {
                CefBrowserExt.this.sendKeyEvent(e);
            }
        });
        this.component.setFocusable(true);
        this.component.addFocusListener(new FocusListener() {
            @Override
            public void focusLost(FocusEvent e) {
                CefBrowserExt.this.setFocus(false);
            }
            @Override
            public void focusGained(FocusEvent e) {
                // Dismiss any Java menus that are currently displayed.
                MenuSelectionManager.defaultManager().clearSelectedPath();
                CefBrowserExt.this.setFocus(true);
            }
        });
        new DropTarget(this.component, new CefDropTargetListenerBase(this));
    }

    protected final CefClientExt clientExt;
    protected final CefBrowserSettings settings;
    protected final C component;
    protected boolean transparency = true,
                      justCreated = false;
    protected Rectangle browser_rect = new Rectangle(0, 0, 1, 1), // Work around CEF issue #1437.
                        popup_rect = new Rectangle(0, 0, 0, 0),
                        original_popup_rect = new Rectangle(0, 0, 0, 0);
    protected Point screenPoint = new Point(0, 0);
    protected double scaleFactor = 1.0;
    protected int depth = 32,
                  depth_per_component = 8,
                  view_width = 0,
                  view_height = 0;

    @Deprecated // CefApp.N_DoMessageLoopWork
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo)
    {
        return this.clientExt.renderBridge.getScreenInfo(browser, screenInfo); 
    }

    @Override
    public CefRenderHandler getRenderHandler()
    {
        return this.clientExt.renderBridge;
    }

    @Override
    public CefWindowHandler getWindowHandler()
    {
        return this.clientExt.windowBridge;
    }

    @Override
    public Component getUIComponent()
    {
        return this.component;
    }

    @Override
    public void createImmediately()
    {
        this.justCreated = true;
        this.createBrowserIfRequired(false); // Create the browser immediately.
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution)
    {
        CompletableFuture<BufferedImage> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Screenshots are not supported by default"));
        return future;
    }

    @Override
    protected abstract CefBrowserBase createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowserBase parent, Point inspectAt);

    // Custom

    protected abstract C createComponent();

    public void sendMouseEvent(int id, long when, int mods, int x, int y, int xabs, int yabs, int clickCount, boolean popupTrigger, int button)
    {
        this.sendMouseEvent(new MouseEvent(this.component, id, when, mods, x, y, xabs, yabs, clickCount, popupTrigger, button));
    }
    public void sendMouseWheelEvent(int id, long when, int mods, int x, int y, int xabs, int yabs, int clickCount, boolean popupTrigger, int scrollType, int scrollAmount, int wheelRotation, double preciseWheelRotation)
    {
        this.sendMouseWheelEvent(new MouseWheelEvent(this.component, id, when, mods, x, y, xabs, yabs, clickCount, popupTrigger, scrollType, scrollAmount, wheelRotation, preciseWheelRotation));
    }
    public void sendKeyEvent(int id, long when, int mods, int keyCode, char keyChar, int keyLocation)
    {
        this.sendKeyEvent(new KeyEvent(this.component, id, when, mods, keyCode, keyChar, keyLocation));
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
        if (rc.x + rc.width > this.view_width)
            rc.x = this.view_width - rc.width;
        if (rc.y + rc.height > this.view_height)
            rc.y = this.view_height - rc.height;
        // if x or y became negative, move them to 0 again.
        if (rc.x < 0)
            rc.x = 0;
        if (rc.y < 0)
            rc.y = 0;
        return rc;
    }

    protected void createBrowserIfRequired(boolean hasParent)
    {
        long windowHandle = 0;
        if (hasParent)
            windowHandle = getWindowHandle();
        if (getNativeRef("CefBrowser") == 0)
            if (getParentBrowser() != null)
                this.createDevTools(this.getParentBrowser(), this.getClient(), windowHandle, true, this.transparency, null, this.getInspectAt());
            else
                this.createBrowser(this.getClient(), windowHandle, this.getUrl(), true, this.transparency, null, this.getRequestContext());
        else if (hasParent && this.justCreated)
        {
            this.notifyAfterParentChanged();
            this.setFocus(true);
            this.justCreated = false;
        }
    }

    protected void notifyAfterParentChanged()
    {   // With OSR there is no native window to reparent but we still need to send the notification.
        this.getClient().onAfterParentChanged(this);
    }

    protected long getWindowHandle()
    {
        return 0L;
    }

    // Focus
    protected boolean focused = false;
    // Focus handler
    @Override
    public void onTakeFocus(boolean next)
    {
        this.focused = false;
    }
    @Override
    public void onGotFocus()
    {
        if (this.focused)
            return;
        this.focused = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        this.setFocus(true);
    }
    @Override
    public boolean isFocused()
    {
        return this.focused;
    }

    // Render
    @Override
    public Rectangle getViewRect()
    {
        return this.browser_rect;
    }
    @Override
    public boolean getScreenInfo(CefScreenInfo screenInfo)
    {
        screenInfo.Set(this.scaleFactor, this.depth, this.depth_per_component, false, this.browser_rect.getBounds(), this.browser_rect.getBounds());
        return true;
    }
    @Override
    public Point getScreenPoint(Point viewPoint)
    {
        Point screenPoint = new Point(this.screenPoint);
        screenPoint.translate(viewPoint.x, viewPoint.y);
        return screenPoint;
    }
    @Override
    public void onPopupShow(boolean show)
    {
        if (!show)
        {
            this.popup_rect.setBounds(0, 0, 0, 0);
            this.original_popup_rect.setBounds(0, 0, 0, 0);
            this.invalidate();
        }
    }
    @Override
    public void onPopupSize(Rectangle rect)
    {
        if (rect.width <= 0 || rect.height <= 0)
            return;
        this.original_popup_rect = rect;
        this.popup_rect = this.getPopupRectInWebView(this.original_popup_rect);
    }
    @Override
    public abstract void onPaint(boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height);
    @Override
    public boolean onCursorChange_Render(int cursorType)
    {
        SwingUtilities.invokeLater(() -> this.component.setCursor(new Cursor(cursorType)));
        return true;
    }
    @Override
    public boolean startDragging(CefDragData dragData, int mask, int x, int y)
    {
        int action = (mask & CefDragData.DragOperations.DRAG_OPERATION_MOVE) == 0
            ? DnDConstants.ACTION_COPY
            : DnDConstants.ACTION_MOVE;
        MouseEvent triggerEvent = new MouseEvent(this.component, MouseEvent.MOUSE_DRAGGED, 0, 0, x, y, 0, false);
        DragGestureEvent ev = new DragGestureEvent(
            new CefDragGestureRecognizer(CefBrowserExt.this.component, action, triggerEvent),
            action,
            new Point(x, y),
            new ArrayList<>(Arrays.asList(triggerEvent))
        );
        DragSource.getDefaultDragSource().startDrag(
            ev,
            /*dragCursor=*/null,
            new StringSelection(dragData.getFragmentText()), new DragSourceAdapter() {
                @Override
                public void dragDropEnd(DragSourceDropEvent dsde) {
                    CefBrowserExt.this.dragSourceEndedAt(dsde.getLocation(), mask);
                    CefBrowserExt.this.dragSourceSystemDragEnded();
                }
            }
        );
        return true;
    }
    @Override
    public void updateDragCursor(int operation)
    {
        // TODO: Consider calling onCursorChange() if we want different cursors based on |operation|.
    }

}
