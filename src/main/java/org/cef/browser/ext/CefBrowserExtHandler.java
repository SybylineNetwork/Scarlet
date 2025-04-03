package org.cef.browser.ext;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.Vector;

import org.cef.CefSettings.LogSeverity;
import org.cef.browser.CefFrame;
import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefCallback;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.callback.CefDragData;
import org.cef.callback.CefFileDialogCallback;
import org.cef.callback.CefJSDialogCallback;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefPrintDialogCallback;
import org.cef.callback.CefPrintJobCallback;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefCookieAccessFilter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefScreenInfo;
import org.cef.handler.CefDialogHandler.FileDialogMode;
import org.cef.handler.CefFocusHandler.FocusSource;
import org.cef.handler.CefJSDialogHandler.JSDialogType;
import org.cef.handler.CefKeyboardHandler.CefKeyEvent;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.handler.CefRequestHandler.TerminationStatus;
import org.cef.misc.BoolRef;
import org.cef.misc.CefPrintSettings;
import org.cef.misc.StringRef;
import org.cef.network.CefCookie;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.cef.network.CefURLRequest;
import org.cef.network.CefRequest.TransitionType;

public interface CefBrowserExtHandler
{

    default CefFrameExtHandler getFrameHandlerFor(CefFrame frame)
    {
        return null;
    }

    // Context menu handler
    default void onContextMenuDismissed(CefFrame frame)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.onContextMenuDismissed(frame);
            return;
        }
    }
    default boolean onContextMenuCommand(CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.onContextMenuCommand(frame, params, commandId, eventFlags);
        }
        return false; // default, true = custom handling
    }
    default void onBeforeContextMenu(CefFrame frame, CefContextMenuParams params, CefMenuModel model)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.onBeforeContextMenu(frame, params, model);
            return;
        }
        model.clear(); // disables context menu
    }

    // (File) Dialog handler
    default boolean onFileDialog(FileDialogMode mode, String title2, String defaultFilePath, Vector<String> acceptFilters, Vector<String> acceptExtensions, Vector<String> acceptDescriptions, CefFileDialogCallback callback)
    {
        return false; // default, true = custom handling
    }

    // Display handler
    default void onAddressChange(CefFrame frame, String url)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.onAddressChange(frame, url);
            return;
        }
    }
    default void onTitleChange(String title) {}
    default void onStatusMessage(String value) {}
    default boolean onTooltip(String text)
    {
        return false; // default, true = custom handling
    }
    default boolean onConsoleMessage(LogSeverity level, String message, String source, int line)
    {
        return false; // default, true = custom handling
    }
    default boolean onCursorChange(int cursorType)
    {
        return false; // default, true = custom handling
    }
    default void onFullscreenModeChange(boolean fullscreen) {}

    // Download handler
    default boolean onBeforeDownload(CefDownloadItem downloadItem, String suggestedName, CefBeforeDownloadCallback callback)
    {
        return false; // default, true = custom handling
    }
    default void onDownloadUpdated(CefDownloadItem downloadItem, CefDownloadItemCallback callback) {}

    // Drag handler
    default boolean onDragEnter(CefDragData dragData, int mask)
    {
        return false; // default, true = custom handling
    }

    // Focus handler
    abstract void onTakeFocus(boolean next);
    default boolean onSetFocus(FocusSource source)
    {
        return false; // default, true = cancel acquire focus
    }
    abstract void onGotFocus();
abstract boolean isFocused();

    // JSDialog handler
    default void onResetDialogState() {}
    default boolean onJSDialog(String origin_url, JSDialogType dialog_type, String message_text, String default_prompt_text, CefJSDialogCallback callback, BoolRef suppress_message)
    {
        return false; // default, true = custom handling
    }
    default void onDialogClosed() {}
    default boolean onBeforeUnloadDialog(String message_text, boolean is_reload, CefJSDialogCallback callback)
    {
        return false; // default, true = custom handling
    }

    // Keyboard handler
    default boolean onPreKeyEvent(CefKeyEvent event, BoolRef is_keyboard_shortcut)
    {
        return false; // default, true = custom handling
    }
    default boolean onKeyEvent(CefKeyEvent event)
    {
        return false; // default, true = custom handling
    }

    // LifeSpan handler
    default boolean onBeforePopup(CefFrame frame, String target_url, String target_frame_name)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.onBeforePopup(frame, target_url, target_frame_name);
        }
        return false; // default, true = custom handling
    }
    default void onBeforeClose() {}
    default void onAfterParentChanged() {}
    default void onAfterCreated() {}
    default boolean doClose() { return false; }

    // Load handler
    default void onLoadingStateChange(boolean isLoading, boolean canGoBack, boolean canGoForward) {}
    default void onLoadStart(CefFrame frame, TransitionType transitionType)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.onLoadStart(frame, transitionType);
            return;
        }
    }
    default void onLoadError(CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.onLoadError(frame, errorCode, errorText, failedUrl);
            return;
        }
    }
    default void onLoadEnd(CefFrame frame, int httpStatusCode)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.onLoadEnd(frame, httpStatusCode);
            return;
        }
    }

    // Message handler
    default boolean onQuery(CefFrame frame, String jsFunction, long queryId, String request, boolean persistent, CefQueryCallback callback)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.onQuery(frame, jsFunction, queryId, request, persistent, callback);
        }
        return false; // default, true = custom handling
    }
    default void onQueryCanceled(CefFrame frame, String jsFunction, long queryId)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.onQueryCanceled(frame, jsFunction, queryId);
            return;
        }
    }

    // Print handler
    default void onPrintStart() {}
    default void onPrintSettings(CefPrintSettings settings, boolean getDefaults) {}
    default boolean onPrintJob(String documentName, String pdfFilePath, CefPrintJobCallback callback)
    {
        return false; // default, true = custom handling
    }
    default boolean onPrintDialog(boolean hasSelection, CefPrintDialogCallback callback)
    {
        return false; // default, true = custom handling
    }
    default void onPrintReset() {}
    default Dimension getPdfPaperSize(int deviceUnitsPerInch)
    {
        // default implementation is A4 letter size
        // @ 300 DPI, A4 is 2480 x 3508
        // @ 150 DPI, A4 is 1240 x 1754
        int adjustedWidth = (int) (((double) deviceUnitsPerInch / 300d) * 2480d);
        int adjustedHeight = (int) (((double) deviceUnitsPerInch / 300d) * 3508d);
        return new Dimension(adjustedWidth, adjustedHeight);
    }

    // Render handler
    abstract Rectangle getViewRect();
    abstract boolean getScreenInfo(CefScreenInfo screenInfo);
    abstract Point getScreenPoint(Point viewPoint);
    abstract void onPopupShow(boolean show);
    abstract void onPopupSize(Rectangle size);
    abstract void onPaint(boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height);
    abstract boolean onCursorChange_Render(int cursorType);
    abstract boolean startDragging(CefDragData dragData, int mask, int x, int y);
    abstract void updateDragCursor(int operation);

    // Requests handler
    default boolean onBeforeBrowse(CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.onBeforeBrowse(frame, request, user_gesture, is_redirect);
        }
        return false; // default, true to cancel
    }
    default boolean onOpenURLFromTab(CefFrame frame, String target_url, boolean user_gesture)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.onOpenURLFromTab(frame, target_url, user_gesture);
        }
        return false; // default, true to cancel
    }
    default CefResourceRequestHandler getResourceRequestHandler(CefFrame frame, CefRequest req, boolean isNav, boolean isDL, String reqInitiator, BoolRef disableDefaultHandling)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.getResourceRequestHandler(frame, req, isNav, isDL, reqInitiator, disableDefaultHandling);
        }
        disableDefaultHandling.set(false);
        return null; // default, !=null to cancel
    }
    default boolean getAuthCredentials(String origin_url, boolean isProxy, String host, int port, String realm, String scheme, CefAuthCallback callback)
    {
        return false; // True to continue the request (callback must be executed) or false to cancel.
    }
    default boolean onCertificateError(ErrorCode cert_error, String request_url, CefCallback callback)
    {
        return false; // True to handle the request (callback must be executed) or false to reject it.
    }
    default void onRenderProcessTerminated(TerminationStatus status, int error_code, String error_string) {}

    // Requests extended
    default CefCookieAccessFilter requests_getCookieAccessFilter(CefFrame frame, CefRequest request)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.requests_getCookieAccessFilter(frame, request);
        }
        return null;
    }
    default boolean requests_onBeforeResourceLoad(CefFrame frame, CefRequest request)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.requests_onBeforeResourceLoad(frame, request);
        }
        return false;
    }
    default CefResourceHandler requests_getResourceHandler(CefFrame frame, CefRequest request)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.requests_getResourceHandler(frame, request);
        }
        return null;
    }
    default void requests_onResourceRedirect(CefFrame frame, CefRequest request, CefResponse response, StringRef new_url)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.requests_onResourceRedirect(frame, request, response, new_url);
            return;
        }
    }
    default boolean requests_onResourceResponse(CefFrame frame, CefRequest request, CefResponse response)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.requests_onResourceResponse(frame, request, response);
        }
        return false;
    }
    default void requests_onResourceLoadComplete(CefFrame frame, CefRequest request, CefResponse response, CefURLRequest.Status status, long receivedContentLength)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.requests_onResourceLoadComplete(frame, request, response, status, receivedContentLength);
            return;
        }
    }
    default void requests_onProtocolExecution(CefFrame frame, CefRequest request, BoolRef allowOsExecution)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            frameHandler.requests_onProtocolExecution(frame, request, allowOsExecution);
            return;
        }
    }
    // Cookies extended
    default boolean requests_canSendCookie(CefFrame frame, CefRequest request, CefCookie cookie)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.requests_canSendCookie(frame, request, cookie);
        }
        return false;
    }
    default boolean requests_canSaveCookie(CefFrame frame, CefRequest request, CefResponse response, CefCookie cookie)
    {
        CefFrameExtHandler frameHandler = this.getFrameHandlerFor(frame);
        if (frameHandler != null)
        {
            return frameHandler.requests_canSaveCookie(frame, request, response, cookie);
        }
        return false;
    }

    // Window handler
    default Rectangle getRect()
    {
        return new Rectangle(0, 0, 0, 0);
    }
    default void onMouseEvent(int event, int screenX, int screenY, int modifier, int button) {}

}
