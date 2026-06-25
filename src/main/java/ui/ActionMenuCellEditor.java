package ui;

import dto.BackendRequestDef;
import model.ActionGroup;
import model.ElementType;
import model.UserAction;
import ui.action.CustomMethodsService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.function.Supplier;

public class ActionMenuCellEditor extends AbstractCellEditor implements TableCellEditor {

	private static final int ACTION_COL_INDEX = 1;
	private static final int VALUE_COL_INDEX = 3;
	private static final int ELEMENT_TYPE_COL_INDEX = 5;

	private final JTable table;
	private final DefaultTableModel tableModel;
	private final Supplier<CustomMethodsService.MethodDef> customMethodSupplier;
	private final Supplier<BackendRequestDef> backendRequestSupplier;

	private final JButton editorButton = new JButton();

	private UserAction selectedAction = UserAction.CLICK;
	private int editingRow = -1;

	public ActionMenuCellEditor(
			JTable table,
			DefaultTableModel tableModel,
			Supplier<CustomMethodsService.MethodDef> customMethodSupplier,
			Supplier<BackendRequestDef> backendRequestSupplier
	) {
		this.table = table;
		this.tableModel = tableModel;
		this.customMethodSupplier = customMethodSupplier;
		this.backendRequestSupplier = backendRequestSupplier;

		editorButton.setBorderPainted(false);
		editorButton.setFocusPainted(false);
		editorButton.setHorizontalAlignment(SwingConstants.LEFT);

		editorButton.addActionListener(e -> showMenu());
	}

	@Override
	public Object getCellEditorValue() {
		return selectedAction;
	}

	@Override
	public Component getTableCellEditorComponent(
			JTable table,
			Object value,
			boolean isSelected,
			int row,
			int column
	) {
		this.editingRow = row;
		this.selectedAction = resolveAction(value);
		editorButton.setText(selectedAction.getGroup().getCode() + " / " + selectedAction.getCode());
		return editorButton;
	}

	private void showMenu() {
		if (editingRow < 0) {
			return;
		}

		int modelRow = table.convertRowIndexToModel(editingRow);

		JPopupMenu popupMenu = new JPopupMenu();

		popupMenu.add(createGroupMenu(ActionGroup.COMMON, modelRow));
		popupMenu.add(createGroupMenu(ActionGroup.SPEC_ACTIONS, modelRow));
		popupMenu.addSeparator();

		JMenuItem customMethodItem = new JMenuItem(UserAction.CUSTOM_METHOD.getCode());
		customMethodItem.addActionListener(e -> {
			CustomMethodsService.MethodDef method = customMethodSupplier.get();
			if (method != null) {
				selectedAction = UserAction.CUSTOM_METHOD;
				tableModel.setValueAt(UserAction.CUSTOM_METHOD, modelRow, ACTION_COL_INDEX);
				tableModel.setValueAt(method.getName(), modelRow, VALUE_COL_INDEX);
				stopEditing();
			} else {
				cancelCellEditing();
			}
		});
		popupMenu.add(customMethodItem);

		JMenuItem backendMethodItem = new JMenuItem(UserAction.USE_BACKEND_METHOD.getCode());
		backendMethodItem.addActionListener(e -> {
			BackendRequestDef request = backendRequestSupplier.get();
			if (request != null) {
				selectedAction = UserAction.USE_BACKEND_METHOD;
				tableModel.setValueAt(UserAction.USE_BACKEND_METHOD, modelRow, ACTION_COL_INDEX);
				tableModel.setValueAt(request.getName(), modelRow, VALUE_COL_INDEX);
				stopEditing();
			} else {
				cancelCellEditing();
			}
		});
		popupMenu.add(backendMethodItem);

		popupMenu.show(editorButton, 0, editorButton.getHeight());
	}

	private JMenu createGroupMenu(ActionGroup group, int modelRow) {
		JMenu menu = new JMenu(group.getCode());

		for (UserAction action : UserAction.byGroup(group)) {
			if (action == UserAction.CUSTOM_METHOD || action == UserAction.USE_BACKEND_METHOD) {
				continue;
			}

			JMenuItem item = new JMenuItem(action.getCode());
			item.addActionListener(e -> {
				selectedAction = action;
				tableModel.setValueAt(action, modelRow, ACTION_COL_INDEX);
				applyElementTypeIfNeeded(action, modelRow);
				stopEditing();
			});
			menu.add(item);
		}

		return menu;
	}

	private void applyElementTypeIfNeeded(UserAction action, int modelRow) {
		if (action.getGroup() != ActionGroup.COMMON) {
			return;
		}

		ElementType elementType = switch (action) {
			case CLICK -> ElementType.BUTTON;
			case FILL, CLEAR -> ElementType.FIELD;
			case FILL_DATE -> ElementType.DATE_PICKER;
			default -> ElementType.SELECT;
		};

		tableModel.setValueAt(elementType, modelRow, ELEMENT_TYPE_COL_INDEX);
	}

	private UserAction resolveAction(Object value) {
		if (value instanceof UserAction action) {
			return action;
		}
		if (value instanceof String str && !str.isBlank()) {
			try {
				return UserAction.fromCode(str);
			} catch (Exception ignored) {
			}
		}
		return UserAction.CLICK;
	}

	private void stopEditing() {
		fireEditingStopped();
	}
}