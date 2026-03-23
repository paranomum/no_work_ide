package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.ActionRecord;
import dto.LocalVariables;
import dto.Scenario;
import model.ElementType;
import model.UserAction;
import ui.frameworkmeta.PageObjectIntrospector;
import ui.frameworkmeta.PageObjectMatcher;
import ui.frameworkmeta.PageObjectRegistry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static ru.rt.iqhr.framework.util.XPathUtils.isProbablyXPath;

public class ActionFileService {

	private final DefaultTableModel tableModel;
	private final JFrame parent;
	private final CustomMethodsService customMethodsService;
	private final VariablesService variablesService;
	private final PageObjectRegistry pageObjectRegistry = new PageObjectRegistry();

	public ActionFileService(JFrame parent, DefaultTableModel tableModel, CustomMethodsService customMethodsService, VariablesService variablesService) {
		this.parent = parent;
		this.tableModel = tableModel;
		this.customMethodsService = customMethodsService;
		this.variablesService = variablesService;
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

		String[] options = {
				"Test plan (JSON)",                 // 0 — как сейчас (без разворачивания)
				"Generated auto test (.java)",      // 1
				"Full test plan JSON (inline)",     // 2 — НОВОЕ
				"Cancel"                            // 3
		};

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

		if (choice == 3 || choice == JOptionPane.CLOSED_OPTION) {
			return;
		}

		if (choice == 1) {
			saveGeneratedJava();
		} else if (choice == 0) {
			saveJsonPlan();             // как было
		} else if (choice == 2) {
			saveJsonPlanWithInlinedCustomMethods(); // НОВЫЙ метод
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
		List<LocalVariables> vars = variablesService.getVariables(); // уже есть метод

		Scenario scenario = new Scenario(rows, vars);

		try (Writer writer = new OutputStreamWriter(
				new FileOutputStream(file), StandardCharsets.UTF_8)) {

			Gson gson = new GsonBuilder()
					.setPrettyPrinting()
					.create();
			gson.toJson(scenario, writer);
			writer.flush();

			JOptionPane.showMessageDialog(
					parent,
					"Table saved to:\n" + file.getAbsolutePath(),
					"Save Successful",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to save table\n", ex
			);
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
			String url = val(r, 10);

			rows.add(new ActionRecord(
					actionCode,
					selector,
					value,
					comment,
					elementType,
					xpath,
					name,
					index,
					byXpath,
					url
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

		JavaBuildResult buildResult = buildJavaLinesFromTableWithPageObjects();

		StringBuilder body = new StringBuilder();

		// --- объявления PageObject-переменных ---
		for (String fqcn : buildResult.usedPageObjectClasses) {
			// fqcn вида ru.rt.iqhr.pageobject.angular.pages.AuthorizationPage
			String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
			String varName = decapitalize(simple);
			body.append("        ")
					.append(simple)
					.append(" ")
					.append(varName)
					.append(" = new ")
					.append(simple)
					.append("();\n");
		}
		if (!buildResult.usedPageObjectClasses.isEmpty()) {
			body.append("\n");
		}

		// --- сами шаги ---
		for (String line : buildResult.lines) {
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
			TestRecorderErrorLogger.logError(
					"Failed to save generated test\n", ex
			);
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					parent,
					"Failed to save generated test: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}


//	private List<String> buildJavaLinesFromTable() {
//		List<String> lines = new ArrayList<>();
//		int rowCount = tableModel.getRowCount();
//
//		for (int r = 0; r < rowCount; r++) {
//			// --- исходные значения из таблицы ---
//			String actionCode = val(r, 1);           // UserAction / строка
//			String selector   = val(r, 2);
//			String value      = val(r, 3);           // Value
//			String comment    = val(r, 4);           // Comment
//			String javaClass  = val(r, 5);           // ElementType.className
//			String xpath      = val(r, 6);           // Xpath
//			String name       = val(r, 7);           // Name
//			String indexStr   = val(r, 8);           // Index
//			String byXpathStr = val(r, 9);           // "true"/"false" или null
//
//			if (actionCode == null || actionCode.isBlank()) {
//				continue;
//			}
//
//			String actionLower = actionCode.toLowerCase();
//			boolean isValueAction = !actionLower.contains("click")
//					&& !actionLower.contains("filldate");
//
//			boolean specialAction = actionLower.contains("pause")
//					|| actionLower.contains("waitloadingpage")
//					|| actionLower.contains("filldata")
//					|| actionLower.contains("auth")
//					|| actionLower.contains("specialaction")
//					|| actionLower.contains("switchtab")
//					|| actionLower.contains("open");
//
//			if (specialAction) {
//				lines.add(appendSpecialAction(actionCode, value, comment));
//				continue;
//			}
//
//			// --- javaWebElement ---
//
//			String javaWebElement = "new " + javaClass + "(\"";
//
//			boolean hasName = name != null && !name.isBlank();
//			boolean byXpath = "true".equalsIgnoreCase(byXpathStr);
//
//			if (hasName) {
//				// есть name
//				if (!byXpath) {
//					// byXpath == false
//					Integer index = null;
//					if (indexStr != null && !indexStr.isBlank()) {
//						try {
//							index = Integer.parseInt(indexStr.trim());
//						} catch (NumberFormatException ignore) {}
//					}
//
//					if (index != null && index > 1) {
//						// index > 1
//						javaWebElement = javaWebElement + name + "\", " + index + ")";
//					} else {
//						// index <= 1
//						javaWebElement = javaWebElement + name + "\")";
//					}
//				} else {
//					// byXpath == true
//					String safeXpath = xpath == null ? "" : xpath.replace("\"", "\\\"");
//					javaWebElement = javaWebElement + name + "\", $x(\"" + safeXpath + "\"))";
//				}
//			} else {
//				String safeSelector =  selector == null ? "" : selector.replace("\"", "\\\"");
//				if (isProbablyXPath(selector))
//					javaWebElement = javaWebElement + javaClass + "\", $x(\"" + safeSelector + "\"))";
//				else {
//					if (hasCommaSpacesDigitAndNoLettersAfter(selector)) {
//						String[] selectors = selector.trim().split(",");
//						javaWebElement = javaWebElement + selectors[0] + "\", " + selectors[1] + ")";
//					}
//					else {
//						javaWebElement = javaWebElement + selector + "\")";
//					}
//				}
//			}
//
//			// --- action ---
//			StringBuilder sb = new StringBuilder();
//
//			sb.append(javaWebElement);
//			sb.append(".");
//			sb.append(actionCode);
//			sb.append("(");
//
//			if (value != null && !value.isBlank() && isValueAction) {
//				String safeValue = value.replace("\"", "\\\"");
//				sb.append("\"").append(safeValue).append("\"");
//			}
//
//			sb.append(");");
//
//			// --- comment ---
//			if (comment != null && !comment.isBlank()) {
//				sb.append(" // ").append(comment);
//			}
//
//			lines.add(sb.toString());
//		}
//
//		return lines;
//	}

	private JavaBuildResult buildJavaLinesFromTableWithPageObjects() {
		JavaBuildResult result = new JavaBuildResult();
		int rowCount = tableModel.getRowCount();

		for (int r = 0; r < rowCount; r++) {
			String actionCode  = val(r, 1);
			String selector    = val(r, 2);
			String value       = val(r, 3);
			String comment     = val(r, 4);
			String elementType = val(r, 5);
			String xpath       = val(r, 6);
			String name        = val(r, 7);
			String indexStr    = val(r, 8);
			String byXpathStr  = val(r, 9);
			String pageUrlPath = val(r, 10);

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
				result.lines.add(appendSpecialAction(actionCode, value, comment));
				continue;
			}

			String javaWebElement = null;

			// --- пробуем матчить на PageObject ---
			if (elementType != null && name != null && !name.isBlank()) {

				PageObjectIntrospector.Descriptor match = null;

				// 1) сначала по текущему pageUrlPath
				if (pageUrlPath != null && !pageUrlPath.isBlank()) {
					List<PageObjectIntrospector.Descriptor> descriptors =
							pageObjectRegistry.getElementsForPath(pageUrlPath);

					match = PageObjectMatcher.findMatch(descriptors, elementType, name, xpath);
				}

				// 2) если не нашли — пробуем среди всех PageObject-классов
				if (match == null) {
					List<PageObjectIntrospector.Descriptor> allDescriptors =
							pageObjectRegistry.getAllPageObjectDescriptors();
					match = PageObjectMatcher.findMatch(allDescriptors, elementType, name, xpath);
				}

				if (match != null) {
					String pageClassSimpleName = match.pageSimpleName;
					String pageVar = decapitalize(pageClassSimpleName);
					String getterName = "get" + capitalize(match.fieldName);
					javaWebElement = pageVar + "." + getterName + "()";

					result.usedPageObjectClasses.add(match.pageClass.getName());
				}
			}

			if (javaWebElement == null) {
				javaWebElement = buildRawJavaWebElement(
						elementType, selector, xpath, name, indexStr, byXpathStr
				);
			}

			StringBuilder sb = new StringBuilder();
			sb.append(javaWebElement).append(".").append(actionCode).append("(");

			if (value != null && !value.isBlank() && isValueAction) {
				String safeValue = value.replace("\"", "\\\"");
				sb.append("\"").append(safeValue).append("\"");
			}

			sb.append(");");

			if (comment != null && !comment.isBlank()) {
				sb.append(" // ").append(comment);
			}

			result.lines.add(sb.toString());
		}

		return result;
	}

	private String buildRawJavaWebElement(
			String javaClass, String selector, String xpath,
			String name, String indexStr, String byXpathStr
	) {
		String jc = javaClass != null ? javaClass : "Field"; // fallback
		String javaWebElement = "new " + jc + "(\"";

		boolean hasName = name != null && !name.isBlank();
		boolean byXpath = "true".equalsIgnoreCase(byXpathStr);

		if (hasName) {
			if (!byXpath) {
				Integer index = null;
				if (indexStr != null && !indexStr.isBlank()) {
					try {
						index = Integer.parseInt(indexStr.trim());
					} catch (NumberFormatException ignore) {}
				}

				if (index != null && index > 1) {
					javaWebElement = javaWebElement + name + "\", " + index + ")";
				} else {
					javaWebElement = javaWebElement + name + "\")";
				}
			} else {
				String safeXpath = xpath == null ? "" : xpath.replace("\"", "\\\"");
				javaWebElement = javaWebElement + name + "\", $x(\"" + safeXpath + "\"))";
			}
		} else {
			String safeSelector = selector == null ? "" : selector.replace("\"", "\\\"");
			if (isProbablyXPath(selector))
				javaWebElement = javaWebElement + jc + "\", $x(\"" + safeSelector + "\"))";
			else {
				if (hasCommaSpacesDigitAndNoLettersAfter(selector)) {
					String[] selectors = selector.trim().split(",");
					javaWebElement = javaWebElement + selectors[0] + "\", " + selectors[1] + ")";
				} else {
					javaWebElement = javaWebElement + selector + "\")";
				}
			}
		}

		return javaWebElement;
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

	private String decapitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

	private String capitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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

			// пытаемся прочитать новый формат
			Scenario scenario = null;
			ActionRecord[] recordsArray = null;

			// читаем в JsonElement, чтобы понять структуру
			com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(reader);

			if (root.isJsonArray()) {
				// старый формат: просто массив ActionRecord
				recordsArray = gson.fromJson(root, ActionRecord[].class);
			} else if (root.isJsonObject()) {
				com.google.gson.JsonObject obj = root.getAsJsonObject();
				// если есть поле "actions" — новый формат Scenario
				if (obj.has("actions")) {
					scenario = gson.fromJson(obj, Scenario.class);
				} else {
					// fallback: пробуем трактовать весь объект как массив actions (на всякий случай)
					recordsArray = gson.fromJson(root, ActionRecord[].class);
				}
			}

			List<ActionRecord> records;

			if (scenario != null) {
				records = scenario.getActions();
				// грузим переменные в variablesService
				loadVariablesIntoService(scenario.getVariables());
			} else if (recordsArray != null) {
				records = List.of(recordsArray);
				// переменных нет (старый формат) — можно очистить или оставить как есть
				// variablesService.clear(); // если хочешь чистить
			} else {
				return;
			}

			// очищаем таблицу
			tableModel.setRowCount(0);

			for (ActionRecord rec : records) {
				Object actionValue = toUserAction(rec.getAction());
				Object elementTypeValue = rec.getElementType(); // строка в 5 колонку

				tableModel.addRow(new Object[]{
						null,               // индекс проставится листенером
						actionValue,        // 1 Action (UserAction или строка)
						rec.getSelector(),  // 2 Selector
						rec.getValue(),     // 3 Value
						rec.getComment(),   // 4 Comment
						elementTypeValue,   // 5 Element Type (строка)
						rec.getXpath(),
						rec.getName(),
						rec.getIndex(),
						rec.getByXpath(),
						rec.getPageUrlPath()
				});
			}

			JOptionPane.showMessageDialog(
					parent,
					"Table loaded from:\n" + file.getAbsolutePath(),
					"Load Successful",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to load table\n", ex
			);
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

	private static final Pattern COMMA_SPACE_DIGIT_NON_LETTERS =
			Pattern.compile(",\\s*\\d[^A-Za-z]*");

	public static boolean hasCommaSpacesDigitAndNoLettersAfter(String s) {
		if (s == null) return false;
		return COMMA_SPACE_DIGIT_NON_LETTERS.matcher(s).find();
	}

	private void saveJsonPlanWithInlinedCustomMethods() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save full test plan (inline custom methods)");
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

		// шаги с развернутыми customMethod
		List<ActionRecord> rows = buildActionRecordsWithInlinedCustomMethods();
		// текущие переменные окружения
		List<LocalVariables> vars = variablesService.getVariables();

		Scenario scenario = new Scenario(rows, vars);

		try (Writer writer = new OutputStreamWriter(
				new FileOutputStream(file), StandardCharsets.UTF_8)) {

			Gson gson = new GsonBuilder()
					.setPrettyPrinting()
					.create();
			gson.toJson(scenario, writer);
			writer.flush();

			JOptionPane.showMessageDialog(
					parent,
					"Full test plan (inline) saved to:\n" + file.getAbsolutePath(),
					"Save Successful",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to save full test plan\n", ex
			);
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					parent,
					"Failed to save full test plan: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}


	private List<ActionRecord> buildActionRecordsWithInlinedCustomMethods() {
		List<ActionRecord> result = new ArrayList<>();
		int rowCount = tableModel.getRowCount();

		for (int r = 0; r < rowCount; r++) {
			// вытаскиваем actionCode так же, как в buildActionRecords()
			Object actionObj = tableModel.getValueAt(r, 1);
			String actionCode = null;
			if (actionObj instanceof UserAction) {
				actionCode = ((UserAction) actionObj).getCode();
			} else if (actionObj != null) {
				actionCode = actionObj.toString();
			}

			// если это не customMethod — просто добавляем один ActionRecord
			if (!"customMethod".equals(actionCode)) {
				result.add(buildActionRecordForRow(r, actionCode));
				continue;
			}

			// customMethod: берём имя метода из Value
			String methodName = val(r, 3); // колонка Value
			if (methodName == null || methodName.isBlank() || customMethodsService == null) {
				// если что-то не так — сохраняем как есть, чтобы не потерять шаг
				result.add(buildActionRecordForRow(r, actionCode));
				continue;
			}

			// грузим шаги метода и inline-им их
			try {
				List<ActionRecord> methodSteps =
						customMethodsService.loadMethodStepsAsActionRecords(methodName);
				if (methodSteps == null || methodSteps.isEmpty()) {
					// если метод пуст — можно либо ничего не добавлять,
					// либо сохранить исходный шаг; я предлагаю сохранить исходный
					result.add(buildActionRecordForRow(r, actionCode));
				} else {
					result.addAll(methodSteps);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				TestRecorderErrorLogger.logError(
						"buildActionRecordsWithInlinedCustomMethods\n", ex
				);
				// в случае ошибки лучше сохранить исходный шаг, чтобы сценарий не «терялся»
				result.add(buildActionRecordForRow(r, actionCode));
			}
		}

		return result;
	}


	private ActionRecord buildActionRecordForRow(int r, String actionCodeFromOutside) {
		String actionCode = actionCodeFromOutside;

		if (actionCode == null) {
			Object actionObj = tableModel.getValueAt(r, 1);
			if (actionObj instanceof UserAction) {
				actionCode = ((UserAction) actionObj).getCode();
			} else if (actionObj != null) {
				actionCode = actionObj.toString();
			}
		}

		Object elementTypeObj = tableModel.getValueAt(r, 5);
		String elementType = null;
		if (elementTypeObj instanceof ElementType) {
			elementType = ((ElementType) elementTypeObj).getClassName();
		} else if (elementTypeObj != null) {
			elementType = elementTypeObj.toString();
		}

		String selector = val(r, 2);
		String value    = val(r, 3);
		String comment  = val(r, 4);
		String xpath    = val(r, 6);
		String name     = val(r, 7);
		String index    = val(r, 8);
		String byXpath  = val(r, 9);
		String url  = val(r, 10);

		return new ActionRecord(
				actionCode,
				selector,
				value,
				comment,
				elementType,
				xpath,
				name,
				index,
				byXpath,
				url
		);
	}

	private void loadVariablesIntoService(List<LocalVariables> vars) {
		if (vars == null || vars.isEmpty()) {
			return;
		}

		// если нужно очищать предыдущие переменные перед загрузкой — добавь clear()
		 variablesService.clear();

		for (LocalVariables v : vars) {
			if (v == null || v.getName() == null || v.getName().isBlank()) {
				continue;
			}
			variablesService.addVariable(v);
		}
	}

	private void debugAllPageObjectsFromTable() {
		int rowCount = tableModel.getRowCount();
		if (rowCount == 0) {
			return;
		}

		// Собираем все уникальные pageUrlPath из таблицы (в порядке появления)
		Set<String> paths = new LinkedHashSet<>();
		for (int r = 0; r < rowCount; r++) {
			String path = val(r, 10); // 10-я колонка с pageUrlPath
			if (path != null && !path.isBlank()) {
				paths.add(path);
			}
		}

		PageObjectRegistry registry = new PageObjectRegistry();

		for (String path : paths) {
			System.out.println("=== PAGE OBJECT ELEMENTS FOR PATH: " + path + " ===");
			List<PageObjectIntrospector.Descriptor> descriptors =
					registry.getElementsForPath(path);
			for (PageObjectIntrospector.Descriptor d : descriptors) {
				System.out.println("  " + d.pageSimpleName + "." + d.fieldName
						+ " : " + d.fieldType.getSimpleName()
						+ " [" + d.label + "]");
			}
			System.out.println("==================================================");
		}
	}

	private static class JavaBuildResult {
		List<String> lines = new ArrayList<>();
		// set имён классов PageObject'ов, которые реально использовались
		java.util.Set<String> usedPageObjectClasses = new java.util.LinkedHashSet<>();
	}

}

