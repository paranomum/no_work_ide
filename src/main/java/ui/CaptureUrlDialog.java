package ui;

import javax.swing.*;
import java.awt.*;

public class CaptureUrlDialog extends JDialog {

	private final JTextField urlField;
	private boolean confirmed = false;

	public CaptureUrlDialog(Frame parent) {
		super(parent, "Захват запроса", true);
		setSize(500, 160);
		setLocationRelativeTo(parent);
		setLayout(new BorderLayout(10, 10));

		JPanel center = new JPanel(new BorderLayout(5, 5));
		center.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));
		center.add(new JLabel("Введите URL (или часть URL) для поиска:"), BorderLayout.NORTH);

		urlField = new JTextField();
		urlField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		center.add(urlField, BorderLayout.CENTER);

		add(center, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton startBtn = new JButton("Начать захват");
		JButton cancelBtn = new JButton("Отмена");

		startBtn.addActionListener(e -> {
			if (urlField.getText().isBlank()) {
				JOptionPane.showMessageDialog(this, "Введите URL", "Ошибка", JOptionPane.WARNING_MESSAGE);
				return;
			}
			confirmed = true;
			dispose();
		});

		cancelBtn.addActionListener(e -> dispose());
		getRootPane().setDefaultButton(startBtn);

		buttons.add(startBtn);
		buttons.add(cancelBtn);
		add(buttons, BorderLayout.SOUTH);
	}

	public String getUrl() {
		return confirmed ? urlField.getText().trim() : null;
	}
}