package ui.action;

import api.jaga.api.*;
import api.jaga.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.AppConfig;
import dto.JagaUserSettings;
import lombok.SneakyThrows;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import api.ApiClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ui.ChipItem;
import ui.MultiSelectChipsField;
import ui.TaskSearchComboBox;
import util.SimpleSecretService;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.TrustManagerFactory;
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

	private List<JagaTaskAttributeResponse> allTaskTypeFields = new ArrayList<>();

	private JagaTaskTypeDetailsResponse loadedTaskTypeResponse;
	private List<JagaTaskAttributeResponse> requiredTaskTypeFields = new ArrayList<>();
	private final Map<Long, JComponent> fieldComponents = new LinkedHashMap<>();
	private final Map<Long, LinkedHashMap<String, Long>> fieldDictionaryValues = new LinkedHashMap<>();

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

		JButton loadCertsButton = new JButton("Загрузить серты");
		loadCertsButton.addActionListener(e -> importJagaCertificates(parentDialog));

		JPanel checkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		checkPanel.add(checkButton);
		checkPanel.add(loadCertsButton);
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
		reloadJagaSettings();
		showJagaCreateBugDialog();
//		String reportText = buildBugReportText();
//		showBugReportDialog(reportText);
	}

	private void showJagaCreateBugDialog() {
		String reportText = buildJagaDescriptionTemplate();

		JDialog dialog = new JDialog((Frame) null, "Создание бага в Jaga", true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel topPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(6, 6, 6, 6);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;

		JLabel taskTypeLabel = new JLabel("Тип задачи");
		JComboBox<TaskTypeOption> taskTypeComboBox = new JComboBox<>();
		taskTypeComboBox.setEnabled(false);

		JLabel statusLabel = new JLabel(" ");
		statusLabel.setForeground(Color.GRAY);

		JPanel fieldsContainer = new JPanel(new GridBagLayout());
		JScrollPane fieldsScrollPane = new JScrollPane(fieldsContainer);
		fieldsScrollPane.setPreferredSize(new Dimension(800, 500));

		JButton closeButton = new JButton("Закрыть");
		closeButton.addActionListener(e -> dialog.dispose());
		JButton createButton = new JButton("Создать");
		createButton.setEnabled(false);
		createButton.addActionListener(e -> {
			TaskTypeOption selected = (TaskTypeOption) taskTypeComboBox.getSelectedItem();
			if (selected == null || selected.getId() == null) {
				JOptionPane.showMessageDialog(dialog, "Выберите тип задачи", "Ошибка", JOptionPane.ERROR_MESSAGE);
				return;
			}

			createButton.setEnabled(false);

			new SwingWorker<JagaTaskResponse, Void>() {
				@Override
				protected JagaTaskResponse doInBackground() throws Exception {
					return createTask(selected);
				}

				@Override
				protected void done() {
					try {
						JagaTaskResponse response = get();
						String message = buildCreatedTaskMessage(response);
						dialog.dispose();
						showCreatedTaskDialog(null, message);
					} catch (Exception ex) {
						Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
						log.error("Ошибка создания задачи Jaga", cause);
						JOptionPane.showMessageDialog(
								dialog,
								"Не удалось создать задачу: " + cause.getMessage(),
								"Ошибка",
								JOptionPane.ERROR_MESSAGE
						);
					} finally {
						createButton.setEnabled(true);
					}
				}
			}.execute();
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(closeButton);
		buttons.add(createButton);

		int y = 0;

		gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0;
		topPanel.add(taskTypeLabel, gbc);
		gbc.gridx = 1; gbc.gridy = y++; gbc.weightx = 1;
		topPanel.add(taskTypeComboBox, gbc);

		gbc.gridx = 1; gbc.gridy = y++;
		topPanel.add(statusLabel, gbc);

		root.add(topPanel, BorderLayout.NORTH);
		root.add(fieldsScrollPane, BorderLayout.CENTER);
		root.add(buttons, BorderLayout.SOUTH);

		dialog.setContentPane(root);

		taskTypeComboBox.addActionListener(e -> {
			if (!taskTypeComboBox.isEnabled()) {
				return;
			}

			TaskTypeOption selected = (TaskTypeOption) taskTypeComboBox.getSelectedItem();
			if (selected == null || selected.getId() == null) {
				clearDynamicFields(fieldsContainer);
				statusLabel.setText("Выберите тип задачи");
				statusLabel.setForeground(Color.GRAY);
				return;
			}

			loadTaskTypeFieldsAsync(selected, fieldsContainer, statusLabel, reportText, createButton);
		});

		initTaskTypeSelection(taskTypeComboBox, fieldsContainer, statusLabel, reportText, createButton);

		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
	}

	private List<JagaTaskAttributeResponse> extractAllFields(JagaTaskTypeDetailsResponse response) {
		if (response == null || response.getGroups() == null || response.getGroups().isEmpty()) {
			return List.of();
		}

		return response.getGroups().stream()
				.filter(Objects::nonNull)
				.filter(group -> !Boolean.TRUE.equals(group.getDeleted()))
				.flatMap(group -> group.getAttributes() == null
						? java.util.stream.Stream.empty()
						: group.getAttributes().stream())
				.filter(Objects::nonNull)
				.filter(field -> !Boolean.TRUE.equals(field.getDeleted()))
				.filter(field -> Boolean.TRUE.equals(field.getVisible()))
				.toList();
	}

	private void initTaskTypeSelection(
			JComboBox<TaskTypeOption> taskTypeComboBox,
			JPanel fieldsContainer,
			JLabel statusLabel,
			String reportText,
			JButton createButton
	) {
		clearTaskTypeState();
		clearDynamicFields(fieldsContainer);
		createButton.setEnabled(false);

		Map<Long, String> configuredTaskTypes = jagaUserSettings.getTaskTypes();

		if (configuredTaskTypes != null && !configuredTaskTypes.isEmpty()) {
			List<TaskTypeOption> options = configuredTaskTypes.entrySet().stream()
					.filter(entry -> entry.getKey() != null)
					.map(entry -> new TaskTypeOption(entry.getKey(), safe(entry.getValue())))
					.filter(option -> !option.getLabel().isBlank())
					.toList();

			fillTaskTypeCombo(taskTypeComboBox, options);

			if (options.isEmpty()) {
				statusLabel.setText("В настройках нет доступных типов задач");
				statusLabel.setForeground(Color.RED);
				taskTypeComboBox.setEnabled(false);
				return;
			}

			if (options.size() == 1) {
				taskTypeComboBox.setSelectedIndex(0);
				taskTypeComboBox.setEnabled(false);
				statusLabel.setText("Тип задачи выбран автоматически");
				statusLabel.setForeground(new Color(0, 128, 0));
				loadTaskTypeFieldsAsync(options.get(0), fieldsContainer, statusLabel, reportText, createButton);
			} else {
				taskTypeComboBox.setEnabled(true);
				statusLabel.setText("Выберите тип задачи");
				statusLabel.setForeground(Color.GRAY);
			}

			return;
		}

		Long projectId = jagaUserSettings.getProjectId();
		if (projectId == null) {
			statusLabel.setText("Укажите projectId в настройках перед использованием интеграции");
			statusLabel.setForeground(Color.RED);
			taskTypeComboBox.setEnabled(false);
			return;
		}

		String username = safe(jagaUserSettings.getEmail());
		String password;
		try {
			password = resolveJagaPassword();
		} catch (Exception ex) {
			log.error("Не удалось получить пароль Jaga", ex);
			statusLabel.setText("Не удалось получить пароль из настроек");
			statusLabel.setForeground(Color.RED);
			taskTypeComboBox.setEnabled(false);
			return;
		}

		if (username.isBlank()) {
			statusLabel.setText("Укажите email в настройках");
			statusLabel.setForeground(Color.RED);
			taskTypeComboBox.setEnabled(false);
			return;
		}

		if (password == null || password.isBlank()) {
			statusLabel.setText("Укажите пароль в настройках");
			statusLabel.setForeground(Color.RED);
			taskTypeComboBox.setEnabled(false);
			return;
		}

		statusLabel.setText("Загружаю типы задач...");
		statusLabel.setForeground(Color.GRAY);
		taskTypeComboBox.setEnabled(false);

		new SwingWorker<LinkedHashMap<String, Long>, Void>() {
			@Override
			protected LinkedHashMap<String, Long> doInBackground() throws Exception {
				return loadTaskLabelToId(projectId, username, password);
			}

			@Override
			protected void done() {
				try {
					LinkedHashMap<String, Long> taskLabelToId = get();

					List<TaskTypeOption> options = taskLabelToId.entrySet().stream()
							.filter(entry -> entry.getValue() != null)
							.map(entry -> new TaskTypeOption(entry.getValue(), safe(entry.getKey())))
							.filter(option -> !option.getLabel().isBlank())
							.toList();

					fillTaskTypeCombo(taskTypeComboBox, options);

					if (options.isEmpty()) {
						statusLabel.setText("Типы задач не найдены");
						statusLabel.setForeground(Color.RED);
						taskTypeComboBox.setEnabled(false);
						return;
					}

					if (options.size() == 1) {
						taskTypeComboBox.setSelectedIndex(0);
						taskTypeComboBox.setEnabled(false);
						statusLabel.setText("Тип задачи выбран автоматически");
						statusLabel.setForeground(new Color(0, 128, 0));
						loadTaskTypeFieldsAsync(options.get(0), fieldsContainer, statusLabel, reportText, createButton);
					} else {
						taskTypeComboBox.setEnabled(true);
						statusLabel.setText("Выберите тип задачи");
						statusLabel.setForeground(Color.GRAY);
					}
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					log.error("Ошибка загрузки типов задач Jaga", cause);
					statusLabel.setText("Не удалось загрузить типы задач");
					statusLabel.setForeground(Color.RED);
					taskTypeComboBox.setEnabled(false);
				}
			}
		}.execute();
	}

	private void loadTaskTypeFieldsAsync(
			TaskTypeOption selectedTaskType,
			JPanel fieldsContainer,
			JLabel statusLabel,
			String reportText,
			JButton createButton
	) {
		clearTaskTypeState();
		clearDynamicFields(fieldsContainer);
		createButton.setEnabled(false);

		Long projectId = jagaUserSettings.getProjectId();
		if (projectId == null) {
			statusLabel.setText("Укажите projectId в настройках");
			statusLabel.setForeground(Color.RED);
			return;
		}

		String username = safe(jagaUserSettings.getEmail());
		final String password;
		try {
			password = resolveJagaPassword();
		} catch (Exception ex) {
			log.error("Не удалось получить пароль Jaga", ex);
			statusLabel.setText("Не удалось получить пароль");
			statusLabel.setForeground(Color.RED);
			return;
		}

		if (username.isBlank() || password == null || password.isBlank()) {
			statusLabel.setText("Не заполнены учетные данные Jaga");
			statusLabel.setForeground(Color.RED);
			return;
		}

		statusLabel.setText("Загружаю поля...");
		statusLabel.setForeground(Color.GRAY);

		new SwingWorker<JagaTaskTypeDetailsResponse, Void>() {
			@Override
			protected JagaTaskTypeDetailsResponse doInBackground() {
				return new JagaControllerApi(
						getApiClient("https://jaga.rt.ru", username, password)
				).getProjectTaskType(projectId, selectedTaskType.getId()).block();
			}

			@Override
			protected void done() {
				try {
					JagaTaskTypeDetailsResponse response = get();

					loadedTaskTypeResponse = response;
					allTaskTypeFields = extractAllFields(response);
					requiredTaskTypeFields = extractRequiredFields(response);

					statusLabel.setText("Поля загружены: " + requiredTaskTypeFields.size());
					statusLabel.setForeground(new Color(0, 128, 0));

					renderRequiredFields(fieldsContainer, requiredTaskTypeFields, reportText, statusLabel);
					createButton.setEnabled(true);
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					log.error("Ошибка загрузки полей типа задачи {}", selectedTaskType.getId(), cause);

					clearTaskTypeState();
					clearDynamicFields(fieldsContainer);

					statusLabel.setText("Не удалось загрузить поля");
					statusLabel.setForeground(Color.RED);
					createButton.setEnabled(false);
				}
			}
		}.execute();
	}

	private List<JagaTaskAttributeResponse> extractRequiredFields(JagaTaskTypeDetailsResponse response) {
		if (response == null || response.getGroups() == null || response.getGroups().isEmpty()) {
			return List.of();
		}

		return response.getGroups().stream()
				.filter(Objects::nonNull)
				.filter(group -> !Boolean.TRUE.equals(group.getDeleted()))
				.flatMap(group -> group.getAttributes() == null
						? java.util.stream.Stream.empty()
						: group.getAttributes().stream())
				.filter(Objects::nonNull)
				.filter(field -> !Boolean.TRUE.equals(field.getDeleted()))
				.filter(field -> !shouldSkipField(field))
				.filter(field ->
						Boolean.TRUE.equals(field.getRequired())
								|| "task.parent_id".equals(safe(field.getObjectTypeNameM()))
				)
				.sorted(java.util.Comparator.comparing(
						JagaTaskAttributeResponse::getOrderNum,
						java.util.Comparator.nullsLast(Integer::compareTo)
				))
				.toList();
	}

	private boolean shouldSkipField(JagaTaskAttributeResponse field) {
		String objectTypeNameM = safe(field.getObjectTypeNameM());
		return "task.type_id".equals(objectTypeNameM)
				|| "task.project_id".equals(objectTypeNameM);
	}

	private void renderRequiredFields(
			JPanel fieldsContainer,
			List<JagaTaskAttributeResponse> fields,
			String reportText,
			JLabel statusLabel
	) {
		fieldsContainer.removeAll();
		fieldComponents.clear();
		fieldDictionaryValues.clear();

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(6, 6, 6, 6);
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		int y = 0;

		if (fields == null || fields.isEmpty()) {
			gbc.gridx = 0;
			gbc.gridy = y;
			gbc.weightx = 1.0;
			gbc.weighty = 1.0;
			fieldsContainer.add(new JLabel("Обязательные поля не найдены"), gbc);
			fieldsContainer.revalidate();
			fieldsContainer.repaint();
			return;
		}

		for (JagaTaskAttributeResponse field : fields) {
			JLabel label = new JLabel(resolveFieldLabel(field));
			JComponent component = buildFieldComponent(field, reportText, statusLabel);

			gbc.gridx = 0;
			gbc.gridy = y;
			gbc.weightx = 0;
			gbc.weighty = 0;
			fieldsContainer.add(label, gbc);

			gbc.gridx = 1;
			gbc.gridy = y++;
			gbc.weightx = 1.0;
			gbc.fill = GridBagConstraints.HORIZONTAL;

			if (component instanceof JScrollPane) {
				gbc.fill = GridBagConstraints.BOTH;
			}

			fieldsContainer.add(component, gbc);
		}

		gbc.gridx = 0;
		gbc.gridy = y;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.BOTH;
		fieldsContainer.add(Box.createVerticalGlue(), gbc);

		fieldsContainer.revalidate();
		fieldsContainer.repaint();
	}

	private JComponent buildFieldComponent(
			JagaTaskAttributeResponse field,
			String reportText,
			JLabel statusLabel
	) {
		String objectType = safe(field.getObjectTypeNameM());

		if ("task.content".equals(objectType)) {
			JTextArea textArea = new JTextArea(reportText, 8, 40);
			textArea.setLineWrap(true);
			textArea.setWrapStyleWord(true);
			JScrollPane scrollPane = new JScrollPane(textArea);
			scrollPane.setPreferredSize(new Dimension(500, 160));
			fieldComponents.put(field.getId(), textArea);
			return scrollPane;
		}

		if ("task.parent_id".equals(objectType)) {
			return buildTaskParentField(field, statusLabel);
		}

		if (field.getDictionaryId() != null) {
			if (Boolean.TRUE.equals(field.getMultipleSelector())) {
				return buildMultiSelectDictionaryField(field, statusLabel);
			}
			return buildSingleSelectDictionaryField(field, statusLabel);
		}

		JTextField textField = new JTextField();
		fieldComponents.put(field.getId(), textField);
		return textField;
	}

	private LinkedHashMap<String, Long> loadDictionaryValueToId(
			Long dictionaryId,
			String username,
			String password
	) throws Exception {
		if (dictionaryId == null) {
			return new LinkedHashMap<>();
		}

		JagaListRefResponse response = new JagaControllerApi(
				getApiClient("https://jaga.rt.ru", username, password)
		).getListRefAny(dictionaryId).block();

		LinkedHashMap<String, Long> result = new LinkedHashMap<>();
		if (response == null || response.getItems() == null) {
			return result;
		}

		for (JagaListRefItemResponse item : response.getItems()) {
			if (item == null || item.getId() == null) {
				continue;
			}

			String value = safe(item.getValue());
			if (value.isBlank()) {
				continue;
			}

			result.putIfAbsent(value, item.getId());
		}

		return result;
	}

	private JComponent buildSingleSelectDictionaryField(
			JagaTaskAttributeResponse field,
			JLabel statusLabel
	) {
		JComboBox<RefOption> comboBox = new JComboBox<>();
		comboBox.setEnabled(false);
		comboBox.addItem(new RefOption(null, "Загрузка..."));

		fieldComponents.put(field.getId(), comboBox);

		loadDictionaryAsync(field, statusLabel, valueToId -> {
			DefaultComboBoxModel<RefOption> model = new DefaultComboBoxModel<>();
			model.addElement(new RefOption(null, ""));

			for (Map.Entry<String, Long> entry : valueToId.entrySet()) {
				model.addElement(new RefOption(entry.getValue(), entry.getKey()));
			}

			comboBox.setModel(model);
			comboBox.setEnabled(true);
		});

		return comboBox;
	}

	private JComponent buildMultiSelectDictionaryField(
			JagaTaskAttributeResponse field,
			JLabel statusLabel
	) {
		MultiSelectChipsField chipsField = new MultiSelectChipsField(List.of());
		setTaskTypesFieldEnabled(chipsField, false);

		fieldComponents.put(field.getId(), chipsField);

		loadDictionaryAsync(field, statusLabel, valueToId -> {
			List<ChipItem> chipItems = new ArrayList<>();
			for (Map.Entry<String, Long> entry : valueToId.entrySet()) {
				chipItems.add(new ChipItem(entry.getValue(), entry.getKey()));
			}

			chipsField.setAvailableItems(chipItems);
			chipsField.setSelectedIds(List.of());
			setTaskTypesFieldEnabled(chipsField, true);
		});

		return chipsField;
	}

	private void loadDictionaryAsync(
			JagaTaskAttributeResponse field,
			JLabel statusLabel,
			java.util.function.Consumer<LinkedHashMap<String, Long>> onSuccess
	) {
		Long dictionaryId = field.getDictionaryId();
		if (dictionaryId == null) {
			onSuccess.accept(new LinkedHashMap<>());
			return;
		}

		String username = safe(jagaUserSettings.getEmail());
		final String password;
		try {
			password = resolveJagaPassword();
		} catch (Exception ex) {
			log.error("Не удалось получить пароль Jaga для dictionaryId={}", dictionaryId, ex);
			statusLabel.setText("Не удалось загрузить справочник для поля: " + resolveFieldLabel(field));
			statusLabel.setForeground(Color.RED);
			return;
		}

		new SwingWorker<LinkedHashMap<String, Long>, Void>() {
			@Override
			protected LinkedHashMap<String, Long> doInBackground() throws Exception {
				return loadDictionaryValueToId(dictionaryId, username, password);
			}

			@Override
			protected void done() {
				try {
					LinkedHashMap<String, Long> valueToId = get();
					fieldDictionaryValues.put(field.getId(), valueToId);
					onSuccess.accept(valueToId);
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					log.error("Ошибка загрузки dictionaryId={} для поля {}", dictionaryId, field.getId(), cause);
					statusLabel.setText("Не удалось загрузить справочник: " + resolveFieldLabel(field));
					statusLabel.setForeground(Color.RED);
				}
			}
		}.execute();
	}

	private JagaTaskResponse createTask(TaskTypeOption selectedTaskType) throws Exception {
		Long projectId = jagaUserSettings.getProjectId();
		String username = safe(jagaUserSettings.getEmail());
		String password = resolveJagaPassword();

		if (projectId == null) {
			throw new IllegalStateException("Не заполнен projectId");
		}
		if (username.isBlank()) {
			throw new IllegalStateException("Не заполнен email");
		}
		if (password == null || password.isBlank()) {
			throw new IllegalStateException("Не найден пароль");
		}

		JagaCreateTaskRequest request = buildCreateTaskRequest(selectedTaskType);

		log.debug("Отправка задачи в Jaga: projectId={}, taskTypeId={}, attributesCount={}",
				projectId, selectedTaskType.getId(), request.getAttributes() == null ? 0 : request.getAttributes().size());

		JagaTaskResponse response = new JagaControllerApi(
				getApiClient("https://jaga.rt.ru", username, password)
		).createTaskByTaskType(projectId, selectedTaskType.getId(), request).block();

		if (response == null) {
			throw new IllegalStateException("Jaga вернула пустой ответ при создании задачи");
		}

		return response;
	}

	private JagaCreateTaskRequest buildCreateTaskRequest(TaskTypeOption selectedTaskType) {
		if (loadedTaskTypeResponse == null) {
			throw new IllegalStateException("Схема типа задачи не загружена");
		}

		if (selectedTaskType == null || selectedTaskType.getId() == null) {
			throw new IllegalStateException("Не выбран тип задачи");
		}

		Long projectId = jagaUserSettings.getProjectId();
		if (projectId == null) {
			throw new IllegalStateException("Не заполнен projectId");
		}

		JagaCreateTaskRequest request = new JagaCreateTaskRequest();
		request.setOrderNum(1);
		request.setStatusModifier(1);
		request.setAttachmentIds(new ArrayList<>());
		request.setAttributes(new ArrayList<>());

		for (JagaTaskAttributeResponse field : allTaskTypeFields) {
			Object value = resolveAttributeValue(field, selectedTaskType, projectId);

			if (shouldSendField(field, value)) {
				JagaCreateTaskAttributeRequest attr = new JagaCreateTaskAttributeRequest();
				attr.setFieldId(field.getId());
				attr.setValue(value);
				attr.setObjectTypeNameM(safe(field.getObjectTypeNameM()));
				attr.setReferenceValue(false);
				attr.setDictionaryId(null);
				attr.setMnemo("");
				request.getAttributes().add(attr);
			}
		}

		Long statusId = resolveStatusIdFromLoadedType();
		request.setStatusId(statusId);

		return request;
	}

	private boolean shouldSendField(JagaTaskAttributeResponse field, Object value) {
		if (field == null) {
			return false;
		}

		if (value == null) {
			return Boolean.TRUE.equals(field.getRequired());
		}

		if (value instanceof String s) {
			return !s.isBlank() || Boolean.TRUE.equals(field.getRequired());
		}

		if (value instanceof List<?> list) {
			return !list.isEmpty() || Boolean.TRUE.equals(field.getRequired());
		}

		return true;
	}

	private Object resolveAttributeValue(
			JagaTaskAttributeResponse field,
			TaskTypeOption selectedTaskType,
			Long projectId
	) {
		String objectType = safe(field.getObjectTypeNameM());

		switch (objectType) {
			case "task.project_id":
				return projectId;

			case "task.type_id":
				return selectedTaskType.getId();

			case "task.content":
				return getTextComponentValue(field);

			case "task.task_title":
				return getTextComponentValue(field);

			case "task.parent_id":
				return getTaskParentIdValue(field);

			default:
				if (field.getDictionaryId() != null) {
					if (Boolean.TRUE.equals(field.getMultipleSelector()) || Boolean.TRUE.equals(field.getMultiple())) {
						return getMultiSelectValue(field);
					}
					return getSingleSelectValue(field);
				}

				if (Boolean.TRUE.equals(field.getMultipleSelector()) || Boolean.TRUE.equals(field.getMultiple())) {
					return getMultiValueWithoutDictionary(field);
				}

				return getTextComponentValue(field);
		}
	}

	private JComponent buildTaskParentField(
			JagaTaskAttributeResponse field,
			JLabel statusLabel
	) {
		Long projectId = jagaUserSettings.getProjectId();
		String username = safe(jagaUserSettings.getEmail());

		final String password;
		try {
			password = resolveJagaPassword();
		} catch (Exception ex) {
			log.error("Не удалось получить пароль Jaga для поля {}", field.getId(), ex);
			statusLabel.setText("Не удалось подготовить поиск задач для поля: " + resolveFieldLabel(field));
			statusLabel.setForeground(Color.RED);

			JTextField fallbackField = new JTextField();
			fallbackField.setEnabled(false);
			fieldComponents.put(field.getId(), fallbackField);
			return fallbackField;
		}

		if (projectId == null || username.isBlank() || password == null || password.isBlank()) {
			JTextField fallbackField = new JTextField();
			fallbackField.setEnabled(false);
			fieldComponents.put(field.getId(), fallbackField);
			return fallbackField;
		}

		TaskSearchComboBox taskSearchComboBox = new TaskSearchComboBox(
				projectId,
				requestDto -> searchTasksByText(projectId, username, password, requestDto)
		);

		fieldComponents.put(field.getId(), taskSearchComboBox);
		return taskSearchComboBox;
	}

	private SearchResultDto searchTasksByText(
			Long projectId,
			String username,
			String password,
			SearchRequestDto requestDto
	) {
		if (projectId == null) {
			throw new IllegalArgumentException("projectId is required");
		}

		if (requestDto == null) {
			requestDto = new SearchRequestDto();
		}

		SearchResultDto response = new JagaControllerApi(
				getApiClient("https://jaga.rt.ru", username, password)
		).searchTaskByIdOrTitle(projectId,
				TaskSearchComboBox.getSearchSize(),
				TaskSearchComboBox.getSearchPage(),
				requestDto).block();

		return response == null ? new SearchResultDto() : response;
	}

	private Long getTaskParentIdValue(JagaTaskAttributeResponse field) {
		JComponent component = fieldComponents.get(field.getId());
		if (component instanceof TaskSearchComboBox taskSearchComboBox) {
			return taskSearchComboBox.getSelectedTaskId();
		}
		return null;
	}

	private String getTextComponentValue(JagaTaskAttributeResponse field) {
		JComponent component = fieldComponents.get(field.getId());
		if (component instanceof JTextField textField) {
			return safe(textField.getText());
		}
		if (component instanceof JTextArea textArea) {
			return safe(textArea.getText());
		}
		return "";
	}

	private Object getSingleSelectValue(JagaTaskAttributeResponse field) {
		JComponent component = fieldComponents.get(field.getId());
		if (component instanceof JComboBox<?> comboBox) {
			Object selected = comboBox.getSelectedItem();
			if (selected instanceof RefOption option) {
				return option.getId();
			}
		}
		return null;
	}

	private Object getMultiSelectValue(JagaTaskAttributeResponse field) {
		JComponent component = fieldComponents.get(field.getId());
		if (component instanceof MultiSelectChipsField chipsField) {
			return new ArrayList<>(chipsField.getSelectedIds());
		}
		return new ArrayList<Long>();
	}

	private Object getMultiValueWithoutDictionary(JagaTaskAttributeResponse field) {
		JComponent component = fieldComponents.get(field.getId());
		if (component instanceof JTextField textField) {
			String text = safe(textField.getText());
			if (text.isBlank()) {
				return new ArrayList<>();
			}

			List<String> values = java.util.Arrays.stream(text.split(","))
					.map(String::trim)
					.filter(s -> !s.isBlank())
					.toList();

			return new ArrayList<>(values);
		}
		return new ArrayList<>();
	}

	private void clearDynamicFields(JPanel fieldsContainer) {
		fieldsContainer.removeAll();
		fieldsContainer.revalidate();
		fieldsContainer.repaint();
	}

	private void clearTaskTypeState() {
		loadedTaskTypeResponse = null;
		allTaskTypeFields = new ArrayList<>();
		requiredTaskTypeFields = new ArrayList<>();
		fieldComponents.clear();
		fieldDictionaryValues.clear();
	}

	private void fillTaskTypeCombo(JComboBox<TaskTypeOption> comboBox, List<TaskTypeOption> options) {
		DefaultComboBoxModel<TaskTypeOption> model = new DefaultComboBoxModel<>();
		for (TaskTypeOption option : options) {
			model.addElement(option);
		}
		comboBox.setModel(model);
	}

	private String resolveFieldLabel(JagaTaskAttributeResponse field) {
		String name = safe(field.getName());
		if (!name.isBlank()) {
			return name;
		}

		String dictionaryName = safe(field.getDictionaryName());
		if (!dictionaryName.isBlank()) {
			return dictionaryName;
		}

		String objectTypeNameM = safe(field.getObjectTypeNameM());
		if (!objectTypeNameM.isBlank()) {
			return objectTypeNameM;
		}

		return "Поле " + field.getId();
	}

	private String buildJagaDescriptionTemplate() {
		String steps = buildBugReportText();

		StringBuilder sb = new StringBuilder();
		sb.append("Шаги воспроизведения:").append(System.lineSeparator());
		if (!steps.isBlank()) {
			sb.append(steps);
		}
		sb.append(System.lineSeparator()).append(System.lineSeparator());
		sb.append("Ожидаемый результат:").append(System.lineSeparator()).append(System.lineSeparator());
		sb.append("Фактический результат:").append(System.lineSeparator()).append(System.lineSeparator());
		sb.append("Доп. инфо:").append(System.lineSeparator());

		return sb.toString();
	}

	private String resolveJagaPassword() throws Exception {
		JagaUserSettings latest = configService.loadJagaUserSettings(config);
		if (latest.getEncryptedPassword() == null || latest.getEncryptedPassword().isBlank()) {
			return null;
		}
		return SimpleSecretService.decrypt(latest.getEncryptedPassword());
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

	private void importJagaCertificates(Component parent) {
		final String domain = "jaga.rt.ru";

		try {
			String ksPath = config.trustStorePath != null ? config.trustStorePath.trim() : "";
			if (ksPath.isEmpty()) {
				Path configDir = configService.loadConfigDir();
				ksPath = configDir.resolve("custom-cacerts.jks").toString();
				config.trustStorePath = ksPath;
			}

			String ksPassword = config.trustStorePassword != null && !config.trustStorePassword.isBlank()
					? config.trustStorePassword
					: "changeit";

			String ksType = config.trustStoreType != null && !config.trustStoreType.isBlank()
					? config.trustStoreType
					: "JKS";

			config.trustStorePassword = ksPassword;
			config.trustStoreType = ksType;
			configService.save(config);

			final String finalKsPath = ksPath;
			final String finalKsPassword = ksPassword;
			final String finalKsType = ksType;

			new SwingWorker<List<String>, Void>() {
				@Override
				protected List<String> doInBackground() throws Exception {
					return util.CertImporter.importCertsFromDomain(
							domain,
							443,
							finalKsPath,
							finalKsPassword,
							finalKsType
					);
				}

				@Override
				protected void done() {
					try {
						List<String> added = get();

						String msg = added.isEmpty()
								? "Сертификаты для " + domain + " уже были в хранилище.\nTrustStore: " + finalKsPath
								: "Добавлено сертификатов: " + added.size() + "\n"
								+ String.join("\n", added)
								+ "\n\nTrustStore: " + finalKsPath;

						JOptionPane.showMessageDialog(
								parent,
								msg,
								"Импорт сертификатов",
								JOptionPane.INFORMATION_MESSAGE
						);
					} catch (Exception ex) {
						Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
						JOptionPane.showMessageDialog(
								parent,
								"Не удалось импортировать сертификаты для " + domain + ":\n" + cause.getMessage(),
								"Ошибка импорта",
								JOptionPane.WARNING_MESSAGE
						);
					}
				}
			}.execute();

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(
					parent,
					"Не удалось подготовить trustStore:\n" + ex.getMessage(),
					"Ошибка",
					JOptionPane.WARNING_MESSAGE
			);
		}
	}

	private String resolveTaskTitleForMessage() {
		for (JagaTaskAttributeResponse field : allTaskTypeFields) {
			if (field == null) {
				continue;
			}

			String objectType = safe(field.getObjectTypeNameM());
			String name = safe(field.getName());

			if ("task.task_title".equals(objectType) || "Название".equalsIgnoreCase(name)) {
				return getTextComponentValue(field);
			}
		}
		return "";
	}

	private String buildCreatedTaskMessage(JagaTaskResponse response) {
		String code = response == null ? "" : safe(response.getCode());
		String title = resolveTaskTitleForMessage();

		String url = code.isBlank()
				? "https://jaga.rt.ru/browse/"
				: "https://jaga.rt.ru/browse/" + code;

		StringBuilder sb = new StringBuilder();
		sb.append("Завел баг ").append(url);

		if (!title.isBlank()) {
			sb.append(System.lineSeparator()).append(title);
		}

		return sb.toString();
	}

	private void showCreatedTaskDialog(Window parent, String message) {
		JDialog dialog = new JDialog(parent, "Баг создан", Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		JTextArea textArea = new JTextArea(message, 6, 60);
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setCaretPosition(0);

		JScrollPane scrollPane = new JScrollPane(textArea);

		JButton copyButton = new JButton("Копировать");
		copyButton.addActionListener(e -> {
			Toolkit.getDefaultToolkit()
					.getSystemClipboard()
					.setContents(new java.awt.datatransfer.StringSelection(message), null);
		});

		JButton closeButton = new JButton("Закрыть");
		closeButton.addActionListener(e -> dialog.dispose());

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(copyButton);
		buttons.add(closeButton);

		JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		root.add(scrollPane, BorderLayout.CENTER);
		root.add(buttons, BorderLayout.SOUTH);

		dialog.setContentPane(root);
		dialog.pack();
		dialog.setLocationRelativeTo(parent);
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
		try {
			WebClient webClient = buildJagaWebClient();
			ApiClient apiClient = new ApiClient(webClient);
			apiClient.setBasePath(fullUrl);

			String token = getJagaToken(username, password);
			apiClient.setBearerToken(token);

			return apiClient;
		} catch (Exception ex) {
			throw new IllegalStateException("Не удалось создать Jaga ApiClient с trustStore: " + ex.getMessage(), ex);
		}
	}

	private TrustManagerFactory buildTrustManagerFactory() throws Exception {
		String ksPath = config.trustStorePath != null ? config.trustStorePath.trim() : "";
		String ksPassword = config.trustStorePassword != null && !config.trustStorePassword.isBlank()
				? config.trustStorePassword
				: "changeit";
		String ksType = config.trustStoreType != null && !config.trustStoreType.isBlank()
				? config.trustStoreType
				: "JKS";

		if (ksPath.isBlank()) {
			throw new IllegalStateException("Не указан trustStorePath");
		}

		Path path = Path.of(ksPath);
		if (!Files.exists(path)) {
			throw new IllegalStateException("TrustStore не найден: " + ksPath);
		}

		KeyStore trustStore = KeyStore.getInstance(ksType);
		try (InputStream is = Files.newInputStream(path)) {
			trustStore.load(is, ksPassword.toCharArray());
		}

		TrustManagerFactory tmf = TrustManagerFactory.getInstance(
				TrustManagerFactory.getDefaultAlgorithm()
		);
		tmf.init(trustStore);
		return tmf;
	}

	private WebClient buildJagaWebClient() throws Exception {
		ObjectMapper mapper = ApiClient.createDefaultObjectMapper(ApiClient.createDefaultDateFormat());

		TrustManagerFactory tmf = buildTrustManagerFactory();

		io.netty.handler.ssl.SslContext sslContext = io.netty.handler.ssl.SslContextBuilder
				.forClient()
				.trustManager(tmf)
				.build();

		reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
				.secure(sslSpec -> sslSpec.sslContext(sslContext));

		return ApiClient.buildWebClientBuilder(mapper)
				.clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
				.build();
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

	//должно быть 100042L
	@SneakyThrows
	private Long resolveStatusIdFromLoadedType() {
		if (loadedTaskTypeResponse == null) {
			throw new IllegalStateException("Схема типа задачи не загружена");
		}

		Long workflowId = loadedTaskTypeResponse.getWorkflowId();
		if (workflowId == null) {
			throw new IllegalStateException("У типа задачи отсутствует workflowId");
		}

		String username = safe(jagaUserSettings.getEmail());
		String password = resolveJagaPassword();

		if (username.isBlank() || password == null || password.isBlank()) {
			throw new IllegalStateException("Не заполнены учетные данные Jaga");
		}

		JagaWorkflowResponse workflow = new JagaControllerApi(
				getApiClient("https://jaga.rt.ru", username, password)
		).getWorkflow(workflowId).block();

		if (workflow == null || workflow.getStatusTransitions() == null || workflow.getStatusTransitions().isEmpty()) {
			throw new IllegalStateException("Не удалось получить переходы workflow");
		}

		Long preferred = workflow.getStatusTransitions().stream()
				.filter(Objects::nonNull)
				.filter(t -> t.getStatusFromId() == null)
				.filter(t -> t.getStatusToId() != null)
				.filter(t -> Integer.valueOf(1).equals(t.getTransitionMod()))
				.map(JagaWorkflowTransitionResponse::getStatusToId)
				.findFirst()
				.orElse(null);

		if (preferred != null) {
			return preferred;
		}

		Long fallback = workflow.getStatusTransitions().stream()
				.filter(Objects::nonNull)
				.filter(t -> t.getStatusFromId() == null)
				.filter(t -> t.getStatusToId() != null)
				.filter(t -> Integer.valueOf(0).equals(t.getTransitionMod()))
				.map(JagaWorkflowTransitionResponse::getStatusToId)
				.findFirst()
				.orElse(null);

		if (fallback != null) {
			return fallback;
		}

		return workflow.getStatusTransitions().stream()
				.filter(Objects::nonNull)
				.map(JagaWorkflowTransitionResponse::getStatusToId)
				.filter(Objects::nonNull)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Не найден стартовый статус workflow"));
	}

	private static class TaskTypeOption {
		private final Long id;
		private final String label;

		private TaskTypeOption(Long id, String label) {
			this.id = id;
			this.label = label;
		}

		public Long getId() {
			return id;
		}

		public String getLabel() {
			return label;
		}

		@Override
		public String toString() {
			return label == null ? "" : label;
		}
	}

	private static class RefOption {
		private final Long id;
		private final String label;

		private RefOption(Long id, String label) {
			this.id = id;
			this.label = label;
		}

		public Long getId() {
			return id;
		}

		public String getLabel() {
			return label;
		}

		@Override
		public String toString() {
			return label == null ? "" : label;
		}
	}
}