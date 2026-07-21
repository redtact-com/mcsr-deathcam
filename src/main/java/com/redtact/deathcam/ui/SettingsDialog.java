package com.redtact.deathcam.ui;

import com.redtact.deathcam.config.AppConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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

    private final AppConfig config;
    private final Runnable onSaved;

    private final JTextField obsHostField;
    private final JSpinner obsPortSpinner;
    private final JPasswordField obsPasswordField;
    private final JSpinner preRollSpinner;
    private final JSpinner postRollSpinner;
    private final JCheckBox skipHungerResetCheck;
    private final JCheckBox recordRankedCheck;
    private final JCheckBox recordPrivateCheck;
    private final JCheckBox recordOtherCheck;
    private final JTextField libraryDirField;

    public SettingsDialog(Window owner, AppConfig config, Runnable onSaved) {
        super(owner, "設定", ModalityType.APPLICATION_MODAL);
        this.config = config;
        this.onSaved = onSaved;

        obsHostField = new JTextField(config.obsHost, 20);
        obsPortSpinner = new JSpinner(new SpinnerNumberModel(config.obsPort, 1, 65535, 1));
        obsPasswordField = new JPasswordField(config.obsPassword, 20);
        preRollSpinner = new JSpinner(new SpinnerNumberModel(config.preRollSeconds, 1, 3600, 1));
        postRollSpinner = new JSpinner(new SpinnerNumberModel(config.postRollSeconds, 0, 3600, 1));
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
        addRow(form, row++, "", skipHungerResetCheck);
        addRow(form, row++, "録画対象:", recordRankedCheck);
        addRow(form, row++, "", recordPrivateCheck);
        addRow(form, row++, "", recordOtherCheck);

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
