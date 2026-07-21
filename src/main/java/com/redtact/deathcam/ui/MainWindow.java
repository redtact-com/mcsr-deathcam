package com.redtact.deathcam.ui;

import com.redtact.deathcam.config.AppConfig;
import com.redtact.deathcam.core.DeathRecord;
import com.redtact.deathcam.core.DeathStore;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact desktop companion window in the spirit of Ninjabrain Bot / Julti:
 * a small dark utility panel showing live status plus quick actions. Rich
 * browsing lives in the web dashboard, so this window stays lean.
 */
public final class MainWindow extends JFrame {

    private static final Color ACCENT = new Color(0xE0584F);   // recorder red (brand)
    private static final Color OK = new Color(0x5DA84E);
    private static final Color WARN = new Color(0xE5B64F);
    private static final Color BAD = new Color(0xC23F37);
    private static final Color MUTED = new Color(0x6B6A72);

    private final AppConfig config;
    private final DeathStore store;

    private final StatusDot obsDot = new StatusDot();
    private final JLabel obsValue = valueLabel("—");
    private final StatusDot worldDot = new StatusDot();
    private final JLabel worldValue = valueLabel("待機中");
    private final StatusDot recDot = new StatusDot();
    private final JLabel recValue = valueLabel("—");
    private final JLabel footer = new JLabel(" ");

    private volatile Runnable dashboardOpener;
    private volatile java.util.function.IntSupplier obsBufferSupplier = () -> -1;

    /** Called by the pipeline once the embedded web server is up. */
    public void setDashboardOpener(Runnable opener) {
        this.dashboardOpener = opener;
    }

    /** Live source of OBS's replay-buffer length (seconds), for the settings dialog. */
    public void setObsBufferSupplier(java.util.function.IntSupplier supplier) {
        this.obsBufferSupplier = supplier;
    }

    /** Show OBS's current replay-buffer length as a hint on the recording row. */
    public void setObsBufferSeconds(int seconds) {
        SwingUtilities.invokeLater(() -> recValue.setToolTipText(
                seconds > 0 ? "OBS リプレイバッファ: " + seconds + "s" : null));
    }

    public MainWindow(AppConfig config, DeathStore store) {
        super("DEATHCAM");
        this.config = config;
        this.store = store;

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        root.add(buildStatusPanel());
        root.add(Box.createVerticalStrut(14));
        root.add(buildActions());
        root.add(Box.createVerticalStrut(10));
        root.add(buildFooter());

        setContentPane(root);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(340, 0));
        pack();
        setSize(Math.max(360, getWidth()), getHeight());
        setLocationRelativeTo(null);
    }

    private JPanel buildStatusPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        addStatusRow(p, 0, obsDot, "OBS", obsValue);
        addStatusRow(p, 1, worldDot, "ワールド", worldValue);
        addStatusRow(p, 2, recDot, "録画", recValue);
        return p;
    }

    private static void addStatusRow(JPanel p, int row, StatusDot dot, String name, JLabel value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(3, 0, 3, 0);

        c.gridx = 0;
        c.anchor = GridBagConstraints.CENTER;
        p.add(dot, c);

        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(3, 10, 3, 12);
        JLabel key = new JLabel(name);
        key.setForeground(MUTED);
        key.setFont(key.getFont().deriveFont(Font.PLAIN, 11f));
        key.setPreferredSize(new Dimension(64, key.getPreferredSize().height));
        p.add(key, c);

        c.gridx = 2;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 0);
        p.add(value, c);
    }

    private JPanel buildActions() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(LEFT_ALIGNMENT);

        JButton dashboard = new JButton("▶  ダッシュボードを開く");
        dashboard.putClientProperty("JButton.buttonType", "roundRect");
        dashboard.setBackground(ACCENT);
        dashboard.setForeground(Color.WHITE);
        dashboard.setFocusPainted(false);
        dashboard.setFont(dashboard.getFont().deriveFont(Font.BOLD));
        dashboard.setAlignmentX(LEFT_ALIGNMENT);
        dashboard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        dashboard.addActionListener(e -> {
            Runnable r = dashboardOpener;
            if (r != null) {
                r.run();
            }
        });
        col.add(dashboard);
        col.add(Box.createVerticalStrut(8));

        JButton settings = ghostButton("設定");
        settings.addActionListener(e ->
                new SettingsDialog(this, config, this::refreshRecords, obsBufferSupplier).setVisible(true));
        JButton folder = ghostButton("フォルダ");
        folder.addActionListener(e -> openLibraryFolder());
        JButton refresh = ghostButton("更新");
        refresh.addActionListener(e -> refreshRecords());

        JCheckBox pin = new JCheckBox("最前面");
        pin.setOpaque(false);
        pin.setFont(pin.getFont().deriveFont(11f));
        pin.addActionListener(e -> setAlwaysOnTop(pin.isSelected()));

        JPanel rowPanel = new JPanel();
        rowPanel.setOpaque(false);
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
        rowPanel.setAlignmentX(LEFT_ALIGNMENT);
        rowPanel.add(settings);
        rowPanel.add(Box.createHorizontalStrut(6));
        rowPanel.add(folder);
        rowPanel.add(Box.createHorizontalStrut(6));
        rowPanel.add(refresh);
        rowPanel.add(Box.createHorizontalGlue());
        rowPanel.add(pin);
        col.add(rowPanel);
        return col;
    }

    private static JButton ghostButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setFocusPainted(false);
        return b;
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        footer.setForeground(MUTED);
        footer.setFont(footer.getFont().deriveFont(11f));
        footer.setHorizontalAlignment(SwingConstants.LEFT);
        p.add(footer, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return p;
    }

    private static JLabel valueLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12.5f));
        return l;
    }

    /** Installs a dark FlatLaf (Ninjabrain Bot / Julti aesthetic); silent fallback. */
    public static void applyLookAndFeel() {
        try {
            JFrame.setDefaultLookAndFeelDecorated(true);
            javax.swing.JDialog.setDefaultLookAndFeelDecorated(true);
            UIManager.put("Component.accentColor", ACCENT);
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("TitlePane.unifiedBackground", true);
            com.formdev.flatlaf.FlatDarculaLaf.setup();
            UIManager.put("Component.accentColor", ACCENT);
            com.formdev.flatlaf.FlatLaf.updateUI();
        } catch (Throwable t) {
            // default Swing look and feel is fine
        }
    }

    public void setObsStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            obsValue.setText(status);
            obsDot.setColor(obsColor(status));
        });
    }

    private static Color obsColor(String s) {
        if (contains(s, "未接続", "切断", "失敗", "エラー")) {
            return BAD;
        }
        if (contains(s, "未有効", "接続中", "待ち")) {
            return WARN;
        }
        if (s.contains("接続済み")) {
            return OK;
        }
        return MUTED;
    }

    public void setWorldStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            worldValue.setText(status);
            Color c = status.contains("待機") ? MUTED
                    : status.contains("記録オフ") ? WARN
                    : OK;
            worldDot.setColor(c);
        });
    }

    public void setBufferStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            recValue.setText(status);
            Color c = contains(status, "失敗") ? BAD
                    : status.contains("スキップ") ? MUTED
                    : status.contains("死亡検知") ? WARN
                    : status.contains("クリップ保存") ? ACCENT
                    : MUTED;
            recDot.setColor(c);
        });
    }

    private static boolean contains(String s, String... needles) {
        for (String n : needles) {
            if (s.contains(n)) {
                return true;
            }
        }
        return false;
    }

    /** Updates the footer stat from the store. Safe to call from any thread. */
    public void refreshRecords() {
        SwingUtilities.invokeLater(() -> {
            try {
                List<DeathRecord> records = store.listRecent(500);
                long midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                long today = records.stream().filter(r -> r.detectedAtMillis >= midnight).count();
                Map<String, Integer> phases = new HashMap<>();
                for (DeathRecord r : records) {
                    if (r.phase != null) {
                        phases.merge(r.phase, 1, Integer::sum);
                    }
                }
                String top = phases.entrySet().stream()
                        .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
                footer.setText("今日 " + today + " 死亡"
                        + (top != null ? "  ·  最多 " + top : "")
                        + "  ·  合計 " + records.size());
            } catch (RuntimeException e) {
                System.err.println("failed to refresh footer: " + e);
            }
        });
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

    /** Small filled circle used as a status indicator. */
    private static final class StatusDot extends JComponent {
        private Color color = MUTED;

        StatusDot() {
            setPreferredSize(new Dimension(10, 10));
        }

        void setColor(Color c) {
            this.color = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight());
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;
            g2.setColor(color);
            g2.fillOval(x, y, d, d);
            g2.dispose();
        }
    }
}
