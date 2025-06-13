package org.cef.browser.ext;

import org.cef.browser.CefFrame;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefCookieAccessFilter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.misc.BoolRef;
import org.cef.misc.StringRef;
import org.cef.network.CefCookie;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.cef.network.CefURLRequest;
import org.cef.network.CefRequest.TransitionType;

public interface CefFrameExtHandler
{

    // Context menu handler
    default void onContextMenuDismissed(CefFrame frame) {}
    default boolean onContextMenuCommand(CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags)
    {
        return false; // default, true = custom handling
    }
    default void onBeforeContextMenu(CefFrame frame, CefContextMenuParams params, CefMenuModel model)
    {
        model.clear(); // disables context menu
    }

    // (File) Dialog handler

    // Display handler
    default void onAddressChange(CefFrame frame, String url) {}

    // Download handler

    // Drag handler

    // Focus handler

    // JSDialog handler

    // Keyboard handler

    // LifeSpan handler
    default boolean onBeforePopup(CefFrame frame, String target_url, String target_frame_name)
    {
        return false; // default, true = custom handling
    }

    // Load handler
    default void onLoadStart(CefFrame frame, TransitionType transitionType) {}
    default void onLoadError(CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {}
    default void onLoadEnd(CefFrame frame, int httpStatusCode) {}

    // Message handler
    default boolean onQuery(CefFrame frame, String jsFunction, long queryId, String request, boolean persistent, CefQueryCallback callback)
    {
        return false; // default, true = custom handling
    }
    default void onQueryCanceled(CefFrame frame, String jsFunction, long queryId) {}

    // Print handler

    // Render handler

    // Requests handler
    default boolean onBeforeBrowse(CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect)
    {
        return false; // default, true to cancel
    }
    default boolean onOpenURLFromTab(CefFrame frame, String target_url, boolean user_gesture)
    {
        return false; // default, true to cancel
    }
    default CefResourceRequestHandler getResourceRequestHandler(CefFrame frame, CefRequest req, boolean isNav, boolean isDL, String reqInitiator, BoolRef disableDefaultHandling)
    {
        disableDefaultHandling.set(false);
        return null; // default, !=null to cancel
    }

    // Requests extended
    default CefCookieAccessFilter requests_getCookieAccessFilter(CefFrame frame, CefRequest request)
    {
        return null;
    }
    default boolean requests_onBeforeResourceLoad(CefFrame frame, CefRequest request)
    {
        return false;
    }
    default CefResourceHandler requests_getResourceHandler(CefFrame frame, CefRequest request)
    {
        return null;
    }
    default void requests_onResourceRedirect(CefFrame frame, CefRequest request, CefResponse response, StringRef new_url) {}
    default boolean requests_onResourceResponse(CefFrame frame, CefRequest request, CefResponse response)
    {
        return false;
    }
    default void requests_onResourceLoadComplete(CefFrame frame, CefRequest request, CefResponse response, CefURLRequest.Status status, long receivedContentLength) {}
    default void requests_onProtocolExecution(CefFrame frame, CefRequest request, BoolRef allowOsExecution) {}
    // Cookies extended
    default boolean requests_canSendCookie(CefFrame frame, CefRequest request, CefCookie cookie)
    {
        return false;
    }
    default boolean requests_canSaveCookie(CefFrame frame, CefRequest request, CefResponse response, CefCookie cookie)
    {
        return false;
    }

    // Window handler

}
