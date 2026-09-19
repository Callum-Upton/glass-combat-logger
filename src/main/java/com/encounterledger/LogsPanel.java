package com.encounterledger;

import java.awt.BorderLayout;



import java.awt.image.BufferedImage;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.PluginPanel;

/** Shows and copies the local recording path without opening external applications. */
final class LogsPanel extends PluginPanel
{
    LogsPanel(Path directory)
    {
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.add(new JLabel("<html><b>Zenyte</b><br><br>"
            + "Your recordings are saved on this computer.<br><br>"
            + "Regular logs are the default. For testing, use ::zenyte research on to capture research and fight logs together. Research resets OFF when the plugin restarts."
            + "<br><br>Wait for the saved message before sharing a log.</html>"), BorderLayout.NORTH);
        JPanel actions = new JPanel(new BorderLayout(0, 8));
        JTextField path = new JTextField(directory.toString());
        path.setEditable(false);
        path.setToolTipText(directory.toString());
        path.getAccessibleContext().setAccessibleName("Logs folder path");
        actions.add(path, BorderLayout.NORTH);
        JButton copy = new JButton("Copy folder path");
        actions.add(copy, BorderLayout.CENTER);
        content.add(actions, BorderLayout.CENTER);
        JLabel status = new JLabel("<html>Paste this path into your file manager.<br>The folder appears after your first save.</html>");
        content.add(status, BorderLayout.SOUTH);
        add(content, BorderLayout.NORTH);
        copy.addActionListener(event -> {
            try
            {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(directory.toString()), null);
                status.setText("Folder path copied.");
            }
            catch (RuntimeException failure)
            {
                path.requestFocusInWindow();
                path.selectAll();
                status.setText("<html>Clipboard unavailable.<br>Copy the selected path manually.</html>");
            }
        });
    }

    static BufferedImage icon()
    {
        return net.runelite.client.util.ImageUtil.loadImageResource(LogsPanel.class, "/zenyte-icon.png");
    }
}
