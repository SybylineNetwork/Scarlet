package org.cef.browser.ext;

import java.io.IOException;

import org.cef.CefApp;
import org.cef.CefApp.CefVersion;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.callback.CefSchemeRegistrar;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.EnumProgress;
import me.friwi.jcefmaven.IProgressHandler;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import me.friwi.jcefmaven.UnsupportedPlatformException;

public class CefAppExt implements AutoCloseable
{

    public CefAppExt() throws IOException, UnsupportedPlatformException, InterruptedException, CefInitializationException
    {
        CefAppBuilder builder = new CefAppBuilder();
        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void stateHasChanged(CefApp.CefAppState state) {
                CefAppExt.this.stateHasChanged(state);
            }
            @Override
            public boolean onBeforeTerminate() {
                return CefAppExt.this.onBeforeTerminate();
            }
            @Override
            public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
                CefAppExt.this.onRegisterCustomSchemes(registrar);
            }
            @Override
            public void onContextInitialized() {
                CefAppExt.this.onContextInitialized();
            }
        });
        builder.setProgressHandler(new IProgressHandler() {
            @Override
            public void handleProgress(EnumProgress state, float percent) {
                CefAppExt.this.handleProgress(state, percent);
            }
        });
        CefSettings settings = builder.getCefSettings();
        settings.windowless_rendering_enabled = true;
        this.setup(builder, settings);
        this.app = builder.build();
    }

    protected final CefApp app;

    // Custom
    protected void setup(CefAppBuilder builder, CefSettings settings) {}

    public CefClientExt createClientExt()
    {
        return new CefClientExt(this.createClient());
    }

    // Closeable
    @Override
    public void close() throws Exception
    {
        this.dispose();
    }

    // App handler
    protected boolean onBeforeTerminate() { return false; }
    protected void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {}
    protected void onContextInitialized() {}
    protected void stateHasChanged(CefApp.CefAppState state) {}

    // IProgressHandler
    protected void handleProgress(EnumProgress state, float percent) {}

    // App
    public CefApp getApp()
    {
        return this.app;
    }
    public boolean clearSchemeHandlerFactories()
    {
        return this.app.clearSchemeHandlerFactories();
    }
    public CefClient createClient()
    {
        return this.app.createClient();
    }
    public void dispose()
    {
        this.app.dispose();
    }
    public void doMessageLoopWork(long delay_ms)
    {
        this.app.doMessageLoopWork(delay_ms);
    }
    public CefVersion getVersion()
    {
        return this.app.getVersion();
    }
    public boolean registerSchemeHandlerFactory(String schemeName, String domainName, CefSchemeHandlerFactory factory)
    {
        return this.app.registerSchemeHandlerFactory(schemeName, domainName, factory);
    }

}
