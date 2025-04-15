package net.sybyline.scarlet.jcef;

import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.event.MouseEvent;
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

    protected void injectClick(int x, int y, int button)
    {
        long now = System.currentTimeMillis();
        boolean is = this.isFocused();
        try
        {
            if (!is) this.setFocus(true);
            Point screen = new Point(x, y);
            try
            {
                screen.setLocation(this.component.getLocationOnScreen());
            }
            catch (IllegalComponentStateException icsex)
            {
            }
            screen.translate(x, y);
            this.sendMouseEvent(new MouseEvent(this.component, MouseEvent.MOUSE_PRESSED, now, 0, x, y, screen.x, screen.y, 1, false, button));
            this.sendMouseEvent(new MouseEvent(this.component, MouseEvent.MOUSE_RELEASED, now, 0, x, y, screen.x, screen.y, 1, false, button));
            this.sendMouseEvent(new MouseEvent(this.component, MouseEvent.MOUSE_CLICKED, now, 0, x, y, screen.x, screen.y, 1, false, button));
        }
        finally
        {
            if (!is) this.setFocus(false);
        }
    }

}
