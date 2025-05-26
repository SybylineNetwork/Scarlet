package net.sybyline.scarlet.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.Collator;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

import javax.swing.Action;
import javax.swing.DefaultRowSorter;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableStringConverter;

import com.google.gson.internal.Primitives;

public class PropsTable<E> extends JTable
{

    private static final long serialVersionUID = 2655466294113059041L;

    public PropsTable()
    {
        super();
        this.tableHeader.setReorderingAllowed(true);
        this.tableHeader.setResizingAllowed(true);
        JPopupMenu headerPopupMenu = new JPopupMenu();
        headerPopupMenu.add("Default order").addActionListener($ -> this.getRowSorter().setSortKeys(null));
        this.tableHeader.setComponentPopupMenu(headerPopupMenu);
        this.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        this.setAutoCreateColumnsFromModel(false);
        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e)
            {
                PropsTable.this.clearSelection();
            }
        });
        this.setRowSorter(new PropsRowSorter());
    }

    protected final JMenu columnSelectMenu = new JMenu();
    public JMenu getColumnSelectMenu()
    {
        return this.columnSelectMenu;
    }

    public class PropsInfo<P>
    {
        protected PropsInfo(String id, String name, boolean editable, boolean enabled, Class<P> type, Function<E, P> getFrom)
        {
            if (type.isEnum())
            {
                @SuppressWarnings({"unchecked", "rawtypes", "unused"})
                boolean editorExisted = PropsTable.this.createDefaultEnumEditor((Class)type, false);
            }
            this.id = id;
            this.name = name;
            this.editable = editable;
            this.type = Primitives.wrap(type);
            this.getFrom = getFrom;
            this.enabled = enabled;
            this.sortable = true;
            this.modelIndex = PropsTable.this.getPropsDataModel().props.size();
            this.checkbox = new JCheckBoxMenuItem(name, enabled);
            this.checkbox.addActionListener($ -> this.setEnabled(this.checkbox.isSelected()));
            PropsTable.this.columnSelectMenu.add(this.checkbox);
            this.column = new TableColumn(this.modelIndex);
            this.column.setIdentifier(name);
            
            PropsTable.this.getPropsDataModel().props.add(this);
            if (enabled)
            {
                PropsTable.this.addColumn(this.column);
                PropsTable.this.getPropsDataModel().fireTableStructureChanged();
            }
            
            this.defaultSort();
        }
        @SuppressWarnings("unchecked")
        private void defaultSort()
        {
            if (this.type == Period.class)
            {
                this.setComparator((Comparator<P>)Comparator.comparingLong((Period $) -> $.toTotalMonths() * 31 + $.getDays()));
            }
            else if (this.type == Action.class)
            {
                this.setSortable(false);
            }
        }
        final String id, name;
        final boolean editable;
        final Class<P> type;
        final Function<E, P> getFrom;
        final int modelIndex;
        final JCheckBoxMenuItem checkbox;
        final TableColumn column;
        boolean enabled, sortable;
        Comparator<P> comparator;
        public void setSortable(boolean sortable)
        {
            this.sortable = sortable;
        }
        public void setComparator(Comparator<P> comparator)
        {
            this.comparator = comparator;
        }
        public String getId()
        {
            return this.id;
        }
        public String getName()
        {
            return this.name;
        }
        public int getWidth()
        {
            return this.column.getWidth();
        }
        public void setWidth(int width)
        {
            this.column.setWidth(width);
            this.column.setPreferredWidth(width);
        }
        public boolean isEnabled()
        {
            return this.enabled;
        }
        public synchronized void setEnabled(boolean enabled)
        {
            if (this.enabled == enabled)
                return;
            if (this.enabled = enabled)
                PropsTable.this.addColumn(this.column);
            else
                PropsTable.this.removeColumn(this.column);
            if (this.checkbox.isSelected() != enabled)
                this.checkbox.setSelected(enabled);
            PropsTable.this.getPropsDataModel().fireTableStructureChanged();
        }
        public int getModelIndex()
        {
            return this.modelIndex;
        }
        public int getDisplayIndex()
        {
            try
            {
                return PropsTable.this.columnModel.getColumnIndex(this.name);
            }
            catch (IllegalArgumentException iaex)
            {
                return -1;
            }
        }
        public void setDisplayIndex(int visualIndex)
        {
            if (this.enabled)
                PropsTable.this.columnModel.moveColumn(visualIndex, visualIndex);
        }
    }

    public <P> PropsInfo<P> addProperty(String name, boolean editable, boolean enabled, Class<P> type, Function<E, P> getFrom)
    { 
        return this.addProperty(name, name, editable, enabled, type, getFrom);
    }
    public <P> PropsInfo<P> addProperty(String id, String name, boolean editable, boolean enabled, Class<P> type, Function<E, P> getFrom)
    { 
        return new PropsInfo<>(id, name, editable, enabled, type, getFrom);
    }

    public synchronized void addEntry(E entry)
    {
        this.getPropsDataModel().addEntry(entry);
    }

    public synchronized boolean removeEntry(E entry)
    {
        return this.getPropsDataModel().removeEntry(entry);
    }

    public synchronized boolean updateEntry(E entry)
    {
        return this.getPropsDataModel().updateEntry(entry);
    }

    public synchronized void clearEntries()
    {
        this.getPropsDataModel().clearEntries();
    }

    public synchronized boolean sortEntries(Comparator<? super E> comparator)
    {
        return this.getPropsDataModel().sortEntries(comparator);
    }

    public synchronized void addEntrySilently(E entry)
    {
        this.getPropsDataModel().addEntrySilently(entry);
    }

    public synchronized boolean removeEntrySilently(E entry)
    {
        return this.getPropsDataModel().removeEntrySilently(entry);
    }

    public List<E> getEntries()
    {
        return Collections.unmodifiableList(this.getPropsDataModel().entries);
    }

    public List<PropsInfo<?>> getProps()
    {
        return Collections.unmodifiableList(this.getPropsDataModel().props);
    }

    public void iterProps(Consumer<? super PropsInfo<?>> consumer)
    {
        this.getPropsDataModel().props.forEach(consumer);
    }

    public PropsInfo<?> getProp(String name)
    {
        List<PropsInfo<?>> props = this.getPropsDataModel().props;
        for (PropsInfo<?> info : props)
            if (info.id.equals(name))
                return info;
        for (PropsInfo<?> info : props)
            if (info.name.equals(name))
                return info;
        return null;
    }

    public List<PropsInfo<?>> getEnabledProps()
    {
        List<PropsInfo<?>> ret = new ArrayList<>();
        this.iterEnabledProps((info, columnIndex) -> ret.add(info));
        return ret;
    }

    public void iterEnabledProps(ObjIntConsumer<? super PropsInfo<?>> consumer)
    {
        PropsTableModel model = this.getPropsDataModel();
        for (int columnIndex = 0, enabledCount = this.columnModel.getColumnCount(); columnIndex < enabledCount; columnIndex++)
        {
            PropsInfo<?> info = model.props.get(this.columnModel.getColumn(columnIndex).getModelIndex());
            if (info.enabled)
            {
                consumer.accept(info, columnIndex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void createDefaultRenderers()
    {
        super.createDefaultRenderers();
        // Default clones
        this.defaultRenderersByColumnClass.put(Object.class, (UIDefaults.LazyValue) t -> new PropsObjectRenderer());
        this.defaultRenderersByColumnClass.put(Number.class, (UIDefaults.LazyValue) t -> new PropsNumberRenderer());
        this.defaultRenderersByColumnClass.put(Float.class, (UIDefaults.LazyValue) t -> new PropsDoubleRenderer());
        this.defaultRenderersByColumnClass.put(Double.class, (UIDefaults.LazyValue) t -> new PropsDoubleRenderer());
        this.defaultRenderersByColumnClass.put(Date.class, (UIDefaults.LazyValue) t -> new PropsDateRenderer());
        this.defaultRenderersByColumnClass.put(Icon.class, (UIDefaults.LazyValue) t -> new PropsIconRenderer());
        this.defaultRenderersByColumnClass.put(ImageIcon.class, (UIDefaults.LazyValue) t -> new PropsIconRenderer());
        this.defaultRenderersByColumnClass.put(Boolean.class, (UIDefaults.LazyValue) t -> new PropsBooleanRenderer());
        // Action
        this.defaultRenderersByColumnClass.put(Action.class, (UIDefaults.LazyValue) t -> new PropsActionRenderer());
        // TemporalAccessor
        this.defaultRenderersByColumnClass.put(Instant.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_I));
        this.defaultRenderersByColumnClass.put(Year.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_LD));
        this.defaultRenderersByColumnClass.put(YearMonth.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_LD));
        this.defaultRenderersByColumnClass.put(LocalDate.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_LD));
        this.defaultRenderersByColumnClass.put(LocalTime.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_LT));
        this.defaultRenderersByColumnClass.put(LocalDateTime.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_LDT));
//        this.defaultRenderersByColumnClass.put(OffsetDate.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_OD));
        this.defaultRenderersByColumnClass.put(OffsetTime.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_OT));
        this.defaultRenderersByColumnClass.put(OffsetDateTime.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_ODT));
        this.defaultRenderersByColumnClass.put(ZonedDateTime.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer(FMT_ZDT));
        this.defaultRenderersByColumnClass.put(TemporalAccessor.class, (UIDefaults.LazyValue) t -> new PropsTemporalAccessorRenderer());
        // TemporalAmount
        this.defaultRenderersByColumnClass.put(Period.class, (UIDefaults.LazyValue) t -> new PropsTemporalAmountRenderer());
        this.defaultRenderersByColumnClass.put(Duration.class, (UIDefaults.LazyValue) t -> new PropsTemporalAmountRenderer());
        this.defaultRenderersByColumnClass.put(TemporalAmount.class, (UIDefaults.LazyValue) t -> new PropsTemporalAmountRenderer());
        // Enum
        this.defaultRenderersByColumnClass.put(Enum.class, (UIDefaults.LazyValue) t -> new PropsObjectRenderer());
    }
    private static final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);
    class PropsObjectRenderer extends PropsTableCellRenderer.PropsUIResource
    {
        private static final long serialVersionUID = 8688842921569726502L;
        PropsObjectRenderer()
        {
            super();
        }
        @Override
        protected void setValue(Object value)
        {
            super.setValue(value);
            Font overrideFont = PropsTable.this.getPropsTableExt().getOverrideFont(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getFont());
            if (overrideFont != null)
            {
                this.setFont(overrideFont);
            }
            Color overrideForegroundColor = PropsTable.this.getPropsTableExt().getOverrideForegroundColor(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getForeground());
            if (overrideForegroundColor != null)
            {
                this.setForeground(overrideForegroundColor);
            }
            else
            {
                this.setForeground(null);
            }
        }
    }
    class PropsNumberRenderer extends PropsTableCellRenderer.PropsUIResource
    {
        private static final long serialVersionUID = 7068217672892266835L;
        PropsNumberRenderer()
        {
            super();
            this.setHorizontalAlignment(JLabel.RIGHT);
        }
        @Override
        protected void setValue(Object value)
        {
            super.setValue(value);
            Font overrideFont = PropsTable.this.getPropsTableExt().getOverrideFont(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getFont());
            if (overrideFont != null)
            {
                this.setFont(overrideFont);
            }
            Color overrideForegroundColor = PropsTable.this.getPropsTableExt().getOverrideForegroundColor(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getForeground());
            if (overrideForegroundColor != null)
            {
                this.setForeground(overrideForegroundColor);
            }
            else
            {
                this.setForeground(null);
            }
        }
    }
    class PropsDoubleRenderer extends PropsNumberRenderer
    {
        private static final long serialVersionUID = 1425063164787487108L;
        NumberFormat formatter;
        PropsDoubleRenderer()
        {
            super();
        }
        @Override
        public void setValue(Object value)
        {
            if (this.formatter == null)
            {
                this.formatter = NumberFormat.getInstance();
            }
            this.setText((value == null) ? "" : this.formatter.format(value));
            Font overrideFont = PropsTable.this.getPropsTableExt().getOverrideFont(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getFont());
            if (overrideFont != null)
            {
                this.setFont(overrideFont);
            }
            Color overrideForegroundColor = PropsTable.this.getPropsTableExt().getOverrideForegroundColor(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getForeground());
            if (overrideForegroundColor != null)
            {
                this.setForeground(overrideForegroundColor);
            }
            else
            {
                this.setForeground(null);
            }
        }
    }
    class PropsDateRenderer extends PropsTableCellRenderer.PropsUIResource
    {
        private static final long serialVersionUID = -6928789909621291528L;
        DateFormat formatter;
        PropsDateRenderer()
        {
            super();
        }
        @Override
        public void setValue(Object value)
        {
            if (this.formatter == null)
            {
                this.formatter = DateFormat.getDateInstance();
            }
            this.setText((value == null) ? "" : this.formatter.format(value));
            Font overrideFont = PropsTable.this.getPropsTableExt().getOverrideFont(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getFont());
            if (overrideFont != null)
            {
                this.setFont(overrideFont);
            }
            Color overrideForegroundColor = PropsTable.this.getPropsTableExt().getOverrideForegroundColor(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getForeground());
            if (overrideForegroundColor != null)
            {
                this.setForeground(overrideForegroundColor);
            }
            else
            {
                this.setForeground(null);
            }
        }
    }
    class PropsIconRenderer extends PropsTableCellRenderer.PropsUIResource
    {
        private static final long serialVersionUID = 1088008190079243567L;
        PropsIconRenderer()
        {
            super();
            this.setHorizontalAlignment(JLabel.CENTER);
        }
        @Override
        public void setValue(Object value)
        {
            this.setIcon((value instanceof Icon) ? (Icon)value : null);
            Font overrideFont = PropsTable.this.getPropsTableExt().getOverrideFont(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getFont());
            if (overrideFont != null)
            {
                this.setFont(overrideFont);
            }
            Color overrideForegroundColor = PropsTable.this.getPropsTableExt().getOverrideForegroundColor(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getForeground());
            if (overrideForegroundColor != null)
            {
                this.setForeground(overrideForegroundColor);
            }
            else
            {
                this.setForeground(null);
            }
        }
    }
    class PropsBooleanRenderer extends JCheckBox implements TableCellRenderer, UIResource
    {
        private static final long serialVersionUID = -1826302992763403224L;
        PropsBooleanRenderer()
        {
            super();
            this.setHorizontalAlignment(JLabel.CENTER);
            this.setBorderPainted(true);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
        {
            if (isSelected)
            {
                this.setForeground(table.getSelectionForeground());
                this.setBackground(table.getSelectionBackground());
            }
            else
            {
                this.setForeground(table.getForeground());
                this.setBackground(table.getBackground());
            }
            this.setSelected((value != null && ((Boolean)value).booleanValue()));
            if (hasFocus)
            {
                this.setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
            }
            else
            {
                this.setBorder(noFocusBorder);
            }
            Font overrideFont = PropsTable.this.getPropsTableExt().getOverrideFont(PropsTable.this.getPropsDataModel().entries.get(row), this.getFont());
            if (overrideFont != null)
            {
                this.setFont(overrideFont);
            }
            Color overrideForegroundColor = PropsTable.this.getPropsTableExt().getOverrideForegroundColor(PropsTable.this.getPropsDataModel().entries.get(row), this.getForeground());
            if (overrideForegroundColor != null)
            {
                this.setForeground(overrideForegroundColor);
            }
            return this;
        }
    }
    class PropsActionRenderer extends JButton implements TableCellRenderer, UIResource
    {
        private static final long serialVersionUID = -1357124806959379173L;
        PropsActionRenderer()
        {
            super();
            this.setHorizontalAlignment(JLabel.CENTER);
            this.setBorderPainted(true);
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
        {
            if (isSelected)
            {
                this.setForeground(table.getSelectionForeground());
                this.setBackground(table.getSelectionBackground());
            }
            else
            {
                this.setForeground(table.getForeground());
                this.setBackground(table.getBackground());
            }
            this.setText(value == null ? null : String.valueOf(((Action)value).getValue(Action.NAME)));
            if (hasFocus)
            {
                this.setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
            }
            else
            {
                this.setBorder(noFocusBorder);
            }
            Font overrideFont = PropsTable.this.getPropsTableExt().getOverrideFont(PropsTable.this.getPropsDataModel().entries.get(row), this.getFont());
            if (overrideFont != null)
            {
                this.setFont(overrideFont);
            }
            Color overrideForegroundColor = PropsTable.this.getPropsTableExt().getOverrideForegroundColor(PropsTable.this.getPropsDataModel().entries.get(row), this.getForeground());
            if (overrideForegroundColor != null)
            {
                this.setForeground(overrideForegroundColor);
            }
            return this;
        }
    }
    static final DateTimeFormatter FMT_I = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
                                   FMT_LD = DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                                   FMT_LT = DateTimeFormatter.ofPattern("HH:mm:ss"),
                                   FMT_LDT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                                   FMT_OD = DateTimeFormatter.ofPattern("yyyy-MM-dd Z"),
                                   FMT_OT = DateTimeFormatter.ofPattern("HH:mm:ss Z"),
                                   FMT_ODT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"),
                                   FMT_ZDT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    class PropsTemporalAccessorRenderer extends PropsTableCellRenderer.PropsUIResource
    {
        private static final long serialVersionUID = 8688842921569726502L;
        PropsTemporalAccessorRenderer()
        {
            this(null);
        }
        PropsTemporalAccessorRenderer(DateTimeFormatter formatter)
        {
            super();
            this.formatter = formatter;
        }
        DateTimeFormatter formatter;
        @Override
        protected void setValue(Object value)
        {
            if (this.formatter == null)
            {
                this.formatter = DateTimeFormatter.ISO_INSTANT;
            }
            this.setText((value == null) ? "" : this.formatter.format((TemporalAccessor)value));
            Font overrideFont = PropsTable.this.getPropsTableExt().getOverrideFont(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getFont());
            if (overrideFont != null)
            {
                this.setFont(overrideFont);
            }
            Color overrideForegroundColor = PropsTable.this.getPropsTableExt().getOverrideForegroundColor(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getForeground());
            if (overrideForegroundColor != null)
            {
                this.setForeground(overrideForegroundColor);
            }
            else
            {
                this.setForeground(null);
            }
        }
    }
    class PropsTemporalAmountRenderer extends PropsTableCellRenderer.PropsUIResource
    {
        private static final long serialVersionUID = 8688842921569726502L;
        PropsTemporalAmountRenderer()
        {
            super();
        }
        @Override
        protected void setValue(Object value)
        {
            if (value == null)
            {
                this.setText("");
            }
            else if (value instanceof Period)
            {
                this.setText(MiscUtils.stringify_ymd((Period)value));
            }
            else
            {
                this.setText(MiscUtils.stringify_ymd_hms_ms_us_ns((TemporalAmount)value));
            }
            
            Font overrideFont = PropsTable.this.getPropsTableExt().getOverrideFont(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getFont());
            if (overrideFont != null)
            {
                this.setFont(overrideFont);
            }
            Color overrideForegroundColor = PropsTable.this.getPropsTableExt().getOverrideForegroundColor(PropsTable.this.getPropsDataModel().entries.get(this.gtcrc_row), this.getForeground());
            if (overrideForegroundColor != null)
            {
                this.setForeground(overrideForegroundColor);
            }
            else
            {
                this.setForeground(null);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void createDefaultEditors()
    {
        super.createDefaultEditors();
        this.defaultEditorsByColumnClass.put(Action.class, (UIDefaults.LazyValue) t -> new ActionEditor());
    }
    public <EE extends Enum<EE>> boolean createDefaultEnumEditor(Class<EE> type, boolean replace)
    {
        return this.createDefaultEnumEditor(type, replace, type.getEnumConstants());
    }
    @SuppressWarnings("unchecked")
    public <EE extends Enum<EE>> boolean createDefaultEnumEditor(Class<EE> type, boolean replace, EE... permittedValues)
    {
        UIDefaults.LazyValue lazyValue = t -> new EnumEditor<>(type, permittedValues);
        return null == (replace
            ? this.defaultEditorsByColumnClass.put(type, lazyValue)
            : this.defaultEditorsByColumnClass.putIfAbsent(type, lazyValue));
    }
    class ActionEditor extends PropsTableCellEditor
    {
        private static final long serialVersionUID = -8640097463662002706L;
        ActionEditor()
        {
            super(new JButton());
            JButton button = (JButton)this.getComponent();
            button.setHorizontalAlignment(JButton.CENTER);
        }
    }
    class EnumEditor<EE extends Enum<EE>> extends PropsTableCellEditor
    {
        private static final long serialVersionUID = -8640097463662002706L;
        EnumEditor(Class<EE> type, EE[] values)
        {
            super(new JComboBox<EE>(values));
        }
    }

    @Override
    protected TableModel createDefaultDataModel()
    {
        return new PropsTableModel();
    }
    @SuppressWarnings("unchecked")
    public PropsTableModel getPropsDataModel()
    {
        return (PropsTableModel)this.dataModel;
    }
    class PropsTableModel extends AbstractTableModel
    {
        private static final long serialVersionUID = 1358535508623151691L;
        PropsTableModel()
        {
        }
        final List<E> entries = new CopyOnWriteArrayList<>();
        final List<PropsInfo<?>> props = new CopyOnWriteArrayList<>();
        boolean pendingSilentOps = false;
        void queuePendingSilentOps()
        {
            this.pendingSilentOps = true;
        }
        void pollPendingSilentOps()
        {
            if (!this.pendingSilentOps)
                return;
            this.pendingSilentOps = false;
            this.fireTableDataChanged();
        }
        void addEntry(E entry)
        {
            this.pollPendingSilentOps();
            int index = this.entries.size();
            this.entries.add(entry);
            this.fireTableRowsInserted(index, index);
        }
        boolean removeEntry(E entry)
        {
            this.pollPendingSilentOps();
            int index = this.entries.indexOf(entry);
            if (index < 0)
                return false;
            this.entries.remove(index);
            this.fireTableRowsDeleted(index, index);
            return true;
        }
        boolean updateEntry(E entry)
        {
            this.pollPendingSilentOps();
            int index = this.entries.indexOf(entry);
            if (index < 0)
                return false;
            this.fireTableRowsUpdated(index, index);
            return true;
        }
        boolean clearEntries()
        {
            this.pollPendingSilentOps();
            int size = this.entries.size();
            if (size == 0)
                return false;
            this.entries.clear();
            this.fireTableRowsDeleted(0, size - 1);
            return true;
        }
        boolean sortEntries(Comparator<? super E> comparator)
        {
            this.pollPendingSilentOps();
            int size = this.entries.size();
            if (size == 0)
                return false;
            this.entries.sort(comparator);
            this.fireTableRowsUpdated(0, size - 1);
            return true;
        }
        void addEntrySilently(E entry)
        {
            this.entries.add(entry);
            this.queuePendingSilentOps();
        }
        boolean removeEntrySilently(E entry)
        {
            int index = this.entries.indexOf(entry);
            if (index < 0)
                return false;
            this.entries.remove(index);
            this.queuePendingSilentOps();
            return true;
        }
        @Override
        public int getRowCount()
        {
            return this.entries.size();
        }
        @Override
        public int getColumnCount()
        {
            if (this.props == null)
                return 0;
            return this.props.size();
        }
        @Override
        public String getColumnName(int column)
        {
            return this.props.get(column).name;
        }
        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            try
            {
                return this.props.get(columnIndex).getFrom.apply(this.entries.get(rowIndex));
            }
            catch (IndexOutOfBoundsException ioobex)
            {
                return null;
            }
        }
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return this.props.get(columnIndex).editable;
        }
        @Override
        public Class<?> getColumnClass(int columnIndex)
        {
            return this.props.get(columnIndex).type;
        }
    }

    @SuppressWarnings("unchecked")
    public PropsRowSorter getPropsRowSorter()
    {
        return (PropsRowSorter)this.getRowSorter();
    }
    class PropsRowSorter extends DefaultRowSorter<PropsTableModel, Integer>
    {
        PropsRowSorter()
        {
            super();
            this.setModelWrapper(new PropsRowSorterModelWrapper());
        }
        public boolean isSortable(int column)
        {
            return super.isSortable(column) && this.getModel().props.get(column).sortable;
        }
        public Comparator<?> getComparator(int column)
        {
            Comparator<?> comparator = super.getComparator(column);
            if (comparator != null)
                return comparator;
            comparator = this.getModel().props.get(column).comparator;
            if (comparator != null)
                return comparator;
            Class<?> columnClass = this.getModel().getColumnClass(column);
            if (columnClass == String.class)
                return Collator.getInstance();
            if (Comparable.class.isAssignableFrom(columnClass))
                return Comparator.naturalOrder();
            return Collator.getInstance();
        }
        protected boolean useToString(int column)
        {
            Comparator<?> comparator = super.getComparator(column);
            if (comparator != null)
                return false;
            comparator = this.getModel().props.get(column).comparator;
            if (comparator != null)
                return false;
            Class<?> columnClass = this.getModel().getColumnClass(column);
            if (columnClass == String.class)
                return false;
            if (Comparable.class.isAssignableFrom(columnClass))
                return false;
            return true;
        }
        class PropsRowSorterModelWrapper extends ModelWrapper<PropsTableModel, Integer>
        {
            TableStringConverter converter = null;
            public PropsTableModel getModel()
            {
                return PropsTable.this.getPropsDataModel();
            }
            public int getColumnCount()
            {
                return this.getModel().getColumnCount();
            }
            public int getRowCount()
            {
                return this.getModel().getRowCount();
            }
            public Object getValueAt(int row, int column)
            {
                return this.getModel().getValueAt(row, column);
            }
            public String getStringValueAt(int row, int column)
            {
                TableStringConverter converter = this.converter;
                if (converter != null)
                {
                    String value = converter.toString(this.getModel(), row, column);
                    if (value != null)
                        return value;
                    return "";
                }

                // No converter, use getValueAt followed by toString
                Object o = this.getValueAt(row, column);
                if (o == null)
                    return "";
                String string = o.toString();
                if (string == null)
                    return "";
                return string;
            }
            public Integer getIdentifier(int index)
            {
                return index;
            }
        }
    }

    protected PropsTableExt getDefaultPropsTableExt()
    {
        return new PropsTableExt();
    }
    public PropsTableExt getPropsTableExt()
    {
        return this.propsTableExt;
    }
    public void setPropsTableExt(PropsTableExt propsTableExt)
    {
        this.propsTableExt = propsTableExt == null ? this.getDefaultPropsTableExt() : propsTableExt;
    }

    protected PropsTableExt propsTableExt;

    public class PropsTableExt
    {
        public Font getOverrideFont(E element, Font prev)
        {
            return null;
        }
        public Color getOverrideForegroundColor(E element, Color prev)
        {
            return null;
        }
    }

}
