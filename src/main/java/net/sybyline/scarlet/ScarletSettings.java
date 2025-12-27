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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import net.sybyline.scarlet.util.ChangeListener;
import net.sybyline.scarlet.util.EncryptedPrefs;
import net.sybyline.scarlet.util.Maths;
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
        this.heuristicKickCount = new FileValuedIntRange("heuristicKickCount", "Heuristic Kick Count", 3, 1, 10);
        this.heuristicPeriodDays = new FileValuedIntRange("heuristicPeriodDays", "Heuristic Period (days)", 3, 1, 30);
        this.outstandingPeriodDays = new FileValuedIntRange("outstandingPeriodDays", "Outstanding Period (days)", 3, 1, 30);
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
    public final FileValuedIntRange heuristicKickCount, heuristicPeriodDays, outstandingPeriodDays;

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

    public interface FileValuedVisitor<T>
    {
        T visitBasic(FileValued<?> fileValued);
        T visitBoolean(FileValued<Boolean> fileValued, boolean defaultValue);
        T visitIntegerRange(FileValued<Integer> fileValued, int defaultValue, int minimum, int maximum);
        <E extends Enum<E>> T visitEnum(FileValued<E> fileValued, E defaultValue);
        T visitStringChoice(FileValued<String> fileValued, Supplier<Collection<String>> validValues);
        T visitStringPattern(FileValued<String> fileValued, String pattern, boolean lenient);
        T visitStringArrayPattern(FileValued<String[]> fileValued, String pattern, boolean lenient);
        T visitVoid(FileValued<Void> fileValued, Runnable task);
    }

    final Map<String, FileValued<?>> fileValuedSettings = Collections.synchronizedMap(new LinkedHashMap<>());
    public class FileValued<T>
    {
        FileValued(String id, String name, Class<T> type, UnaryOperator<T> validate, T ifNull)
        {
            this(id, name, type, validate, () -> ifNull);
        }
        FileValued(String id, String name, Class<T> type, UnaryOperator<T> validate, Supplier<T> ifNull)
        {
            if (ScarletSettings.this.fileValuedSettings.putIfAbsent(id, this) != null)
                throw new IllegalArgumentException("Duplicate setting: "+id);
            this.id = id;
            this.name = name;
            this.type = type;
            this.validate = validate != null ? validate : UnaryOperator.identity();
            this.ifNull = ifNull != null ? ifNull : () -> null;
            this.cached = null;
            this.listeners = ChangeListener.newListenerList();
        }
        final String id, name;
        final Class<T> type;
        final UnaryOperator<T> validate;
        final Supplier<T> ifNull;
        T cached;
        final ChangeListener.ListenerList<T> listeners;
        public String id()
        {
            return this.id;
        }
        public String name()
        {
            return this.name;
        }
        public Class<T> getType()
        {
            return this.type;
        }
        public T get()
        {
            T cached_ = this.cached;
            if (cached_ == null)
            {
                cached_ = ScarletSettings.this.getObject(this.id, this.type);
                this.cached = cached_;
            }
            if (cached_ == null)
            {
                cached_ = this.ifNull.get();
                this.cached = cached_;
            }
            return cached_;
        }
        public boolean set(T value_, String source)
        {
            T prev = this.cached;
            if (Objects.deepEquals(prev, value_))
                return true;
            if (value_ == null)
                value_ = this.ifNull.get();
            T validated = this.validate.apply(value_);
            boolean valid = validated != null;
            if (valid)
            {
                this.cached = value_;
                ScarletSettings.this.setObject(this.id, this.type, value_);
            }
            this.listeners.onMaybeChange(prev, valid ? validated : value_, valid, source);
            return valid;
        }
        protected <TT> TT visit(FileValuedVisitor<TT> visitor)
        {
            return visitor.visitBasic(this);
        }
    }
    public class FileValuedBoolean extends FileValued<Boolean>
    {
        public FileValuedBoolean(String id, String name, boolean defaultValue)
        {
            super(id, name, Boolean.class, null, defaultValue);
            this.defaultValue = defaultValue;
        }
        final boolean defaultValue;
        @Override
        protected <TT> TT visit(FileValuedVisitor<TT> visitor)
        {
            return visitor.visitBoolean(this, this.defaultValue);
        }
    }
    public class FileValuedIntRange extends FileValued<Integer>
    {
        public FileValuedIntRange(String id, String name, int defaultValue, int min, int max)
        {
            super(id, name, Integer.class, value -> Maths.clamp(value, min, max), defaultValue);
            this.def = defaultValue;
            this.min = min;
            this.max = max;
        }
        final int def, min, max;
        @Override
        protected <TT> TT visit(FileValuedVisitor<TT> visitor)
        {
            return visitor.visitIntegerRange(this, this.def, this.min, this.max);
        }
    }
    static UnaryOperator<String> patternOne(String pattern, boolean lenient)
    {
        if (pattern == null)
            return UnaryOperator.identity();
        Pattern p = Pattern.compile(pattern);
        if (lenient)
            return value ->
            {
                Matcher m = p.matcher(value);
                return m.find() ? m.group() : null;
            };
        return value -> p.matcher(value).matches() ? value : null;
    }
    public class FileValuedEnum<E extends Enum<E>> extends FileValued<E>
    {
        public FileValuedEnum(String id, String name, E defaultValue)
        {
            super(id, name, defaultValue.getDeclaringClass(), UnaryOperator.identity(), defaultValue);
            this.defaultValue = defaultValue;
        }
        final E defaultValue;
        @Override
        protected <TT> TT visit(FileValuedVisitor<TT> visitor)
        {
            return visitor.visitEnum(this, this.defaultValue);
        }
    }
    public class FileValuedStringChoice extends FileValued<String>
    {
        public FileValuedStringChoice(String id, String name, String defaultValue, Supplier<Collection<String>> validValues)
        {
            super(id, name, String.class, value -> validValues.get().contains(value) ? value : null, defaultValue);
            this.validValues = validValues;
        }
        final Supplier<Collection<String>> validValues;
        @Override
        protected <TT> TT visit(FileValuedVisitor<TT> visitor)
        {
            return visitor.visitStringChoice(this, this.validValues);
        }
    }
    public class FileValuedStringPattern extends FileValued<String>
    {
        public FileValuedStringPattern(String id, String name, String defaultValue, String pattern, boolean lenient)
        {
            super(id, name, String.class, patternOne(pattern, lenient), defaultValue);
            this.pattern = pattern;
            this.lenient = lenient;
        }
        final String pattern;
        final boolean lenient;
        @Override
        protected <TT> TT visit(FileValuedVisitor<TT> visitor)
        {
            return visitor.visitStringPattern(this, this.pattern, this.lenient);
        }
    }
    static UnaryOperator<String[]> patternAll(String pattern, boolean lenient)
    {
        if (pattern == null)
            return UnaryOperator.identity();
        Pattern p = Pattern.compile(pattern);
        if (lenient)
            return values ->
            {
                if (values != null)
                {
                    values = values.clone();
                    for (int i = 0; i < values.length; i++)
                    {
                        Matcher m = p.matcher(values[i]);
                        if (!m.find())
                            return null;
                        values[i] = m.group();
                    }
                }
                return values;
            };
        return values ->
        {
            if (values != null)
                for (String value : values)
                    if (!p.matcher(value).matches())
                        return null;
            return values;
        };
    }
    public class FileValuedStringArrayPattern extends FileValued<String[]>
    {
        public FileValuedStringArrayPattern(String id, String name, String[] defaultValue, String pattern, boolean lenient)
        {
            super(id, name, String[].class, patternAll(pattern, lenient), defaultValue);
            this.pattern = pattern;
            this.lenient = lenient;
        }
        final String pattern;
        final boolean lenient;
        @Override
        protected <TT> TT visit(FileValuedVisitor<TT> visitor)
        {
            return visitor.visitStringArrayPattern(this, this.pattern, this.lenient);
        }
    }
    public class FileValuedVoid extends FileValued<Void>
    {
        public FileValuedVoid(String id, String name, Runnable task)
        {
            super(id, name, Void.class, null, (Void)null);
            this.task = task;
        }
        final Runnable task;
        @Override
        protected <TT> TT visit(FileValuedVisitor<TT> visitor)
        {
            return visitor.visitVoid(this, this.task);
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
