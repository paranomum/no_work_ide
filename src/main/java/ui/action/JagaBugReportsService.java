package ui.action;

import api.jaga.api.*;
import api.jaga.dto.*;
import dto.AppConfig;
import dto.JagaUserSettings;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import api.ApiClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ui.ChipItem;
import ui.MultiSelectChipsField;
import util.SimpleSecretService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class JagaBugReportsService {

	private static final Logger log = LoggerFactory.getLogger(JagaBugReportsService.class);
	private static final String PASSWORD_MASK = "************";

	private final DefaultTableModel tableModel;
	private final ConfigService configService;
	private final AppConfig config;
	private JagaUserSettings jagaUserSettings;

	public JagaBugReportsService(DefaultTableModel tableModel, ConfigService configService, AppConfig config) {
		this.tableModel = tableModel;
		this.configService = configService;
		this.config = config;
		this.jagaUserSettings = configService.loadJagaUserSettings(config);
	}

	public JPanel createJagaSettingsPanel(JDialog parentDialog) {
		reloadJagaSettings();

		JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(6, 6, 6, 6);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;

		JLabel emailLabel = new JLabel("Email");
		JTextField emailField = new JTextField(safe(jagaUserSettings.getEmail()), 30);

		JLabel passwordLabel = new JLabel("Пароль");
		JPasswordField passwordField = new JPasswordField(30);
		boolean hasSavedPassword = jagaUserSettings.getEncryptedPassword() != null
				&& !jagaUserSettings.getEncryptedPassword().isBlank();
		if (hasSavedPassword) {
			passwordField.setText(PASSWORD_MASK);
		}

		JLabel passwordHint = new JLabel("Оставьте как есть, чтобы не менять пароль");
		passwordHint.setForeground(Color.GRAY);

		JButton checkButton = new JButton("Проверить");
		JLabel checkStatusLabel = new JLabel(" ");
		checkStatusLabel.setForeground(Color.GRAY);
		checkButton.addActionListener(e -> {
			String email = emailField.getText() == null ? "" : emailField.getText().trim();
			String password = new String(passwordField.getPassword()).trim();

			if (email.isBlank()) {
				checkStatusLabel.setText("Заполните Email");
				checkStatusLabel.setForeground(Color.RED);
				return;
			}

			if (password.isBlank()) {
				checkStatusLabel.setText("Заполните пароль");
				checkStatusLabel.setForeground(Color.RED);
				return;
			}

			checkButton.setEnabled(false);
			checkStatusLabel.setText("Проверяем...");
			checkStatusLabel.setForeground(Color.GRAY);

			final String passwordToCheck;

			if (PASSWORD_MASK.equals(password)) {
				try {
					JagaUserSettings latest = configService.loadJagaUserSettings(config);
					if (latest.getEncryptedPassword() == null || latest.getEncryptedPassword().isBlank()) {
						checkStatusLabel.setText("Пароль не найден");
						checkStatusLabel.setForeground(Color.RED);
						startCheckButtonCooldown(checkButton);
						return;
					}
					passwordToCheck = SimpleSecretService.decrypt(latest.getEncryptedPassword());
				} catch (Exception ex) {
					log.error("Не удалось расшифровать сохраненный пароль Jaga для пользователя {}", email, ex);
					checkStatusLabel.setText("Ошибка проверки");
					checkStatusLabel.setForeground(Color.RED);
					startCheckButtonCooldown(checkButton);
					return;
				}
			} else {
				passwordToCheck = password;
			}

			new SwingWorker<String, Void>() {
				@Override
				protected String doInBackground() {
					return getJagaToken(email, passwordToCheck);
				}

				@Override
				protected void done() {
					try {
						String token = get();

						if (token != null && !token.isBlank()) {
							checkStatusLabel.setText("200 OK");
							checkStatusLabel.setForeground(new Color(0, 128, 0));
						} else {
							checkStatusLabel.setText("Ошибка проверки");
							checkStatusLabel.setForeground(Color.RED);
							log.error("Jaga login вернул пустой accessToken для пользователя {}", email);
						}
					} catch (Exception ex) {
						checkStatusLabel.setText("Ошибка проверки");
						checkStatusLabel.setForeground(Color.RED);
						log.error("Ошибка авторизации в Jaga для пользователя {}", email, ex);
					} finally {
						startCheckButtonCooldown(checkButton);
					}
				}
			}.execute();
		});

		JPanel checkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		checkPanel.add(checkButton);
		checkPanel.add(checkStatusLabel);

		JLabel projectIdLabel = new JLabel("ID проекта");
		JTextField projectIdField = new JTextField(
				jagaUserSettings.getProjectId() == null ? "" : String.valueOf(jagaUserSettings.getProjectId()), 12
		);
		applyDigitsOnly(projectIdField);

		JButton loadTaskTypesButton = new JButton("Получить типы задач");
		JLabel taskTypesStatusLabel = new JLabel(" ");
		taskTypesStatusLabel.setForeground(Color.GRAY);

		JPanel projectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		projectPanel.add(projectIdField);
		projectPanel.add(loadTaskTypesButton);

		java.util.List<ChipItem> taskTypeOptions = new java.util.ArrayList<>();

		if (jagaUserSettings.getTaskTypes() != null && !jagaUserSettings.getTaskTypes().isEmpty()) {
			jagaUserSettings.getTaskTypes().forEach((id, label) ->
					taskTypeOptions.add(new ChipItem(id, label))
			);
		}

		JLabel taskIdsLabel = new JLabel("Типы задач");
		MultiSelectChipsField taskIdsField = new MultiSelectChipsField(taskTypeOptions);
		taskIdsField.setSelectedIds(
				jagaUserSettings.getTaskTypes() == null
						? java.util.List.of()
						: new java.util.ArrayList<>(jagaUserSettings.getTaskTypes().keySet())
		);

		boolean hasProjectId = !projectIdField.getText().trim().isBlank();
		setTaskTypesFieldEnabled(taskIdsField, hasProjectId && jagaUserSettings.getTaskTypes() != null && !jagaUserSettings.getTaskTypes().isEmpty());
		loadTaskTypesButton.setEnabled(hasProjectId);
		loadTaskTypesButton.addActionListener(e -> {
			String projectIdText = projectIdField.getText().trim();

			if (projectIdText.isBlank()) {
				taskTypesStatusLabel.setText("Введите ID проекта");
				taskTypesStatusLabel.setForeground(Color.RED);
				setTaskTypesFieldEnabled(taskIdsField, false);
				return;
			}

			Long projectId;
			try {
				projectId = parseLong(projectIdText);
			} catch (Exception ex) {
				taskTypesStatusLabel.setText("Некорректный ID проекта");
				taskTypesStatusLabel.setForeground(Color.RED);
				setTaskTypesFieldEnabled(taskIdsField, false);
				return;
			}

			String username = safe(emailField.getText());
			if (username.isBlank()) {
				taskTypesStatusLabel.setText("Заполните Email");
				taskTypesStatusLabel.setForeground(Color.RED);
				return;
			}

			String enteredPassword = new String(passwordField.getPassword()).trim();
			final String passwordToUse;

			try {
				if (enteredPassword.isBlank()) {
					taskTypesStatusLabel.setText("Заполните пароль");
					taskTypesStatusLabel.setForeground(Color.RED);
					return;
				}

				if (PASSWORD_MASK.equals(enteredPassword)) {
					JagaUserSettings latest = configService.loadJagaUserSettings(config);
					if (latest.getEncryptedPassword() == null || latest.getEncryptedPassword().isBlank()) {
						taskTypesStatusLabel.setText("Пароль не найден");
						taskTypesStatusLabel.setForeground(Color.RED);
						return;
					}
					passwordToUse = SimpleSecretService.decrypt(latest.getEncryptedPassword());
				} else {
					passwordToUse = enteredPassword;
				}
			} catch (Exception ex) {
				log.error("Не удалось подготовить пароль для загрузки типов задач", ex);
				taskTypesStatusLabel.setText("Ошибка подготовки пароля");
				taskTypesStatusLabel.setForeground(Color.RED);
				return;
			}

			loadTaskTypesButton.setEnabled(false);

			taskIdsField.setSelectedIds(List.of());
			taskIdsField.setAvailableItems(List.of());
			setTaskTypesFieldEnabled(taskIdsField, false);

			taskTypesStatusLabel.setText("Собираю типы...");
			taskTypesStatusLabel.setForeground(Color.GRAY);

			new SwingWorker<LinkedHashMap<String, Long>, Void>() {
				@Override
				protected LinkedHashMap<String, Long> doInBackground() throws Exception {
					return loadTaskLabelToId(projectId, username, passwordToUse);
				}

				@Override
				protected void done() {
					try {
						LinkedHashMap<String, Long> taskLabelToId = get();

						List<ChipItem> loadedItems = toChipItems(taskLabelToId);

						taskIdsField.setAvailableItems(loadedItems);
						taskIdsField.setSelectedIds(List.of());
						setTaskTypesFieldEnabled(taskIdsField, !loadedItems.isEmpty());

						if (loadedItems.isEmpty()) {
							taskTypesStatusLabel.setText("Типы задач не найдены");
							taskTypesStatusLabel.setForeground(Color.RED);
							return;
						}

						taskTypesStatusLabel.setText("Типы задач собраны");
						taskTypesStatusLabel.setForeground(new Color(0, 128, 0));
					} catch (Exception ex) {
						setTaskTypesFieldEnabled(taskIdsField, false);

						Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

						if (cause instanceof WebClientResponseException webEx) {
							String responseBody = webEx.getResponseBodyAsString();
							log.error("Ошибка получения типов задач. projectId={}, status={}, body={}",
									projectId, webEx.getStatusCode(), responseBody, webEx);
						} else {
							log.error("Ошибка получения типов задач для projectId={}", projectId, cause);
						}

						taskTypesStatusLabel.setText("Произошла ошибка");
						taskTypesStatusLabel.setForeground(Color.RED);
					} finally {
						loadTaskTypesButton.setEnabled(true);
					}
				}
			}.execute();
		});

		if (!hasProjectId) {
			taskTypesStatusLabel.setText("Введите ID проекта");
			taskTypesStatusLabel.setForeground(Color.GRAY);
		} else if (jagaUserSettings.getTaskTypes() != null && !jagaUserSettings.getTaskTypes().isEmpty()) {
			taskTypesStatusLabel.setText("Типы задач уже загружены");
			taskTypesStatusLabel.setForeground(new Color(0, 128, 0));
		} else {
			taskTypesStatusLabel.setText("Нажмите \"Получить типы задач\"");
			taskTypesStatusLabel.setForeground(Color.GRAY);
		}

		projectIdField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				onProjectChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				onProjectChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				onProjectChanged();
			}

			private void onProjectChanged() {
				String projectIdText = projectIdField.getText().trim();
				boolean filled = !projectIdText.isBlank();

				loadTaskTypesButton.setEnabled(filled);

				taskIdsField.setSelectedIds(List.of());
				taskIdsField.setAvailableItems(List.of());
				setTaskTypesFieldEnabled(taskIdsField, false);

				if (!filled) {
					taskTypesStatusLabel.setText("Введите ID проекта");
					taskTypesStatusLabel.setForeground(Color.GRAY);
				} else {
					taskTypesStatusLabel.setText("Нажмите \"Получить типы задач\"");
					taskTypesStatusLabel.setForeground(Color.GRAY);
				}
			}
		});

		JButton saveButton = new JButton("Сохранить");
		saveButton.addActionListener(e -> {
			try {
				JagaUserSettings latest = configService.loadJagaUserSettings(config);

				latest.setEmail(emailField.getText().trim());
				latest.setProjectId(parseLong(projectIdField.getText().trim()));
				java.util.LinkedHashMap<Long, String> selectedTaskTypes = new java.util.LinkedHashMap<>();
				if (taskIdsField.isEnabled()) {
					for (ChipItem item : taskIdsField.getSelectedItems()) {
						selectedTaskTypes.put(item.getId(), item.getLabel());
					}
				}
				latest.setTaskTypes(selectedTaskTypes);

				String enteredPassword = new String(passwordField.getPassword()).trim();

				if (!enteredPassword.isBlank() && !PASSWORD_MASK.equals(enteredPassword)) {
					latest.setPassword(enteredPassword);
				} else {
					latest.setPassword("");
				}

				configService.saveJagaUserSettings(config, latest);
				this.jagaUserSettings = configService.loadJagaUserSettings(config);

				if (this.jagaUserSettings.getEncryptedPassword() != null
						&& !this.jagaUserSettings.getEncryptedPassword().isBlank()) {
					passwordField.setText(PASSWORD_MASK);
				} else {
					passwordField.setText("");
				}

				checkStatusLabel.setText(" ");
				checkStatusLabel.setForeground(Color.GRAY);

				JOptionPane.showMessageDialog(parentDialog, "Настройки Яги сохранены");
			} catch (IOException ex) {
				log.error("Ошибка сохранения настроек Яги", ex);
				JOptionPane.showMessageDialog(
						parentDialog,
						"Не удалось сохранить настройки Яги: " + ex.getMessage(),
						"Ошибка",
						JOptionPane.ERROR_MESSAGE
				);
			}
		});

		int y = 0;

		gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0;
		form.add(emailLabel, gbc);
		gbc.gridx = 1; gbc.gridy = y++; gbc.weightx = 1;
		form.add(emailField, gbc);

		gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0;
		form.add(passwordLabel, gbc);
		gbc.gridx = 1; gbc.gridy = y++; gbc.weightx = 1;
		form.add(passwordField, gbc);

		gbc.gridx = 1; gbc.gridy = y++;
		form.add(passwordHint, gbc);

		gbc.gridx = 1; gbc.gridy = y++;
		form.add(checkPanel, gbc);

		gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0;
		form.add(projectIdLabel, gbc);
		gbc.gridx = 1; gbc.gridy = y++; gbc.weightx = 1;
		form.add(projectPanel, gbc);
		gbc.gridx = 1; gbc.gridy = y++; gbc.weightx = 1;
		form.add(taskTypesStatusLabel, gbc);

		gbc.gridx = 0;
		gbc.gridy = y;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		form.add(taskIdsLabel, gbc);

		gbc.gridx = 1;
		gbc.gridy = y++;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.fill = GridBagConstraints.BOTH;
		form.add(taskIdsField, gbc);

		gbc.weighty = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bottom.add(saveButton);

		root.add(form, BorderLayout.CENTER);
		root.add(bottom, BorderLayout.SOUTH);

		return root;
	}

	private void reloadJagaSettings() {
		this.jagaUserSettings = configService.loadJagaUserSettings(config);
	}

	private void applyDigitsOnly(JTextField field) {
		((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
			@Override
			public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
				if (string != null && string.chars().allMatch(Character::isDigit)) {
					super.insertString(fb, offset, string, attr);
				}
			}

			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
				if (text == null || text.isEmpty() || text.chars().allMatch(Character::isDigit)) {
					super.replace(fb, offset, length, text, attrs);
				}
			}
		});
	}

	private Long parseLong(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return Long.parseLong(value);
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}

	public void createBugReport() {
		String reportText = buildBugReportText();
		showBugReportDialog(reportText);
	}

	private String buildBugReportText() {
		StringBuilder sb = new StringBuilder();
		int rowCount = tableModel.getRowCount();

		log.debug("createBugReport: rowCount={}", rowCount);

		int stepNumber = 1;
		for (int row = 0; row < rowCount; row++) {
			String interpreted = interpretRow(row);

			if (interpreted == null || interpreted.isBlank()) {
				log.trace("Row {} skipped in bug report", row + 1);
				continue;
			}

			sb.append(stepNumber++)
					.append(". ")
					.append(interpreted.trim())
					.append(System.lineSeparator());
		}

		return sb.toString().trim();
	}

	private String interpretRow(int row) {
		String action = extractAction(row);
		if (action == null || action.isBlank()) {
			return null;
		}

		String elementType = val(row, 5);
		String name = val(row, 7);
		String value = val(row, 3);
		String comment = val(row, 4);

		if (action.contains("switchTab")) return null;
		if (action.contains("waitLoadingPage")) return null;
		if (action.contains("refreshPage")) return null;
		if (action.equals("assertExists")) return null;
		if (action.equals("assertNotExists")) return null;
		if (action.equals("auth")) return null;
		if (action.contains("pause")) return null;

		if (action.contains("open")) {
			return text("Открыть \"%s\"", value);
		}

		if (action.contains("fillData")) {
			return "Заполнить обязательные данные";
		}

		if (action.contains("customMethod")) {
			return safe(value);
		}

		if (action.contains("useBackendMethod")) {
			return text("Выполнить запрос \"%s\"", value);
		}

		if (action.contains("specialAction")) {
			return safe(comment);
		}

		if (action.contains("fillDate") && "DatePicker".equals(elementType)) {
			return text("Заполнить \"%s\"", name);
		}

		if (action.contains("clear") && isField(elementType)) {
			return text("Очистить поле \"%s\"", name);
		}

		if (isSelectAction(action) && isSelectable(elementType)) {
			return textWithExample("Выбрать \"%s\"", name, value);
		}

		if (action.contains("click")) {
			if ("CheckBoxButton".equals(elementType)) {
				return text("Нажать чекбокс \"%s\"", name);
			}
			if (isButton(elementType)) {
				return text("Нажать кнопку \"%s\"", name);
			}
		}

		if (action.contains("fill") && isField(elementType)) {
			return text("Заполнить поле \"%s\"", name);
		}

		log.debug("Row {} not interpreted: action={}, elementType={}", row + 1, action, elementType);
		return null;
	}

	private void showBugReportDialog(String text) {
		JTextArea textArea = new JTextArea(text, 25, 80);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setEditable(true);
		textArea.setCaretPosition(0);

		JScrollPane scrollPane = new JScrollPane(textArea);

		JButton copyButton = new JButton("Скопировать");
		copyButton.addActionListener(e -> {
			textArea.selectAll();
			textArea.copy();
			textArea.select(0, 0);
		});

		JButton closeButton = new JButton("Закрыть");
		closeButton.addActionListener(e -> {
			Window window = SwingUtilities.getWindowAncestor(closeButton);
			if (window != null) {
				window.dispose();
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(copyButton);
		buttons.add(closeButton);

		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		root.add(scrollPane, BorderLayout.CENTER);
		root.add(buttons, BorderLayout.SOUTH);

		JDialog dialog = new JDialog((Frame) null, "Черновик баг-репорта", true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setContentPane(root);
		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
	}

	private boolean isField(String elementType) {
		return "Field".equals(elementType) || "RichField".equals(elementType);
	}

	private boolean isButton(String elementType) {
		return "Button".equals(elementType)
				|| "TabButton".equals(elementType)
				|| "LinkButton".equals(elementType)
				|| "CheckBoxButton".equals(elementType)
				|| "RadioButton".equals(elementType);
	}

	private boolean isSelectable(String elementType) {
		return "Select".equals(elementType) || "Dropdown".equals(elementType);
	}

	private boolean isSelectAction(String action) {
		return action.contains("selectOption")
				|| action.contains("selectExactOption")
				|| action.contains("selectOptions");
	}

	private String text(String pattern, String value) {
		String safeValue = safe(value);
		return safeValue.isBlank() ? "" : pattern.formatted(safeValue);
	}

	private String val(int row, int col) {
		return Objects.toString(tableModel.getValueAt(row, col), "").trim();
	}

	private String extractAction(int row) {
		Object actionObj = tableModel.getValueAt(row, 1);
		return actionObj == null ? "" : actionObj.toString().trim();
	}

	private String textWithExample(String pattern, String mainValue, String exampleValue) {
		String base = text(pattern, mainValue);
		String safeExample = safe(exampleValue);

		if (base.isBlank()) {
			return "";
		}

		if (safeExample.isBlank()) {
			return base;
		}

		return base + " (например: \"" + safeExample + "\")";
	}

	private ApiClient getApiClient(String fullUrl) {
		ApiClient apiClient = new ApiClient();
		apiClient.setBasePath(fullUrl);
		return apiClient;
	}

	private ApiClient getApiClient(String fullUrl, String username, String password) {
		ApiClient apiClient = new ApiClient();
		String token = getJagaToken(username, password);
		apiClient.setBearerToken(token);
		apiClient.setBasePath(fullUrl);
		return apiClient;
	}

	private String getJagaToken(String username, String password) {
		val jagaApiClient = new JagaControllerApi(getApiClient("https://stage.jaga.rt.ru"));
		val response = jagaApiClient.login(new JagaLoginRequest().mail(username).password(password)).block();
		return response == null ? null : response.getAccessToken();
	}

	private void startCheckButtonCooldown(JButton checkButton) {
		Timer timer = new Timer(2000, event -> checkButton.setEnabled(true));
		timer.setRepeats(false);
		timer.start();
	}

	private void setTaskTypesFieldEnabled(JComponent component, boolean enabled) {
		component.setEnabled(enabled);

		for (Component child : component.getComponents()) {
			child.setEnabled(enabled);

			if (child instanceof JComponent jChild) {
				setTaskTypesFieldEnabled(jChild, enabled);
			}
		}
	}

	private LinkedHashMap<String, Long> buildTaskLabelToIdMap(List<JagaTaskTypeResponse> taskTypes) {
		LinkedHashMap<String, Long> taskLabelToId = new LinkedHashMap<>();

		if (taskTypes == null) {
			return taskLabelToId;
		}

		for (JagaTaskTypeResponse taskType : taskTypes) {
			if (taskType == null || taskType.getId() == null) {
				continue;
			}

			String label = safe(taskType.getTypeName());
			if (label.isBlank()) {
				continue;
			}

			taskLabelToId.putIfAbsent(label, taskType.getId());
		}

		return taskLabelToId;
	}

	private List<ChipItem> toChipItems(Map<String, Long> taskLabelToId) {
		List<ChipItem> result = new ArrayList<>();

		if (taskLabelToId == null || taskLabelToId.isEmpty()) {
			return result;
		}

		for (Map.Entry<String, Long> entry : taskLabelToId.entrySet()) {
			result.add(new ChipItem(entry.getValue(), entry.getKey()));
		}

		return result;
	}

	private LinkedHashMap<String, Long> loadTaskLabelToId(Long projectId, String username, String password) throws Exception {
		if (projectId == null) {
			throw new IllegalArgumentException("ProjectId is required");
		}

		username = safe(username);
		if (username.isBlank()) {
			throw new IllegalStateException("Email не заполнен");
		}

		if (password == null || password.isBlank()) {
			throw new IllegalStateException("Пароль не найден");
		}

		List<JagaTaskTypeResponse> response = new JagaControllerApi(
				getApiClient("https://jaga.rt.ru", username, password)
		).getProjectTaskTypes(projectId, true).block();

		return buildTaskLabelToIdMap(response);
	}
}