package net.sybyline.scarlet;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;

public class ScarletUISplash implements AutoCloseable
{

    public ScarletUISplash(Scarlet scarlet)
    {
        this.scarlet = scarlet;
    }

    final Scarlet scarlet;
    JWindow splash = new JWindow();
    JPanel splashPanel = new JPanel(null);
    JLabel splashText = new TransparentJLabel("Loading...", JLabel.CENTER),
           splashSubtext = new TransparentJLabel("", JLabel.CENTER);

    class TransparentJLabel extends JLabel
    {
        private static final long serialVersionUID = -477245339419302629L;
        TransparentJLabel(String text, int horizontalAlignment)
        {
            super(text, horizontalAlignment);
        }
        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D)g;
            // Enable anti-aliasing for smooth text
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Clear previous text by filling with a transparent color
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            g2.fillRect(0, 0, getWidth(), getHeight());
            // Reset composite for normal drawing
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
            super.paintComponent(g);
        }
    }

    {
        Image image = Toolkit.getDefaultToolkit().createImage(ScarletUI.class.getResource("sybyline_scarlet.png"));
        this.splash.setSize(400, 450);
        this.splash.setLocationRelativeTo(null);
        this.splash.setFocusable(false);
        this.splash.setAlwaysOnTop(true);
        this.splash.setBackground(new Color(127, 127, 127, 127));
        {
            this.splashPanel.setOpaque(false);
            {
                JLabel jlabel_logo = new JLabel(new ImageIcon(image.getScaledInstance(this.splash.getWidth(), this.splash.getHeight(), Image.SCALE_SMOOTH)), JLabel.CENTER);
                this.splashPanel.add(jlabel_logo);
                jlabel_logo.setBounds(0, 0, 400, 400);
                this.splashPanel.setComponentZOrder(jlabel_logo, 0);
                jlabel_logo.revalidate();
            }
            {
                this.splashText.setFont(new Font("Arial", Font.BOLD, 24));
                this.splashText.setBackground(new Color(127, 127, 127, 127));
                this.splashText.setForeground(Color.WHITE);
                this.splashPanel.add(this.splashText);
                this.splashText.setBounds(0, 400, 400, 30);
                this.splashPanel.setComponentZOrder(this.splashText, 0);
                this.splashText.revalidate();
            }
            {
                this.splashSubtext.setFont(new Font("Arial", Font.BOLD, 16));
                this.splashSubtext.setBackground(new Color(127, 127, 127, 127));
                this.splashSubtext.setForeground(Color.WHITE);
                this.splashPanel.add(this.splashSubtext);
                this.splashSubtext.setBounds(0, 430, 400, 20);
                this.splashPanel.setComponentZOrder(this.splashSubtext, 0);
                this.splashSubtext.revalidate();
            }
        }
        this.splash.setContentPane(this.splashPanel);
        this.splash.setVisible(true);
    }

    @Override
    public synchronized void close()
    {
        JWindow splash = this.splash;
        if (splash == null)
            return;
        this.splash = null;
        this.splashPanel = null;
        this.splashText = null;
        this.splashSubtext = null;
        splash.setVisible(false);
        splash.dispose();
        this.scarlet.ui.jframe.setVisible(true);
    }

    public void splashText(String text)
    {
        JLabel splashText = this.splashText;
        if (splashText != null)
            splashText.setText(text);
        this.splashSubtext("");
    }

    public void splashSubtext(String text)
    {
        JLabel splashSubtext = this.splashSubtext;
        if (splashSubtext != null)
            splashSubtext.setText(text);
    }

}
