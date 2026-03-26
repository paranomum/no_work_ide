package ui.action;

import dto.LocalVariables;
import ui.AbstractTableSettingsPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

import static ru.rt.iqhr.framework.util.StringUtils.*;

public class VariablesService extends AbstractTableSettingsPanel {

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
		JPanel panel = buildTablePanel(
				"Variables",
				new String[] {"Variable Name", "Value"},
				() -> saveVariables(parentDialog),
				null
		);

		// связываем наследуемые поля с нашими
		this.variablesTable = this.table;
		this.variablesTableModel = this.model;

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

	public Map<String, String> buildAllVariableValuesMap() {
		Map<String, String> result = new HashMap<>();

		for (LocalVariables v : getVariables()) {
			String name = v.getName();
			if (name == null || name.isBlank()) {
				continue;
			}

			String base;
			if (v.getMethod() != null && !"addUuid".equals(v.getMethod())) {
				base = v.getMethod() + "()";
			} else if ("addUuid".equals(v.getMethod())) {
				base = "addUuid(" + v.getValue() + ")";
			} else {
				base = v.getValue();
			}

			String resolved = resolveValue(base, result);
			result.put(name, resolved);
		}

		return result;
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

	public String resolveValue(String rawValue, Map<String, String> nameToValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return rawValue;
		}

		String value = rawValue;

		// ${varName}
		if (value.startsWith("${") && value.endsWith("}")) {
			String varName = value.substring(2, value.length() - 1);

			// если не было ещё посчитано, берём базовое форматированное значение
			if (!nameToValue.containsKey(varName)) {
				String formatted = getVariableValueByNameFormatted(varName);
				nameToValue.put(varName, formatted);
			}

			value = nameToValue.get(varName);
		}

		// дальше — интерпретация специальных методов
		if (value.startsWith("addUuid(") && value.endsWith(")")) {
			String arg = value.substring(8, value.length() - 1);
			return addUuid(arg); // твой util
		} else if (value.contains("generatePhoneNumber()")) {
			return generatePhoneNumber(); // твой util
		} else if (value.contains("generateEmail()")) {
			return generateEmail(); // твой util
		}

		return value;
	}
}
