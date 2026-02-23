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
	private String lastSelectOpenXpath = null;

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

		injectScriptsIntoCurrentTab();
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript("window.recordedClicks = [];");
		js.executeScript("window.recordedInputs = [];");

		startCapture();
	}

	private void startCapture() {
		new Thread(() -> {
			while (isRecording && driver != null) {
				try {
					JavascriptExecutor js = (JavascriptExecutor) driver;

					// ===== ВВОД ТЕКСТА =====
					Object inputs = js.executeScript("return window.recordedInputs;");
					if (inputs instanceof java.util.List) {
						@SuppressWarnings("unchecked")
						java.util.List<Map<String, ?>> inputList = (java.util.List<Map<String, ?>>) inputs;

						if (!inputList.isEmpty()) {
							// Очищаем очередь в браузере перед обработкой пачки
							js.executeScript("window.recordedInputs = [];");

							for (Map<String, ?> input : inputList) {
								String xpath = (String) input.get("xpath");
								String value = (String) input.get("value");

								System.out.println("[CAPTURE] fill: xpath=" + xpath + ", value=" + value);

								record("fill", xpath, value);
							}
						}
					}

					// ===== КЛИКИ =====
					Object clicks = js.executeScript("return window.recordedClicks;");
					if (clicks instanceof java.util.List) {
						@SuppressWarnings("unchecked")
						java.util.List<Map<String, ?>> clickList = (java.util.List<Map<String, ?>>) clicks;

						if (!clickList.isEmpty()) {
							// Сначала очищаем очередь в браузере,
							// чтобы новые события не смешивались с текущими
							js.executeScript("window.recordedClicks = [];");

							for (Map<String, ?> click : clickList) {
								String xpath = (String) click.get("xpath");
								String text = (String) click.get("text");
								String selectXpath = (String) click.get("selectXpath");
								Object rawEvent = click.get("eventType");
								String eventType = rawEvent != null ? rawEvent.toString() : "click";

								Object newTabRaw = click.get("newTab");
								boolean newTab = false;
								if (newTabRaw instanceof Boolean) {
									newTab = (Boolean) newTabRaw;
								} else if (newTabRaw != null) {
									newTab = Boolean.parseBoolean(newTabRaw.toString());
								}

								System.out.println("[CAPTURE] raw click: eventType=" + eventType
										+ ", xpath=" + xpath
										+ ", selectXpath=" + selectXpath
										+ ", text=" + text
										+ ", newTab=" + newTab);

								if (newTab) {
									// клик по ссылке, открывающей новую вкладку
									record("click", xpath, text);
									System.out.println("[CAPTURE] newTab click recorded before any switch");
									// НЕ делаем continue всего while — только переходим к следующему click
									continue;
								}

								switch (eventType) {
									case "tab-inactive":
										System.out.println("[CAPTURE] EVENT tab-inactive");
										handleTabInactive();
										break;

									case "tab-active":
										System.out.println("[CAPTURE] EVENT tab-active, handle="
												+ driver.getWindowHandle()
												+ ", url=" + driver.getCurrentUrl());
										break;

									case "select-open":
										lastSelectOpenXpath = xpath;
										System.out.println("[CAPTURE] select-open, remember xpath="
												+ lastSelectOpenXpath);
										break;

									case "select-option":
										if (selectXpath != null && !selectXpath.isEmpty()) {
											System.out.println("[CAPTURE] select-option with selectXpath="
													+ selectXpath + ", text=" + text);
											record("select", selectXpath, text);
										} else {
											System.out.println("[CAPTURE] select-option WITHOUT selectXpath -> IGNORE");
										}
										lastSelectOpenXpath = null;
										break;

									default:
										System.out.println("[CAPTURE] normal click -> record(click): xpath="
												+ xpath + ", text=" + text);
										record("click", xpath, text);
										lastSelectOpenXpath = null;
								}
							}
						}
					}

					// Слип между циклами
					Thread.sleep(200);
				} catch (Exception e) {
					System.err.println("Error in capture loop: " + e.getMessage());
					e.printStackTrace();
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

	private void handleTabInactive() {
		try {
			if (driver == null) {
				System.out.println("[TAB] tab-inactive: driver is null, nothing to do");
				return;
			}

			String currentHandle = driver.getWindowHandle();
			String currentUrl = driver.getCurrentUrl();

			System.out.println("[TAB] tab-inactive on handle=" + currentHandle +
					", url=" + currentUrl);

			java.util.Set<String> handles = driver.getWindowHandles();
			if (handles.size() < 2) {
				System.out.println("[TAB] only one window handle, nothing to switch");
				return;
			}

			String targetHandle = null;
			for (String handle : handles) {
				if (!handle.equals(currentHandle)) {
					targetHandle = handle;
					break;
				}
			}

			if (targetHandle == null) {
				System.out.println("[TAB] no other handle found, abort switch");
				return;
			}

			// переключаемся на новую вкладку
			driver.switchTo().window(targetHandle);
			String newUrl = driver.getCurrentUrl();

			System.out.println("[TAB] SWITCH TAB from " + currentHandle + " (" + currentUrl +
					") to " + targetHandle + " (" + newUrl + ")");

			// записываем switchTab
			record("switchTab", null, newUrl);
			System.out.println("[TAB] RECORDED switchTab to url=" + newUrl);

			// закрываем старую вкладку
			driver.switchTo().window(currentHandle);
			driver.close();
			System.out.println("[TAB] closed previous tab handle=" + currentHandle +
					", url=" + currentUrl);

			// остаёмся на новой вкладке и реинжектим скрипты
			driver.switchTo().window(targetHandle);
			injectScriptsIntoCurrentTab();

		} catch (Exception e) {
			System.err.println("[TAB] Error during handleTabInactive: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@SneakyThrows
	private void injectScriptsIntoCurrentTab() {
		if (!(driver instanceof JavascriptExecutor)) return;
		JavascriptExecutor js = (JavascriptExecutor) driver;

		String buttons = new String(Files.readAllBytes(Paths.get("src/main/resources/buttons.js")));
		String fields  = new String(Files.readAllBytes(Paths.get("src/main/resources/input.js")));
		String script  = new String(Files.readAllBytes(Paths.get("src/main/resources/actions.js")));

		js.executeScript(buttons + fields + script);
		System.out.println("[TAB] recorder scripts injected into current tab: " + driver.getCurrentUrl());
	}
}
