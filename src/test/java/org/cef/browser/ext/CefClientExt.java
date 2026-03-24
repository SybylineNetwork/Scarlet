package org.cef.browser.ext;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.Vector;
import java.util.function.Consumer;

import org.cef.CefClient;
import org.cef.CefSettings.LogSeverity;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefPaintEvent;
import org.cef.browser.CefRequestContext;
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
import org.cef.handler.CefContextMenuHandler;
import org.cef.handler.CefCookieAccessFilter;
import org.cef.handler.CefDialogHandler;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefDownloadHandler;
import org.cef.handler.CefDragHandler;
import org.cef.handler.CefFocusHandler;
import org.cef.handler.CefJSDialogHandler;
import org.cef.handler.CefKeyboardHandler;
import org.cef.handler.CefLifeSpanHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefPrintHandlerAdapter;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefRequestContextHandler;
import org.cef.handler.CefRequestHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefScreenInfo;
import org.cef.handler.CefWindowHandler;
import org.cef.handler.CefDialogHandler.FileDialogMode;
import org.cef.handler.CefFocusHandler.FocusSource;
import org.cef.handler.CefJSDialogHandler.JSDialogType;
import org.cef.handler.CefKeyboardHandler.CefKeyEvent;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.handler.CefMessageRouterHandler;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefRequestHandler.TerminationStatus;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.CefPrintSettings;
import org.cef.misc.StringRef;
import org.cef.network.CefCookie;
import org.cef.network.CefRequest;
import org.cef.network.CefRequest.TransitionType;
import org.cef.network.CefResponse;
import org.cef.network.CefURLRequest.Status;

public class CefClientExt
{

    public CefClientExt(CefClient client)
    {
        this.client = client;
        this.renderBridge = new CefRenderHandler() {
            @Override
            public Rectangle getViewRect(CefBrowser browser) {
                return CefClientExt.this.getViewRect(browser);
            }
            @Override
            public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
                return CefClientExt.this.getScreenInfo(browser, screenInfo);
            }
            @Override
            public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
                return CefClientExt.this.getScreenPoint(browser, viewPoint);
            }
            @Override
            public void onPopupShow(CefBrowser browser, boolean show) {
                CefClientExt.this.onPopupShow(browser, show);
            }
            @Override
            public void onPopupSize(CefBrowser browser, Rectangle size) {
                CefClientExt.this.onPopupSize(browser, size);
            }
            @Override
            public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
                CefClientExt.this.onPaint(browser, popup, dirtyRects, buffer, width, height);
            }
            @Override
            public boolean onCursorChange(CefBrowser browser, int cursorType) {
                return CefClientExt.this.onCursorChange_Render(browser, cursorType);
            }
            @Override
            public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
                return CefClientExt.this.startDragging(browser, dragData, mask, x, y);
            }
            @Override
            public void updateDragCursor(CefBrowser browser, int operation) {
                CefClientExt.this.updateDragCursor(browser, operation);
            }
            @Override
            public void addOnPaintListener(Consumer<CefPaintEvent> listener) {
                CefClientExt.this.addOnPaintListener(listener);
            }
            @Override
            public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
                CefClientExt.this.setOnPaintListener(listener);
            }
            @Override
            public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
                CefClientExt.this.removeOnPaintListener(listener);
            }
        };
        this.windowBridge = new CefWindowHandler() {
            @Override
            public Rectangle getRect(CefBrowser browser) {
                return CefClientExt.this.getRect(browser);
            }
            @Override
            public void onMouseEvent(CefBrowser browser, int event, int screenX, int screenY, int modifier, int button) {
                CefClientExt.this.onMouseEvent(browser, event, screenX, screenY, modifier, button);
            }
        };
        this.resourceRequestBridge = new CefResourceRequestHandler() {
            @Override
            public CefCookieAccessFilter getCookieAccessFilter(CefBrowser browser, CefFrame frame, CefRequest request) {
                return CefClientExt.this.getCookieAccessFilter(browser, frame, request);
            }
            @Override
            public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
                return CefClientExt.this.onBeforeResourceLoad(browser, frame, request);
            }
            @Override
            public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
                return CefClientExt.this.getResourceHandler(browser, frame, request);
            }
            @Override
            public void onResourceRedirect(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response, StringRef new_url) {
                CefClientExt.this.onResourceRedirect(browser, frame, request, response, new_url);
            }
            @Override
            public boolean onResourceResponse(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response) {
                return CefClientExt.this.onResourceResponse(browser, frame, request, response);
            }
            @Override
            public void onResourceLoadComplete(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response, Status status, long receivedContentLength) {
                CefClientExt.this.onResourceLoadComplete(browser, frame, request, response, status, receivedContentLength);
            }
            @Override
            public void onProtocolExecution(CefBrowser browser, CefFrame frame, CefRequest request, BoolRef allowOsExecution) {
                CefClientExt.this.onProtocolExecution(browser, frame, request, allowOsExecution);
            }
        };
        this.cookieAccessBridge = new CefCookieAccessFilter() {
            @Override
            public boolean canSendCookie(CefBrowser browser, CefFrame frame, CefRequest request, CefCookie cookie) {
                return CefClientExt.this.canSendCookie(browser, frame, request, cookie);
            }
            @Override
            public boolean canSaveCookie(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response, CefCookie cookie) {
                return CefClientExt.this.canSaveCookie(browser, frame, request, response, cookie);
            }
        };
        this.config();
    }

    protected final CefClient client;
    protected final CefRenderHandler renderBridge;
    protected final CefWindowHandler windowBridge;
    protected final CefResourceRequestHandler resourceRequestBridge;
    protected final CefCookieAccessFilter cookieAccessBridge;

    // Custom
    public CefRenderHandler getRenderBridge()
    {
        return this.renderBridge;
    }
    public CefWindowHandler getWindowBridge()
    {
        return this.windowBridge;
    }
    public CefResourceRequestHandler getResourceRequestBridge()
    {
        return this.resourceRequestBridge;
    }
    public CefCookieAccessFilter getCookieAccessBridge()
    {
        return this.cookieAccessBridge;
    }
    protected void config()
    {
        this.client.addContextMenuHandler(new CefContextMenuHandler() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
                CefClientExt.this.onBeforeContextMenu(browser, frame, params, model);
            }
            @Override
            public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags) {
                return CefClientExt.this.onContextMenuCommand(browser, frame, params, commandId, eventFlags);
            }
            @Override
            public void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {
                CefClientExt.this.onContextMenuDismissed(browser, frame);
            }
        });
        this.client.addDialogHandler(new CefDialogHandler() {
            @Override
            public boolean onFileDialog(CefBrowser browser, FileDialogMode mode, String title, String defaultFilePath, Vector<String> acceptFilters, Vector<String> acceptExtensions, Vector<String> acceptDescriptions, CefFileDialogCallback callback) {
                return CefClientExt.this.onFileDialog(browser, mode, title, defaultFilePath, acceptFilters, acceptExtensions, acceptDescriptions, callback);
            }
        });
        this.client.addDisplayHandler(new CefDisplayHandler() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                CefClientExt.this.onAddressChange(browser, frame, url);
            }
            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                CefClientExt.this.onTitleChange(browser, title);
            }
            @Override
            public boolean onTooltip(CefBrowser browser, String text) {
                return CefClientExt.this.onTooltip(browser, text);
            }
            @Override
            public void onStatusMessage(CefBrowser browser, String value) {
                CefClientExt.this.onStatusMessage(browser, value);
            }
            @Override
            public boolean onConsoleMessage(CefBrowser browser, LogSeverity level, String message, String source, int line) {
                return CefClientExt.this.onConsoleMessage(browser, level, message, source, line);
            }
            @Override
            public boolean onCursorChange(CefBrowser browser, int cursorType) {
                return CefClientExt.this.onCursorChange(browser, cursorType);
            }
            @Override
            public void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {
                CefClientExt.this.onFullscreenModeChange(browser, fullscreen);
            }
        });
        this.client.addDownloadHandler(new CefDownloadHandler() {
            @Override
            public void onDownloadUpdated(CefBrowser browser, CefDownloadItem downloadItem, CefDownloadItemCallback callback) {
                CefClientExt.this.onDownloadUpdated(browser, downloadItem, callback);
            }  
            @Override          
            public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem, String suggestedName, CefBeforeDownloadCallback callback) {
                return CefClientExt.this.onBeforeDownload(browser, downloadItem, suggestedName, callback);
            }
        });
        this.client.addDragHandler(new CefDragHandler() {
            @Override
            public boolean onDragEnter(CefBrowser browser, CefDragData dragData, int mask) {
                return CefClientExt.this.onDragEnter(browser, dragData, mask);
            }
        });
        this.client.addFocusHandler(new CefFocusHandler() {
            @Override
            public void onTakeFocus(CefBrowser browser, boolean next) {
                CefClientExt.this.onTakeFocus(browser, next);
            }
            @Override
            public boolean onSetFocus(CefBrowser browser, FocusSource source) {
                return CefClientExt.this.onSetFocus(browser, source);
            }
            @Override
            public void onGotFocus(CefBrowser browser) {
                CefClientExt.this.onGotFocus(browser);
            }
        });
        this.client.addJSDialogHandler(new CefJSDialogHandler() {
            @Override
            public void onResetDialogState(CefBrowser browser) {
                CefClientExt.this.onResetDialogState(browser);
            }
            @Override
            public boolean onJSDialog(CefBrowser browser, String origin_url, JSDialogType dialog_type, String message_text, String default_prompt_text, CefJSDialogCallback callback, BoolRef suppress_message) {
                return CefClientExt.this.onJSDialog(browser, origin_url, dialog_type, message_text, default_prompt_text, callback, suppress_message);
            }
            @Override
            public void onDialogClosed(CefBrowser browser) { 
                CefClientExt.this.onDialogClosed(browser);
            }
            @Override
            public boolean onBeforeUnloadDialog(CefBrowser browser, String message_text, boolean is_reload, CefJSDialogCallback callback) {
                return CefClientExt.this.onBeforeUnloadDialog(browser, message_text, is_reload, callback);
            }
        });
        this.client.addKeyboardHandler(new CefKeyboardHandler() {
            @Override
            public boolean onPreKeyEvent(CefBrowser browser, CefKeyEvent event, BoolRef is_keyboard_shortcut) {
                return CefClientExt.this.onPreKeyEvent(browser, event, is_keyboard_shortcut);
            }
            @Override
            public boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
                return CefClientExt.this.onKeyEvent(browser, event);
            }
        });
        this.client.addLifeSpanHandler(new CefLifeSpanHandler() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String target_url, String target_frame_name) {
                return CefClientExt.this.onBeforePopup(browser, frame, target_url, target_frame_name);
            }
            @Override
            public void onBeforeClose(CefBrowser browser) {
                CefClientExt.this.onBeforeClose(browser);
            }
            @Override
            public void onAfterParentChanged(CefBrowser browser) {
                CefClientExt.this.onAfterParentChanged(browser);
            }
            @Override
            public void onAfterCreated(CefBrowser browser) {
                CefClientExt.this.onAfterCreated(browser);
            }
            @Override
            public boolean doClose(CefBrowser browser) {
                return CefClientExt.this.doClose(browser);
            }
        });
        this.client.addLoadHandler(new CefLoadHandler() {
            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                CefClientExt.this.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
            }
            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, TransitionType transitionType) {
                CefClientExt.this.onLoadStart(browser, frame, transitionType);
            }
            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
                CefClientExt.this.onLoadError(browser, frame, errorCode, errorText, failedUrl);
            }
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                CefClientExt.this.onLoadEnd(browser, frame, httpStatusCode);
            }
        });
        this.client.addPrintHandler(new CefPrintHandlerAdapter() {
            @Override
            public void onPrintStart(CefBrowser browser) {
                CefClientExt.this.onPrintStart(browser);
            }
            @Override
            public void onPrintSettings(CefBrowser browser, CefPrintSettings settings, boolean getDefaults) {
                CefClientExt.this.onPrintSettings(browser, settings, getDefaults);
            }
            @Override
            public void onPrintReset(CefBrowser browser) {
                CefClientExt.this.onPrintReset(browser);
            }
            @Override
            public boolean onPrintJob(CefBrowser browser, String documentName, String pdfFilePath, CefPrintJobCallback callback) {
                return CefClientExt.this.onPrintJob(browser, documentName, pdfFilePath, callback);
            }
            @Override
            public boolean onPrintDialog(CefBrowser browser, boolean hasSelection, CefPrintDialogCallback callback) {
                return CefClientExt.this.onPrintDialog(browser, hasSelection, callback);
            }
            @Override
            public Dimension getPdfPaperSize(CefBrowser browser, int deviceUnitsPerInch) {
                return CefClientExt.this.getPdfPaperSize(browser, deviceUnitsPerInch);
            }
        });
        this.client.addRequestHandler(new CefRequestHandler() {
            @Override
            public void onRenderProcessTerminated(CefBrowser browser, TerminationStatus status, int error_code, String error_string) {
                CefClientExt.this.onRenderProcessTerminated(browser, status, error_code, error_string);
            }
            @Override
            public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
                return CefClientExt.this.onOpenURLFromTab(browser, frame, target_url, user_gesture);
            }
            @Override
            public boolean onCertificateError(CefBrowser browser, ErrorCode cert_error, String request_url, CefCallback callback) {
                return CefClientExt.this.onCertificateError(browser, cert_error, request_url, callback);
            }
            @Override
            public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect) {
                return CefClientExt.this.onBeforeBrowse(browser, frame, request, user_gesture, is_redirect);
            }
            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator, BoolRef disableDefaultHandling) {
                return CefClientExt.this.getResourceRequestHandler(browser, frame, request, isNavigation, isDownload, requestInitiator, disableDefaultHandling);
            }
            @Override
            public boolean getAuthCredentials(CefBrowser browser, String origin_url, boolean isProxy, String host, int port, String realm, String scheme, CefAuthCallback callback) {
                return CefClientExt.this.getAuthCredentials(browser, origin_url, isProxy, host, port, realm, scheme, callback);
            }
        });
    }

    public CefBrowserExtGL createBrowserExtGL(String url)
    {
        return new CefBrowserExtGL(this, url);
    }
    public CefBrowserExtGL createBrowserExtGL(String url, CefRequestContext context)
    {
        return new CefBrowserExtGL(this, url, context);
    }
    public CefBrowserExtGL createBrowserExtGL(String url, CefRequestContextHandler handler)
    {
        return new CefBrowserExtGL(this, url, handler);
    }

    // Client
    public CefClient getClient()
    {
        return this.client;
    }
    public void addMessageRouter(CefMessageRouter router, CefMessageRouterHandler... handlers)
    {
        String jsQueryFunction = router.getMessageRouterConfig().jsQueryFunction,
               jsCancelFunction = router.getMessageRouterConfig().jsCancelFunction;
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback) {
                return CefClientExt.this.onQuery(browser, frame, jsQueryFunction, queryId, request, persistent, callback);
            }
            public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
                CefClientExt.this.onQueryCanceled(browser, frame, jsCancelFunction, queryId);
            }
        }, false);
        if (handlers != null)
            for (CefMessageRouterHandler handler : handlers)
                if (handler != null)
                    router.addHandler(handler, false);
        this.client.addMessageRouter(router);
    }
    public void addMessageRouter(CefMessageRouter.CefMessageRouterConfig config, CefMessageRouterHandler... handlers)
    { this.addMessageRouter(CefMessageRouter.create(config), handlers); }
    public void addMessageRouter(String jsQueryFunction, String jsCancelFunction, CefMessageRouterHandler... handlers)
    { this.addMessageRouter(new CefMessageRouter.CefMessageRouterConfig(jsQueryFunction, jsCancelFunction), handlers); }
    public void dispose()
    {
        this.client.dispose();
    }

    // ContextMenu
    protected void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onBeforeContextMenu(frame, params, model);
    }
    protected boolean onContextMenuCommand(CefBrowser browser, CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onContextMenuCommand(frame, params, commandId, eventFlags);
        return false;
    }
    protected void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onContextMenuDismissed(frame);
    }
    // Dialog
    protected boolean onFileDialog(CefBrowser browser, FileDialogMode mode, String title, String defaultFilePath, Vector<String> acceptFilters, Vector<String> acceptExtensions, Vector<String> acceptDescriptions, CefFileDialogCallback callback) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onFileDialog(mode, title, defaultFilePath, acceptFilters, acceptExtensions, acceptDescriptions, callback);
        return false;
    }
    // Display
    protected void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onAddressChange(frame, url);
    }
    protected void onTitleChange(CefBrowser browser, String title) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onTitleChange(title);
    }
    protected boolean onTooltip(CefBrowser browser, String text) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onTooltip(text);
        return false;
    }
    protected void onStatusMessage(CefBrowser browser, String value) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onStatusMessage(value);
    }
    protected boolean onConsoleMessage(CefBrowser browser, LogSeverity level, String message, String source, int line) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onConsoleMessage(level, message, source, line);
        return false;
    }
    protected boolean onCursorChange(CefBrowser browser, int cursorType) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onCursorChange(cursorType);
        return false;
    }
    protected void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onFullscreenModeChange(fullscreen);
    }
    // Download
    protected void onDownloadUpdated(CefBrowser browser, CefDownloadItem downloadItem, CefDownloadItemCallback callback) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onDownloadUpdated(downloadItem, callback);
    }            
    protected boolean onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem, String suggestedName, CefBeforeDownloadCallback callback) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onBeforeDownload(downloadItem, suggestedName, callback);
        return false;
    }
    // Drag
    protected boolean onDragEnter(CefBrowser browser, CefDragData dragData, int mask) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onDragEnter(dragData, mask);
        return false;
    }
    // Focus
    protected void onTakeFocus(CefBrowser browser, boolean next) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onTakeFocus(next);
    }
    protected boolean onSetFocus(CefBrowser browser, FocusSource source) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onSetFocus(source);
        return false;
    }
    protected void onGotFocus(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onGotFocus();
    }
    // JSDialog
    protected void onResetDialogState(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onResetDialogState();
    }
    protected boolean onJSDialog(CefBrowser browser, String origin_url, JSDialogType dialog_type, String message_text, String default_prompt_text, CefJSDialogCallback callback, BoolRef suppress_message) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onJSDialog(origin_url, dialog_type, message_text, default_prompt_text, callback, suppress_message);
        return false;
    }
    protected void onDialogClosed(CefBrowser browser) { 
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onDialogClosed();
    }
    protected boolean onBeforeUnloadDialog(CefBrowser browser, String message_text, boolean is_reload, CefJSDialogCallback callback) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onBeforeUnloadDialog(message_text, is_reload, callback);
        return false;
    }
    // Keyboard
    protected boolean onPreKeyEvent(CefBrowser browser, CefKeyEvent event, BoolRef is_keyboard_shortcut) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onPreKeyEvent(event, is_keyboard_shortcut);
        return false;
    }
    protected boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onKeyEvent(event);
        return false;
    }
    // LifeSpan
    protected boolean onBeforePopup(CefBrowser browser, CefFrame frame, String target_url, String target_frame_name) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onBeforePopup(frame, target_url, target_frame_name);
        return false;
    }
    protected void onBeforeClose(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onBeforeClose();
    }
    protected void onAfterParentChanged(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onAfterParentChanged();
    }
    protected void onAfterCreated(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onAfterCreated();
    }
    protected boolean doClose(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).doClose();
        return false;
    }
    // Load
    protected void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onLoadingStateChange(isLoading, canGoBack, canGoForward);
    }
    protected void onLoadStart(CefBrowser browser, CefFrame frame, TransitionType transitionType) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onLoadStart(frame, transitionType);
    }
    protected void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onLoadError(frame, errorCode, errorText, failedUrl);
    }
    protected void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onLoadEnd(frame, httpStatusCode);
    }
    // MessageRouter
    protected boolean onQuery(CefBrowser browser, CefFrame frame, String jsFunction, long queryId, String request, boolean persistent, CefQueryCallback callback) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onQuery(frame, jsFunction, queryId, request, persistent, callback);
        return false;
    }
    protected void onQueryCanceled(CefBrowser browser, CefFrame frame, String jsFunction, long queryId) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onQueryCanceled(frame, jsFunction, queryId);
    }
    // Print
    protected void onPrintStart(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onPrintStart();
    }
    protected void onPrintSettings(CefBrowser browser, CefPrintSettings settings, boolean getDefaults) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onPrintSettings(settings, getDefaults);
    }
    protected void onPrintReset(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onPrintReset();
    }
    protected boolean onPrintJob(CefBrowser browser, String documentName, String pdfFilePath, CefPrintJobCallback callback) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onPrintJob(documentName, pdfFilePath, callback);
        return false;
    }
    protected boolean onPrintDialog(CefBrowser browser, boolean hasSelection, CefPrintDialogCallback callback) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onPrintDialog(hasSelection, callback);
        return false;
    }
    protected Dimension getPdfPaperSize(CefBrowser browser, int deviceUnitsPerInch) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).getPdfPaperSize(deviceUnitsPerInch);
        // default implementation is A4 letter size
        // @ 300 DPI, A4 is 2480 x 3508
        // @ 150 DPI, A4 is 1240 x 1754
        int adjustedWidth = (int) (((double) deviceUnitsPerInch / 300d) * 2480d);
        int adjustedHeight = (int) (((double) deviceUnitsPerInch / 300d) * 3508d);
        return new Dimension(adjustedWidth, adjustedHeight);
    }
    // Render
    protected Rectangle getViewRect(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).getViewRect();
        return null;
    }
    protected boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).getScreenInfo(screenInfo);
        return false;
    }
    protected Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).getScreenPoint(viewPoint);
        return null;
    }
    protected void onPopupShow(CefBrowser browser, boolean show) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onPopupShow(show);
    }
    protected void onPopupSize(CefBrowser browser, Rectangle size) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onPopupSize(size);
    }
    protected void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onPaint(popup, dirtyRects, buffer, width, height);
    }
    protected boolean onCursorChange_Render(CefBrowser browser, int cursorType) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onCursorChange_Render(cursorType);
        return false;
    }
    protected boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).startDragging(dragData, mask, x, y);
        return false;
    }
    protected void updateDragCursor(CefBrowser browser, int operation) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).updateDragCursor(operation);
    }
    protected void addOnPaintListener(Consumer<CefPaintEvent> listener) {
    }
    protected void setOnPaintListener(Consumer<CefPaintEvent> listener) {
    }
    protected void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
    }
    // Request
    protected void onRenderProcessTerminated(CefBrowser browser, TerminationStatus status, int error_code, String error_string) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onRenderProcessTerminated(status, error_code, error_string);
    }
    protected boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onOpenURLFromTab(frame, target_url, user_gesture);
        return false;
    }
    protected boolean onCertificateError(CefBrowser browser, ErrorCode cert_error, String request_url, CefCallback callback) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onCertificateError(cert_error, request_url, callback);
        return false;
    }
    protected boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).onBeforeBrowse(frame, request, user_gesture, is_redirect);
        return false;
    }
    protected CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator, BoolRef disableDefaultHandling) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).getResourceRequestHandler(frame, request, isNavigation, isDownload, requestInitiator, disableDefaultHandling);
        return null;
    }
    protected boolean getAuthCredentials(CefBrowser browser, String origin_url, boolean isProxy, String host, int port, String realm, String scheme, CefAuthCallback callback) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).getAuthCredentials(origin_url, isProxy, host, port, realm, scheme, callback);
        return false;
    }
    // Window
    protected Rectangle getRect(CefBrowser browser) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).getRect();
        return new Rectangle(0, 0, 0, 0);
    }
    protected void onMouseEvent(CefBrowser browser, int event, int screenX, int screenY, int modifier, int button) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).onMouseEvent(event, screenX, screenY, modifier, button);
    }
    // Request extended
    protected CefCookieAccessFilter getCookieAccessFilter(CefBrowser browser, CefFrame frame, CefRequest request) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).requests_getCookieAccessFilter(frame, request);
        return null;
    }
    protected boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).requests_onBeforeResourceLoad(frame, request);
        return false;
    }
    protected CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).requests_getResourceHandler(frame, request);
        return null;
    }
    protected void onResourceRedirect(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response, StringRef new_url) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).requests_onResourceRedirect(frame, request, response, new_url);
    }
    protected boolean onResourceResponse(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).requests_onResourceResponse(frame, request, response);
        return false;
    }
    protected void onResourceLoadComplete(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response, Status status, long receivedContentLength) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).requests_onResourceLoadComplete(frame, request, response, status, receivedContentLength);
    }
    protected void onProtocolExecution(CefBrowser browser, CefFrame frame, CefRequest request, BoolRef allowOsExecution) {
        if (browser instanceof CefBrowserExtHandler)
            ((CefBrowserExtHandler)browser).requests_onProtocolExecution(frame, request, allowOsExecution);
    }
    // Cookies extended
    protected boolean canSendCookie(CefBrowser browser, CefFrame frame, CefRequest request, CefCookie cookie) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).requests_canSendCookie(frame, request, cookie);
        return false;
    }
    protected boolean canSaveCookie(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response, CefCookie cookie) {
        if (browser instanceof CefBrowserExtHandler)
            return ((CefBrowserExtHandler)browser).requests_canSaveCookie(frame, request, response, cookie);
        return false;
    }

}
