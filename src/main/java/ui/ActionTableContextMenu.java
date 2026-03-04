package ui;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ActionTableContextMenu {

	public interface Callback {
		void deleteSelectedRow();
		void toggleMarkSelectedRow();      // mark / unmark
		void startScenarioFromSelectedRow();
		void createMethodFromSelectedSteps();
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

		JMenuItem createMethodItem = new JMenuItem("Create method from steps");
		createMethodItem.addActionListener(e -> callback.createMethodFromSelectedSteps());
		popup.add(createMethodItem);

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
					int[] selected = table.getSelectedRows();
					boolean rowAlreadySelected = false;
					for (int r : selected) {
						if (r == row) {
							rowAlreadySelected = true;
							break;
						}
					}
					if (!rowAlreadySelected) {
						table.setRowSelectionInterval(row, row);
					}
				} else {
					table.clearSelection();
				}

				int selectedCount = table.getSelectedRowCount();

				// Start scenario: только при одной выбранной строке
				if (selectedCount == 1) {
					startFromHereItem.setEnabled(true);
					startFromHereItem.setToolTipText(null);
				} else {
					startFromHereItem.setEnabled(false);
					startFromHereItem.setToolTipText("Doesn't work for multi selected rows");
				}

				// Create method: только если выбрано >= 2 строк
				if (selectedCount >= 2) {
					createMethodItem.setEnabled(true);
					createMethodItem.setToolTipText(null);
				} else {
					createMethodItem.setEnabled(false);
					createMethodItem.setToolTipText("Select at least 2 steps");
				}

				popup.show(table, e.getX(), e.getY());
			}
		});
	}
}
