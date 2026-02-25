package ui;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.function.Consumer;

public class SelectorCellEditor extends AbstractCellEditor implements TableCellEditor {
	private final JPanel panel = new JPanel(new BorderLayout());
	private final JTextField textField = new JTextField();
	private final JButton pickButton = new JButton("🔍");

	private LocatorPicker locatorPicker;

	public SelectorCellEditor() {
		panel.add(textField, BorderLayout.CENTER);
		panel.add(pickButton, BorderLayout.EAST);

		pickButton.addActionListener(e -> {
			if (locatorPicker == null) {
				return;
			}
			locatorPicker.pick(xpath -> {
				if (xpath != null) {
					textField.setText(xpath);
				}
				stopCellEditing();
			});
		});
	}

	public void setLocatorPicker(LocatorPicker locatorPicker) {
		this.locatorPicker = locatorPicker;
	}

	@Override
	public Object getCellEditorValue() {
		return textField.getText();
	}

	@Override
	public Component getTableCellEditorComponent(
			JTable table, Object value, boolean isSelected, int row, int column) {
		textField.setText(value == null ? "" : value.toString());
		return panel;
	}
}
