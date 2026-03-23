package ui.action;

import lombok.SneakyThrows;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import javax.swing.table.DefaultTableModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ActionRecorder {

	private final DefaultTableModel tableModel;
	private final Map<String, String> variables = new HashMap<>();
	private boolean isRecording = false;
	private WebDriver driver;

	private volatile String lastFocusedXPath = null;
	private volatile String lastFocusedValue = "";

	private String lastSelectOpenXpath = null;
	private String selectName = null;
	private String selectIndex = null;
	private String selectByXpath = null;

	private String lastDropdownOpenXpath = null;
	private String dropdownName = null;
	private String dropdownIndex = null;
	private String dropdownByXpath = null;

	private String lastDatePickerOpenXpath = null;
	private String datePickerName = null;
	private String datePickerIndex = null;
	private String datePickerByXpath = null;
	private List<String> currentDateRange = new ArrayList<>();

	private Thread recorderThread;

	public ActionRecorder(DefaultTableModel tableModel) {
		this.tableModel = tableModel;
	}

	public void setDriver(WebDriver driver) {
		this.driver = driver;
		if (isRecording) {
			injectListeners();
		}
	}

	/**
	 * Вкл/выкл записи. При выключении аккуратно гасим поток.
	 */
	public synchronized void toggleRecording() {
		if (isRecording) {
			isRecording = false;
			stopRecorderThread();
		} else {
			if (driver != null) {
				isRecording = true;
				injectListeners();
			}
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

	/**
	 * Запуск одного потока‑рекордера. Если уже запущен — не создаём новый.
	 */
	private synchronized void startCapture() {
		if (recorderThread != null && recorderThread.isAlive()) {
			System.out.println("[RECORDER] capture thread already running: " + recorderThread.getId());
			return;
		}

		recorderThread = new Thread(() -> {
			System.out.println("[RECORDER] capture thread started: " + Thread.currentThread().getId());
			while (isRecording && driver != null) {
				try {
					if (!(driver instanceof JavascriptExecutor)) {
						break;
					}
					JavascriptExecutor js = (JavascriptExecutor) driver;

					// ===== ВВОД ТЕКСТА =====
					Object inputs = js.executeScript("return window.recordedInputs;");
					if (inputs instanceof List) {
						@SuppressWarnings("unchecked")
						List<Map<String, ?>> inputList = (List<Map<String, ?>>) inputs;

						if (!inputList.isEmpty()) {
							js.executeScript("window.recordedInputs = [];");

							for (Map<String, ?> input : inputList) {
								String xpath   = (String) input.get("xpath");
								String value   = (String) input.get("value");
								String name = (String) input.get("name");
								String pageUrlPath = (String) input.get("pageUrlPath");

								Object indexObj = input.get("index");
								String index = null;
								if (indexObj instanceof Number) {
									index = String.valueOf(((Number) indexObj).longValue());
								}

								Object byXpathObj = input.get("initByXpath");
								String byXpath = null;
								if (byXpathObj instanceof Boolean) {
									byXpath = byXpathObj.toString();
								};

								System.out.println("[CAPTURE] fill: xpath=" + xpath + ", value=" + value);
								record("fill", xpath, value, "Field", xpath, name, index, byXpath, pageUrlPath);
							}
						}
					}

					// ===== КЛИКИ =====
					Object clicks = js.executeScript("return window.recordedClicks;");
					if (clicks instanceof List) {
						@SuppressWarnings("unchecked")
						List<Map<String, ?>> clickList = (List<Map<String, ?>>) clicks;

						if (!clickList.isEmpty()) {
							js.executeScript("window.recordedClicks = [];");

							for (Map<String, ?> click : clickList) {
								System.out.println(clickList);
								String xpath   = (String) click.get("xpath");
								String elType  = (String) click.get("elementType");
								String text    = (String) click.get("text");
								String selectXpath = (String) click.get("selectXpath");
								Object rawEvent    = click.get("eventType");
								String eventType   = rawEvent != null ? rawEvent.toString() : "click";
								String pageUrlPath = (String) click.get("pageUrlPath");

								Object indexObj = click.get("index");
								String index = null;
								if (indexObj instanceof Number) {
									index = String.valueOf(((Number) indexObj).longValue());
								}

								Object byXpathObj = click.get("initByXpath");
								String byXpath = null;
								if (byXpathObj instanceof Boolean) {
									byXpath = byXpathObj.toString();
								};

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
									record("click", xpath, "", elType, xpath, text, index, byXpath, pageUrlPath);
									System.out.println("[CAPTURE] newTab click recorded before any switch");
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
										selectName = text;
										selectIndex = index;
										selectByXpath = byXpath;
										break;

									case "select-option":
										if (selectXpath != null && !selectXpath.isEmpty()) {
											System.out.println("[CAPTURE] select-option with selectXpath="
													+ selectXpath + ", text=" + text);
											record("selectOption", lastSelectOpenXpath, text, "Select",
													lastSelectOpenXpath, selectName, selectIndex, selectByXpath, pageUrlPath);
										} else {
											System.out.println("[CAPTURE] select-option WITHOUT selectXpath -> IGNORE");
										}
										lastSelectOpenXpath = null;
										selectName = null;
										selectIndex = null;
										selectByXpath = null;
										break;

									case "dropdown-open":
										lastDropdownOpenXpath = selectXpath;
										System.out.println("[CAPTURE] dropdown-open, remember xpath="
												+ lastDropdownOpenXpath);
										dropdownName = text;
										dropdownIndex = index;
										dropdownByXpath = byXpath;
										break;

									case "dropdown-option":
										if (selectXpath != null && !selectXpath.isEmpty()) {
											System.out.println("[CAPTURE] dropdown-option with selectXpath="
													+ selectXpath + ", text=" + text);
											record("selectOption", lastDropdownOpenXpath, text, "Dropdown",
													lastDropdownOpenXpath, dropdownName, dropdownIndex, dropdownByXpath, pageUrlPath);
										} else {
											System.out.println("[CAPTURE] dropdown-option WITHOUT selectXpath -> IGNORE");
										}
										lastDropdownOpenXpath = null;
										dropdownName = null;
										dropdownIndex = null;
										dropdownByXpath = null;
										break;

									case "datepicker-open":
										lastDatePickerOpenXpath = xpath;
										System.out.println("[CAPTURE] datepicker-open, remember xpath=" + lastDatePickerOpenXpath);
										currentDateRange.clear();
										datePickerName = text;
										datePickerIndex = index;
										datePickerByXpath = byXpath;
										break;

									case "datepicker-date":
										Object rangeRaw = click.get("rangeIndex");
										Integer rangeIndex = null;
										if (rangeRaw instanceof Number) {
											rangeIndex = ((Number) rangeRaw).intValue();
										} else if (rangeRaw != null) {
											try {
												rangeIndex = Integer.parseInt(rangeRaw.toString());
											} catch (NumberFormatException ignore) {}
										}

										System.out.println("[CAPTURE] datepicker-date: rangeIndex=" + rangeIndex
												+ ", text=" + text + ", selectXpath=" + selectXpath);

										if (rangeIndex == null) {
											record("fillDate",
													lastDatePickerOpenXpath != null ? lastDatePickerOpenXpath : xpath,
													text, "DatePicker",
													lastDatePickerOpenXpath, datePickerName, datePickerIndex, datePickerByXpath, pageUrlPath);
											break;
										}

										while (currentDateRange.size() <= rangeIndex) {
											currentDateRange.add(null);
										}
										currentDateRange.set(rangeIndex, text);

										if (rangeIndex == 0) {
											break;
										}

										if (rangeIndex == 1) {
											String start = currentDateRange.get(0);
											String end   = currentDateRange.get(1);
											if (start != null && end != null) {
												String value = start + " - " + end;
												String selector = lastDatePickerOpenXpath != null
														? lastDatePickerOpenXpath
														: selectXpath;

												System.out.println("[CAPTURE] datepicker-range -> " + value);
												record("fillDate", selector, value, "DatePicker",
														lastDatePickerOpenXpath, datePickerName, datePickerIndex, datePickerByXpath, pageUrlPath);
											} else {
												System.out.println("[CAPTURE] datepicker-range incomplete, skip");
											}

											currentDateRange.clear();
											lastDatePickerOpenXpath = null;
											datePickerName = null;
											datePickerIndex = null;
											datePickerByXpath = null;
										}
										break;

									default:
										System.out.println("[CAPTURE] normal click -> record(click): xpath="
												+ xpath + ", text=" + text);
										record("click", xpath, "", elType, xpath, text, index, byXpath, pageUrlPath);
										lastSelectOpenXpath = null;
								}
							}
						}
					}

					Thread.sleep(200);
				} catch (InterruptedException e) {
					// прервали поток — выходим из цикла
					Thread.currentThread().interrupt();
					break;
				} catch (Exception e) {
					TestRecorderErrorLogger.logError(
							"Error in capture loop\n", e
					);
					System.err.println("Error in capture loop: " + e.getMessage());
					e.printStackTrace();
				}
			}

			System.out.println("[RECORDER] capture thread finished: " + Thread.currentThread().getId());
		}, "RecorderThread");

		recorderThread.setDaemon(true);
		recorderThread.start();
	}

	/**
	 * Остановка потока‑рекордера.
	 */
	private synchronized void stopRecorderThread() {
		if (recorderThread != null && recorderThread.isAlive()) {
			recorderThread.interrupt();
		}
		recorderThread = null;
	}

	private void record(String action, String selector, String value, String type, String xpath, String name, String index, String byXpath, String pageUrlPath) {
		if (!isRecording) return;
		System.out.printf("REC: %s | %s | %s%n", action, selector, value);
		tableModel.addRow(new Object[]{
				null,
				action,
				selector != null ? selector : "",
				value != null ? value : "",
				"",
				type != null ? type : "",
				xpath != null ? xpath : "",
				name != null ? name : "",
				index != null ? index.toString() : "",
				byXpath != null ? byXpath : "",
				pageUrlPath != null ? pageUrlPath : "",
		});
	}

	// ---------- Locator Picker ----------

	@SneakyThrows
	public void startLocatorPick(java.util.function.Consumer<String> callback) {
		if (driver == null || !(driver instanceof JavascriptExecutor)) return;

		JavascriptExecutor js = (JavascriptExecutor) driver;

		String locatorScript = loadResource("get_locator.js");
		String base = loadResource("base.js");
		String buttonScript  = loadResource("buttons.js");
		String inputScript   = loadResource("input.js");
		String picker        = loadResource("date_picker.js");
		String select        = loadResource("select.js");
		js.executeScript(base+ locatorScript + buttonScript + inputScript + picker + select);

		new Thread(() -> {
			try {
				while (driver != null) {
					Object result = ((JavascriptExecutor) driver).executeScript("return window.locatorPickResult");
					if (result instanceof String) {
						String xpath = (String) result;
						System.out.println("LOCATOR_PICK result: " + xpath);
						callback.accept(xpath);

						injectScriptsIntoCurrentTab();
						break;
					}
					Thread.sleep(150);
				}
			} catch (Exception e) {
				TestRecorderErrorLogger.logError(
						"Error in locator pick\n", e
				);
				System.err.println("Error in locator pick: " + e.getMessage());
				callback.accept("");
				try { injectScriptsIntoCurrentTab(); } catch (Exception ignored) {}
			}
		}, "LocatorPickThread").start();
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

			driver.switchTo().window(targetHandle);
			String newUrl = driver.getCurrentUrl();

			System.out.println("[TAB] SWITCH TAB from " + currentHandle + " (" + currentUrl +
					") to " + targetHandle + " (" + newUrl + ")");

			record("switchTab", null, newUrl, "", "", "", null, "", "");
			System.out.println("[TAB] RECORDED switchTab to url=" + newUrl);

			driver.switchTo().window(currentHandle);
			driver.close();
			System.out.println("[TAB] closed previous tab handle=" + currentHandle +
					", url=" + currentUrl);

			driver.switchTo().window(targetHandle);
			injectScriptsIntoCurrentTab();

		} catch (Exception e) {
			TestRecorderErrorLogger.logError(
					"[TAB] Error during handleTabInactive\n", e
			);
			System.err.println("[TAB] Error during handleTabInactive: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@SneakyThrows
	private void injectScriptsIntoCurrentTab() {
		if (!(driver instanceof JavascriptExecutor)) return;
		JavascriptExecutor js = (JavascriptExecutor) driver;

		String buttons = loadResource("buttons.js");
		String base = loadResource("base.js");
		String fields  = loadResource("input.js");
		String script  = loadResource("actions.js");
		String picker  = loadResource("date_picker.js");
		String select  = loadResource("select.js");

		js.executeScript(base + buttons + fields + script + picker + select);
		System.out.println("[TAB] recorder scripts injected into current tab: " + driver.getCurrentUrl());
	}

	public void highlightByXpath(String xpath) {
		if (driver == null || !(driver instanceof JavascriptExecutor)) {
			return;
		}
		if (xpath == null || xpath.isEmpty()) {
			return;
		}

		JavascriptExecutor js = (JavascriptExecutor) driver;
		String script =
				"function ensureHighlightStyle() {" +
						"  if (document.getElementById('__locator-highlight-style')) return;" +
						"  var style = document.createElement('style');" +
						"  style.id = '__locator-highlight-style';" +
						"  style.textContent = '.__locator-highlight { outline: 2px solid yellow !important; " +
						"     background-color: rgba(255,255,0,0.2) !important; }';" +
						"  document.head.appendChild(style);" +
						"}" +
						"ensureHighlightStyle();" +
						"function getElementByXPath(path) {" +
						"  var result = document.evaluate(path, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);" +
						"  return result.singleNodeValue;" +
						"}" +
						"var el = getElementByXPath(arguments[0]);" +
						"if (el) {" +
						"  el.classList.add('__locator-highlight');" +
						"  setTimeout(function() { el.classList.remove('__locator-highlight'); }, 1000);" +
						"}";

		try {
			js.executeScript(script, xpath);
		} catch (Exception e) {
			TestRecorderErrorLogger.logError(
					"Error highlighting by xpath\n", e
			);
			System.err.println("Error highlighting by xpath: " + e.getMessage());
		}
	}

	private String loadResource(String resourcePath) throws IOException {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new IOException("Resource not found: " + resourcePath);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}