package ui;

import com.codeborne.selenide.WebDriverRunner;
import dto.AppConfig;
import lombok.val;
import model.ElementType;
import model.UserAction;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import ui.action.*;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.*;
import java.util.List;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;

import static com.codeborne.selenide.Selenide.open;

public class ActionWindow extends JFrame {

	private static final int UNDO_LIMIT = 5;

	private final java.util.Deque<Runnable> undoStack = new java.util.ArrayDeque<>();
	private final java.util.Deque<Runnable> redoStack = new java.util.ArrayDeque<>();

	private JTable actionTable;
	private DefaultTableModel tableModel;
	private JButton addActionButton;
	private JButton menuButton;
	private JButton openBrowserButton;
	private JButton playButton;
	private JComboBox<String> themeSelect;
	private JTextField driverPathField;
	private Map<String, String> variables;
	private final ActionRecorder actionRecorder;
	private WebDriver driver;
	private JButton recordingButton;

	private final ActionFileService fileService;
	private final ConfigService configService = new ConfigService();
	private final AppConfig config;

	private final OpenApiService openApiService;
	private final UsersService usersService;
	private final PlayActionService playActionService;

	public ActionWindow() {
		config = configService.load();
		openApiService = new OpenApiService(configService, config);
		usersService = new UsersService(configService, config);

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
		playActionService =  new PlayActionService(tableModel, usersService);
		driver = null;
		fileService = new ActionFileService(this, tableModel);

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
		menuButton = new JButton("☰");
		menuButton.setFocusable(false);
		menuButton.setToolTipText("Menu");
		ToolTipManager.sharedInstance().setInitialDelay(200);

		JPopupMenu popup = new JPopupMenu();

		JMenuItem openItem = new JMenuItem("Open..");
		openItem.addActionListener(e -> {
			if (fileService != null) {
				fileService.loadFromJsonFile();
			}
		});
		popup.add(openItem);

		JMenuItem generateApiClientItem = new JMenuItem("Generate ApiClient");
		generateApiClientItem.addActionListener(e -> openApiService.openGenerateApiClientDialog(this));
		popup.add(generateApiClientItem);

		JMenuItem settingsItem = new JMenuItem("Settings");
		settingsItem.addActionListener(e -> openSettingsDialog());

		popup.add(settingsItem);

		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.addActionListener(e -> dispose());
		popup.add(exitItem);

		menuButton.addActionListener(e ->
				popup.show(menuButton, 0, menuButton.getHeight())
		);

		addActionButton = new JButton("+");
		addActionButton.setFont(new Font("Arial", Font.BOLD, 16));
		addActionButton.setToolTipText("Add action");
		ToolTipManager.sharedInstance().setInitialDelay(200);
		addActionButton.addActionListener(e -> addNewAction());

		openBrowserButton = new JButton("🌐 Open Browser");
		openBrowserButton.setToolTipText("Open Chrome for Testing browser");
		ToolTipManager.sharedInstance().setInitialDelay(200);
		openBrowserButton.addActionListener(e -> openBrowser());

		// НОВАЯ КНОПКА ПРОГОНА
		playButton = new JButton("▶");
		playButton.setToolTipText("Run actions from table in browser");
		ToolTipManager.sharedInstance().setInitialDelay(200);
		playButton.addActionListener(e -> playActionService.playActionsFromTable(this));

		recordingButton = new JButton("⏺ Start Recording");
		recordingButton.setToolTipText("Start/Stop recording");
		ToolTipManager.sharedInstance().setInitialDelay(200);
		recordingButton.addActionListener(e -> toggleRecording());
	}

	private JPanel createTopBarPanel() {
		JPanel topBar = new JPanel(new BorderLayout());
		topBar.setBorder(new EmptyBorder(5, 5, 5, 5));

		JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		leftButtons.add(menuButton);
		leftButtons.add(addActionButton);

		JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
		separator.setPreferredSize(new Dimension(1, 25));
		leftButtons.add(separator);

		JButton saveVarButton = new JButton("💾");
		saveVarButton.setToolTipText("Save table to file");
		ToolTipManager.sharedInstance().setInitialDelay(200);
		saveVarButton.addActionListener(e -> saveTableToFile());
		leftButtons.add(saveVarButton);

		JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		rightButtons.add(playButton);
		rightButtons.add(recordingButton);

		topBar.add(leftButtons, BorderLayout.WEST);
		topBar.add(openBrowserButton, BorderLayout.CENTER);
		topBar.add(rightButtons, BorderLayout.EAST);

		return topBar;
	}

	private void initActionTable() {
		String[] columns = {"#", "Action", "Selector", "Value", "Comment", "Element Type", "Xpath", "Name", "Index", "By xpath"};
		tableModel = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				// колонка с индексом не редактируется
				return column != 0;
			}
		};

		actionTable = new JTable(tableModel) {
			@Override
			public boolean editCellAt(int row, int column, EventObject e) {
				if (e instanceof java.awt.event.MouseEvent) {
					java.awt.event.MouseEvent me = (java.awt.event.MouseEvent) e;
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

		actionTable.setDragEnabled(true);
		actionTable.setDropMode(DropMode.INSERT_ROWS);
		actionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		actionTable.setTransferHandler(new TableRowTransferHandler(actionTable, this));

		JTableHeader header = actionTable.getTableHeader();
		header.setBackground(new Color(200, 200, 200));
		header.setForeground(Color.BLACK);
		header.setOpaque(true);
		DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
		headerRenderer.setBackground(new Color(200, 200, 200));
		headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
		header.setDefaultRenderer(headerRenderer);

		if (config.actionTableColumnWidths.isEmpty()) {
			actionTable.getColumnModel().getColumn(0).setPreferredWidth(50);
		} else {
			List<String> columnList = Arrays.stream(columns).toList();
			for (String column : columnList) {
				if (config.actionTableColumnWidths.containsKey(column)) {
					int index = columnList.indexOf(column);
					int width = config.actionTableColumnWidths.get(column);
					actionTable.getColumnModel().getColumn(index).setPreferredWidth(width);
					actionTable.getColumnModel().getColumn(index).setWidth(width);
				}
			}
		}
		actionTable.getColumnModel().getColumn(0).setCellRenderer(
				new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value,
																   boolean isSelected, boolean hasFocus,
																   int row, int column) {
						Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						setHorizontalAlignment(SwingConstants.CENTER);
						return c;
					}
				}
		);

		// колонка Action (индекс 1) с UserAction
		JComboBox<UserAction> actionComboBox = new JComboBox<>(UserAction.values());
		actionTable.getColumnModel().getColumn(1).setCellEditor(
				new DefaultCellEditor(actionComboBox)
		);
		actionTable.getColumnModel().getColumn(1).setCellRenderer(
				new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value,
																   boolean isSelected, boolean hasFocus,
																   int row, int column) {
						Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						if (value instanceof UserAction) {
							setText(((UserAction) value).getCode());
						}
						return c;
					}
				}
		);

		// Selector column (index 2)
		SelectorCellEditor selectorEditor = new SelectorCellEditor();
		selectorEditor.setLocatorPicker(callback -> {
			if (actionRecorder == null) return;
			actionRecorder.startLocatorPick(callback);
		});
		selectorEditor.setLocatorHighlighter(xpath -> {
			if (actionRecorder == null) return;
			actionRecorder.highlightByXpath(xpath);
		});
		actionTable.getColumnModel()
				.getColumn(2)
				.setCellEditor(selectorEditor);



		JComboBox<ElementType> elementComboBox = new JComboBox<>(ElementType.values());
		actionTable.getColumnModel().getColumn(5).setCellEditor(
				new DefaultCellEditor(elementComboBox)
		);
		actionTable.getColumnModel().getColumn(5).setCellRenderer(
				new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value,
																   boolean isSelected, boolean hasFocus,
																   int row, int column) {
						Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						if (value instanceof ElementType) {
							setText(((ElementType) value).getClassName());
						}
						return c;
					}
				}
		);

		TableModelListener[] holder = new TableModelListener[1];

		TableModelListener indexUpdater = e -> {
			// временно отключаем себя, чтобы не поймать рекурсию
			tableModel.removeTableModelListener(holder[0]);

			int rowCount = tableModel.getRowCount();
			for (int i = 0; i < rowCount; i++) {
				Object cur = tableModel.getValueAt(i, 0);
				if (!(cur instanceof Integer) || ((Integer) cur) != i) {
					tableModel.setValueAt(i, i, 0); // индексы с 0
				}
			}

			// возвращаем слушатель
			tableModel.addTableModelListener(holder[0]);
		};

		holder[0] = indexUpdater;
		tableModel.addTableModelListener(indexUpdater);

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
				// 1. если сейчас редактируется ячейка — аккуратно остановить
				if (actionTable.isEditing()) {
					int row = actionTable.getEditingRow();
					int col = actionTable.getEditingColumn();
					TableCellEditor editor = actionTable.getCellEditor(row, col);
					if (editor != null) {
						// если редактор не согласен остановиться — просто не удаляем
						if (!editor.stopCellEditing()) {
							return;
						}
					}
				}

				// 2. дальше твоя логика удаления
				int row = actionTable.getSelectedRow();
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
		});


		actionTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				int row = actionTable.rowAtPoint(e.getPoint());
				int col = actionTable.columnAtPoint(e.getPoint());

				// клик вне реальных ячеек таблицы
				if (row == -1 || col == -1) {
					actionTable.clearSelection();
				}
			}
		});

		actionTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(
					JTable table, Object value,
					boolean isSelected, boolean hasFocus,
					int row, int column) {

				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

				// переводим view-row в model-row (на случай сортировки/фильтра)
				int modelRow = table.convertRowIndexToModel(row);

				// currentRow берём из PlayActionService
				int current = playActionService.getCurrentRow(); // поле сервиса должно быть доступно в ActionWindow

				if (modelRow == current) {
					// мягкий жёлтый
					c.setBackground(new Color(255, 250, 180));
				} else {
					// стандартное поведение для selection / обычного фона
					if (isSelected) {
						c.setBackground(table.getSelectionBackground());
					} else {
						c.setBackground(table.getBackground());
					}
				}

				return c;
			}
		});

		actionTable.getColumnModel().getColumn(0).setCellRenderer(
				new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value,
																   boolean isSelected, boolean hasFocus,
																   int row, int column) {
						Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						setHorizontalAlignment(SwingConstants.CENTER);
						applyCurrentRowHighlight(c, table, isSelected, row);
						return c;
					}
				}
		);

		actionTable.getColumnModel().getColumn(1).setCellRenderer(
				new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value,
																   boolean isSelected, boolean hasFocus,
																   int row, int column) {
						Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						if (value instanceof UserAction) {
							setText(((UserAction) value).getCode());
						}
						applyCurrentRowHighlight(c, table, isSelected, row);
						return c;
					}
				}
		);

		actionTable.getColumnModel().getColumn(5).setCellRenderer(
				new DefaultTableCellRenderer() {
					@Override
					public Component getTableCellRendererComponent(JTable table, Object value,
																   boolean isSelected, boolean hasFocus,
																   int row, int column) {
						Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
						if (value instanceof ElementType) {
							setText(((ElementType) value).getClassName());
						}
						applyCurrentRowHighlight(c, table, isSelected, row);
						return c;
					}
				}
		);


		actionTable.getColumnModel().removeColumn(actionTable.getColumnModel().getColumn(9));
		actionTable.getColumnModel().removeColumn(actionTable.getColumnModel().getColumn(8));
		actionTable.getColumnModel().removeColumn(actionTable.getColumnModel().getColumn(7));
		actionTable.getColumnModel().removeColumn(actionTable.getColumnModel().getColumn(6));
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
		browseButton.addActionListener(e -> selectChromeDriver());
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
//	private void saveToVariable() {
//		JPanel panel = new JPanel();
//		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
//
//		JLabel nameLabel = new JLabel("Variable name:");
//		JTextField nameField = new JTextField(15);
//
//		JLabel valueLabel = new JLabel("Variable value:");
//		JTextField valueField = new JTextField(15);
//
//		panel.add(nameLabel);
//		panel.add(nameField);
//		panel.add(Box.createVerticalStrut(10));
//		panel.add(valueLabel);
//		panel.add(valueField);
//
//		int result = JOptionPane.showConfirmDialog(
//				this,
//				panel,
//				"Save Variable",
//				JOptionPane.OK_CANCEL_OPTION,
//				JOptionPane.PLAIN_MESSAGE
//		);
//
//		if (result == JOptionPane.OK_OPTION) {
//			String varName = nameField.getText().trim();
//			String varValue = valueField.getText().trim();
//
//			if (!varName.isEmpty() && !varValue.isEmpty()) {
//				variables.put(varName, varValue);
//				JOptionPane.showMessageDialog(
//						this,
//						"Variable '" + varName + "' saved successfully",
//						"Success",
//						JOptionPane.INFORMATION_MESSAGE
//				);
//			} else {
//				JOptionPane.showMessageDialog(
//						this,
//						"Variable name and value cannot be empty",
//						"Error",
//						JOptionPane.ERROR_MESSAGE
//				);
//			}
//		}
//	}

	private void selectChromeDriver() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if(System.getProperty("os.name").toLowerCase().contains("mac"))
			fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		fileChooser.setDialogTitle("Select ChromeDriver");

		fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) return true;
				String name = file.getName().toLowerCase();
				// Windows / Linux: chromedriver
				if (name.contains("chromedriver")) return true;
				// macOS: .app бандл
				if (name.endsWith(".app")) return true;
				// На всякий случай позволим выбрать любой бинарь
				return file.canExecute();
			}

			@Override
			public String getDescription() {
				return "Chrome/ChromeDriver executable (chromedriver, chromedriver.exe, *.app)";
			}
		});

		int result = fileChooser.showOpenDialog(this);

		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			String path = selectedFile.getAbsolutePath();
			driverPathField.setText(path);
			System.out.println("Selected ChromeDriver: " + path);

			config.chromeDriverPath = path;
			try {
				configService.save(config);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	private void openBrowser() {
		String driverPath = driverPathField.getText().trim();

		if (driverPath.isEmpty()) {
			JOptionPane.showMessageDialog(
					this,
					"ChromeDriver path is not set. Please select it first.",
					"ChromeDriver Path Required",
					JOptionPane.WARNING_MESSAGE
			);
			selectChromeDriver();
			return;
		}

		val nowDriver = isBrowserClosed(driver);

		if (!nowDriver) {
			JOptionPane.showMessageDialog(
					this,
					"Browser is already open",
					"Browser Running",
					JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}

		try {
			ArrayList<String> browserArgs = new ArrayList<>();
			browserArgs.add("no-sandbox");
			browserArgs.add("allow-running-insecure-content");

			Map<String, Object> prefs = new HashMap<>();
			prefs.put("intl.accept_languages", "ru");
			prefs.put("intl.selected_languages", "ru");

			ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.setExperimentalOption("prefs", prefs);
			chromeOptions.addArguments(browserArgs);
			chromeOptions.addArguments("--unsafely-treat-insecure-origin-as-secure=test-iqhr.rt.ru");
			chromeOptions.addArguments("--block-insecure-private-network-requests=Disabled");
			chromeOptions.addArguments("--ignore-certificate-errors");
			chromeOptions.addArguments("--ignore-urlfetcher-cert-requests");
			chromeOptions.setAcceptInsecureCerts(true);

			String osName = System.getProperty("os.name");
			if (osName.startsWith("Windows")) {
				System.setProperty("webdriver.chrome.driver", driverPath);
				chromeOptions.addArguments("--incognito");
			} else {
				chromeOptions.setBinary(driverPath);
			}

			ChromeDriver rawDriver = new ChromeDriver(chromeOptions);
			driver = rawDriver;
			driver.manage().window().maximize();
			WebDriverRunner.setWebDriver(driver);
			open("https://test-iqhr.rt.ru/");
			actionRecorder.setDriver(driver);
			playActionService.setDriver(driver);

//			JOptionPane.showMessageDialog(
//					this,
//					"Browser opened successfully",
//					"Success",
//					JOptionPane.INFORMATION_MESSAGE
//			);

			System.out.println("ChromeDriver initialized successfully with: " + driverPath);
		} catch (Exception e) {
			String errorMessage = e.getMessage();

			if (errorMessage != null && (errorMessage.contains("DevToolsActivePort") ||
					errorMessage.contains("Chrome failed to start") ||
					errorMessage.contains("exited normally"))) {
				JOptionPane.showMessageDialog(
						this,
						"Wrong Chrome version selected!\n\n" +
								"You selected a ChromeDriver executable, but need to select 'Google Chrome for Testing' application.\n\n" +
								"Please select the correct Chrome for Testing application.",
						"Wrong Chrome Version",
						JOptionPane.ERROR_MESSAGE
				);
			} else {
				JOptionPane.showMessageDialog(
						this,
						"Failed to open browser: " + errorMessage,
						"Error",
						JOptionPane.ERROR_MESSAGE
				);
			}

			e.printStackTrace();
			driver = null;
		}
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

		JPanel openApiPanel = openApiService.createOpenApiSettingsPanel(dialog);
		tabs.addTab("OpenApi", openApiPanel);

		JPanel usersPanel = usersService.createUsersSettingsPanel(dialog);
		tabs.addTab("Users", usersPanel);

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

	public static boolean isBrowserClosed(WebDriver driver) {
		if (driver == null) return true;
		try {
			driver.getTitle();
			driver.getTitle();
			driver.getTitle();
			return false;                   // сессия жива
		} catch (Exception e) {
			return true;                    // окно/сессия уже мертвы
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



}

