package org.cef.browser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.cef.handler.CefCookieAccessFilter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.misc.BoolRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.cef.network.CefURLRequest.Status;

public class CefMappingResourceRequestHandler implements CefResourceRequestHandler
{

    public CefMappingResourceRequestHandler()
    {
        this.resources = new ConcurrentHashMap<>();
    }

    final Map<String, BytesRes> resources;

    public void submitContent(String url, byte[] bytes, ErrorCode errorCode, int status, String statusText, String mimeType)
    {
        this.submitContent(url, BytesRes.of(bytes, errorCode, status, statusText, mimeType));
    }

    public void submitContent(String url, BytesRes res)
    {
        this.resources.put(url, res);
    }

    public CefCookieAccessFilter getCookieAccessFilter(CefBrowser browser, CefFrame frame, CefRequest request)
    {
        return null; // ?
    }

    public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request)
    {
        return false; // proceed without canceling
    }

    public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request)
    {
        BytesRes res = this.resources.get(request.getURL());
        return res == null ? null : res.newHandler();
    }

    public void onResourceRedirect(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response, StringRef new_url)
    {
    }

    public boolean onResourceResponse(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response)
    {
        return false; // proceed without modification
    }

    public void onResourceLoadComplete(CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response, Status status, long receivedContentLength)
    {
    }

    public void onProtocolExecution(CefBrowser browser, CefFrame frame, CefRequest request, BoolRef allowOsExecution)
    {
    }

}