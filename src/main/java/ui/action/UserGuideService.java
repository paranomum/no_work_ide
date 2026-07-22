package ui.action;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class UserGuideService {

	private static final String DOCS_INDEX = "/docs/index.txt";

	public void openUserGuideDialog(Window parent) {
		JDialog dialog = new JDialog();
		dialog.setTitle("Руководство пользователя");
		dialog.setModalityType(Dialog.ModalityType.MODELESS);
		dialog.setLocationRelativeTo(parent);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setSize(1100, 700);
		dialog.setLocationRelativeTo(parent);
		dialog.setLayout(new BorderLayout());

		List<UserGuideSection> sections = loadSections();

		DefaultListModel<UserGuideSection> listModel = new DefaultListModel<>();
		for (UserGuideSection section : sections) {
			listModel.addElement(section);
		}

		JList<UserGuideSection> sectionsList = new JList<>(listModel);
		sectionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JScrollPane leftScroll = new JScrollPane(sectionsList);
		leftScroll.setPreferredSize(new Dimension(260, 700));

		JEditorPane contentPane = new JEditorPane();
		contentPane.setEditable(false);
		contentPane.setContentType("text/html");
		contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

		JScrollPane rightScroll = new JScrollPane(contentPane);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
		splitPane.setDividerLocation(260);

		sectionsList.addListSelectionListener((ListSelectionEvent e) -> {
			if (e.getValueIsAdjusting()) {
				return;
			}

			UserGuideSection selected = sectionsList.getSelectedValue();
			if (selected == null) {
				contentPane.setText(emptyHtml("Раздел не выбран"));
				return;
			}

			try {
				String markdown = readResourceAsString(selected.getResourcePath());
				String html = renderMarkdownToHtml(markdown, selected.getResourcePath());
				contentPane.setText(html);
				contentPane.setCaretPosition(0);
			} catch (Exception ex) {
				contentPane.setText(errorHtml("Не удалось загрузить раздел: " + ex.getMessage()));
			}
		});

		JButton closeButton = new JButton("Закрыть");
		closeButton.addActionListener(e -> dialog.dispose());

		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bottomPanel.add(closeButton);

		dialog.add(splitPane, BorderLayout.CENTER);
		dialog.add(bottomPanel, BorderLayout.SOUTH);

		if (!sections.isEmpty()) {
			sectionsList.setSelectedIndex(0);
		} else {
			contentPane.setText(emptyHtml("Документация не найдена"));
		}

		dialog.setVisible(true);
	}

	private List<UserGuideSection> loadSections() {
		List<UserGuideSection> result = new ArrayList<>();

		try {
			String indexContent = readResourceAsString(DOCS_INDEX);
			String[] lines = indexContent.split("\\R");

			for (String line : lines) {
				String trimmed = line == null ? "" : line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}

				String[] parts = trimmed.split("\\|", 2);
				if (parts.length != 2) {
					continue;
				}

				String title = parts[0].trim();
				String resourcePath = parts[1].trim();

				if (!resourcePath.startsWith("/")) {
					resourcePath = "/docs/" + resourcePath;
				}

				result.add(new UserGuideSection(title, resourcePath));
			}
		} catch (Exception ex) {
			result.add(new UserGuideSection("Ошибка загрузки", "/docs/error.md"));
		}

		return result;
	}

	private String readResourceAsString(String resourcePath) {
		InputStream stream = getClass().getResourceAsStream(resourcePath);
		if (stream == null) {
			throw new IllegalArgumentException("Resource not found: " + resourcePath);
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read resource: " + resourcePath, e);
		}
	}

	private String renderMarkdownToHtml(String markdown, String currentResourcePath) {
		StringBuilder html = new StringBuilder();
		html.append("<html><body style='font-family:sans-serif; padding:16px;'>");

		String[] lines = markdown.split("\\R");
		boolean inList = false;

		for (String rawLine : lines) {
			String line = Objects.toString(rawLine, "").trim();

			if (line.isEmpty()) {
				if (inList) {
					html.append("</ul>");
					inList = false;
				}
				continue;
			}

			if (line.startsWith("# ")) {
				if (inList) {
					html.append("</ul>");
					inList = false;
				}
				html.append("<h1>").append(escapeHtml(line.substring(2))).append("</h1>");
			} else if (line.startsWith("## ")) {
				if (inList) {
					html.append("</ul>");
					inList = false;
				}
				html.append("<h2>").append(escapeHtml(line.substring(3))).append("</h2>");
			} else if (line.startsWith("### ")) {
				if (inList) {
					html.append("</ul>");
					inList = false;
				}
				html.append("<h3>").append(escapeHtml(line.substring(4))).append("</h3>");
			} else if (line.startsWith("- ")) {
				if (!inList) {
					html.append("<ul>");
					inList = true;
				}
				html.append("<li>").append(renderInline(line.substring(2), currentResourcePath)).append("</li>");
			} else {
				if (inList) {
					html.append("</ul>");
					inList = false;
				}
				if (line.startsWith("![")) {
					html.append(renderImage(line, currentResourcePath));
				} else {
					html.append("<p>").append(renderInline(line, currentResourcePath)).append("</p>");
				}
			}
		}

		if (inList) {
			html.append("</ul>");
		}

		html.append("</body></html>");
		return html.toString();
	}

	private String renderInline(String text, String currentResourcePath) {
		String escaped = escapeHtml(text);

		escaped = escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
		escaped = escaped.replaceAll("\\*(.+?)\\*", "<i>$1</i>");

		return escaped;
	}

	private String renderImage(String markdownLine, String currentResourcePath) {
		int altStart = markdownLine.indexOf('[');
		int altEnd = markdownLine.indexOf(']');
		int pathStart = markdownLine.indexOf('(', altEnd);
		int pathEnd = markdownLine.indexOf(')', pathStart);

		if (altStart < 0 || altEnd < 0 || pathStart < 0 || pathEnd < 0) {
			return "<p>" + escapeHtml(markdownLine) + "</p>";
		}

		String alt = markdownLine.substring(altStart + 1, altEnd).trim();
		String path = markdownLine.substring(pathStart + 1, pathEnd).trim();

		String resolvedPath = resolveImagePath(currentResourcePath, path);
		java.net.URL imageUrl = getClass().getResource(resolvedPath);

		if (imageUrl == null) {
			return "<p style='color:red;'>Картинка не найдена: " + escapeHtml(path) + "</p>";
		}

		return "<div style='margin:12px 0;'>"
				+ "<img src='" + imageUrl + "' alt='" + escapeHtml(alt) + "' style='max-width:100%; height:auto; border:1px solid #ccc;'/>"
				+ "</div>";
	}

	private String resolveImagePath(String currentResourcePath, String imagePath) {
		if (imagePath.startsWith("/")) {
			return imagePath;
		}

		int idx = currentResourcePath.lastIndexOf('/');
		String baseDir = idx >= 0 ? currentResourcePath.substring(0, idx + 1) : "/";
		return baseDir + imagePath;
	}

	private String emptyHtml(String text) {
		return "<html><body style='font-family:sans-serif; padding:16px; color:#666;'>"
				+ "<p>" + escapeHtml(text) + "</p>"
				+ "</body></html>";
	}

	private String errorHtml(String text) {
		return "<html><body style='font-family:sans-serif; padding:16px; color:red;'>"
				+ "<p>" + escapeHtml(text) + "</p>"
				+ "</body></html>";
	}

	private String escapeHtml(String text) {
		return Objects.toString(text, "")
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}
}