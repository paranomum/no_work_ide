package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MultiSelectChipsField extends JPanel {

	private static final int H_GAP = 4;
	private static final int V_GAP = 4;
	private static final int FONT_SIZE = 12;
	private static final Dimension POPUP_SIZE = new Dimension(280, 130);
	private static final Dimension INPUT_SIZE = new Dimension(90, 22);
	private static final int MIN_HEIGHT = 32;

	private final List<ChipItem> availableItems = new ArrayList<>();
	private final LinkedHashMap<Long, ChipItem> selectedItems = new LinkedHashMap<>();

	private final JPanel contentPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, H_GAP, V_GAP));
	private final JTextField inputField = new JTextField(10);

	private final DefaultListModel<ChipItem> suggestionModel = new DefaultListModel<>();
	private final JList<ChipItem> suggestionList = new JList<>(suggestionModel);
	private final JScrollPane suggestionScroll = new JScrollPane(suggestionList);
	private final JPopupMenu popup = new JPopupMenu();

	private final AWTEventListener outsideClickListener = event -> {
		if (!(event instanceof MouseEvent mouseEvent)) {
			return;
		}

		if (mouseEvent.getID() != MouseEvent.MOUSE_PRESSED) {
			return;
		}

		if (!popup.isVisible()) {
			return;
		}

		Component clickedComponent = mouseEvent.getComponent();
		if (clickedComponent == null) {
			hidePopup();
			return;
		}

		if (SwingUtilities.isDescendingFrom(clickedComponent, MultiSelectChipsField.this)) {
			return;
		}

		if (SwingUtilities.isDescendingFrom(clickedComponent, popup)) {
			return;
		}

		hidePopup();
	};

	public MultiSelectChipsField(List<ChipItem> items) {
		if (items != null) {
			availableItems.addAll(items);
		}

		setLayout(new BorderLayout());
		setOpaque(true);
		setBackground(Color.WHITE);
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(180, 180, 180)),
				new EmptyBorder(4, 6, 4, 6)
		));

		contentPanel.setOpaque(false);

		inputField.setBorder(null);
		inputField.setOpaque(false);
		inputField.setFont(inputField.getFont().deriveFont(Font.PLAIN, (float) FONT_SIZE));
		inputField.setPreferredSize(INPUT_SIZE);
		inputField.setMinimumSize(INPUT_SIZE);

		suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		suggestionList.setCellRenderer(new SuggestionRenderer());
		suggestionScroll.setBorder(BorderFactory.createEmptyBorder());
		suggestionScroll.setPreferredSize(POPUP_SIZE);

		popup.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)));
		popup.setFocusable(false);
		popup.add(suggestionScroll);

		add(contentPanel, BorderLayout.CENTER);

		bindEvents();
		refreshChips();
		refreshSuggestions("");
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension size = super.getPreferredSize();
		size.height = Math.max(size.height, MIN_HEIGHT);
		return size;
	}

	@Override
	public Dimension getMinimumSize() {
		Dimension size = super.getMinimumSize();
		size.height = Math.max(size.height, MIN_HEIGHT);
		return size;
	}

	private void bindEvents() {
		Toolkit.getDefaultToolkit().addAWTEventListener(
				outsideClickListener,
				AWTEvent.MOUSE_EVENT_MASK
		);

		addHierarchyListener(e -> {
			if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !isDisplayable()) {
				Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
			}
		});

		inputField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				onInputChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				onInputChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				onInputChanged();
			}
		});

		inputField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				refreshSuggestions(inputField.getText());
				showPopupIfNeeded();
			}
		});

		inputField.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				refreshSuggestions(inputField.getText());
				showPopupIfNeeded();
			}
		});

		contentPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				inputField.requestFocusInWindow();
				refreshSuggestions(inputField.getText());
				showPopupIfNeeded();
			}
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				inputField.requestFocusInWindow();
				refreshSuggestions(inputField.getText());
				showPopupIfNeeded();
			}
		});

		inputField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (!popup.isVisible() && (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP)) {
					refreshSuggestions(inputField.getText());
					showPopupIfNeeded();
				}

				if (popup.isVisible()) {
					if (e.getKeyCode() == KeyEvent.VK_DOWN) {
						int index = suggestionList.getSelectedIndex();
						if (index < suggestionModel.size() - 1) {
							suggestionList.setSelectedIndex(index + 1);
							suggestionList.ensureIndexIsVisible(index + 1);
						}
						e.consume();
						return;
					}

					if (e.getKeyCode() == KeyEvent.VK_UP) {
						int index = suggestionList.getSelectedIndex();
						if (index > 0) {
							suggestionList.setSelectedIndex(index - 1);
							suggestionList.ensureIndexIsVisible(index - 1);
						}
						e.consume();
						return;
					}

					if (e.getKeyCode() == KeyEvent.VK_ENTER) {
						ChipItem selected = suggestionList.getSelectedValue();
						if (selected != null) {
							addSelectedItem(selected);
						}
						e.consume();
						return;
					}

					if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
						hidePopup();
						e.consume();
						return;
					}
				}

				if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE
						&& inputField.getText().isEmpty()
						&& !selectedItems.isEmpty()) {
					List<Long> keys = new ArrayList<>(selectedItems.keySet());
					Long lastKey = keys.get(keys.size() - 1);
					selectedItems.remove(lastKey);
					refreshChips();
					refreshSuggestions(inputField.getText());
					showPopupIfNeeded();
					e.consume();
				}
			}
		});

		suggestionList.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				int index = suggestionList.locationToIndex(e.getPoint());
				if (index >= 0) {
					suggestionList.setSelectedIndex(index);
					ChipItem selected = suggestionList.getSelectedValue();
					if (selected != null) {
						addSelectedItem(selected);
					}
				}
			}
		});
	}

	private void onInputChanged() {
		refreshSuggestions(inputField.getText());
		showPopupIfNeeded();
	}

	private void showPopupIfNeeded() {
		if (suggestionModel.isEmpty()) {
			hidePopup();
			return;
		}

		if (!isShowing() || !inputField.isShowing()) {
			hidePopup();
			return;
		}

		if (!popup.isVisible()) {
			popup.show(inputField, 0, inputField.getHeight() + 4);
		}
	}

	private void hidePopup() {
		popup.setVisible(false);
	}

	private void refreshSuggestions(String filter) {
		suggestionModel.clear();

		String normalized = filter == null ? "" : filter.trim().toLowerCase();

		List<ChipItem> filtered = availableItems.stream()
				.filter(item -> !selectedItems.containsKey(item.getId()))
				.filter(item -> normalized.isBlank() || item.getLabel().toLowerCase().contains(normalized))
				.collect(Collectors.toList());

		for (ChipItem item : filtered) {
			suggestionModel.addElement(item);
		}

		if (!suggestionModel.isEmpty()) {
			suggestionList.setSelectedIndex(0);
		} else {
			hidePopup();
		}
	}

	private void addSelectedItem(ChipItem item) {
		selectedItems.put(item.getId(), item);
		inputField.setText("");
		refreshChips();
		refreshSuggestions("");
		hidePopup();
		inputField.requestFocusInWindow();
	}

	private void removeSelectedItem(Long id) {
		selectedItems.remove(id);
		refreshChips();
		refreshSuggestions(inputField.getText());
		inputField.requestFocusInWindow();
		showPopupIfNeeded();
	}

	private void refreshChips() {
		contentPanel.removeAll();

		for (ChipItem item : selectedItems.values()) {
			contentPanel.add(createChip(item));
		}

		contentPanel.add(inputField);

		contentPanel.revalidate();
		contentPanel.repaint();
		revalidate();
		repaint();

		Container parent = getParent();
		if (parent != null) {
			parent.revalidate();
			parent.repaint();
		}

		Window window = SwingUtilities.getWindowAncestor(this);
		if (window != null) {
			window.repaint();
		}
	}

	private JComponent createChip(ChipItem item) {
		JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
		chip.setBackground(new Color(235, 235, 235));
		chip.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(220, 220, 220)),
				new EmptyBorder(2, 6, 2, 4)
		));

		JLabel label = new JLabel(item.getLabel());
		label.setFont(label.getFont().deriveFont(Font.PLAIN, (float) FONT_SIZE));

		JButton removeBtn = new JButton("×");
		removeBtn.setMargin(new Insets(0, 0, 0, 0));
		removeBtn.setBorder(null);
		removeBtn.setContentAreaFilled(false);
		removeBtn.setFocusPainted(false);
		removeBtn.setFont(removeBtn.getFont().deriveFont(Font.PLAIN, (float) FONT_SIZE));
		removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		removeBtn.addActionListener(e -> removeSelectedItem(item.getId()));

		chip.add(label);
		chip.add(removeBtn);
		return chip;
	}

	public List<Long> getSelectedIds() {
		return new ArrayList<>(selectedItems.keySet());
	}

	public List<ChipItem> getSelectedItems() {
		return new ArrayList<>(selectedItems.values());
	}

	public void setSelectedIds(List<Long> ids) {
		selectedItems.clear();

		if (ids != null) {
			for (Long id : ids) {
				availableItems.stream()
						.filter(item -> Objects.equals(item.getId(), id))
						.findFirst()
						.ifPresent(item -> selectedItems.put(item.getId(), item));
			}
		}

		refreshChips();
		refreshSuggestions(inputField.getText());
		hidePopup();
	}

	private static class SuggestionRenderer extends DefaultListCellRenderer {
		@Override
		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus
		) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			if (value instanceof ChipItem item) {
				label.setText(item.getLabel());
				label.setBorder(new EmptyBorder(4, 8, 4, 8));
				label.setFont(label.getFont().deriveFont(Font.PLAIN, (float) FONT_SIZE));
			}

			return label;
		}
	}

	private static class WrapLayout extends FlowLayout {

		public WrapLayout() {
			super();
		}

		public WrapLayout(int align) {
			super(align);
		}

		public WrapLayout(int align, int hgap, int vgap) {
			super(align, hgap, vgap);
		}

		@Override
		public Dimension preferredLayoutSize(Container target) {
			return layoutSize(target, true);
		}

		@Override
		public Dimension minimumLayoutSize(Container target) {
			Dimension minimum = layoutSize(target, false);
			minimum.width -= (getHgap() + 1);
			return minimum;
		}

		private Dimension layoutSize(Container target, boolean preferred) {
			synchronized (target.getTreeLock()) {
				int targetWidth = target.getSize().width;

				Container container = target;
				while (targetWidth == 0 && container.getParent() != null) {
					container = container.getParent();
					targetWidth = container.getSize().width;
				}

				if (targetWidth == 0) {
					targetWidth = 300;
				}

				Insets insets = target.getInsets();
				int horizontalInsetsAndGap = insets.left + insets.right + (getHgap() * 2);
				int maxWidth = targetWidth - horizontalInsetsAndGap;

				Dimension dim = new Dimension(0, 0);
				int rowWidth = 0;
				int rowHeight = 0;

				int nmembers = target.getComponentCount();

				for (int i = 0; i < nmembers; i++) {
					Component m = target.getComponent(i);

					if (!m.isVisible()) {
						continue;
					}

					Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

					if (rowWidth + d.width > maxWidth && rowWidth > 0) {
						addRow(dim, rowWidth, rowHeight);
						rowWidth = 0;
						rowHeight = 0;
					}

					if (rowWidth != 0) {
						rowWidth += getHgap();
					}

					rowWidth += d.width;
					rowHeight = Math.max(rowHeight, d.height);
				}

				addRow(dim, rowWidth, rowHeight);

				dim.width += horizontalInsetsAndGap;
				dim.height += insets.top + insets.bottom + (getVgap() * 2);

				Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
				if (scrollPane != null && target.isValid()) {
					dim.width -= (getHgap() + 1);
				}

				return dim;
			}
		}

		private void addRow(Dimension dim, int rowWidth, int rowHeight) {
			dim.width = Math.max(dim.width, rowWidth);

			if (dim.height > 0) {
				dim.height += getVgap();
			}

			dim.height += rowHeight;
		}
	}

	public void setAvailableItems(List<ChipItem> items) {
		availableItems.clear();
		if (items != null) {
			availableItems.addAll(items);
		}
		selectedItems.entrySet().removeIf(entry ->
				availableItems.stream().noneMatch(item -> Objects.equals(item.getId(), entry.getKey()))
		);
		refreshChips();
		refreshSuggestions(inputField.getText());
	}
}