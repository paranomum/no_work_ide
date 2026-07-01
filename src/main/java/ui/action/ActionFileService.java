package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.*;
import lombok.SneakyThrows;
import lombok.val;
import model.ElementType;
import model.UserAction;
import ui.frameworkmeta.PageObjectRegistry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class ActionFileService {

	private static final Pattern COMMA_SPACE_DIGIT_NON_LETTERS =
			Pattern.compile(",\\s*\\d[^A-Za-z]*");
	private final DefaultTableModel tableModel;
	private final JFrame parent;
	private final CustomMethodsService customMethodsService;
	private final VariablesService variablesService;
	private final TestGeneratorService testGeneratorService;
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

	public static boolean hasCommaSpacesDigitAndNoLettersAfter(String s) {
		if (s == null) return false;
		return COMMA_SPACE_DIGIT_NON_LETTERS.matcher(s).find();
	}

	// --------- Публичный вход ---------

	public void setBackendRequestsService(BackendRequestsService backendRequestsService) {
		this.backendRequestsService = backendRequestsService;
	}

	// --------- JSON ---------

	public void setPlayActionServiceRef(PlayActionService playActionService) {
		this.playActionServiceRef = playActionService;
	}

	public void saveWithModeDialog() {
		if (tableModel.getRowCount() == 0) {
			JOptionPane.showMessageDialog(
					parent,
					"Таблица шагов пуста.",
					"Сохранить тест-план",
					JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}

		int choice = showSaveModeDialog();

		if (choice == 3 || choice == -1) {
			return;
		}

		if (choice == 1) {
			saveGeneratedJava();
		} else if (choice == 0) {
			saveJsonPlan();
		} else if (choice == 2) {
			saveJsonPlanWithInlinedCustomMethods();
		}
	}

	private int showSaveModeDialog() {
		final int[] result = {-1};

		JDialog dialog = new JDialog(parent, "Сохранение", true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		JPanel root = new JPanel();
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

		JLabel label = new JLabel("Что сохранить?");
		label.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

		JPanel buttonsPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
		buttonsPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

		JButton jsonButton = new JButton("Тест");
		JButton javaButton = new JButton("Шаблон автотеста");
		JButton fullJsonButton = new JButton("Развернутый тест");
		JButton cancelButton = new JButton("Отмена");

		jsonButton.setToolTipText("Сохранить тест-план в JSON");
		javaButton.setToolTipText("Сгенерировать шаблон автотеста в .java");
		fullJsonButton.setToolTipText("Сохранить развернутый тест-план с подстановкой кастомных методов");
		cancelButton.setToolTipText("Закрыть окно без сохранения");

		java.awt.Insets compactInsets = new java.awt.Insets(3, 8, 3, 8);
		java.awt.Dimension compactSize = new java.awt.Dimension(170, 30);

		for (JButton button : new JButton[]{cancelButton, javaButton, fullJsonButton, jsonButton}) {
			button.setMargin(compactInsets);
			button.setFocusPainted(false);
			button.setPreferredSize(compactSize);
			button.setFont(button.getFont().deriveFont(14f));
		}

		jsonButton.addActionListener(e -> {
			result[0] = 0;
			dialog.dispose();
		});

		javaButton.addActionListener(e -> {
			result[0] = 1;
			dialog.dispose();
		});

		fullJsonButton.addActionListener(e -> {
			result[0] = 2;
			dialog.dispose();
		});

		cancelButton.addActionListener(e -> {
			result[0] = 3;
			dialog.dispose();
		});

		buttonsPanel.add(cancelButton);
		buttonsPanel.add(javaButton);
		buttonsPanel.add(fullJsonButton);
		buttonsPanel.add(jsonButton);

		root.add(label);
		root.add(Box.createVerticalStrut(10));
		root.add(buttonsPanel);

		dialog.setContentPane(root);
		dialog.pack();
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);

		return result[0];
	}

	// --------- Java test ---------

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

		List<BackendRequestDef> usedBackendRequests = collectBackendRequestsForScenario(rows);
		List<String> usedNames = extractBackendRequestNames(usedBackendRequests);
		Map<String, ScenarioBackendConfig> scenarioOverrides = buildScenarioOverrides(usedNames);

		Scenario scenario = new Scenario(rows, vars, usedBackendRequests, scenarioOverrides);

		try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(scenario, writer);
			writer.flush();
			JOptionPane.showMessageDialog(parent,
					"Тест план сохранен:\n" + file.getAbsolutePath(),
					"Save Successful",
					JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError("Ошибка при сохранении\n", ex);
			JOptionPane.showMessageDialog(parent,
					"Не удалось сохранить тест план: " + ex.getMessage(),
					"Error",
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

	// --------- JSON load ---------

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
					"Автотест сгенерирован:\n" + file.getAbsolutePath(),
					"Save Successful",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Ошибка при генерации автотеста\n", ex
			);
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					parent,
					"Не удалось сгенерировать автотест: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private String val(int row, int col) {
		Object v = tableModel.getValueAt(row, col);
		return v == null ? null : v.toString();
	}

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
				records = scenario.getActions() != null ? scenario.getActions() : List.of();
				loadVariablesIntoService(scenario.getVariables());

				if (backendRequestsService != null) {
					backendRequestsService.load(
							scenario.getBackendRequests(),
							scenario.getScenarioOverrides()
					);

					importBackendRequestsFromCustomMethods(records, true);
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

		List<BackendRequestDef> usedBackendRequests = collectBackendRequestsForScenario(rows);
		List<String> usedNames = extractBackendRequestNames(usedBackendRequests);
		Map<String, ScenarioBackendConfig> scenarioOverrides = buildScenarioOverrides(usedNames);

		Scenario scenario = new Scenario(rows, vars, usedBackendRequests, scenarioOverrides);

		try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(scenario, writer);
			writer.flush();
			JOptionPane.showMessageDialog(parent,
					"Full test plan (inline) saved to:\n" + file.getAbsolutePath(),
					"Save Successful",
					JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError("Failed to save full test plan\n", ex);
			JOptionPane.showMessageDialog(parent,
					"Failed to save full test plan: " + ex.getMessage(),
					"Error",
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
			Object actionObj = tableModel.getValueAt(r, 1);
			String actionCode = null;
			if (actionObj instanceof UserAction) {
				actionCode = ((UserAction) actionObj).getCode();
			} else if (actionObj != null) {
				actionCode = actionObj.toString();
			}

			ActionRecord rowRecord = buildActionRecordForRow(r, actionCode);
			appendExpandedActionRecord(result, rowRecord, new LinkedHashSet<>());
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
		String value = val(r, 3);
		String comment = val(r, 4);
		String xpath = val(r, 6);
		String name = val(r, 7);
		String index = val(r, 8);
		String byXpath = val(r, 9);
		String url = val(r, 10);

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

	private List<BackendRequestDef> collectBackendRequestsForScenario(List<ActionRecord> records) {
		Map<String, BackendRequestDef> collected = new LinkedHashMap<>();
		collectBackendRequestsRecursive(records, collected, new LinkedHashSet<>());
		return new ArrayList<>(collected.values());
	}

	private void collectBackendRequestsRecursive(List<ActionRecord> records,
												 Map<String, BackendRequestDef> collected,
												 Set<String> visitedCustomMethods) {
		if (records == null || records.isEmpty()) {
			return;
		}

		for (ActionRecord rec : records) {
			if (rec == null) {
				continue;
			}

			String action = safeTrim(rec.getAction());

			if ("useBackendMethod".equals(action)) {
				String backendName = safeTrim(rec.getValue());
				if (backendName.isEmpty() || backendRequestsService == null) {
					continue;
				}

				BackendRequestDef def = backendRequestsService.findByName(backendName);
				if (def != null) {
					collected.putIfAbsent(def.getName(), def);
				}
				continue;
			}

			if ("customMethod".equals(action)) {
				String methodName = safeTrim(rec.getValue());
				if (methodName.isEmpty() || !visitedCustomMethods.add(methodName)) {
					continue;
				}

				try {
					List<BackendRequestDef> methodBackends =
							customMethodsService.loadMethodBackendRequests(methodName);
					if (methodBackends != null) {
						for (BackendRequestDef def : methodBackends) {
							if (def != null && def.getName() != null && !def.getName().isBlank()) {
								collected.putIfAbsent(def.getName(), def);
								if (backendRequestsService != null && backendRequestsService.findByName(def.getName()) == null) {
									backendRequestsService.loadFromScenario(List.of(def));
								}
							}
						}
					}

					List<ActionRecord> nestedSteps = customMethodsService.loadMethodSteps(methodName);
					collectBackendRequestsRecursive(nestedSteps, collected, visitedCustomMethods);
				} catch (Exception ex) {
					TestRecorderErrorLogger.logError(
							"Failed to collect backend requests from custom method '" + methodName + "'",
							ex
					);
				}
			}
		}
	}

	private List<String> extractBackendRequestNames(List<BackendRequestDef> defs) {
		List<String> names = new ArrayList<>();
		if (defs == null || defs.isEmpty()) {
			return names;
		}

		for (BackendRequestDef def : defs) {
			if (def == null) {
				continue;
			}
			String name = safeTrim(def.getName());
			if (!name.isEmpty()) {
				names.add(name);
			}
		}

		return names;
	}

	private void appendExpandedActionRecord(List<ActionRecord> target,
											ActionRecord record,
											Set<String> visitedCustomMethods) {
		if (record == null) {
			return;
		}

		String action = safeTrim(record.getAction());
		if (!"customMethod".equals(action)) {
			target.add(record);
			return;
		}

		String methodName = safeTrim(record.getValue());
		if (methodName.isEmpty()) {
			target.add(record);
			return;
		}

		if (!visitedCustomMethods.add(methodName)) {
			target.add(record);
			return;
		}

		try {
			List<ActionRecord> methodSteps = customMethodsService.loadMethodSteps(methodName);
			if (methodSteps == null || methodSteps.isEmpty()) {
				target.add(record);
				return;
			}

			for (ActionRecord step : methodSteps) {
				appendExpandedActionRecord(target, step, visitedCustomMethods);
			}
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to inline custom method '" + methodName + "'",
					ex
			);
			target.add(record);
		}
	}

	private String safeTrim(String value) {
		return value == null ? "" : value.trim();
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
	private void importBackendRequestsFromCustomMethods(List<ActionRecord> records, boolean importVariablesToo) {
		if (records == null || customMethodsService == null || backendRequestsService == null) {
			return;
		}

		Set<String> visitedMethods = new LinkedHashSet<>();
		importBackendRequestsFromCustomMethodsRecursive(records, visitedMethods, importVariablesToo);
	}

	private void importBackendRequestsFromCustomMethodsRecursive(List<ActionRecord> records,
																 Set<String> visitedMethods,
																 boolean importVariablesToo) {
		if (records == null || records.isEmpty()) {
			return;
		}

		for (ActionRecord rec : records) {
			if (rec == null || !"customMethod".equals(safeTrim(rec.getAction()))) {
				continue;
			}

			String methodName = safeTrim(rec.getValue());
			if (methodName.isEmpty() || !visitedMethods.add(methodName)) {
				continue;
			}

			try {
				List<BackendRequestDef> methodBackendRequests =
						customMethodsService.loadMethodBackendRequests(methodName);
				backendRequestsService.loadFromScenario(methodBackendRequests);

				if (importVariablesToo) {
					List<LocalVariables> methodVars = customMethodsService.loadMethodVariables(methodName);
					if (methodVars != null) {
						for (LocalVariables v : methodVars) {
							if (v != null && v.getName() != null && !v.getName().isBlank()) {
								variablesService.addVariable(v);
							}
						}
						variablesService.refreshTableFromVariables();
					}
				}

				List<ActionRecord> nestedSteps = customMethodsService.loadMethodSteps(methodName);
				importBackendRequestsFromCustomMethodsRecursive(nestedSteps, visitedMethods, importVariablesToo);
			} catch (Exception ex) {
				TestRecorderErrorLogger.logError(
						"Failed to import backendRequests from custom method '" + methodName + "'",
						ex
				);
			}
		}
	}
}

