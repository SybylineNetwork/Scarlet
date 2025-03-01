package net.sybyline.scarlet.util;

import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
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

import com.google.gson.internal.Primitives;

public class PropsTable<E> extends JTable
{

    private static final long serialVersionUID = 2655466294113059041L;

    public PropsTable()
    {
        super();
        this.tableHeader.setReorderingAllowed(true);
        this.tableHeader.setResizingAllowed(true);
        this.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        this.setAutoCreateColumnsFromModel(false);
        this.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e)
            {
                PropsTable.this.clearSelection();
            }
        });
    }

    protected final JMenu columnSelectMenu = new JMenu();
    public JMenu getColumnSelectMenu()
    {
        return this.columnSelectMenu;
    }

    public class PropsInfo<P>
    {
        protected PropsInfo(String name, boolean editable, boolean enabled, Class<P> type, Function<E, P> getFrom)
        {
            this.name = name;
            this.editable = editable;
            this.type = Primitives.wrap(type);
            this.getFrom = getFrom;
            this.enabled = enabled;
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
        }
        final String name;
        final boolean editable;
        final Class<P> type;
        final Function<E, P> getFrom;
        final int modelIndex;
        final JCheckBoxMenuItem checkbox;
        final TableColumn column;
        boolean enabled;
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
        return new PropsInfo<>(name, editable, enabled, type, getFrom);
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
        for (PropsInfo<?> info : this.getPropsDataModel().props)
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
        this.defaultRenderersByColumnClass.put(Action.class, (UIDefaults.LazyValue) t -> new ActionRenderer());
    }
    private static final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);
    class ActionRenderer extends JButton implements TableCellRenderer, UIResource
    {
        private static final long serialVersionUID = -1357124806959379173L;
        ActionRenderer()
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
            return this;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void createDefaultEditors()
    {
        super.createDefaultEditors();
        this.defaultEditorsByColumnClass.put(Action.class, (UIDefaults.LazyValue) t -> new ActionEditor());
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
        void addEntry(E entry)
        {
            int index = this.entries.size();
            this.entries.add(entry);
            this.fireTableRowsInserted(index, index);
        }
        boolean removeEntry(E entry)
        {
            int index = this.entries.indexOf(entry);
            if (index < 0)
                return false;
            this.entries.remove(index);
            this.fireTableRowsDeleted(index, index);
            return true;
        }
        boolean updateEntry(E entry)
        {
            int index = this.entries.indexOf(entry);
            if (index < 0)
                return false;
            this.fireTableRowsUpdated(index, index);
            return true;
        }
        boolean clearEntries()
        {
            int size = this.entries.size();
            if (size == 0)
                return false;
            this.entries.clear();
            this.fireTableRowsDeleted(0, size - 1);
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

}
