package net.sybyline.scarlet;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.model.AgeVerificationStatus;
import io.github.vrchatapi.model.FileAnalysis;
import io.github.vrchatapi.model.FileAnalysisAvatarStats;
import io.github.vrchatapi.model.GroupJoinRequestAction;
import io.github.vrchatapi.model.GroupMemberStatus;
import io.github.vrchatapi.model.ModelFile;
import io.github.vrchatapi.model.User;

import net.sybyline.scarlet.ScarletSettings.FileValued;
import net.sybyline.scarlet.ext.AvatarBundleInfo;
import net.sybyline.scarlet.ui.Swing;
import net.sybyline.scarlet.util.Credits;
import net.sybyline.scarlet.util.Func;
import net.sybyline.scarlet.util.HttpURLInputStream;
import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.PropsTable;
import net.sybyline.scarlet.util.VrcWeb;
import net.sybyline.scarlet.util.VersionedFile;

public class ScarletUI implements IScarletUI
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/UI");
    static
    {
        Swing.init();
    }

    public ScarletUI(Scarlet scarlet)
    {
        this.scarlet = scarlet;
        this.jframe = new JFrame(Scarlet.NAME);
        this.jtabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        this.propstable = new PropsTable<>();
        this.jpanel_settings = new JPanel();
        this.jlabel_lastSavedAt = new JLabel("", JLabel.LEFT);
        this.ssettings = Collections.synchronizedList(new ArrayList<>());
        this.propstableColumsDirty = false;
        this.connectedPlayers = new HashMap<>();
        this.pendingUpdates = new HashMap<>();
        
        this.initUI();
    }

    public void jframe(Consumer<JFrame> edit)
    {
        edit.accept(this.jframe);
    }

    public void setUIScale()
    {
        if (GraphicsEnvironment.isHeadless())
            return;
        this.scarlet.execModal.execute(() ->
        {
            JSlider slider = new JSlider(50, 400, 100);
            slider.setSnapToTicks(true);
            slider.setPaintTicks(true);
            slider.setMajorTickSpacing(10);
            Float uiScale = this.scarlet.settings.getObject("ui_scale", Float.class);
            if (uiScale != null)
                slider.setValue(Math.round(uiScale.floatValue() * 100));
            JLabel label = new JLabel(slider.getValue()+"%");
            slider.addChangeListener($->label.setText(slider.getValue()+"%"));
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(label, BorderLayout.NORTH);
            panel.add(slider, BorderLayout.CENTER);
            if (JOptionPane.showConfirmDialog(null, panel, "Set UI scale %", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION)
            {
                float newUiScale = 0.01F * (float)slider.getValue();
                this.scarlet.settings.setObject("ui_scale", Float.class, newUiScale);
                this.messageModalAsyncInfo(null, "UI scale will take effect on restart", "UI scale updated");
            }
        });
    }

    private void updatePending(String id, ConnectedPlayer player)
    {
        List<Func.V1.NE<ConnectedPlayer>> pending = this.pendingUpdates.remove(id);
        if (pending != null)
            for (Func.V1.NE<ConnectedPlayer> update : pending)
                update.invoke(player);
    }

    public synchronized void playerJoin(boolean initialPreamble, String id, String name, LocalDateTime joined, String advisory, Color text_color, int priority, boolean isRejoinFromPrev)
    {
        User user = this.scarlet.vrc.getUser(id);
        Period period = null;
        if (user != null)
        {
            period = user.getDateJoined().until(LocalDate.now(ZoneOffset.UTC));
        }
        
        ConnectedPlayer player = this.connectedPlayers.get(id);
        if (player != null)
        {
            player.name = name;
            if (!isRejoinFromPrev)
            {
                player.joined = joined;
            }
            player.acctdays = period;
            player.left = null;
            player.advisory = advisory;
            player.text_color = text_color;
            player.priority = priority;
            player.ageVerificationStatus = user.getAgeVerificationStatus();
            player.avatarInfo = this.scarlet.eventListener.clientLocation_userDisplayName2avatarBundleInfo.get(name);
            this.updatePending(id, player);
            if (initialPreamble)
            {
                ; // noop
            }
            else
            {
                this.propstable.updateEntry(player);
            }
        }
        else
        {
            player = new ConnectedPlayer();
            player.id = id;
            player.name = name;
            if (!isRejoinFromPrev)
            {
                player.joined = joined;
            }
            player.acctdays = period;
            player.advisory = advisory;
            player.text_color = text_color;
            player.priority = priority;
            player.ageVerificationStatus = user.getAgeVerificationStatus();
            player.avatarInfo = this.scarlet.eventListener.clientLocation_userDisplayName2avatarBundleInfo.get(name);
            this.updatePending(id, player);
            this.connectedPlayers.put(id, player);
            if (initialPreamble)
            {
                this.propstable.addEntrySilently(player);
            }
            else
            {
                this.propstable.addEntry(player);
            }
        }
        if (initialPreamble)
        {
            ; // noop
        }
        else
        {
            this.fireSort();
        }
    }

    public synchronized void playerUpdate(boolean initialPreamble, String id, Func.V1.NE<ConnectedPlayer> update)
    {
        ConnectedPlayer player = this.connectedPlayers.get(id);
        if (player == null)
        {
            List<Func.V1.NE<ConnectedPlayer>> pending = this.pendingUpdates.get(id);
            if (pending == null)
                this.pendingUpdates.put(id, pending = new ArrayList<>());
            pending.add(update);
            return;
        }
        update.invoke(player);
        this.propstable.updateEntry(player);
        if (initialPreamble)
        {
            ; // noop
        }
        else
        {
            this.fireSort();
        }
    }

    public synchronized void playerLeave(boolean initialPreamble, String id, String name, LocalDateTime left)
    {
        ConnectedPlayer player = this.connectedPlayers.get(id);
        if (player != null)
        {
            player.name = name;
            player.left = left;
            this.updatePending(id, player);
            if (initialPreamble)
            {
                ; // noop
            }
            else
            {
                this.propstable.updateEntry(player);
            }
        }
        else
        {
            player = new ConnectedPlayer();
            player.id = id;
            player.name = name;
            player.left = left;
            this.updatePending(id, player);
            this.connectedPlayers.put(id, player);
            if (initialPreamble)
            {
                this.propstable.addEntrySilently(player);
            }
            else
            {
                this.propstable.addEntry(player);
            }
        }
        if (initialPreamble)
        {
            ; // noop
        }
        else
        {
            this.fireSort();
        }
    }

    public synchronized void clearInstance()
    {
        this.connectedPlayers.clear();
        this.propstable.clearEntries();
    }

    public synchronized void fireSort()
    {
        this.propstable.sortEntries(COMPARE);
    }

    private final Scarlet scarlet;
    private final JFrame jframe;
    private final JTabbedPane jtabs;
    private final PropsTable<ConnectedPlayer> propstable;
    private final JPanel jpanel_settings;
    private final JLabel jlabel_lastSavedAt;
    private final List<GUISetting<?>> ssettings;
    private boolean propstableColumsDirty;
    private final Map<String, ConnectedPlayer> connectedPlayers;
    private final Map<String, List<Func.V1.NE<ConnectedPlayer>>> pendingUpdates;
    
    class ConnectedPlayer
    {
        String name;
        String id;
        String avatarName;
        AvatarBundleInfo avatarInfo;
        Action avatarStats = new AbstractAction("View") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                ScarletUI.this.infoStats(ConnectedPlayer.this.name, ConnectedPlayer.this.avatarName, ConnectedPlayer.this.avatarInfo);
            }
            @Override
            public String toString()
            {
                return "View";
            }
        };
        Period acctdays;
        LocalDateTime joined;
        LocalDateTime left;
        String advisory;
        Action profile = new AbstractAction("Open") {
            private static final long serialVersionUID = -7804449090453940172L;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                MiscUtils.AWTDesktop.browse(URI.create("https://vrchat.com/home/user/"+ConnectedPlayer.this.id));
            }
            @Override
            public String toString()
            {
                return "https://vrchat.com/home/user/"+ConnectedPlayer.this.id;
            }
        };
        Action ban = new AbstractAction("Ban") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                ScarletUI.this.scarlet.settings.requireConfirmYesNoAsync("Are you sure you want to ban "+ConnectedPlayer.this.name+"?", "Confirm ban",
                    () -> ScarletUI.this.tryBan(ConnectedPlayer.this.id, ConnectedPlayer.this.name), null);
            }
            @Override
            public String toString()
            {
                return "Ban";
            }
        };
        Action unban = new AbstractAction("Unban") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                ScarletUI.this.scarlet.settings.requireConfirmYesNoAsync("Are you sure you want to unban "+ConnectedPlayer.this.name+"?", "Confirm unban",
                    () -> ScarletUI.this.tryUnban(ConnectedPlayer.this.id, ConnectedPlayer.this.name), null);
            }
            @Override
            public String toString()
            {
                return "Unban";
            }
        };
        Action copy = new AbstractAction("Copy") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ConnectedPlayer.this.id), null);
            }
            @Override
            public String toString()
            {
                return "Copy";
            }
        };
        Action invite = new AbstractAction("Invite") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                ScarletUI.this.tryInvite(ConnectedPlayer.this.id, ConnectedPlayer.this.name);
            }
            @Override
            public String toString()
            {
                return "Invite";
            }
        };
        Color text_color;
        int priority;
        AgeVerificationStatus ageVerificationStatus;
    }
    static final Comparator<ConnectedPlayer> COMPARE = Comparator
        .<ConnectedPlayer>comparingInt($ -> 0) // dummy
        .thenComparingInt($ -> $.left == null ? 0 : 1)
        .thenComparingInt($ -> -$.priority)
        .thenComparing($ -> $.joined, Comparator.nullsLast(Comparator.naturalOrder()))
        ;

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
    private void initUI()
    {
        // Properties
        {
            this.propstable.addProperty("Name", false, true, String.class, $ -> $.name);
            this.propstable.addProperty("Id", false, true, String.class, $ -> $.id);
            this.propstable.addProperty("Avatar", false, true, String.class, $ -> $.avatarName);
            this.propstable.addProperty("Performance", false, true, String.class, $ -> $.avatarInfo==null?null:$.avatarInfo.analysis==null?null:$.avatarInfo.analysis.getPerformanceRating());
            this.propstable.addProperty("Avatar Stats", true, true, Action.class, $ -> $.avatarStats);
            this.propstable.addProperty("AcctAge", "Acc-Age", false, true, Period.class, $ -> $.acctdays);
            this.propstable.addProperty("Joined", false, true, LocalDateTime.class, $ -> $.joined);
            this.propstable.addProperty("Left", false, true, LocalDateTime.class, $ -> $.left);
            this.propstable.addProperty("Advisory", false, true, String.class, $ -> $.advisory);
            this.propstable.addProperty("AgeVer", "18+", false, true, AgeVerificationStatus.class, $ -> $.ageVerificationStatus);
            this.propstable.addProperty("Profile", true, true, Action.class, $ -> $.profile);
            this.propstable.addProperty("Copy ID", true, true, Action.class, $ -> $.copy);
            this.propstable.addProperty("Ban", true, true, Action.class, $ -> $.ban);
            this.propstable.addProperty("Unban", true, false, Action.class, $ -> $.unban);
            this.propstable.addProperty("Invite", true, false, Action.class, $ -> $.invite);
            
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
            this.propstable.setPropsTableExt(this.propstable.new PropsTableExt()
            {
                @Override
                public Color getOverrideForegroundColor(ConnectedPlayer element, Color prev)
                {
                    if (element.text_color != null)
                        return element.text_color;
                    return super.getOverrideForegroundColor(element, prev);
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
                {
                    JMenu jmenu_importwg = new JMenu("Import watched groups");
                    {
                        jmenu_importwg.add("From URL").addActionListener($ -> this.importWG(false));
                        jmenu_importwg.add("From File").addActionListener($ -> this.importWG(true));
                    }
                    jmenu_edit.add(jmenu_importwg);
                }
                {
                    JMenu jmenu_advanced = new JMenu("Advanced");
                    {
                        jmenu_advanced.add("Discord: update command list").addActionListener($ -> this.discordUpdateCommandList());
                    }
                    jmenu_edit.add(jmenu_advanced);
                }
                jmenubar.add(jmenu_edit);
            }
            {
                JMenu jmenu_help = new JMenu("Help");
                {
                    jmenu_help.add("Scarlet: VRChat Group").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(Scarlet.SCARLET_VRCHAT_GROUP_URL)));
                    jmenu_help.add("Scarlet: Github").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(Scarlet.GITHUB_URL)));
                    jmenu_help.add("Scarlet: License").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(Scarlet.LICENSE_URL)));
                    jmenu_help.add("Scarlet: Credits").addActionListener($ -> this.infoCredits());
                    jmenu_help.addSeparator();
                    jmenu_help.add("VRChat: Terms of Service").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(VrcWeb.TERMS_OF_SERVICE)));
                    jmenu_help.add("VRChat: Privacy Policy").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(VrcWeb.PRIVACY_POLICY+"#7")));
                    jmenu_help.add("VRChat: Community Guidelines").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(VrcWeb.Community.GUIDELINES)));
                    jmenu_help.addSeparator();
                    jmenu_help.add("VRChat Community (unofficial): API Documentation").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(Scarlet.COMMUNITY_URL)));
                    jmenu_help.add("VRChat Community (unofficial): API Documentation Github").addActionListener($ -> MiscUtils.AWTDesktop.browse(URI.create(Scarlet.COMMUNITY_GITHUB_URL)));
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
                this.jpanel_settings.setLayout(new GridBagLayout());
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
                    ScarletUI.this.scarlet.settings.uiBounds.set(ScarletUI.this.jframe.getBounds());
                }
                @Override
                public void componentMoved(ComponentEvent evt)
                {
                    ScarletUI.this.scarlet.settings.uiBounds.set(ScarletUI.this.jframe.getBounds());
                }
            });
            Rectangle uiBounds = this.scarlet.settings.uiBounds.getOrNull();
            if (uiBounds != null)
                this.jframe.setBounds(uiBounds);
            else
                this.jframe.setBounds(100, 100, 600, 400);
        }
        this.scarlet.exec.scheduleAtFixedRate(this::saveIfDirty, 10_000L, 10_000L, TimeUnit.MILLISECONDS);
    }

    private void loadInstanceColumns()
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

    private void saveIfDirty()
    {
        this.saveSettings(false);
        if (!this.propstableColumsDirty)
            return;
        this.propstableColumsDirty = false;
        this.saveInstanceColumns();
    }

    private void saveInstanceColumns()
    {
        Map<String, UIPropsInfo> map = new HashMap<>();
        this.propstable.iterProps(info -> map.put(info.getId(), new UIPropsInfo(info.getDisplayIndex(), info.getWidth())));
        this.scarlet.settings.setObject("ui_instance_columns", UIPropsInfo.MAPOF, map);
    }

    private void uiModalExit()
    {
        this.scarlet.settings.requireConfirmYesNoAsync("Are you sure you want to quit?", "Confirm exit", this.scarlet::stop, null);
    }

    private void messageModalAsyncInfo(Component component, Object message, String title)
    {
        this.scarlet.execModal.execute(() -> JOptionPane.showMessageDialog(component != null ? component : this.jframe, message, title, JOptionPane.INFORMATION_MESSAGE));
    }

    private void importWG(boolean isFile)
    {
        if (isFile)
        {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("CSV or JSON", "csv", "json"));
            if (chooser.showDialog(this.jframe, "Import") != JFileChooser.APPROVE_OPTION)
            {
                this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation canceled", Color.PINK);
                return;
            }
            File file = chooser.getSelectedFile();
            try (Reader reader = MiscUtils.reader(file))
            {
                if (file.getName().endsWith(".csv"))
                {
                    if (this.scarlet.watchedGroups.importLegacyCSV(reader, true))
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation succeeded", Color.WHITE);
                    }
                    else
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation failed", Color.PINK);
                    }
                }
                else if (file.getName().endsWith(".json"))
                {
                    if (this.scarlet.watchedGroups.importJson(reader, true))
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation succeeded", Color.WHITE);
                    }
                    else
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation failed", Color.PINK);
                    }
                }
                else
                {
                    this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Unrecognized file type", Color.PINK);
                }
            }
            catch (Exception ex)
            {
                LOG.error("Exception importing watched groups from "+file, ex);
                this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation failed", Color.PINK);
            }
        }
        else
        {
            String url = this.scarlet.settings.requireInput("URL of CSV or JSON", false);
            try (Reader reader = new InputStreamReader(HttpURLInputStream.get(url), StandardCharsets.UTF_8))
            {
                if (url.contains(".csv"))
                {
                    if (this.scarlet.watchedGroups.importLegacyCSV(reader, true))
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation succeeded", Color.WHITE);
                    }
                    else
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation failed", Color.PINK);
                    }
                }
                else if (url.contains(".json"))
                {
                    if (this.scarlet.watchedGroups.importJson(reader, true))
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation succeeded", Color.WHITE);
                    }
                    else
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation failed", Color.PINK);
                    }
                }
                else
                {
                    this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Unrecognized file type", Color.PINK);
                }
            }
            catch (Exception ex)
            {
                LOG.error("Exception importing watched groups from "+url, ex);
                this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation failed", Color.PINK);
            }
        }
    }

    private final AtomicLong discordUpdateCommandListlastUpdated = new AtomicLong();
    private void discordUpdateCommandList()
    {
        long then = this.discordUpdateCommandListlastUpdated.get(),
             now = System.currentTimeMillis();
        if (then > (now - 3600_000L))
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Skipped, too fast", Color.PINK);
            return;
        }
        if (!this.discordUpdateCommandListlastUpdated.compareAndSet(then, now))
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Skipped, file type", Color.PINK);
            return;
        }
        this.scarlet.splash.queueFeedbackPopup(this.jframe, 3_000L, "Operation queued", Color.WHITE);
        this.scarlet.execModal.execute(this.scarlet.discord::updateCommandList);
    }

    private static void infoStatsAppend(JPanel panel, GridBagConstraints constraints, String name)
    {
        constraints.gridx = 0;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(name, JLabel.LEFT), constraints);
        constraints.gridy++;
    }
    private static void infoStatsAppend(JPanel panel, GridBagConstraints constraints, String name, Supplier<Object> getter)
    {
        Object value = getter.get();
        if (value == null)
            return;
        constraints.gridx = 0;
        constraints.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel(name+":", JLabel.RIGHT), constraints);
        constraints.gridx = 1;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(value.toString(), JLabel.LEFT), constraints);
        constraints.gridy++;
    }
    private void infoStats(String name, String avatarDisplayName, AvatarBundleInfo bundleInfo)
    {
        JPanel panel = new JPanel(new GridBagLayout());
        {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridheight = 1;
            constraints.gridwidth = 1;
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.insets = new Insets(1, 1, 1, 1);
            constraints.weightx = 0.0D;
            constraints.weighty = 0.0D;
            FileAnalysis analysis = bundleInfo.analysis;
            VersionedFile versionedFile = bundleInfo.id;
            ModelFile file = bundleInfo.file;
            FileAnalysisAvatarStats stats = analysis.getAvatarStats();
            infoStatsAppend(panel, constraints, "Avatar name", ()->avatarDisplayName);
            infoStatsAppend(panel, constraints, "File statistics");
            if (file != null)
            {
                infoStatsAppend(panel, constraints, "Owner ID", file::getOwnerId);
                infoStatsAppend(panel, constraints, "Owner name", ()->this.scarlet.vrc.getUserDisplayName(file.getOwnerId()));
            }
            if (analysis != null)
            {
                infoStatsAppend(panel, constraints, "Created at", analysis::getCreatedAt);
                infoStatsAppend(panel, constraints, "Performance rating", analysis::getPerformanceRating);
                infoStatsAppend(panel, constraints, "Uncompressed size", analysis::getUncompressedSize);
                infoStatsAppend(panel, constraints, "File size", analysis::getFileSize);
            }
            if (versionedFile != null)
            {
                infoStatsAppend(panel, constraints, "File version", versionedFile::version);
                infoStatsAppend(panel, constraints, "File qualifier", versionedFile::qualifier);
            }
            if (file != null)
            {
                infoStatsAppend(panel, constraints, "File version count", ()->file.getVersions().size());
            }
            if (stats != null)
            {
            infoStatsAppend(panel, constraints, "Avatar statistics");
                infoStatsAppend(panel, constraints, "Animator count", stats::getAnimatorCount);
                infoStatsAppend(panel, constraints, "Audio source count", stats::getAudioSourceCount);
                infoStatsAppend(panel, constraints, "Blend shape count", stats::getBlendShapeCount);
                infoStatsAppend(panel, constraints, "Bone count", stats::getBoneCount);
                infoStatsAppend(panel, constraints, "Bounds", stats::getBounds);
                infoStatsAppend(panel, constraints, "Camera count", stats::getCameraCount);
                infoStatsAppend(panel, constraints, "Cloth count", stats::getClothCount);
                infoStatsAppend(panel, constraints, "Constraint count", stats::getConstraintCount);
                infoStatsAppend(panel, constraints, "Constraint depth", stats::getConstraintDepth);
                infoStatsAppend(panel, constraints, "Contact count", stats::getContactCount);
                infoStatsAppend(panel, constraints, "Custom expressions", stats::getCustomExpressions);
                infoStatsAppend(panel, constraints, "Customize animation layers", stats::getCustomizeAnimationLayers);
                infoStatsAppend(panel, constraints, "Enable eye look", stats::getEnableEyeLook);
                infoStatsAppend(panel, constraints, "Light count", stats::getLightCount);
                infoStatsAppend(panel, constraints, "Line renderer count", stats::getLineRendererCount);
                infoStatsAppend(panel, constraints, "Lip sync", stats::getLipSync);
                infoStatsAppend(panel, constraints, "Material count", stats::getMaterialCount);
                infoStatsAppend(panel, constraints, "Material slots used", stats::getMaterialSlotsUsed);
                infoStatsAppend(panel, constraints, "Mesh count", stats::getMeshCount);
                infoStatsAppend(panel, constraints, "Mesh indices", stats::getMeshIndices);
                infoStatsAppend(panel, constraints, "Mesh particle max polygons", stats::getMeshParticleMaxPolygons);
                infoStatsAppend(panel, constraints, "Mesh polygons", stats::getMeshPolygons);
                infoStatsAppend(panel, constraints, "Mesh vertices", stats::getMeshVertices);
                infoStatsAppend(panel, constraints, "Particle collision enabled", stats::getParticleCollisionEnabled);
                infoStatsAppend(panel, constraints, "Particle system count", stats::getParticleSystemCount);
                infoStatsAppend(panel, constraints, "Particle trails enabled", stats::getParticleTrailsEnabled);
                infoStatsAppend(panel, constraints, "Phys bone collider count", stats::getPhysBoneColliderCount);
                infoStatsAppend(panel, constraints, "Phys bone collision check count", stats::getPhysBoneCollisionCheckCount);
                infoStatsAppend(panel, constraints, "Phys bone component count", stats::getPhysBoneComponentCount);
                infoStatsAppend(panel, constraints, "Phys bone transform count", stats::getPhysBoneTransformCount);
                infoStatsAppend(panel, constraints, "Physics colliders", stats::getPhysicsColliders);
                infoStatsAppend(panel, constraints, "Physics rigidbodies", stats::getPhysicsRigidbodies);
                infoStatsAppend(panel, constraints, "Skinned mesh count", stats::getSkinnedMeshCount);
                infoStatsAppend(panel, constraints, "Skinned mesh indices", stats::getSkinnedMeshIndices);
                infoStatsAppend(panel, constraints, "Skinned mesh polygons", stats::getSkinnedMeshPolygons);
                infoStatsAppend(panel, constraints, "Skinned mesh vertices", stats::getSkinnedMeshVertices);
                infoStatsAppend(panel, constraints, "Total cloth vertices", stats::getTotalClothVertices);
                infoStatsAppend(panel, constraints, "Total indices", stats::getTotalIndices);
                infoStatsAppend(panel, constraints, "Total max particles", stats::getTotalMaxParticles);
                infoStatsAppend(panel, constraints, "Total polygons", stats::getTotalPolygons);
                infoStatsAppend(panel, constraints, "Total texture usage", stats::getTotalTextureUsage);
                infoStatsAppend(panel, constraints, "Total vertices", stats::getTotalVertices);
                infoStatsAppend(panel, constraints, "Trail renderer count", stats::getTrailRendererCount);
                infoStatsAppend(panel, constraints, "Write defaults used", stats::getWriteDefaultsUsed);
            }
            
        }
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setSize(new Dimension(500, 300));
        scroll.setPreferredSize(new Dimension(500, 300));
        scroll.setMaximumSize(new Dimension(500, 300));
        ScarletUI.this.messageModalAsyncInfo(null, scroll, name+"'s selected avatar's stats");
    }
    private static void infoCreditsAppend(JPanel panel, GridBagConstraints constraints, Credits credits)
    {
        JLabel name = new JLabel(String.format("<html><a href=\"#\">%s</a></html>", credits.name), JLabel.RIGHT),
               desc = new JLabel(credits.role, JLabel.LEFT);
        name.setCursor(new Cursor(Cursor.HAND_CURSOR));
        name.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                MiscUtils.AWTDesktop.browse(URI.create(credits.url));
            }
        });
        constraints.gridx = 0;
        constraints.anchor = GridBagConstraints.EAST;
        panel.add(name, constraints);
        constraints.gridx = 1;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(desc, constraints);
        constraints.gridy++;
    }
    private void infoCredits()
    {
        if (GraphicsEnvironment.isHeadless())
        {
            Credits[] credits = Credits.load();
            if (credits == null)
            {
                LOG.error("Failed to load credits!?!?");
                return;
            }
            StringBuilder sb = new StringBuilder("Credits:");
            for (Credits credit : credits)
                MiscUtils.fmt(sb, "\n\t[%s](%s): %s", credit.name, credit.url, credit.role);
            LOG.info(sb.toString());
            return;
        }
        JPanel panel = new JPanel(new GridBagLayout());
        {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridheight = 1;
            constraints.gridwidth = 1;
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.insets = new Insets(1, 1, 1, 1);
            constraints.weightx = 0.0D;
            constraints.weighty = 0.0D;
            Credits[] credits = Credits.load();
            if (credits == null)
            {
                LOG.error("Failed to load credits!?!?");
            }
            else for (Credits credit : credits)
            {
                infoCreditsAppend(panel, constraints, credit);
            }
        }
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setSize(new Dimension(700, 200));
        scroll.setPreferredSize(new Dimension(700, 200));
        scroll.setMaximumSize(new Dimension(700, 200));
        ScarletUI.this.messageModalAsyncInfo(null, scroll, "Credits");
    }

    private void tryBan(String id, String name)
    {
        String ownerId = this.scarlet.vrc.groupOwnerId;
        
        if (!this.scarlet.staffMode)
        if (ownerId == null)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Internal error", "Group owner id missing", Color.PINK);
            return;
        }
        
        GroupMemberStatus status = this.scarlet.vrc.getGroupMembershipStatus(this.scarlet.vrc.groupId, id);
        
        if (status == GroupMemberStatus.BANNED)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "User already banned");
            return;
        }
        
        if (!this.scarlet.staffMode)
        if (this.scarlet.pendingModActions.addPending(GroupAuditType.USER_BAN, id, ownerId) != null)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "User ban pending", name, Color.CYAN);
            return;
        }
        
        if (!this.scarlet.vrc.banFromGroup(id))
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Failed to ban user", name, Color.PINK);
            return;
        }
        
        this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Banned user", name);
    }

    private void tryUnban(String id, String name)
    {
        String ownerId = this.scarlet.vrc.groupOwnerId;

        if (!this.scarlet.staffMode)
        if (ownerId == null)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Internal error", "Group owner id missing", Color.PINK);
            return;
        }
        
        GroupMemberStatus status = this.scarlet.vrc.getGroupMembershipStatus(this.scarlet.vrc.groupId, id);
        
        if (status != GroupMemberStatus.BANNED)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "User not banned", name);
            return;
        }

        if (!this.scarlet.staffMode)
        if (this.scarlet.pendingModActions.addPending(GroupAuditType.USER_UNBAN, id, ownerId) != null)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "User unban pending", name, Color.CYAN);
            return;
        }
        
        if (!this.scarlet.vrc.unbanFromGroup(id))
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Failed to unban user", name, Color.PINK);
            return;
        }
        
        this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Unbanned user", name);
    }

    private void tryInvite(String id, String name)
    {

        ScarletUI.this.scarlet.execModal.execute(() ->
        {
            String ownerId = this.scarlet.vrc.groupOwnerId;
            
            if (!this.scarlet.staffMode)
            if (ownerId == null)
            {
                this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Internal error", "Group owner id missing", Color.PINK);
                return;
            }
            
            GroupMemberStatus status = this.scarlet.vrc.getGroupMembershipStatus(this.scarlet.vrc.groupId, id);
            
            String question = "Are you sure you want to invite "+name+"?",
                   subquestion = "Confirm invite";
            boolean respond = false;
            if (status != null) switch (status)
            {
            case BANNED:
                this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "User is currently banned", name);
                return;
            case INACTIVE:
                break;
            case INVITED:
                this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "User is already invited", name);
                return;
            case MEMBER:
                this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "User is already a member", name);
                return;
            case REQUESTED:
                respond = true;
                question = "Are you sure you want to accept "+name+"'s group join request?";
                subquestion = "Confirm accept group join request";
                break;
            case USERBLOCKED:
                question = "Are you sure you want to invite "+name+"? (User is currently blocked)";
                break;
            }
            
            
            if (!this.scarlet.confirmGroupInvite.get() || ScarletUI.this.scarlet.settings.requireConfirmYesNo(question, subquestion))
            {
                if (respond)
                {
                    if (!this.scarlet.vrc.respondToGroupJoinRequest(id, GroupJoinRequestAction.ACCEPT, null))
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Failed to accept group join request", name, Color.PINK);
                        return;
                    }
                    
                    this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Accepted group join request", name);
                }
                else
                {
                    if (!this.scarlet.vrc.inviteToGroup(id, Boolean.TRUE))
                    {
                        this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Failed to invite to group", name, Color.PINK);
                        return;
                    }
                    
                    this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Invited to group", name);
                }
            }
        });
        String ownerId = this.scarlet.vrc.groupOwnerId;

        if (!this.scarlet.staffMode)
        if (ownerId == null)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Internal error", "Group owner id missing", Color.PINK);
            return;
        }
        
        GroupMemberStatus status = this.scarlet.vrc.getGroupMembershipStatus(this.scarlet.vrc.groupId, id);
        
        if (status != GroupMemberStatus.BANNED)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "User not banned", name);
            return;
        }

        if (!this.scarlet.staffMode)
        if (this.scarlet.pendingModActions.addPending(GroupAuditType.USER_UNBAN, id, ownerId) != null)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "User unban pending", name, Color.CYAN);
            return;
        }
        
        if (!this.scarlet.vrc.unbanFromGroup(id))
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Failed to unban user", name, Color.PINK);
            return;
        }
        
        this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Unbanned user", name);
    }

    @Override
    public void close()
    {
        this.saveSettings(false);
        this.saveInstanceColumns();
        this.jframe.dispose();
    }

    private void readSettingUI()
    {
        this.jpanel_settings.removeAll();
        GridBagConstraints gbc_settings = new GridBagConstraints();
        gbc_settings.gridx = 0;
        gbc_settings.gridy = 0;
        gbc_settings.insets = new Insets(2, 2, 2, 2);
        for (GUISetting<?> ssetting : this.ssettings)
        {
            gbc_settings.anchor = GridBagConstraints.EAST;
            this.jpanel_settings.add(new JLabel(ssetting.name()+":", JLabel.RIGHT), gbc_settings);
            gbc_settings.gridx = 1;
            gbc_settings.anchor = GridBagConstraints.WEST;
            this.jpanel_settings.add(ssetting.render(), gbc_settings);
            gbc_settings.gridx = 0;
            gbc_settings.gridy++;
        }
        gbc_settings.gridx = 2;
        gbc_settings.fill = GridBagConstraints.BOTH;
        gbc_settings.gridheight = 1;
        gbc_settings.gridwidth = GridBagConstraints.REMAINDER;
        gbc_settings.weightx = 1.0D;
        gbc_settings.weighty = 1.0D;
        gbc_settings.anchor = GridBagConstraints.WEST;
        this.jpanel_settings.add(new JLabel(), gbc_settings);
        
        gbc_settings.gridy++;
        
        gbc_settings.gridx = 0;
        gbc_settings.fill = GridBagConstraints.NONE;
        gbc_settings.gridheight = GridBagConstraints.REMAINDER;
        gbc_settings.gridwidth = 1;
        gbc_settings.weightx = 0.0D;
        gbc_settings.weighty = 0.0D;
        gbc_settings.anchor = GridBagConstraints.EAST;
            JButton save = new JButton("Save");
            save.addActionListener($ -> this.saveSettings(true));
        this.jpanel_settings.add(save, gbc_settings);

        gbc_settings.gridx = 1;
        gbc_settings.fill = GridBagConstraints.BOTH;
        gbc_settings.gridheight = GridBagConstraints.REMAINDER;
        gbc_settings.gridwidth = 1;
        gbc_settings.weightx = 0.0D;
        gbc_settings.weighty = 0.0D;
        gbc_settings.anchor = GridBagConstraints.WEST;
        this.jpanel_settings.add(this.jlabel_lastSavedAt, gbc_settings);
        
        
        gbc_settings.gridx = 2;
        gbc_settings.fill = GridBagConstraints.BOTH;
        gbc_settings.gridheight = GridBagConstraints.REMAINDER;
        gbc_settings.gridwidth = GridBagConstraints.REMAINDER;
        gbc_settings.weightx = 1.0D;
        gbc_settings.weighty = 0.0D;
        gbc_settings.anchor = GridBagConstraints.EAST;
        this.jpanel_settings.add(new JLabel(Scarlet.VERSION, JLabel.RIGHT), gbc_settings);
    }

    public void loadSettings()
    {
        SettingUI settingui = new SettingUI();
        for (ScarletSettings.FileValued<?> fileValued : this.scarlet.settings.fileValuedSettings.values())
        {
            fileValued.visit(settingui);
        }
        this.readSettingUI();
        if (this.scarlet.showUiDuringLoad.get())
        {
            this.jframe.setVisible(true);
        }
    }

    private void saveSettings(boolean showTimeSaved)
    {
        this.scarlet.settings.saveJson();
        if (showTimeSaved)
        {
            String savedText = "Saved settings: " + DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.now());
            this.jlabel_lastSavedAt.setText(savedText);
            this.scarlet.exec.schedule(() -> {
                if (Objects.equals(savedText, this.jlabel_lastSavedAt.getText()))
                    this.jlabel_lastSavedAt.setText("");
            }, 5_000L, TimeUnit.MILLISECONDS);
        }
    }

    public interface GUISetting<T>
    {
        String id();
        String name();
        Component render();
        T get();
        T getDefault();
        void set(T value);
    }

    private class SettingUI implements ScarletSettings.FileValuedVisitor<GUISetting<?>>
    {
        @Override
        public GUISetting<?> visitBasic(FileValued<?> fileValued)
        {
            return null;
        }
        @Override
        public GUISetting<?> visitBoolean(FileValued<Boolean> fileValued, boolean defaultValue)
        {
            return new BoolSetting(fileValued);
        }
        @Override
        public GUISetting<?> visitIntegerRange(FileValued<Integer> fileValued, int defaultValue, int minimum, int maximum)
        {
            return new IntSetting(fileValued);
        }
        @Override
        public <E extends Enum<E>> GUISetting<?> visitEnum(FileValued<E> fileValued, E defaultValue)
        {
            return new EnumSetting<>(fileValued);
        }
        @Override
        public GUISetting<?> visitStringChoice(FileValued<String> fileValued, Supplier<Collection<String>> validValues)
        {
            return null;//new StringSetting(fileValued);
        }
        @Override
        public GUISetting<?> visitStringPattern(FileValued<String> fileValued, String pattern, boolean lenient)
        {
            return new StringSetting(fileValued);
        }
        @Override
        public GUISetting<?> visitStringArrayPattern(FileValued<String[]> fileValued, String pattern, boolean lenient)
        {
            return null;//new StringArr2Setting(pattern, pattern, null, null);
        }
        @Override
        public GUISetting<?> visitVoid(FileValued<Void> fileValued, Runnable task)
        {
            return new VoidSetting(fileValued, task);
        }
    }

    private abstract class ASetting<T, C extends Component> implements GUISetting<T>
    {
//        protected ASetting(ScarletSettings.FileValued<T> setting, Supplier<C> render)
//        {
//            this(setting, render.get());
//        }
        protected ASetting(ScarletSettings.FileValued<T> setting, C render)
        {
            this.setting = setting;
            this.render = render;
            this.update();
            ScarletUI.this.ssettings.add(this);
            setting.listeners.register("ui", 0, true, this::onMaybeChange);
        }
        final ScarletSettings.FileValued<T> setting;
        final C render;
        @Override
        public final String id()
        {
            return this.setting.id;
        }
        @Override
        public final String name()
        {
            return this.setting.name;
        }
        @Override
        public final T get()
        {
            return this.setting.get();
        }
        @Override
        public final T getDefault()
        {
            return this.setting.ifNull.get();
        }
        @Override
        public final void set(T value)
        {
            this.setting.set(value, "ui");
            this.update();
        }
        @Override
        public final Component render()
        {
            return this.render;
        }
        protected abstract void update();
        protected abstract void onMaybeChange(T previous, T next, boolean valid, String source);
    }

    private class StringSetting extends ASetting<String, JTextField>
    {
        StringSetting(ScarletSettings.FileValued<String> setting)
        {
            super(setting, new JTextField(32));
            this.background = this.render.getBackground();
            JPopupMenu cpm = new JPopupMenu();
            cpm.add("Paste").addActionListener($ -> {
                String cbc = MiscUtils.AWTToolkit.get();
                if (cbc != null)
                {
                    this.render.replaceSelection(cbc);
                    this.accept();
                }
            });
            this.render.setComponentPopupMenu(cpm);
            this.render.addActionListener($ -> {
                this.accept();
            });
            this.render.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e)
                {
                    StringSetting.this.accept();
                }
            });
            this.render.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void removeUpdate(DocumentEvent e)
                {
                    StringSetting.this.accept();
                }
                @Override
                public void insertUpdate(DocumentEvent e)
                {
                    StringSetting.this.accept();
                }
                @Override
                public void changedUpdate(DocumentEvent e)
                {
                    StringSetting.this.accept();
                }
            });
        }
        final Color background;
        void accept()
        {
            this.set(this.render.getText());
        }
        @Override
        protected void update()
        {
            if (Objects.equals(this.get(), this.render.getText()))
                return;
            this.render.setText(this.get());
        }
        @Override
        protected void onMaybeChange(String previous, String next, boolean valid, String source)
        {
            if ("ui".equals(source))
            {
                this.render.setBackground(valid ? this.background : MiscUtils.lerp(this.background, Color.PINK, 0.5F));
            }
            else if (valid)
            {
                this.render.setText(next);
            }
        }
    }

 /*
    class StringArr2Setting extends ASetting<String[], JPanel>
    {
        class EntryPanel extends JPanel
        {
            private static final long serialVersionUID = -1300111578131336387L;
            EntryPanel(String value)
            {
                super(new BorderLayout());
                this.button = new JButton("-");
                this.button.addActionListener($ ->
                {
                    StringArr2Setting.this.renderInner.remove(this);
                    StringArr2Setting.this.entries.remove(this);
                    StringArr2Setting.this.accept();
                });
                this.text = new JTextField(32);
                this.background = this.text.getBackground();
                JPopupMenu cpm = new JPopupMenu();
                cpm.add("Paste").addActionListener($ -> {
                    String cbc = MiscUtils.AWTToolkit.get();
                    if (cbc != null)
                    {
                        this.text.replaceSelection(cbc);
                        StringArr2Setting.this.accept();
                    }
                });
                this.text.setComponentPopupMenu(cpm);
                this.text.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e)
                    {
                        StringArr2Setting.this.accept();
                    }
                });
                this.text.setText(value);
                this.text.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void removeUpdate(DocumentEvent e)
                    {
                        StringArr2Setting.this.accept();
                    }
                    @Override
                    public void insertUpdate(DocumentEvent e)
                    {
                        StringArr2Setting.this.accept();
                    }
                    @Override
                    public void changedUpdate(DocumentEvent e)
                    {
                        StringArr2Setting.this.accept();
                    }
                });
                this.add(this.button, BorderLayout.WEST);
                this.add(this.text, BorderLayout.CENTER);
                List<EntryPanel> entries = StringArr2Setting.this.entries;
                GridBagConstraints constraints = new GridBagConstraints();
                this.gridy = entries.isEmpty() ? 0 : entries.get(entries.size() - 1).gridy + 1;
                constraints.gridx = 0;
                constraints.gridy = this.gridy;
                constraints.anchor = GridBagConstraints.WEST;
                StringArr2Setting.this.renderInner.add(this, constraints);
                entries.add(this);
            }
            final JButton button;
            final JTextField text;
            final Color background;
            final int gridy;
            boolean validateAndColor()
            {
                boolean valid = StringArr2Setting.this.validator.test(this.text.getText());
                this.text.setBackground(valid ? this.background : MiscUtils.lerp(this.background, Color.PINK, 0.5F));
                return valid;
            }
            String getStringValue()
            {
                return this.text.getText();
            }
        }
        StringArr2Setting(ScarletSettings.FileValued<String[]> setting)
        {
            super(setting, new JPanel(new BorderLayout()));
            JButton button = new JButton("+");
            button.addActionListener($ -> Swing.invokeLater(() -> new EntryPanel("")));
            JPanel panel = new JPanel(new GridBagLayout());
            this.renderInner = panel;
            JScrollPane scroll = new JScrollPane(panel);
            Dimension size = new Dimension(400, 100);
            scroll.setSize(size);
            scroll.setPreferredSize(size);
            scroll.setMaximumSize(size);
            scroll.setMinimumSize(size);
            this.entries = new ArrayList<>();
            this.render.add(scroll, BorderLayout.CENTER);
            this.render.add(button, BorderLayout.SOUTH);
        }
        final List<EntryPanel> entries;
        final JPanel renderInner;
        void accept()
        {
            String[] valuesValidated = this.entries.stream().filter(EntryPanel::validateAndColor).map(EntryPanel::getStringValue).toArray(String[]::new);
            this.valueFiltered = valuesValidated.length == this.value.length ? null : valuesValidated;
        }
        @Override
        protected void update()
        {
            if (this.entries == null)
                return;
            this.entries.clear();
            this.renderInner.removeAll();
            for (String value : this.value)
                new EntryPanel(value);
            this.accept();
            if (Objects.equals(String.valueOf(this.get()), this.render.getText()))
                return;
            this.render.setText(String.valueOf(this.get()));
        }
    }
//*/

    private class IntSetting extends ASetting<Integer, JTextField>
    {
        IntSetting(ScarletSettings.FileValued<Integer> setting)
        {
            super(setting, new JTextField(32));
            JPopupMenu cpm = new JPopupMenu();
            cpm.add("Paste").addActionListener($ -> Optional.ofNullable(MiscUtils.AWTToolkit.get()).ifPresent($$ -> {
                this.render.setText($$);
                this.accept();
            }));
            this.render.setComponentPopupMenu(cpm);
            this.render.addActionListener($ -> {
                this.accept();
            });
            this.render.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e)
                {
                    IntSetting.this.accept();
                }
            });
            this.render.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void removeUpdate(DocumentEvent e)
                {
                    IntSetting.this.accept();
                }
                @Override
                public void insertUpdate(DocumentEvent e)
                {
                    IntSetting.this.accept();
                }
                @Override
                public void changedUpdate(DocumentEvent e)
                {
                    IntSetting.this.accept();
                }
            });
        }
        Color bg_ok = this.render.getBackground(),
              bg_err = MiscUtils.lerp(this.bg_ok, Color.RED, 0.1F);
        void accept()
        {
            try
            {
                this.set(Integer.parseInt(this.render.getText()));
            }
            catch (Exception ex)
            {
            }
        }
        @Override
        protected void update()
        {
            if (Objects.equals(String.valueOf(this.get()), this.render.getText()))
                return;
            this.render.setText(String.valueOf(this.get()));
        }
        @Override
        protected void onMaybeChange(Integer previous, Integer next, boolean valid, String source)
        {
            if ("ui".equals(source))
            {
                this.render.setBackground(valid ? this.bg_ok : this.bg_err);
            }
            else if (valid)
            {
                this.render.setText(next.toString());
            }
        }
    }

    private class BoolSetting extends ASetting<Boolean, JCheckBox>
    {
        BoolSetting(ScarletSettings.FileValued<Boolean> setting)
        {
            super(setting, new JCheckBox(null, null, setting.get()));
            this.render.addActionListener($ -> {
                this.accept();
            });
        }
        void accept()
        {
            this.set(this.render.isSelected());
        }
        @Override
        protected void update()
        {
            if (Objects.equals(this.get(), this.render.isSelected()))
                return;
            this.render.setSelected(this.get());
        }
        @Override
        protected void onMaybeChange(Boolean previous, Boolean next, boolean valid, String source)
        {
            if ("ui".equals(source))
            {
                ; // noop
            }
            else if (valid)
            {
                this.render.setSelected(next.booleanValue());
            }
        }
    }

    private class EnumSetting<E extends Enum<E>> extends ASetting<E, JComboBox<E>>
    {
        EnumSetting(ScarletSettings.FileValued<E> setting)
        {
            super(setting, new JComboBox<>(setting.ifNull.get().getDeclaringClass().getEnumConstants()));
            this.render.setSelectedItem(setting.ifNull.get());
            this.render.addItemListener($ -> {
                if ($.getStateChange() == ItemEvent.SELECTED)
                {
                    this.accept();
                }
            });
            this.nameMap = new HashMap<>();
            for (E value : setting.ifNull.get().getDeclaringClass().getEnumConstants())
                this.nameMap.put(value.name(), value);
        }
        final Map<String, E> nameMap;
        void accept()
        {
            @SuppressWarnings("unchecked")
            E value = (E)this.render.getSelectedItem();
            this.set(value);
        }
        @Override
        protected void update()
        {
            if (Objects.equals(this.get(), this.render.getSelectedItem()))
                return;
            this.render.setSelectedItem(this.get());
        }
        @Override
        protected void onMaybeChange(E previous, E next, boolean valid, String source)
        {
            if ("ui".equals(source))
            {
                ; // noop
            }
            else if (valid)
            {
                this.render.setSelectedItem(next);
            }
        }
    }

    private class VoidSetting implements GUISetting<Void>
    {
        protected VoidSetting(ScarletSettings.FileValued<Void> setting, Runnable buttonPressed)
        {
            this.name = setting.id;
            this.render = new JButton(setting.name);
            this.render.addActionListener($ ->
            {
                try
                {
                    buttonPressed.run();
                }
                catch (Exception ex)
                {
                    LOG.error("Exception handling in runnable setting "+name, ex);
                }
            });
            ScarletUI.this.ssettings.add(this);
        }
        final String name;
        final JButton render;
        @Override
        public final String id()
        {
            return "";
        }
        @Override
        public final String name()
        {
            return this.name;
        }
        @Override
        public final Void get()
        {
            return null;
        }
        @Override
        public final Void getDefault()
        {
            return null;
        }
        @Override
        public final void set(Void value)
        {
        }
        @Override
        public final Component render()
        {
            return this.render;
        }
    }

}
