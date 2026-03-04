package ui;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ActionTableContextMenu {

	public interface Callback {
		void deleteSelectedRow();
		void toggleMarkSelectedRow();      // mark / unmark
		void startScenarioFromSelectedRow();
	}

	private final JTable table;
	private final Callback callback;

	public ActionTableContextMenu(JTable table, Callback callback) {
		this.table = table;
		this.callback = callback;
		install();
	}

	private void install() {
		JPopupMenu popup = new JPopupMenu();

		JMenuItem deleteItem = new JMenuItem("Delete");
		deleteItem.addActionListener(e -> callback.deleteSelectedRow());
		popup.add(deleteItem);

		JMenuItem markItem = new JMenuItem("Mark / Unmark");
		markItem.addActionListener(e -> callback.toggleMarkSelectedRow());
		popup.add(markItem);

		JMenuItem startFromHereItem = new JMenuItem("Start scenario from this step");
		startFromHereItem.addActionListener(e -> callback.startScenarioFromSelectedRow());
		popup.add(startFromHereItem);

		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (e.isPopupTrigger()) showPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) showPopup(e);
			}

			private void showPopup(MouseEvent e) {
				int row = table.rowAtPoint(e.getPoint());
				int col = table.columnAtPoint(e.getPoint());

				if (row >= 0 && col >= 0) {
					table.setRowSelectionInterval(row, row);
				} else {
					table.clearSelection();
				}

				popup.show(table, e.getX(), e.getY());
			}
		});
	}
}
