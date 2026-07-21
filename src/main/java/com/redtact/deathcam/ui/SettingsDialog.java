package com.redtact.deathcam.ui;

import com.redtact.deathcam.config.AppConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;

/** Modal dialog that edits an {@link AppConfig} in place and saves on OK. */
public final class SettingsDialog extends JDialog {

    /** Recommended latency headroom between pre+post and the OBS buffer (mirrors DeathCamApp). */
    private static final int BUFFER_MARGIN_SECONDS = 5;

    private static final String[] RES_PRESETS = {"そのまま", "1280x720", "854x480", "640x360"};

    private final AppConfig config;
    private final Runnable onSaved;
    private final java.util.function.IntSupplier obsBufferSeconds;
    private final java.util.function.Supplier<String> obsBaseRes;
    private final java.util.function.Supplier<String> obsClipResStatus;

    private final JTextField obsHostField;
    private final JSpinner obsPortSpinner;
    private final JPasswordField obsPasswordField;
    private final JSpinner preRollSpinner;
    private final JSpinner postRollSpinner;
    private final JCheckBox autoStartBufferCheck;
    private final JLabel bufferHintLabel = new JLabel(" ");
    private final JComboBox<String> clipResCombo;
    private final JLabel resHintLabel = new JLabel(" ");
    private final JCheckBox retentionCheck;
    private final JSpinner maxGbSpinner;
    private final JCheckBox skipHungerResetCheck;
    private final JCheckBox recordRankedCheck;
    private final JCheckBox recordPrivateCheck;
    private final JCheckBox recordOtherCheck;
    private final JTextField libraryDirField;

    public SettingsDialog(Window owner, AppConfig config, Runnable onSaved) {
        this(owner, config, onSaved, () -> -1, () -> null, () -> null);
    }

    public SettingsDialog(Window owner, AppConfig config, Runnable onSaved,
                          java.util.function.IntSupplier obsBufferSeconds,
                          java.util.function.Supplier<String> obsBaseRes,
                          java.util.function.Supplier<String> obsClipResStatus) {
        super(owner, "設定", ModalityType.APPLICATION_MODAL);
        this.config = config;
        this.onSaved = onSaved;
        this.obsBufferSeconds = obsBufferSeconds;
        this.obsBaseRes = obsBaseRes;
        this.obsClipResStatus = obsClipResStatus;

        obsHostField = new JTextField(config.obsHost, 20);
        obsPortSpinner = new JSpinner(new SpinnerNumberModel(config.obsPort, 1, 65535, 1));
        obsPasswordField = new JPasswordField(config.obsPassword, 20);
        preRollSpinner = new JSpinner(new SpinnerNumberModel(config.preRollSeconds, 1, 3600, 1));
        postRollSpinner = new JSpinner(new SpinnerNumberModel(config.postRollSeconds, 0, 3600, 1));
        autoStartBufferCheck = new JCheckBox("リプレイバッファを自動起動", config.autoStartReplayBuffer);
        preRollSpinner.addChangeListener(e -> updateBufferHint());
        postRollSpinner.addChangeListener(e -> updateBufferHint());
        clipResCombo = new JComboBox<>(RES_PRESETS);
        clipResCombo.setEditable(true);
        selectClipRes();
        clipResCombo.addActionListener(e -> updateResHint());
        retentionCheck = new JCheckBox("合計サイズ上限を超えたら古い動画を削除 (メタデータは保持)",
                config.retentionEnabled);
        maxGbSpinner = new JSpinner(new SpinnerNumberModel(config.maxLibraryGb, 0.1, 10000.0, 0.5));
        skipHungerResetCheck = new JCheckBox("ハンガーリセット時はスキップ", config.skipHungerReset);
        recordRankedCheck = new JCheckBox("ランクマを記録 (type 2)", config.recordRanked);
        recordPrivateCheck = new JCheckBox("プライベートを記録 (type 3)", config.recordPrivate);
        recordOtherCheck = new JCheckBox("その他ワールドを記録 (練習/シングル)", config.recordOther);
        libraryDirField = new JTextField(config.libraryDir != null ? config.libraryDir : "", 20);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        int row = 0;
        addRow(form, row++, "OBS Host:", obsHostField);
        addRow(form, row++, "OBS Port:", obsPortSpinner);
        addRow(form, row++, "OBS Password:", obsPasswordField);
        addRow(form, row++, "Pre-roll (s):", preRollSpinner);
        addRow(form, row++, "Post-roll (s):", postRollSpinner);
        addRow(form, row++, "", autoStartBufferCheck);
        addRow(form, row++, "", bufferHintLabel);
        addRow(form, row++, "clip 解像度:", clipResCombo);
        addRow(form, row++, "", resHintLabel);
        addRow(form, row++, "容量削減:", retentionCheck);
        addRow(form, row++, "上限 (GB):", maxGbSpinner);
        addRow(form, row++, "", skipHungerResetCheck);
        addRow(form, row++, "録画対象:", recordRankedCheck);
        addRow(form, row++, "", recordPrivateCheck);
        addRow(form, row++, "", recordOtherCheck);
        updateBufferHint();
        updateResHint();

        JPanel libraryPanel = new JPanel();
        libraryPanel.setLayout(new BoxLayout(libraryPanel, BoxLayout.X_AXIS));
        libraryPanel.add(libraryDirField);
        libraryPanel.add(Box.createHorizontalStrut(5));
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseLibraryDir());
        libraryPanel.add(browseButton);
        addRow(form, row++, "Library Dir:", libraryPanel);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> saveAndClose());
        JButton cancelButton = new JButton("キャンセル");
        cancelButton.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okButton);
        buttons.add(cancelButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okButton);
        pack();
        setLocationRelativeTo(owner);
    }

    private static void addRow(JPanel form, int row, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_END;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        form.add(field, c);
    }

    private void selectClipRes() {
        String cur = config.clipRescaleRes == null ? "" : config.clipRescaleRes.trim();
        if (cur.isEmpty()) {
            clipResCombo.setSelectedItem("そのまま");
        } else {
            clipResCombo.setSelectedItem(cur);   // editable combo accepts custom "WxH"
        }
    }

    private String selectedClipRes() {
        Object v = clipResCombo.getSelectedItem();
        String s = v == null ? "" : v.toString().trim();
        return ("そのまま".equals(s) || s.isEmpty()) ? "" : s;
    }

    /**
     * Hint under the clip-resolution combo. The first line is neutral guidance; the second line
     * reflects OBS's ACTUAL last apply status (green when applied, amber on a real problem) so it
     * isn't a static warning that never clears.
     */
    private void updateResHint() {
        String base = obsBaseRes.get();
        String sel = selectedClipRes();
        String state = obsClipResStatus.get();

        StringBuilder sb = new StringBuilder("<html><div style='width:420px'>");
        if (sel.isEmpty()) {
            sb.append("OBS の解像度そのまま");
            if (base != null) {
                sb.append("（OBS base ").append(base).append("）");
            }
        } else {
            sb.append("clip のみ ").append(sel).append(" で録画（配信は不変）。要: OBS 詳細出力モード＋録画エンコーダを配信と別に");
            if (base != null) {
                sb.append("。base ").append(base).append(" 以下のみ");
            }
        }
        if (state != null && !state.isBlank()) {
            sb.append("<br>OBS 現在: ").append(state);
        }
        sb.append("</div></html>");
        resHintLabel.setText(sb.toString());

        java.awt.Color color = new java.awt.Color(0x8A8781);   // neutral guidance
        if (state != null) {
            if (state.startsWith("⚠")) {
                color = new java.awt.Color(0xE5B64F);
            } else if (state.startsWith("✓")) {
                color = new java.awt.Color(0x5DA84E);
            }
        }
        resHintLabel.setForeground(color);
    }

    /** Live hint under the roll spinners: compares pre+post against OBS's buffer length. */
    private void updateBufferHint() {
        int pre = ((Number) preRollSpinner.getValue()).intValue();
        int post = ((Number) postRollSpinner.getValue()).intValue();
        int window = pre + post;
        int obs = obsBufferSeconds.getAsInt();
        int recommended = window + BUFFER_MARGIN_SECONDS;
        if (obs <= 0) {
            bufferHintLabel.setText("<html>撮影窓 " + window + "s。OBS のリプレイバッファは "
                    + recommended + "s 以上を推奨</html>");
            bufferHintLabel.setForeground(new java.awt.Color(0x8A8781));
        } else if (obs < recommended) {
            bufferHintLabel.setText("<html>⚠ OBS バッファ " + obs + "s。撮影窓 " + window
                    + "s には余裕不足 — OBS を <b>" + recommended + "s 以上</b>に設定してください</html>");
            bufferHintLabel.setForeground(new java.awt.Color(0xE5B64F));
        } else {
            bufferHintLabel.setText("OBS バッファ " + obs + "s / 撮影窓 " + window + "s ✓");
            bufferHintLabel.setForeground(new java.awt.Color(0x5DA84E));
        }
    }

    private void browseLibraryDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = libraryDirField.getText().trim();
        if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            libraryDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void saveAndClose() {
        try {
            obsPortSpinner.commitEdit();
            preRollSpinner.commitEdit();
            postRollSpinner.commitEdit();
        } catch (java.text.ParseException ignored) {
            // keep last valid spinner values
        }
        config.obsHost = obsHostField.getText().trim();
        config.obsPort = ((Number) obsPortSpinner.getValue()).intValue();
        config.obsPassword = new String(obsPasswordField.getPassword());
        config.preRollSeconds = ((Number) preRollSpinner.getValue()).intValue();
        config.postRollSeconds = ((Number) postRollSpinner.getValue()).intValue();
        config.autoStartReplayBuffer = autoStartBufferCheck.isSelected();
        config.clipRescaleRes = selectedClipRes();
        config.retentionEnabled = retentionCheck.isSelected();
        config.maxLibraryGb = ((Number) maxGbSpinner.getValue()).doubleValue();
        config.skipHungerReset = skipHungerResetCheck.isSelected();
        config.recordRanked = recordRankedCheck.isSelected();
        config.recordPrivate = recordPrivateCheck.isSelected();
        config.recordOther = recordOtherCheck.isSelected();
        String library = libraryDirField.getText().trim();
        config.libraryDir = library.isEmpty() ? null : library;
        config.save();
        if (onSaved != null) {
            try {
                onSaved.run();
            } catch (RuntimeException e) {
                System.err.println("onSaved callback failed: " + e);
            }
        }
        dispose();
    }
}
