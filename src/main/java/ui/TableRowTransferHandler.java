package ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.datatransfer.*;

public class TableRowTransferHandler extends TransferHandler {

	private static final DataFlavor ROW_FLAVOR =
			new DataFlavor(Integer.class, "Integer Row Index");

	private final JTable table;

	public TableRowTransferHandler(JTable table) {
		this.table = table;
	}

	@Override
	protected Transferable createTransferable(JComponent c) {
		final int row = table.getSelectedRow();
		return new Transferable() {
			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return new DataFlavor[]{ROW_FLAVOR};
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor) {
				return ROW_FLAVOR.equals(flavor);
			}

			@Override
			public Object getTransferData(DataFlavor flavor)
					throws UnsupportedFlavorException {
				if (!isDataFlavorSupported(flavor)) {
					throw new UnsupportedFlavorException(flavor);
				}
				return row;
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
				&& info.isDataFlavorSupported(ROW_FLAVOR)
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
			Integer from = (Integer) t.getTransferData(ROW_FLAVOR);
			if (from == -1 || from == index) {
				return false;
			}

			DefaultTableModel model = (DefaultTableModel) table.getModel();
			int columnCount = model.getColumnCount();
			Object[] rowData = new Object[columnCount];
			for (int col = 0; col < columnCount; col++) {
				rowData[col] = model.getValueAt(from, col);
			}

			model.removeRow(from);
			if (index > from) {
				index--;
			}
			model.insertRow(index, rowData);
			table.getSelectionModel().setSelectionInterval(index, index);
//			table.clearSelection();
			return true;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return false;
	}
}
