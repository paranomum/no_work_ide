package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.ActionRecord;
import lombok.val;
import model.ElementType;
import model.UserAction;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ActionFileService {

	private final DefaultTableModel tableModel;
	private final JFrame parent;

	public ActionFileService(JFrame parent, DefaultTableModel tableModel) {
		this.parent = parent;
		this.tableModel = tableModel;
	}

	// --------- Публичный вход ---------

	public void saveWithModeDialog() {
		if (tableModel.getRowCount() == 0) {
			JOptionPane.showMessageDialog(
					parent,
					"Table is empty, nothing to save.",
					"Save Table",
					JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}

		String[] options = { "Test plan (JSON)", "Generated auto test (.java)", "Cancel" };
		int choice = JOptionPane.showOptionDialog(
				parent,
				"Что сохранить?",
				"Save",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[0]
		);

		if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
			return;
		}

		if (choice == 1) {
			saveGeneratedJava();
		} else {
			saveJsonPlan();
		}
	}

	// --------- JSON ---------

	private void saveJsonPlan() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save actions");
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
				"JSON files", "json"));

		int result = chooser.showSaveDialog(parent);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".json")) {
			file = new File(file.getParentFile(), file.getName() + ".json");
		}

		List<ActionRecord> rows = buildActionRecords();

		try (Writer writer = new OutputStreamWriter(
				new FileOutputStream(file), StandardCharsets.UTF_8)) {

			Gson gson = new GsonBuilder()
					.setPrettyPrinting()
					.create();
			gson.toJson(rows, writer);
			writer.flush();

			JOptionPane.showMessageDialog(
					parent,
					"Table saved to:\n" + file.getAbsolutePath(),
					"Save Successful",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					parent,
					"Failed to save table: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private List<ActionRecord> buildActionRecords() {
		List<ActionRecord> rows = new ArrayList<>();
		int rowCount = tableModel.getRowCount();

		for (int r = 0; r < rowCount; r++) {
			Object actionObj = tableModel.getValueAt(r, 1);
			String actionCode = null;
			if (actionObj instanceof UserAction) {
				actionCode = ((UserAction) actionObj).getCode();
			} else if (actionObj != null) {
				actionCode = actionObj.toString();
			}

			Object elementTypeObj = tableModel.getValueAt(r, 5);
			String elementType = null;
			if (elementTypeObj instanceof ElementType) {
				elementType = ((ElementType) elementTypeObj).getClassName();
			} else if (elementTypeObj != null) {
				elementType = elementTypeObj.toString();
			}

			String selector = val(r, 2);
			String value = val(r, 3);
			String comment = val(r, 4);
			String java = val(r, 6);

			rows.add(new ActionRecord(
					actionCode,
					selector,
					value,
					comment,
					elementType,
					java
			));
		}

		return rows;
	}

	// --------- Java test ---------

	private void saveGeneratedJava() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save generated test");
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
				"Java files", "java"));

		int result = chooser.showSaveDialog(parent);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".java")) {
			file = new File(file.getParentFile(), file.getName() + ".java");
		}

		List<String> javaLines = buildJavaLinesFromTable();

		StringBuilder body = new StringBuilder();
		for (String line : javaLines) {
			body.append("        ").append(line).append("\n");
		}

		String content =
				"@Tag(\"\")\n" +
						"@TestClassIQHR(name = \"\")\n" +
						"public class GeneratedTestCase {\n" +
						"\n" +
						"    @TestIQHR(name = \"\", tmsLink = \"IQHR-T\")\n" +
						"    public void generatedTest() {\n" +
						body.toString() +
						"    }\n" +
						"}\n";

		try {
			Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
			JOptionPane.showMessageDialog(
					parent,
					"Generated test saved to:\n" + file.getAbsolutePath(),
					"Save Successful",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					parent,
					"Failed to save generated test: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private List<String> buildJavaLinesFromTable() {
		List<String> lines = new ArrayList<>();
		int rowCount = tableModel.getRowCount();

		for (int r = 0; r < rowCount; r++) {
			String action = extractAction(r);
			if (action == null || action.isBlank()) {
				continue;
			}

			String javaData = val(r, 6);
			String value    = val(r, 3);
			String comment  = val(r, 4);

			StringBuilder sb = new StringBuilder();

			// 1) Если есть javaData — используем его как точку входа
			val passValue = !action.contains("click") && !action.contains("selectOption") && !action.contains("fillDate");
			if (hasText(javaData)) {
				appendCall(sb, javaData, action, value, comment, passValue);
				lines.add(sb.toString());
				continue;
			}

			// 2) Специальные действия (switchTab / fillData)
			boolean isSpecial = action.contains("switchTab") || action.contains("fillData") || action.contains("specialAction");
			if (isSpecial) {
				appendSpecialCall(sb, action, value, comment);
				lines.add(sb.toString());
				continue;
			}

			// 3) Обычные действия по селектору и типу элемента
			String javaClassName = val(r, 5);
			String selector      = val(r, 2);
			if (!hasText(javaClassName) || !hasText(selector)) {
				continue;
			}

			sb.append("new ")
					.append(javaClassName)
					.append("($x(\"").append(selector.replace("\"", "\\\"")).append("\"))");

			appendCall(sb, null, action, value, comment, passValue);

			lines.add(sb.toString());
		}

		return lines;
	}

	// --------- helper ---------

	private String val(int row, int col) {
		Object v = tableModel.getValueAt(row, col);
		return v == null ? null : v.toString();
	}

	private String extractAction(int row) {
		Object actionObj = tableModel.getValueAt(row, 1);
		if (actionObj instanceof UserAction) {
			return ((UserAction) actionObj).getCode();
		}
		return actionObj != null ? actionObj.toString() : null;
	}

	private boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	/**
	 * Строит вызов вида:
	 *   prefix.action("value") // comment
	 * если prefix == null:
	 *   .action("value") // comment
	 */
	private void appendCall(StringBuilder sb,
							String prefix,
							String action,
							String value,
							String comment,
							boolean passValue) {
		if (hasText(prefix)) {
			sb.append(prefix);
			sb.append(".");
		}
		sb.append(action);
		sb.append("(");

		if (passValue && hasText(value)) {
			sb.append("\"").append(value.replace("\"", "\\\"")).append("\"");
		}

		sb.append(");");

		if (hasText(comment)) {
			sb.append(" // ").append(comment);
		}
	}

	/**
	 * Спец‑ кейсы типа switchTab / fillData
	 */
	private void appendSpecialCall(StringBuilder sb,
								   String action,
								   String value,
								   String comment) {
		sb.append(action).append("();");
		if (hasText(comment)) {
			sb.append(" // ").append(comment);
		}
		if (hasText(value)) {
			if (!hasText(comment)) {
				sb.append(" // ");
			} else {
				sb.append(", ");
			}
			sb.append(value);
		}
	}

	// --------- JSON load ---------

	public void loadFromJsonFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Open actions JSON");
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
				"JSON files", "json"));

		int result = chooser.showOpenDialog(parent);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File file = chooser.getSelectedFile();
		if (file == null || !file.isFile()) {
			return;
		}

		try (Reader reader = new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8)) {

			Gson gson = new GsonBuilder().create();
			ActionRecord[] records = gson.fromJson(reader, ActionRecord[].class);
			if (records == null) {
				return;
			}

			// очищаем таблицу
			tableModel.setRowCount(0);

			for (ActionRecord rec : records) {
				Object actionValue = toUserAction(rec.getAction());
				Object elementTypeValue = rec.getElementType(); // строка в 5 колонку
				String selector = rec.getSelector();
				String value = rec.getValue();
				String comment = rec.getComment();
				String javaData = rec.getJavaElementAndAction(); // если поле так называется

				tableModel.addRow(new Object[] {
						null,               // индекс проставится листенером
						actionValue,        // 1 Action (UserAction или строка)
						selector,           // 2 Selector
						value,              // 3 Value
						comment,            // 4 Comment
						elementTypeValue,   // 5 Element Type (строка)
						javaData            // 6 Java
				});
			}

			JOptionPane.showMessageDialog(
					parent,
					"Table loaded from:\n" + file.getAbsolutePath(),
					"Load Successful",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					parent,
					"Failed to load table: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private Object toUserAction(String actionCode) {
		if (actionCode == null) {
			return null;
		}
		for (UserAction ua : UserAction.values()) {
			if (actionCode.equals(ua.getCode())) {
				return ua;
			}
		}
		return actionCode; // если не нашли — положим строкой
	}
}

