package ui;

import dto.BackendRequestDef;
import ui.action.BackendRequestsService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class CaptureListDialog extends JDialog {

	private final List<BackendRequestDef> captured;
	private final BackendRequestsService backendRequestsService;

	private JTable listTable;
	private DefaultTableModel listModel;

	public CaptureListDialog(Frame parent,
							 List<BackendRequestDef> captured,
							 BackendRequestsService backendRequestsService) {
		super(parent, "Захваченные запросы", true);
		this.captured = captured;
		this.backendRequestsService = backendRequestsService;
		setSize(820, 440);
		setLocationRelativeTo(parent);
		buildUi();
	}

	private void buildUi() {
		setLayout(new BorderLayout(8, 8));

		JLabel info = new JLabel("Выберите запрос для сохранения:");
		info.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
		add(info, BorderLayout.NORTH);

		String[] cols = {"Method", "URL", "Body (preview)", "Captured at"};
		listModel = new DefaultTableModel(cols, 0) {
			@Override
			public boolean isCellEditable(int r, int c) {
				return false;
			}
		};

		for (BackendRequestDef def : captured) {
			String body = def.getRequestBody();
			String bodyPreview;
			if (body == null || body.isBlank()) {
				bodyPreview = "[no request body]";
			} else {
				String normalized = body.replace("\n", " ").replace("\r", " ");
				bodyPreview = normalized.substring(0, Math.min(120, normalized.length()))
						+ (normalized.length() > 120 ? "..." : "");
			}
			listModel.addRow(new Object[]{def.getMethod(), def.getUrl(), bodyPreview, def.getCapturedAt()});
		}

		listTable = new JTable(listModel);
		listTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		listTable.setRowHeight(22);
		listTable.getColumnModel().getColumn(0).setMaxWidth(90);
		listTable.getColumnModel().getColumn(3).setMaxWidth(180);

		listTable.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2) {
					openSelected();
				}
			}
		});

		add(new JScrollPane(listTable), BorderLayout.CENTER);

		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton openBtn = new JButton("Открыть запрос");
		JButton closeBtn = new JButton("Закрыть");

		openBtn.addActionListener(e -> openSelected());
		closeBtn.addActionListener(e -> dispose());

		btnPanel.add(openBtn);
		btnPanel.add(closeBtn);
		add(btnPanel, BorderLayout.SOUTH);
	}

	private void openSelected() {
		int row = listTable.getSelectedRow();
		if (row < 0) {
			JOptionPane.showMessageDialog(this, "Выберите запрос",
					"Нет выбора", JOptionPane.WARNING_MESSAGE);
			return;
		}
		BackendRequestDef def = captured.get(row);
		CaptureResultDialog resultDlg = new CaptureResultDialog((Frame) getOwner(), def, backendRequestsService);
		resultDlg.setVisible(true);
		// НЕ закрываем список — пользователь возвращается назад и может выбрать следующий
		if (resultDlg.isSaved()) {
			// обновляем строку в таблице, чтобы визуально отметить сохранённый запрос
			listModel.setValueAt("✅ " + def.getMethod(), row, 0);
			listTable.repaint();
		}
	}
}