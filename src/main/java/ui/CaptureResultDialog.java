package ui;

import dto.BackendRequestDef;
import lombok.Getter;
import ui.action.BackendRequestsService;

import javax.swing.*;
import java.awt.*;

public class CaptureResultDialog extends JDialog {

	private final BackendRequestsService backendRequestsService;
	private final BackendRequestDef captured;
	@Getter
	private boolean saved = false;

	public CaptureResultDialog(Frame parent,
							   BackendRequestDef captured,
							   BackendRequestsService backendRequestsService) {
		super(parent, "Захваченный запрос", true);
		this.captured = captured;
		this.backendRequestsService = backendRequestsService;
		setSize(680, 560);
		setLocationRelativeTo(parent);
		buildUi();
	}

	private void buildUi() {
		setLayout(new BorderLayout(8, 8));

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

		JTextArea bodyArea = new JTextArea(captured.getRequestBody() != null ? captured.getRequestBody() : "");
		bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		bodyArea.setLineWrap(true);
		bodyArea.setWrapStyleWord(false);
		JScrollPane bodyScroll = new JScrollPane(bodyArea);
		bodyScroll.setBorder(BorderFactory.createTitledBorder("Request Body (DTO)"));

		JTextArea headersArea = new JTextArea(captured.getRequestHeaders() != null ? captured.getRequestHeaders() : "{}");
		headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		headersArea.setRows(5);
		headersArea.setLineWrap(true);
		JScrollPane headersScroll = new JScrollPane(headersArea);
		headersScroll.setBorder(BorderFactory.createTitledBorder("Заголовки (JSON)"));

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bodyScroll, headersScroll);
		split.setDividerLocation(280);
		add(split, BorderLayout.CENTER);

		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveBtn = new JButton("Сохранить как Backend Request");
		JButton editBtn = new JButton("Редактировать DTO");
		JButton closeBtn = new JButton("Закрыть");

		saveBtn.addActionListener(e -> {
			String name = nameField.getText().trim();
			if (name.isBlank()) {
				JOptionPane.showMessageDialog(this, "Введите имя", "Ошибка", JOptionPane.WARNING_MESSAGE);
				return;
			}
			captured.setName(name);
			captured.setRequestBody(bodyArea.getText());
			captured.setRequestHeaders(headersArea.getText());
			backendRequestsService.addRequest(captured);
			backendRequestsService.save();
			saved = true;
			JOptionPane.showMessageDialog(this, "Запрос сохранён: " + name,
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

}