package com.redtact.deathcam.ui;

import com.redtact.deathcam.config.AppConfig;
import com.redtact.deathcam.core.DeathRecord;
import com.redtact.deathcam.core.DeathStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Main application window: status bar, death record table, action buttons. */
public final class MainWindow extends JFrame {

    private final AppConfig config;
    private final DeathStore store;
    private final RecordTableModel model = new RecordTableModel();
    private final JTable table = new JTable(model);
    private final JLabel obsLabel = new JLabel("OBS: -");
    private final JLabel worldLabel = new JLabel("World: -");
    private final JLabel bufferLabel = new JLabel("Buffer: -");
    private volatile Runnable dashboardOpener;

    /** Called by the pipeline once the embedded web server is up. */
    public void setDashboardOpener(Runnable opener) {
        this.dashboardOpener = opener;
    }

    public MainWindow(AppConfig config, DeathStore store) {
        super("mcsr-deathcam");
        this.config = config;
        this.store = store;

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        statusPanel.add(obsLabel);
        statusPanel.add(worldLabel);
        statusPanel.add(bufferLabel);

        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openClipAtPoint(e);
                }
            }
        });

        JButton dashboardButton = new JButton("ダッシュボードを開く");
        dashboardButton.addActionListener(e -> {
            Runnable r = dashboardOpener;
            if (r != null) {
                r.run();
            }
        });
        JButton settingsButton = new JButton("設定");
        settingsButton.addActionListener(e ->
                new SettingsDialog(this, config, this::refreshRecords).setVisible(true));
        JButton openFolderButton = new JButton("フォルダを開く");
        openFolderButton.addActionListener(e -> openLibraryFolder());
        JButton refreshButton = new JButton("更新");
        refreshButton.addActionListener(e -> refreshRecords());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        buttonPanel.add(dashboardButton);
        buttonPanel.add(settingsButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(refreshButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(statusPanel, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setPreferredSize(new Dimension(900, 500));
        pack();
        setLocationRelativeTo(null);
    }

    /** Installs FlatLaf; falls back silently to the default L&F. */
    public static void applyLookAndFeel() {
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
        } catch (Throwable t) {
            // default Swing look and feel is fine
        }
    }

    public void setObsStatus(String status) {
        SwingUtilities.invokeLater(() -> obsLabel.setText("OBS: " + status));
    }

    public void setWorldStatus(String status) {
        SwingUtilities.invokeLater(() -> worldLabel.setText("World: " + status));
    }

    public void setBufferStatus(String status) {
        SwingUtilities.invokeLater(() -> bufferLabel.setText("Buffer: " + status));
    }

    /** Reloads the table from the store. Safe to call from any thread. */
    public void refreshRecords() {
        SwingUtilities.invokeLater(() -> {
            try {
                List<DeathRecord> records = store.listRecent(200);
                model.setRecords(records);
            } catch (RuntimeException e) {
                System.err.println("failed to refresh records: " + e);
            }
        });
    }

    private void openClipAtPoint(MouseEvent e) {
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewRow < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        DeathRecord record = model.getRecordAt(modelRow);
        if (record.clipPath == null || !Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(new File(record.clipPath));
        } catch (Exception ex) {
            System.err.println("failed to open clip " + record.clipPath + ": " + ex);
        }
    }

    private void openLibraryFolder() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Path dir = config.libraryPath();
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception ex) {
            System.err.println("failed to open library folder: " + ex);
        }
    }
}
