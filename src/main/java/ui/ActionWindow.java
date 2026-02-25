package ui;

import com.codeborne.selenide.WebDriverRunner;
import dto.ActionRecord;
import model.ElementType;
import model.UserAction;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.events.EventFiringDecorator;
import ui.action.ActionRecorder;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.UIManager;


import static com.codeborne.selenide.Selenide.open;

public class ActionWindow extends JFrame {

	private JTable actionTable;
	private DefaultTableModel tableModel;
	private JButton addActionButton;
	private JButton menuButton;
	private JButton openBrowserButton;
	private JComboBox<String> themeSelect;
	private JTextField driverPathField;
	private Map<String, String> variables;
	private ActionRecorder actionRecorder;
	private WebDriver driver;
	private JButton recordingButton;
	private TableColumn hiddenJavaColumn;

	public ActionWindow() {
		setTitle("Test Recorder – Action Panel");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(900, 650);
		setLocationRelativeTo(null);

		initTopBar();
		initActionTable();
		initBottomPanel();

		actionRecorder = new ActionRecorder(tableModel);
		driver = null;

		Container content = getContentPane();
		content.setLayout(new BorderLayout());
		content.add(createTopBarPanel(), BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(actionTable);
//		RowNumberTable rowHeader = new RowNumberTable(actionTable);
//		scrollPane.setRowHeaderView(rowHeader);
		content.add(scrollPane, BorderLayout.CENTER);

		content.add(createBottomPanel(), BorderLayout.SOUTH);
	}

	private void initTopBar() {
		menuButton = new JButton("☰");
		menuButton.setFocusable(false);
		menuButton.setToolTipText("Menu");
		ToolTipManager.sharedInstance().setInitialDelay(200);

		JPopupMenu popup = new JPopupMenu();
		popup.add(new JMenuItem("New test"));
		popup.add(new JMenuItem("Open..."));
		popup.addSeparator();
		popup.add(new JMenuItem("Exit"));

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

		topBar.add(leftButtons, BorderLayout.WEST);
		topBar.add(openBrowserButton, BorderLayout.CENTER);
		topBar.add(recordingButton, BorderLayout.EAST);

		return topBar;
	}

	private void initActionTable() {
		String[] columns = {"#", "Action", "Selector", "Value", "Comment", "Element Type"};
		tableModel = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				// колонка с индексом не редактируется
				return column != 0;
			}
		};

		actionTable = new JTable(tableModel);
		actionTable.setFillsViewportHeight(true);
		actionTable.setRowHeight(28);
		actionTable.setShowGrid(true);
		actionTable.setGridColor(new Color(180, 180, 180));
		actionTable.setIntercellSpacing(new Dimension(2, 2));

		actionTable.setDragEnabled(true);
		actionTable.setDropMode(DropMode.INSERT_ROWS);
		actionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		actionTable.setTransferHandler(new TableRowTransferHandler(actionTable));

		JTableHeader header = actionTable.getTableHeader();
		header.setBackground(new Color(200, 200, 200));
		header.setForeground(Color.BLACK);
		header.setOpaque(true);
		DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
		headerRenderer.setBackground(new Color(200, 200, 200));
		headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
		header.setDefaultRenderer(headerRenderer);

		// колонка с индексом (#)
		actionTable.getColumnModel().getColumn(0).setPreferredWidth(50);
		actionTable.getColumnModel().getColumn(0).setMaxWidth(50);
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

		hiddenJavaColumn = actionTable.getColumnModel().getColumn(5);
		actionTable.getColumnModel().removeColumn(hiddenJavaColumn);

	}


	private void initBottomPanel() {
		themeSelect = new JComboBox<>(new String[]{"Light", "Dark"});
		themeSelect.setSelectedItem("Light");
		themeSelect.addActionListener(e -> {
			Object sel = themeSelect.getSelectedItem();
			if ("Light".equals(sel)) {
				FlatLightLaf.setup();
			} else if ("Dark".equals(sel)) {
				FlatDarkLaf.setup();
			}

			// обновить внешний вид всего окна
			SwingUtilities.updateComponentTreeUI(this);
		});

		driverPathField = new JTextField(20);
		driverPathField.setText("/Applications/chrome/chrome/Google Chrome for Testing.app");
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
		tableModel.addRow(new Object[]{null, UserAction.CLICK, "", "", "", ElementType.BUTTON});
	}

	private void saveTableToFile() {
		if (tableModel.getRowCount() == 0) {
			JOptionPane.showMessageDialog(
					this,
					"Table is empty, nothing to save.",
					"Save Table",
					JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save actions");
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
				"JSON files", "json"));

		int result = chooser.showSaveDialog(this);
		if (result != JFileChooser.APPROVE_OPTION) {
			return;
		}

		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".json")) {
			file = new File(file.getParentFile(), file.getName() + ".json");
		}

		java.util.List<ActionRecord> rows = new java.util.ArrayList<>();
		int rowCount = tableModel.getRowCount();

		for (int r = 0; r < rowCount; r++) {
			// 0‑я колонка — индекс, данные начинаются с 1
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

			String selector   = val(r, 2);
			String value      = val(r, 3);
			String comment    = val(r, 4);

			rows.add(new ActionRecord(
					actionCode,
					selector,
					value,
					comment,
					elementType
			));
		}

		try (java.io.Writer writer = new java.io.OutputStreamWriter(
				new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {

			com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
					.setPrettyPrinting()
					.create();
			gson.toJson(rows, writer);
			writer.flush();

			JOptionPane.showMessageDialog(
					this,
					"Table saved to:\n" + file.getAbsolutePath(),
					"Save Successful",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					this,
					"Failed to save table: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	// удобный helper, чтобы не ловить NPE
	private String val(int row, int col) {
		Object v = tableModel.getValueAt(row, col);
		return v == null ? "" : v.toString();
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
		fileChooser.setDialogTitle("Select ChromeDriver");

		fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isDirectory() ||
						file.getName().contains("chromedriver");
			}

			@Override
			public String getDescription() {
				return "ChromeDriver executable (chromedriver, chromedriver.exe)";
			}
		});

		int result = fileChooser.showOpenDialog(this);

		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			driverPathField.setText(selectedFile.getAbsolutePath());
			System.out.println("Selected ChromeDriver: " + selectedFile.getAbsolutePath());
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

		if (driver != null) {
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
			actionRecorder.setDriver(driver);

			driver.navigate().to("about:blank");

//			JOptionPane.showMessageDialog(
//					this,
//					"Browser opened successfully",
//					"Success",
//					JOptionPane.INFORMATION_MESSAGE
//			);

			System.out.println("ChromeDriver initialized successfully with: " + driverPath);
			driver.navigate().to("https://test-iqhr.rt.ru/");
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

			System.err.println("Error initializing ChromeDriver: " + errorMessage);
			e.printStackTrace();
			driver = null;
		}
	}
}

