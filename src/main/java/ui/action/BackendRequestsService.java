package ui.action;

import com.google.gson.*;
import dto.*;
import model.VariableAction;
import ui.AbstractTableSettingsPanel;
import ui.ActionWindow;

import javax.swing.*;
import javax.swing.event.UndoableEditEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.*;

public class BackendRequestsService extends AbstractTableSettingsPanel {

	private static final String[] TABLE_COLUMNS = {"Name", "Method", "URL", "Body type"};
	private static final String[] HTTP_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE"};
	private final AppConfig config;
	private final List<BackendRequestDef> requests = new ArrayList<>();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	/**
	 * ВАЖНО:
	 * таблица теперь держит ссылку на исходный объект,
	 * а не "старое имя", чтобы rename работал как update.
	 */
	private final Map<Integer, BackendRequestDef> rowToRequestRef = new HashMap<>();
	private JTable backendTable;
	private DefaultTableModel backendTableModel;
	private final ActionWindow currentActionWindow;
	/**
	 * Прокидывается из ActionWindow после создания, нужен для выбора переменных
	 */
	private VariablesService variablesService;

	public BackendRequestsService(ActionWindow currentActionWindow, AppConfig config) {
		this.currentActionWindow = currentActionWindow;
		this.config = config;
	}

	public void setVariablesService(VariablesService variablesService) {
		this.variablesService = variablesService;
	}

	// ──────────────────────────────────────────────────────────────────────
	// Панель настроек
	// ──────────────────────────────────────────────────────────────────────

	public JPanel createBackendRequestsSettingsPanel(JDialog parentDialog) {
		JPanel panel = buildTablePanel(
				"Backend Requests",
				TABLE_COLUMNS,
				() -> saveFromTable(parentDialog),
				null
		);

		this.backendTable = this.table;

		DefaultTableModel readOnlyModel = new DefaultTableModel(TABLE_COLUMNS, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		this.backendTable.setModel(readOnlyModel);
		this.backendTableModel = readOnlyModel;

		// ВАЖНО: синхронизируем модель родительской панели,
		// иначе кнопки add/remove из buildTablePanel работают со старой моделью
		this.model = readOnlyModel;

		JButton editDtoBtn = new JButton("Редактировать метод");
		editDtoBtn.setToolTipText("Редактировать название, url, тип метода и request/response body");
		editDtoBtn.addActionListener(e -> openEditDtoDialog(parentDialog));

		Component southComp = ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
		if (southComp instanceof JPanel southPanel) {
			southPanel.add(editDtoBtn, 0);
		}

		loadIntoTable();
		return panel;
	}

	private void loadIntoTable() {
		backendTableModel.setRowCount(0);
		rowToRequestRef.clear();

		int row = 0;
		for (BackendRequestDef r : requests) {
			backendTableModel.addRow(new Object[]{
					r.getName(),
					r.getMethod(),
					r.getUrl(),
					r.getBodyType() != null ? r.getBodyType() : "JSON"
			});
			rowToRequestRef.put(row, r);
			row++;
		}
	}

	private void saveFromTable(JDialog parentDialog) {
		List<BackendRequestDef> oldRequests = new ArrayList<>(requests);
		List<BackendRequestDef> updated = new ArrayList<>();
		Map<String, BackendRequestDef> renamedRequests = new LinkedHashMap<>();

		try {
			for (int row = 0; row < backendTableModel.getRowCount(); row++) {
				String name = safeTrim(Objects.toString(backendTableModel.getValueAt(row, 0), ""));
				String method = safeTrim(Objects.toString(backendTableModel.getValueAt(row, 1), ""));
				String url = safeTrim(Objects.toString(backendTableModel.getValueAt(row, 2), ""));

				if (name.isEmpty() && url.isEmpty()) {
					continue;
				}

				if (name.isEmpty()) {
					JOptionPane.showMessageDialog(
							parentDialog,
							"Имя backend-метода не должно быть пустым.",
							"Ошибка валидации",
							JOptionPane.ERROR_MESSAGE
					);
					return;
				}

				BackendRequestDef original = rowToRequestRef.get(row);

				if (original != null) {
					String oldName = safeTrim(original.getName());

					original.setName(name);
					original.setMethod(method);
					original.setUrl(url);
					updated.add(original);

					if (!oldName.isEmpty() && !Objects.equals(oldName, name)) {
						renamedRequests.put(oldName, original);
					}
				} else {
					BackendRequestDef created = new BackendRequestDef(name, url, method, "", "{}", null);
					created.setBodyType("JSON");
					created.setFormData(new ArrayList<>());
					created.setToken("");
					updated.add(created);
				}
			}

			validateUniqueNames(updated, parentDialog);

			List<BackendRequestDef> removedRequests = detectRemovedRequests(oldRequests, updated);

			setRequests(updated);

			if (variablesService != null) {
				for (Map.Entry<String, BackendRequestDef> e : renamedRequests.entrySet()) {
					String oldName = e.getKey();
					BackendRequestDef renamedDef = e.getValue();
					renameVariablesInService(oldName, renamedDef.getName(), renamedDef.getResponseExtractors());
				}

				for (BackendRequestDef removedReq : removedRequests) {
					removeVariablesForRequest(removedReq);
				}

				variablesService.refreshTableFromVariables();
			}

			for (Map.Entry<String, BackendRequestDef> e : renamedRequests.entrySet()) {
				String oldName = safeTrim(e.getKey());
				String newName = safeTrim(e.getValue().getName());
				renameBackendMethod(oldName, newName);
			}

			save();
			loadIntoTable();

			JOptionPane.showMessageDialog(
					parentDialog,
					"Backend requests saved",
					"Saved",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (IllegalStateException ex) {
			JOptionPane.showMessageDialog(
					parentDialog,
					ex.getMessage(),
					"Ошибка",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void validateUniqueNames(List<BackendRequestDef> updated, JDialog parentDialog) {
		Set<String> names = new HashSet<>();

		for (BackendRequestDef r : updated) {
			String name = safeTrim(r.getName());
			if (name.isEmpty()) {
				continue;
			}

			if (!names.add(name)) {
				JOptionPane.showMessageDialog(
						parentDialog,
						"Дублирующееся имя: '" + name + "'. Все имена должны быть уникальны.",
						"Ошибка валидации",
						JOptionPane.ERROR_MESSAGE
				);
				throw new IllegalStateException("Duplicate backend request name: " + name);
			}
		}
	}

	private List<BackendRequestDef> detectRemovedRequests(List<BackendRequestDef> oldRequests,
														  List<BackendRequestDef> updated) {
		Set<BackendRequestDef> updatedRefs = Collections.newSetFromMap(new IdentityHashMap<>());
		updatedRefs.addAll(updated);

		List<BackendRequestDef> removedRequests = new ArrayList<>();
		for (BackendRequestDef oldReq : oldRequests) {
			if (oldReq != null && !updatedRefs.contains(oldReq)) {
				removedRequests.add(oldReq);
			}
		}
		return removedRequests;
	}

	private void removeVariablesForRequest(BackendRequestDef request) {
		if (variablesService == null || request == null) {
			return;
		}

		List<ResponseFieldExtractor> extractors = request.getResponseExtractors();
		if (extractors == null || extractors.isEmpty()) {
			return;
		}

		for (ResponseFieldExtractor ex : extractors) {
			if (ex == null) {
				continue;
			}

			String variableName = safeTrim(ex.getVariableName());
			if (variableName.isEmpty()) {
				String fieldPath = safeTrim(ex.getFieldPath());
				if (!fieldPath.isEmpty()) {
					variableName = safeTrim(request.getName()) + "." + fieldPath;
				}
			}

			if (!variableName.isEmpty()) {
				variablesService.removeVariable(variableName);
			}
		}
	}

	private void openEditDtoDialog(JDialog parentDialog) {
		int row = backendTable.getSelectedRow();
		if (row < 0) {
			JOptionPane.showMessageDialog(
					parentDialog,
					"Select a request first",
					"No selection",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		BackendRequestDef def = rowToRequestRef.get(row);
		if (def != null) {
			openEditDtoDialogFor(parentDialog, def);
		}
	}

	// ──────────────────────────────────────────────────────────────────────
	// Edit DTO — главный диалог
	// ──────────────────────────────────────────────────────────────────────

	public void openEditDtoDialogFor(Component parent, BackendRequestDef def) {
		JDialog dlg = new JDialog(
				SwingUtilities.getWindowAncestor(parent),
				"Edit DTO: " + def.getName(),
				Dialog.ModalityType.APPLICATION_MODAL
		);
		dlg.setSize(980, 920);
		dlg.setLocationRelativeTo(parent);
		dlg.setLayout(new BorderLayout(8, 8));

		JTextField nameField = new JTextField(def.getName());
		JComboBox<String> httpMethodCombo = new JComboBox<>(HTTP_METHODS);
		String currentMethod = def.getMethod() != null ? def.getMethod().toUpperCase() : "POST";
		httpMethodCombo.setSelectedItem(Arrays.asList(HTTP_METHODS).contains(currentMethod) ? currentMethod : HTTP_METHODS[0]);
		JTextField urlField = new JTextField(def.getUrl());
		JComboBox<String> bodyTypeCombo = new JComboBox<>(new String[]{"JSON", "FORM_URLENCODED"});
		bodyTypeCombo.setSelectedItem(def.getBodyType() != null ? def.getBodyType() : "JSON");
		JTextField tokenField = new JTextField(def.getToken() != null ? def.getToken() : "");

		JPanel top = createTopPanel(nameField, httpMethodCombo, urlField, bodyTypeCombo, tokenField);
		dlg.add(top, BorderLayout.NORTH);

		JTextField searchField = new JTextField(20);
		JButton prevSearchBtn = new JButton("↑");
		JButton nextSearchBtn = new JButton("↓");
		JButton closeSearchBtn = new JButton("✕");
		JLabel searchInfoLabel = new JLabel();

		JPanel searchPanel = createSearchPanel(
				searchField,
				searchInfoLabel,
				prevSearchBtn,
				nextSearchBtn,
				closeSearchBtn
		);

		JTextArea bodyArea = new JTextArea(beautifyJson(def.getRequestBody() != null ? def.getRequestBody() : ""));
		bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

		JTextArea headersArea = new JTextArea(beautifyJson(def.getRequestHeaders() != null ? def.getRequestHeaders() : ""));
		headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		headersArea.setRows(6);

		JTextArea responseBodyArea = new JTextArea(beautifyJson(def.getCapturedResponseBody() != null ? def.getCapturedResponseBody() : ""));
		responseBodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

		attachUndoRedo(bodyArea);
		attachUndoRedo(headersArea);
		attachUndoRedo(responseBodyArea);

		JPanel formDataPanel = createFormDataEditorPanel(def);
		JSplitPane dtoTopSplit = createEditorsPanel(bodyArea, headersArea, formDataPanel, bodyTypeCombo);
		JPanel fieldOverridesPanel = createFieldOverridesPanel(
				dlg,
				def,
				bodyArea,
				responseBodyArea,
				bodyTypeCombo,
				formDataPanel
		);

		JSplitPane dtoTabSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, dtoTopSplit, fieldOverridesPanel);
		dtoTabSplit.setResizeWeight(0.65);
		dtoTabSplit.setDividerLocation(430);

		JPanel responseTab = createResponseTemplateTab(dlg, def, nameField, responseBodyArea);

		installSearchShortcut(
				dlg,
				searchPanel,
				searchField,
				searchInfoLabel,
				prevSearchBtn,
				nextSearchBtn,
				closeSearchBtn,
				bodyArea,
				headersArea,
				responseBodyArea,
				dtoTopSplit,
				responseTab
		);

		JTabbedPane centerTabs = new JTabbedPane();
		centerTabs.addTab("DTO Template", dtoTabSplit);
		centerTabs.addTab("Response Template", responseTab);

		dlg.add(centerTabs, BorderLayout.CENTER);

		JPanel south = createButtonsPanel(
				dlg,
				def,
				nameField,
				httpMethodCombo,
				urlField,
				bodyTypeCombo,
				tokenField,
				bodyArea,
				headersArea,
				responseBodyArea,
				formDataPanel,
				fieldOverridesPanel,
				responseTab
		);
		dlg.add(south, BorderLayout.SOUTH);

		dlg.setVisible(true);
	}

	private JPanel createTopPanel(
			JTextField nameField,
			JComboBox<String> httpMethodCombo,
			JTextField urlField,
			JComboBox<String> bodyTypeCombo,
			JTextField tokenField
	) {
		JPanel top = new JPanel(new GridLayout(5, 2, 6, 6));
		top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

		top.add(new JLabel("Name"));
		top.add(nameField);
		top.add(new JLabel("Method"));
		top.add(httpMethodCombo);
		top.add(new JLabel("Path"));
		top.add(urlField);
		top.add(new JLabel("Body type"));
		top.add(bodyTypeCombo);
		top.add(new JLabel("Token"));
		top.add(tokenField);

		return top;
	}

	private JPanel createSearchPanel(
			JTextField searchField,
			JLabel searchInfoLabel,
			JButton prevSearchBtn,
			JButton nextSearchBtn,
			JButton closeSearchBtn
	) {
		for (JButton btn : new JButton[]{prevSearchBtn, nextSearchBtn, closeSearchBtn}) {
			btn.setMargin(new Insets(2, 8, 2, 8));
			btn.setFocusable(false);
		}

		JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 6));
		searchPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));
		searchPanel.add(new JLabel("Find:"));
		searchPanel.add(searchField);
		searchPanel.add(searchInfoLabel);
		searchPanel.add(prevSearchBtn);
		searchPanel.add(nextSearchBtn);
		searchPanel.add(closeSearchBtn);
		searchPanel.setVisible(false);

		return searchPanel;
	}

	private JSplitPane createEditorsPanel(
			JTextArea bodyArea,
			JTextArea headersArea,
			JPanel formDataPanel,
			JComboBox<String> bodyTypeCombo
	) {
		JButton bodyBeautifyBtn = new JButton("Beautify");
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

		JPanel bodySearchHost = new JPanel(new BorderLayout());
		bodySearchHost.setOpaque(false);

		JPanel bodyToolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		bodyToolbarLeft.setOpaque(false);
		bodyToolbarLeft.add(new JLabel("Request Body"));
		bodyToolbarLeft.add(bodyBeautifyBtn);

		JPanel bodyToolbar = new JPanel(new BorderLayout());
		bodyToolbar.add(bodyToolbarLeft, BorderLayout.WEST);
		bodyToolbar.add(bodySearchHost, BorderLayout.EAST);

		JPanel jsonBodyPanel = new JPanel(new BorderLayout());
		jsonBodyPanel.add(bodyToolbar, BorderLayout.NORTH);
		jsonBodyPanel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);

		JPanel bodyCardPanel = new JPanel(new CardLayout());
		bodyCardPanel.add(jsonBodyPanel, "JSON");
		bodyCardPanel.add(formDataPanel, "FORM_URLENCODED");

		JPanel headersSearchHost = new JPanel(new BorderLayout());
		headersSearchHost.setOpaque(false);

		JPanel headersToolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		headersToolbarLeft.setOpaque(false);
		headersToolbarLeft.add(new JLabel("Headers"));
		headersToolbarLeft.add(headersBeautifyBtn);

		JPanel headersToolbar = new JPanel(new BorderLayout());
		headersToolbar.add(headersToolbarLeft, BorderLayout.WEST);
		headersToolbar.add(headersSearchHost, BorderLayout.EAST);

		JPanel headersPanel = new JPanel(new BorderLayout());
		headersPanel.add(headersToolbar, BorderLayout.NORTH);
		headersPanel.add(new JScrollPane(headersArea), BorderLayout.CENTER);

		JSplitPane dtoTopSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bodyCardPanel, headersPanel);
		dtoTopSplit.setResizeWeight(0.78);
		dtoTopSplit.setDividerLocation(360);

		dtoTopSplit.putClientProperty("bodySearchHost", bodySearchHost);
		dtoTopSplit.putClientProperty("headersSearchHost", headersSearchHost);
		dtoTopSplit.putClientProperty("bodyCardPanel", bodyCardPanel);

		CardLayout cardLayout = (CardLayout) bodyCardPanel.getLayout();
		String selectedBodyType = Objects.toString(bodyTypeCombo.getSelectedItem(), "JSON");
		cardLayout.show(bodyCardPanel, selectedBodyType);

		bodyTypeCombo.addActionListener(e -> {
			String bodyType = Objects.toString(bodyTypeCombo.getSelectedItem(), "JSON");
			cardLayout.show(bodyCardPanel, bodyType);
		});

		return dtoTopSplit;
	}

	private DefaultTableModel createFieldOverridesModel(BackendRequestDef def) {
		String[] cols = {"Field Path", "Method", "Arg / Variable", "Type"};
		DefaultTableModel uniqueModel = new DefaultTableModel(cols, 0) {
			@Override
			public Class<?> getColumnClass(int col) {
				return String.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				return true;
			}
		};

		if (def.getFieldOverrides() != null) {
			for (DtoFieldOverride ov : def.getFieldOverrides()) {
				uniqueModel.addRow(new Object[]{
						ov.getFieldPath() != null ? ov.getFieldPath() : "",
						ov.getMethod() != null ? ov.getMethod() : VariableAction.GENERATE_EMAIL.getCode(),
						ov.getMethodArg() != null ? ov.getMethodArg() : "",
						"string"
				});
			}
		}

		return uniqueModel;
	}

	private JTable createFieldOverridesTable(JDialog dlg, DefaultTableModel uniqueModel) {
		JTable uniqueTable = new JTable(uniqueModel);
		uniqueTable.setRowHeight(28);
		uniqueTable.setShowGrid(true);
		uniqueTable.setGridColor(new Color(180, 180, 180));

		uniqueTable.getColumnModel().getColumn(0).setPreferredWidth(280);
		uniqueTable.getColumnModel().getColumn(1).setPreferredWidth(180);
		uniqueTable.getColumnModel().getColumn(2).setPreferredWidth(230);
		uniqueTable.getColumnModel().getColumn(3).setPreferredWidth(120);

		String[] methodOptions = buildMethodOptions();
		JComboBox<String> methodCombo = new JComboBox<>(methodOptions);
		uniqueTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(methodCombo) {
			@Override
			public boolean stopCellEditing() {
				String selected = (String) methodCombo.getSelectedItem();
				if ("use variable".equals(selected)) {
					String varName = showVariablePickerDialog(dlg);
					if (varName != null) {
						int editRow = uniqueTable.getEditingRow();
						if (editRow >= 0) {
							uniqueModel.setValueAt("use variable", editRow, 1);
							uniqueModel.setValueAt("${" + varName + "}", editRow, 2);
						}
					}
					super.cancelCellEditing();
					return true;
				}
				return super.stopCellEditing();
			}
		});

		JComboBox<String> typeCombo = new JComboBox<>(new String[]{"string", "number"});
		uniqueTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(typeCombo));

		uniqueTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(
					JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col
			) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
				String val = Objects.toString(value, "");
				if (!isSelected) {
					if (val.startsWith("${") && val.endsWith("}")) {
						c.setBackground(new Color(230, 245, 255));
						c.setForeground(new Color(0, 80, 160));
					} else {
						c.setBackground(table.getBackground());
						c.setForeground(table.getForeground());
					}
				}
				return c;
			}
		});

		uniqueModel.addTableModelListener(e -> {
			if (e.getColumn() == 2) {
				uniqueTable.repaint();
			}
		});

		return uniqueTable;
	}

	private JPanel createFieldOverridesPanel(
			JDialog dlg,
			BackendRequestDef def,
			JTextArea bodyArea,
			JTextArea responseBodyArea,
			JComboBox<String> bodyTypeCombo,
			JPanel formDataPanel
	) {
		DefaultTableModel uniqueModel = createFieldOverridesModel(def);
		JTable uniqueTable = createFieldOverridesTable(dlg, uniqueModel);

		JButton addUniqueRow = new JButton("+");
		addUniqueRow.setToolTipText("Добавить переопределение значения для поля");
		JButton removeUniqueRow = new JButton("-");
		removeUniqueRow.setToolTipText("Удалить переопределение значения для поля");
		JButton parseBtn = new JButton("⬇ Разобрать поля из ответа");
		parseBtn.setFont(parseBtn.getFont().deriveFont(11f));

		addUniqueRow.addActionListener(e ->
				uniqueModel.addRow(new Object[]{"", VariableAction.GENERATE_EMAIL.getCode(), "", "string"})
		);

		removeUniqueRow.addActionListener(e -> {
			int row = uniqueTable.getSelectedRow();
			if (row >= 0) {
				if (uniqueTable.isEditing()) {
					uniqueTable.getCellEditor().stopCellEditing();
				}
				uniqueModel.removeRow(row);
			}
		});

		parseBtn.addActionListener(e -> {
			if (uniqueTable.isEditing()) {
				uniqueTable.getCellEditor().stopCellEditing();
			}

			String bodyType = Objects.toString(bodyTypeCombo.getSelectedItem(), "JSON");
			List<String> paths = new ArrayList<>();

			if ("FORM_URLENCODED".equals(bodyType)) {
				DefaultTableModel formModel = (DefaultTableModel) formDataPanel.getClientProperty("formModel");
				if (formModel != null) {
					for (int r = 0; r < formModel.getRowCount(); r++) {
						String key = Objects.toString(formModel.getValueAt(r, 0), "").trim();
						if (!key.isEmpty()) {
							paths.add(key);
						}
					}
				}

				if (paths.isEmpty()) {
					JOptionPane.showMessageDialog(
							dlg,
							"Не удалось разобрать form-data: список полей пуст.",
							"Parse error",
							JOptionPane.WARNING_MESSAGE
					);
					return;
				}
			} else {
				String responseText = responseBodyArea.getText().trim();
				if (!responseText.isEmpty()) {
					paths = extractJsonLeafPaths(responseText);
				}

				if (paths.isEmpty()) {
					String requestText = bodyArea.getText().trim();
					if (!requestText.isEmpty()) {
						paths = extractJsonLeafPaths(requestText);
					}
				}

				if (paths.isEmpty()) {
					JOptionPane.showMessageDialog(
							dlg,
							"Не удалось разобрать JSON ответа или request body пуст/невалиден.",
							"Parse error",
							JOptionPane.WARNING_MESSAGE
					);
					return;
				}
			}

			Set<String> existing = new HashSet<>();
			for (int r = 0; r < uniqueModel.getRowCount(); r++) {
				existing.add(Objects.toString(uniqueModel.getValueAt(r, 0), "").trim());
			}

			int added = 0;
			for (String path : paths) {
				if (!existing.contains(path)) {
					uniqueModel.addRow(new Object[]{path, VariableAction.GENERATE_EMAIL.getCode(), "", "string"});
					added++;
				}
			}

			JOptionPane.showMessageDialog(
					dlg,
					"Добавлено " + added + " полей (дубликаты пропущены).",
					"Done",
					JOptionPane.INFORMATION_MESSAGE
			);
		});

		JPanel uniqueToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		uniqueToolbar.add(new JLabel("Field Overrides:"));
		uniqueToolbar.add(addUniqueRow);
		uniqueToolbar.add(removeUniqueRow);
		uniqueToolbar.add(parseBtn);

		JLabel argHint = new JLabel(
				"Arg / Variable: для value — просто введи значение; для use variable — вставляется ${varName}."
		);
		argHint.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));

		JPanel uniquePanel = new JPanel(new BorderLayout(2, 2));
		uniquePanel.add(uniqueToolbar, BorderLayout.NORTH);
		uniquePanel.add(new JScrollPane(uniqueTable), BorderLayout.CENTER);
		uniquePanel.add(argHint, BorderLayout.SOUTH);

		uniquePanel.putClientProperty("uniqueModel", uniqueModel);
		uniquePanel.putClientProperty("uniqueTable", uniqueTable);

		return uniquePanel;
	}

	private JPanel createResponseTemplateTab(JDialog dlg, BackendRequestDef def, JTextField nameField, JTextArea responseBodyArea) {
		JButton responseBeautifyBtn = new JButton("Beautify");
		responseBeautifyBtn.setFont(responseBeautifyBtn.getFont().deriveFont(11f));
		responseBeautifyBtn.setFocusable(false);
		responseBeautifyBtn.addActionListener(e -> {
			String pretty = beautifyJson(responseBodyArea.getText());
			responseBodyArea.setText(pretty);
			responseBodyArea.setCaretPosition(0);
		});

		JPanel responseSearchHost = new JPanel(new BorderLayout());
		responseSearchHost.setOpaque(false);

		JPanel responseToolbarLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		responseToolbarLeft.setOpaque(false);
		responseToolbarLeft.add(new JLabel("Response Body Template"));
		responseToolbarLeft.add(responseBeautifyBtn);

		JPanel responseToolbar = new JPanel(new BorderLayout());
		responseToolbar.add(responseToolbarLeft, BorderLayout.WEST);
		responseToolbar.add(responseSearchHost, BorderLayout.EAST);

		JPanel responseBodyPanel = new JPanel(new BorderLayout());
		responseBodyPanel.add(responseToolbar, BorderLayout.NORTH);
		responseBodyPanel.add(new JScrollPane(responseBodyArea), BorderLayout.CENTER);

		DefaultTableModel extractorModel = new DefaultTableModel(
				new String[]{"JSON fieldPath", "Variable"},
				0
		) {
			@Override
			public boolean isCellEditable(int r, int c) {
				return true;
			}
		};

		if (def.getResponseExtractors() != null) {
			for (ResponseFieldExtractor ex : def.getResponseExtractors()) {
				extractorModel.addRow(new Object[]{ex.getFieldPath(), ex.getVariableName()});
			}
		}

		JTable extractorTable = new JTable(extractorModel);
		extractorTable.setRowHeight(22);
		extractorTable.setShowGrid(true);
		extractorTable.setGridColor(new Color(180, 180, 180));
		extractorTable.getColumnModel().getColumn(0).setPreferredWidth(280);
		extractorTable.getColumnModel().getColumn(1).setPreferredWidth(320);

		JButton addExtRow = new JButton("+");
		JButton removeExtRow = new JButton("-");
		JButton parseResponseBtn = new JButton("Parse fields from Response");
		parseResponseBtn.setFont(parseResponseBtn.getFont().deriveFont(11f));

		addExtRow.addActionListener(e -> {
			if (extractorTable.isEditing()) {
				extractorTable.getCellEditor().stopCellEditing();
			}
			extractorModel.addRow(new Object[]{"", ""});
		});

		removeExtRow.addActionListener(e -> {
			if (extractorTable.isEditing()) {
				extractorTable.getCellEditor().stopCellEditing();
			}
			int row = extractorTable.getSelectedRow();
			if (row >= 0) {
				String variableName = Objects.toString(extractorModel.getValueAt(row, 1), "").trim();
				extractorModel.removeRow(row);
				if (!variableName.isEmpty() && variablesService != null) {
					variablesService.removeVariable(variableName);
					variablesService.refreshTableFromVariables();
				}
			}
		});

		parseResponseBtn.addActionListener(e -> {
			if (extractorTable.isEditing()) {
				extractorTable.getCellEditor().stopCellEditing();
			}

			List<String> paths = extractJsonLeafPaths(responseBodyArea.getText().trim());
			if (paths.isEmpty()) {
				JOptionPane.showMessageDialog(dlg, "Response JSON is empty or invalid.", "Parse error", JOptionPane.WARNING_MESSAGE);
				return;
			}

			Set<String> existing = new HashSet<>();
			for (int r = 0; r < extractorModel.getRowCount(); r++) {
				existing.add(Objects.toString(extractorModel.getValueAt(r, 0), "").trim());
			}

			String reqName = nameField.getText().trim().isEmpty() ? def.getName() : nameField.getText().trim();
			int added = 0;
			List<ResponseFieldExtractor> addedExtractors = new ArrayList<>();

			for (String path : paths) {
				if (!existing.contains(path)) {
					String variableName = reqName + "." + path;
					extractorModel.addRow(new Object[]{path, variableName});
					addedExtractors.add(new ResponseFieldExtractor(path, variableName));
					added++;
				}
			}

			syncResponseExtractorsToVariables(addedExtractors);
			JOptionPane.showMessageDialog(dlg, added + " extractors and variables added.", "Done", JOptionPane.INFORMATION_MESSAGE);
		});

		JPanel extractorTopBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		extractorTopBar.add(new JLabel("Response Extractors"));
		extractorTopBar.add(addExtRow);
		extractorTopBar.add(removeExtRow);
		extractorTopBar.add(parseResponseBtn);

		JLabel extHint = new JLabel("Variable = requestName.fieldPath from json fieldPath.");
		extHint.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

		JPanel extractorPanel = new JPanel(new BorderLayout(2, 2));
		extractorPanel.add(extractorTopBar, BorderLayout.NORTH);
		extractorPanel.add(new JScrollPane(extractorTable), BorderLayout.CENTER);
		extractorPanel.add(extHint, BorderLayout.SOUTH);

		JSplitPane responseTabSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, responseBodyPanel, extractorPanel);
		responseTabSplit.setResizeWeight(0.58);
		responseTabSplit.setDividerLocation(430);

		JPanel responseTab = new JPanel(new BorderLayout());
		responseTab.add(responseTabSplit, BorderLayout.CENTER);
		responseTab.putClientProperty("extractorModel", extractorModel);
		responseTab.putClientProperty("extractorTable", extractorTable);
		responseTab.putClientProperty("responseSearchHost", responseSearchHost);

		return responseTab;
	}

	private JPanel createButtonsPanel(
			JDialog dlg,
			BackendRequestDef def,
			JTextField nameField,
			JComboBox<String> httpMethodCombo,
			JTextField urlField,
			JComboBox<String> bodyTypeCombo,
			JTextField tokenField,
			JTextArea bodyArea,
			JTextArea headersArea,
			JTextArea responseBodyArea,
			JPanel formDataPanel,
			JPanel fieldOverridesPanel,
			JPanel responseTab
	) {
		JButton saveBtn = new JButton("Save");
		JButton cancelBtn = new JButton("Cancel");

		saveBtn.addActionListener(e -> saveDtoChanges(
				dlg,
				def,
				nameField,
				httpMethodCombo,
				urlField,
				bodyTypeCombo,
				tokenField,
				bodyArea,
				headersArea,
				responseBodyArea,
				formDataPanel,
				fieldOverridesPanel,
				responseTab
		));

		cancelBtn.addActionListener(e -> dlg.dispose());

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(saveBtn);
		buttons.add(cancelBtn);

		JPanel south = new JPanel(new BorderLayout());
		south.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
		south.add(buttons, BorderLayout.EAST);

		return south;
	}

	private void saveDtoChanges(
			JDialog dlg,
			BackendRequestDef def,
			JTextField nameField,
			JComboBox<String> httpMethodCombo,
			JTextField urlField,
			JComboBox<String> bodyTypeCombo,
			JTextField tokenField,
			JTextArea bodyArea,
			JTextArea headersArea,
			JTextArea responseBodyArea,
			JPanel formDataPanel,
			JPanel fieldOverridesPanel,
			JPanel responseTab
	) {
		try {
			DefaultTableModel uniqueModel = (DefaultTableModel) fieldOverridesPanel.getClientProperty("uniqueModel");
			JTable uniqueTable = (JTable) fieldOverridesPanel.getClientProperty("uniqueTable");
			DefaultTableModel extractorModel = (DefaultTableModel) responseTab.getClientProperty("extractorModel");
			JTable extractorTable = (JTable) responseTab.getClientProperty("extractorTable");
			JTable formTable = (JTable) formDataPanel.getClientProperty("formTable");
			DefaultTableModel formModel = (DefaultTableModel) formDataPanel.getClientProperty("formModel");

			if (uniqueTable != null && uniqueTable.isEditing()) uniqueTable.getCellEditor().stopCellEditing();
			if (extractorTable != null && extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing();
			if (formTable != null && formTable.isEditing()) formTable.getCellEditor().stopCellEditing();

			String oldDefName = safeTrim(def.getName());
			String newDefName = safeTrim(nameField.getText());

			def.setName(newDefName);
			def.setMethod(Objects.toString(httpMethodCombo.getSelectedItem(), "GET"));
			def.setUrl(normalizeUrlToPath(urlField.getText()));
			def.setRequestHeaders(headersArea.getText());
			def.setCapturedResponseBody(responseBodyArea.getText());
			def.setToken(safeTrim(tokenField.getText()));

			String bodyType = Objects.toString(bodyTypeCombo.getSelectedItem(), "JSON");
			def.setBodyType(bodyType);

			if ("FORM_URLENCODED".equals(bodyType)) {
				List<FormDataParam> formData = collectFormData(formModel);
				def.setFormData(formData);
				def.setRequestBody(buildFormUrlencodedBody(formData));
			} else {
				def.setRequestBody(bodyArea.getText());
				def.setFormData(new ArrayList<>());
			}

			List<DtoFieldOverride> overrides = new ArrayList<>();
			for (int r = 0; r < uniqueModel.getRowCount(); r++) {
				String fieldPath = Objects.toString(uniqueModel.getValueAt(r, 0), "").trim();
				String methodVal = Objects.toString(uniqueModel.getValueAt(r, 1), "").trim();
				String arg = Objects.toString(uniqueModel.getValueAt(r, 2), "").trim();
				String typeVal = Objects.toString(uniqueModel.getValueAt(r, 3), "string").trim();
				if (fieldPath.isEmpty()) continue;
				overrides.add(new DtoFieldOverride(fieldPath, methodVal, arg, typeVal));
			}
			def.setFieldOverrides(overrides);

			List<ResponseFieldExtractor> oldExtractors = cloneExtractors(def.getResponseExtractors());
			List<ResponseFieldExtractor> newExtractors = collectResponseExtractors(extractorModel, def);
			syncExtractorVariablesAfterEdit(oldDefName, newDefName, oldExtractors, newExtractors);
			def.setResponseExtractors(newExtractors);

			if (!Objects.equals(oldDefName, newDefName)) {
				renameBackendMethod(oldDefName, newDefName);
			}

			save();
			loadIntoTable();
			dlg.dispose();
		} catch (IllegalStateException ex) {
			JOptionPane.showMessageDialog(dlg, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ──────────────────────────────────────────────────────────────────────
	// Выбор переменной
	// ──────────────────────────────────────────────────────────────────────

	private String showVariablePickerDialog(Component parent) {
		if (variablesService == null) {
			JOptionPane.showMessageDialog(
					parent,
					"VariablesService недоступен. Убедитесь что переменные настроены в Settings.",
					"Нет переменных",
					JOptionPane.WARNING_MESSAGE
			);
			return null;
		}

		List<String> names = variablesService.getVariableNames();
		if (names.isEmpty()) {
			JOptionPane.showMessageDialog(
					parent,
					"Переменные не заданы. Добавьте их в Settings → Variables.",
					"Нет переменных",
					JOptionPane.INFORMATION_MESSAGE
			);
			return null;
		}

		return (String) JOptionPane.showInputDialog(
				parent,
				"Выберите переменную:",
				"Выбор переменной",
				JOptionPane.PLAIN_MESSAGE,
				null,
				names.toArray(),
				names.get(0)
		);
	}

	private String[] buildMethodOptions() {
		List<String> options = new ArrayList<>();
		for (VariableAction a : VariableAction.values()) {
			options.add(a.getCode());
		}
		options.add("value");
		options.add("use variable");
		return options.toArray(new String[0]);
	}

	// ──────────────────────────────────────────────────────────────────────
	// JSON-утилиты
	// ──────────────────────────────────────────────────────────────────────

	private String beautifyJson(String raw) {
		if (raw == null || raw.isBlank()) {
			return raw != null ? raw : "";
		}

		try {
			JsonElement el = JsonParser.parseString(raw);
			return gson.toJson(el);
		} catch (JsonSyntaxException e) {
			return raw;
		}
	}

	private List<String> extractJsonLeafPaths(String jsonText) {
		List<String> paths = new ArrayList<>();
		if (jsonText == null || jsonText.isBlank()) {
			return paths;
		}

		try {
			JsonElement root = JsonParser.parseString(jsonText);
			collectLeafPaths(root, "", paths);
		} catch (JsonSyntaxException ignored) {
		}

		return paths;
	}

	private void collectLeafPaths(JsonElement element, String prefix, List<String> result) {
		if (element == null || element.isJsonNull()) {
			if (!prefix.isEmpty()) {
				result.add(prefix);
			}
			return;
		}

		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				String key = entry.getKey();
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
		} else if (!prefix.isEmpty()) {
			result.add(prefix);
		}
	}

	// ──────────────────────────────────────────────────────────────────────
	// CRUD + персистентность
	// ──────────────────────────────────────────────────────────────────────

	public List<BackendRequestDef> getRequests() {
		return Collections.unmodifiableList(requests);
	}

	public void setRequests(List<BackendRequestDef> list) {
		requests.clear();
		if (list != null) {
			requests.addAll(list);
		}
	}

	public void addRequest(BackendRequestDef def) {
		BackendRequestDef existing = findByName(def.getName());
		if (existing != null) {
			throw new IllegalArgumentException(
					"Запрос с именем '" + def.getName() + "' уже существует. Используйте другое имя."
			);
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
			if (Objects.equals(r.getName(), name)) {
				return r;
			}
		}
		return null;
	}

	public void load(List<BackendRequestDef> backendRequests,
					 Map<String, ScenarioBackendConfig> scenarioOverrides) {
		requests.clear();

		if (backendRequests != null) {
			for (BackendRequestDef def : backendRequests) {
				if (def == null || def.getName() == null || def.getName().isBlank()) {
					continue;
				}
				requests.add(def);
			}
		}

		if (scenarioOverrides != null && !scenarioOverrides.isEmpty()) {
			applyScenarioOverrides(scenarioOverrides);
		}

		if (backendTableModel != null) {
			loadIntoTable();
		}
	}

	private void applyScenarioOverrides(Map<String, ScenarioBackendConfig> scenarioOverrides) {
		for (BackendRequestDef def : requests) {
			if (def == null || def.getName() == null) {
				continue;
			}

			ScenarioBackendConfig cfg = scenarioOverrides.get(def.getName());
			if (cfg == null) {
				continue;
			}

			if (cfg.getFieldOverrides() != null) {
				def.setFieldOverrides(new ArrayList<>(cfg.getFieldOverrides()));
			}

			if (cfg.getResponseExtractors() != null) {
				def.setResponseExtractors(new ArrayList<>(cfg.getResponseExtractors()));
			}
		}
	}

	public void save() {
		// бек-методы хранятся только в JSON-файлах тестов, не в системе
	}

	public void reloadFromSystem() {
		requests.clear();
	}

	private void attachUndoRedo(JTextArea area) {
		UndoManager undoManager = new UndoManager();
		undoManager.setLimit(100);
		area.getDocument().addUndoableEditListener((UndoableEditEvent e) -> undoManager.addEdit(e.getEdit()));

		InputMap im = area.getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap am = area.getActionMap();

		im.put(KeyStroke.getKeyStroke("control Z"), "undo");
		im.put(KeyStroke.getKeyStroke("meta Z"), "undo");

		im.put(KeyStroke.getKeyStroke("control Y"), "redo");
		im.put(KeyStroke.getKeyStroke("control shift Z"), "redo");
		im.put(KeyStroke.getKeyStroke("meta shift Z"), "redo");
		im.put(KeyStroke.getKeyStroke("meta Y"), "redo");

		am.put("undo", new AbstractAction("undo") {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (undoManager.canUndo()) {
					undoManager.undo();
				}
			}
		});

		am.put("redo", new AbstractAction("redo") {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (undoManager.canRedo()) {
					undoManager.redo();
				}
			}
		});
	}

	private void syncResponseExtractorsToVariables(List<ResponseFieldExtractor> extractors) {
		if (variablesService == null || extractors == null || extractors.isEmpty()) {
			return;
		}

		for (ResponseFieldExtractor ex : extractors) {
			if (ex == null) {
				continue;
			}

			String variableName = safeTrim(ex.getVariableName());
			if (variableName.isEmpty()) {
				continue;
			}

			String fieldPath = safeTrim(ex.getFieldPath());
			String value = fieldPath.isEmpty() ? "" : "json(" + fieldPath + ")";
			variablesService.addVariable(variableName, value);
		}

		variablesService.refreshTableFromVariables();
	}

	private List<ResponseFieldExtractor> collectResponseExtractors(DefaultTableModel extractorModel,
																   BackendRequestDef def) {
		List<ResponseFieldExtractor> extractors = new ArrayList<>();

		for (int r = 0; r < extractorModel.getRowCount(); r++) {
			String fp = Objects.toString(extractorModel.getValueAt(r, 0), "").trim();
			String vn = Objects.toString(extractorModel.getValueAt(r, 1), "").trim();

			if (fp.isEmpty()) {
				continue;
			}

			if (vn.isEmpty()) {
				vn = safeTrim(def.getName()) + "." + fp;
			}

			extractors.add(new ResponseFieldExtractor(fp, vn));
		}

		return extractors;
	}

	private List<ResponseFieldExtractor> cloneExtractors(List<ResponseFieldExtractor> extractors) {
		List<ResponseFieldExtractor> copy = new ArrayList<>();
		if (extractors == null) {
			return copy;
		}

		for (ResponseFieldExtractor ex : extractors) {
			if (ex == null) {
				continue;
			}
			copy.add(new ResponseFieldExtractor(ex.getFieldPath(), ex.getVariableName()));
		}
		return copy;
	}

	private void syncExtractorVariablesAfterEdit(String oldRequestName,
												 String newRequestName,
												 List<ResponseFieldExtractor> oldExtractors,
												 List<ResponseFieldExtractor> newExtractors) {
		if (variablesService == null) {
			return;
		}

		Set<String> oldNames = new HashSet<>();
		if (oldExtractors != null) {
			for (ResponseFieldExtractor ex : oldExtractors) {
				String oldVar = resolveVariableName(oldRequestName, ex);
				if (!oldVar.isEmpty()) {
					oldNames.add(oldVar);
				}
			}
		}

		Set<String> newNames = new HashSet<>();
		if (newExtractors != null) {
			for (ResponseFieldExtractor ex : newExtractors) {
				String finalVarName = resolveVariableName(newRequestName, ex);
				if (finalVarName.isEmpty()) {
					continue;
				}

				ex.setVariableName(finalVarName);
				newNames.add(finalVarName);

				String fieldPath = safeTrim(ex.getFieldPath());
				String value = fieldPath.isEmpty() ? "" : "json(" + fieldPath + ")";
				variablesService.addVariable(finalVarName, value);
			}
		}

		for (String oldVar : oldNames) {
			if (!newNames.contains(oldVar)) {
				variablesService.removeVariable(oldVar);
			}
		}

		variablesService.refreshTableFromVariables();
	}

	private void renameRequestVariables(String oldName,
										String newName,
										List<ResponseFieldExtractor> extractors) {
		if (variablesService == null || extractors == null || extractors.isEmpty()) {
			return;
		}

		if (Objects.equals(oldName, newName)) {
			return;
		}

		for (ResponseFieldExtractor ex : extractors) {
			if (ex == null) continue;

			String fieldPath = safeTrim(ex.getFieldPath());
			if (fieldPath.isEmpty()) continue;

			String oldVarName = oldName + "." + fieldPath;
			String newVarName = newName + "." + fieldPath;
			String value = "json(" + fieldPath + ")";

			variablesService.removeVariable(oldVarName);
			variablesService.addVariable(newVarName, value);
			ex.setVariableName(newVarName);
		}

		variablesService.refreshTableFromVariables();
	}

	private String resolveVariableName(String requestName, ResponseFieldExtractor ex) {
		if (ex == null) {
			return "";
		}

		String variableName = safeTrim(ex.getVariableName());
		if (!variableName.isEmpty()) {
			return variableName;
		}

		String fieldPath = safeTrim(ex.getFieldPath());
		if (fieldPath.isEmpty()) {
			return "";
		}

		return safeTrim(requestName) + "." + fieldPath;
	}

	private String safeTrim(String value) {
		return value == null ? "" : value.trim();
	}

	/**
	 * Загружает backend-запросы из сценария (JSON теста) только в память,
	 * НЕ записывая в системный backendRequests.json.
	 * Если запрос с таким именем уже есть — он НЕ перезаписывается
	 * (системный приоритет выше тестового, чтобы не ломать настроенные URL/методы).
	 */
	public void loadFromScenario(List<BackendRequestDef> defs) {
		if (defs == null || defs.isEmpty()) {
			return;
		}

		for (BackendRequestDef def : defs) {
			if (def == null || def.getName() == null || def.getName().isBlank()) {
				continue;
			}

			if (findByName(def.getName()) == null) {
				requests.add(def);
			}
		}

		if (backendTableModel != null) {
			loadIntoTable();
		}
	}

	private void renameVariablesInService(String oldName, String newName, List<ResponseFieldExtractor> extractors) {
		if (variablesService == null) {
			return;
		}
		if (oldName == null || newName == null) {
			return;
		}
		if (Objects.equals(oldName, newName)) {
			return;
		}
		if (extractors == null || extractors.isEmpty()) {
			return;
		}

		for (ResponseFieldExtractor ex : extractors) {
			if (ex == null) {
				continue;
			}

			String fieldPath = ex.getFieldPath() != null ? ex.getFieldPath().trim() : "";
			if (fieldPath.isEmpty()) {
				continue;
			}

			String oldVarName = oldName + "." + fieldPath;
			String newVarName = newName + "." + fieldPath;
			String value = "json(" + fieldPath + ")";

			variablesService.removeVariable(oldVarName);
			variablesService.addVariable(newVarName, value);
			ex.setVariableName(newVarName);
		}

		variablesService.refreshTableFromVariables();
	}

	private void installSearchShortcut(
			JDialog dlg,
			JPanel searchPanel,
			JTextField searchField,
			JLabel searchInfoLabel,
			JButton prevSearchBtn,
			JButton nextSearchBtn,
			JButton closeSearchBtn,
			JTextArea bodyArea,
			JTextArea headersArea,
			JTextArea responseBodyArea,
			JSplitPane dtoTopSplit,
			JPanel responseTab
	) {
		JPanel bodySearchHost = (JPanel) dtoTopSplit.getClientProperty("bodySearchHost");
		JPanel headersSearchHost = (JPanel) dtoTopSplit.getClientProperty("headersSearchHost");
		JPanel responseSearchHost = (JPanel) responseTab.getClientProperty("responseSearchHost");

		Runnable closeSearch = () -> {
			searchField.setText("");
			searchInfoLabel.setText("");
			searchPanel.setVisible(false);
			dlg.revalidate();
			dlg.repaint();
		};

		java.util.function.Supplier<JTextArea> currentAreaSupplier = () -> {
			Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
			if (focusOwner == headersArea || SwingUtilities.isDescendingFrom(focusOwner, headersArea)) {
				return headersArea;
			}
			if (focusOwner == responseBodyArea || SwingUtilities.isDescendingFrom(focusOwner, responseBodyArea)) {
				return responseBodyArea;
			}
			return bodyArea;
		};

		Runnable focusSearch = () -> {
			Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

			JPanel targetHost = bodySearchHost;
			if (focusOwner == headersArea || SwingUtilities.isDescendingFrom(focusOwner, headersArea)) {
				targetHost = headersSearchHost;
			} else if (focusOwner == responseBodyArea || SwingUtilities.isDescendingFrom(focusOwner, responseBodyArea)) {
				targetHost = responseSearchHost;
			}

			moveSearchPanel(searchPanel, targetHost, dlg);
			searchPanel.setVisible(true);
			searchField.requestFocusInWindow();
			searchField.selectAll();
			dlg.revalidate();
			dlg.repaint();
		};

		Runnable findNext = () -> {
			String query = searchField.getText();
			if (query == null || query.isBlank()) {
				searchInfoLabel.setText("");
				return;
			}

			JTextArea area = currentAreaSupplier.get();
			String text = area.getText();
			String lowerText = text.toLowerCase();
			String lowerQuery = query.toLowerCase();

			int startIndex = area.getSelectionEnd();
			int index = lowerText.indexOf(lowerQuery, startIndex);
			if (index < 0) {
				index = lowerText.indexOf(lowerQuery, 0);
			}

			if (index >= 0) {
				area.requestFocusInWindow();
				area.select(index, index + query.length());
				searchInfoLabel.setText((index + 1) + "/" + text.length());
			} else {
				searchInfoLabel.setText("not found");
			}
		};

		Runnable findPrev = () -> {
			String query = searchField.getText();
			if (query == null || query.isBlank()) {
				searchInfoLabel.setText("");
				return;
			}

			JTextArea area = currentAreaSupplier.get();
			String text = area.getText();
			String lowerText = text.toLowerCase();
			String lowerQuery = query.toLowerCase();

			int startIndex = Math.max(area.getSelectionStart() - 1, 0);
			int index = lowerText.lastIndexOf(lowerQuery, startIndex);
			if (index < 0) {
				index = lowerText.lastIndexOf(lowerQuery);
			}

			if (index >= 0) {
				area.requestFocusInWindow();
				area.select(index, index + query.length());
				searchInfoLabel.setText((index + 1) + "/" + text.length());
			} else {
				searchInfoLabel.setText("not found");
			}
		};

		JRootPane rootPane = dlg.getRootPane();
		InputMap im = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = rootPane.getActionMap();

		im.put(KeyStroke.getKeyStroke("control F"), "showSearch");
		im.put(KeyStroke.getKeyStroke("meta F"), "showSearch");
		im.put(KeyStroke.getKeyStroke("ESCAPE"), "hideSearch");
		im.put(KeyStroke.getKeyStroke("F3"), "findNext");
		im.put(KeyStroke.getKeyStroke("shift F3"), "findPrev");

		am.put("showSearch", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				focusSearch.run();
			}
		});

		am.put("hideSearch", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				closeSearch.run();
			}
		});

		am.put("findNext", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				findNext.run();
			}
		});

		am.put("findPrev", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				findPrev.run();
			}
		});

		searchField.addActionListener(e -> findNext.run());
		nextSearchBtn.addActionListener(e -> findNext.run());
		prevSearchBtn.addActionListener(e -> findPrev.run());
		closeSearchBtn.addActionListener(e -> closeSearch.run());
	}

	private void moveSearchPanel(JPanel searchPanel, JPanel targetHost, JDialog dlg) {
		Container parent = searchPanel.getParent();
		if (parent != null) {
			parent.remove(searchPanel);
			parent.revalidate();
			parent.repaint();
		}

		targetHost.removeAll();
		targetHost.add(searchPanel, BorderLayout.CENTER);
		targetHost.revalidate();
		targetHost.repaint();

		dlg.revalidate();
		dlg.repaint();
	}

	private void renameBackendMethod(String oldName, String newName) {
		if (currentActionWindow == null) {
			return;
		}
		if (oldName == null || newName == null || oldName.equals(newName)) {
			return;
		}

		currentActionWindow.renameBackendMethod(oldName, newName);
	}

	private String normalizeUrlToPath(String rawUrl) {
		String value = safeTrim(rawUrl);
		if (value.isEmpty()) {
			return value;
		}

		if (value.startsWith("http://") || value.startsWith("https://")) {
			try {
				java.net.URI uri = java.net.URI.create(value);
				String path = uri.getRawPath() != null ? uri.getRawPath() : "";
				String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
				String result = path + query;
				return result.isBlank() ? "/" : result;
			} catch (Exception ignored) {
			}
		}

		if (!value.startsWith("/")) {
			value = "/" + value;
		}
		return value;
	}

	private JPanel createFormDataEditorPanel(BackendRequestDef def) {
		DefaultTableModel formModel = new DefaultTableModel(new String[]{"Key", "Value"}, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return true;
			}
		};

		for (FormDataParam param : def.getFormData()) {
			formModel.addRow(new Object[]{param.getKey(), param.getValue()});
		}

		JTable formTable = new JTable(formModel);
		formTable.setRowHeight(24);

		JButton addBtn = new JButton("+");
		addBtn.addActionListener(e -> formModel.addRow(new Object[]{"", ""}));

		JButton removeBtn = new JButton("-");
		removeBtn.addActionListener(e -> {
			int row = formTable.getSelectedRow();
			if (row >= 0) {
				if (formTable.isEditing()) {
					formTable.getCellEditor().stopCellEditing();
				}
				formModel.removeRow(row);
			}
		});

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		top.add(new JLabel("Form Data"));
		top.add(addBtn);
		top.add(removeBtn);

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(top, BorderLayout.NORTH);
		panel.add(new JScrollPane(formTable), BorderLayout.CENTER);
		panel.putClientProperty("formTable", formTable);
		panel.putClientProperty("formModel", formModel);
		return panel;
	}

	private List<FormDataParam> collectFormData(DefaultTableModel model) {
		List<FormDataParam> result = new ArrayList<>();
		for (int row = 0; row < model.getRowCount(); row++) {
			String key = Objects.toString(model.getValueAt(row, 0), "").trim();
			String value = Objects.toString(model.getValueAt(row, 1), "");
			if (key.isEmpty()) {
				continue;
			}
			result.add(new FormDataParam(key, value));
		}
		return result;
	}

	private String buildFormUrlencodedBody(List<FormDataParam> params) {
		if (params == null || params.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (FormDataParam param : params) {
			if (param == null) continue;
			String key = param.getKey() != null ? param.getKey().trim() : "";
			if (key.isEmpty()) continue;
			String value = param.getValue() != null ? param.getValue() : "";
			if (!sb.isEmpty()) sb.append("&");
			sb.append(java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8));
			sb.append("=");
			sb.append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
		}
		return sb.toString();
	}
}