package net.sybyline.scarlet.util;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.UIResource;
import javax.swing.table.TableCellRenderer;

import java.awt.Component;
import java.awt.Color;
import java.awt.Rectangle;

import java.io.Serializable;

public class PropsTableCellRenderer extends JLabel implements TableCellRenderer, Serializable
{

    private static final long serialVersionUID = 7994695842080707860L;

    private static final Border SAFE_NO_FOCUS_BORDER = new EmptyBorder(1, 1, 1, 1);
    private static final Border DEFAULT_NO_FOCUS_BORDER = new EmptyBorder(1, 1, 1, 1);
    protected static Border noFocusBorder = DEFAULT_NO_FOCUS_BORDER;

    // We need a place to store the color the JLabel should be returned
    // to after its foreground and background colors have been set
    // to the selection background color.
    // These ivars will be made protected when their names are finalized.
    private Color unselectedForeground;
    private Color unselectedBackground;
    protected boolean gtcrc_selected, gtcrc_focus;
    protected int gtcrc_row, gtcrc_column;

    public PropsTableCellRenderer()
    {
        super();
        setOpaque(true);
        setBorder(getNoFocusBorder());
        setName("Table.cellRenderer");
    }

    private Border getNoFocusBorder()
    {
        Border border = UIManager.getBorder("Table.cellNoFocusBorder", this.getLocale());
        if (System.getSecurityManager() != null)
        {
            if (border != null) return border;
            return SAFE_NO_FOCUS_BORDER;
        }
        else if (border != null)
        {
            if (noFocusBorder == null || noFocusBorder == DEFAULT_NO_FOCUS_BORDER)
            {
                return border;
            }
        }
        return noFocusBorder;
    }

    /**
     * Overrides <code>JComponent.setForeground</code> to assign
     * the unselected-foreground color to the specified color.
     *
     * @param c set the foreground color to this value
     */
    public void setForeground(Color c)
    {
        super.setForeground(c);
        unselectedForeground = c;
    }

    /**
     * Overrides <code>JComponent.setBackground</code> to assign
     * the unselected-background color to the specified color.
     *
     * @param c set the background color to this value
     */
    public void setBackground(Color c)
    {
        super.setBackground(c);
        unselectedBackground = c;
    }

    /**
     * Notification from the <code>UIManager</code> that the look and feel
     * [L&amp;F] has changed.
     * Replaces the current UI object with the latest version from the
     * <code>UIManager</code>.
     *
     * @see JComponent#updateUI
     */
    public void updateUI()
    {
        super.updateUI();
        setForeground(null);
        setBackground(null);
    }

    // implements javax.swing.table.TableCellRenderer
    /**
     *
     * Returns the default table cell renderer.
     * <p>
     * During a printing operation, this method will be called with
     * <code>isSelected</code> and <code>hasFocus</code> values of
     * <code>false</code> to prevent selection and focus from appearing
     * in the printed output. To do other customization based on whether
     * or not the table is being printed, check the return value from
     * {@link javax.swing.JComponent#isPaintingForPrint()}.
     *
     * @param table  the <code>JTable</code>
     * @param value  the value to assign to the cell at
     *                  <code>[row, column]</code>
     * @param isSelected true if cell is selected
     * @param hasFocus true if cell has focus
     * @param row  the row of the cell to render
     * @param column the column of the cell to render
     * @return the default table cell renderer
     * @see javax.swing.JComponent#isPaintingForPrint()
     */
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
    {
        this.gtcrc_selected = isSelected;
        this.gtcrc_focus = hasFocus;
        this.gtcrc_row = table == null ? row : table.convertRowIndexToModel(row);
        this.gtcrc_column = table == null ? column : table.convertColumnIndexToModel(column);
        if (table == null)
        {
            return this;
        }

        Color fg = null;
        Color bg = null;

        JTable.DropLocation dropLocation = table.getDropLocation();
        if (dropLocation != null
                && !dropLocation.isInsertRow()
                && !dropLocation.isInsertColumn()
                && dropLocation.getRow() == row
                && dropLocation.getColumn() == column)
        {

            fg = UIManager.getColor("Table.dropCellForeground", this.getLocale());
            bg = UIManager.getColor("Table.dropCellBackground", this.getLocale());

            isSelected = true;
        }

        if (isSelected)
        {
            super.setForeground(fg == null ? table.getSelectionForeground()
                                           : fg);
            super.setBackground(bg == null ? table.getSelectionBackground()
                                           : bg);
        }
        else
        {
            Color background = unselectedBackground != null
                                    ? unselectedBackground
                                    : table.getBackground();
            if (background == null || background instanceof UIResource)
            {
                Color alternateColor = UIManager.getColor("Table.alternateRowColor", this.getLocale());
                if (alternateColor != null && row % 2 != 0)
                {
                    background = alternateColor;
                }
            }
            super.setForeground(unselectedForeground != null
                                    ? unselectedForeground
                                    : table.getForeground());
            super.setBackground(background);
        }

        setFont(table.getFont());

        if (hasFocus)
        {
            Border border = null;
            if (isSelected)
            {
                border = UIManager.getBorder("Table.focusSelectedCellHighlightBorder", this.getLocale());
            }
            if (border == null)
            {
                border = UIManager.getBorder("Table.focusCellHighlightBorder", this.getLocale());
            }
            setBorder(border);

            if (!isSelected && table.isCellEditable(row, column))
            {
                Color col;
                col = UIManager.getColor("Table.focusCellForeground", this.getLocale());
                if (col != null)
                {
                    super.setForeground(col);
                }
                col = UIManager.getColor("Table.focusCellBackground", this.getLocale());
                if (col != null)
                {
                    super.setBackground(col);
                }
            }
        }
        else
        {
            setBorder(getNoFocusBorder());
        }

        setValue(value);

        return this;
    }

    /*
     * The following methods are overridden as a performance measure to
     * to prune code-paths are often called in the case of renders
     * but which we know are unnecessary.  Great care should be taken
     * when writing your own renderer to weigh the benefits and
     * drawbacks of overriding methods like these.
     */

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public boolean isOpaque()
    {
        Color back = getBackground();
        Component p = getParent();
        if (p != null)
        {
            p = p.getParent();
        }

        // p should now be the JTable.
        boolean colorMatch = (back != null) && (p != null) &&
            back.equals(p.getBackground()) &&
                        p.isOpaque();
        return !colorMatch && super.isOpaque();
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     *
     * @since 1.5
     */
    public void invalidate()
    {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void validate()
    {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void revalidate()
    {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void repaint(long tm, int x, int y, int width, int height)
    {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void repaint(Rectangle r)
    { 
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     *
     * @since 1.5
     */
    public void repaint()
    {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue)
    {
        // Strings get interned...
        if (propertyName=="text"
                || propertyName == "labelFor"
                || propertyName == "displayedMnemonic"
                || ((propertyName == "font" || propertyName == "foreground")
                    && oldValue != newValue
                    && getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey) != null))
        {
            super.firePropertyChange(propertyName, oldValue, newValue);
        }
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue)
    {
    }


    /**
     * Sets the <code>String</code> object for the cell being rendered to
     * <code>value</code>.
     *
     * @param value  the string value for this cell; if value is
     *          <code>null</code> it sets the text value to an empty string
     * @see JLabel#setText
     *
     */
    protected void setValue(Object value)
    {
        setText((value == null) ? "" : value.toString());
    }


    /**
     * A subclass of <code>DefaultTableCellRenderer</code> that
     * implements <code>UIResource</code>.
     * <code>DefaultTableCellRenderer</code> doesn't implement
     * <code>UIResource</code>
     * directly so that applications can safely override the
     * <code>cellRenderer</code> property with
     * <code>DefaultTableCellRenderer</code> subclasses.
     * <p>
     * <strong>Warning:</strong>
     * Serialized objects of this class will not be compatible with
     * future Swing releases. The current serialization support is
     * appropriate for short term storage or RMI between applications running
     * the same version of Swing.  As of 1.4, support for long term storage
     * of all JavaBeans&trade;
     * has been added to the <code>java.beans</code> package.
     * Please see {@link java.beans.XMLEncoder}.
     */
    public static class PropsUIResource extends PropsTableCellRenderer implements UIResource
    {
        private static final long serialVersionUID = 452524930233650552L;
    }

}
