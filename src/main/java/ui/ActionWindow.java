package ui;

import com.codeborne.selenide.WebDriverRunner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.AppConfig;
import model.ElementType;
import model.UserAction;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import ui.action.*;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import java.awt.event.*;
import javax.swing.AbstractAction;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;

import static com.codeborne.selenide.Selenide.open;

public class ActionWindow extends JFrame {

	private static final int UNDO_LIMIT = 5;

	private final java.util.Deque<Runnable> undoStack = new java.util.ArrayDeque<>();
	private final java.util.Deque<Runnable> redoStack = new java.util.ArrayDeque<>();
	private final Map<Integer, Color> rowMarks = new HashMap<>();


	private JTable actionTable;
	private DefaultTableModel tableModel;
	private JButton addActionButton;
	private JButton menuButton;
	private JButton openBrowserButton;
	private JButton playButton;
	private JComboBox<String> themeSelect;
	private JTextField driverPathField;
	private final ActionRecorder actionRecorder;
	private WebDriver driver;
	private JButton recordingButton;

	private final ActionFileService fileService;
	private final ConfigService configService = new ConfigService();
	private final AppConfig config;

	private final OpenApiService openApiService;
	private final UsersService usersService;
	private final PlayActionService playActionService;
	private final BrowserService browserService;
	private final CustomMethodsService customMethodsService;
	private final VariablesService variablesService;

	public ActionWindow() {
		config = configService.load();
		variablesService = new VariablesService();
		openApiService = new OpenApiService(configService, config);
		usersService = new UsersService(configService, config);
		browserService = new BrowserService(configService, config);
		customMethodsService = new CustomMethodsService(configService, config);
		this.customMethodsService.load();
		driver = null;

		if ("Dark".equalsIgnoreCase(config.theme)) {
			FlatDarkLaf.setup();
		} else {
			FlatLightLaf.setup();
		}

		setTitle("Test Recorder – Action Panel");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(900, 650);
		setLocationRelativeTo(null);

		initTopBar();
		initActionTable();
		initBottomPanel();
		initKeyBindings();

		actionRecorder = new ActionRecorder(tableModel);
		playActionService =  new PlayActionService(tableModel, usersService, customMethodsService, variablesService);
		driver = null;
		fileService = new ActionFileService(this, tableModel, customMethodsService, variablesService);

		Container content = getContentPane();
		content.setLayout(new BorderLayout());
		content.add(createTopBarPanel(), BorderLayout.NORTH);

		actionTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
			@Override
			public void columnMarginChanged(ChangeEvent e) {
				saveColumnWidthsToConfig();
			}

			@Override public void columnAdded(TableColumnModelEvent e) {}
			@Override public void columnRemoved(TableColumnModelEvent e) {}
			@Override public void columnMoved(TableColumnModelEvent e) {}
			@Override public void columnSelectionChanged(ListSelectionEvent e) {}
		});
		JScrollPane scrollPane = new JScrollPane(actionTable);
		content.add(scrollPane, BorderLayout.CENTER);
		content.add(createBottomPanel(), BorderLayout.SOUTH);
	}

	private void initTopBar() {
		// один раз на всё окно
		ToolTipManager.sharedInstance().setInitialDelay(200);

		// --- Menu button ---
		menuButton = new JButton("☰");
		menuButton.setFocusable(false);
		menuButton.setToolTipText("Menu");

		JPopupMenu menuPopup = new JPopupMenu();

		JMenuItem openItem = new JMenuItem("Open..");
		openItem.addActionListener(e -> {
			if (fileService != null) {
				fileService.loadFromJsonFile();
			}
		});
		menuPopup.add(openItem);

		JMenuItem generateApiClientItem = new JMenuItem("Generate ApiClient");
		generateApiClientItem.addActionListener(
				e -> openApiService.openGenerateApiClientDialog(this)
		);
		menuPopup.add(generateApiClientItem);

		JMenuItem settingsItem = new JMenuItem("Settings");
		settingsItem.addActionListener(e -> openSettingsDialog());
		menuPopup.add(settingsItem);

		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.addActionListener(e -> dispose());
		menuPopup.add(exitItem);

		menuButton.addActionListener(
				e -> menuPopup.show(menuButton, 0, menuButton.getHeight())
		);

		// --- Add action button ---
		addActionButton = new JButton("+");
		addActionButton.setFont(new Font("Arial", Font.BOLD, 16));
		addActionButton.setToolTipText("Add action");
		addActionButton.addActionListener(e -> addNewAction());

		// --- Open browser button ---
		openBrowserButton = new JButton("🌐 Open Browser");
		openBrowserButton.setToolTipText("Open Chrome for Testing browser");
		openBrowserButton.addActionListener(e -> openBrowserAsync());

		// --- Play button ---
		playButton = new JButton("▶");
		playButton.setToolTipText("Run actions from table in browser");
		playButton.addActionListener(e -> {
			javax.swing.table.TableModel model = actionTable.getModel();
			int rowCount = model.getRowCount();
			int colCount = model.getColumnCount();

			// TODO: перевести на нормальный логгер и/или debug-режим
			System.out.println("==== TABLE DUMP ====");
			for (int r = 0; r < rowCount; r++) {
				System.out.print("row " + r + ": ");
				for (int c = 0; c < colCount; c++) {
					System.out.print("[" + c + "]=" + model.getValueAt(r, c) + "  ");
				}
				System.out.println();
			}
			System.out.println("====================");

			int viewRow = actionTable.getSelectedRow();
			toggleScenario(Math.max(viewRow, 0));
		});

		// popup на play (force stop)
		JPopupMenu playPopup = new JPopupMenu();
		JMenuItem forceStopItem = new JMenuItem("Force stop");
		forceStopItem.addActionListener(e -> playActionService.stopPlayback());
		playPopup.add(forceStopItem);

		playButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e);
			}

			private void maybeShowPopup(MouseEvent e) {
				if (e.isPopupTrigger()) {
					playPopup.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});

		// --- Recording button ---
		recordingButton = new JButton("⏺ Start Recording");
		recordingButton.setToolTipText("Start/Stop recording");
		recordingButton.addActionListener(e -> toggleRecording());
	}

	private JPanel createTopBarPanel() {
		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBorder(new EmptyBorder(5, 5, 5, 5));

		// левая часть: меню, добавление, сохранение
		JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		leftButtons.add(menuButton);
		leftButtons.add(addActionButton);

		JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
		separator.setPreferredSize(new Dimension(1, 25));
		leftButtons.add(separator);

		JButton saveVarButton = new JButton("💾");
		saveVarButton.setToolTipText("Save table to file");
		saveVarButton.addActionListener(e -> saveTableToFile());
		leftButtons.add(saveVarButton);

		// правая часть: play, запись
		JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		rightButtons.add(playButton);
		rightButtons.add(recordingButton);

		topBar.add(leftButtons, BorderLayout.WEST);
		topBar.add(openBrowserButton, BorderLayout.CENTER);
		topBar.add(rightButtons, BorderLayout.EAST);

		return topBar;
	}

	private void initActionTable() {
		initTableModel();
		initTableComponent();
		initDnD();
		initHeader();
		initColumnWidths();
		initColumnEditors();
		initIndexUpdater();
		initKeyBindingsTable();
		initMouseBehaviors();
		initRenderersCommon();   // всё, кроме 0 и 1
		initColumn0Renderer();   // только колонка 0
		initColumn1Renderer();   // только колонка 1
		actionTable.getColumnModel().removeColumn(actionTable.getColumnModel().getColumn(9));
		actionTable.getColumnModel().removeColumn(actionTable.getColumnModel().getColumn(8));
		actionTable.getColumnModel().removeColumn(actionTable.getColumnModel().getColumn(7));
		actionTable.getColumnModel().removeColumn(actionTable.getColumnModel().getColumn(6));
		createActionMenu();
	}

	private void initTableModel() {
		String[] columns = {"#", "Action", "Selector", "Value", "Comment",
				"Element Type", "Xpath", "Name", "Index", "By xpath"};

		tableModel = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return column != 0;
			}
		};
	}

	private void initTableComponent() {
		actionTable = new JTable(tableModel) {
			@Override
			public boolean editCellAt(int row, int column, EventObject e) {
				if (e instanceof MouseEvent me) {
					if (me.getClickCount() < 2) {
						return false;
					}
				}
				return super.editCellAt(row, column, e);
			}
		};
		actionTable.setFillsViewportHeight(true);
		actionTable.setRowHeight(28);
		actionTable.setShowGrid(true);
		actionTable.setGridColor(new Color(180, 180, 180));
		actionTable.setIntercellSpacing(new Dimension(2, 2));
	}

	private void initDnD() {
		actionTable.setDragEnabled(true);
		actionTable.setDropMode(DropMode.INSERT_ROWS);
		actionTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		actionTable.setTransferHandler(new TableRowTransferHandler(actionTable, this));
	}

	private void initHeader() {
		JTableHeader header = actionTable.getTableHeader();
		header.setBackground(new Color(200, 200, 200));
		header.setForeground(Color.BLACK);
		header.setOpaque(true);

		DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
		headerRenderer.setBackground(new Color(200, 200, 200));
		headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
		header.setDefaultRenderer(headerRenderer);
	}

	private void initColumnWidths() {
		if (config.actionTableColumnWidths.isEmpty()) {
			actionTable.getColumnModel().getColumn(0).setPreferredWidth(50);
			return;
		}

		List<String> columnList = Arrays.stream(
				new String[]{"#", "Action", "Selector", "Value", "Comment",
						"Element Type", "Xpath", "Name", "Index", "By xpath"}
		).toList();

		for (String column : columnList) {
			if (config.actionTableColumnWidths.containsKey(column)) {
				int index = columnList.indexOf(column);
				int width = config.actionTableColumnWidths.get(column);
				actionTable.getColumnModel().getColumn(index).setPreferredWidth(width);
				actionTable.getColumnModel().getColumn(index).setWidth(width);
			}
		}
	}

	private void initColumnEditors() {
		// колонка 1 — UserAction
		JComboBox<UserAction> actionComboBox = new JComboBox<>(UserAction.values());
		actionComboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);

		ItemListener listener = e -> {
			if (e.getStateChange() != ItemEvent.SELECTED) return;

			UserAction selected = (UserAction) e.getItem();
			if (selected != UserAction.CUSTOM_METHOD) return;

			CustomMethodsService.MethodDef method = showCustomMethodChooser();
			if (method == null) {
				// Ничего не делаем, просто выходим, выбор останется как есть
				return;
			}

			int editingRow = actionTable.getEditingRow();
			if (editingRow < 0) {
				return;
			}
			int modelRow = actionTable.convertRowIndexToModel(editingRow);
			int valueColIndex = 3; // "Value"

			if (actionTable.isEditing()) {
				actionTable.getCellEditor().stopCellEditing(); // критично важно[web:2]
			}

			tableModel.setValueAt(method.getName(), modelRow, valueColIndex);
		};

		actionComboBox.addItemListener(listener);

		DefaultCellEditor actionEditor = new DefaultCellEditor(actionComboBox);
		actionTable.getColumnModel().getColumn(1).setCellEditor(actionEditor);

		// колонка 2 — Selector
		SelectorCellEditor selectorEditor = new SelectorCellEditor();
		selectorEditor.setLocatorPicker(callback -> {
			if (actionRecorder == null) return;
			actionRecorder.startLocatorPick(callback);
		});
		selectorEditor.setLocatorHighlighter(xpath -> {
			if (actionRecorder == null) return;
			actionRecorder.highlightByXpath(xpath);
		});
		actionTable.getColumnModel().getColumn(2).setCellEditor(selectorEditor);

		ValueCellEditor valueEditor = new ValueCellEditor(actionTable, variablesService);
		actionTable.getColumnModel().getColumn(3).setCellEditor(valueEditor);


		// колонка 5 — ElementType
		JComboBox<ElementType> elementComboBox = new JComboBox<>(ElementType.values());
		actionTable.getColumnModel().getColumn(5)
				.setCellEditor(new DefaultCellEditor(elementComboBox));
	}

	private CustomMethodsService.MethodDef showCustomMethodChooser() {
		customMethodsService.load();
		java.util.List<CustomMethodsService.MethodDef> methods = customMethodsService.getMethods();
		if (methods == null || methods.isEmpty()) {
			JOptionPane.showMessageDialog(
					this,
					"Список кастомных методов пуст",
					"Custom method",
					JOptionPane.WARNING_MESSAGE
			);
			return null;
		}

		JComboBox<CustomMethodsService.MethodDef> combo =
				new JComboBox<>(methods.toArray(new CustomMethodsService.MethodDef[0]));
		combo.setSelectedIndex(0);

		int result = JOptionPane.showConfirmDialog(
				this,
				combo,
				"Выберите кастомный метод",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE
		);

		if (result != JOptionPane.OK_OPTION) {
			return null;
		}

		Object selected = combo.getSelectedItem();
		return (selected instanceof CustomMethodsService.MethodDef)
				? (CustomMethodsService.MethodDef) selected
				: null;
	}

	private void initIndexUpdater() {
		TableModelListener[] holder = new TableModelListener[1];

		TableModelListener indexUpdater = e -> {
			tableModel.removeTableModelListener(holder[0]);

			int rowCount = tableModel.getRowCount();
			for (int i = 0; i < rowCount; i++) {
				Object cur = tableModel.getValueAt(i, 0);
				if (!(cur instanceof Integer) || ((Integer) cur) != i) {
					tableModel.setValueAt(i, i, 0);
				}
			}

			tableModel.addTableModelListener(holder[0]);
		};

		holder[0] = indexUpdater;
		tableModel.addTableModelListener(indexUpdater);
	}

	private void initKeyBindingsTable() {
		InputMap im = actionTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		ActionMap am = actionTable.getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear-selection");
		am.put("clear-selection", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				actionTable.clearSelection();
			}
		});

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete-row");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete-row");

		am.put("delete-row", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int[] rows = actionTable.getSelectedRows();
				if (rows.length == 0) return;
				for (int i = rows.length - 1; i >= 0; i--) {
					deleteRow(rows[i]);
				}
			}
		});
	}

	private void initRenderersCommon() {
		actionTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(
					JTable table, Object value,
					boolean isSelected, boolean hasFocus,
					int row, int column) {

				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				int modelRow = table.convertRowIndexToModel(row);
				int current = playActionService.getCurrentRow();

				if (modelRow == current) {
					c.setBackground(new Color(255, 250, 180));
				} else {
					if (isSelected) {
						c.setBackground(table.getSelectionBackground());
					} else {
						c.setBackground(table.getBackground());
					}
				}
				return c;
			}
		});
	}

	private void initColumn0Renderer() {
		actionTable.getColumnModel().getColumn(0)
				.setCellRenderer(new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(
							JTable table, Object value,
							boolean isSelected, boolean hasFocus,
							int row, int column) {

						Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						setHorizontalAlignment(SwingConstants.CENTER);

						int modelRow = table.convertRowIndexToModel(row);
						int current = playActionService.getCurrentRow();
						Color mark = rowMarks.get(modelRow);

						if (modelRow == current) {
							c.setBackground(new Color(255, 250, 180));
							c.setForeground(Color.BLACK);
						} else if (mark != null) {
							c.setBackground(mark);
							c.setForeground(Color.BLACK);
						} else {
							if (isSelected) {
								c.setBackground(table.getSelectionBackground());
								c.setForeground(table.getSelectionForeground());
							} else {
								c.setBackground(table.getBackground());
								c.setForeground(table.getForeground());
							}
						}
						return c;
					}
				});
	}

	private void initColumn1Renderer() {
		actionTable.getColumnModel().getColumn(1)
				.setCellRenderer(new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(
							JTable table, Object value,
							boolean isSelected, boolean hasFocus,
							int row, int column) {

						Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						if (value instanceof UserAction ua) {
							setText(ua.getCode());
						}
						applyCurrentRowHighlight(c, table, isSelected, row);
						return c;
					}
				});

		actionTable.getColumnModel().getColumn(5)
				.setCellRenderer(new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(
							JTable table, Object value,
							boolean isSelected, boolean hasFocus,
							int row, int column) {

						Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						if (value instanceof ElementType et) {
							setText(et.getClassName());
						}
						applyCurrentRowHighlight(c, table, isSelected, row);
						return c;
					}
				});
	}


	private void initMouseBehaviors() {
		actionTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				int row = actionTable.rowAtPoint(e.getPoint());
				int col = actionTable.columnAtPoint(e.getPoint());
				if (row == -1 || col == -1) {
					actionTable.clearSelection();
				}
			}
		});
	}


	private void createActionMenu() {
		new ActionTableContextMenu(actionTable, new ActionTableContextMenu.Callback() {
			@Override
			public void deleteSelectedRow() {
				int[] viewRows = actionTable.getSelectedRows();
				if (viewRows.length == 0) return;

				int[] modelRows = Arrays.stream(viewRows)
						.map(actionTable::convertRowIndexToModel)
						.sorted()
						.toArray();

				for (int i = modelRows.length - 1; i >= 0; i--) {
					deleteRow(modelRows[i]); // вынеси твою логику из deleteRow в этот метод
				}
			}

			@Override
			public void toggleMarkSelectedRow() {
				int[] viewRows = actionTable.getSelectedRows();
				if (viewRows.length == 0) return;

				int[] modelRows = Arrays.stream(viewRows)
						.map(actionTable::convertRowIndexToModel)
						.sorted()
						.toArray();

				// спросим цвет один раз, применим ко всем
				int firstRow = modelRows[0];
				Color mark = showMarkColorChooser(firstRow); // изменим showMarkColorChooser

				for (int modelRow : modelRows) {
					if (mark == null) {
						rowMarks.remove(modelRow); // условный сигнал «снять метку»
					} else {
						rowMarks.put(modelRow, mark);
					}
				}

				actionTable.repaint();
			}

			@Override
			public void startScenarioFromSelectedRow() {
				int[] viewRows = actionTable.getSelectedRows();
				if (viewRows.length != 1 || playActionService.isStopRequested()) {
					// можно ничего не делать или показать сообщение
					return;
				}
				int viewRow = viewRows[0];

				int modelRow = actionTable.convertRowIndexToModel(viewRow);

				playButton.setText("■");
				playButton.setToolTipText("Stop scenario");
				playActionService.playActionsFromTable(ActionWindow.this, modelRow, false);
			}

			@Override
			public void playOnlyStep() {
				int[] viewRows = actionTable.getSelectedRows();
				if (viewRows.length != 1 || playActionService.isStopRequested()) {
					return;
				}
				int viewRow = viewRows[0];

				int modelRow = actionTable.convertRowIndexToModel(viewRow);

				playButton.setText("■");
				playButton.setToolTipText("Stop scenario");
				playActionService.playActionsFromTable(ActionWindow.this, modelRow, true);
			}

			@Override
			public void createMethodFromSelectedSteps() {
				int[] viewRows = actionTable.getSelectedRows();
				if (viewRows.length < 2) return;

				int[] modelRows = Arrays.stream(viewRows)
						.map(actionTable::convertRowIndexToModel)
						.sorted()
						.toArray();

				// 1) спросить имя и файл
				CustomMethodSaveData data = askCustomMethodNameAndFile();
				if (data == null) {
					return;
				}

				List<dto.ActionRecord> records = buildActionRecordsForRows(modelRows);

				// 3) сохранить JSON
				try (Writer writer = new OutputStreamWriter(
						new FileOutputStream(data.file), StandardCharsets.UTF_8)) {

					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					gson.toJson(records, writer);
				} catch (Exception ex) {
					ex.printStackTrace();
					JOptionPane.showMessageDialog(
							ActionWindow.this,
							"Failed to save custom method steps: " + ex.getMessage(),
							"Error",
							JOptionPane.ERROR_MESSAGE
					);
					return;
				}

				// 4) добавить запись в CustomMethodsService
				customMethodsService.load(); // подтянуть текущее
				customMethodsService.addMethod(data.name, data.file.getAbsolutePath());
				customMethodsService.save();

				JOptionPane.showMessageDialog(
						ActionWindow.this,
						"Custom method '" + data.name + "' saved to:\n" + data.file.getAbsolutePath(),
						"Saved",
						JOptionPane.INFORMATION_MESSAGE
				);
			}
		});

	}

	private void initBottomPanel() {
		themeSelect = new JComboBox<>(new String[]{"Light", "Dark"});
		String theme = "Dark".equalsIgnoreCase(config.theme) ? "Dark" : "Light";
		themeSelect.setSelectedItem(theme);

		themeSelect.addActionListener(e -> {
			Object sel = themeSelect.getSelectedItem();
			if ("Light".equals(sel)) {
				FlatLightLaf.setup();
				config.theme = "Light";
			} else if ("Dark".equals(sel)) {
				FlatDarkLaf.setup();
				config.theme = "Dark";
			}
			SwingUtilities.updateComponentTreeUI(this);
			try {
				configService.save(config);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});

		driverPathField = new JTextField(20);
		driverPathField.setText(
				(config.chromeDriverPath != null && !config.chromeDriverPath.isEmpty())
						? config.chromeDriverPath
						: ""
		);
	}

	private void toggleRecording() {
		if (driver == null) {
			JOptionPane.showMessageDialog(
					this,
					"Browser is not open. Please open browser first.",
					"Browser Required",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		if (playActionService.isStopRequested() && !actionRecorder.isRecording()) {
			playActionService.setStopRequested(false);
			playButton.setText("▶");
			playButton.setToolTipText("Run actions from table in browser");
		}

		actionRecorder.toggleRecording();

		if (actionRecorder.isRecording()) {
			recordingButton.setText("⏹ Stop Recording");
			System.out.println("Recording started");
		} else {
			recordingButton.setText("⏺ Start Recording");
			System.out.println("Recording stopped");
		}
	}


	private JPanel createBottomPanel() {
		JPanel bottomPanel = new JPanel(new BorderLayout());

		JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Settings"));
		settingsPanel.add(new JLabel("Theme:"));
		settingsPanel.add(themeSelect);
		settingsPanel.add(Box.createHorizontalStrut(20));
		settingsPanel.add(new JLabel("ChromeDriver Path:"));
		settingsPanel.add(driverPathField);

		JButton browseButton = new JButton("Browse...");
		browseButton.setToolTipText("Select ChromeDriver executable");
		ToolTipManager.sharedInstance().setInitialDelay(200);
		browseButton.addActionListener(e -> browserService.selectChromeDriver(this, driverPathField));
		settingsPanel.add(browseButton);

		bottomPanel.add(settingsPanel, BorderLayout.SOUTH);
		return bottomPanel;
	}

	private void addNewAction() {
//		int rowIndex = tableModel.getRowCount();
		Object[] row = new Object[]{null, UserAction.CLICK, "", "", "", ElementType.BUTTON, ""};
		int rowIndex = tableModel.getRowCount();
		tableModel.addRow(row);

		pushUndo(
				() -> {
					if (tableModel.getRowCount() > rowIndex) {
						tableModel.removeRow(rowIndex);
					}
				},
				// redo: снова вставить строку
				() -> {
					if (tableModel.getRowCount() >= rowIndex) {
						tableModel.insertRow(rowIndex, row);
					} else {
						tableModel.addRow(row);
					}
				}
		);
	}

	private void saveTableToFile() {
		fileService.saveWithModeDialog();
	}

	private void initKeyBindings() {
		JRootPane root = getRootPane();
		InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = root.getActionMap();

		// Ctrl+Z -> undo
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undo");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.META_DOWN_MASK), "undo");
		am.put("undo", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!undoStack.isEmpty()) {
					Runnable undo = undoStack.pop();
					undo.run();
					// сюда положишь связанный redo, когда сделаешь нормальную пару
				}
			}
		});

//		// Ctrl+Y -> redo
//		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "redo");
//		am.put("redo", new AbstractAction() {
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				if (!redoStack.isEmpty()) {
//					Runnable redo = redoStack.pop();
//					redo.run();
//					// и обратно в undoStack, если нужно
//				}
//			}
//		});
	}

	private void openSettingsDialog() {
		JDialog dialog = new JDialog(this, "Settings", true);
		dialog.setSize(600, 400);
		dialog.setLocationRelativeTo(this);
		dialog.setLayout(new BorderLayout());

		JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);

		JPanel mainPanel = new JPanel();
		mainPanel.add(new JLabel("Main settings (TODO move theme/chromeDriver here)"));
		tabs.addTab("Main", mainPanel);

		JPanel customMethods = customMethodsService.createCustomMethodsSettingsPanel(dialog);
		tabs.addTab("CustomMethods", customMethods);

		JPanel variables = variablesService.createVariablesSettingsPanel(dialog);
		tabs.addTab("Variables", variables);

		JPanel usersPanel = usersService.createUsersSettingsPanel(dialog);
		tabs.addTab("Users", usersPanel);

		JPanel openApiPanel = openApiService.createOpenApiSettingsPanel(dialog);
		tabs.addTab("OpenApi", openApiPanel);

		dialog.add(tabs, BorderLayout.CENTER);

		JButton closeBtn = new JButton("Close");
		closeBtn.addActionListener(e -> dialog.dispose());
		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bottom.add(closeBtn);
		dialog.add(bottom, BorderLayout.SOUTH);

		dialog.setVisible(true);
	}

	private void pushUndo(Runnable undo, Runnable redo) {
		undoStack.push(undo);
		// при новом действии история redo сбрасывается
		redoStack.clear();
		while (undoStack.size() > UNDO_LIMIT) {
			undoStack.removeLast();
		}
		// чтобы redo работал, кладём противоположное действие
		redoStack.push(redo);
	}

	public void pushMoveUndo(int from, int to, Object[] rowData) {
		pushUndo(
				() -> {
					DefaultTableModel model = (DefaultTableModel) actionTable.getModel();
					model.removeRow(to);
					model.insertRow(from, rowData);
					actionTable.getSelectionModel().setSelectionInterval(from, from);
				},
				() -> {
					DefaultTableModel model = (DefaultTableModel) actionTable.getModel();
					model.removeRow(from);
					model.insertRow(to, rowData);
					actionTable.getSelectionModel().setSelectionInterval(to, to);
				}
		);
	}

	private void saveColumnWidthsToConfig() {
		if (config == null) return;

		Map<String, Integer> map = new LinkedHashMap<>();
		int columnCount = actionTable.getColumnModel().getColumnCount();

		for (int i = 0; i < columnCount; i++) {
			String name = actionTable.getColumnModel().getColumn(i).getHeaderValue().toString();
			int width = actionTable.getColumnModel().getColumn(i).getWidth();
			map.put(name, width);
		}

		config.actionTableColumnWidths = map;

		try {
			configService.save(config);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	// в классе ActionWindow
	public void repaintActionTable() {
		if (actionTable != null) {
			actionTable.repaint();
		}
	}

	private void applyCurrentRowHighlight(Component c, JTable table, boolean isSelected, int row) {
		int modelRow = table.convertRowIndexToModel(row);
		int current = playActionService.getCurrentRow();

		if (modelRow == current) {
			c.setBackground(new Color(255, 250, 180));
		} else {
			if (isSelected) {
				c.setBackground(table.getSelectionBackground());
			} else {
				c.setBackground(table.getBackground());
			}
		}
	}

	private void deleteRow(int row) {
		// 1. если сейчас редактируется ячейка — аккуратно остановить
		if (actionTable.isEditing()) {
			int col = actionTable.getEditingColumn();
			TableCellEditor editor = actionTable.getCellEditor(row, col);
			if (editor != null) {
				// если редактор не согласен остановиться — просто не удаляем
				if (!editor.stopCellEditing()) {
					return;
				}
			}
		}

		if (row >= 0) {
			Object[] data = new Object[tableModel.getColumnCount()];
			for (int c = 0; c < data.length; c++) {
				data[c] = tableModel.getValueAt(row, c);
			}
			int rowIndex = row;

			tableModel.removeRow(rowIndex);

			pushUndo(
					() -> {
						tableModel.insertRow(rowIndex, data);
						actionTable.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
					},
					() -> {
						if (rowIndex < tableModel.getRowCount()) {
							tableModel.removeRow(rowIndex);
						}
					}
			);
		}
	}

	private Color showMarkColorChooser(int modelRow) {
		Object[] options = {"Красный", "Зелёный", "Синий", "Жёлтый", "Снять метку"};
		int choice = JOptionPane.showOptionDialog(
				this,
				"Выбери цвет метки для строки " + modelRow,
				"Mark row",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.PLAIN_MESSAGE,
				null,
				options,
				options[0]
		);

		if (choice == -1) {
			return null; // отмена
		}

		return switch (choice) {
			case 0 -> new Color(255, 150, 150); // мягкий красный
			case 1 -> new Color(180, 240, 180); // мягкий зелёный
			case 2 -> new Color(150, 180, 255); // мягкий синий
			case 3 -> new Color(255, 250, 180); // мягкий жёлтый
			default -> null;
		};
	}

	private void toggleScenario(int rowStart) {
		if (playActionService.isStopRequested() && actionRecorder.isRecording()) {
			actionRecorder.toggleRecording();
		}
		if (!playActionService.isStopRequested()) {
			playButton.setText("■");
			playButton.setToolTipText("Stop scenario");
			playActionService.playActionsFromTable(this, rowStart, false);
		} else {
			playActionService.setStopRequested(false);
			playButton.setText("▶");
			playButton.setToolTipText("Run actions from table in browser");
		}
	}

	public void onScenarioFinished() {
		SwingUtilities.invokeLater(() -> {
			playButton.setText("▶");
			playButton.setToolTipText("Run actions from table in browser");
		});
	}

	private static class CustomMethodSaveData {
		final String name;
		final File file;
		CustomMethodSaveData(String name, File file) {
			this.name = name;
			this.file = file;
		}
	}

	private CustomMethodSaveData askCustomMethodNameAndFile() {
		JTextField nameField = new JTextField(20);
		JButton browseBtn = new JButton("Browse...");
		JTextField pathField = new JTextField(25);

		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
		panel.add(new JLabel("Method name:"), gbc);
		gbc.gridx = 1; gbc.weightx = 1.0;
		panel.add(nameField, gbc);

		gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
		panel.add(new JLabel("File path:"), gbc);
		gbc.gridx = 1; gbc.weightx = 1.0;
		panel.add(pathField, gbc);
		gbc.gridx = 2; gbc.weightx = 0;
		panel.add(browseBtn, gbc);

		browseBtn.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Select file to save custom method");
			chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
					"JSON files", "json"));
			int res = chooser.showSaveDialog(this);
			if (res == JFileChooser.APPROVE_OPTION) {
				File f = chooser.getSelectedFile();
				if (!f.getName().toLowerCase().endsWith(".json")) {
					f = new File(f.getParentFile(), f.getName() + ".json");
				}
				pathField.setText(f.getAbsolutePath());
			}
		});

		int result = JOptionPane.showConfirmDialog(
				this,
				panel,
				"Save selected steps as custom method",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE
		);

		if (result != JOptionPane.OK_OPTION) {
			return null;
		}

		String name = nameField.getText().trim();
		String path = pathField.getText().trim();
		if (name.isEmpty() || path.isEmpty()) {
			JOptionPane.showMessageDialog(
					this,
					"Method name and file path must not be empty",
					"Validation error",
					JOptionPane.WARNING_MESSAGE
			);
			return null;
		}

		return new CustomMethodSaveData(name, new File(path));
	}

	private List<dto.ActionRecord> buildActionRecordsForRows(int[] modelRows) {
		List<dto.ActionRecord> list = new ArrayList<>();
		for (int r : modelRows) {
			Object actionObj = tableModel.getValueAt(r, 1);
			String actionCode = null;
			if (actionObj instanceof UserAction ua) {
				actionCode = ua.getCode();
			} else if (actionObj != null) {
				actionCode = actionObj.toString();
			}

			Object elementTypeObj = tableModel.getValueAt(r, 5);
			String elementType = null;
			if (elementTypeObj instanceof ElementType et) {
				elementType = et.getClassName();
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

			list.add(new dto.ActionRecord(
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
		return list;
	}

	private String val(int row, int col) {
		Object v = tableModel.getValueAt(row, col);
		return v == null ? null : v.toString();
	}

	private void openBrowserAsync() {
		// чтобы пользователь не тыкал по 10 раз
		openBrowserButton.setEnabled(false);

		new SwingWorker<ChromeDriver, Void>() {
			@Override
			protected ChromeDriver doInBackground() {
				// тяжёлая часть — в фоне
				return browserService.openBrowser(
						ActionWindow.this,
						driverPathField,
						driver
				);
			}

			@Override
			protected void done() {
				try {
					ChromeDriver newDriver = get(); // может быть null
					if (newDriver != null) {
						driver = newDriver;
						driver.manage().window().maximize();
						WebDriverRunner.setWebDriver(driver);
						open("https://test-iqhr.rt.ru/"); // как у тебя
						actionRecorder.setDriver(driver);
						playActionService.setDriver(driver);
						System.out.println(
								"ChromeDriver initialized successfully with: "
										+ driverPathField.getText().trim()
						);
					}
				} catch (Throwable ex) {
					String errorMessage = ex.getMessage();

					if (errorMessage != null && (
							errorMessage.contains("DevToolsActivePort") ||
									errorMessage.contains("Chrome failed to start") ||
									errorMessage.contains("exited normally")
					)) {
						JOptionPane.showMessageDialog(
								ActionWindow.this,
								"Wrong Chrome version selected!\n\n" +
										"You selected a ChromeDriver executable, but need to select 'Google Chrome for Testing' application.\n\n" +
										"Please select the correct Chrome for Testing application.",
								"Wrong Chrome Version",
								JOptionPane.ERROR_MESSAGE
						);
					} else {
						JOptionPane.showMessageDialog(
								ActionWindow.this,
								"Failed to open browser: " + errorMessage,
								"Error",
								JOptionPane.ERROR_MESSAGE
						);
					}

					ex.printStackTrace();
				} finally {
					openBrowserButton.setEnabled(true);
				}
			}
		}.execute();
	}

}

