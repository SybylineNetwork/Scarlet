package net.sybyline.scarlet.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JPanel;

public class UICollapsablePanel extends JPanel
{

    private static final long serialVersionUID = -4183653750085197597L;

    public UICollapsablePanel()
    {
        this(false);
    }
    public UICollapsablePanel(boolean expanded)
    {
        super();
        this.contentToggle = new UIButton().onPress($ -> this.toggleExpanded());
        this.contentHeader = new JPanel();
        this.contentBody = new JPanel();
        this.expanded = expanded;
        this.init();
    }

    protected UIButton contentToggle;
    protected JPanel contentHeader, contentBody;
    protected boolean expanded;

    public JPanel getContentHeader()
    {
        return this.contentHeader;
    }

    public JPanel getContentBody()
    {
        return this.contentBody;
    }

    public boolean isExpanded()
    {
        return this.expanded;
    }

    public synchronized void toggleExpanded()
    {
        this.setExpanded(!this.isExpanded());
    }

    public synchronized void setExpanded(boolean expanded)
    {
        if (this.expanded == expanded)
            return;
        this.expanded = expanded;
        this.update();
    }

    protected synchronized void init()
    {
        this.removeAll();
        this.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = new Insets(1, 1, 1, 1);
        constraints.weightx = 0.0D;
        constraints.weighty = 0.0D;
        this.add(this.contentToggle, constraints);
        constraints.gridwidth = GridBagConstraints.REMAINDER;
        constraints.gridx = 1;
        constraints.anchor = GridBagConstraints.WEST;
        this.add(this.contentHeader, constraints);
        this.contentToggle.setFont(Swing.MONOSPACED);
        this.update();
    }

    protected synchronized void update()
    {
        boolean expanded = this.expanded;
        this.contentToggle.setText(expanded ? "-" : "+");
        if (expanded)
        {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 1;
            constraints.insets = new Insets(1, 1, 1, 1);
            constraints.weightx = 0.0D;
            constraints.weighty = 0.0D;
            constraints.anchor = GridBagConstraints.WEST;
            constraints.gridwidth = GridBagConstraints.REMAINDER;
            constraints.gridheight = GridBagConstraints.REMAINDER;
            this.add(this.contentBody, constraints);
        }
        else
        {
            this.remove(this.contentBody);
        }
    }

}
