package ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dto.BackendRequestDef;
import dto.ResponseFieldExtractor;
import lombok.Getter;
import ui.action.BackendRequestsService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CaptureResultDialog extends JDialog {

	private final BackendRequestsService backendRequestsService;
	private final BackendRequestDef captured;
	@Getter
	private boolean saved = false;

	// Таблица извлечённых полей из ответа
	private DefaultTableModel extractorModel;

	public CaptureResultDialog(Frame parent,
							   BackendRequestDef captured,
							   BackendRequestsService backendRequestsService) {
		super(parent, "Захваченный запрос", true);
		this.captured = captured;
		this.backendRequestsService = backendRequestsService;
		setSize(760, 680);
		setLocationRelativeTo(parent);
		buildUi();
	}

	private void buildUi() {
		setLayout(new BorderLayout(8, 8));

		// ── NORTH: мета-информация ─────────────────────────────────────────
		JPanel metaPanel = new JPanel(new GridBagLayout());
		metaPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder("Информация о запросе"),
				BorderFactory.createEmptyBorder(5, 10, 5, 10)));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(3, 3, 3, 3);

		gbc.gridx = 0; gbc.gridy = 0;
		metaPanel.add(new JLabel("Метод:"), gbc);
		JLabel methodLabel = new JLabel(captured.getMethod());
		methodLabel.setFont(methodLabel.getFont().deriveFont(Font.BOLD));
		gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
		metaPanel.add(methodLabel, gbc);

		gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
		metaPanel.add(new JLabel("URL:"), gbc);
		JTextField urlField = new JTextField(captured.getUrl());
		urlField.setEditable(false);
		urlField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
		metaPanel.add(urlField, gbc);

		gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
		metaPanel.add(new JLabel("Имя запроса:"), gbc);
		JTextField nameField = new JTextField(captured.getName());
		gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
		metaPanel.add(nameField, gbc);

		add(metaPanel, BorderLayout.NORTH);

		// ── CENTER: табы Request Body / Response Body ──────────────────────
		JTextArea bodyArea = new JTextArea(captured.getRequestBody() != null ? captured.getRequestBody() : "");
		bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		bodyArea.setLineWrap(true);
		bodyArea.setWrapStyleWord(false);

		JTextArea headersArea = new JTextArea(captured.getRequestHeaders() != null ? captured.getRequestHeaders() : "{}");
		headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		headersArea.setRows(5);
		headersArea.setLineWrap(true);

		String rawResponse = captured.getCapturedResponseBody() != null ? captured.getCapturedResponseBody() : "";
		JTextArea responseArea = new JTextArea(beautifyJson(rawResponse));
		responseArea.setEditable(false);
		responseArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		responseArea.setLineWrap(true);
		responseArea.setBackground(new Color(245, 250, 245));

		// ── Таблица extractors ─────────────────────────────────────────────
		extractorModel = new DefaultTableModel(new String[]{"JSON путь (fieldPath)", "Имя переменной"}, 0) {
			@Override public boolean isCellEditable(int r, int c) { return true; }
		};

		// Загружаем уже существующие extractors (если редактируем повторно)
		for (ResponseFieldExtractor ex : captured.getResponseExtractors()) {
			extractorModel.addRow(new Object[]{ex.getFieldPath(), ex.getVariableName()});
		}

		JTable extractorTable = new JTable(extractorModel);
		extractorTable.setRowHeight(22);
		extractorTable.getColumnModel().getColumn(0).setPreferredWidth(280);
		extractorTable.getColumnModel().getColumn(1).setPreferredWidth(280);

		JButton addExtractorBtn = new JButton("+");
		JButton removeExtractorBtn = new JButton("-");
		JButton parseResponseBtn = new JButton("⬇ Разобрать поля из ответа");
		parseResponseBtn.setFont(parseResponseBtn.getFont().deriveFont(11f));
		parseResponseBtn.setToolTipText("Разобрать JSON ответа и предложить все листовые поля");

		addExtractorBtn.addActionListener(e ->
				extractorModel.addRow(new Object[]{"", ""}));

		removeExtractorBtn.addActionListener(e -> {
			int row = extractorTable.getSelectedRow();
			if (row >= 0) {
				if (extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing();
				extractorModel.removeRow(row);
			}
		});

		parseResponseBtn.addActionListener(e -> {
			if (extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing();
			String respText = responseArea.getText().trim();
			List<String> paths = extractJsonLeafPaths(respText);
			if (paths.isEmpty()) {
				JOptionPane.showMessageDialog(this,
						"Не удалось разобрать JSON ответа или ответ пустой.",
						"Parse error", JOptionPane.WARNING_MESSAGE);
				return;
			}
			String reqName = nameField.getText().trim().isEmpty() ? captured.getName() : nameField.getText().trim();
			for (String path : paths) {
				// Имя переменной: requestName.fieldPath → отображается как json(fieldPath)
				String varName = reqName + "." + path;
				extractorModel.addRow(new Object[]{path, varName});
			}
		});

		JPanel extractorTopBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		extractorTopBar.add(new JLabel("Поля из ответа:"));
		extractorTopBar.add(addExtractorBtn);
		extractorTopBar.add(removeExtractorBtn);
		extractorTopBar.add(parseResponseBtn);

		JLabel extractorHint = new JLabel(
				"<html><font color='gray' size='2'>" +
						"Имя переменной будет доступно как <b>${requestName.fieldPath}</b>. " +
						"Отображается в сценарии как <b>json(fieldPath)</b> для наглядности." +
						"</font></html>"
		);
		extractorHint.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

		JPanel extractorPanel = new JPanel(new BorderLayout(2, 2));
		extractorPanel.add(extractorTopBar, BorderLayout.NORTH);
		extractorPanel.add(new JScrollPane(extractorTable), BorderLayout.CENTER);
		extractorPanel.add(extractorHint, BorderLayout.SOUTH);
		extractorPanel.setBorder(BorderFactory.createTitledBorder("Извлечение переменных из ответа"));

		// Response-таб
		JPanel responseTabContent = new JPanel(new BorderLayout(4, 4));
		responseTabContent.add(new JScrollPane(responseArea), BorderLayout.CENTER);
		responseTabContent.add(extractorPanel, BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				new JScrollPane(bodyArea), new JScrollPane(headersArea));
		split.setDividerLocation(200);
		split.setBorder(null);

		JPanel requestTabContent = new JPanel(new BorderLayout());
		requestTabContent.add(split, BorderLayout.CENTER);

		JTabbedPane centerTabs = new JTabbedPane();
		centerTabs.addTab("Request Body / Headers", requestTabContent);
		centerTabs.addTab("Response Body + Поля", responseTabContent);

		add(centerTabs, BorderLayout.CENTER);

		// ── SOUTH: кнопки ─────────────────────────────────────────────────
		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveBtn = new JButton("💾 Сохранить и вернуться к списку");
		JButton editBtn = new JButton("Редактировать DTO");
		JButton closeBtn = new JButton("Отмена");

		saveBtn.addActionListener(e -> {
			String name = nameField.getText().trim();
			if (name.isBlank()) {
				JOptionPane.showMessageDialog(this, "Введите имя", "Ошибка", JOptionPane.WARNING_MESSAGE);
				return;
			}
			captured.setName(name);
			captured.setRequestBody(bodyArea.getText());
			captured.setRequestHeaders(headersArea.getText());

			// Сохраняем extractors
			if (extractorTable.isEditing()) extractorTable.getCellEditor().stopCellEditing();
			List<ResponseFieldExtractor> extractors = new ArrayList<>();
			for (int i = 0; i < extractorModel.getRowCount(); i++) {
				String fp = String.valueOf(extractorModel.getValueAt(i, 0)).trim();
				String vn = String.valueOf(extractorModel.getValueAt(i, 1)).trim();
				if (!fp.isEmpty()) {
					if (vn.isEmpty()) vn = name + "." + fp;
					extractors.add(new ResponseFieldExtractor(fp, vn));
				}
			}
			captured.setResponseExtractors(extractors);

			try {
				backendRequestsService.addRequest(captured);
			} catch (IllegalArgumentException ex) {
				JOptionPane.showMessageDialog(this,
						ex.getMessage() + "\n\nИзмените имя запроса в поле выше.",
						"Имя уже занято", JOptionPane.WARNING_MESSAGE);
				return; // не закрываем диалог, даём изменить имя
			}
			backendRequestsService.save();
			saved = true;
			JOptionPane.showMessageDialog(this, "Запрос сохранён: " + captured.getName(),
					"Сохранено", JOptionPane.INFORMATION_MESSAGE);
			dispose();
		});

		editBtn.addActionListener(e -> {
			captured.setRequestBody(bodyArea.getText());
			captured.setRequestHeaders(headersArea.getText());
			captured.setName(nameField.getText().trim());
			backendRequestsService.openEditDtoDialogFor(this, captured);
			bodyArea.setText(captured.getRequestBody());
			headersArea.setText(captured.getRequestHeaders());
		});

		closeBtn.addActionListener(e -> dispose());

		btnPanel.add(editBtn);
		btnPanel.add(saveBtn);
		btnPanel.add(closeBtn);
		add(btnPanel, BorderLayout.SOUTH);
	}

	private String beautifyJson(String raw) {
		if (raw == null || raw.isBlank()) return raw != null ? raw : "";
		try {
			JsonElement el = JsonParser.parseString(raw);
			return new GsonBuilder().setPrettyPrinting().create().toJson(el);
		} catch (JsonSyntaxException e) {
			return raw;
		}
	}

	private List<String> extractJsonLeafPaths(String jsonText) {
		List<String> paths = new ArrayList<>();
		if (jsonText == null || jsonText.isBlank()) return paths;
		try {
			JsonElement root = JsonParser.parseString(jsonText);
			collectLeafPaths(root, "", paths);
		} catch (JsonSyntaxException ignored) {}
		return paths;
	}

	private void collectLeafPaths(JsonElement element, String prefix, List<String> result) {
		if (element.isJsonObject()) {
			for (var entry : element.getAsJsonObject().entrySet()) {
				String newPath = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
				JsonElement child = entry.getValue();
				if (child.isJsonObject()) {
					collectLeafPaths(child, newPath, result);
				} else if (child.isJsonArray()) {
					var arr = child.getAsJsonArray();
					if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
						collectLeafPaths(arr.get(0), newPath + "[0]", result);
					} else {
						result.add(newPath);
					}
				} else {
					result.add(newPath);
				}
			}
		}
	}
}