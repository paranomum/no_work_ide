package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.*;
import lombok.SneakyThrows;
import lombok.val;
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
import java.util.*;
import java.util.regex.Pattern;

import static ru.rt.iqhr.framework.util.XPathUtils.isProbablyXPath;

public class ActionFileService {

	private final DefaultTableModel tableModel;
	private final JFrame parent;
	private final CustomMethodsService customMethodsService;
	private final VariablesService variablesService;
	private TestGeneratorService testGeneratorService;
	private BackendRequestsService backendRequestsService;
	private PlayActionService playActionServiceRef;

	@SneakyThrows
	public ActionFileService(JFrame parent, DefaultTableModel tableModel, CustomMethodsService customMethodsService, VariablesService variablesService) {
		this.parent = parent;
		this.tableModel = tableModel;
		this.customMethodsService = customMethodsService;
		this.variablesService = variablesService;
		try {
			val pageObjectRegistry = new PageObjectRegistry();
			this.testGeneratorService = new TestGeneratorService(pageObjectRegistry);
		} catch (Throwable e) {
			e.printStackTrace(new PrintWriter(new FileWriter("startup-error.log", true)));
			throw e;
		}

	}

	public void setBackendRequestsService(BackendRequestsService backendRequestsService) {
		this.backendRequestsService = backendRequestsService;
	}
	public void setPlayActionServiceRef(PlayActionService playActionService) {
		this.playActionServiceRef = playActionService;
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
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));

		int result = chooser.showSaveDialog(parent);
		if (result != JFileChooser.APPROVE_OPTION) return;

		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".json")) {
			file = new File(file.getParentFile(), file.getName() + ".json");
		}

		List<ActionRecord> rows = buildActionRecords();
		List<LocalVariables> vars = variablesService.getVariables();

		// Собираем backend-запросы, используемые в сценарии
		List<BackendRequestDef> usedBackendRequests = new ArrayList<>();
		if (backendRequestsService != null) {
			for (String name : collectUsedBackendRequestNames()) {
				BackendRequestDef def = backendRequestsService.findByName(name);
				if (def != null) usedBackendRequests.add(def);
			}
		}

		List<String> usedNames = collectUsedBackendRequestNames();
		Map<String, ScenarioBackendConfig> scenarioOverrides = buildScenarioOverrides(usedNames);
		Scenario scenario = new Scenario(rows, vars, usedBackendRequests, scenarioOverrides);

		try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(scenario, writer);
			writer.flush();
			JOptionPane.showMessageDialog(parent,
					"Table saved to:\n" + file.getAbsolutePath(), "Save Successful",
					JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError("Failed to save table\n", ex);
			JOptionPane.showMessageDialog(parent,
					"Failed to save table: " + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private List<ActionRecord> buildActionRecords() {
		List<ActionRecord> rows = new ArrayList<>();
		int rowCount = tableModel.getRowCount();

		// --- 0. Предрасчёт: какие customMethod раскрыты и какие строки являются их "детьми" ---

		// map: modelRow строки CUSTOM_METHOD -> true, если у него есть дочерние шаги
		java.util.Map<Integer, Boolean> customMethodExpanded = new java.util.HashMap<>();
		// set: строки, которые являются дочерними шагами любых раскрытых customMethod
		java.util.Set<Integer> childRowsOfExpandedMethods = new java.util.HashSet<>();

		// индексы из колонки "#"
		java.util.List<String> indices = new java.util.ArrayList<>(rowCount);
		for (int r = 0; r < rowCount; r++) {
			indices.add(val(r, 0) == null ? "" : val(r, 0).trim());
		}

		// проходим по всем строкам, ищем CUSTOM_METHOD верхнего уровня
		for (int r = 0; r < rowCount; r++) {
			Object actionObj = tableModel.getValueAt(r, 1);
			if (!(actionObj instanceof UserAction ua) || ua != UserAction.CUSTOM_METHOD) {
				continue;
			}

			String idx = indices.get(r); // например "1"
			if (idx.isEmpty()) continue;

			String methodName = val(r, 3); // Value = имя метода
			if (methodName == null || methodName.isBlank()) continue;

			String prefix = idx + ".";     // "1."

			boolean hasChildren = false;

			for (int rr = r + 1; rr < rowCount; rr++) {
				String childIdx = indices.get(rr); // "1.1", "1.2", "2", ...
				if (!childIdx.startsWith(prefix)) {
					// как только префикс перестал совпадать — дочерние шаги этого метода закончились
					break;
				}

				// проверяем, что это действительно шаг этого метода, а не какая-то чужая строка
				Object refObj = tableModel.getValueAt(rr, 11); // CustomMethodRef
				if (refObj != null && refObj.equals(methodName)) {
					hasChildren = true;
					childRowsOfExpandedMethods.add(rr);
				}
			}

			customMethodExpanded.put(r, hasChildren);
		}

		// --- 1. Основной проход: формируем ActionRecord для нужных строк ---

		for (int r = 0; r < rowCount; r++) {
			Object actionObj = tableModel.getValueAt(r, 1);
			String actionCode = null;
			if (actionObj instanceof UserAction) {
				actionCode = ((UserAction) actionObj).getCode();
			} else if (actionObj != null) {
				actionCode = actionObj.toString();
			}

			// 1) если это дочерний шаг раскрытого customMethod — НЕ сохраняем его в обычный план
			if (childRowsOfExpandedMethods.contains(r)) {
				continue;
			}

			// 2) если это верхний customMethod и он раскрыт — сохраняем ТОЛЬКО его, а не детей
			if (actionObj instanceof UserAction ua && ua == UserAction.CUSTOM_METHOD) {
				Boolean expanded = customMethodExpanded.get(r);
				// expanded == true → дети уже проигнорированы выше, эту строку сохраняем как одну
				// expanded == false → обычный, нераскрытый customMethod, тоже сохраняем как есть
				// ничего дополнительно делать не нужно
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
			String url      = val(r, 10);

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

		List<ActionRecord> rows = buildActionRecords(); // уже есть метод
		String content = testGeneratorService.generateJavaTestClass(rows);

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

	private String val(int row, int col) {
		Object v = tableModel.getValueAt(row, col);
		return v == null ? null : v.toString();
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
				loadVariablesIntoService(scenario.getVariables());

				// ← НОВОЕ: сброс к системным запросам перед загрузкой тестовых
				if (backendRequestsService != null) {
					backendRequestsService.load(
							scenario.getBackendRequests(),
							scenario.getScenarioOverrides()
					);

					importBackendRequestsFromCustomMethods(records);
				}

				if (scenario.getScenarioOverrides() != null && this.playActionServiceRef != null) {
					this.playActionServiceRef.setCurrentScenarioOverrides(scenario.getScenarioOverrides());
				}
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
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));

		int result = chooser.showSaveDialog(parent);
		if (result != JFileChooser.APPROVE_OPTION) return;

		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".json")) {
			file = new File(file.getParentFile(), file.getName() + ".json");
		}

		List<ActionRecord> rows = buildActionRecordsWithInlinedCustomMethods();
		List<LocalVariables> vars = variablesService.getVariables();

		// Собираем backend-запросы, используемые в сценарии
		List<BackendRequestDef> usedBackendRequests = new ArrayList<>();
		if (backendRequestsService != null) {
			for (String name : collectUsedBackendRequestNames()) {
				BackendRequestDef def = backendRequestsService.findByName(name);
				if (def != null) usedBackendRequests.add(def);
			}
		}

		List<String> usedNames = collectUsedBackendRequestNames();
		Map<String, ScenarioBackendConfig> scenarioOverrides = buildScenarioOverrides(usedNames);
		Scenario scenario = new Scenario(rows, vars, usedBackendRequests, scenarioOverrides);

		try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(scenario, writer);
			writer.flush();
			JOptionPane.showMessageDialog(parent,
					"Full test plan (inline) saved to:\n" + file.getAbsolutePath(),
					"Save Successful", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError("Failed to save full test plan\n", ex);
			JOptionPane.showMessageDialog(parent,
					"Failed to save full test plan: " + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void importBackendRequestsFromScenario(List<BackendRequestDef> backendRequests) {
		if (backendRequests == null || backendRequests.isEmpty() || backendRequestsService == null) {
			return;
		}
		// Загружаем только в память — НЕ сохраняем в системный файл
		backendRequestsService.loadFromScenario(backendRequests);
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
		System.out.println("=== loadVariablesIntoService START ===");
		System.out.println("vars from json = " + vars);

		variablesService.clear();
		System.out.println("after clear variablesService.getVariables() = " + variablesService.getVariables());

		if (vars != null) {
			for (LocalVariables v : vars) {
				System.out.println("adding var = " + v);
				if (v == null || v.getName() == null || v.getName().isBlank()) {
					continue;
				}
				variablesService.addVariable(v);
			}
		}

		System.out.println("after addVariable variablesService.getVariables() = " + variablesService.getVariables());

		variablesService.refreshTableFromVariables();

		System.out.println("after refreshTableFromVariables variablesService.getVariables() = " + variablesService.getVariables());
		System.out.println("=== loadVariablesIntoService END ===");
	}

	private List<String> collectUsedBackendRequestNames() {
		List<String> names = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (int r = 0; r < tableModel.getRowCount(); r++) {
			Object actionObj = tableModel.getValueAt(r, 1);
			String actionCode = actionObj instanceof model.UserAction
					? ((model.UserAction) actionObj).getCode()
					: (actionObj != null ? actionObj.toString() : null);
			if ("useBackendMethod".equals(actionCode)) {
				String name = String.valueOf(tableModel.getValueAt(r, 3)).trim();
				if (!name.isEmpty() && seen.add(name)) {
					names.add(name);
				}
			}
		}
		return names;
	}

	private Map<String, ScenarioBackendConfig> buildScenarioOverrides(List<String> usedRequestNames) {
		if (backendRequestsService == null || usedRequestNames.isEmpty()) return null;
		Map<String, ScenarioBackendConfig> overrides = new java.util.LinkedHashMap<>();
		for (String name : usedRequestNames) {
			BackendRequestDef def = backendRequestsService.findByName(name);
			if (def == null) continue;
			List<DtoFieldOverride> fo = def.getFieldOverrides();
			List<ResponseFieldExtractor> re = def.getResponseExtractors();
			if ((fo != null && !fo.isEmpty()) || (re != null && !re.isEmpty())) {
				overrides.put(name, new ScenarioBackendConfig(fo, re));
			}
		}
		return overrides.isEmpty() ? null : overrides;
	}

	/**
	 * Проходит по всем шагам теста с action=customMethod,
	 * загружает JSON-файлы методов и подтягивает их backendRequests в память.
	 */
	private void importBackendRequestsFromCustomMethods(List<ActionRecord> records) {
		if (records == null || customMethodsService == null || backendRequestsService == null) {
			return;
		}
		for (ActionRecord rec : records) {
			if (!"customMethod".equals(rec.getAction())) {
				continue;
			}
			String methodName = rec.getValue();
			if (methodName == null || methodName.isBlank()) {
				continue;
			}
			try {
				List<BackendRequestDef> methodBackendRequests =
						customMethodsService.loadMethodBackendRequests(methodName);
				backendRequestsService.loadFromScenario(methodBackendRequests);
			} catch (Exception ex) {
				// Метод может не иметь backendRequests — игнорируем тихо
				ex.printStackTrace();
			}
		}
	}
}

