package net.sybyline.scarlet;

import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sybyline.scarlet.util.MiscUtils;

public class ScarletUI implements AutoCloseable
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/UI");

    public ScarletUI(Scarlet scarlet)
    {
        this.scarlet = scarlet;
        this.jframe = new JFrame(Scarlet.NAME+" "+Scarlet.VERSION);
        this.initUI();
    }

    final Scarlet scarlet;
    final JFrame jframe;

    void initUI()
    {
        {
            JMenuBar jmenubar = new JMenuBar();
            {
                JMenu jmenu_file = new JMenu("File");
                    jmenu_file.add("Browse data folder").addActionListener($ -> MiscUtils.AWTDesktop.browse(Scarlet.dir.toURI()));
                jmenubar.add(jmenu_file);
            }
            {
                JMenu jmenu_help = new JMenu("Help");
                    jmenu_help.add("Open Github webpage").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(Scarlet.GITHUB_URL)));
                jmenubar.add(jmenu_help);
            }
            this.jframe.setJMenuBar(jmenubar);
        }
        this.jframe.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        this.jframe.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                ScarletUI.this.scarlet.stop();
            }
        });
        this.jframe.addComponentListener(new ComponentAdapter() 
        {
            @Override
            public void componentResized(ComponentEvent evt)
            {
                ScarletUI.this.scarlet.settings.setUIBounds(ScarletUI.this.jframe.getBounds());
            }
            @Override
            public void componentMoved(ComponentEvent evt)
            {
                ScarletUI.this.scarlet.settings.setUIBounds(ScarletUI.this.jframe.getBounds());
            }
        });
        Rectangle uiBounds = this.scarlet.settings.getUIBounds();
        if (uiBounds != null)
            this.jframe.setBounds(uiBounds);
        else
            this.jframe.setBounds(100, 100, 600, 400);
        this.jframe.setResizable(true);
        this.jframe.setEnabled(true);
        this.jframe.setVisible(true);
    }

    @Override
    public void close() throws Exception
    {
        this.jframe.dispose();
    }

}
