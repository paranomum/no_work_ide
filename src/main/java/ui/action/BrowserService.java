package ui.action;

import com.codeborne.selenide.WebDriverRunner;
import dto.AppConfig;
import lombok.val;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import ui.ActionWindow;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.codeborne.selenide.Selenide.open;

public class BrowserService {

	private final ConfigService configService;
	private final AppConfig config;

	public BrowserService(ConfigService configService, AppConfig config) {
		this.configService = configService;
		this.config = config;
	}

	public ChromeDriver openBrowser(ActionWindow parent,
									JTextField driverPathField,
									WebDriver driver,
									Proxy seleniumProxy) {

		String driverPath = driverPathField.getText().trim();

		if (driverPath.isEmpty()) {
			JOptionPane.showMessageDialog(
					parent,
					"ChromeDriver path is not set. Please select it first.",
					"ChromeDriver Path Required",
					JOptionPane.WARNING_MESSAGE
			);
			selectChromeDriver(parent, driverPathField);
			return null;
		}

		if (driver != null) {
			val nowDriver = isBrowserClosed(driver);
			if (!nowDriver) {
				JOptionPane.showMessageDialog(
						parent,
						"Browser is already open",
						"Browser Running",
						JOptionPane.INFORMATION_MESSAGE
				);
				return (ChromeDriver) driver;
			}
		}

		ArrayList<String> browserArgs = new ArrayList<>();
		browserArgs.add("no-sandbox");
		browserArgs.add("allow-running-insecure-content");
		browserArgs.add("--ignore-certificate-errors");
		browserArgs.add("--ignore-urlfetcher-cert-requests");

		Map<String, Object> prefs = new HashMap<>();
		prefs.put("intl.accept_languages", "ru");
		prefs.put("intl.selected_languages", "ru");

		ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions.setExperimentalOption("prefs", prefs);
		chromeOptions.addArguments(browserArgs);
		chromeOptions.addArguments("--incognito");
		chromeOptions.setAcceptInsecureCerts(true);

		// NEW: прокидываем proxy в браузер
		if (seleniumProxy != null) {
			chromeOptions.setProxy(seleniumProxy);
			chromeOptions.addArguments("--proxy-bypass-list=autofaq-hr.rt.ru;localhost;127.0.0.1");
		}

		String osName = System.getProperty("os.name");
		if (osName.startsWith("Windows")) {
			System.setProperty("webdriver.chrome.driverToInit", driverPath);
			System.setProperty("webdriver.chrome.driver", driverPath);
		} else {
			chromeOptions.setBinary(driverPath);
		}

		return new ChromeDriver(chromeOptions);
	}

	public void selectChromeDriver(ActionWindow actionWindow, JTextField driverPathField) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if(System.getProperty("os.name").toLowerCase().contains("mac"))
			fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		fileChooser.setDialogTitle("Select ChromeDriver");

		fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) return true;
				String name = file.getName().toLowerCase();
				if (name.contains("chromedriver")) return true;
				if (name.endsWith(".app")) return true;
				return file.canExecute();
			}

			@Override
			public String getDescription() {
				return "Chrome/ChromeDriver executable (chromedriver, chromedriver.exe, *.app)";
			}
		});

		int result = fileChooser.showOpenDialog(actionWindow);

		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			String path = selectedFile.getAbsolutePath();
			driverPathField.setText(path);
			System.out.println("Selected ChromeDriver: " + path);

			config.chromeDriverPath = path;
			try {
				configService.save(config);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	public static boolean isBrowserClosed(WebDriver driver) {
		if (driver == null) return true;
		try {
			driver.getTitle();
			driver.getTitle();
			driver.getTitle();
			return false;                   // сессия жива
		} catch (Exception e) {
			return true;                    // окно/сессия уже мертвы
		}
	}
}
