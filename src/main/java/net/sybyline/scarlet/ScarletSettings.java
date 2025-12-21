package net.sybyline.scarlet;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.Console;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import java.util.function.Supplier;
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

import net.sybyline.scarlet.util.EncryptedPrefs;
import net.sybyline.scarlet.util.MiscUtils;

public class ScarletSettings
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Settings");
    private static final String globalPW = Optional.ofNullable(System.getenv("SCARLET_GLOBAL_PW")).orElseGet(()->System.getProperty("scarlet.global.pw", "ZaxzVNStRpG1DU9dLVE"));

    public ScarletSettings(File settingsFile)
    {
        this.settingsFile = settingsFile;
        this.settingsFileLastModified = settingsFile.lastModified();
        this.hasVersionChangedSinceLastRun = null;
        this.globalPreferences = Preferences.userNodeForPackage(Scarlet.class);
        this.globalEncrypted = new EncryptedPrefs(this.globalPreferences, globalPW);
        this.preferences = this.globalPreferences;
        this.encrypted = this.globalEncrypted;
        this.json = null;
        this.lastRunVersion = new RegistryString("lastRunVersion");
        this.lastRunTime = new RegistryOffsetDateTime("lastRunTime");
        this.lastAuditQuery = new RegistryOffsetDateTime("lastAuditQuery");
        this.lastInstancesCheck = new RegistryOffsetDateTime("lastInstancesCheck");
        this.lastAuthRefresh = new RegistryOffsetDateTime("lastAuthRefresh");
        this.lastUpdateCheck = new RegistryOffsetDateTime("lastUpdateCheck");
        this.nextPollAction = new RegistryOffsetDateTime("nextPollAction");
        this.nextModSummary = new RegistryOffsetDateTime("nextModSummary");
        this.nextOutstandingMod = new RegistryOffsetDateTime("nextOutstandingMod");
        this.lastInstanceJoined = new RegistryLocalDateTime("lastInstanceJoined");
        this.uiBounds = new RegistryRectangle("uiBounds");
        this.heuristicKickCount = new FileValued<>("heuristicKickCount", Integer.class, 3);
        this.heuristicPeriodDays = new FileValued<>("heuristicPeriodDays", Integer.class, 3);
        this.outstandingPeriodDays = new FileValued<>("outstandingPeriodDays", Integer.class, 3);
    }

    public void setNamespace(String namespace)
    {
        this.preferences = this.globalPreferences.node(namespace);
        this.encrypted = new EncryptedPrefs(this.preferences, globalPW);
    }

    final File settingsFile;
    final long settingsFileLastModified;
    Boolean hasVersionChangedSinceLastRun;
    final Preferences globalPreferences;
    final EncryptedPrefs globalEncrypted;
    Preferences preferences;
    EncryptedPrefs encrypted;
    private JsonObject json;
    public final RegistryString lastRunVersion;
    public final RegistryOffsetDateTime lastRunTime, lastAuditQuery, lastInstancesCheck, lastAuthRefresh, lastUpdateCheck, nextPollAction, nextModSummary, nextOutstandingMod;
    public final RegistryLocalDateTime lastInstanceJoined;
    public final RegistryRectangle uiBounds;
    public final FileValued<Integer> heuristicKickCount, heuristicPeriodDays, outstandingPeriodDays;

    public boolean checkHasVersionChangedSinceLastRun()
    {
        Boolean hasVersionChangedSinceLastRun = this.hasVersionChangedSinceLastRun;
        if (hasVersionChangedSinceLastRun != null)
            return hasVersionChangedSinceLastRun.booleanValue();
        OffsetDateTime lastRunTime = this.lastRunTime.getOrNull();
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
        if (!Objects.equals(Scarlet.VERSION, this.lastRunVersion.getOrNull()))
        {
            this.hasVersionChangedSinceLastRun = Boolean.TRUE;
            return true;
        }
        this.hasVersionChangedSinceLastRun = Boolean.FALSE;
        return false;
    }

    public void updateRunVersionAndTime()
    {
        this.lastRunVersion.set(Scarlet.VERSION);
        this.lastRunTime.set(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public class FileValued<T>
    {
        FileValued(String name, Class<T> type, T ifNull)
        {
            this(name, type, () -> ifNull);
        }
        FileValued(String name, Class<T> type, Supplier<T> ifNull)
        {
            this.name = name;
            this.type = type;
            this.ifNull = ifNull != null ? ifNull : () -> null;
            this.cached = null;
        }
        final String name;
        final Class<T> type;
        final Supplier<T> ifNull;
        T cached;
        public T getOrNull()
        {
            return this.get(false);
        }
        public T getOrSupply()
        {
            return this.get(true);
        }
        private T get(boolean orDefault)
        {
            T cached_ = this.cached;
            if (cached_ == null)
            {
                cached_ = ScarletSettings.this.getObject(this.name, this.type);
                this.cached = cached_;
            }
            if (cached_ == null && orDefault)
            {
                cached_ = this.ifNull.get();
                this.cached = cached_;
            }
            return cached_;
        }
        public void set(T value_)
        {
            if (value_ == null)
                return;
            this.cached = value_;
            ScarletSettings.this.setObject(this.name, this.type, value_);
        }
    }

    public class RegistryStringValued<T>
    {
        RegistryStringValued(String name, Supplier<T> ifNull, Function<String, T> parse, Function<T, String> stringify)
        {
            this.name = name;
            this.ifNull = ifNull != null ? ifNull : () -> null;
            this.parse = parse;
            this.stringify = stringify;
            this.cached = null;
        }
        final String name;
        final Supplier<T> ifNull;
        final Function<String, T> parse;
        final Function<T, String> stringify;
        T cached;
        public T getOrNull()
        {
            return this.get(false);
        }
        public T getOrSupply()
        {
            return this.get(true);
        }
        private T get(boolean orNow)
        {
            T cached_ = this.cached;
            if (cached_ != null)
                return cached_;
            synchronized (ScarletSettings.this)
            {
                String string = this.read();
                if (string != null) try
                {
                    cached_ = this.parse.apply(string);
                    this.cached = cached_;
                    this.write(string);
                    return cached_;
                }
                catch (RuntimeException ex)
                {
                }
                if (orNow)
                {
                    cached_ = this.ifNull.get();
                    this.cached = cached_;
                    this.write(this.stringify.apply(cached_));
                }
                return cached_;
            }
        }
        protected String read()
        {
            String string = ScarletSettings.this.preferences.get(this.name, null);
            if (string == null) string = ScarletSettings.this.globalPreferences.get(this.name, null);
            return string;
        }
        protected void write(String string)
        {
            ScarletSettings.this.preferences.put(this.name, string);
        }
        public void set(T value_)
        {
            if (value_ == null)
                return;
            this.cached = value_;
            synchronized (ScarletSettings.this)
            {
                this.write(this.stringify.apply(value_));
            }
        }
    }
    public class RegistryStringValuedEncrypted<T> extends RegistryStringValued<T>
    {
        RegistryStringValuedEncrypted(String name, boolean globalOnly, Supplier<T> ifNull, Function<String, T> parse, Function<T, String> stringify)
        {
            super(name, ifNull, parse, stringify);
            this.globalOnly = globalOnly;
        }
        protected final boolean globalOnly;
        @Override
        protected String read()
        {
            String string = this.globalOnly ? null : ScarletSettings.this.encrypted.get(this.name);
            if (string == null) string = ScarletSettings.this.globalEncrypted.get(this.name);
            return string;
        }
        @Override
        protected void write(String string)
        {
            (this.globalOnly ? ScarletSettings.this.globalEncrypted : ScarletSettings.this.encrypted).put(this.name, string);
        }
    }

    public class RegistryOffsetDateTime extends RegistryStringValued<OffsetDateTime>
    {
        RegistryOffsetDateTime(String name)
        {
            super(name,
                () -> OffsetDateTime.now(ZoneOffset.UTC),
                string -> OffsetDateTime.parse(string, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME::format);
        }
    }

    public class RegistryLocalDateTime extends RegistryStringValued<LocalDateTime>
    {
        RegistryLocalDateTime(String name)
        {
            super(name,
                () -> LocalDateTime.now(),
                string -> LocalDateTime.parse(string, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME::format);
        }
    }

    public class RegistryRectangle extends RegistryStringValued<Rectangle>
    {
        RegistryRectangle(String name)
        {
            super(name, null,
                string ->
                {
                    String[] values = string.split(",");
                    return new Rectangle(
                        Integer.parseInt(values[0]),
                        Integer.parseInt(values[1]),
                        Integer.parseInt(values[2]),
                        Integer.parseInt(values[3]));
                },
                value -> String.format("%d,%d,%d,%d", value.x, value.y, value.width, value.height));
        }
    }

    public class RegistryString extends RegistryStringValued<String>
    {
        RegistryString(String name)
        {
            super(name, null, Function.identity(), Function.identity());
        }
    }
    public class RegistryStringEncrypted extends RegistryStringValuedEncrypted<String>
    {
        RegistryStringEncrypted(String name, boolean globalOnly)
        {
            super(name, globalOnly, null, Function.identity(), Function.identity());
        }
    }
    public class RegistryJsonEncrypted<T> extends RegistryStringValuedEncrypted<T>
    {
        RegistryJsonEncrypted(String name, boolean globalOnly, Supplier<T> ifNull, Type type)
        {
            super(name, globalOnly, ifNull, $->Scarlet.GSON.fromJson($, type), $->Scarlet.GSON.toJson($, type));
        }
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
