package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.AppConfig;
import dto.JagaUserSettings;
import util.SimpleSecretService;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class ConfigService {
	private static final String CONFIG_FILE_NAME = "settings.json";
	private static final String OPENAPI_SPECS_FILE_NAME = "openApiSpec.json";
	private static final String CUSTOM_METHODS_FILE_NAME = "customMethods.json";
	private static final String USERS_FILE_NAME = "users.json";
	//	private static final String BACKEND_REQUESTS_FILE_NAME = "backendRequests.json";
	private static final String DEFAULT_TRUSTSTORE_FILE_NAME = "custom-cacerts.jks";
	private static final String JAGA_USER_SETTINGS_FILE_NAME = "jagaUserSettings.json";
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private Path getConfigDir() throws IOException {
		String appName = "TestRecorder";
		String os = System.getProperty("os.name").toLowerCase();
		String base;

		if (os.contains("win")) {
			base = System.getenv("APPDATA");
			if (base == null) base = System.getProperty("user.home");
			return Paths.get(base, appName);
		} else if (os.contains("mac")) {
			base = System.getProperty("user.home");
			return Paths.get(base, "Library", "Application Support", appName);
		} else {
			base = System.getProperty("user.home");
			return Paths.get(base, "." + appName.toLowerCase());
		}
	}

//	public Path getBackendRequestsFile(AppConfig cfg) throws IOException {
//		Path dir = getConfigDir();
//		if (!Files.exists(dir)) {
//			Files.createDirectories(dir);
//		}
//		Path file;
//		if (cfg != null && cfg.backendRequestsPath != null && !cfg.backendRequestsPath.isBlank()) {
//			file = Paths.get(cfg.backendRequestsPath);
//			if (!file.isAbsolute()) {
//				file = dir.resolve(cfg.backendRequestsPath);
//			}
//		} else {
//			file = dir.resolve(BACKEND_REQUESTS_FILE_NAME);
//		}
//
//		if (!Files.exists(file)) {
//			try (Writer w = Files.newBufferedWriter(file)) {
//				w.write("[]");
//			}
//		}
//		return file;
//	}

	private Path getConfigFile() throws IOException {
		Path dir = getConfigDir();
		if (!Files.exists(dir)) {
			Files.createDirectories(dir);
		}
		return dir.resolve(CONFIG_FILE_NAME);
	}

	public AppConfig load() {
		try {
			Path file = getConfigFile();
			if (!Files.exists(file)) {
				AppConfig cfg = new AppConfig();
				save(cfg);
				return cfg;
			}
			try (Reader r = Files.newBufferedReader(file)) {
				AppConfig cfg = gson.fromJson(r, AppConfig.class);

				if (cfg == null) cfg = new AppConfig();
				if (cfg.theme == null) cfg.theme = "Light";
				if (cfg.chromeDriverPath == null) cfg.chromeDriverPath = "";
				if (cfg.openApiSpecsPath == null) cfg.openApiSpecsPath = "";
				if (cfg.customMethodsPath == null) cfg.customMethodsPath = "";
				if (cfg.usersSpecsPath == null) cfg.usersSpecsPath = "";
				if (cfg.backendRequestsPath == null) cfg.backendRequestsPath = "";
				if (cfg.trustStorePath == null) cfg.trustStorePath = "";
				if (cfg.trustStorePassword == null) cfg.trustStorePassword = "changeit";
				if (cfg.trustStoreType == null) cfg.trustStoreType = "JKS";
				if (cfg.actionTableColumnWidths == null) cfg.actionTableColumnWidths = new HashMap<>();
				if (cfg.domains == null) cfg.domains = new java.util.ArrayList<>();
				if (cfg.selectedDomain == null) cfg.selectedDomain = "";

				if (cfg.jagaUserSettingsPath == null) cfg.jagaUserSettingsPath = "";
				if (cfg.jagaUserSettings == null) cfg.jagaUserSettings = new JagaUserSettings();
				if (cfg.jagaUserSettings.getEmail() == null) cfg.jagaUserSettings.setEmail("");
				if (cfg.jagaUserSettings.getPassword() == null) cfg.jagaUserSettings.setPassword("");
				if (cfg.jagaUserSettings.getTaskTypes() == null) {
					cfg.jagaUserSettings.setTaskTypes(new java.util.LinkedHashMap<>());
				}

				return cfg;
			}
		} catch (Exception e) {
			e.printStackTrace();
			AppConfig cfg = new AppConfig();
			try { save(cfg); } catch (Exception ignored) {}
			return cfg;
		}
	}

	public void save(AppConfig config) throws IOException {
		Path file = getConfigFile();
		try (Writer w = Files.newBufferedWriter(file)) {
			gson.toJson(config, w);
		}
	}

	// путь к openApiSpec.json
	public Path getOpenApiSpecsFile(AppConfig cfg) throws IOException {
		Path dir = getConfigDir();
		if (!Files.exists(dir)) {
			Files.createDirectories(dir);
		}
		Path file;
		if (cfg != null && cfg.openApiSpecsPath != null && !cfg.openApiSpecsPath.isBlank()) {
			file = Paths.get(cfg.openApiSpecsPath);
			if (!file.isAbsolute()) {
				file = dir.resolve(cfg.openApiSpecsPath);
			}
		} else {
			file = dir.resolve(OPENAPI_SPECS_FILE_NAME);
		}

		if (!Files.exists(file)) {
			// создаём пустой json массив по дефолту
			try (Writer w = Files.newBufferedWriter(file)) {
				w.write("[]");
			}
		}
		return file;
	}

	// путь к openApiSpec.json
	public Path getCustomMethodsFile(AppConfig cfg) throws IOException {
		Path dir = getConfigDir();
		if (!Files.exists(dir)) {
			Files.createDirectories(dir);
		}
		Path file;
		if (cfg != null && cfg.customMethodsPath != null && !cfg.customMethodsPath.isBlank()) {
			file = Paths.get(cfg.customMethodsPath);
			if (!file.isAbsolute()) {
				file = dir.resolve(cfg.customMethodsPath);
			}
		} else {
			file = dir.resolve(CUSTOM_METHODS_FILE_NAME);
		}

		if (!Files.exists(file)) {
			try (Writer w = Files.newBufferedWriter(file)) {
				w.write("[]");
			}
		}
		return file;
	}

	// путь к openApiSpec.json
	public Path getUsersFile(AppConfig cfg) throws IOException {
		Path dir = getConfigDir();
		if (!Files.exists(dir)) {
			Files.createDirectories(dir);
		}
		Path file;
		if (cfg != null && cfg.usersSpecsPath != null && !cfg.usersSpecsPath.isBlank()) {
			file = Paths.get(cfg.usersSpecsPath);
			if (!file.isAbsolute()) {
				file = dir.resolve(cfg.usersSpecsPath);
			}
		} else {
			file = dir.resolve(USERS_FILE_NAME);
		}

		if (!Files.exists(file)) {
			// создаём пустой json массив по дефолту
			try (Writer w = Files.newBufferedWriter(file)) {
				w.write("[]");
			}
		}
		return file;
	}

	public Path loadConfigDir() throws IOException {
		return getConfigDir();
	}

	public Path getJagaUserSettingsFile(AppConfig cfg) throws IOException {
		Path dir = getConfigDir();
		if (!Files.exists(dir)) {
			Files.createDirectories(dir);
		}

		Path file;
		if (cfg != null && cfg.jagaUserSettingsPath != null && !cfg.jagaUserSettingsPath.isBlank()) {
			file = Paths.get(cfg.jagaUserSettingsPath);
			if (!file.isAbsolute()) {
				file = dir.resolve(cfg.jagaUserSettingsPath);
			}
		} else {
			file = dir.resolve(JAGA_USER_SETTINGS_FILE_NAME);
		}

		if (file.getParent() != null && !Files.exists(file.getParent())) {
			Files.createDirectories(file.getParent());
		}

		if (!Files.exists(file)) {
			try (Writer w = Files.newBufferedWriter(file)) {
				gson.toJson(new JagaUserSettings(), w);
			}
		}

		return file;
	}

	public JagaUserSettings loadJagaUserSettings(AppConfig cfg) {
		try {
			Path file = getJagaUserSettingsFile(cfg);

			try (Reader r = Files.newBufferedReader(file)) {
				JagaUserSettings settings = gson.fromJson(r, JagaUserSettings.class);

				if (settings == null) {
					settings = new JagaUserSettings();
				}

				if (settings.getEmail() == null) {
					settings.setEmail("");
				}
				if (settings.getTaskTypes() == null) {
					settings.setTaskTypes(new java.util.LinkedHashMap<>());
				}
				if (settings.getEncryptedPassword() == null) {
					settings.setEncryptedPassword("");
				}
				if (settings.getPassword() == null) {
					settings.setPassword("");
				}

				return settings;
			}
		} catch (Exception e) {
			e.printStackTrace();

			JagaUserSettings settings = new JagaUserSettings();
			settings.setEmail("");
			settings.setTaskTypes(new java.util.LinkedHashMap<>());
			settings.setEncryptedPassword("");
			settings.setPassword("");

			return settings;
		}
	}

	public void saveJagaUserSettings(AppConfig cfg, JagaUserSettings settings) throws IOException {
		Path file = getJagaUserSettingsFile(cfg);

		JagaUserSettings existingSettings = loadJagaUserSettings(cfg);

		if (settings == null) {
			settings = new JagaUserSettings();
		}

		if (settings.getEmail() == null) {
			settings.setEmail("");
		}
		if (settings.getTaskTypes() == null) {
			settings.setTaskTypes(new java.util.LinkedHashMap<>());
		}
		if (settings.getEncryptedPassword() == null) {
			settings.setEncryptedPassword("");
		}

		if (settings.getPassword() != null && !settings.getPassword().isBlank()) {
			settings.setEncryptedPassword(SimpleSecretService.encrypt(settings.getPassword()));
		} else {
			settings.setEncryptedPassword(existingSettings.getEncryptedPassword() == null
					? ""
					: existingSettings.getEncryptedPassword());
		}

		settings.setPassword("");

		try (Writer w = Files.newBufferedWriter(file)) {
			gson.toJson(settings, w);
		}
	}
}


