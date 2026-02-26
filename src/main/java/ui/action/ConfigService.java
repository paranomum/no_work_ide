package ui.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dto.AppConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigService {
	private static final String CONFIG_FILE_NAME = "settings.json";
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
				// первый запуск: создаём файл с дефолтами
				AppConfig cfg = new AppConfig();
				save(cfg);
				return cfg;
			}
			try (Reader r = Files.newBufferedReader(file)) {
				AppConfig cfg = gson.fromJson(r, AppConfig.class);
				if (cfg == null) {
					cfg = new AppConfig();
				}
				// на всякий случай подстрахуем поля
				if (cfg.theme == null) cfg.theme = "Light";
				if (cfg.chromeDriverPath == null) cfg.chromeDriverPath = "";
				return cfg;
			}
		} catch (Exception e) {
			e.printStackTrace();
			// при любой ошибке работаем с дефолтами
			AppConfig cfg = new AppConfig();
			try {
				save(cfg);
			} catch (Exception ignored) {}
			return cfg;
		}
	}

	public void save(AppConfig config) throws IOException {
		Path file = getConfigFile();
		try (Writer w = Files.newBufferedWriter(file)) {
			gson.toJson(config, w);
		}
	}
}


