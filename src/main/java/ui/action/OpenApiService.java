package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.AppConfig;
import ui.ActionWindow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OpenApiService {

	private DefaultTableModel openApiTableModel;
	private JTable openApiTable;
	private final ConfigService configService;
	private final AppConfig config;

	public OpenApiService(ConfigService configService, AppConfig config) {
		this.configService = configService;
		this.config = config;
	}

	// ----- SETTINGS PANEL -----

	public JPanel createOpenApiSettingsPanel(JDialog parentDialog) {
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		String[] cols = {"Service", "OpenAPI URL / file"};
		openApiTableModel = new DefaultTableModel(cols, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return true;
			}
		};
		openApiTable = new JTable(openApiTableModel);

		openApiTable.setRowHeight(24);
		openApiTable.setShowHorizontalLines(true);
		openApiTable.setShowVerticalLines(true);
		openApiTable.setGridColor(new Color(180, 180, 180));
		openApiTable.setIntercellSpacing(new Dimension(1, 1));
		openApiTable.setFillsViewportHeight(true);

		JScrollPane scroll = new JScrollPane(openApiTable);
		scroll.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(new Color(150, 150, 150)),
						BorderFactory.createEmptyBorder(2, 2, 2, 2)
				)
		);
		panel.add(scroll, BorderLayout.CENTER);

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton addBtn = new JButton("+");
		JButton removeBtn = new JButton("-");

		addBtn.addActionListener(e -> openApiTableModel.addRow(new Object[]{"", ""}));
		removeBtn.addActionListener(e -> {
			int row = openApiTable.getSelectedRow();
			if (row >= 0) {
				openApiTableModel.removeRow(row);
			}
		});

		top.add(new JLabel("Services:"));
		top.add(addBtn);
		top.add(removeBtn);
		panel.add(top, BorderLayout.NORTH);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveBtn = new JButton("Save");
		saveBtn.addActionListener(e -> {
			if (openApiTable.isEditing()) {
				openApiTable.getCellEditor().stopCellEditing();
			}
			saveOpenApiSpecs(parentDialog);
		});
		bottom.add(saveBtn);
		panel.add(bottom, BorderLayout.SOUTH);

		loadOpenApiSpecsIntoTable();

		return panel;
	}

	private void loadOpenApiSpecsIntoTable() {
		openApiTableModel.setRowCount(0);
		try {
			Path specFile = configService.getOpenApiSpecsFile(config);
			if (!Files.exists(specFile)) {
				return;
			}
			String json = Files.readString(specFile);
			Gson gson = new GsonBuilder().create();
			dto.OpenApiServiceSpec[] arr =
					gson.fromJson(json, dto.OpenApiServiceSpec[].class);
			if (arr != null) {
				for (dto.OpenApiServiceSpec s : arr) {
					openApiTableModel.addRow(new Object[]{
							s.service != null ? s.service : "",
							s.spec != null ? s.spec : ""
					});
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					null,
					"Failed to load openApiSpec.json: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void saveOpenApiSpecs(JDialog dialog) {
		try {
			int rows = openApiTableModel.getRowCount();
			java.util.List<dto.OpenApiServiceSpec> list = new java.util.ArrayList<>();
			for (int r = 0; r < rows; r++) {
				String service = (String) openApiTableModel.getValueAt(r, 0);
				String spec = (String) openApiTableModel.getValueAt(r, 1);
				if ((service != null && !service.isBlank()) ||
						(spec != null && !spec.isBlank())) {
					list.add(new dto.OpenApiServiceSpec(
							service != null ? service.trim() : "",
							spec != null ? spec.trim() : ""
					));
				}
			}

			Path specFile = configService.getOpenApiSpecsFile(config);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			String json = gson.toJson(list.toArray(new dto.OpenApiServiceSpec[0]));
			Files.writeString(specFile, json, StandardCharsets.UTF_8);

			JOptionPane.showMessageDialog(
					dialog,
					"OpenAPI specs saved to:\n" + specFile.toAbsolutePath(),
					"Saved",
					JOptionPane.INFORMATION_MESSAGE
			);
		} catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					dialog,
					"Failed to save openApiSpec.json: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private java.util.Map<String, String> loadOpenApiSpecsMap() {
		java.util.Map<String, String> map = new java.util.HashMap<>();
		try {
			Path specFile = configService.getOpenApiSpecsFile(config);
			if (!Files.exists(specFile)) {
				return map;
			}
			String json = Files.readString(specFile);
			Gson gson = new GsonBuilder().create();
			dto.OpenApiServiceSpec[] arr =
					gson.fromJson(json, dto.OpenApiServiceSpec[].class);
			if (arr != null) {
				for (dto.OpenApiServiceSpec s : arr) {
					if (s.service != null && s.spec != null) {
						map.put(s.service, s.spec);
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return map;
	}

	private File extractOpenApiGeneratorJar() throws IOException {
		String resourcePath = "openapi/openapi-generator-cli.jar";
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new IOException("Resource not found: " + resourcePath);
			}

			Path configDir = configService.loadConfigDir();
			Path targetDir = configDir.resolve("openapi");
			if (!Files.exists(targetDir)) {
				Files.createDirectories(targetDir);
			}

			Path target = targetDir.resolve("openapi-generator-cli.jar");
			Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			return target.toFile();
		}
	}

	private void runOpenApiGeneratorWithEmbeddedJar(
													ActionWindow actionWindow,
													String specLocation,
													String outDirPath) {
		try {
			File generatorJar = extractOpenApiGeneratorJar();
			File outDir = new File(outDirPath);
			if (!outDir.exists() && !outDir.mkdirs()) {
				JOptionPane.showMessageDialog(
						actionWindow,
						"Failed to create output directory: " + outDir.getAbsolutePath(),
						"IO error",
						JOptionPane.ERROR_MESSAGE
				);
				return;
			}

			Path configDir = configService.loadConfigDir();

			java.util.List<String> command = new java.util.ArrayList<>();
			command.add("java");
			command.add("-jar");
			command.add(generatorJar.getAbsolutePath());
			command.add("generate");
			command.add("-g");
			command.add("java");
			command.add("-l");
			command.add("webclient");
			command.add("-i");
			command.add(specLocation);
			command.add("--skip-validate-spec");
			command.add("-o");
			command.add(outDir.getAbsolutePath());

			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(configDir.toFile());
			pb.redirectErrorStream(true);

			JDialog waitDialog = new JDialog(actionWindow, "Generating ApiClient", false);
			waitDialog.setLayout(new BorderLayout(10, 10));
			waitDialog.add(new JLabel("Please wait, generating client..."), BorderLayout.CENTER);
			waitDialog.setSize(300, 100);
			waitDialog.setLocationRelativeTo(actionWindow);
			waitDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			waitDialog.setResizable(false);
			waitDialog.setVisible(true);

			new Thread(() -> {
				try {
					Process process = pb.start();

					try (BufferedReader reader = new BufferedReader(
							new InputStreamReader(process.getInputStream()))) {
						String line;
						while ((line = reader.readLine()) != null) {
							System.out.println("[openapi-generator] " + line);
						}
					}

					int exitCode = process.waitFor();
					SwingUtilities.invokeLater(() -> {
						waitDialog.dispose();
						if (exitCode == 0) {
							JOptionPane.showMessageDialog(
									actionWindow,
									"ApiClient generated into:\n" + outDir.getAbsolutePath(),
									"Generation complete",
									JOptionPane.INFORMATION_MESSAGE
							);
						} else {
							JOptionPane.showMessageDialog(
									actionWindow,
									"Generator exited with code " + exitCode,
									"Generation failed",
									JOptionPane.ERROR_MESSAGE
							);
						}
					});
				} catch (Exception ex) {
					ex.printStackTrace();
					SwingUtilities.invokeLater(() -> {
						waitDialog.dispose();
						JOptionPane.showMessageDialog(
								actionWindow,
								"Failed to run generator: " + ex.getMessage(),
								"Error",
								JOptionPane.ERROR_MESSAGE
						);
					});
				}
			}, "openapi-generator-thread").start();

		} catch (Exception ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
					actionWindow,
					"Unexpected error: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE
			);
		}
	}

	public void openGenerateApiClientDialog(ActionWindow actionWindow) {
		java.util.Map<String, String> specs = loadOpenApiSpecsMap();
		if (specs.isEmpty()) {
			JOptionPane.showMessageDialog(
					actionWindow,
					"OpenAPI specs list is empty. Please configure it in Settings -> OpenApi.",
					"No specs",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		JDialog dialog = new JDialog(actionWindow, "Generate ApiClient", true);
		dialog.setSize(480, 220);
		dialog.setLocationRelativeTo(actionWindow);
		dialog.setLayout(new BorderLayout(10, 10));

		JPanel main = new JPanel(new GridBagLayout());
		main.setBorder(new EmptyBorder(10, 10, 10, 10));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// 1. Service
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.0;
		JLabel serviceLabel = new JLabel("Service:");
		main.add(serviceLabel, gbc);

		JComboBox<String> serviceCombo = new JComboBox<>(
				specs.keySet().toArray(new String[0])
		);
		Dimension comboSize = new Dimension(200, serviceCombo.getPreferredSize().height);
		serviceCombo.setPreferredSize(comboSize);
		serviceCombo.setMaximumSize(comboSize);

		gbc.gridx = 1;
		gbc.weightx = 1.0;
		main.add(serviceCombo, gbc);

		// 2. Output folder label + field
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.weightx = 0.0;
		JLabel outputLabel = new JLabel("Output folder:");
		main.add(outputLabel, gbc);

		String userHome = System.getProperty("user.home");
		String os = System.getProperty("os.name").toLowerCase();

		Path desktop = Paths.get(userHome, "Desktop");
		String desktopPath = desktop.toString();

		JTextField outputDirField = new JTextField(30);
		String initialService = (String) serviceCombo.getSelectedItem();
		if (initialService == null || initialService.isBlank()) {
			initialService = "service";
		}

		outputDirField.setText(
				desktopPath + File.separator + "api_clients" + File.separator + initialService
		);

		gbc.gridx = 1;
		gbc.weightx = 1.0;
		main.add(outputDirField, gbc);

		String finalInitialService = initialService;
		serviceCombo.addActionListener(e -> {
			String s = (String) serviceCombo.getSelectedItem();
			if (s == null || s.isBlank()) s = finalInitialService;
			outputDirField.setText(
					desktopPath + File.separator + "api_clients" + File.separator + s
			);
		});

		// 3. Browse button
		gbc.gridx = 1;
		gbc.gridy++;
		gbc.weightx = 0.0;
		gbc.anchor = GridBagConstraints.WEST;
		JButton browseBtn = new JButton("Browse...");
		browseBtn.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setDialogTitle("Select output folder");

			String current = outputDirField.getText().trim();
			if (!current.isEmpty()) {
				File cur = new File(current);
				if (cur.exists()) {
					chooser.setCurrentDirectory(cur);
				}
			}

			int res = chooser.showOpenDialog(dialog);
			if (res == JFileChooser.APPROVE_OPTION) {
				File dir = chooser.getSelectedFile();
				if (dir != null) {
					outputDirField.setText(dir.getAbsolutePath());
				}
			}
		});
		main.add(browseBtn, gbc);

		dialog.add(main, BorderLayout.CENTER);

		// bottom buttons
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton cancelBtn = new JButton("Cancel");
		JButton generateBtn = new JButton("Generate");

		cancelBtn.addActionListener(e -> dialog.dispose());
		generateBtn.addActionListener(e -> {
			String serviceName = (String) serviceCombo.getSelectedItem();
			if (serviceName == null || serviceName.isBlank()) {
				return;
			}

			String specLocation = specs.get(serviceName);
			if (specLocation == null || specLocation.isBlank()) {
				JOptionPane.showMessageDialog(
						dialog,
						"Spec URL for service is empty. Please check Settings -> OpenApi.",
						"Spec missing",
						JOptionPane.WARNING_MESSAGE
				);
				return;
			}

			String outDirPath = outputDirField.getText().trim();
			if (outDirPath.isEmpty()) {
				JOptionPane.showMessageDialog(
						dialog,
						"Output folder must not be empty",
						"Validation error",
						JOptionPane.WARNING_MESSAGE
				);
				return;
			}

			dialog.dispose();
			runOpenApiGeneratorWithEmbeddedJar(actionWindow, specLocation, outDirPath);
		});

		buttons.add(cancelBtn);
		buttons.add(generateBtn);
		dialog.add(buttons, BorderLayout.SOUTH);

		dialog.setVisible(true);
	}
}