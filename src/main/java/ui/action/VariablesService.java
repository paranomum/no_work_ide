package ui.action;

import dto.LocalVariables;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public class VariablesService {

	private final Map<String, LocalVariables> variables = new HashMap<>();

	private JTable variablesTable;
	private DefaultTableModel variablesTableModel;

	public void addVariable(String name, String value, String method) {
		variables.put(name, new LocalVariables(name, value, method));
	}

	public void addVariable(String name, String value) {
		String method = null;
		String raw = value;

		if (value != null &&
			(value.startsWith("generateEmail(") ||
			 value.startsWith("generatePhoneNumber(") ||
			 value.startsWith("addUuid("))) {

			int idx = value.indexOf('(');
			int last = value.lastIndexOf(')');
			if (idx > 0 && last > idx) {
				method = value.substring(idx == -1 ? 0 : 0, idx);
				raw = value.substring(idx + 1, last);
			}
		}

		variables.put(name, new LocalVariables(name, raw, method));
	}

	// перегрузка, если уже есть готовый объект
	public void addVariable(LocalVariables variable) {
		variables.put(variable.getName(), variable);
	}

	// получить текущий список переменных (read‑only)
	public List<LocalVariables> getVariables() {
		return Collections.unmodifiableList(new ArrayList<>(variables.values()));
	}


	public List<String> getVariableNames() {
		return variables.keySet().stream().toList();
	}

	public String getVariableValueByNameFormatted(String variable) {
		LocalVariables var = variables.get(variable);
		if (var.getMethod() != null && !var.getMethod().equals("addUuid"))
			return var.getMethod() + "()";
		else if (var.getMethod() != null)
			return "addUuid(" + var.getValue() + ")";
		else
			return var.getValue();
	}

	public String getVariableValueByName(String variable) {
		LocalVariables var = variables.get(variable.substring(2, variable.length() - 1));
		if (var.getMethod() != null && !var.getMethod().equals("addUuid"))
			return var.getMethod() + "()";
		else if (var.getMethod() != null)
			return "addUuid(" + var.getValue() + ")";
		else
			return var.getValue();
	}

	public void clear() {
		variables.clear();
	}

	public JPanel createVariablesSettingsPanel(JDialog parentDialog) {
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		String[] cols = {"Variable Name", "Value"};
		variablesTableModel = new DefaultTableModel(cols, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return true;
			}
		};
		variablesTable = new JTable(variablesTableModel);

		variablesTable.setRowHeight(24);
		variablesTable.setShowHorizontalLines(true);
		variablesTable.setShowVerticalLines(true);
		variablesTable.setGridColor(new Color(180, 180, 180));
		variablesTable.setIntercellSpacing(new Dimension(1, 1));
		variablesTable.setFillsViewportHeight(true);

		JScrollPane scroll = new JScrollPane(variablesTable);
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
				variablesTableModel.addRow(new Object[]{"", ""})
		);
		removeBtn.addActionListener(e -> {
			int row = variablesTable.getSelectedRow();
			if (row >= 0) {
				variablesTableModel.removeRow(row);
			}
		});

		top.add(new JLabel("Variables:"));
		top.add(addBtn);
		top.add(removeBtn);
		panel.add(top, BorderLayout.NORTH);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));

		JButton saveBtn = new JButton("Save");
		saveBtn.addActionListener(e -> {
			if (variablesTable.isEditing()) {
				variablesTable.getCellEditor().stopCellEditing();
			}
			saveVariables(parentDialog);
		});

		bottom.add(saveBtn);
		panel.add(bottom, BorderLayout.SOUTH);

		loadVariablesIntoTable();

		return panel;
	}

	private void loadVariablesIntoTable() {
		variablesTableModel.setRowCount(0);
		for (LocalVariables v : variables.values()) {
			String display;
			if (v.getMethod() != null && !"addUuid".equals(v.getMethod())) {
				display = v.getMethod() + "()";
			} else if ("addUuid".equals(v.getMethod())) {
				display = "addUuid(" + v.getValue() + ")";
			} else {
				display = v.getValue();
			}
			variablesTableModel.addRow(new Object[]{v.getName(), display});
		}
	}

	private void saveVariables(JDialog parentDialog) {
		variables.clear(); // сбрасываем старые

		for (int row = 0; row < variablesTableModel.getRowCount(); row++) {
			String name = Objects.toString(variablesTableModel.getValueAt(row, 0), "").trim();
			String value = Objects.toString(variablesTableModel.getValueAt(row, 1), "").trim();

			if (name.isEmpty()) {
				continue; // игнорим пустые
			}

			addVariable(name, value);
		}

		JOptionPane.showMessageDialog(
				parentDialog,
				"Variables saved",
				"Saved",
				JOptionPane.INFORMATION_MESSAGE
		);
	}
}
