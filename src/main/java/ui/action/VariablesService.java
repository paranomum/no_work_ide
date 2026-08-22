package ui.action;

import dto.LocalVariables;
import ui.AbstractTableSettingsPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.util.*;

import static ru.rt.iqhr.framework.util.StringUtils.*;

public class VariablesService extends AbstractTableSettingsPanel {

	private final Map<String, LocalVariables> variables = new LinkedHashMap<>();

	private JTable variablesTable;
	private DefaultTableModel variablesTableModel;

	public static String formatVariableDisplayName(String variableName) {
		if (variableName == null) return "";
		int dotIdx = variableName.indexOf('.');
		if (dotIdx > 0 && dotIdx < variableName.length() - 1) {
			String fieldPath = variableName.substring(dotIdx + 1);
			return variableName + " [json(" + fieldPath + ")]";
		}
		return variableName;
	}

	public void addVariable(String name, String value, String method) {
		if (name == null || name.isBlank()) {
			return;
		}
		putIfAbsentOrEmpty(new LocalVariables(name.trim(), value, method));
	}

	public void addVariable(String name, String value) {
		if (name == null || name.isBlank()) {
			return;
		}

		putIfAbsentOrEmpty(toLocalVariable(name, value));
	}

	public void addVariable(LocalVariables variable) {
		System.out.println("VariablesService.addVariable input = " + variable);

		if (variable == null
				|| variable.getName() == null
				|| variable.getName().isBlank()) {
			throw new IllegalArgumentException("Variable is null or has empty name");
		}

		putIfAbsentOrEmpty(variable);

		System.out.println(
				"VariablesService.addVariable map after putIfAbsentOrEmpty = " + variables
		);
	}

	/**
	 * Совместимость со старым вызовом.
	 */
	public void addVariableIfAbsent(LocalVariables variable) {
		putIfAbsentOrEmpty(variable);
	}

	/**
	 * Явное имя для загрузки дефолтов из custom method.
	 */
	public void addVariableIfAbsentOrEmpty(LocalVariables variable) {
		putIfAbsentOrEmpty(variable);
	}

	/**
	 * Единая политика добавления:
	 * - отсутствует ключ -> добавить;
	 * - ключ есть, но его значение пустое -> заменить дефолтом;
	 * - ключ есть и задан -> оставить пользовательское/сценарное значение.
	 */
	private void putIfAbsentOrEmpty(LocalVariables incoming) {
		if (incoming == null
				|| incoming.getName() == null
				|| incoming.getName().isBlank()) {
			return;
		}

		String name = incoming.getName().trim();
		LocalVariables existing = variables.get(name);

		if (existing == null || isEmptyVariable(existing)) {
			variables.put(
					name,
					new LocalVariables(name, incoming.getValue(), incoming.getMethod())
			);
		}
	}

	/**
	 * Распознаёт строковое отображение переменной и переводит его в DTO.
	 *
	 * generateEmail()       -> method=generateEmail,       value=""
	 * generatePhoneNumber() -> method=generatePhoneNumber, value=""
	 * addUuid(prefix)       -> method=addUuid,              value="prefix"
	 * обычное значение      -> method=null,                 value=значение
	 */
	private LocalVariables toLocalVariable(String name, String value) {
		String method = null;
		String raw = value;

		if (value != null
				&& (value.startsWith("generateEmail(")
				|| value.startsWith("generatePhoneNumber(")
				|| value.startsWith("addUuid("))) {

			int idx = value.indexOf('(');
			int last = value.lastIndexOf(')');

			if (idx > 0 && last > idx) {
				method = value.substring(0, idx);
				raw = value.substring(idx + 1, last);
			}
		}

		return new LocalVariables(name.trim(), raw, method);
	}

	/**
	 * Пустой считается только обычная переменная без method:
	 * null, "", пробелы.
	 *
	 * generateEmail(), generatePhoneNumber() и addUuid(...)
	 * не считаются пустыми: raw value у первых двух может быть "",
	 * но method означает, что значение определено.
	 */
	private boolean isEmptyVariable(LocalVariables variable) {
		if (variable == null) {
			return true;
		}

		String method = variable.getMethod();
		if (method != null && !method.isBlank()) {
			return false;
		}

		String value = variable.getValue();
		return value == null || value.isBlank();
	}

	private void stopTableEditing() {
		if (variablesTable != null && variablesTable.isEditing()) {
			TableCellEditor editor = variablesTable.getCellEditor();
			if (editor != null) {
				editor.stopCellEditing();
			}
		}
	}

	public void syncVariablesFromTable() {
		stopTableEditing();

		if (variablesTableModel == null) {
			return;
		}

		Map<String, LocalVariables> fromTable = new LinkedHashMap<>();

		for (int row = 0; row < variablesTableModel.getRowCount(); row++) {
			String name = Objects.toString(
					variablesTableModel.getValueAt(row, 0),
					""
			).trim();

			String value = Objects.toString(
					variablesTableModel.getValueAt(row, 1),
					""
			).trim();

			if (name.isEmpty()) {
				continue;
			}

			// ВАЖНО: здесь put, а не putIfAbsentOrEmpty.
			// Таблица — источник истины после ручного редактирования.
			fromTable.put(name, toLocalVariable(name, value));
		}

		variables.clear();
		variables.putAll(fromTable);
	}

	public List<LocalVariables> getVariables() {
		return Collections.unmodifiableList(new ArrayList<>(variables.values()));
	}

	public List<String> getVariableNames() {
		return variables.keySet().stream().sorted().toList();
	}

	public String getVariableValueByNameFormatted(String variable) {
		LocalVariables var = variables.get(variable);
		if (var == null) {
			throw new IllegalArgumentException("Variable not found: " + variable);
		}

		if (var.getMethod() != null && !"addUuid".equals(var.getMethod())) {
			return var.getMethod() + "()";
		} else if ("addUuid".equals(var.getMethod())) {
			return "addUuid(" + (var.getValue() != null ? var.getValue() : "") + ")";
		} else {
			return var.getValue();
		}
	}

	public String getVariableValueByName(String variable) {
		String variableName = variable;

		if (variableName != null
				&& variableName.startsWith("${")
				&& variableName.endsWith("}")) {
			variableName = variableName.substring(2, variableName.length() - 1);
		}

		LocalVariables var = variables.get(variableName);
		if (var == null) {
			throw new IllegalArgumentException("Variable not found: " + variableName);
		}

		if (var.getMethod() != null && !"addUuid".equals(var.getMethod())) {
			return var.getMethod() + "()";
		} else if ("addUuid".equals(var.getMethod())) {
			return "addUuid(" + (var.getValue() != null ? var.getValue() : "") + ")";
		} else {
			return var.getValue();
		}
	}

	public void clear() {
		System.out.println("VariablesService.clear BEFORE = " + variables);

		variables.clear();

		if (variablesTableModel != null) {
			variablesTableModel.setRowCount(0);
			variablesTableModel.fireTableDataChanged();
		}

		if (variablesTable != null) {
			variablesTable.revalidate();
			variablesTable.repaint();
		}

		System.out.println("VariablesService.clear AFTER = " + variables);
	}

	public JPanel createVariablesSettingsPanel(JDialog parentDialog) {
		JPanel panel = buildTablePanel(
				"Variables",
				new String[]{"Variable Name", "Value"},
				() -> saveVariables(parentDialog),
				null
		);

		this.variablesTable = this.table;
		this.variablesTableModel = this.model;

		loadVariablesIntoTable();
		return panel;
	}

	private void loadVariablesIntoTable() {
		if (variablesTableModel == null) {
			return;
		}

		variablesTableModel.setRowCount(0);

		for (LocalVariables v : variables.values()) {
			String display;

			if (v.getMethod() != null && !"addUuid".equals(v.getMethod())) {
				display = v.getMethod() + "()";
			} else if ("addUuid".equals(v.getMethod())) {
				display = "addUuid(" + (v.getValue() != null ? v.getValue() : "") + ")";
			} else {
				display = v.getValue();
			}

			variablesTableModel.addRow(new Object[]{v.getName(), display});
		}

		variablesTableModel.fireTableDataChanged();
	}

	public void refreshTableFromVariables() {
		loadVariablesIntoTable();

		if (variablesTable != null) {
			variablesTable.revalidate();
			variablesTable.repaint();
		}
	}

	public Map<String, String> buildAllVariableValuesMap() {
		Map<String, String> result = new LinkedHashMap<>();

		for (LocalVariables v : variables.values()) {
			String name = v.getName();

			if (name == null || name.isBlank()) {
				continue;
			}

			String base;

			if (v.getMethod() != null && !"addUuid".equals(v.getMethod())) {
				base = v.getMethod() + "()";
			} else if ("addUuid".equals(v.getMethod())) {
				base = "addUuid(" + (v.getValue() != null ? v.getValue() : "") + ")";
			} else {
				base = v.getValue();
			}

			String resolved = resolveValue(base, result);
			result.put(name, resolved);
		}

		return result;
	}

	private void saveVariables(JDialog parentDialog) {
		syncVariablesFromTable();

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

		String value = rawValue.trim();

		while (value.contains("${") && value.contains("}")) {
			int start = value.indexOf("${");
			int end = value.indexOf("}", start + 2);

			if (start < 0 || end <= start + 2) {
				break;
			}

			String varName = value.substring(start + 2, end);

			if (!nameToValue.containsKey(varName)) {
				String formatted = getVariableValueByNameFormatted(varName);

				if (formatted == null) {
					throw new IllegalArgumentException(
							"Variable '" + varName + "' resolved to null"
					);
				}

				String resolvedFormatted = resolveValue(formatted, nameToValue);
				nameToValue.put(varName, resolvedFormatted);
			}

			String resolvedValue = nameToValue.get(varName);
			value = value.substring(0, start)
					+ resolvedValue
					+ value.substring(end + 1);
		}

		if (value.startsWith("addUuid(") && value.endsWith(")")) {
			String arg = value.substring(8, value.length() - 1);
			String resolvedArg = resolveValue(arg, nameToValue);
			return addUuid(resolvedArg != null ? resolvedArg : "");
		} else if (value.equals("generatePhoneNumber()")) {
			return generatePhoneNumber();
		} else if (value.equals("generateEmail()")) {
			return generateEmail();
		}

		return value;
	}

	/**
	 * Удаляет переменную по имени из внутреннего Map.
	 */
	public void removeVariable(String name) {
		if (name == null || name.isBlank()) {
			return;
		}

		variables.remove(name.trim());
	}
}