package net.sybyline.scarlet;

import java.awt.GraphicsEnvironment;
import java.io.Console;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ScarletSettings
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Settings");

    public ScarletSettings(File settingsFile)
    {
        this.settingsFile = settingsFile;
        this.preferences = Preferences.userNodeForPackage(Scarlet.class);
        this.json = null;
        this.lastAuditQuery = null;
        this.lastAuthRefresh = null;
    }

    final File settingsFile;
    final Preferences preferences;
    private JsonObject json;
    private OffsetDateTime lastAuditQuery, lastAuthRefresh;

    public synchronized OffsetDateTime getLastAuditQuery()
    {
        OffsetDateTime lastAuditQuery = this.lastAuditQuery;
        if (lastAuditQuery != null)
            return lastAuditQuery;
        String lastAuditQueryString = this.preferences.get("lastAuditQuery", null);
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

    public synchronized OffsetDateTime getLastAuthRefresh()
    {
        OffsetDateTime lastAuthRefresh = this.lastAuthRefresh;
        if (lastAuthRefresh != null)
            return lastAuthRefresh;
        String lastAuthRefreshString = this.preferences.get("lastAuthRefresh", null);
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
        return lastAuditQuery;
    }

    public synchronized void setLastAuthRefresh(OffsetDateTime lastAuthRefresh)
    {
        if (lastAuthRefresh == null)
            return;
        this.lastAuthRefresh = lastAuthRefresh;
        this.preferences.put("lastAuthRefresh", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastAuthRefresh));
    }

    synchronized JsonObject getJson()
    {
        JsonObject json = this.json;
        if (json != null)
            return json;
        try (FileReader fr = new FileReader(this.settingsFile))
        {
            json = Scarlet.GSON_PRETTY.fromJson(fr, JsonObject.class);
        }
        catch (Exception ex)
        {
            LOG.error("Exception loading settings", ex);
            json = new JsonObject();
        }
        this.json = json;
        return json;
    }

    synchronized void saveJson()
    {
        JsonObject json = this.json;
        if (json == null)
            return;
        if (!this.settingsFile.getParentFile().isDirectory())
            this.settingsFile.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(this.settingsFile))
        {
            Scarlet.GSON_PRETTY.toJson(json, JsonObject.class, fw);
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
            if (sensitive)
            {
                JPasswordField jpf = new JPasswordField(32);
                int res = JOptionPane.showConfirmDialog(null, jpf, display, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (res == JOptionPane.OK_OPTION)
                    return new String(jpf.getPassword());
            }
            else
            {
                JTextField jtf = new JTextField(32);
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
