package ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class RowNumberTable extends JTable {

	private final JTable mainTable;

	public RowNumberTable(JTable table) {
		this.mainTable = table;

		// модель с одной колонкой (имя можно пустым оставить)
		DefaultTableModel model = new DefaultTableModel(new Object[]{"#"}, 0);
		setModel(model);

		setFocusable(false);
		setRowSelectionAllowed(false);
		setShowGrid(false);
		setIntercellSpacing(new Dimension(0, 0));

		// ширина как у тебя для других колонок — через columnModel
		getColumnModel().getColumn(0).setPreferredWidth(60);
		getColumnModel().getColumn(0).setMaxWidth(60);

		// рендерер по центру + чуть более тёмный фон
		DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(
					JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {

				super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				setHorizontalAlignment(SwingConstants.CENTER);
				if (!isSelected) {
					setBackground(new Color(230, 230, 230));
				}
				return this;
			}
		};
		setDefaultRenderer(Object.class, renderer);

		// синхронизация количества строк с основной таблицей
		mainTable.getModel().addTableModelListener(e -> syncRowCount());
		syncRowCount();
	}

	private void syncRowCount() {
		DefaultTableModel model = (DefaultTableModel) getModel();
		int rowCount = mainTable.getRowCount();

		model.setRowCount(rowCount);
		for (int i = 0; i < rowCount; i++) {
			model.setValueAt(i, i, 0); // индексы с 0
		}
	}
}
