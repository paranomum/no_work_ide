package ui;

import dto.BackendRequestDef;
import model.ActionGroup;
import model.ElementType;
import model.UserAction;
import ui.action.CustomMethodsService;
import ui.action.VariablesService;
import ui.action.iqhr_only.FunnelMoveDialog;
import ui.action.iqhr_only.FunnelMoveRequestDef;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ActionMenuCellEditor extends AbstractCellEditor implements TableCellEditor {

	private static final int ACTION_COL_INDEX = 1;
	private static final int VALUE_COL_INDEX = 3;
	private static final int ELEMENT_TYPE_COL_INDEX = 5;

	private final JTable table;
	private final DefaultTableModel tableModel;
	private final Supplier<CustomMethodsService.MethodDef> customMethodSupplier;
	private final Supplier<BackendRequestDef> backendRequestSupplier;

	private final VariablesService variablesService;

	private final JButton editorButton = new JButton();

	private UserAction selectedAction = UserAction.CLICK;
	private int editingRow = -1;

	public ActionMenuCellEditor(
			JTable table,
			DefaultTableModel tableModel,
			Supplier<CustomMethodsService.MethodDef> customMethodSupplier,
			Supplier<BackendRequestDef> backendRequestSupplier,
			VariablesService variablesService
	) {
		this.table = table;
		this.tableModel = tableModel;
		this.customMethodSupplier = customMethodSupplier;
		this.backendRequestSupplier = backendRequestSupplier;
		this.variablesService = variablesService;

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
				if (action == UserAction.MOVE_FULL || action == UserAction.MOVE_TO_JR) {
					FunnelMoveRequestDef currentRequest = getCurrentFunnelMoveRequest(modelRow);

					FunnelMoveRequestDef request = FunnelMoveDialog.showDialog(
							table,
							variablesService,
							currentRequest
					);

					// Нажали "Отмена": action и value в таблице не меняем.
					if (request == null) {
						cancelCellEditing();
						return;
					}

					selectedAction = action;
					tableModel.setValueAt(action, modelRow, ACTION_COL_INDEX);
					tableModel.setValueAt(
							serializeFunnelMoveRequest(request),
							modelRow,
							VALUE_COL_INDEX
					);

					stopEditing();
					return;
				}

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

	private FunnelMoveRequestDef getCurrentFunnelMoveRequest(int modelRow) {
		Object valueObject = tableModel.getValueAt(modelRow, VALUE_COL_INDEX);

		if (valueObject == null || valueObject.toString().isBlank()) {
			return null;
		}

		try {
			return parseFunnelMoveRequest(valueObject.toString());
		} catch (IllegalArgumentException e) {
			JOptionPane.showMessageDialog(
					table,
					"Не удалось разобрать параметры Funnel Move:\n" + e.getMessage(),
					"Funnel Move",
					JOptionPane.WARNING_MESSAGE
			);
			return null;
		}
	}

	private FunnelMoveRequestDef parseFunnelMoveRequest(String value) {
		Map<String, String> params = new HashMap<>();

		for (String part : value.split(";")) {
			String[] keyValue = part.split("=", 2);

			if (keyValue.length != 2) {
				continue;
			}

			params.put(keyValue[0].trim(), keyValue[1].trim());
		}

		return new FunnelMoveRequestDef(
				params.get("jrId"),
				params.get("candidateId"),
				params.get("vacancyId"),
				params.get("username"),
				params.get("password")
		);
	}

	private String serializeFunnelMoveRequest(FunnelMoveRequestDef request) {
		return "jrId=" + nullToEmpty(request.getJrId())
				+ ";candidateId=" + nullToEmpty(request.getCandidateId())
				+ ";vacancyId=" + nullToEmpty(request.getVacancyId())
				+ ";username=" + nullToEmpty(request.getUsername())
				+ ";password=" + nullToEmpty(request.getPassword());
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}