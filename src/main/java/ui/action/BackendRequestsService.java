package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dto.AppConfig;
import dto.BackendRequestDef;
import dto.DtoFieldOverride;
import dto.ResponseFieldExtractor;
import model.VariableAction;
import ui.AbstractTableSettingsPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public class BackendRequestsService extends AbstractTableSettingsPanel {

	private static final String[] TABLE_COLUMNS = {"Name", "Method", "URL"};

	// HTTP-методы для выпадашки
	private static final String[] HTTP_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE"};

	private JTable backendTable;
	private DefaultTableModel backendTableModel;

	private final ConfigService configService;
	private final AppConfig config;
	private final List<BackendRequestDef> requests = new ArrayList<>();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	/** Прокидывается из ActionWindow после создания, нужен для выбора переменных */
	private VariablesService variablesService;

	public BackendRequestsService(ConfigService configService, AppConfig config) {
		this.configService = configService;
		this.config = config;
	}

	public void setVariablesService(VariablesService variablesService) {
		this.variablesService = variablesService;
	}

	// ──────────────────────────────────────────────────────────────────────
	//  Панель настроек
	// ──────────────────────────────────────────────────────────────────────

	public JPanel createBackendRequestsSettingsPanel(JDialog parentDialog) {
		JPanel panel = buildTablePanel(
				"Backend Requests",
				TABLE_COLUMNS,
				() -> saveFromTable(parentDialog),
				null
		);

		this.backendTable = this.table;
		this.backendTableModel = this.model;

		// HTTP-method комбобокс в колонке "Method" таблицы списка
		JComboBox<String> httpCombo = new JComboBox<>(HTTP_METHODS);
		backendTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(httpCombo));

		JButton editDtoBtn = new JButton("Edit DTO");
		editDtoBtn.addActionListener(e -> openEditDtoDialog(parentDialog));

		Component southComp = ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
		if (southComp instanceof JPanel southPanel) {
			southPanel.add(editDtoBtn, 0);
		}

		load();
		loadIntoTable();
		return panel;
	}

	private void loadIntoTable() {
		backendTableModel.setRowCount(0);
		for (BackendRequestDef r : requests) {
			backendTableModel.addRow(new Object[]{r.getName(), r.getMethod(), r.getUrl()});
		}
	}

	private void saveFromTable(JDialog parentDialog) {
		List<BackendRequestDef> updated = new ArrayList<>();
		for (int row = 0; row < backendTableModel.getRowCount(); row++) {
			String name   = Objects.toString(backendTableModel.getValueAt(row, 0), "").trim();
			String method = Objects.toString(backendTableModel.getValueAt(row, 1), "").trim();
			String url    = Objects.toString(backendTableModel.getValueAt(row, 2), "").trim();
			if (!name.isEmpty() || !url.isEmpty()) {
				BackendRequestDef original = findByName(name);
				if (original != null) {
					original.setMethod(method);
					original.setUrl(url);
					updated.add(original);
				} else {
					updated.add(new BackendRequestDef(name, url, method, "", "{}", null));
				}
			}
		}
		Set<String> names = new java.util.HashSet<>();
		for (BackendRequestDef r : updated) {
			if (!names.add(r.getName())) {
				JOptionPane.showMessageDialog(parentDialog,
						"Дублирующееся имя: '" + r.getName() + "'. Все имена должны быть уникальны.",
						"Ошибка валидации", JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
		setRequests(updated);
		save();
		JOptionPane.showMessageDialog(parentDialog, "Backend requests saved", "Saved",
				JOptionPane.INFORMATION_MESSAGE);
	}

	private void openEditDtoDialog(JDialog parentDialog) {
		int row = backendTable.getSelectedRow();
		if (row < 0) {
			JOptionPane.showMessageDialog(parentDialog, "Select a request first",
					"No selection", JOptionPane.WARNING_MESSAGE);
			return;
		}
		String name = Objects.toString(backendTableModel.getValueAt(row, 0), "").trim();
		BackendRequestDef def = findByName(name);
		if (def != null) {
			openEditDtoDialogFor(parentDialog, def);
		}
	}

	// ──────────────────────────────────────────────────────────────────────
	//  Edit DTO — главный диалог
	// ──────────────────────────────────────────────────────────────────────

	public void openEditDtoDialogFor(Component parent, BackendRequestDef def) {
		JDialog dlg = new JDialog(
				SwingUtilities.getWindowAncestor(parent),
				"Edit DTO — " + def.getName(),
				Dialog.ModalityType.APPLICATION_MODAL
		);
		dlg.setSize(980, 920);
		dlg.setLocationRelativeTo(parent);
		dlg.setLayout(new BorderLayout(8, 8));

		JPanel top = new JPanel(new GridLayout(3, 2, 6, 6));
		top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

		JTextField nameField = new JTextField(def.getName());

		JComboBox<String> httpMethodCombo = new JComboBox<>(HTTP_METHODS);
		String currentMethod = def.getMethod() != null ? def.getMethod().toUpperCase() : "POST";
		httpMethodCombo.setSelectedItem(
				Arrays.asList(HTTP_METHODS).contains(currentMethod) ? currentMethod : HTTP_METHODS[0]
		);

		JTextField urlField = new JTextField(def.getUrl());

		top.add(new JLabel("Name:"));   top.add(nameField);
		top.add(new JLabel("Method:")); top.add(httpMethodCombo);
		top.add(new JLabel("URL:"));    top.add(urlField);

		dlg.add(top, BorderLayout.NORTH);

		String rawBody = def.getRequestBody() != null ? def.getRequestBody() : "";
		String rawHeaders = def.getRequestHeaders() != null ? def.getRequestHeaders() : "{}";
		String rawResponse = def.getCapturedResponseBody() != null ? def.getCapturedResponseBody() : "";

		JTextArea bodyArea = new JTextArea(beautifyJson(rawBody));
		bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

		JTextArea headersArea = new JTextArea(beautifyJson(rawHeaders));
		headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		headersArea.setRows(6);

		JTextArea responseBodyArea = new JTextArea(beautifyJson(rawResponse));
		responseBodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

		JButton bodyBeautifyBtn = new JButton("Beautify");
		JButton headersBeautifyBtn = new JButton("Beautify");
		JButton responseBeautifyBtn = new JButton("Beautify");

		for (JButton btn : new JButton[]{bodyBeautifyBtn, headersBeautifyBtn, responseBeautifyBtn}) {
			btn.setFont(btn.getFont().deriveFont(11f));
			btn.setFocusable(false);
		}

		bodyBeautifyBtn.addActionListener(e -> {
			String pretty = beautifyJson(bodyArea.getText());
			bodyArea.setText(pretty);
			bodyArea.setCaretPosition(0);
		});

		headersBeautifyBtn.addActionListener(e -> {
			String pretty = beautifyJson(headersArea.getText());
			headersArea.setText(pretty);
			headersArea.setCaretPosition(0);
		});

		responseBeautifyBtn.addActionListener(e -> {
			String pretty = beautifyJson(responseBodyArea.getText());
			responseBodyArea.setText(pretty);
			responseBodyArea.setCaretPosition(0);
		});

		JPanel bodyToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		bodyToolbar.add(new JLabel("Request Body"));
		bodyToolbar.add(bodyBeautifyBtn);

		JPanel headersToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		headersToolbar.add(new JLabel("Headers"));
		headersToolbar.add(headersBeautifyBtn);

		JPanel responseToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		responseToolbar.add(new JLabel("Response Body Template"));
		responseToolbar.add(responseBeautifyBtn);

		JPanel bodyPanel = new JPanel(new BorderLayout());
		bodyPanel.add(bodyToolbar, BorderLayout.NORTH);
		bodyPanel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);

		JPanel headersPanel = new JPanel(new BorderLayout());
		headersPanel.add(headersToolbar, BorderLayout.NORTH);
		headersPanel.add(new JScrollPane(headersArea), BorderLayout.CENTER);

		JSplitPane dtoTopSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bodyPanel, headersPanel);
		dtoTopSplit.setResizeWeight(0.78);
		dtoTopSplit.setDividerLocation(360);

		String[] cols = {"Unique", "Field Path", "Method", "Arg / Variable"};
		DefaultTableModel uniqueModel = new DefaultTableModel(cols, 0) {
			@Override
			public Class<?> getColumnClass(int col) {
				return col == 0 ? Boolean.class : String.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				return true;
			}
		};

		for (DtoFieldOverride ov : def.getFieldOverrides()) {
			uniqueModel.addRow(new Object[]{
					ov.isUnique(),
					ov.getFieldPath() != null ? ov.getFieldPath() : "",
					ov.getMethod() != null ? ov.getMethod() : VariableAction.GENERATE_EMAIL.getCode(),
					ov.getMethodArg() != null ? ov.getMethodArg() : ""
			});
		}

		JTable uniqueTable = new JTable(uniqueModel);
		uniqueTable.setRowHeight(28);
		uniqueTable.setShowGrid(true);
		uniqueTable.setGridColor(new Color(180, 180, 180));

		uniqueTable.getColumnModel().getColumn(0).setPreferredWidth(55);
		uniqueTable.getColumnModel().getColumn(0).setMaxWidth(60);
		uniqueTable.getColumnModel().getColumn(1).setPreferredWidth(280);
		uniqueTable.getColumnModel().getColumn(2).setPreferredWidth(180);
		uniqueTable.getColumnModel().getColumn(3).setPreferredWidth(230);

		String[] methodOptions = buildMethodOptions();
		JComboBox<String> methodCombo = new JComboBox<>(methodOptions);
		uniqueTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(methodCombo) {
			@Override
			public boolean stopCellEditing() {
				String selected = (String) methodCombo.getSelectedItem();
				if ("use variable".equals(selected)) {
					String varName = showVariablePickerDialog(dlg);
					if (varName != null) {
						int row = uniqueTable.getEditingRow();
						super.stopCellEditing();
						if (row >= 0) {
							uniqueModel.setValueAt("use variable", row, 2);
							uniqueModel.setValueAt("${" + varName + "}", row, 3);
						}
						return true;
					}
					cancelCellEditing();
					return false;
				}
				return super.stopCellEditing();
			}
		});

		uniqueTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(
					JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
				Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
				String val = Objects.toString(value, "");
				if (!isSelected) {
					if (val.startsWith("${") && val.endsWith("}")) {
						c.setBackground(new Color(230, 245, 255));
						c.setForeground(new Color(0, 80, 160));
					} else {
						c.setBackground(t.getBackground());
						c.setForeground(t.getForeground());
					}
				}
				return c;
			}
		});

		uniqueModel.addTableModelListener(e -> {
			if (e.getColumn() == 2) uniqueTable.repaint();
		});

		JButton addFieldBtn = new JButton("+");
		JButton removeFieldBtn = new JButton("-");
		JButton parseBtn = new JButton("⬇ Parse fields from Body");
		parseBtn.setFont(parseBtn.getFont().deriveFont(11f));

		addFieldBtn.addActionListener(e ->
				uniqueModel.addRow(new Object[]{false, "", VariableAction.GENERATE_EMAIL.getCode(), ""}));

		removeFieldBtn.addActionListener(e -> {
			int row = uniqueTable.getSelectedRow();
			if (row >= 0) {
				if (uniqueTable.isEditing()) uniqueTable.getCellEditor().stopCellEditing();
				uniqueModel.removeRow(row);
			}
		});

		parseBtn.addActionListener(e -> {
			if (uniqueTable.isEditing()) uniqueTable.getCellEditor().stopCellEditing();
			List<String> paths = extractJsonLeafPaths(bodyArea.getText().trim());
			if (paths.isEmpty()) {
				JOptionPane.showMessageDialog(dlg,
						"Не удалось разобрать JSON или тело пустое.",
						"Parse error", JOptionPane.WARNING_MESSAGE);
				return;
			}

			Set<String> existing = new HashSet<>();
			for (int r = 0; r < uniqueModel.getRowCount(); r++) {
				existing.add(Objects.toString(uniqueModel.getValueAt(r, 1), "").trim());
			}

			int added = 0;
			for (String path : paths) {
				if (!existing.contains(path)) {
					uniqueModel.addRow(new Object[]{false, path, VariableAction.GENERATE_EMAIL.getCode(), ""});
					added++;
				}
			}

			JOptionPane.showMessageDialog(dlg,
					"Добавлено " + added + " полей (дубликаты пропущены).",
					"Done", JOptionPane.INFORMATION_MESSAGE);
		});

		JPanel fieldTopBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		fieldTopBar.add(new JLabel("Unique Fields"));
		fieldTopBar.add(addFieldBtn);
		fieldTopBar.add(removeFieldBtn);
		fieldTopBar.add(parseBtn);

		JLabel argHint = new JLabel(
				"<html><font color='gray' size='2'>" +
						"Arg/Variable: для addUuid — дефолтный префикс; для «use variable» — вставляется ${varName}; " +
						"оба варианта можно комбинировать (addUuid + ${var} тоже сработает)." +
						"</font></html>"
		);
		argHint.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));

		JPanel uniquePanel = new JPanel(new BorderLayout(2, 2));
		uniquePanel.add(fieldTopBar, BorderLayout.NORTH);
		uniquePanel.add(new JScrollPane(uniqueTable), BorderLayout.CENTER);
		uniquePanel.add(argHint, BorderLayout.SOUTH);

		JSplitPane dtoTabSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, dtoTopSplit, uniquePanel);
		dtoTabSplit.setResizeWeight(0.58);
		dtoTabSplit.setDividerLocation(430);

		DefaultTableModel extractorModel = new DefaultTableModel(
				new String[]{"JSON путь (fieldPath)", "Имя переменной"}, 0) {
			@Override
			public boolean isCellEditable(int r, int c) {
				return true;
			}
		};

		for (ResponseFieldExtractor ex : def.getResponseExtractors()) {
			extractorModel.addRow(new Object[]{ex.getFieldPath(), ex.getVariableName()});
		}

		JTable extractorTable = new JTable(extractorModel);
		extractorTable.setRowHeight(22);

		JButton addExtBtn = new JButton("+");
		JButton removeExtBtn = new JButton("-");
		JButton parseResponseBtn = new JButton("⬇ Parse fields from Response");
		parseResponseBtn.setFont(parseResponseBtn.getFont().deriveFont(11f));

		addExtBtn.addActionListener(e -> extractorModel.addRow(new Object[]{"", ""}));

		removeExtBtn.addActionListener(e -> {
			int r = extractorTable.getSelectedRow();
			if (r >= 0) {
				if (extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing();
				extractorModel.removeRow(r);
			}
		});

		parseResponseBtn.addActionListener(e -> {
			if (extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing();

			List<String> paths = extractJsonLeafPaths(responseBodyArea.getText().trim());
			if (paths.isEmpty()) {
				JOptionPane.showMessageDialog(dlg,
						"Не удалось разобрать JSON ответа или шаблон ответа пустой.",
						"Parse error", JOptionPane.WARNING_MESSAGE);
				return;
			}

			Set<String> existing = new HashSet<>();
			for (int r = 0; r < extractorModel.getRowCount(); r++) {
				existing.add(Objects.toString(extractorModel.getValueAt(r, 0), "").trim());
			}

			String reqName = nameField.getText().trim().isEmpty() ? def.getName() : nameField.getText().trim();
			int added = 0;

			for (String path : paths) {
				if (!existing.contains(path)) {
					extractorModel.addRow(new Object[]{path, reqName + "." + path});
					added++;
				}
			}

			JOptionPane.showMessageDialog(dlg,
					"Добавлено " + added + " полей из ответа (дубликаты пропущены).",
					"Done", JOptionPane.INFORMATION_MESSAGE);
		});

		JPanel extractorTopBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		extractorTopBar.add(new JLabel("Response Extractors"));
		extractorTopBar.add(addExtBtn);
		extractorTopBar.add(removeExtBtn);
		extractorTopBar.add(parseResponseBtn);

		JLabel extHint = new JLabel(
				"<html><font color='gray' size='2'>" +
						"Переменная доступна как <b>${requestName.fieldPath}</b> — отображается как <b>json(fieldPath)</b>" +
						"</font></html>"
		);
		extHint.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

		JPanel responseBodyPanel = new JPanel(new BorderLayout());
		responseBodyPanel.add(responseToolbar, BorderLayout.NORTH);
		responseBodyPanel.add(new JScrollPane(responseBodyArea), BorderLayout.CENTER);

		JPanel extractorPanel = new JPanel(new BorderLayout(2, 2));
		extractorPanel.add(extractorTopBar, BorderLayout.NORTH);
		extractorPanel.add(new JScrollPane(extractorTable), BorderLayout.CENTER);
		extractorPanel.add(extHint, BorderLayout.SOUTH);

		JSplitPane responseTabSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, responseBodyPanel, extractorPanel);
		responseTabSplit.setResizeWeight(0.58);
		responseTabSplit.setDividerLocation(430);

		JTabbedPane centerTabs = new JTabbedPane();
		centerTabs.addTab("DTO Template", dtoTabSplit);
		centerTabs.addTab("Response Template", responseTabSplit);

		dlg.add(centerTabs, BorderLayout.CENTER);

		JButton saveBtn = new JButton("Save");
		JButton cancelBtn = new JButton("Cancel");

		saveBtn.addActionListener(e -> {
			if (uniqueTable.isEditing()) uniqueTable.getCellEditor().stopCellEditing();
			if (extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing();

			def.setName(nameField.getText().trim());
			def.setMethod(Objects.toString(httpMethodCombo.getSelectedItem(), "GET"));
			def.setUrl(urlField.getText().trim());
			def.setRequestBody(bodyArea.getText());
			def.setRequestHeaders(headersArea.getText());
			def.setCapturedResponseBody(responseBodyArea.getText());

			List<DtoFieldOverride> overrides = new ArrayList<>();
			for (int r = 0; r < uniqueModel.getRowCount(); r++) {
				boolean isUnique = Boolean.TRUE.equals(uniqueModel.getValueAt(r, 0));
				String fieldPath = Objects.toString(uniqueModel.getValueAt(r, 1), "").trim();
				String method = Objects.toString(uniqueModel.getValueAt(r, 2), "").trim();
				String arg = Objects.toString(uniqueModel.getValueAt(r, 3), "").trim();
				if (fieldPath.isEmpty()) continue;
				overrides.add(new DtoFieldOverride(fieldPath, method, arg, isUnique));
			}
			def.setFieldOverrides(overrides);

			List<ResponseFieldExtractor> extractors = new ArrayList<>();
			for (int r = 0; r < extractorModel.getRowCount(); r++) {
				String fp = String.valueOf(extractorModel.getValueAt(r, 0)).trim();
				String vn = String.valueOf(extractorModel.getValueAt(r, 1)).trim();
				if (!fp.isEmpty()) {
					if (vn.isEmpty()) vn = def.getName() + "." + fp;
					extractors.add(new ResponseFieldExtractor(fp, vn));
				}
			}
			def.setResponseExtractors(extractors);

			save();
			dlg.dispose();
		});

		cancelBtn.addActionListener(e -> dlg.dispose());

		JButton mergeBtn = new JButton("🔄 Обновить DTO (сохранить настройки)");
		mergeBtn.setToolTipText(
				"Заменяет тело запроса, заголовки и шаблон ответа новым значением, НЕ затирая fieldOverrides и responseExtractors");

		mergeBtn.addActionListener(e -> {
			if (uniqueTable.isEditing()) uniqueTable.getCellEditor().stopCellEditing();
			if (extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing();

			List<DtoFieldOverride> existingOverrides = new ArrayList<>();
			for (int r = 0; r < uniqueModel.getRowCount(); r++) {
				boolean isUnique = Boolean.TRUE.equals(uniqueModel.getValueAt(r, 0));
				String fieldPath = Objects.toString(uniqueModel.getValueAt(r, 1), "").trim();
				String method = Objects.toString(uniqueModel.getValueAt(r, 2), "").trim();
				String arg = Objects.toString(uniqueModel.getValueAt(r, 3), "").trim();
				if (!fieldPath.isEmpty()) {
					existingOverrides.add(new DtoFieldOverride(fieldPath, method, arg, isUnique));
				}
			}

			List<ResponseFieldExtractor> existingExtractors = new ArrayList<>();
			for (int r = 0; r < extractorModel.getRowCount(); r++) {
				String fp = String.valueOf(extractorModel.getValueAt(r, 0)).trim();
				String vn = String.valueOf(extractorModel.getValueAt(r, 1)).trim();
				if (!fp.isEmpty()) {
					if (vn.isEmpty()) vn = nameField.getText().trim() + "." + fp;
					existingExtractors.add(new ResponseFieldExtractor(fp, vn));
				}
			}

			def.setName(nameField.getText().trim());
			def.setMethod(Objects.toString(httpMethodCombo.getSelectedItem(), "GET"));
			def.setUrl(urlField.getText().trim());
			def.setRequestBody(bodyArea.getText());
			def.setRequestHeaders(headersArea.getText());
			def.setCapturedResponseBody(responseBodyArea.getText());
			def.setFieldOverrides(existingOverrides);
			def.setResponseExtractors(existingExtractors);

			List<String> newPaths = extractJsonLeafPaths(bodyArea.getText().trim());
			Set<String> existingPaths = existingOverrides.stream()
					.map(DtoFieldOverride::getFieldPath)
					.collect(java.util.stream.Collectors.toSet());

			int added = 0;
			for (String path : newPaths) {
				if (!existingPaths.contains(path)) {
					def.getFieldOverrides().add(
							new DtoFieldOverride(path, VariableAction.GENERATE_EMAIL.getCode(), "", false)
					);
					added++;
				}
			}

			save();
			dlg.dispose();

			if (added > 0) {
				JOptionPane.showMessageDialog(parent,
						"DTO обновлено. Добавлено " + added + " новых полей в fieldOverrides.\n" +
								"Шаблон ответа и предыдущие настройки сохранены.",
						"Merge Done", JOptionPane.INFORMATION_MESSAGE);
			} else {
				JOptionPane.showMessageDialog(parent,
						"DTO обновлено. Шаблон ответа и предыдущие настройки сохранены.",
						"Merge Done", JOptionPane.INFORMATION_MESSAGE);
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(saveBtn);
		buttons.add(cancelBtn);
		buttons.add(mergeBtn);

		JPanel south = new JPanel(new BorderLayout());
		south.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		south.add(buttons, BorderLayout.EAST);

		dlg.add(south, BorderLayout.SOUTH);
		dlg.setVisible(true);
	}

	// ──────────────────────────────────────────────────────────────────────
	//  Выбор переменной
	// ──────────────────────────────────────────────────────────────────────

	/**
	 * Показывает диалог выбора переменной из VariablesService.
	 * Возвращает имя переменной без обёртки ${}, или null если отмена / нет переменных.
	 */
	private String showVariablePickerDialog(Component parent) {
		if (variablesService == null) {
			JOptionPane.showMessageDialog(parent,
					"VariablesService недоступен. Убедитесь что переменные настроены в Settings.",
					"Нет переменных", JOptionPane.WARNING_MESSAGE);
			return null;
		}

		List<String> names = variablesService.getVariableNames();
		if (names.isEmpty()) {
			JOptionPane.showMessageDialog(parent,
					"Переменные не заданы. Добавьте их в Settings → Variables.",
					"Нет переменных", JOptionPane.INFORMATION_MESSAGE);
			return null;
		}

		String selected = (String) JOptionPane.showInputDialog(
				parent,
				"Выберите переменную:",
				"Выбор переменной",
				JOptionPane.PLAIN_MESSAGE,
				null,
				names.toArray(),
				names.get(0)
		);
		return selected; // null если отмена
	}

	/**
	 * Строит список опций для комбобокса Method:
	 * все значения VariableAction + разделитель + "use variable"
	 */
	private String[] buildMethodOptions() {
		List<String> options = new ArrayList<>();
		for (VariableAction a : VariableAction.values()) {
			options.add(a.getCode());
		}
		options.add("──────────");   // визуальный разделитель
		options.add("use variable");
		return options.toArray(new String[0]);
	}

	// ──────────────────────────────────────────────────────────────────────
	//  JSON-утилиты
	// ──────────────────────────────────────────────────────────────────────

	/** Beautify JSON. Если невалидный — возвращает как есть. */
	private String beautifyJson(String raw) {
		if (raw == null || raw.isBlank()) return raw != null ? raw : "";
		try {
			JsonElement el = JsonParser.parseString(raw);
			return gson.toJson(el);
		} catch (JsonSyntaxException e) {
			return raw;
		}
	}

	/**
	 * Рекурсивно обходит JSON и собирает пути до листовых значений.
	 * {"user":{"email":"a"}} → ["user.email"]
	 */
	private List<String> extractJsonLeafPaths(String jsonText) {
		List<String> paths = new ArrayList<>();
		if (jsonText == null || jsonText.isBlank()) return paths;
		try {
			JsonElement root = JsonParser.parseString(jsonText);
			collectLeafPaths(root, "", paths);
		} catch (JsonSyntaxException ignored) {}
		return paths;
	}

	private void collectLeafPaths(JsonElement element, String prefix, List<String> result) {
		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				String key     = entry.getKey();
				String newPath = prefix.isEmpty() ? key : prefix + "." + key;
				JsonElement child = entry.getValue();
				if (child.isJsonObject()) {
					collectLeafPaths(child, newPath, result);
				} else if (child.isJsonArray()) {
					var arr = child.getAsJsonArray();
					if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
						collectLeafPaths(arr.get(0), newPath + "[0]", result);
					} else {
						result.add(newPath);
					}
				} else {
					result.add(newPath);
				}
			}
		}
	}

	// ──────────────────────────────────────────────────────────────────────
	//  CRUD + персистентность
	// ──────────────────────────────────────────────────────────────────────

	public List<BackendRequestDef> getRequests() {
		return Collections.unmodifiableList(requests);
	}

	public void setRequests(List<BackendRequestDef> list) {
		requests.clear();
		if (list != null) requests.addAll(list);
	}

	public void addRequest(BackendRequestDef def) {
		BackendRequestDef existing = findByName(def.getName());
		if (existing != null) {
			throw new IllegalArgumentException("Запрос с именем '" + def.getName() + "' уже существует. Используйте другое имя.");
		}
		requests.add(def);
	}

	public void addOrReplaceRequest(BackendRequestDef def) {
		for (int i = 0; i < requests.size(); i++) {
			if (Objects.equals(requests.get(i).getName(), def.getName())) {
				requests.set(i, def);
				return;
			}
		}
		requests.add(def);
	}

	public void removeRequest(int index) {
		requests.remove(index);
	}

	public BackendRequestDef findByName(String name) {
		for (BackendRequestDef r : requests) {
			if (Objects.equals(r.getName(), name)) return r;
		}
		return null;
	}

	private Path getFile() throws Exception {
		return configService.getBackendRequestsFile(config);
	}

	public void load() {
		requests.clear();
		try {
			Path file = getFile();
			if (!Files.exists(file)) return;
			String json = Files.readString(file);
			BackendRequestDef[] arr = gson.fromJson(json, BackendRequestDef[].class);
			if (arr != null) requests.addAll(Arrays.asList(arr));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void save() {
		try {
			Path file = getFile();
			String json = gson.toJson(requests.toArray(new BackendRequestDef[0]));
			Files.writeString(file, json, StandardCharsets.UTF_8);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}