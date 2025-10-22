
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class Main {
	public static void main(String[] args) {
		FlatLightLaf.install();

		SwingUtilities.invokeLater(() -> {
			// Главное окно
			JFrame frame = new JFrame("Test Recorder – Панель действий");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setSize(900, 650);
			frame.setLocationRelativeTo(null);

			// ====== Верхняя панель с кнопкой меню слева ======
			JPanel topBar = new JPanel(new BorderLayout());
			topBar.setBorder(new EmptyBorder(5, 5, 5, 5));

			// Кнопка-стрелочка слева
			JButton menuButton = new JButton("☰");
			menuButton.setFocusable(false);
			// Простое выпадающее меню
			JPopupMenu popup = new JPopupMenu();
			popup.add(new JMenuItem("Новый тест"));
			popup.add(new JMenuItem("Открыть..."));
			popup.addSeparator();
			popup.add(new JMenuItem("Выход"));
			menuButton.addActionListener(e ->
					popup.show(menuButton, 0, menuButton.getHeight())
			);
			topBar.add(menuButton, BorderLayout.WEST);

			// ====== Основная таблица действий ======
			String[] columns = {"Action", "Selector", "Value", "Comment", "Element Type"};
			DefaultTableModel model = new DefaultTableModel(columns, 0);
			JTable table = new JTable(model);
			table.setFillsViewportHeight(true);
			JScrollPane tableScroll = new JScrollPane(table);

			// ====== Нижняя панель «Настройки» ======
			JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			settingsPanel.setBorder(BorderFactory.createTitledBorder("Настройки"));
			settingsPanel.add(new JLabel("Тема:"));
			String[] themes = {"Light", "Dark"};
			JComboBox<String> themeSelect = new JComboBox<>(themes);
			themeSelect.setSelectedItem("Light");
			settingsPanel.add(themeSelect);

			settingsPanel.add(Box.createHorizontalStrut(20));
			settingsPanel.add(new JLabel("ChromeDriver Path:"));
			JTextField driverPath = new JTextField(20);
			settingsPanel.add(driverPath);

			// ====== Сборка окна ======
			Container content = frame.getContentPane();
			content.setLayout(new BorderLayout());
			content.add(topBar, BorderLayout.NORTH);
			content.add(tableScroll, BorderLayout.CENTER);
			content.add(settingsPanel, BorderLayout.SOUTH);

			frame.setVisible(true);
		});
	}
}
