package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.AppConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public class CustomMethodsService {

	public static class MethodDef {
		private String name;
		private String path;

		public MethodDef(String name, String path) {
			this.name = name;
			this.path = path;
		}

		public String getName() { return name; }
		public String getPath() { return path; }

		@Override
		public String toString() {
			return name;
		}
	}

	private JTable customMethodsTable;
	private DefaultTableModel customMethodsTableModel;
	private final ConfigService configService;
	private final AppConfig config;

	// внутренняя коллекция как у OpenApiService (там map, здесь список)
	private final List<MethodDef> methods = new ArrayList<>();

	// Gson один раз на сервис
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public CustomMethodsService(ConfigService configService, AppConfig config) {
		this.configService = configService;
		this.config = config;
	}

	// ---------- SETTINGS PANEL ----------

	public JPanel createCustomMethodsSettingsPanel(JDialog parentDialog) {
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		String[] cols = {"Method", "Path"};
		customMethodsTableModel = new DefaultTableModel(cols, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return true;
			}
		};
		customMethodsTable = new JTable(customMethodsTableModel);

		customMethodsTable.setRowHeight(24);
		customMethodsTable.setShowHorizontalLines(true);
		customMethodsTable.setShowVerticalLines(true);
		customMethodsTable.setGridColor(new Color(180, 180, 180));
		customMethodsTable.setIntercellSpacing(new Dimension(1, 1));
		customMethodsTable.setFillsViewportHeight(true);

		JScrollPane scroll = new JScrollPane(customMethodsTable);
		scroll.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(new Color(150, 150, 150)),
						BorderFactory.createEmptyBorder(2, 2, 2, 2)
				)
		);
		panel.add(scroll, BorderLayout.CENTER);

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton addBtn = new JButton("+");
		JButton removeBtn = new JButton("-");

		addBtn.addActionListener(e ->
				customMethodsTableModel.addRow(new Object[]{"", ""})
		);
		removeBtn.addActionListener(e -> {
			int row = customMethodsTable.getSelectedRow();
			if (row >= 0) {
				customMethodsTableModel.removeRow(row);
			}
		});

		top.add(new JLabel("Custom methods:"));
		top.add(addBtn);
		top.add(removeBtn);
		panel.add(top, BorderLayout.NORTH);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));

		JButton browseBtn = new JButton("Browse...");
		browseBtn.addActionListener(e -> openPathFileChooser(parentDialog));

		JButton saveBtn = new JButton("Save");
		saveBtn.addActionListener(e -> {
			if (customMethodsTable.isEditing()) {
				customMethodsTable.getCellEditor().stopCellEditing();
			}
			saveCustomMethods(parentDialog);
		});

		bottom.add(browseBtn);
		bottom.add(saveBtn);
		panel.add(bottom, BorderLayout.SOUTH);

		// 1) грузим из файла во внутренний список
		load();
		// 2) отображаем во вью
		loadCustomMethodsIntoTable();

		return panel;
	}

	// ---------- TABLE <-> LIST BINDING ----------

	private void loadCustomMethodsIntoTable() {
		customMethodsTableModel.setRowCount(0);
		for (MethodDef m : this.getMethods()) {
			customMethodsTableModel.addRow(new Object[]{m.getName(), m.getPath()});
		}
	}

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

	// ---------- IN-MEMORY API ----------

	public List<MethodDef> getMethods() {
		return Collections.unmodifiableList(methods);
	}

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

	// ---------- PERSISTENCE (по образцу OpenApiService) ----------

	// тут использую аналогичный подход: отдельный json-файл рядом с конфигом
	private Path getCustomMethodsFile() throws Exception {
		// сделай в ConfigService метод вроде getCustomMethodsFile(config)
		return configService.getCustomMethodsFile(config);
	}

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

}
