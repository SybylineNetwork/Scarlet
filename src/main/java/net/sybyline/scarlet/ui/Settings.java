package net.sybyline.scarlet.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.sybyline.scarlet.util.MiscUtils;

public interface Settings
{

    public static Settings graphical(String dataName, String displayName)
    {
        return new SettingsGUI(dataName, displayName);
    }

    public interface Graphical
    {
        public Component createGUI();
    }

    public Category rootCategory();
    public boolean isGraphical();
    public interface Setting
    {
        public String dataName();
        public String displayName();
        public boolean isDirty();
        public boolean pollDirty();
        public void markDirty();
        public void loadValue(JsonElement savedValue);
        public JsonElement storeValue();
    }
    public interface Category extends Setting
    {
        public Category subCategory(String dataName, String displayName);
        public StringField stringField(String dataName, String displayName, String defaultValue, Predicate<String> validator);
        public BoolCheckBox boolCheckBox(String dataName, String displayName, boolean defaultValue);
        public RunnableButton runnableButton(String dataName, String displayName, JsonElement defaultValue, String buttonText, Runnable buttonPressed);
    }
    public interface Value<V extends Value<V, T>, T> extends Setting
    {
        public default V self() { @SuppressWarnings("unchecked") V v = (V)this; return v; }
        public T getDefault();
        public T get();
        public V set(T newValue);
        public V listen(BiConsumer<T, T> onChange);
        public default V listen(Consumer<T> onChange) { return onChange != null ? this.listen((oldValue, newValue) -> onChange.accept(newValue)) : this.self(); }
    }
    public interface StringField extends Value<StringField, String>
    {
    }
    public interface BoolCheckBox extends Value<BoolCheckBox, Boolean>
    {
    }
    public interface RunnableButton extends Value<RunnableButton, JsonElement>
    {
    }

}

class SettingsGUI implements Settings
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/Settings/GUI");

    SettingsGUI(String dataName, String displayName)
    {
        this.rootCategory = new CategoryGUI(null, dataName, displayName);
    }

    final CategoryGUI rootCategory;

    @Override
    public Category rootCategory()
    {
        return this.rootCategory;
    }
    @Override
    public boolean isGraphical()
    {
        return true;
    }

    abstract class SettingGUI implements Setting, Graphical
    {
        SettingGUI(CategoryGUI parent, String dataName, String displayName)
        {
            this.parent = parent;
            this.dataName = dataName;
            this.displayName = displayName;
            this.dirty = false;
        }
        final CategoryGUI parent;
        final String dataName, displayName;
        boolean dirty;
        @Override
        public final String dataName()
        {
            return this.dataName;
        }
        @Override
        public final String displayName()
        {
            return this.displayName;
        }
        @Override
        public final boolean isDirty()
        {
            return this.dirty;
        }
        @Override
        public boolean pollDirty()
        {
            try
            {
                return this.dirty;
            }
            finally
            {
                this.dirty = false;
            }
        }
        @Override
        public final void markDirty()
        {
            this.dirty = true;
            if (this.parent != null)
                this.parent.markDirty();
        }
    }

    abstract class ValueGUI<V extends Value<V, T>, T> extends SettingGUI implements Value<V, T>
    {
        ValueGUI(CategoryGUI parent, String dataName, String displayName, T defaultValue)
        {
            super(parent, dataName, displayName);
            this.listeners = new ArrayList<>();
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }
        final List<BiConsumer<T, T>> listeners;
        final T defaultValue;
        T value;
        @Override
        public T getDefault()
        {
            return this.defaultValue;
        }
        @Override
        public T get()
        {
            return this.value;
        }
        @Override
        public V set(T newValue)
        {
            this.setImpl(newValue, true);
            return this.self();
        }
        @Override
        public V listen(BiConsumer<T, T> onChange)
        {
            if (onChange != null)
                this.listeners.add(onChange);
            return this.self();
        }
        void setImpl(T newValue, boolean updateUI)
        {
            T oldValue = this.value;
            this.value = newValue;
            if (!Objects.equals(oldValue, newValue))
                this.markDirty();
            for (BiConsumer<T, T> onChange : this.listeners)
                onChange.accept(oldValue, newValue);
            if (updateUI)
                this.updateGUI(oldValue, newValue);
        }
        abstract void updateGUI(T oldValue, T newValue);
    }

    class CategoryGUI extends SettingGUI implements Category
    {
        CategoryGUI(CategoryGUI parent, String dataName, String displayName)
        {
            super(parent, dataName, displayName);
            this.settings = new HashMap<>();
        }
        final Map<String, SettingGUI> settings;
        <GUI extends SettingGUI> GUI checkAdd(GUI value)
        {
            if ("__expanded__".equals(value.dataName))
                throw new IllegalArgumentException("Reserved setting id: __expanded__");
            if (this.settings.putIfAbsent(value.dataName, value) != null)
                throw new IllegalArgumentException("Duplicate setting id: "+value.dataName);
            return value;
        }
        @Override
        public void loadValue(JsonElement savedValue)
        {
            if (savedValue == null || !savedValue.isJsonObject())
                return;
            JsonObject value = savedValue.getAsJsonObject();
            for (String dataName : value.keySet())
            {
                SettingGUI setting = this.settings.get(dataName);
                if (setting != null)
                {
                    setting.loadValue(value.get(dataName));
                }
            }
            
        }
        @Override
        public JsonElement storeValue()
        {
            JsonObject value = new JsonObject();
            for (SettingGUI setting : this.settings.values())
            {
                value.add(setting.dataName, setting.storeValue());
            }
            if (this.collapsablePanel != null)
                this.expanded = this.collapsablePanel.isExpanded();
            
            return value;
        }
        @Override
        public boolean pollDirty()
        {
            this.settings.values().forEach(SettingGUI::pollDirty);
            return super.pollDirty();
        }
        @Override
        public Category subCategory(String dataName, String displayName)
        {
            return this.checkAdd(new CategoryGUI(this, dataName, displayName));
        }
        @Override
        public StringField stringField(String dataName, String displayName, String defaultValue, Predicate<String> validator)
        {
            return this.checkAdd(new StringFieldGUI(this, dataName, displayName, defaultValue, validator));
        }
        @Override
        public BoolCheckBox boolCheckBox(String dataName, String displayName, boolean defaultValue)
        {
            return this.checkAdd(new BoolCheckBoxGUI(this, dataName, displayName, defaultValue));
        }
        @Override
        public RunnableButton runnableButton(String dataName, String displayName, JsonElement defaultValue, String buttonText, Runnable buttonPressed)
        {
            return this.checkAdd(new RunnableButtonGUI(this, dataName, displayName, defaultValue, buttonText, buttonPressed));
        }
        boolean expanded = false;
        UICollapsablePanel collapsablePanel = null;
        @Override
        public Component createGUI()
        {
            if (this.collapsablePanel != null)
                this.expanded = this.collapsablePanel.isExpanded();
            this.collapsablePanel = new UICollapsablePanel();
            {
                GridBagConstraints gbc_header = new GridBagConstraints();
                this.collapsablePanel.getContentHeader().setLayout(new GridBagLayout());
                gbc_header.gridy = 0;
                gbc_header.insets.set(1, 1, 1, 1);
                gbc_header.weightx = 0.0D;
                gbc_header.gridwidth = 1;
                gbc_header.gridheight = 1;

                gbc_header.gridx = 0;
                gbc_header.anchor = GridBagConstraints.WEST;
                this.collapsablePanel.getContentHeader().add(new JLabel(this.displayName()), gbc_header);

                gbc_header.gridx = 1;
                gbc_header.anchor = GridBagConstraints.EAST;
                this.collapsablePanel.getContentHeader().add(new JLabel(""), gbc_header);
            }
            {
                GridBagConstraints gbc_body = new GridBagConstraints();
                this.collapsablePanel.getContentBody().setLayout(new GridBagLayout());
                gbc_body.gridx = 0;
                gbc_body.gridy = 0;
                gbc_body.insets.set(1, 1, 1, 1);
                gbc_body.weightx = 0.0D;
                gbc_body.weighty = 0.0D;
                gbc_body.gridwidth = 1;
                gbc_body.gridheight = 1;
                for (SettingGUI setting : this.settings.values())
                {
                    gbc_body.gridx = 0;
                    gbc_body.gridwidth = 1;
                    gbc_body.anchor = GridBagConstraints.EAST;
                    this.collapsablePanel.getContentBody().add(new JLabel(setting.displayName()), gbc_body);
                    
                    gbc_body.gridx = 1;
                    gbc_body.anchor = GridBagConstraints.WEST;
                    this.collapsablePanel.getContentBody().add(setting.createGUI(), gbc_body);

                    gbc_body.gridy++;
                }
                gbc_body.gridx = 0;
                gbc_body.gridwidth = GridBagConstraints.REMAINDER;
                gbc_body.gridwidth = GridBagConstraints.REMAINDER;
                gbc_body.weightx = 1.0D;
                gbc_body.weighty = 1.0D;
                gbc_body.anchor = GridBagConstraints.SOUTHEAST;
                this.collapsablePanel.getContentBody().add(new JLabel(""), gbc_body);
            }
            this.collapsablePanel.setExpanded(this.expanded);
            return this.collapsablePanel;
        }
    }

    class StringFieldGUI extends ValueGUI<StringField, String> implements StringField
    {
        StringFieldGUI(CategoryGUI parent, String dataName, String displayName, String defaultValue, Predicate<String> validator)
        {
            super(parent, dataName, displayName, defaultValue);
            this.validator = validator == null ? $ -> true : validator;
        }
        final Predicate<String> validator;
        @Override
        public void loadValue(JsonElement savedValue)
        {
            if (savedValue == null || !savedValue.isJsonPrimitive() && !savedValue.getAsJsonPrimitive().isString())
                return;
            this.setImpl(savedValue.getAsString(), true);
        }
        @Override
        public JsonElement storeValue()
        {
            return new JsonPrimitive(this.value);
        }
        JTextField textField;
        @Override
        void updateGUI(String oldValue, String newValue)
        {
            if (this.textField != null)
                this.textField.setText(newValue);
        }
        @Override
        public Component createGUI()
        {
            this.textField = new JTextField();
            this.textField.setColumns(24);
            this.textField.setText(this.value);
            JPopupMenu cpm = new JPopupMenu();
            cpm.add("Paste").addActionListener($ -> {
                String cbc = MiscUtils.AWTToolkit.get();
                if (cbc != null)
                {
                    this.textField.replaceSelection(cbc);
                }
            });
            this.textField.setComponentPopupMenu(cpm);
            this.textField.addActionListener($ ->
            {
                StringFieldGUI.this.accept();
            });
            this.textField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e)
                {
                    StringFieldGUI.this.accept();
                }
            });
            this.textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void removeUpdate(DocumentEvent e)
                {
                    StringFieldGUI.this.accept();
                }
                @Override
                public void insertUpdate(DocumentEvent e)
                {
                    StringFieldGUI.this.accept();
                }
                @Override
                public void changedUpdate(DocumentEvent e)
                {
                    StringFieldGUI.this.accept();
                }
            });
            this.background = null;
            return this.textField;
        }
        Color background;
        void accept()
        {
            if (this.textField != null)
            {
                String value = this.textField.getText();
                boolean ret = this.validator.test(value);
                if (this.background == null)
                    this.background = this.textField.getBackground();
                this.textField.setBackground(ret ? this.background : MiscUtils.lerp(this.background, Color.PINK, 0.5F));
                if (ret)
                    this.setImpl(value, false);
            }
        }
    }

    class BoolCheckBoxGUI extends ValueGUI<BoolCheckBox, Boolean> implements BoolCheckBox
    {
        BoolCheckBoxGUI(CategoryGUI parent, String dataName, String displayName, boolean defaultValue)
        {
            super(parent, dataName, displayName, defaultValue);
        }
        @Override
        public void loadValue(JsonElement savedValue)
        {
            if (savedValue == null || !savedValue.isJsonPrimitive() && !savedValue.getAsJsonPrimitive().isBoolean())
                return;
            this.setImpl(savedValue.getAsBoolean(), true);
        }
        @Override
        public JsonElement storeValue()
        {
            return new JsonPrimitive(this.value);
        }
        JCheckBox checkBox;
        @Override
        void updateGUI(Boolean oldValue, Boolean newValue)
        {
            if (this.checkBox != null)
                this.checkBox.setEnabled(newValue);
        }
        @Override
        public Component createGUI()
        {
            this.checkBox = new JCheckBox(null, null, this.value);
            this.checkBox.addActionListener($ -> this.setImpl(this.checkBox.isSelected(), false));
            return this.checkBox;
        }
    }

    class RunnableButtonGUI extends ValueGUI<RunnableButton, JsonElement> implements RunnableButton
    {
        RunnableButtonGUI(CategoryGUI parent, String dataName, String displayName, JsonElement defaultValue, String buttonText, Runnable buttonPressed)
        {
            super(parent, dataName, displayName, defaultValue);
            this.buttonText = buttonText;
            this.buttonPressed = buttonPressed;
        }
        final String buttonText;
        final Runnable buttonPressed;
        @Override
        public void loadValue(JsonElement savedValue)
        {
            if (savedValue == null)
                return;
            this.setImpl(savedValue, true);
        }
        @Override
        public JsonElement storeValue()
        {
            return this.value;
        }
        JButton button;
        @Override
        void updateGUI(JsonElement oldValue, JsonElement newValue)
        {
            if (this.button != null)
                ;
        }
        @Override
        public Component createGUI()
        {
            this.button = new JButton(this.buttonText);
            this.button.addActionListener($ ->
            {
                try
                {
                    this.buttonPressed.run();
                }
                catch (Exception ex)
                {
                    LOG.error("Exception handling in runnable setting "+this.displayName(), ex);
                }
            });
            return this.button;
        }
    }

}
