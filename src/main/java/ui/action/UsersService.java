package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.AppConfig;
import dto.UsersServiceSpec;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static ru.rt.iqhr.framework.util.StringUtils.isEmail;

public class UsersService {

	private final ConfigService configService;
	private final AppConfig config;
	private DefaultTableModel usersTableModel;
	private JTable usersTable;

	public UsersService(ConfigService configService, AppConfig config) {
		this.configService = configService;
		this.config = config;
	}

	// ----- SETTINGS PANEL -----

	public JPanel createUsersSettingsPanel(JDialog parentDialog) {
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		String[] cols = {"Role", "Username", "Password"};
		usersTableModel = new DefaultTableModel(cols, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return true;
			}
		};
		usersTable = new JTable(usersTableModel);

		usersTable.setRowHeight(24);
		usersTable.setShowHorizontalLines(true);
		usersTable.setShowVerticalLines(true);
		usersTable.setGridColor(new Color(180, 180, 180));
		usersTable.setIntercellSpacing(new Dimension(1, 1));
		usersTable.setFillsViewportHeight(true);

		JScrollPane scroll = new JScrollPane(usersTable);
		scroll.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(new Color(150, 150, 150)),
						BorderFactory.createEmptyBorder(2, 2, 2, 2)
				)
		);
		panel.add(scroll, BorderLayout.CENTER);

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton addBtn = new JButton("+");
		addBtn.setToolTipText("Добавить пользователя");
		JButton removeBtn = new JButton("-");
		removeBtn.setToolTipText("Удалить пользователя");

		addBtn.addActionListener(e -> usersTableModel.addRow(new Object[]{"", "", ""}));
		removeBtn.addActionListener(e -> {
			int row = usersTable.getSelectedRow();
			if (row >= 0) {
				usersTableModel.removeRow(row);
			}
		});

		top.add(new JLabel("Пользователи:"));
		top.add(addBtn);
		top.add(removeBtn);
		panel.add(top, BorderLayout.NORTH);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveBtn = new JButton("Сохранить");
		saveBtn.addActionListener(e -> {
			if (usersTable.isEditing()) {
				usersTable.getCellEditor().stopCellEditing();
			}
			saveUsersSpecs(parentDialog);
		});
		bottom.add(saveBtn);
		panel.add(bottom, BorderLayout.SOUTH);

		loadUsersSpecsIntoTable();

		return panel;
	}

	private void loadUsersSpecsIntoTable() {
		usersTableModel.setRowCount(0);
		dto.UsersServiceSpec[] arr = getAllUsers();
		if (arr != null) {
			for (dto.UsersServiceSpec s : arr) {
				usersTableModel.addRow(new Object[]{
						s.role != null ? s.role : "",
						s.username != null ? s.username : "",
						s.password != null ? s.password : ""
				});
			}
		}

	}

	private void saveUsersSpecs(JDialog dialog) {
		try {
			int rows = usersTableModel.getRowCount();
			java.util.List<dto.UsersServiceSpec> list = new java.util.ArrayList<>();
			for (int r = 0; r < rows; r++) {
				String role = (String) usersTableModel.getValueAt(r, 0);
				String username = (String) usersTableModel.getValueAt(r, 1);
				String password = (String) usersTableModel.getValueAt(r, 2);
				System.out.println("Row " + r +
						" role=" + usersTableModel.getValueAt(r, 0) +
						" user=" + usersTableModel.getValueAt(r, 1) +
						" pass=" + usersTableModel.getValueAt(r, 2));
				if ((role != null && !role.isBlank()) ||
						(username != null && !username.isBlank()) ||
						(password != null && !password.isBlank())) {
					list.add(new dto.UsersServiceSpec(
							role != null ? role.trim() : "",
							username != null ? username.trim() : "",
							password != null ? password.trim() : ""
					));
				}
			}

			Path specFile = configService.getUsersFile(config);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			String json = gson.toJson(list.toArray(new dto.UsersServiceSpec[0]));
			Files.writeString(specFile, json, StandardCharsets.UTF_8);

			JOptionPane.showMessageDialog(
					dialog,
					"Users specs saved to:\n" + specFile.toAbsolutePath(),
					"Saved",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to save users", ex
			);
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					dialog,
					"Failed to save users.json: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private UsersServiceSpec[] getAllUsers() {
		try {
			Path specFile = configService.getUsersFile(config);
			if (!Files.exists(specFile)) {
				return null;
			}
			String json = Files.readString(specFile);
			Gson gson = new GsonBuilder().create();
			return gson.fromJson(json, dto.UsersServiceSpec[].class);
		} catch (Exception ex) {
			TestRecorderErrorLogger.logError(
					"Failed to load users", ex
			);
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					null,
					"Failed to load users.json: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
			return new UsersServiceSpec[]{};
		}
	}

	public UsersServiceSpec getUser(String role) {
		if (role == null) {
			throw new IllegalArgumentException("data must not be null");
		}
		String key = role.trim();
		if (key.isEmpty()) {
			throw new IllegalArgumentException("data must not be blank");
		}

		UsersServiceSpec[] users = getAllUsers();
		if (isEmail(role)) {
			for (UsersServiceSpec u : users) {
				if (u != null && key.equals(u.username)) {
					return u;
				}
			}
		} else {
			for (UsersServiceSpec u : users) {
				if (u != null && key.equals(u.role)) {
					return u;
				}
			}
		}
		throw new IllegalStateException("User with role '" + key + "' not found");
	}
}