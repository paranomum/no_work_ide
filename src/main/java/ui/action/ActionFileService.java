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
			String xpath = val(r, 6);
			String name = val(r, 7);
			String index = val(r, 8);
			String byXpath = val(r, 9);

			rows.add(new ActionRecord(
					actionCode,
					selector,
					value,
					comment,
					elementType,
					xpath,
					name,
					index,
					byXpath
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
			// --- исходные значения из таблицы ---
			String actionCode = val(r, 1);           // UserAction / строка
			String selector   = val(r, 2);
			String value      = val(r, 3);           // Value
			String comment    = val(r, 4);           // Comment
			String javaClass  = val(r, 5);           // ElementType.className
			String xpath      = val(r, 6);           // Xpath
			String name       = val(r, 7);           // Name
			String indexStr   = val(r, 8);           // Index
			String byXpathStr = val(r, 9);           // "true"/"false" или null

			if (actionCode == null || actionCode.isBlank()) {
				continue;
			}

			String actionLower = actionCode.toLowerCase();
			boolean isValueAction = !actionLower.contains("click")
					&& !actionLower.contains("filldate");

			boolean specialAction = actionLower.contains("pause")
					|| actionLower.contains("waitloadingpage")
					|| actionLower.contains("filldata")
					|| actionLower.contains("auth")
					|| actionLower.contains("specialaction")
					|| actionLower.contains("switchtab")
					|| actionLower.contains("open");

			if (specialAction) {
				lines.add(appendSpecialAction(actionCode, value, comment));
				continue;
			}

			// --- javaWebElement ---

			String javaWebElement = "new " + javaClass + "(\"";

			boolean hasName = name != null && !name.isBlank();
			boolean byXpath = "true".equalsIgnoreCase(byXpathStr);

			if (hasName) {
				// есть name
				if (!byXpath) {
					// byXpath == false
					Integer index = null;
					if (indexStr != null && !indexStr.isBlank()) {
						try {
							index = Integer.parseInt(indexStr.trim());
						} catch (NumberFormatException ignore) {}
					}

					if (index != null && index > 1) {
						// index > 1
						javaWebElement = javaWebElement + name + "\", " + index + ")";
					} else {
						// index <= 1
						javaWebElement = javaWebElement + name + "\")";
					}
				} else {
					// byXpath == true
					String safeXpath = xpath == null ? "" : xpath.replace("\"", "\\\"");
					javaWebElement = javaWebElement + name + "\", $x(\"" + safeXpath + "\"))";
				}
			} else {
				// нет name
				String safeSelector =  selector == null ? "" : selector.replace("\"", "\\\"");
				String safeXpath = xpath == null ? "" : xpath.replace("\"", "\\\"");

				String locator = !safeXpath.isEmpty()
						? safeXpath
						: (!safeSelector.isEmpty() ? safeSelector : "");

				javaWebElement = javaWebElement + javaClass + "\", $x(\"" + locator + "\"))";
			}

			// --- action ---
			StringBuilder sb = new StringBuilder();

			sb.append(javaWebElement);
			sb.append(".");
			sb.append(actionCode);
			sb.append("(");

			if (value != null && !value.isBlank() && isValueAction) {
				String safeValue = value.replace("\"", "\\\"");
				sb.append("\"").append(safeValue).append("\"");
			}

			sb.append(");");

			// --- comment ---
			if (comment != null && !comment.isBlank()) {
				sb.append(" // ").append(comment);
			}

			lines.add(sb.toString());
		}

		return lines;
	}

	// --------- helper ---------

	private String appendSpecialAction(String actionCode, String value, String comment) {
		String actionLower = actionCode == null ? "" : actionCode.toLowerCase();

		boolean isParamAction =
				"open".equals(actionLower)
						|| "auth".equals(actionLower)
						|| "waitloadingpage".equals(actionLower)
						|| "pause".equals(actionLower);

		StringBuilder sb = new StringBuilder();

		// --- action ---
		sb.append(actionCode);
		sb.append("(");

		if (isParamAction && value != null && !value.isBlank()) {
			if ("waitloadingpage".equals(actionLower) || "pause".equals(actionLower)) {
				// value.replaceAll("[\\D]", "");
				sb.append("\"")
						.append(value.replaceAll("[\\D]", ""))
						.append("\"");
			} else {
				// open/auth: просто "value"
				sb.append("\"")
						.append(value.replace("\"", "\\\""))
						.append("\"");
			}
		}

		sb.append(");");

		// --- comment ---
		if (comment != null && !comment.isBlank() || (value != null && !value.isBlank() && !isParamAction)) {
			sb.append(" // ");
			if (isParamAction) {
				// open, auth, waitloadingpage, pause -> только comment
				if (comment != null && !comment.isBlank()) {
					sb.append(comment);
				}
			} else {
				// всё остальное -> "comment, value"
				boolean hasComment = comment != null && !comment.isBlank();
				if (hasComment) {
					sb.append(comment);
				}
				if (value != null && !value.isBlank()) {
					if (hasComment) {
						sb.append(", ");
					}
					sb.append(value);
				}
			}
		}

		return sb.toString();
	}

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
				String xpath = rec.getXpath(); // если поле так называется
				String name = rec.getName();
				String index = rec.getIndex();
				String byXpath = rec.getByXpath();

				tableModel.addRow(new Object[] {
						null,               // индекс проставится листенером
						actionValue,        // 1 Action (UserAction или строка)
						selector,           // 2 Selector
						value,              // 3 Value
						comment,            // 4 Comment
						elementTypeValue,   // 5 Element Type (строка)
						xpath,
						name,
						index,
						byXpath
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

