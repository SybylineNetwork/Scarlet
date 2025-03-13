package net.sybyline.scarlet;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.Console;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import net.sybyline.scarlet.util.MiscUtils;

public class ScarletSettings
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Settings");

    public ScarletSettings(File settingsFile)
    {
        this.settingsFile = settingsFile;
        this.settingsFileLastModified = settingsFile.lastModified();
        this.hasVersionChangedSinceLastRun = null;
        this.globalPreferences = Preferences.userNodeForPackage(Scarlet.class);
        this.preferences = this.globalPreferences;
        this.json = null;
        this.lastRunVersion = null;
        this.lastAuditQuery = null;
        this.lastInstancesCheck = null;
        this.lastAuthRefresh = null;
        this.lastUpdateCheck = null;
        this.uiBounds = null;
    }

    public void setNamespace(String namespace)
    {
        this.preferences = this.globalPreferences.node(namespace);
    }

    final File settingsFile;
    final long settingsFileLastModified;
    Boolean hasVersionChangedSinceLastRun;
    final Preferences globalPreferences;
    Preferences preferences;
    private JsonObject json;
    private String lastRunVersion;
    private OffsetDateTime lastRunTime, lastAuditQuery, lastInstancesCheck, lastAuthRefresh, lastUpdateCheck;
    private Rectangle uiBounds;

    public boolean checkHasVersionChangedSinceLastRun()
    {
        Boolean hasVersionChangedSinceLastRun = this.hasVersionChangedSinceLastRun;
        if (hasVersionChangedSinceLastRun != null)
            return hasVersionChangedSinceLastRun.booleanValue();
        OffsetDateTime lastRunTime = this.getLastRunTime();
        if (lastRunTime == null)
        {
            this.hasVersionChangedSinceLastRun = Boolean.TRUE;
            return true;
        }
        if (this.settingsFileLastModified > lastRunTime.toInstant().plusMillis(1_000L).toEpochMilli())
        {
            this.hasVersionChangedSinceLastRun = Boolean.TRUE;
            return true;
        }
        if (!Objects.equals(Scarlet.VERSION, this.getLastRunVersion()))
        {
            this.hasVersionChangedSinceLastRun = Boolean.TRUE;
            return true;
        }
        this.hasVersionChangedSinceLastRun = Boolean.FALSE;
        return false;
    }

    public void updateRunVersionAndTime()
    {
        this.setLastRunVersion(Scarlet.VERSION);
        this.setLastRunTime(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public synchronized String getLastRunVersion()
    {
        String lastRunVersion = this.lastRunVersion;
        if (lastRunVersion != null)
            return lastRunVersion;
        lastRunVersion = this.preferences.get("lastRunVersion", null);
        if (lastRunVersion != null)
            return lastRunVersion;
        lastRunVersion = this.globalPreferences.get("lastRunVersion", null);
        if (lastRunVersion != null)
        {
            this.lastRunVersion = lastRunVersion;
            this.preferences.put("lastRunVersion", lastRunVersion);
        }
        return lastRunVersion;
    }

    public synchronized void setLastRunVersion(String lastRunVersion)
    {
        if (lastRunVersion == null)
            return;
        this.lastRunVersion = lastRunVersion;
        this.preferences.put("lastRunVersion", lastRunVersion);
    }

    public synchronized OffsetDateTime getLastRunTime()
    {
        OffsetDateTime lastRunTime = this.lastRunTime;
        if (lastRunTime != null)
            return lastRunTime;
        String lastRunTimeString = this.preferences.get("lastRunTime", null);
        if (lastRunTimeString == null) lastRunTimeString = this.globalPreferences.get("lastRunTime", null);
        if (lastRunTimeString != null) try
        {
            lastRunTime = OffsetDateTime.parse(lastRunTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            this.lastRunTime = lastRunTime;
            this.preferences.put("lastRunTime", lastRunTimeString);
            return lastRunTime;
        }
        catch (RuntimeException ex)
        {
        }
        lastRunTime = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastRunTime = lastRunTime;
        this.preferences.put("lastRunTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastRunTime));
        return lastRunTime;
    }

    public synchronized void setLastRunTime(OffsetDateTime lastRunTime)
    {
        if (lastRunTime == null)
            return;
        this.lastRunTime = lastRunTime;
        this.preferences.put("lastRunTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastRunTime));
    }

    public synchronized OffsetDateTime getLastAuditQuery()
    {
        OffsetDateTime lastAuditQuery = this.lastAuditQuery;
        if (lastAuditQuery != null)
            return lastAuditQuery;
        String lastAuditQueryString = this.preferences.get("lastAuditQuery", null);
        if (lastAuditQueryString == null) lastAuditQueryString = this.globalPreferences.get("lastAuditQuery", null);
        if (lastAuditQueryString != null) try
        {
            lastAuditQuery = OffsetDateTime.parse(lastAuditQueryString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            this.lastAuditQuery = lastAuditQuery;
            return lastAuditQuery;
        }
        catch (RuntimeException ex)
        {
        }
        lastAuditQuery = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastAuditQuery = lastAuditQuery;
        this.preferences.put("lastAuditQuery", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastAuditQuery));
        return lastAuditQuery;
    }

    public synchronized void setLastAuditQuery(OffsetDateTime lastAuditQuery)
    {
        if (lastAuditQuery == null)
            return;
        this.lastAuditQuery = lastAuditQuery;
        this.preferences.put("lastAuditQuery", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastAuditQuery));
    }

    public synchronized OffsetDateTime getLastInstancesCheck()
    {
        OffsetDateTime lastInstancesCheck = this.lastInstancesCheck;
        if (lastInstancesCheck != null)
            return lastInstancesCheck;
        String lastInstancesCheckString = this.preferences.get("lastInstancesCheck", null);
        if (lastInstancesCheckString == null) lastInstancesCheckString = this.globalPreferences.get("lastInstancesCheck", null);
        if (lastInstancesCheckString != null) try
        {
            lastInstancesCheck = OffsetDateTime.parse(lastInstancesCheckString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            this.lastInstancesCheck = lastInstancesCheck;
            return lastInstancesCheck;
        }
        catch (RuntimeException ex)
        {
        }
        lastInstancesCheck = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastInstancesCheck = lastInstancesCheck;
        this.preferences.put("lastInstancesCheck", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastInstancesCheck));
        return lastInstancesCheck;
    }

    public synchronized void setLastInstancesCheck(OffsetDateTime lastInstancesCheck)
    {
        if (lastInstancesCheck == null)
            return;
        this.lastInstancesCheck = lastInstancesCheck;
        this.preferences.put("lastInstancesCheck", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastInstancesCheck));
    }

    public synchronized OffsetDateTime getLastAuthRefresh()
    {
        OffsetDateTime lastAuthRefresh = this.lastAuthRefresh;
        if (lastAuthRefresh != null)
            return lastAuthRefresh;
        String lastAuthRefreshString = this.preferences.get("lastAuthRefresh", null);
        if (lastAuthRefreshString == null) lastAuthRefreshString = this.globalPreferences.get("lastAuthRefresh", null);
        if (lastAuthRefreshString != null) try
        {
            lastAuthRefresh = OffsetDateTime.parse(lastAuthRefreshString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            this.lastAuthRefresh = lastAuthRefresh;
            return lastAuthRefresh;
        }
        catch (RuntimeException ex)
        {
        }
        lastAuthRefresh = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastAuthRefresh = lastAuthRefresh;
        this.preferences.put("lastAuthRefresh", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastAuthRefresh));
        return lastAuthRefresh;
    }

    public synchronized void setLastAuthRefresh(OffsetDateTime lastAuthRefresh)
    {
        if (lastAuthRefresh == null)
            return;
        this.lastAuthRefresh = lastAuthRefresh;
        this.preferences.put("lastAuthRefresh", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastAuthRefresh));
    }

    public synchronized OffsetDateTime getLastUpdateCheck()
    {
        OffsetDateTime lastUpdateCheck = this.lastUpdateCheck;
        if (lastUpdateCheck != null)
            return lastUpdateCheck;
        String lastUpdateCheckString = this.preferences.get("lastUpdateCheck", null);
        if (lastUpdateCheckString == null) lastUpdateCheckString = this.globalPreferences.get("lastUpdateCheck", null);
        if (lastUpdateCheckString != null) try
        {
            lastUpdateCheck = OffsetDateTime.parse(lastUpdateCheckString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            this.lastUpdateCheck = lastUpdateCheck;
            return lastUpdateCheck;
        }
        catch (RuntimeException ex)
        {
        }
        lastUpdateCheck = OffsetDateTime.now(ZoneOffset.UTC);
        this.lastUpdateCheck = lastUpdateCheck;
        this.preferences.put("lastUpdateCheck", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastUpdateCheck));
        return lastUpdateCheck;
    }

    public synchronized void setLastUpdateCheck(OffsetDateTime lastUpdateCheck)
    {
        if (lastUpdateCheck == null)
            return;
        this.lastUpdateCheck = lastUpdateCheck;
        this.preferences.put("lastUpdateCheck", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastUpdateCheck));
    }

    public synchronized Rectangle getUIBounds()
    {
        Rectangle uiBounds = this.uiBounds;
        if (uiBounds != null)
            return uiBounds;
        String uiBoundsString = this.preferences.get("uiBounds", null);
        if (uiBoundsString == null) uiBoundsString = this.globalPreferences.get("uiBoundsString", null);
        if (uiBoundsString != null) try
        {
            String[] values = uiBoundsString.split(",");
            uiBounds = new Rectangle(
                Integer.parseInt(values[0]),
                Integer.parseInt(values[1]),
                Integer.parseInt(values[2]),
                Integer.parseInt(values[3]));
            this.uiBounds = uiBounds;
            return uiBounds;
        }
        catch (RuntimeException ex)
        {
        }
        return uiBounds;
    }

    public synchronized void setUIBounds(Rectangle uiBounds)
    {
        if (uiBounds == null)
            return;
        this.uiBounds = uiBounds;
        this.preferences.put("uiBounds", String.format("%d,%d,%d,%d", uiBounds.x, uiBounds.y, uiBounds.width, uiBounds.height));
    }

    synchronized JsonObject getJson()
    {
        JsonObject json = this.json;
        if (json != null)
            return json;
        if (this.settingsFile.exists())
        try (Reader r = MiscUtils.reader(this.settingsFile))
        {
            json = Scarlet.GSON_PRETTY.fromJson(r, JsonObject.class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading settings", ex);
            json = new JsonObject();
        }
        else
        {
            json = new JsonObject();
        }
        this.json = json;
        return json;
    }

    public synchronized void saveJson()
    {
        JsonObject json = this.json;
        if (json == null)
            return;
        if (!this.settingsFile.getParentFile().isDirectory())
            this.settingsFile.getParentFile().mkdirs();
        try (Writer w = MiscUtils.writer(this.settingsFile))
        {
            Scarlet.GSON_PRETTY.toJson(json, JsonObject.class, w);
        }
        catch (Exception ex)
        {
            LOG.error("Exception saving settings", ex);
        }
    }

    public synchronized String getString(String key)
    {
        JsonObject json = this.getJson();
        if (json.has(key))
        {
            JsonElement elem = json.get(key);
            if (!elem.isJsonNull())
                return elem.getAsString();
        }
        return System.getProperty("net.sybyline.scarlet.setting."+key);
    }
    public synchronized void setString(String key, String value)
    {
        JsonObject json = this.getJson();
        json.addProperty(key, value);
        this.saveJson();
    }

    public <T> T getObject(String key, TypeToken<T> type)
    {
        return this.getObject(key, type.getType());
    }
    public <T> T getObject(String key, Class<T> type)
    {
        return this.getObject(key, (Type)type);
    }
    public synchronized <T> T getObject(String key, Type type)
    {
        JsonObject json = this.getJson();
        if (json.has(key)) try
        {
            return Scarlet.GSON_PRETTY.fromJson(json.get(key), type);
        }
        catch (Exception ex)
        {
            LOG.error("Exception deserializing setting `"+key+"` of type `"+type+"`", ex);
        }
        return null;
    }
    public <T> void setObject(String key, TypeToken<T> type, T value)
    {
        this.setObject(key, type.getType(), value);
    }
    public <T> void setObject(String key, Class<T> type, T value)
    {
        this.setObject(key, (Type)type, value);
    }
    public synchronized <T> void setObject(String key, Type type, T value)
    {
        JsonObject json = this.getJson();
        JsonElement element;
        try
        {
            element = Scarlet.GSON_PRETTY.toJsonTree(value, type);
        }
        catch (Exception ex)
        {
            LOG.error("Exception serializing setting `"+key+"` of type `"+type+"`", ex);
            return;
        }
        json.add(key, element);
        this.saveJson();
    }
    public synchronized void setElementNoSave(String key, JsonElement element)
    {
        this.getJson().add(key, element);
    }
    public synchronized JsonElement getElement(String key)
    {
        return this.getJson().get(key);
    }

    public synchronized String getStringOrRequireInput(String key, String display, boolean sensitive)
    {
        String value = this.getString(key);
        if (value == null)
        {
            value = this.requireInput(display, sensitive);
            this.setString(key, value);
        }
        return value;
    }

    public String requireInput(String display, boolean sensitive)
    {
        if (!GraphicsEnvironment.isHeadless())
        {
            JPopupMenu cpm = new JPopupMenu();
            if (sensitive)
            {
                JPasswordField jpf = new JPasswordField(32);
                    cpm.add("Paste").addActionListener($ -> Optional.ofNullable(MiscUtils.AWTToolkit.get()).ifPresent(jpf::setText));
                    jpf.setComponentPopupMenu(cpm);
                int res = JOptionPane.showConfirmDialog(null, jpf, display, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (res == JOptionPane.OK_OPTION)
                    return new String(jpf.getPassword());
            }
            else
            {
                JTextField jtf = new JTextField(32);
                    cpm.add("Paste").addActionListener($ -> Optional.ofNullable(MiscUtils.AWTToolkit.get()).ifPresent(jtf::setText));
                    jtf.setComponentPopupMenu(cpm);
                int res = JOptionPane.showConfirmDialog(null, jtf, display, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (res == JOptionPane.OK_OPTION)
                    return jtf.getText();
            }
        }
        Console console = System.console();
        if (console != null)
            return sensitive
                ? new String(console.readPassword(display+": "))
                : console.readLine(display+": ");
        System.out.print(display+": ");
        @SuppressWarnings("resource")
        Scanner s = new Scanner(System.in);
        return s.nextLine();
    }

}
