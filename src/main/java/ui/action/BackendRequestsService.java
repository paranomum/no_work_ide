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
	private static final String[] HTTP_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"};

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
		dlg.setSize(860, 900);
		dlg.setLocationRelativeTo(parent);
		dlg.setLayout(new BorderLayout(8, 8));

		// ── NORTH: Name / Method / URL ────────────────────────────────────
		JPanel top = new JPanel(new GridLayout(3, 2, 6, 6));
		top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

		JTextField nameField = new JTextField(def.getName());

		// HTTP-method выпадашка
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

		// ── CENTER: табы Body / Headers ───────────────────────────────────
		String rawBody    = def.getRequestBody()    != null ? def.getRequestBody()    : "";
		String rawHeaders = def.getRequestHeaders() != null ? def.getRequestHeaders() : "{}";

		JTextArea bodyArea = new JTextArea(beautifyJson(rawBody));
		bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

		JTextArea headersArea = new JTextArea(beautifyJson(rawHeaders));
		headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

		// Beautify-кнопки
		JButton bodyBeautifyBtn    = new JButton("Beautify");
		JButton headersBeautifyBtn = new JButton("Beautify");
		for (JButton btn : new JButton[]{bodyBeautifyBtn, headersBeautifyBtn}) {
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

		// Body-таб
		JPanel bodyHeaderBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		bodyHeaderBar.add(bodyBeautifyBtn);
		JPanel bodyTabContent = new JPanel(new BorderLayout());
		bodyTabContent.add(bodyHeaderBar,                 BorderLayout.NORTH);
		bodyTabContent.add(new JScrollPane(bodyArea),     BorderLayout.CENTER);

		// Headers-таб
		JPanel headersHeaderBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		headersHeaderBar.add(headersBeautifyBtn);
		JPanel headersTabContent = new JPanel(new BorderLayout());
		headersTabContent.add(headersHeaderBar,               BorderLayout.NORTH);
		headersTabContent.add(new JScrollPane(headersArea),   BorderLayout.CENTER);

		DefaultTableModel extractorModel = new DefaultTableModel(
				new String[]{"JSON путь (fieldPath)", "Имя переменной"}, 0) {
			@Override public boolean isCellEditable(int r, int c) { return true; }
		};
		for (ResponseFieldExtractor ex : def.getResponseExtractors()) {
			extractorModel.addRow(new Object[]{ex.getFieldPath(), ex.getVariableName()});
		}
		JTable extractorTable = new JTable(extractorModel);
		extractorTable.setRowHeight(22);

		JButton addExtBtn = new JButton("+");
		JButton removeExtBtn = new JButton("-");
		addExtBtn.addActionListener(e -> extractorModel.addRow(new Object[]{"", ""}));
		removeExtBtn.addActionListener(e -> {
			int r = extractorTable.getSelectedRow();
			if (r >= 0) { if (extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing(); extractorModel.removeRow(r); }
		});

		JPanel extBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		extBar.add(new JLabel("Поля ответа:"));
		extBar.add(addExtBtn);
		extBar.add(removeExtBtn);

		JLabel extHint = new JLabel(
				"<html><font color='gray' size='2'>Переменная доступна как <b>${requestName.fieldPath}</b> — отображается как <b>json(fieldPath)</b></font></html>"
		);
		extHint.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

		JPanel extractorTabContent = new JPanel(new BorderLayout(2, 2));
		extractorTabContent.add(extBar, BorderLayout.NORTH);
		extractorTabContent.add(new JScrollPane(extractorTable), BorderLayout.CENTER);
		extractorTabContent.add(extHint, BorderLayout.SOUTH);

		JTabbedPane centerTabs = new JTabbedPane();
		centerTabs.addTab("Request Body", bodyTabContent);
		centerTabs.addTab("Headers",      headersTabContent);
		centerTabs.addTab("Response Extractors", extractorTabContent);

		dlg.add(centerTabs, BorderLayout.CENTER);

		// ── SOUTH: таблица уникальных полей ──────────────────────────────
		//   Колонки: [✓] [Field Path] [Method] [Arg / Variable]

		String[] cols = {"Unique", "Field Path", "Method", "Arg / Variable"};
		DefaultTableModel uniqueModel = new DefaultTableModel(cols, 0) {
			@Override
			public Class<?> getColumnClass(int col) {
				return col == 0 ? Boolean.class : String.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				// колонка Arg/Variable — всегда редактируема (переменная может быть у любого метода)
				return true;
			}
		};

		// загружаем существующие overrides
		for (DtoFieldOverride ov : def.getFieldOverrides()) {
			uniqueModel.addRow(new Object[]{
					ov.isUnique(),
					ov.getFieldPath() != null ? ov.getFieldPath() : "",
					ov.getMethod()    != null ? ov.getMethod()    : VariableAction.GENERATE_EMAIL.getCode(),
					ov.getMethodArg() != null ? ov.getMethodArg() : ""
			});
		}

		JTable uniqueTable = new JTable(uniqueModel);
		uniqueTable.setRowHeight(28);
		uniqueTable.setShowGrid(true);
		uniqueTable.setGridColor(new Color(180, 180, 180));

		// ширины колонок
		uniqueTable.getColumnModel().getColumn(0).setPreferredWidth(55);
		uniqueTable.getColumnModel().getColumn(0).setMaxWidth(60);
		uniqueTable.getColumnModel().getColumn(1).setPreferredWidth(280);
		uniqueTable.getColumnModel().getColumn(2).setPreferredWidth(180);
		uniqueTable.getColumnModel().getColumn(3).setPreferredWidth(230);

		// Method-колонка — все варианты VariableAction + "use variable"
		String[] methodOptions = buildMethodOptions();
		JComboBox<String> methodCombo = new JComboBox<>(methodOptions);
		uniqueTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(methodCombo) {
			@Override
			public boolean stopCellEditing() {
				String selected = (String) methodCombo.getSelectedItem();
				if ("use variable".equals(selected)) {
					// показываем диалог выбора переменной
					String varName = showVariablePickerDialog(dlg);
					if (varName != null) {
						int row = uniqueTable.getEditingRow();
						super.stopCellEditing();
						// вставляем "${varName}" в колонку Arg/Variable
						if (row >= 0) {
							uniqueModel.setValueAt("use variable", row, 2);
							uniqueModel.setValueAt("${" + varName + "}", row, 3);
						}
						return true;
					}
					// отмена — откатываем выбор на предыдущий
					cancelCellEditing();
					return false;
				}
				return super.stopCellEditing();
			}
		});

		// Рендерер колонки Arg/Variable — подсвечиваем ${...} серым фоном
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

		// repaint при изменении Method чтобы Arg подхватил вид
		uniqueModel.addTableModelListener(e -> {
			if (e.getColumn() == 2) uniqueTable.repaint();
		});

		// скроллпейн побольше
		JScrollPane uniqueScroll = new JScrollPane(uniqueTable);
		uniqueScroll.setPreferredSize(new Dimension(820, 200));
		uniqueScroll.setBorder(BorderFactory.createTitledBorder("Unique Fields"));

		// ── кнопки таблицы: +, -, Parse ──────────────────────────────────
		JButton addFieldBtn    = new JButton("+");
		JButton removeFieldBtn = new JButton("-");
		JButton parseBtn       = new JButton("⬇ Parse fields from Body");
		parseBtn.setFont(parseBtn.getFont().deriveFont(11f));
		parseBtn.setToolTipText("Разобрать JSON тела и добавить все листовые поля в таблицу");

		addFieldBtn.addActionListener(e ->
				uniqueModel.addRow(new Object[]{false, "",
						VariableAction.GENERATE_EMAIL.getCode(), ""}));

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
					uniqueModel.addRow(new Object[]{false, path,
							VariableAction.GENERATE_EMAIL.getCode(), ""});
					added++;
				}
			}
			// переключаемся на Body-таб если были на Headers
			centerTabs.setSelectedIndex(0);
			JOptionPane.showMessageDialog(dlg,
					"Добавлено " + added + " полей (дубликаты пропущены).",
					"Done", JOptionPane.INFORMATION_MESSAGE);
		});

		JPanel fieldTopBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		fieldTopBar.add(new JLabel("Fields:"));
		fieldTopBar.add(addFieldBtn);
		fieldTopBar.add(removeFieldBtn);
		fieldTopBar.add(parseBtn);

		JPanel uniquePanel = new JPanel(new BorderLayout(2, 2));
		uniquePanel.add(fieldTopBar,  BorderLayout.NORTH);
		uniquePanel.add(uniqueScroll, BorderLayout.CENTER);

		// подсказка для колонки Arg/Variable
		JLabel argHint = new JLabel(
				"<html><font color='gray' size='2'>" +
						"Arg/Variable: для addUuid — дефолтный префикс; " +
						"для «use variable» — вставляется ${varName}; " +
						"оба варианта можно комбинировать (addUuid + ${var} тоже сработает)." +
						"</font></html>"
		);
		argHint.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));

		// ── Save / Cancel ─────────────────────────────────────────────────
		JButton saveBtn   = new JButton("Save");
		JButton cancelBtn = new JButton("Cancel");

		saveBtn.addActionListener(e -> {
			if (uniqueTable.isEditing()) uniqueTable.getCellEditor().stopCellEditing();

			def.setName(nameField.getText().trim());
			def.setMethod(Objects.toString(httpMethodCombo.getSelectedItem(), "GET"));
			def.setUrl(urlField.getText().trim());
			def.setRequestBody(bodyArea.getText());
			def.setRequestHeaders(headersArea.getText());

			List<DtoFieldOverride> overrides = new ArrayList<>();
			for (int r = 0; r < uniqueModel.getRowCount(); r++) {
				boolean isUnique = Boolean.TRUE.equals(uniqueModel.getValueAt(r, 0));
				String fieldPath = Objects.toString(uniqueModel.getValueAt(r, 1), "").trim();
				String method    = Objects.toString(uniqueModel.getValueAt(r, 2), "").trim();
				String arg       = Objects.toString(uniqueModel.getValueAt(r, 3), "").trim();
				if (fieldPath.isEmpty()) continue;
				overrides.add(new DtoFieldOverride(fieldPath, method, arg, isUnique));
			}
			def.setFieldOverrides(overrides);

			save();
			if (extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing();
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
			dlg.dispose();
		});
		cancelBtn.addActionListener(e -> dlg.dispose());

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(saveBtn);
		buttons.add(cancelBtn);

		JPanel southPanel = new JPanel(new BorderLayout(4, 2));
		southPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		southPanel.add(uniquePanel, BorderLayout.CENTER);
		southPanel.add(argHint,     BorderLayout.NORTH);
		southPanel.add(buttons,     BorderLayout.SOUTH);

		dlg.add(southPanel, BorderLayout.SOUTH);
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