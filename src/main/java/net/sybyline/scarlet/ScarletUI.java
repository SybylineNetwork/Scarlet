package net.sybyline.scarlet;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.model.User;

import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.PropsTable;
import net.sybyline.scarlet.util.VRChatWebPageURLs;

public class ScarletUI implements AutoCloseable
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/UI");
    static final DateTimeFormatter LTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                                   DUR = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception ex)
        {
            LOG.error("Exception setting system look and feel", ex);
        }
    }

    public static interface Option<T>
    {
        
    }

    public ScarletUI(Scarlet scarlet)
    {
        this.scarlet = scarlet;
        this.jframe = new JFrame(Scarlet.NAME+" "+Scarlet.VERSION);
        this.jtabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        this.propstable = new PropsTable<>();
        this.propstableColumsDirty = false;
        this.connectedPlayers = new HashMap<>();
        this.initUI();
    }

    public synchronized void playerJoin(String id, String name, LocalDateTime joined, String advisory, boolean isRejoinFromPrev)
    {
        User user = this.scarlet.vrc.getUser(id);
        String periodString = null;
        if (user != null)
        {
            Period period = user.getDateJoined().until(LocalDate.now());
            StringBuilder sb = new StringBuilder();
            if (period.getYears() != 0)
                sb.append(period.getYears()).append('y');
            if (period.getMonths() != 0)
                (sb.length() == 0 ? sb : sb.append(' ')).append(period.getMonths()).append('m');
            (sb.length() == 0 ? sb : sb.append(' ')).append(period.getDays()).append('d');
            periodString = sb.toString();
        }
        
        ConnectedPlayer player = this.connectedPlayers.get(id);
        if (player != null)
        {
            player.name = name;
            if (!isRejoinFromPrev)
            {
                player.joined = LTF.format(joined);
            }
            player.acctdays = periodString;
            player.left = null;
            player.advisory = advisory;
            this.propstable.updateEntry(player);
        }
        else
        {
            player = new ConnectedPlayer();
            player.id = id;
            player.name = name;
            if (!isRejoinFromPrev)
            {
                player.joined = LTF.format(joined);
            }
            player.acctdays = periodString;
            player.advisory = advisory;
            this.connectedPlayers.put(id, player);
            this.propstable.addEntry(player);
        }
    }

    public synchronized void playerLeave(String id, String name, LocalDateTime left)
    {
        ConnectedPlayer player = this.connectedPlayers.get(id);
        if (player != null)
        {
            player.name = name;
            player.left = LTF.format(left);
            this.propstable.updateEntry(player);
        }
        else
        {
            player = new ConnectedPlayer();
            player.id = id;
            player.name = name;
            player.left = LTF.format(left);
            this.connectedPlayers.put(id, player);
            this.propstable.addEntry(player);
        }
    }

    public synchronized void clearInstance()
    {
        this.connectedPlayers.clear();
        this.propstable.clearEntries();
    }

    final Scarlet scarlet;
    final JFrame jframe;
    final JTabbedPane jtabs;
    final PropsTable<ConnectedPlayer> propstable;
    boolean propstableColumsDirty;
    final Map<String, ConnectedPlayer> connectedPlayers;
    static class ConnectedPlayer
    {
        String name;
        String id;
        String acctdays;
        String joined;
        String left;
        String advisory;
        Action profile = new AbstractAction("Open") {
            private static final long serialVersionUID = -7804449090453940172L;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                LOG.info("Click on "+ConnectedPlayer.this.id+" "+e);
//                MiscUtils.AWTDesktop.browse(URI.create("https://vrchat.com/home/user/"+ConnectedPlayer.this.id));
            }
        };
    }

    public static final class UIPropsInfo
    {
        public static final TypeToken<Map<String, UIPropsInfo>> MAPOF = new TypeToken<Map<String, UIPropsInfo>>(){};
        public UIPropsInfo(int index, int width)
        {
            this.index = index;
            this.width = width;
        }
        public UIPropsInfo()
        {
        }
        public int index;
        public int width;
    }
    void initUI()
    {
        // Properties
        {
            this.propstable.addProperty("Name", false, true, String.class, $ -> $.name);
            this.propstable.addProperty("Id", false, true, String.class, $ -> $.id);
            this.propstable.addProperty("AcctAge", false, true, String.class, $ -> $.acctdays);
            this.propstable.addProperty("Joined", false, true, String.class, $ -> $.joined);
            this.propstable.addProperty("Left", false, true, String.class, $ -> $.left);
            this.propstable.addProperty("Advisory", false, true, String.class, $ -> $.advisory);
            this.propstable.addProperty("Profile", true, true, Action.class, $ -> $.profile);
            
            this.propstable.getColumnModel().addColumnModelListener(new TableColumnModelListener()
            {
                @Override
                public void columnSelectionChanged(ListSelectionEvent e) {}
                @Override
                public void columnRemoved(TableColumnModelEvent e)
                {
                    ScarletUI.this.propstableColumsDirty = true;
                }
                @Override
                public void columnMoved(TableColumnModelEvent e)
                {
                    ScarletUI.this.propstableColumsDirty = true;
                }
                @Override
                public void columnMarginChanged(ChangeEvent e)
                {
                    ScarletUI.this.propstableColumsDirty = true;
                }
                @Override
                public void columnAdded(TableColumnModelEvent e)
                {
                    ScarletUI.this.propstableColumsDirty = true;
                }
            });
            this.loadInstanceColumns();
        }
        // Menu
        {
            JMenuBar jmenubar = new JMenuBar();
            {
                JMenu jmenu_file = new JMenu("File");
                {
                    jmenu_file.add("Browse data folder").addActionListener($ -> MiscUtils.AWTDesktop.browse(Scarlet.dir.toURI()));
                    jmenu_file.addSeparator();
                    jmenu_file.add("Quit").addActionListener($ -> this.uiModalExit());
                }
                jmenubar.add(jmenu_file);
            }
            {
                JMenu jmenu_edit = new JMenu("Edit");
                {
                    JMenu jmenu_props = this.propstable.getColumnSelectMenu();
                    jmenu_props.setText("Columns");
                    jmenu_edit.add(jmenu_props);
                }
                jmenubar.add(jmenu_edit);
            }
            {
                JMenu jmenu_help = new JMenu("Help");
                {
                    jmenu_help.add("Scarlet Github").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(Scarlet.GITHUB_URL)));
                    jmenu_help.add("Scarlet License").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(Scarlet.LICENSE_URL)));
                    jmenu_help.addSeparator();
                    jmenu_help.add("VRChat Terms of Service").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(VRChatWebPageURLs.VRCHAT_TOS_URL)));
                    jmenu_help.add("VRChat Community Guidelines").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(VRChatWebPageURLs.VRCHAT_CG_URL)));
                }
                jmenubar.add(jmenu_help);
            }
            this.jframe.setJMenuBar(jmenubar);
        }
        // Tabs
        {
            {
                this.jtabs.addTab("Instance", new JScrollPane(this.propstable));
            }
            {
                JPanel jpanel_settings = new JPanel();
                jpanel_settings.setLayout(new GridBagLayout());
                GridBagConstraints gbc_settings = new GridBagConstraints();
                {
                    // TODO : settings
                    jpanel_settings.add(new JLabel("// TODO"), gbc_settings);
                }
                this.jtabs.addTab("Settings", new JScrollPane(jpanel_settings));
            }
            this.jframe.add(this.jtabs, BorderLayout.CENTER);
        }
        // Frame
        {
            this.jframe.setIconImage(Toolkit.getDefaultToolkit().createImage(ScarletUI.class.getResource("sybyline_scarlet.png")));
            this.jframe.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            this.jframe.addWindowListener(new WindowAdapter()
            {
                @Override
                public void windowClosing(WindowEvent e)
                {
                    ScarletUI.this.uiModalExit();
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
        this.scarlet.exec.scheduleAtFixedRate(this::saveInstanceColumnsIfDirty, 60_000L, 60_000L, TimeUnit.MILLISECONDS);
    }

    void loadInstanceColumns()
    {
        Map<String, UIPropsInfo> map = this.scarlet.settings.getObject("ui_instance_columns", UIPropsInfo.MAPOF);
        if (map == null || map.isEmpty())
            return;
        map.entrySet()
            .stream()
            .sorted((l, r) -> Integer.compare(l.getValue().index, r.getValue().index))
            .forEachOrdered($ ->
            {
                PropsTable<ConnectedPlayer>.PropsInfo<?> pinfo = this.propstable.getProp($.getKey());
                if (pinfo == null)
                    return;
                UIPropsInfo uiinfo = $.getValue();
                pinfo.setWidth(uiinfo.width);
                pinfo.setEnabled(uiinfo.index >= 0);
                pinfo.setDisplayIndex(uiinfo.index);
            });
    }

    void saveInstanceColumnsIfDirty()
    {
        if (!this.propstableColumsDirty)
            return;
        this.propstableColumsDirty = false;
        this.saveInstanceColumns();
    }

    void saveInstanceColumns()
    {
        Map<String, UIPropsInfo> map = new HashMap<>();
        this.propstable.iterProps(info -> map.put(info.getName(), new UIPropsInfo(info.getDisplayIndex(), info.getWidth())));
        this.scarlet.settings.setObject("ui_instance_columns", UIPropsInfo.MAPOF, map);
    }

    void uiModalExit()
    {
        this.scarlet.execModal.execute(() ->
        {
            if (JOptionPane.showConfirmDialog(this.jframe, "Are you sure you want to quit?", "Confirm quit", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
            {
                this.scarlet.stop();
            }
        });
    }

    @Override
    public void close() throws Exception
    {
        this.saveInstanceColumns();
        this.jframe.dispose();
    }

}
