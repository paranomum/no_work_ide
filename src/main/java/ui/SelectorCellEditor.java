package ui;

import ui.action.ActionRecorder;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;

public class SelectorCellEditor extends AbstractCellEditor implements TableCellEditor {

	private final DefaultTableModel tableModel;
	private final ActionRecorder actionRecorder;
	private final JTextField textField;
	private final JButton pickButton;
	private final JPanel panel;
	private int currentRow = -1;

	public SelectorCellEditor(DefaultTableModel tableModel, ActionRecorder actionRecorder) {
		this.tableModel = tableModel;
		this.actionRecorder = actionRecorder;

		textField = new JTextField();
		textField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

		pickButton = new JButton("\uD83D\uDD0D");
		pickButton.setPreferredSize(new Dimension(28, 28));
		pickButton.setFocusable(false);
		pickButton.setToolTipText("Pick element from page");
		pickButton.addActionListener(e -> startPick());

		panel = new JPanel(new BorderLayout());
		panel.add(textField, BorderLayout.CENTER);
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
		currentRow = row;
		String text = value != null ? value.toString() : "";
		textField.setText(text);

		panel.removeAll();
		panel.add(textField, BorderLayout.CENTER);

		if (text.isEmpty()) {
			panel.add(pickButton, BorderLayout.EAST);
		}

		return panel;
	}

	@Override
	public Object getCellEditorValue() {
		return textField.getText();
	}

	private void startPick() {
		pickButton.setEnabled(false);

		actionRecorder.startLocatorPick(xpath -> {
			SwingUtilities.invokeLater(() -> {
				textField.setText(xpath);
				tableModel.setValueAt(xpath, currentRow, 2);
				pickButton.setEnabled(true);
				fireEditingStopped();
			});
		});
	}
}