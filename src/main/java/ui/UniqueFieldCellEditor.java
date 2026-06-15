package ui;

import model.VariableAction;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;

/**
 * Редактор ячейки "Method" для таблицы уникальных полей в Edit DTO.
 * Левая часть: текстовое поле (отображает текущий метод + аргумент).
 * Правая часть: кнопка "➡️" открывает popup выбора метода.
 * После выбора addUuid — предлагает ввести аргумент (префикс).
 */
public class UniqueFieldCellEditor extends AbstractCellEditor implements TableCellEditor {

	private final JPanel panel = new JPanel(new BorderLayout());
	private final JTextField textField = new JTextField();
	private final JButton selectMethodBtn = new JButton("➡️");

	public UniqueFieldCellEditor() {
		Dimension btnSize = new Dimension(28, 22);
		selectMethodBtn.setPreferredSize(btnSize);
		selectMethodBtn.setMinimumSize(btnSize);
		selectMethodBtn.setMaximumSize(btnSize);
		selectMethodBtn.setMargin(new Insets(0, 0, 0, 0));
		selectMethodBtn.setFocusable(false);

		panel.add(textField, BorderLayout.CENTER);
		panel.add(selectMethodBtn, BorderLayout.EAST);

		JPopupMenu popup = new JPopupMenu();
		for (VariableAction action : VariableAction.values()) {
			JMenuItem item = new JMenuItem(action.getCode());
			item.addActionListener(e -> applyMethod(action));
			popup.add(item);
		}

		selectMethodBtn.addActionListener(e ->
				popup.show(selectMethodBtn, 0, selectMethodBtn.getHeight())
		);
	}

	private void applyMethod(VariableAction action) {
		if (action == VariableAction.ADD_UUID) {
			// для addUuid спрашиваем аргумент (префикс)
			String current = textField.getText();
			// пытаемся вытащить старый аргумент
			String defaultArg = "";
			if (current.startsWith("addUuid(") && current.endsWith(")")) {
				defaultArg = current.substring(8, current.length() - 1);
			}
			String arg = JOptionPane.showInputDialog(
					panel,
					"Введите префикс (аргумент addUuid):",
					defaultArg
			);
			if (arg == null) return; // отмена
			textField.setText("addUuid(" + arg + ")");
		} else {
			textField.setText(action.getCode() + "()");
		}
		stopCellEditing();
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