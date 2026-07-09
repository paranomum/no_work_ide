package ui;

import api.jaga.dto.SearchItems;
import api.jaga.dto.SearchRequestDto;
import api.jaga.dto.SearchResultDto;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class TaskSearchComboBox extends JPanel {

	private static final int MIN_SEARCH_LENGTH = 2;
	private static final int SEARCH_PAGE = 0;
	private static final int SEARCH_SIZE = 10;
	private static final int MAX_TEXT_LENGTH = 50;
	private static final int SEARCH_DELAY_MS = 400;

	private final JComboBox<TaskOption> comboBox;
	private final DefaultComboBoxModel<TaskOption> model;
	private final JTextField editor;
	private final JButton clearButton;
	private final Timer debounceTimer;

	private final Long projectId;
	private final Function<SearchRequestDto, SearchResultDto> searchFunction;

	private boolean suppressEditorEvents = false;
	private boolean suppressComboEvents = false;
	private boolean clearingNow = false;
	private String lastSearchText = "";
	private TaskOption selectedTaskOption;

	public TaskSearchComboBox(
			Long projectId,
			Function<SearchRequestDto, SearchResultDto> searchFunction
	) {
		this.projectId = projectId;
		this.searchFunction = searchFunction;

		setLayout(new BorderLayout(6, 0));

		model = new DefaultComboBoxModel<>();
		comboBox = new JComboBox<>(model);
		comboBox.setEditable(true);

		editor = (JTextField) comboBox.getEditor().getEditorComponent();
		clearButton = new JButton("✕");
		debounceTimer = new Timer(SEARCH_DELAY_MS, e -> triggerSearch());

		debounceTimer.setRepeats(false);

		clearButton.setMargin(new Insets(0, 8, 0, 8));
		clearButton.setFocusable(false);
		clearButton.setToolTipText("Очистить");
		clearButton.setEnabled(false);
		clearButton.addActionListener(e -> clearSelection());

		comboBox.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(
					JList<?> list,
					Object value,
					int index,
					boolean isSelected,
					boolean cellHasFocus
			) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

				if (value instanceof TaskOption option) {
					setText(option.getDisplayText());
				} else {
					setText(value == null ? "" : trimToMax(String.valueOf(value)));
				}

				return this;
			}
		});

		editor.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				onTextChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				onTextChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				onTextChanged();
			}
		});

		comboBox.addItemListener(e -> {
			if (clearingNow || suppressComboEvents) {
				return;
			}

			if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() instanceof TaskOption option) {
				selectedTaskOption = option;
				setEditorText(option.getDisplayText());
				updateClearButtonState();
			}
		});

		comboBox.addActionListener(e -> {
			if (clearingNow || suppressComboEvents) {
				return;
			}

			Object selected = comboBox.getSelectedItem();
			if (selected instanceof TaskOption option) {
				selectedTaskOption = option;
				setEditorText(option.getDisplayText());
			} else {
				TaskOption matched = findOptionByCurrentText();
				if (matched != null) {
					setSelectedOption(matched);
				}
			}

			updateClearButtonState();
		});

		add(comboBox, BorderLayout.CENTER);
		add(clearButton, BorderLayout.EAST);

		updateClearButtonState();
	}

	private void onTextChanged() {
		if (suppressEditorEvents || clearingNow) {
			return;
		}

		String currentText = safe(editor.getText());

		if (currentText.isBlank()) {
			selectedTaskOption = null;
			lastSearchText = "";
			updateClearButtonState();
			debounceTimer.stop();
			return;
		}

		if (selectedTaskOption != null && matchesByCode(currentText, selectedTaskOption)) {
			updateClearButtonState();
			return;
		}

		TaskOption matched = findOptionByText(currentText);
		if (matched != null) {
			selectedTaskOption = matched;
			updateClearButtonState();
			return;
		}

		selectedTaskOption = null;
		updateClearButtonState();
		debounceTimer.restart();
	}

	private void triggerSearch() {
		String text = safe(editor.getText());

		if (text.length() < MIN_SEARCH_LENGTH) {
			lastSearchText = "";
			clearLoadedItemsOnly();
			return;
		}

		if (Objects.equals(text, lastSearchText)) {
			return;
		}

		lastSearchText = text;
		searchAsync(text);
	}

	private void searchAsync(String text) {
		comboBox.setEnabled(false);
		clearButton.setEnabled(false);

		new SwingWorker<List<TaskOption>, Void>() {
			@Override
			protected List<TaskOption> doInBackground() {
				SearchRequestDto requestDto = new SearchRequestDto();
				requestDto.setSearchText(text);

				SearchResultDto response = searchFunction.apply(requestDto);

				List<TaskOption> result = new ArrayList<>();
				if (response == null || response.getContent() == null) {
					return result;
				}

				for (SearchItems item : response.getContent()) {
					if (item == null || item.getId() == null) {
						continue;
					}

					result.add(new TaskOption(
							item.getId(),
							safe(item.getCode()),
							safe(item.getTitle())
					));
				}

				return result;
			}

			@Override
			protected void done() {
				try {
					List<TaskOption> items = get();

					suppressComboEvents = true;
					try {
						model.removeAllElements();
						for (TaskOption item : items) {
							model.addElement(item);
						}

						TaskOption matched = selectedTaskOption != null
								? findOptionByCode(selectedTaskOption.getCode())
								: findOptionByText(text);

						comboBox.setEnabled(true);

						if (matched != null) {
							setSelectedOption(matched);
						} else {
							selectedTaskOption = null;
							comboBox.setSelectedItem(null);
							setEditorText(text);
						}
					} finally {
						suppressComboEvents = false;
					}

					if (!items.isEmpty()) {
						comboBox.showPopup();
					}
				} catch (Exception ex) {
					comboBox.setEnabled(true);
					clearLoadedItemsOnly();
				} finally {
					updateClearButtonState();
				}
			}
		}.execute();
	}

	public void clearSelection() {
		clearingNow = true;
		try {
			debounceTimer.stop();
			lastSearchText = "";
			selectedTaskOption = null;

			suppressComboEvents = true;
			try {
				model.removeAllElements();
				comboBox.hidePopup();
				comboBox.setSelectedItem(null);
				setEditorText("");
			} finally {
				suppressComboEvents = false;
			}
		} finally {
			clearingNow = false;
			updateClearButtonState();
		}
	}

	private void clearLoadedItemsOnly() {
		selectedTaskOption = null;

		suppressComboEvents = true;
		try {
			model.removeAllElements();
			comboBox.hidePopup();
			comboBox.setSelectedItem(null);
		} finally {
			suppressComboEvents = false;
		}

		updateClearButtonState();
	}

	private void setSelectedOption(TaskOption option) {
		if (option == null) {
			return;
		}

		selectedTaskOption = option;

		suppressComboEvents = true;
		try {
			comboBox.setSelectedItem(option);
			setEditorText(option.getDisplayText());
		} finally {
			suppressComboEvents = false;
		}
	}

	private TaskOption findOptionByCurrentText() {
		return findOptionByText(editor.getText());
	}

	private TaskOption findOptionByText(String text) {
		return findOptionByCode(extractCode(text));
	}

	private TaskOption findOptionByCode(String code) {
		String normalizedCode = safe(code);
		if (normalizedCode.isBlank()) {
			return null;
		}

		for (int i = 0; i < model.getSize(); i++) {
			TaskOption option = model.getElementAt(i);
			if (option == null) {
				continue;
			}

			if (Objects.equals(safe(option.getCode()), normalizedCode)) {
				return option;
			}
		}

		return null;
	}

	private boolean matchesByCode(String text, TaskOption option) {
		if (option == null) {
			return false;
		}

		return Objects.equals(extractCode(text), safe(option.getCode()));
	}

	private static String extractCode(String text) {
		String normalized = safe(text);
		if (normalized.isBlank()) {
			return "";
		}

		int firstSpace = normalized.indexOf(' ');
		return firstSpace < 0
				? normalized
				: normalized.substring(0, firstSpace).trim();
	}

	private void setEditorText(String text) {
		suppressEditorEvents = true;
		try {
			editor.setText(trimToMax(text));
		} finally {
			suppressEditorEvents = false;
		}
	}

	private void updateClearButtonState() {
		boolean hasText = !safe(editor.getText()).isBlank();
		boolean hasSelection = selectedTaskOption != null;
		clearButton.setEnabled(hasText || hasSelection);
	}

	public Long getSelectedTaskId() {
		TaskOption matched = selectedTaskOption != null ? selectedTaskOption : findOptionByCurrentText();
		return matched == null ? null : matched.getId();
	}

	public String getSelectedDisplayText() {
		TaskOption matched = selectedTaskOption != null ? selectedTaskOption : findOptionByCurrentText();
		return matched == null ? "" : matched.getDisplayText();
	}

	public JComboBox<TaskOption> getComboBox() {
		return comboBox;
	}

	public static int getSearchPage() {
		return SEARCH_PAGE;
	}

	public static int getSearchSize() {
		return SEARCH_SIZE;
	}

	private static String trimToMax(String value) {
		String text = safe(value);
		return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
	}

	private static String safe(String value) {
		return value == null ? "" : value.trim();
	}

	public static class TaskOption {
		private final Long id;
		private final String code;
		private final String title;

		public TaskOption(Long id, String code, String title) {
			this.id = id;
			this.code = code;
			this.title = title;
		}

		public Long getId() {
			return id;
		}

		public String getCode() {
			return code;
		}

		public String getTitle() {
			return title;
		}

		public String getDisplayText() {
			String text = (safe(code) + " " + safe(title)).trim();
			return trimToMax(text);
		}

		@Override
		public String toString() {
			return getDisplayText();
		}
	}
}