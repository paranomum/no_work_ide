package ui;

import model.UserAction;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ActionTableContextMenu {

	private final JTable table;
	private final Callback callback;
	public ActionTableContextMenu(JTable table, Callback callback) {
		this.table = table;
		this.callback = callback;
		install();
	}

	private void install() {
		JPopupMenu popup = new JPopupMenu();

		JMenuItem deleteItem = new JMenuItem("Удалить");
		deleteItem.addActionListener(e -> callback.deleteSelectedRow());
		popup.add(deleteItem);

		JMenuItem markItem = new JMenuItem("Отметить / снять отметку");
		markItem.addActionListener(e -> callback.toggleMarkSelectedRow());
		popup.add(markItem);

		JMenuItem startFromHereItem =
				new JMenuItem("Запустить сценарий с этого шага");
		startFromHereItem.addActionListener(e -> callback.startScenarioFromSelectedRow());
		popup.add(startFromHereItem);

		JMenuItem playOnlyThisStep =
				new JMenuItem("Выполнить только этот шаг");
		playOnlyThisStep.addActionListener(e -> callback.playOnlyStep());
		popup.add(playOnlyThisStep);

		JMenuItem createMethodItem =
				new JMenuItem("Создать метод из шагов");
		createMethodItem.addActionListener(e -> callback.createMethodFromSelectedSteps());
		popup.add(createMethodItem);

		JMenuItem editCustomMethodItem =
				new JMenuItem("Изменить пользовательский метод");
		editCustomMethodItem.addActionListener(e -> callback.editCustomMethod());
		popup.add(editCustomMethodItem);

		JMenuItem saveCollapseItem =
				new JMenuItem("Сохранить и свернуть пользовательский метод");
		saveCollapseItem.addActionListener(e -> callback.saveAndCollapseCustomMethod());
		popup.add(saveCollapseItem);


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
				int viewRow = table.getSelectedRow();
				int actionColIndex = 1; // "Action"

				boolean isSingleCustomMethodRow = false;
				if (selectedCount == 1 && viewRow >= 0) {
					Object v = table.getValueAt(viewRow, actionColIndex);
					isSingleCustomMethodRow = (v instanceof UserAction ua) && ua == UserAction.CUSTOM_METHOD;
				}

				editCustomMethodItem.setEnabled(isSingleCustomMethodRow);
				saveCollapseItem.setEnabled(isSingleCustomMethodRow);

				// Start scenario: только при одной выбранной строке
				if (selectedCount == 1) {
					startFromHereItem.setEnabled(true);
					startFromHereItem.setToolTipText(null);
					playOnlyThisStep.setEnabled(true);
					playOnlyThisStep.setToolTipText(null);

				} else {
					startFromHereItem.setEnabled(false);
					startFromHereItem.setToolTipText("Doesn't work for multi selected rows");
					playOnlyThisStep.setEnabled(false);
					playOnlyThisStep.setToolTipText("Doesn't work for multi selected rows");
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

	public interface Callback {
		void deleteSelectedRow();

		void toggleMarkSelectedRow();      // mark / unmark

		void startScenarioFromSelectedRow();

		void createMethodFromSelectedSteps();

		void playOnlyStep();

		void editCustomMethod();          // NEW

		void saveAndCollapseCustomMethod(); // NEW
	}
}
