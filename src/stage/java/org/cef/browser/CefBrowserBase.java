package org.cef.browser;

import java.awt.Point;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;

public abstract class CefBrowserBase extends CefBrowser_N
{

    protected CefBrowserBase(CefClient client, String url, CefRequestContext context, CefBrowserBase parent, Point inspectAt, CefBrowserSettings settings)
    {
        super(client, url, context, parent, inspectAt, settings);
        this.baseInit(client, url, context, parent, inspectAt, settings);
    }

    protected abstract CefBrowserBase createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowserBase parent, Point inspectAt);

    @Override
    protected final CefBrowser_N createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser_N parent, Point inspectAt)
    {
        return this.createDevToolsBrowser(client, url, context, (CefBrowserBase)parent, inspectAt);
    }

    protected void baseInit(CefClient client, String url, CefRequestContext context, CefBrowserBase parent, Point inspectAt, CefBrowserSettings settings) {}

}
