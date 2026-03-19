package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public abstract class AbstractTableSettingsPanel {

	protected JTable table;
	protected DefaultTableModel model;

	protected JPanel buildTablePanel(
			String title,
			String[] columns,
			Runnable onSave
	) {
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		model = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return true;
			}
		};

		table = new JTable(model);
		table.setRowHeight(24);
		table.setShowHorizontalLines(true);
		table.setShowVerticalLines(true);
		table.setGridColor(new Color(180, 180, 180));
		table.setIntercellSpacing(new Dimension(1, 1));
		table.setFillsViewportHeight(true);

		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(new Color(150, 150, 150)),
						BorderFactory.createEmptyBorder(2, 2, 2, 2)
				)
		);
		panel.add(scroll, BorderLayout.CENTER);

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton addBtn = new JButton("+");
		JButton removeBtn = new JButton("-");

		addBtn.addActionListener(e ->
				model.addRow(new Object[columns.length])
		);
		removeBtn.addActionListener(e -> {
			int row = table.getSelectedRow();
			if (row >= 0) {
				model.removeRow(row);
			}
		});

		top.add(new JLabel(title + ":"));
		top.add(addBtn);
		top.add(removeBtn);
		panel.add(top, BorderLayout.NORTH);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveBtn = new JButton("Save");
		saveBtn.addActionListener(e -> {
			if (table.isEditing()) {
				table.getCellEditor().stopCellEditing();
			}
			onSave.run();
		});
		bottom.add(saveBtn);
		panel.add(bottom, BorderLayout.SOUTH);

		return panel;
	}
}

