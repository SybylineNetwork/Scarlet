package org.cef.browser;

import java.awt.Component;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.event.MouseEvent;

public final class CefDragGestureRecognizer extends DragGestureRecognizer
{

    private static final long serialVersionUID = 8946333142837916920L;

    public CefDragGestureRecognizer(Component c, int action, MouseEvent triggerEvent)
    {
        super(new DragSource(), c, action);
        this.appendEvent(triggerEvent);
    }

    @Override
    protected void registerListeners()
    {
    }

    @Override
    protected void unregisterListeners()
    {
    }

}
