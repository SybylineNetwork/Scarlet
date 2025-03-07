package net.sybyline.scarlet;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import io.github.vrchatapi.model.GroupMemberStatus;
import io.github.vrchatapi.model.User;

import net.sybyline.scarlet.util.MiscUtils;
import net.sybyline.scarlet.util.PropsTable;
import net.sybyline.scarlet.util.VRChatWebPageURLs;

public class ScarletUI implements AutoCloseable
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/UI");
    static final DateTimeFormatter LTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static
    {
        try
        {
//            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.setLookAndFeel(new FlatDarkLaf());
        }
        catch (Exception ex)
        {
            LOG.error("Exception setting system look and feel", ex);
        }
    }

    public interface Setting<T>
    {
        String id();
        String name();
        Component render();
        T get();
        T getDefault();
        void set(T value);
    }
//    public interface SettingCategory
//    {
//        String id();
//        String name();
//        Component render();
//        Setting<String> settingString(String id, String name, String defaultValue);
//        Setting<Integer> settingInt(String id, String name, int defaultValue, int min, int max);
//        Setting<Boolean> settingBool(String id, String name, boolean defaultValue);
//    }
    interface SerializableSetting<T> extends Setting<T>
    {
        boolean pollDirty();
        void markDirty();
        JsonElement serialize();
        void deserialize(JsonElement element);
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
        
        this.initUI();
    }

    public synchronized void playerJoin(String id, String name, LocalDateTime joined, String advisory, Color text_color, int priority, boolean isRejoinFromPrev)
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
            player.text_color = text_color;
            player.priority = priority;
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
            player.text_color = text_color;
            player.priority = priority;
            this.connectedPlayers.put(id, player);
            this.propstable.addEntry(player);
        }
        this.propstable.sortEntries(COMPARE);
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
        this.propstable.sortEntries(COMPARE);
    }

    public synchronized void clearInstance()
    {
        this.connectedPlayers.clear();
        this.propstable.clearEntries();
    }

//    public synchronized SettingCategory settingCategory(String id, String name)
//    {
//        try
//        {
//            return new SettingCategoryImpl(id, name);
//        }
//        finally
//        {
//            this.readdSettingUI();
//        }
//    }

    public synchronized Setting<String> settingString(String id, String name, String defaultValue)
    {
        return this.settingString(id, name, defaultValue, (Predicate<String>)null);
    }
    public synchronized Setting<String> settingString(String id, String name, String defaultValue, String regex)
    {
        return this.settingString(id, name, defaultValue, regex == null ? null : Pattern.compile(regex).asPredicate());
    }
    public synchronized Setting<String> settingString(String id, String name, String defaultValue, Predicate<String> validator)
    {
        try
        {
            return new StringSetting(id, name, defaultValue, validator);
        }
        finally
        {
            this.readdSettingUI();
        }
    }

    public synchronized Setting<Integer> settingInt(String id, String name, int defaultValue, int min, int max)
    {
        try
        {
            return new IntSetting(id, name, defaultValue, min, max);
        }
        finally
        {
            this.readdSettingUI();
        }
    }

    public synchronized Setting<Boolean> settingBool(String id, String name, boolean defaultValue)
    {
        try
        {
            return new BoolSetting(id, name, defaultValue);
        }
        finally
        {
            this.readdSettingUI();
        }
    }

    public synchronized Setting<Void> settingVoid(String name, String buttonText, Runnable buttonPressed)
    {
        try
        {
            return new VoidSetting(name, buttonText, buttonPressed);
        }
        finally
        {
            this.readdSettingUI();
        }
    }

    final Scarlet scarlet;
    final JFrame jframe;
    final JTabbedPane jtabs;
    final PropsTable<ConnectedPlayer> propstable;
    final JPanel jpanel_settings;
    final JLabel jlabel_lastSavedAt;
    final List<SerializableSetting<?>> ssettings;
    boolean propstableColumsDirty;
    final Map<String, ConnectedPlayer> connectedPlayers;
    
    class ConnectedPlayer
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
                MiscUtils.AWTDesktop.browse(URI.create("https://vrchat.com/home/user/"+ConnectedPlayer.this.id));
            }
        };
        Action ban = new AbstractAction("Ban") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                ScarletUI.this.scarlet.execModal.execute(() ->
                {
                    if (JOptionPane.showConfirmDialog(ScarletUI.this.jframe, "Are you sure you want to ban "+ConnectedPlayer.this.name+"?", "Confirm ban", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
                    {
                        ScarletUI.this.tryBan(ConnectedPlayer.this.id, ConnectedPlayer.this.name);
                    }
                });
            }
        };
        Action unban = new AbstractAction("Unban") {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e)
            {
                ScarletUI.this.scarlet.execModal.execute(() ->
                {
                    if (JOptionPane.showConfirmDialog(ScarletUI.this.jframe, "Are you sure you want to unban "+ConnectedPlayer.this.name+"?", "Confirm unban", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
                    {
                        ScarletUI.this.tryUnban(ConnectedPlayer.this.id, ConnectedPlayer.this.name);
                    }
                });
            }
        };
        Color text_color;
        int priority;
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
            this.propstable.addProperty("Ban", true, true, Action.class, $ -> $.ban);
            this.propstable.addProperty("Unban", true, false, Action.class, $ -> $.unban);
            
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
        }
        this.scarlet.exec.scheduleAtFixedRate(this::saveIfDirty, 10_000L, 10_000L, TimeUnit.MILLISECONDS);
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

    void saveIfDirty()
    {
        this.saveSettings(false);
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

    protected void tryBan(String id, String name)
    {
        String ownerId = this.scarlet.vrc.groupOwnerId;
        
        if (!this.scarlet.staffMode)
        if (ownerId == null)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Internal error", "Group owner id missing", Color.PINK);
            return;
        }
        
        GroupMemberStatus status = this.scarlet.vrc.getMembership(id);
        
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

    protected void tryUnban(String id, String name)
    {
        String ownerId = this.scarlet.vrc.groupOwnerId;

        if (!this.scarlet.staffMode)
        if (ownerId == null)
        {
            this.scarlet.splash.queueFeedbackPopup(this.jframe, 2_000L, "Internal error", "Group owner id missing", Color.PINK);
            return;
        }
        
        GroupMemberStatus status = this.scarlet.vrc.getMembership(id);
        
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
    public void close() throws Exception
    {
        this.saveSettings(false);
        this.saveInstanceColumns();
        this.jframe.dispose();
    }

    void readdSettingUI()
    {
        this.jpanel_settings.removeAll();
        GridBagConstraints gbc_settings = new GridBagConstraints();
        gbc_settings.gridx = 0;
        gbc_settings.gridy = 0;
        gbc_settings.insets = new Insets(2, 2, 2, 2);
        for (SerializableSetting<?> ssetting : this.ssettings)
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

    void loadSettings()
    {
        for (SerializableSetting<?> ssetting : this.ssettings)
        {
            JsonElement element = this.scarlet.settings.getElement(ssetting.id());
            if (element != null && !element.isJsonNull())
            {
                ssetting.deserialize(element);
            }
        }
        if (this.scarlet.showUiDuringLoad.get())
        {
            this.jframe.setVisible(true);
        }
    }

    void saveSettings(boolean showTimeSaved)
    {
        int saved = 0;
        for (SerializableSetting<?> ssetting : this.ssettings)
        {
            if (!ssetting.id().isEmpty())
            {
                this.scarlet.settings.setElementNoSave(ssetting.id(), ssetting.serialize());
                if (ssetting.pollDirty())
                {
                    saved++;
                }
            }
        }
        if (saved > 0)
        {
            this.scarlet.settings.saveJson();
        }
        if (showTimeSaved)
        {
            String savedAtText = "Saved settings: " + DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.now());
            this.jlabel_lastSavedAt.setText(savedAtText);
            this.scarlet.exec.schedule(() -> {
                if (Objects.equals(savedAtText, this.jlabel_lastSavedAt.getText()))
                    this.jlabel_lastSavedAt.setText("");
            }, 5_000L, TimeUnit.MILLISECONDS);
        }
    }

//    class SettingCategoryImpl implements SettingCategory, SerializableSetting<JsonObject>
//    {
//        @Override
//        public JsonObject get()
//        {
//            return null;
//        }
//        @Override
//        public JsonObject getDefault()
//        {
//            return null;
//        }
//        @Override
//        public void set(JsonObject value)
//        {
//        }
//        @Override
//        public boolean pollDirty()
//        {
//            return false;
//        }
//        @Override
//        public void markDirty()
//        {
//        }
//        @Override
//        public JsonElement serialize()
//        {
//            return null;
//        }
//        @Override
//        public void deserialize(JsonElement element)
//        {
//        }
//        @Override
//        public String id()
//        {
//            return null;
//        }
//        @Override
//        public String name()
//        {
//            return null;
//        }
//        @Override
//        public Component render()
//        {
//            return null;
//        }
//        @Override
//        public Setting<String> settingString(String id, String name, String defaultValue)
//        {
//            return null;
//        }
//        @Override
//        public Setting<Integer> settingInt(String id, String name, int defaultValue, int min, int max)
//        {
//            return null;
//        }
//        @Override
//        public Setting<Boolean> settingBool(String id, String name, boolean defaultValue)
//        {
//            return null;
//        }
//    }

    abstract class ASetting<T, C extends Component> implements SerializableSetting<T>
    {
        protected ASetting(String id, String name, T defaultValue, Supplier<C> render)
        {
            this(id, name, defaultValue, render.get());
        }
        protected ASetting(String id, String name, T defaultValue, C render)
        {
            this.id = id;
            this.name = name;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.render = render;
            this.dirty = new AtomicBoolean(false);
            this.update();
            ScarletUI.this.ssettings.add(this);
        }
        final String id, name;
        final T defaultValue;
        final C render;
        final AtomicBoolean dirty;
        T value;
        @Override
        public final String id()
        {
            return this.id;
        }
        @Override
        public final String name()
        {
            return this.name;
        }
        @Override
        public final T get()
        {
            return this.value;
        }
        @Override
        public final T getDefault()
        {
            return this.defaultValue;
        }
        @Override
        public final void set(T value)
        {
            this.value = value != null ? value : this.defaultValue;
            this.update();
            this.markDirty();
        }
        @Override
        public final Component render()
        {
            return this.render;
        }
        @Override
        public final boolean pollDirty()
        {
            return this.dirty.getAndSet(false);
        }
        @Override
        public final void markDirty()
        {
            this.dirty.set(true);
        }
        protected abstract void update();
    }

    class StringSetting extends ASetting<String, JTextField>
    {
        StringSetting(String id, String name, String defaultValue, Predicate<String> validator)
        {
            super(id, name, defaultValue, new JTextField(32));
            this.validator = validator == null ? $ -> true : validator;
            this.background = this.render.getBackground();
            JPopupMenu cpm = new JPopupMenu();
            cpm.add("Paste").addActionListener($ -> {
                String cbc = MiscUtils.AWTToolkit.get();
                if (cbc != null)
                {
                    this.render.setText(cbc);
                    this.accept();
                    this.markDirty();
                }
            });
            this.render.setComponentPopupMenu(cpm);
            this.render.addActionListener($ -> {
                this.accept();
                this.markDirty();
            });
            this.render.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e)
                {
                    StringSetting.this.accept();
                    StringSetting.this.markDirty();
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
        final Predicate<String> validator;
        final Color background;
        @Override
        public JsonElement serialize()
        {
            return new JsonPrimitive(this.value);
        }
        @Override
        public void deserialize(JsonElement element)
        {
            if (!element.isJsonPrimitive())
                return;
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isString())
                return;
            this.set(primitive.getAsString());
        }
        @Override
        protected void update()
        {
            if (Objects.equals(this.value, this.render.getText()))
                return;
            this.render.setText(this.value);
            this.testValid(this.value);
        }
        void accept()
        {
            String value = this.render.getText();
            if (this.testValid(value))
            {
                this.value = value;
            }
        }
        boolean testValid(String value)
        {
            boolean ret = this.validator.test(value);
            this.render.setBackground(ret ? this.background : MiscUtils.lerp(this.background, Color.PINK, 0.5F));
            return ret;
        }
    }

    class IntSetting extends ASetting<Integer, JTextField>
    {
        IntSetting(String id, String name, int defaultValue, int min, int max)
        {
            super(id, name, defaultValue, new JTextField(32));
            this.min = min;
            this.max = max;
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
        final int min, max;
        @Override
        public JsonElement serialize()
        {
            return new JsonPrimitive(this.value);
        }
        @Override
        public void deserialize(JsonElement element)
        {
            if (!element.isJsonPrimitive())
                return;
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isNumber())
                return;
            this.set(primitive.getAsInt());
        }
        @Override
        protected void update()
        {
            this.value = Math.max(this.min, Math.min(this.value, this.max));
            this.render.setText(String.valueOf(this.value));
        }
        Color bg_ok = this.render.getBackground(),
              bg_err = MiscUtils.lerp(this.bg_ok, Color.RED, 0.1F);
        void accept()
        {
            Integer value;
            try
            {
                value = Integer.parseInt(this.render.getText());
            }
            catch (Exception ex)
            {
                value = null;
            }
            if (value != null && value >= this.min && value <= this.max)
            {
                this.render.setBackground(this.bg_ok);
                this.value = value;
                this.markDirty();
            }
            else
            {
                this.render.setBackground(this.bg_err);
            }
        }
    }

    class BoolSetting extends ASetting<Boolean, JCheckBox>
    {
        BoolSetting(String id, String name, boolean defaultValue)
        {
            super(id, name, defaultValue, new JCheckBox(null, null, defaultValue));
            this.render.addActionListener($ -> {
                this.accept();
                this.markDirty();
            });
        }
        @Override
        public JsonElement serialize()
        {
            return new JsonPrimitive(this.value);
        }
        @Override
        public void deserialize(JsonElement element)
        {
            if (!element.isJsonPrimitive())
                return;
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isBoolean())
                return;
            this.set(primitive.getAsBoolean());
        }
        @Override
        protected void update()
        {
            this.render.setSelected(this.value);
        }
        void accept()
        {
            this.value = this.render.isSelected();
        }
    }

    class VoidSetting implements SerializableSetting<Void>
    {
        protected VoidSetting(String name, String buttonText, Runnable buttonPressed)
        {
            this.name = name;
            this.render = new JButton(buttonText);
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
        @Override
        public final boolean pollDirty()
        {
            return false;
        }
        @Override
        public final void markDirty()
        {
        }
        @Override
        public JsonElement serialize()
        {
            return null;
        }
        @Override
        public void deserialize(JsonElement element)
        {
        }
    }

//    class ObjSetting<T> extends ASetting<T, JButton>
//    {
//        ObjSetting(String id, String name, T defaultValue, Class<T> type)
//        {
//            super(id, name, defaultValue, new JButton("Edit"));
//            this.type = type;
//            this.render.addActionListener($ -> this.edit());
//        }
//        final Class<T> type;
//        @Override
//        public JsonElement serialize()
//        {
//            return Scarlet.GSON_PRETTY.toJsonTree(this.value, this.type);
//        }
//        @Override
//        public void deserialize(JsonElement element)
//        {
//            this.set(Scarlet.GSON_PRETTY.fromJson(element, this.type));
//        }
//        @Override
//        protected void update()
//        {
//        }
//        void edit()
//        {
//            ScarletUI.this.scarlet.execModal.execute(() -> {
//                
//            });
//        }
//    }

}
