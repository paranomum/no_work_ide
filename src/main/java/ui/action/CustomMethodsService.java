package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.ActionRecord;
import dto.AppConfig;
import dto.BackendRequestDef;
import dto.LocalVariables;
import ui.AbstractTableSettingsPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CustomMethodsService extends AbstractTableSettingsPanel {

	private final ConfigService configService;
	private final AppConfig config;
	// внутренняя коллекция как у OpenApiService (там map, здесь список)
	private final List<MethodDef> methods = new ArrayList<>();
	// Gson один раз на сервис
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private JTable customMethodsTable;
	private DefaultTableModel customMethodsTableModel;

	public CustomMethodsService(ConfigService configService, AppConfig config) {
		this.configService = configService;
		this.config = config;
	}

	public JPanel createCustomMethodsSettingsPanel(JDialog parentDialog) {
		JPanel panel = buildTablePanel(
				"Custom methods",
				new String[]{"Method", "Path"},
				() -> saveCustomMethods(parentDialog),
				() -> openPathFileChooser(parentDialog)
		);

		this.customMethodsTable = this.table;
		this.customMethodsTableModel = this.model;

		load();                  // как и сейчас
		loadCustomMethodsIntoTable();
		return panel;
	}

	// ---------- SETTINGS PANEL ----------

	private void loadCustomMethodsIntoTable() {
		customMethodsTableModel.setRowCount(0);
		for (MethodDef m : this.getMethods()) {
			customMethodsTableModel.addRow(new Object[]{m.getName(), m.getPath()});
		}
	}


	// ---------- TABLE <-> LIST BINDING ----------

	private void saveCustomMethods(JDialog parentDialog) {
		List<MethodDef> list = new ArrayList<>();
		for (int row = 0; row < customMethodsTableModel.getRowCount(); row++) {
			String name = Objects.toString(customMethodsTableModel.getValueAt(row, 0), "").trim();
			String path = Objects.toString(customMethodsTableModel.getValueAt(row, 1), "").trim();
			if (!name.isEmpty() || !path.isEmpty()) {
				list.add(new MethodDef(name, path));
			}
		}
		this.setMethods(list);
		this.save();

		JOptionPane.showMessageDialog(
				parentDialog,
				"Custom methods saved",
				"Saved",
				JOptionPane.INFORMATION_MESSAGE
		);
	}

	public List<MethodDef> getMethods() {
		return Collections.unmodifiableList(methods);
	}

	// ---------- IN-MEMORY API ----------

	public void setMethods(List<MethodDef> list) {
		methods.clear();
		if (list != null) {
			methods.addAll(list);
		}
	}

	public void addMethod(String name, String path) {
		methods.add(new MethodDef(name, path));
	}

	public void removeMethod(int index) {
		methods.remove(index);
	}

	public MethodDef findByName(String name) {
		for (MethodDef m : methods) {
			if (Objects.equals(m.getName(), name)) {
				return m;
			}
		}
		return null;
	}

	// тут использую аналогичный подход: отдельный json-файл рядом с конфигом
	private Path getCustomMethodsFile() throws Exception {
		// сделай в ConfigService метод вроде getCustomMethodsFile(config)
		return configService.getCustomMethodsFile(config);
	}

	// ---------- PERSISTENCE (по образцу OpenApiService) ----------

	public void load() {
		methods.clear();
		try {
			Path file = getCustomMethodsFile();
			if (!Files.exists(file)) {
				return;
			}
			String json = Files.readString(file);
			MethodDef[] arr = gson.fromJson(json, MethodDef[].class);
			if (arr != null) {
				methods.addAll(Arrays.asList(arr));
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			// здесь, как в loadOpenApiSpecsIntoTable, можно в тихую, без диалога
		}
	}

	public void save() {
		try {
			Path file = getCustomMethodsFile();
			String json = gson.toJson(methods.toArray(new MethodDef[0]));
			Files.writeString(file, json, StandardCharsets.UTF_8);
		} catch (Exception ex) {
			ex.printStackTrace();
			// при желании можно показать JOptionPane из вызывающего кода
		}
	}

	private void openPathFileChooser(JDialog parentDialog) {
		if (customMethodsTable.isEditing()) {
			customMethodsTable.getCellEditor().stopCellEditing(); // важный вызов[web:75]
		}

		int row = customMethodsTable.getSelectedRow();
		if (row < 0) {
			JOptionPane.showMessageDialog(
					parentDialog,
					"Select a row first",
					"No row selected",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setDialogTitle("Select custom method JSON");

		// можно попробовать инициализировать текущим значением path
		String currentPath = Objects.toString(
				customMethodsTableModel.getValueAt(row, 1), ""
		).trim();
		if (!currentPath.isEmpty()) {
			java.io.File cur = new java.io.File(currentPath);
			if (cur.exists()) {
				if (cur.isDirectory()) {
					chooser.setCurrentDirectory(cur);
				} else {
					chooser.setCurrentDirectory(cur.getParentFile());
					chooser.setSelectedFile(cur);
				}
			}
		}

		int res = chooser.showOpenDialog(parentDialog);
		if (res == JFileChooser.APPROVE_OPTION) {
			java.io.File file = chooser.getSelectedFile();
			if (file != null) {
				customMethodsTableModel.setValueAt(
						file.getAbsolutePath(),
						row,
						1 // колонка Path
				);
			}
		}
	}

	public List<ActionRecord> loadMethodSteps(String methodName) {
		MethodDef def = findByName(methodName);
		if (def == null || def.getPath() == null || def.getPath().isBlank()) {
			throw new IllegalArgumentException("Custom method not found or path is empty: " + methodName);
		}

		File file = new File(def.getPath());
		if (!file.exists()) {
			throw new IllegalArgumentException("Custom method file not found: " + file.getAbsolutePath());
		}

		try (Reader reader = new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8)) {

			com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(reader);

			// формат 1: в корне массив шагов
			if (root.isJsonArray()) {
				java.lang.reflect.Type listType =
						new com.google.gson.reflect.TypeToken<List<ActionRecord>>() {
						}.getType();
				List<ActionRecord> steps = gson.fromJson(root, listType);
				return steps != null ? steps : List.of();
			}

			// формат 2: в корне объект с полем actions
			if (root.isJsonObject()) {
				MethodFile methodFile = gson.fromJson(root, MethodFile.class);
				List<ActionRecord> steps =
						methodFile != null && methodFile.getActions() != null
								? methodFile.getActions()
								: List.of();
				return steps;
			}

			// на всякий случай
			return List.of();
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to load custom method '" + methodName, ex
			);
			throw new RuntimeException(
					"Failed to load custom method '" + methodName + "': " + ex.getMessage(), ex);
		}
	}

	public List<ActionRecord> loadMethodStepsAsActionRecords(String methodName) {
		return loadMethodSteps(methodName);
	}

	public void saveMethod(String methodName,
						   List<ActionRecord> actions,
						   List<LocalVariables> variables) {
		saveMethod(methodName, actions, variables, List.of());
	}

	public void saveMethod(String methodName,
						   List<ActionRecord> actions,
						   List<LocalVariables> variables,
						   List<BackendRequestDef> backendRequests) {

		MethodDef def = findByName(methodName);
		if (def == null || def.getPath() == null || def.getPath().isBlank()) {
			throw new IllegalArgumentException("Custom method not found or path is empty: " + methodName);
		}

		File file = new File(def.getPath());

		MethodFile mf = new MethodFile();
		mf.actions = actions != null ? actions : List.of();
		mf.variables = variables != null ? variables : List.of();
		mf.backendRequests = backendRequests != null ? backendRequests : List.of();

		try (Writer writer = new OutputStreamWriter(
				new FileOutputStream(file), StandardCharsets.UTF_8)) {

			gson.toJson(mf, writer);
			writer.flush();
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to save custom method '" + methodName + "'", ex
			);
			throw new RuntimeException(
					"Failed to save custom method '" + methodName + "': " + ex.getMessage(), ex);
		}
	}

	public List<LocalVariables> loadMethodVariables(String methodName) {
		MethodDef def = findByName(methodName);
		if (def == null || def.getPath() == null || def.getPath().isBlank()) {
			throw new IllegalArgumentException("Custom method not found or path is empty: " + methodName);
		}

		File file = new File(def.getPath());
		if (!file.exists()) {
			throw new IllegalArgumentException("Custom method file not found: " + file.getAbsolutePath());
		}

		try (Reader reader = new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8)) {

			com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(reader);

			if (root.isJsonArray()) {
				// старый формат без variables
				return List.of();
			}

			if (root.isJsonObject()) {
				MethodFile methodFile = gson.fromJson(root, MethodFile.class);
				return methodFile != null && methodFile.getVariables() != null
						? methodFile.getVariables()
						: List.of();
			}

			return List.of();
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to load variables for custom method '" + methodName + "'", ex
			);
			return List.of();
		}
	}

	public List<BackendRequestDef> loadMethodBackendRequests(String methodName) {
		Map<String, BackendRequestDef> collected = new LinkedHashMap<>();
		loadMethodBackendRequestsRecursive(methodName, collected, new LinkedHashSet<>());
		return new ArrayList<>(collected.values());
	}

	private void loadMethodBackendRequestsRecursive(String methodName,
													Map<String, BackendRequestDef> collected,
													Set<String> visitedMethods) {
		if (methodName == null || methodName.isBlank() || !visitedMethods.add(methodName)) {
			return;
		}

		MethodDef def = findByName(methodName);
		if (def == null || def.getPath() == null || def.getPath().isBlank()) {
			return;
		}

		File file = new File(def.getPath());
		if (!file.exists()) {
			return;
		}

		try (Reader reader = new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8)) {

			com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(reader);

			if (root.isJsonObject()) {
				MethodFile methodFile = gson.fromJson(root, MethodFile.class);

				if (methodFile != null && methodFile.getBackendRequests() != null) {
					for (BackendRequestDef req : methodFile.getBackendRequests()) {
						if (req != null && req.getName() != null && !req.getName().isBlank()) {
							collected.putIfAbsent(req.getName(), req);
						}
					}
				}

				if (methodFile != null && methodFile.getActions() != null) {
					for (ActionRecord action : methodFile.getActions()) {
						if (action == null) {
							continue;
						}

						if ("customMethod".equals(action.getAction())) {
							String nestedMethodName = action.getValue();
							loadMethodBackendRequestsRecursive(nestedMethodName, collected, visitedMethods);
						}
					}
				}
			}
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to load backendRequests for custom method '" + methodName + "'",
					ex
			);
		}
	}

	public static class MethodDef {
		private final String name;
		private final String path;

		public MethodDef(String name, String path) {
			this.name = name;
			this.path = path;
		}

		public String getName() {
			return name;
		}

		public String getPath() {
			return path;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public static class MethodFile {
		private List<ActionRecord> actions;
		private List<LocalVariables> variables;
		private List<BackendRequestDef> backendRequests;

		public List<ActionRecord> getActions() {
			return actions;
		}

		public List<LocalVariables> getVariables() {
			return variables;
		}

		public List<BackendRequestDef> getBackendRequests() {
			return backendRequests;
		}
	}

}
