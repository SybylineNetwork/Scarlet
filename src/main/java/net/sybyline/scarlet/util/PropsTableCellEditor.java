package net.sybyline.scarlet.util;

import java.awt.Component;
import java.awt.event.*;
import java.beans.ConstructorProperties;
import java.lang.Boolean;
import javax.swing.table.*;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.util.EventObject;
import java.io.Serializable;

public class PropsTableCellEditor extends AbstractCellEditor implements TableCellEditor
{
    private static final long serialVersionUID = 8957826004827944674L;

    protected JComponent editorComponent;

    protected PropsTableEditorDelegate delegate;

    protected int clickCountToStart = 1;

    @ConstructorProperties({ "component" })
    public PropsTableCellEditor(final JTextField textField)
    {
        editorComponent = textField;
        this.clickCountToStart = 2;
        delegate = new PropsTableEditorDelegate()
        {
            private static final long serialVersionUID = 2225240265920038133L;

            @Override
            public void setValue(Object value)
            {
                textField.setText((value != null) ? value.toString() : "");
            }

            @Override
            public Object getCellEditorValue()
            {
                return textField.getText();
            }
        };
        textField.addActionListener(delegate);
    }

    public PropsTableCellEditor(final JCheckBox checkBox)
    {
        editorComponent = checkBox;
        delegate = new PropsTableEditorDelegate()
        {
            private static final long serialVersionUID = -234201957533610946L;

            @Override
            public void setValue(Object value)
            {
                boolean selected = false;
                if (value instanceof Boolean)
                {
                    selected = ((Boolean) value).booleanValue();
                } else if (value instanceof String)
                {
                    selected = value.equals("true");
                }
                checkBox.setSelected(selected);
            }

            @Override
            public Object getCellEditorValue()
            {
                return Boolean.valueOf(checkBox.isSelected());
            }
        };
        checkBox.addActionListener(delegate);
        checkBox.setRequestFocusEnabled(false);
    }

    public PropsTableCellEditor(final JComboBox<?> comboBox)
    {
        editorComponent = comboBox;
        comboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
        delegate = new PropsTableEditorDelegate()
        {
            private static final long serialVersionUID = 1232250788898682784L;

            @Override
            public void setValue(Object value)
            {
                comboBox.setSelectedItem(value);
            }

            @Override
            public Object getCellEditorValue()
            {
                return comboBox.getSelectedItem();
            }

            @Override
            public boolean shouldSelectCell(EventObject anEvent)
            {
                if (anEvent instanceof MouseEvent)
                {
                    MouseEvent e = (MouseEvent) anEvent;
                    return e.getID() != MouseEvent.MOUSE_DRAGGED;
                }
                return true;
            }

            @Override
            public boolean stopCellEditing()
            {
                if (comboBox.isEditable())
                {
                    comboBox.actionPerformed(new ActionEvent(PropsTableCellEditor.this, 0, ""));
                }
                return super.stopCellEditing();
            }
        };
        comboBox.addActionListener(delegate);
    }

    public PropsTableCellEditor(final JButton button)
    {
        editorComponent = button;
        button.putClientProperty("JButton.isTableCellEditor", Boolean.TRUE);
        delegate = new PropsTableEditorDelegate()
        {
            private static final long serialVersionUID = -3650208933997412154L;

            @Override
            public void setValue(Object value)
            {
                button.setText(String.valueOf(((Action) value).getValue(Action.NAME)));
                button.setAction((Action) value);
            }

            @Override
            public Object getCellEditorValue()
            {
                return button.getAction();
            }

            @Override
            public boolean shouldSelectCell(EventObject anEvent)
            {
                if (anEvent instanceof MouseEvent)
                {
                    MouseEvent e = (MouseEvent) anEvent;
                    return e.getID() != MouseEvent.MOUSE_DRAGGED;
                }
                return true;
            }
        };
        button.addActionListener(delegate);
    }

    public Component getComponent()
    {
        return editorComponent;
    }

    public void setClickCountToStart(int count)
    {
        clickCountToStart = count;
    }

    public int getClickCountToStart()
    {
        return clickCountToStart;
    }

    @Override
    public Object getCellEditorValue()
    {
        return delegate.getCellEditorValue();
    }

    @Override
    public boolean isCellEditable(EventObject anEvent)
    {
        return delegate.isCellEditable(anEvent);
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent)
    {
        return delegate.shouldSelectCell(anEvent);
    }

    @Override
    public boolean stopCellEditing()
    {
        return delegate.stopCellEditing();
    }

    @Override
    public void cancelCellEditing()
    {
        delegate.cancelCellEditing();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
    {
        delegate.setValue(value);
        if (editorComponent instanceof JCheckBox)
        {
            TableCellRenderer renderer = table.getCellRenderer(row, column);
            Component c = renderer.getTableCellRendererComponent(table, value, isSelected, true, row, column);
            if (c != null)
            {
                editorComponent.setOpaque(true);
                editorComponent.setBackground(c.getBackground());
                if (c instanceof JComponent)
                {
                    editorComponent.setBorder(((JComponent) c).getBorder());
                }
            }
            else
            {
                editorComponent.setOpaque(false);
            }
        }
        return editorComponent;
    }

    protected class PropsTableEditorDelegate implements ActionListener, ItemListener, Serializable
    {
        private static final long serialVersionUID = 7825950726910952489L;

        protected Object value;

        public Object getCellEditorValue()
        {
            return value;
        }

        public void setValue(Object value)
        {
            this.value = value;
        }

        public boolean isCellEditable(EventObject anEvent)
        {
            if (anEvent instanceof MouseEvent)
            {
                return ((MouseEvent) anEvent).getClickCount() >= clickCountToStart;
            }
            return true;
        }

        public boolean shouldSelectCell(EventObject anEvent)
        {
            return true;
        }

        public boolean startCellEditing(EventObject anEvent)
        {
            return true;
        }

        public boolean stopCellEditing()
        {
            fireEditingStopped();
            return true;
        }

        public void cancelCellEditing()
        {
            fireEditingCanceled();
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            PropsTableCellEditor.this.stopCellEditing();
        }

        @Override
        public void itemStateChanged(ItemEvent e)
        {
            PropsTableCellEditor.this.stopCellEditing();
        }
    }

}
