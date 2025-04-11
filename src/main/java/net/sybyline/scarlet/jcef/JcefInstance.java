package net.sybyline.scarlet.jcef;

import java.util.concurrent.CompletableFuture;

import net.sybyline.scarlet.Scarlet;

public class JcefInstance extends JcefService.JcefBrowser
{

    protected JcefInstance(JcefService jcef, String startURL, boolean hidden)
    {
        jcef.super(startURL, hidden);
    }

    protected CompletableFuture<Void>  setElementByIdValue(String id, Object value)
    {
        this.executeJavaScript("document.getElementById('"+id+"').value="+Scarlet.GSON.toJson(value)+";", "set:"+id, 1);
        return CompletableFuture.completedFuture(null);
    }

}
