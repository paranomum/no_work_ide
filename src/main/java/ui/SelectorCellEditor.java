package ui;

import lombok.Setter;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.function.Consumer;

public class SelectorCellEditor extends AbstractCellEditor implements TableCellEditor {
	private final JPanel panel = new JPanel(new BorderLayout());
	private final JTextField textField = new JTextField();
	private final JButton pickButton = new JButton("\uD83C\uDFAF");
	private final JButton highlightButton = new JButton("🔍");

	@Setter
	private LocatorPicker locatorPicker;
	@Setter
	private LocatorHighlighter locatorHighlighter;

	public SelectorCellEditor() {
		JPanel buttons =  new JPanel(new GridLayout(1, 2, 3, 0));
		buttons.add(pickButton);
		buttons.add(highlightButton);

		panel.add(textField, BorderLayout.CENTER);
		panel.add(buttons, BorderLayout.EAST);

		pickButton.addActionListener(e -> {
			if (locatorPicker == null) return;
			locatorPicker.pick(xpath -> {
				if (xpath != null) {
					textField.setText(xpath);
				}
				stopCellEditing();
			});
		});

		highlightButton.addActionListener(e -> {
			if (locatorHighlighter == null) return;
			String xpath = textField.getText();
			locatorHighlighter.highlight(xpath);
		});
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
