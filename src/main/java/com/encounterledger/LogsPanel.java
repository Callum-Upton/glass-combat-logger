package com.encounterledger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/** Local file access only: opening the panel never sends or uploads a recording. */
final class LogsPanel extends PluginPanel
{
    LogsPanel(Path directory)
    {
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.add(new JLabel("<html><b>Glass Combat Logger</b><br><br>"
            + "Your recordings are saved on this computer.<br><br>"
            + "Enable Combined capture in the plugin settings to keep research and fight logs together."
            + "<br><br>Wait for the saved message before sharing a log.</html>"), BorderLayout.NORTH);
        JButton open = new JButton("Open logs folder");
        open.setToolTipText(directory.toString());
        content.add(open, BorderLayout.CENTER);
        JLabel status = new JLabel(" ");
        content.add(status, BorderLayout.SOUTH);
        add(content, BorderLayout.NORTH);
        open.addActionListener(event -> {
            open.setEnabled(false);
            new SwingWorker<Void, Void>()
            {
                @Override protected Void doInBackground() throws Exception
                {
                    Files.createDirectories(directory);
                    LinkBrowser.open(directory.toString());
                    return null;
                }
                @Override protected void done()
                {
                    open.setEnabled(true);
                    try { get(); status.setText(" "); }
                    catch (Exception failure) { status.setText("Could not open the logs folder."); }
                }
            }.execute();
        });
    }

    static BufferedImage icon()
    {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(222, 169, 75));
            g.fillPolygon(new int[]{5, 18, 22, 11, 2}, new int[]{3, 3, 10, 23, 10}, 5);
            g.setColor(new Color(255, 221, 151));
            g.fillPolygon(new int[]{5, 12, 11, 2}, new int[]{3, 9, 23, 10}, 4);
            g.setColor(new Color(142, 92, 36));
            g.fillPolygon(new int[]{18, 22, 11, 12}, new int[]{3, 10, 23, 9}, 4);
        }
        finally { g.dispose(); }
        return image;
    }
}
