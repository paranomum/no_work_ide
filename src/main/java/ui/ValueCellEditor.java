package ui;

import model.VariableAction;
import ui.action.VariablesService;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.function.Consumer;

public class ValueCellEditor extends AbstractCellEditor implements TableCellEditor {

	private final JPanel panel = new JPanel(new BorderLayout());
	private final JTextField textField = new JTextField();
	private final JButton pickVariableButton = new JButton("$");
	private final JButton createVariable = new JButton("+");
	private final JButton applyMethod = new JButton("➡️");

	private final VariablesService variablesService;

	private JTable table;

	public ValueCellEditor(JTable table, VariablesService variablesService) {
		this.variablesService = variablesService;
		this.table = table;

		initButtonsSize();
		initApplyButton();

		JPanel buttons = new JPanel(new GridLayout(1, 3, 3, 0));
		buttons.add(pickVariableButton);
		buttons.add(createVariable);
		buttons.add(applyMethod);

		panel.add(textField, BorderLayout.CENTER);
		panel.add(buttons, BorderLayout.EAST);

		pickVariableButton.addActionListener(e -> {
			java.util.List<String> names = variablesService.getVariableNames();
			if (names.isEmpty()) {
				JOptionPane.showMessageDialog(panel,
						"Нет доступных переменных",
						"Variables",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			String selected = (String) JOptionPane.showInputDialog(
					panel,
					"Выберите переменную:",
					"Variables",
					JOptionPane.PLAIN_MESSAGE,
					null,
					names.toArray(),
					names.get(0)
			);

			if (selected != null) {
				String expr = "${" + selected + "}";
				textField.setText(expr);

				if (table != null) {
					int row = table.getEditingRow();
					int col = table.getEditingColumn();
					if (row >= 0 && col >= 0) {
						table.setValueAt(expr, row, col);
					}
				}

				stopCellEditing();
			}
		});

		createVariable.addActionListener(e -> {
			// 1. имя переменной
			String name = JOptionPane.showInputDialog(
					panel,
					"Variable name:",
					"Create variable",
					JOptionPane.PLAIN_MESSAGE
			);
			if (name == null || name.isBlank()) {
				return; // отмена или пусто
			}

			// 2. берём текущее значение из поля
			String currentValue = textField.getText();
			if (currentValue == null || currentValue.isBlank()) {
				return;
			}

			variablesService.addVariable(name, currentValue);

			String expr = "${" + name + "}";
			textField.setText(expr);

			if (table != null) {
				int row = table.getEditingRow();
				int col = table.getEditingColumn();
				if (row >= 0 && col >= 0) {
					table.setValueAt(expr, row, col);
				}
			}

			stopCellEditing();
		});

	}

	@Override
	public Object getCellEditorValue() {
		return textField.getText();
	}

	@Override
	public Component getTableCellEditorComponent(
			JTable table, Object value, boolean isSelected, int row, int column) {

		this.table = table;

		textField.setText(value == null ? "" : value.toString());
		return panel;
	}

	private void initApplyButton() {

		Consumer<VariableAction> onVariableActionSelected = action -> {
			if (!action.getCode().contains("addUuid"))
				textField.setText(action.getCode() + "()");
			else {
				String old = textField.getText();
				textField.setText("addUuid(" + old + ")");
			}
			stopCellEditing();
		};

		JPopupMenu applyPopup = new JPopupMenu();

		for (VariableAction action : VariableAction.values()) {
			String text = action.getCode();
			JMenuItem item = new JMenuItem(text);

			item.addActionListener(e -> onVariableActionSelected.accept(action));

			applyPopup.add(item);
		}

		applyMethod.addActionListener(e -> {
			JButton btn = (JButton) e.getSource();
			applyPopup.show(btn, 0, btn.getHeight());
		});
	}

	private void initButtonsSize() {
		Dimension btnSize = new Dimension(24, 22); // подбери по вкусу

		for (JButton btn : new JButton[]{pickVariableButton, createVariable, applyMethod}) {
			btn.setMargin(new Insets(0, 0, 0, 0));
			btn.setFocusable(false);
			btn.setPreferredSize(btnSize);
			btn.setMinimumSize(btnSize);
			btn.setMaximumSize(btnSize);
		}
	}
}
