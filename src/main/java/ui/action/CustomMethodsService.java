package ui.action;

import dto.AppConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
			return name; // для отображения в меню/списке
		}
	}


	private JTable customMethodsTable;
	private DefaultTableModel customMethodsTableModel;
	private final ConfigService configService;
	private final AppConfig config;

	public CustomMethodsService(ConfigService configService, AppConfig config) {
		this.configService = configService;
		this.config = config;
	}

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
		JButton saveBtn = new JButton("Save");
		saveBtn.addActionListener(e -> {
			if (customMethodsTable.isEditing()) {
				customMethodsTable.getCellEditor().stopCellEditing();
			}
			saveCustomMethods(parentDialog);
		});
		bottom.add(saveBtn);
		panel.add(bottom, BorderLayout.SOUTH);

		loadCustomMethodsIntoTable();

		return panel;
	}

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
			if (!name.isEmpty()) {
				list.add(new MethodDef(name, path));
			}
		}
		this.setMethods(list);
		this.save();
		// можно показать диалог "Saved"
	}

	private final List<MethodDef> methods = new ArrayList<>();

	public List<MethodDef> getMethods() {
		return Collections.unmodifiableList(methods);
	}

	public void setMethods(List<MethodDef> list) {
		methods.clear();
		methods.addAll(list);
	}

	public void addMethod(String name, String path) {
		methods.add(new MethodDef(name, path));
	}

	public void removeMethod(int index) {
		methods.remove(index);
	}

	// TODO: сюда же load/save (в файл/Preferences/JSON) — по аналогии с OpenAPI
	public void load() {
		// ...
	}

	public void save() {
		// ...
	}

	public MethodDef findByName(String name) {
		for (MethodDef m : methods) {
			if (Objects.equals(m.getName(), name)) {
				return m;
			}
		}
		return null;
	}
}

