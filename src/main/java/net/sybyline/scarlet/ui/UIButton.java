package net.sybyline.scarlet.ui;

import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JButton;

public class UIButton extends JButton
{

    private static final long serialVersionUID = -869414285040954095L;

    public UIButton()
    {
        super();
    }

    public UIButton(Icon icon)
    {
        super(icon);
    }

    public UIButton(String text)
    {
        super(text);
    }

    public UIButton(String text, Icon icon)
    {
        super(text, icon);
    }

    public UIButton onPress(Consumer<UIButton> listener)
    {
        this.addActionListener(e -> listener.accept(this));
        return this;
    }

}
