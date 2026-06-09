package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.AppConfig;
import dto.BackendRequestDef;
import ui.AbstractTableSettingsPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public class BackendRequestsService extends AbstractTableSettingsPanel {

	private static final String[] TABLE_COLUMNS = {"Name", "Method", "URL"};

	private JTable backendTable;
	private DefaultTableModel backendTableModel;

	private final ConfigService configService;
	private final AppConfig config;
	private final List<BackendRequestDef> requests = new ArrayList<>();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public BackendRequestsService(ConfigService configService, AppConfig config) {
		this.configService = configService;
		this.config = config;
	}

	public JPanel createBackendRequestsSettingsPanel(JDialog parentDialog) {
		JPanel panel = buildTablePanel(
				"Backend Requests",
				TABLE_COLUMNS,
				() -> saveFromTable(parentDialog),
				null
		);

		this.backendTable = this.table;
		this.backendTableModel = this.model;

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

	public void openEditDtoDialogFor(Component parent, BackendRequestDef def) {
		JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(parent),
				"Edit DTO — " + def.getName(), Dialog.ModalityType.APPLICATION_MODAL);
		dlg.setSize(700, 560);
		dlg.setLocationRelativeTo(parent);
		dlg.setLayout(new BorderLayout(8, 8));

		JPanel top = new JPanel(new GridLayout(3, 2, 6, 6));
		top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

		JTextField nameField = new JTextField(def.getName());
		JTextField methodField = new JTextField(def.getMethod());
		JTextField urlField = new JTextField(def.getUrl());

		top.add(new JLabel("Name:"));
		top.add(nameField);
		top.add(new JLabel("Method:"));
		top.add(methodField);
		top.add(new JLabel("URL:"));
		top.add(urlField);

		dlg.add(top, BorderLayout.NORTH);

		JTextArea bodyArea = new JTextArea(def.getRequestBody() != null ? def.getRequestBody() : "");
		bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		JScrollPane bodyScroll = new JScrollPane(bodyArea);
		bodyScroll.setBorder(BorderFactory.createTitledBorder("Request Body (DTO)"));

		JTextArea headersArea = new JTextArea(def.getRequestHeaders() != null ? def.getRequestHeaders() : "{}");
		headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		JScrollPane headersScroll = new JScrollPane(headersArea);
		headersScroll.setBorder(BorderFactory.createTitledBorder("Request Headers (JSON)"));

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bodyScroll, headersScroll);
		splitPane.setDividerLocation(320);
		dlg.add(splitPane, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveBtn = new JButton("Save");
		JButton cancelBtn = new JButton("Cancel");

		saveBtn.addActionListener(e -> {
			def.setName(nameField.getText().trim());
			def.setMethod(methodField.getText().trim().toUpperCase());
			def.setUrl(urlField.getText().trim());
			def.setRequestBody(bodyArea.getText());
			def.setRequestHeaders(headersArea.getText());
			save();
			dlg.dispose();
		});
		cancelBtn.addActionListener(e -> dlg.dispose());

		buttons.add(saveBtn);
		buttons.add(cancelBtn);
		dlg.add(buttons, BorderLayout.SOUTH);

		dlg.setVisible(true);
	}

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

	private Path getFile() throws Exception {
		return configService.getBackendRequestsFile(config);
	}

	public void load() {
		requests.clear();
		try {
			Path file = getFile();
			if (!Files.exists(file)) {
				return;
			}
			String json = Files.readString(file);
			BackendRequestDef[] arr = gson.fromJson(json, BackendRequestDef[].class);
			if (arr != null) {
				requests.addAll(Arrays.asList(arr));
			}
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