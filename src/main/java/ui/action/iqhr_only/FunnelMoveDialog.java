package ui.action.iqhr_only;

import ui.action.VariablesService;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class FunnelMoveDialog extends JDialog {

	private final VariablesService variablesService;

	private final JTextField jrIdField = new JTextField();
	private final JTextField candidateIdField = new JTextField();
	private final JTextField vacancyIdField = new JTextField();
	private final JTextField usernameField = new JTextField();
	private final JPasswordField passwordField = new JPasswordField();

	private FunnelMoveRequestDef result;

	private FunnelMoveDialog(
			Window parent,
			VariablesService variablesService,
			FunnelMoveRequestDef initialValue
	) {
		super(parent, "Параметры процессинга кандидата", ModalityType.APPLICATION_MODAL);

		this.variablesService = variablesService;

		fillInitialValues(initialValue);
		initUi();

		setMinimumSize(new Dimension(650, 300));
		pack();
		setLocationRelativeTo(parent);
	}

	public static FunnelMoveRequestDef showDialog(
			Component parent,
			VariablesService variablesService,
			FunnelMoveRequestDef initialValue
	) {
		Window parentWindow = SwingUtilities.getWindowAncestor(parent);

		FunnelMoveDialog dialog = new FunnelMoveDialog(
				parentWindow,
				variablesService,
				initialValue
		);

		dialog.setVisible(true);

		return dialog.result;
	}

	private void fillInitialValues(FunnelMoveRequestDef initialValue) {
		if (initialValue == null) {
			return;
		}

		jrIdField.setText(safe(initialValue.getJrId()));
		candidateIdField.setText(safe(initialValue.getCandidateId()));
		vacancyIdField.setText(safe(initialValue.getVacancyId()));
		usernameField.setText(safe(initialValue.getUsername()));
		passwordField.setText(safe(initialValue.getPassword()));
	}

	private void initUi() {
		JPanel formPanel = new JPanel(new GridBagLayout());
		formPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		addFieldRow(formPanel, 0, "jrId:", jrIdField);
		addFieldRow(formPanel, 1, "candidateId:", candidateIdField);
		addFieldRow(formPanel, 2, "vacancyId:", vacancyIdField);
		addFieldRow(formPanel, 3, "username:", usernameField);
		addFieldRow(formPanel, 4, "password:", passwordField);

		JButton saveButton = new JButton("Сохранить");
		saveButton.addActionListener(e -> save());

		JButton cancelButton = new JButton("Отмена");
		cancelButton.addActionListener(e -> dispose());

		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttonsPanel.add(cancelButton);
		buttonsPanel.add(saveButton);

		setLayout(new BorderLayout(8, 8));
		add(formPanel, BorderLayout.CENTER);
		add(buttonsPanel, BorderLayout.SOUTH);
	}

	private void addFieldRow(
			JPanel formPanel,
			int row,
			String labelText,
			JTextField field
	) {
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = row;
		constraints.insets = new Insets(4, 4, 4, 4);
		constraints.fill = GridBagConstraints.HORIZONTAL;

		constraints.gridx = 0;
		constraints.weightx = 0;
		formPanel.add(new JLabel(labelText), constraints);

		constraints.gridx = 1;
		constraints.weightx = 1;
		formPanel.add(field, constraints);

		constraints.gridx = 2;
		constraints.weightx = 0;
		formPanel.add(createPickVariableButton(field), constraints);
	}

	private JButton createPickVariableButton(JTextField field) {
		JButton button = new JButton("$");
		button.setToolTipText("Использовать переменную");

		button.addActionListener(e -> {
			List<String> names = variablesService.getVariableNames();

			if (names.isEmpty()) {
				JOptionPane.showMessageDialog(
						this,
						"Нет доступных переменных",
						"Variables",
						JOptionPane.INFORMATION_MESSAGE
				);
				return;
			}

			String selectedVariable = (String) JOptionPane.showInputDialog(
					this,
					"Выберите переменную:",
					"Variables",
					JOptionPane.PLAIN_MESSAGE,
					null,
					names.toArray(),
					names.get(0)
			);

			if (selectedVariable != null) {
				field.setText("${" + selectedVariable + "}");
			}
		});

		return button;
	}

	private void save() {
		String jrId = jrIdField.getText().trim();
		String candidateId = candidateIdField.getText().trim();
		String vacancyId = vacancyIdField.getText().trim();
		String username = usernameField.getText().trim();
		String password = new String(passwordField.getPassword()).trim();

		if (jrId.isBlank()
				|| candidateId.isBlank()
				|| vacancyId.isBlank()
				|| username.isBlank()
				|| password.isBlank()) {

			JOptionPane.showMessageDialog(
					this,
					"Заполни все поля.",
					"Не заполнены параметры",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		result = new FunnelMoveRequestDef(
				jrId,
				candidateId,
				vacancyId,
				username,
				password
		);

		dispose();
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}
}