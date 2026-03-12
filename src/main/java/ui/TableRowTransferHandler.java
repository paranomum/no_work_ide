package ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.Arrays;

public class TableRowTransferHandler extends TransferHandler {

	private static final DataFlavor ROWS_FLAVOR =
			new DataFlavor(int[].class, "Integer Row Indices");

	private final JTable table;
	private final ActionWindow window;

	public TableRowTransferHandler(JTable table, ActionWindow window) {
		this.table = table;
		this.window = window;
	}

	@Override
	protected Transferable createTransferable(JComponent c) {
		// берём ВСЕ выделенные строки (вью-индексы)
		int[] selected = table.getSelectedRows();
		if (selected == null || selected.length == 0) {
			return null;
		}
		// копируем, чтобы не трогать внутренний массив
		int[] rows = Arrays.copyOf(selected, selected.length);
		// сортируем, чтобы «самый верхний идёт первым»
		Arrays.sort(rows);

		return new Transferable() {
			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return new DataFlavor[]{ROWS_FLAVOR};
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor) {
				return ROWS_FLAVOR.equals(flavor);
			}

			@Override
			public Object getTransferData(DataFlavor flavor)
					throws UnsupportedFlavorException, IOException {
				if (!isDataFlavorSupported(flavor)) {
					throw new UnsupportedFlavorException(flavor);
				}
				return rows;
			}
		};
	}

	@Override
	public int getSourceActions(JComponent c) {
		return MOVE;
	}

	@Override
	public boolean canImport(TransferSupport info) {
		return info.isDrop()
				&& info.isDataFlavorSupported(ROWS_FLAVOR)
				&& info.getComponent() == table;
	}

	@Override
	public boolean importData(TransferSupport info) {
		if (!canImport(info)) {
			return false;
		}

		JTable.DropLocation dl = (JTable.DropLocation) info.getDropLocation();
		int index = dl.getRow();
		int max = table.getModel().getRowCount();
		if (index < 0 || index > max) {
			index = max;
		}

		try {
			Transferable t = info.getTransferable();
			int[] fromRows = (int[]) t.getTransferData(ROWS_FLAVOR);
			if (fromRows == null || fromRows.length == 0) {
				return false;
			}

			// если дропнули внутрь того же самого диапазона — ничего не делаем
			int minFrom = fromRows[0];
			int maxFrom = fromRows[fromRows.length - 1] + 1;
			if (index >= minFrom && index <= maxFrom) {
				return false;
			}

			DefaultTableModel model = (DefaultTableModel) table.getModel();
			int columnCount = model.getColumnCount();

			// сохраняем данные всех переносимых строк
			Object[][] rowsData = new Object[fromRows.length][columnCount];
			for (int i = 0; i < fromRows.length; i++) {
				int row = fromRows[i];
				for (int col = 0; col < columnCount; col++) {
					rowsData[i][col] = model.getValueAt(row, col);
				}
			}

			// удаляем строки, двигаясь снизу вверх,
			// чтобы индексы выше не «съехали»
			for (int i = fromRows.length - 1; i >= 0; i--) {
				model.removeRow(fromRows[i]);
				// если точка вставки была ниже удаляемой строки — она смещается на 1 вверх
				if (fromRows[i] < index) {
					index--;
				}
			}

			// вставляем пачкой, сохраняя порядок (верхняя — первая)
			int insertIndex = index;
			for (Object[] rowData : rowsData) {
				model.insertRow(insertIndex, rowData);
				insertIndex++;
			}

			// выделяем перемещённый блок
			table.getSelectionModel().setSelectionInterval(index, insertIndex - 1);

			// регистрируем операцию для undo/redo (можно сохранить весь диапазон)
			window.pushMoveUndo(minFrom, index, rowsData);

			return true;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return false;
	}
}
