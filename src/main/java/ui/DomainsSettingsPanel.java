package ui;

import dto.AppConfig;
import ui.action.ConfigService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DomainsSettingsPanel extends JPanel {

	private final AppConfig config;
	private final ConfigService configService;
	private final Runnable refreshCallback;

	private final DefaultTableModel tableModel;
	private final JTable domainsTable;
	private final JTextField domainField;

	public DomainsSettingsPanel(AppConfig config, ConfigService configService, Runnable refreshCallback) {
		this.config = config;
		this.configService = configService;
		this.refreshCallback = refreshCallback;

		setLayout(new BorderLayout(10, 10));

		tableModel = new DefaultTableModel(new Object[]{"Домен"}, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		domainsTable = new JTable(tableModel);
		domainsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		domainsTable.setRowHeight(26);

		JScrollPane scrollPane = new JScrollPane(domainsTable);

		domainField = new JTextField(30);

		JButton addButton = new JButton("Добавить");
		addButton.addActionListener(e -> addDomain());

		JButton removeButton = new JButton("Удалить");
		removeButton.addActionListener(e -> removeSelectedDomain());

		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		topPanel.add(new JLabel("Домен:"));
		topPanel.add(domainField);
		topPanel.add(addButton);
		topPanel.add(removeButton);

		add(topPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);

		reloadTableFromConfig();
	}

	private void reloadTableFromConfig() {
		tableModel.setRowCount(0);

		if (config.domains == null) {
			config.domains = new ArrayList<>();
		}

		for (String domain : config.domains) {
			if (domain == null) {
				continue;
			}

			String trimmed = domain.trim();
			if (!trimmed.isEmpty()) {
				tableModel.addRow(new Object[]{trimmed});
			}
		}
	}

	private void addDomain() {
		String newDomain = domainField.getText() != null ? domainField.getText().trim() : "";
		if (newDomain.isEmpty()) {
			JOptionPane.showMessageDialog(
					this,
					"Введите доменное имя.",
					"Пустое значение",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		newDomain = newDomain.replaceFirst("^https?://", "").replaceAll("/.*$", "").trim();
		if (newDomain.isEmpty()) {
			JOptionPane.showMessageDialog(
					this,
					"Некорректное доменное имя.",
					"Ошибка",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		if (config.domains == null) {
			config.domains = new ArrayList<>();
		}

		for (String existing : config.domains) {
			if (existing != null && existing.trim().equalsIgnoreCase(newDomain)) {
				JOptionPane.showMessageDialog(
						this,
						"Такой домен уже добавлен.",
						"Дубликат",
						JOptionPane.WARNING_MESSAGE
				);
				return;
			}
		}

		config.domains.add(newDomain);

		if (config.selectedDomain == null || config.selectedDomain.isBlank()) {
			config.selectedDomain = newDomain;
		}

		persistDomains();
		reloadTableFromConfig();
		domainField.setText("");

		if (refreshCallback != null) {
			refreshCallback.run();
		}

		String ksPath = config.trustStorePath != null ? config.trustStorePath.trim() : "";
		if (!ksPath.isEmpty()) {
			String ksPassword = config.trustStorePassword != null && !config.trustStorePassword.isBlank()
					? config.trustStorePassword
					: "changeit";

			String ksType = config.trustStoreType != null && !config.trustStoreType.isBlank()
					? config.trustStoreType
					: "JKS";

			final String domainToImport = newDomain;

			new Thread(() -> {
				try {
					java.util.List<String> added = util.CertImporter.importCertsFromDomain(
							domainToImport,
							443,
							ksPath,
							ksPassword,
							ksType
					);

					String msg = added.isEmpty()
							? "Сертификаты для " + domainToImport + " уже были в хранилище."
							: "Добавлено сертификатов: " + added.size() + "\n" + String.join("\n", added);

					SwingUtilities.invokeLater(() ->
							JOptionPane.showMessageDialog(
									DomainsSettingsPanel.this,
									msg,
									"Импорт сертификатов",
									JOptionPane.INFORMATION_MESSAGE
							)
					);
				} catch (Exception ex) {
					SwingUtilities.invokeLater(() ->
							JOptionPane.showMessageDialog(
									DomainsSettingsPanel.this,
									"Не удалось импортировать сертификаты для " + domainToImport + ":\n" + ex.getMessage(),
									"Ошибка импорта",
									JOptionPane.WARNING_MESSAGE
							)
					);
				}
			}, "cert-importer").start();
		}
	}

	private void removeSelectedDomain() {
		int selectedRow = domainsTable.getSelectedRow();
		if (selectedRow < 0) {
			JOptionPane.showMessageDialog(
					this,
					"Выберите домен для удаления.",
					"Нет выбора",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		Object value = tableModel.getValueAt(selectedRow, 0);
		String domainToRemove = value != null ? value.toString().trim() : "";
		if (domainToRemove.isEmpty()) {
			return;
		}

		List<String> updated = new ArrayList<>();
		if (config.domains != null) {
			for (String domain : config.domains) {
				if (domain == null) {
					continue;
				}

				String trimmed = domain.trim();
				if (!trimmed.equalsIgnoreCase(domainToRemove) && !trimmed.isEmpty()) {
					updated.add(trimmed);
				}
			}
		}

		config.domains = updated;

		if (config.selectedDomain != null && config.selectedDomain.trim().equalsIgnoreCase(domainToRemove)) {
			config.selectedDomain = updated.isEmpty() ? "" : updated.get(0);
		}

		persistDomains();
		reloadTableFromConfig();

		if (refreshCallback != null) {
			refreshCallback.run();
		}
	}

	private void persistDomains() {
		normalizeDomainsInConfig();

		try {
			configService.save(config);
		} catch (IOException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					this,
					"Не удалось сохранить домены: " + ex.getMessage(),
					"Ошибка сохранения",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void normalizeDomainsInConfig() {
		List<String> normalized = new ArrayList<>();

		if (config.domains != null) {
			for (String domain : config.domains) {
				if (domain == null) {
					continue;
				}

				String trimmed = domain.trim();
				if (trimmed.isEmpty()) {
					continue;
				}

				boolean exists = false;
				for (String existing : normalized) {
					if (existing.equalsIgnoreCase(trimmed)) {
						exists = true;
						break;
					}
				}

				if (!exists) {
					normalized.add(trimmed);
				}
			}
		}

		config.domains = normalized;

		String selected = config.selectedDomain != null ? config.selectedDomain.trim() : "";
		boolean selectedExists = false;

		for (String domain : normalized) {
			if (domain.equalsIgnoreCase(selected)) {
				selected = domain;
				selectedExists = true;
				break;
			}
		}

		if (!selectedExists) {
			selected = normalized.isEmpty() ? "" : normalized.get(0);
		}

		config.selectedDomain = selected;
	}
}