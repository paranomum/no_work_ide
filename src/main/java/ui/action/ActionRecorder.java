package ui.action;

import lombok.SneakyThrows;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import javax.swing.table.DefaultTableModel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ActionRecorder {

	private final DefaultTableModel tableModel;
	private final Map<String, String> variables = new HashMap<>();
	private boolean isRecording = false;
	private WebDriver driver;
	private volatile String lastFocusedXPath = null;
	private volatile String lastFocusedValue = "";

	public ActionRecorder(DefaultTableModel tableModel) {
		this.tableModel = tableModel;
	}

	public void setDriver(WebDriver driver) {
		this.driver = driver;
		if (isRecording) {
			injectListeners();
		}
	}

	public void toggleRecording() {
		isRecording = !isRecording;
		if (isRecording && driver != null) {
			injectListeners();
		}
	}

	public boolean isRecording() {
		return isRecording;
	}

	@SneakyThrows
	private void injectListeners() {
		if (!(driver instanceof JavascriptExecutor)) return;
		JavascriptExecutor js = (JavascriptExecutor) driver;

		String buttonXpath =  "(//button[contains(.//span, '') " +
				"or contains(@ng-reflect-message, '') " +
				"or contains(@aria-label, '') " +
				"or contains(.//@aria-label, '') " +
				"or contains(text(), '') " +
				"or contains(., '')] | " +
				"//*[@data-testid='button' " +
				"and ./span[contains(text(), '')] and not(contains(@class, '-trigger'))])";

		String buttons = new String(Files.readAllBytes(Paths.get("src/main/resources/buttons.js")));
		String script = new String(Files.readAllBytes(Paths.get("src/main/resources/actions.js")));

		js.executeScript(buttons + script);
		startCapture();
	}


	private void startCapture() {
		new Thread(() -> {
			while (isRecording && driver != null) {
				try {
					JavascriptExecutor js = (JavascriptExecutor) driver;

					// Проверяем клики
					Object clicks = js.executeScript("return window.recordedClicks;");
					if (clicks instanceof java.util.List) {
						java.util.List<Map<String, ?>> clickList = (java.util.List<Map<String, ?>>) clicks;
						if (!clickList.isEmpty()) {
							Map<String, ?> click = clickList.get(clickList.size() - 1);
							String xpath = (String) click.get("xpath");
							String text = (String) click.get("text");
							record("click", xpath, text);
							js.executeScript("window.recordedClicks = [];");
						}
					}

					// Проверяем ввод текста
					Object inputs = js.executeScript("return window.recordedInputs;");
					if (inputs instanceof java.util.List) {
						java.util.List<Map<String, ?>> inputList = (java.util.List<Map<String, ?>>) inputs;
						if (!inputList.isEmpty()) {
							Map<String, ?> input = inputList.get(inputList.size() - 1);
							String xpath = (String) input.get("xpath");
							String value = (String) input.get("value");
							record("fill", xpath, value);
							js.executeScript("window.recordedInputs = [];");
						}
					}

					Thread.sleep(500);
				} catch (Exception e) {
					System.err.println("Error in capture loop: " + e.getMessage());
				}
			}
		}).start();
	}


	private void record(String action, String selector, String value) {
		if (!isRecording) return;
		System.out.printf("REC: %s | %s | %s%n", action, selector, value);
		tableModel.addRow(new Object[]{
				action,
				selector != null ? selector : "",
				value != null ? value : "",
				"",
				""
		});
	}

	public Map<String, String> getVariables() {
		return variables;
	}
}
