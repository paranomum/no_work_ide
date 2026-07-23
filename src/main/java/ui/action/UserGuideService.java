package ui.action;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserGuideService {

	private static final String DOCS_INDEX = "/docs/index.txt";

	private final Parser parser;
	private final HtmlRenderer renderer;

	private JDialog dialog;

	public UserGuideService() {
		MutableDataSet options = new MutableDataSet();

		options.set(Parser.EXTENSIONS, List.of(
				TablesExtension.create(),
				StrikethroughExtension.create(),
				TaskListExtension.create(),
				AutolinkExtension.create()
		));

		options.set(TablesExtension.COLUMN_SPANS, false);
		options.set(TablesExtension.APPEND_MISSING_COLUMNS, true);
		options.set(TablesExtension.DISCARD_EXTRA_COLUMNS, true);
		options.set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true);

		this.parser = Parser.builder(options).build();
		this.renderer = HtmlRenderer.builder(options).build();
	}

	public void openUserGuideDialog(Window parent) {
		SwingUtilities.invokeLater(() -> {
			if (dialog != null && dialog.isDisplayable()) {
				dialog.toFront();
				dialog.requestFocus();
				return;
			}

			dialog = new JDialog();
			dialog.setTitle("Руководство пользователя");
			dialog.setModalityType(Dialog.ModalityType.MODELESS);
			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			dialog.setSize(1100, 700);
			dialog.setLocationRelativeTo(parent);
			dialog.setLayout(new BorderLayout());

			dialog.addWindowListener(new java.awt.event.WindowAdapter() {
				@Override
				public void windowClosed(java.awt.event.WindowEvent e) {
					dialog = null;
				}
			});

			List<UserGuideSection> sections = loadSections();

			DefaultListModel<UserGuideSection> listModel = new DefaultListModel<>();
			for (UserGuideSection section : sections) {
				listModel.addElement(section);
			}

			JList<UserGuideSection> sectionsList = new JList<>(listModel);
			sectionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			sectionsList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
			sectionsList.setFixedCellHeight(28);

			JScrollPane leftScroll = new JScrollPane(sectionsList);
			leftScroll.setPreferredSize(new Dimension(260, 700));

			JEditorPane contentPane = createContentPane();
			JScrollPane rightScroll = new JScrollPane(contentPane);
			rightScroll.getVerticalScrollBar().setUnitIncrement(16);

			JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
			splitPane.setDividerLocation(260);
			splitPane.setResizeWeight(0.0);

			sectionsList.addListSelectionListener((ListSelectionEvent e) -> {
				if (e.getValueIsAdjusting()) {
					return;
				}

				UserGuideSection selected = sectionsList.getSelectedValue();
				if (selected == null) {
					setHtml(contentPane, wrapHtml("<p class='muted'>Раздел не выбран</p>"), null);
					return;
				}

				loadSectionAsync(selected, contentPane);
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
				setHtml(contentPane, wrapHtml("<p class='muted'>Документация не найдена</p>"), null);
			}

			dialog.setVisible(true);
		});
	}

	private JEditorPane createContentPane() {
		JEditorPane contentPane = new JEditorPane();
		contentPane.setEditable(false);
		contentPane.setContentType("text/html");
		contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

		HTMLEditorKit editorKit = new HTMLEditorKit();
		StyleSheet css = new StyleSheet();
		css.addStyleSheet(editorKit.getStyleSheet());

		css.addRule("body { " +
				"font-family: 'Segoe UI'; " +
				"font-size: 14pt; " +
				"color: #1f2328; " +
				"background-color: #ffffff; " +
				"margin: 18px 22px; " +
				"line-height: 1.55; " +
				"}");

		css.addRule("h1 { " +
				"font-size: 24pt; " +
				"font-weight: bold; " +
				"color: #0f172a; " +
				"margin-top: 6px; " +
				"margin-bottom: 14px; " +
				"padding-bottom: 6px; " +
				"border-bottom: 1px solid #d8dee4; " +
				"}");

		css.addRule("h2 { " +
				"font-size: 19pt; " +
				"font-weight: bold; " +
				"color: #111827; " +
				"margin-top: 22px; " +
				"margin-bottom: 10px; " +
				"padding-bottom: 4px; " +
				"border-bottom: 1px solid #e5e7eb; " +
				"}");

		css.addRule("h3 { " +
				"font-size: 16pt; " +
				"font-weight: bold; " +
				"color: #111827; " +
				"margin-top: 18px; " +
				"margin-bottom: 8px; " +
				"}");

		css.addRule("p { " +
				"margin-top: 8px; " +
				"margin-bottom: 10px; " +
				"}");

		css.addRule("ul { " +
				"margin-top: 6px; " +
				"margin-bottom: 12px; " +
				"margin-left: 24px; " +
				"}");

		css.addRule("ol { " +
				"margin-top: 6px; " +
				"margin-bottom: 12px; " +
				"margin-left: 28px; " +
				"}");

		css.addRule("li { " +
				"margin-top: 4px; " +
				"margin-bottom: 4px; " +
				"}");

		css.addRule("blockquote { " +
				"margin-top: 12px; " +
				"margin-bottom: 12px; " +
				"margin-left: 8px; " +
				"padding-left: 12px; " +
				"color: #57606a; " +
				"border-left: 4px solid #d0d7de; " +
				"}");

		css.addRule("pre { " +
				"font-family: 'Consolas'; " +
				"font-size: 12pt; " +
				"background-color: #f6f8fa; " +
				"color: #1f2328; " +
				"margin-top: 10px; " +
				"margin-bottom: 14px; " +
				"padding: 10px; " +
				"border: 1px solid #d0d7de; " +
				"}");

		css.addRule("code { " +
				"font-family: 'Consolas'; " +
				"font-size: 12pt; " +
				"background-color: #f6f8fa; " +
				"padding: 2px 4px; " +
				"}");

		css.addRule("a { " +
				"color: #0969da; " +
				"text-decoration: underline; " +
				"}");

		css.addRule("hr { " +
				"color: #d0d7de; " +
				"margin-top: 18px; " +
				"margin-bottom: 18px; " +
				"}");

		css.addRule("img { " +
				"margin-top: 12px; " +
				"margin-bottom: 12px; " +
				"}");

		css.addRule("table { " +
				"margin-top: 14px; " +
				"margin-bottom: 16px; " +
				"border-collapse: collapse; " +
				"}");

		css.addRule("th { " +
				"background-color: #eaf2ff; " +
				"color: #0f172a; " +
				"font-weight: bold; " +
				"padding: 8px 10px; " +
				"border: 1px solid #b8c7db; " +
				"}");

		css.addRule("td { " +
				"background-color: #ffffff; " +
				"padding: 8px 10px; " +
				"border: 1px solid #d0d7de; " +
				"}");

		css.addRule("tr { " +
				"background-color: #ffffff; " +
				"}");

		css.addRule(".muted { color: #6b7280; }");
		css.addRule(".error { color: #cf222e; font-weight: bold; }");

		editorKit.setStyleSheet(css);
		contentPane.setEditorKit(editorKit);

		contentPane.addHyperlinkListener(e -> {
			if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
				return;
			}

			try {
				if (e.getURL() != null) {
					openInBrowser(e.getURL().toURI());
				} else if (e.getDescription() != null && !e.getDescription().isBlank()) {
					openInBrowser(URI.create(e.getDescription()));
				}
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(
						dialog,
						"Не удалось открыть ссылку: " + ex.getMessage(),
						"Ошибка",
						JOptionPane.ERROR_MESSAGE
				);
			}
		});

		return contentPane;
	}

	private void loadSectionAsync(UserGuideSection section, JEditorPane contentPane) {
		setHtml(contentPane, wrapHtml("<p class='muted'>Загрузка...</p>"), null);

		new SwingWorker<RenderedDoc, Void>() {
			@Override
			protected RenderedDoc doInBackground() {
				String markdown = readResourceAsString(section.getResourcePath());
				String bodyHtml = renderer.render(parser.parse(markdown));
				String fullHtml = wrapHtml(bodyHtml);
				URL baseUrl = resolveBaseUrl(section.getResourcePath());
				return new RenderedDoc(fullHtml, baseUrl);
			}

			@Override
			protected void done() {
				try {
					RenderedDoc rendered = get();
					setHtml(contentPane, rendered.html(), rendered.baseUrl());
					contentPane.setCaretPosition(0);
				} catch (Exception ex) {
					setHtml(
							contentPane,
							wrapHtml("<p class='error'>Не удалось загрузить раздел: "
									+ escapeHtml(ex.getMessage()) + "</p>"),
							null
					);
				}
			}
		}.execute();
	}

	private void setHtml(JEditorPane editorPane, String html, URL baseUrl) {
		HTMLEditorKit kit = (HTMLEditorKit) editorPane.getEditorKit();
		Document doc = kit.createDefaultDocument();

		if (doc instanceof HTMLDocument htmlDoc && baseUrl != null) {
			htmlDoc.setBase(baseUrl);
		}

		editorPane.setDocument(doc);
		editorPane.setText(html);
	}

	private URL resolveBaseUrl(String resourcePath) {
		int idx = resourcePath.lastIndexOf('/');
		String basePath = idx >= 0 ? resourcePath.substring(0, idx + 1) : "/";
		URL url = getClass().getResource(basePath);
		if (url == null) {
			url = getClass().getResource("/");
		}
		return url;
	}

	private void openInBrowser(URI uri) throws Exception {
		if (!Desktop.isDesktopSupported()) {
			throw new UnsupportedOperationException("Desktop API не поддерживается");
		}

		Desktop desktop = Desktop.getDesktop();
		if (!desktop.isSupported(Desktop.Action.BROWSE)) {
			throw new UnsupportedOperationException("BROWSE не поддерживается");
		}

		desktop.browse(uri);
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

	private String wrapHtml(String bodyHtml) {
		return """
                <html>
                <body>
                __BODY__
                </body>
                </html>
                """.replace("__BODY__", bodyHtml);
	}

	private String escapeHtml(String text) {
		return text == null ? "" :
				text.replace("&", "&amp;")
						.replace("<", "&lt;")
						.replace(">", "&gt;")
						.replace("\"", "&quot;");
	}

	private record RenderedDoc(String html, URL baseUrl) {
	}
}