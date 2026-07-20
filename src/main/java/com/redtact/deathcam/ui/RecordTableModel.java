package com.redtact.deathcam.ui;

import com.redtact.deathcam.core.DeathRecord;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Read-only table model over a list of {@link DeathRecord}. */
public final class RecordTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"時刻", "World", "Phase", "死因", "IGT", "Clip"};

    private List<DeathRecord> records = new ArrayList<>();

    public void setRecords(List<DeathRecord> records) {
        this.records = records != null ? new ArrayList<>(records) : new ArrayList<>();
        fireTableDataChanged();
    }

    public DeathRecord getRecordAt(int row) {
        return records.get(row);
    }

    @Override
    public int getRowCount() {
        return records.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DeathRecord r = records.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return Formats.dateTime(r.detectedAtMillis);
            case 1:
                return r.worldName != null ? r.worldName : "";
            case 2:
                return r.phase != null ? r.phase : "";
            case 3: {
                String cause = r.cause != null ? r.cause : "";
                return r.killer != null ? cause + " ← " + r.killer : cause;
            }
            case 4:
                return Formats.mmss(r.igtAtDeathMillis);
            case 5:
                return r.clipPath != null ? "✔" : "";
            default:
                return "";
        }
    }
}
