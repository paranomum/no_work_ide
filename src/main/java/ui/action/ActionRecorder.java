package ui.action;

import lombok.SneakyThrows;
import lombok.val;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import javax.swing.table.DefaultTableModel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionRecorder {

	private final DefaultTableModel tableModel;
	private final Map<String, String> variables = new HashMap<>();
	private boolean isRecording = false;
	private WebDriver driver;
	private volatile String lastFocusedXPath = null;
	private volatile String lastFocusedValue = "";


	private String lastSelectOpenXpath = null;
	private String selectJava = null;
	private String lastDropdownOpenXpath = null;
	private String dropdownJava = null;

	private String lastDatePickerOpenXpath = null;
	private String datePickerJava = null;
	private List<String> currentDateRange = new ArrayList<>();

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
								String javaData = (String) input.get("javaData");


								System.out.println("[CAPTURE] fill: xpath=" + xpath + ", value=" + value);

								record("fill", xpath, value, "Field", javaData);
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
								System.out.println(clickList);
								String xpath = (String) click.get("xpath");
								String еlType = (String) click.get("elementType");
								String text = (String) click.get("text");
								String selectXpath = (String) click.get("selectXpath");
								Object rawEvent = click.get("eventType");
								String eventType = rawEvent != null ? rawEvent.toString() : "click";
								String javaData = (String) click.getOrDefault("javaData", null);

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
										+ ", newTab=" + newTab
										+ ", javaData=" + javaData);

								if (newTab) {
									// клик по ссылке, открывающей новую вкладку
									record("click", xpath, "", еlType, javaData);
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
										selectJava = javaData;
										break;

									case "select-option":
										if (selectXpath != null && !selectXpath.isEmpty()) {
											System.out.println("[CAPTURE] select-option with selectXpath="
													+ selectXpath + ", text=" + text);
											record("selectOption", lastSelectOpenXpath, text, "Select", selectJava);
										} else {
											System.out.println("[CAPTURE] select-option WITHOUT selectXpath -> IGNORE");
										}
										lastSelectOpenXpath = null;
										selectJava = null;
										break;

									case "dropdown-open":
										lastDropdownOpenXpath = selectXpath;
										System.out.println("[CAPTURE] dropdown-open, remember xpath="
												+ lastDropdownOpenXpath);
										dropdownJava = javaData;
										break;

									case "dropdown-option":
										if (selectXpath != null && !selectXpath.isEmpty()) {
											System.out.println("[CAPTURE] dropdown-option with selectXpath="
													+ selectXpath + ", text=" + text);
											record("selectOption", lastDropdownOpenXpath, text, "Dropdown", javaData);
										} else {
											System.out.println("[CAPTURE] dropdown-option WITHOUT selectXpath -> IGNORE");
										}
										lastDropdownOpenXpath = null;
										dropdownJava = null;
										break;

									case "datepicker-open":
										// аналог select-open / dropdown-open
										lastDatePickerOpenXpath = xpath; // или отдельная переменная lastDatePickerXpath, если хочешь
										System.out.println("[CAPTURE] datepicker-open, remember xpath=" + lastDatePickerOpenXpath);
										currentDateRange.clear();
										datePickerJava = javaData;
										break;

									case "datepicker-date":
										// rangeIndex приходит из JS в click
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
											// fallback: одиночная дата
											record("fillDate", lastDatePickerOpenXpath != null ? lastDatePickerOpenXpath : xpath,
													text, "DatePicker", datePickerJava);
											break;
										}

										// гарантируем размер currentDateRange
										while (currentDateRange.size() <= rangeIndex) {
											currentDateRange.add(null);
										}
										currentDateRange.set(rangeIndex, text);

										// если это первая дата — просто копим
										if (rangeIndex == 0) {
											break;
										}

										// если это вторая дата диапазона
										if (rangeIndex == 1) {
											String start = currentDateRange.get(0);
											String end = currentDateRange.get(1);
											if (start != null && end != null) {
												String value = start + " - " + end;
												String selector = lastDatePickerOpenXpath != null ? lastDatePickerOpenXpath : selectXpath;

												System.out.println("[CAPTURE] datepicker-range -> " + value);
												record("fillDate", selector, value, "DatePicker", datePickerJava);
											} else {
												System.out.println("[CAPTURE] datepicker-range incomplete, skip");
											}

											// сброс состояния диапазона
											currentDateRange.clear();
											lastDatePickerOpenXpath = null;
											datePickerJava = null;
										}
										break;
									default:
										System.out.println("[CAPTURE] normal click -> record(click): xpath="
												+ xpath + ", text=" + text);
										record("click", xpath, "", еlType, javaData);
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


	private void record(String action, String selector, String value, String type, String java) {
		if (!isRecording) return;
		System.out.printf("REC: %s | %s | %s%n", action, selector, value);
		tableModel.addRow(new Object[]{
				null,
				action,
				selector != null ? selector : "",
				value != null ? value : "",
				"",
				type != null ? type : "",
				java != null ? java : "",
		});
	}

	// ---------- Locator Picker ----------

	@SneakyThrows
	public void startLocatorPick(java.util.function.Consumer<String> callback) {
		if (driver == null || !(driver instanceof JavascriptExecutor)) return;

		JavascriptExecutor js = (JavascriptExecutor) driver;

		// 1) подгружаем get_locator.js
		String locatorScript = new String(Files.readAllBytes(Paths.get("src/main/resources/get_locator.js")));
		String buttonScript = new String(Files.readAllBytes(Paths.get("src/main/resources/buttons.js")));
		String inputScript = new String(Files.readAllBytes(Paths.get("src/main/resources/input.js")));
		String picker  = new String(Files.readAllBytes(Paths.get("src/main/resources/date_picker.js")));
		String select  = new String(Files.readAllBytes(Paths.get("src/main/resources/select.js")));
		js.executeScript(locatorScript + buttonScript + inputScript + picker + select);

		// 2) поллим результат
		new Thread(() -> {
			try {
				while (driver != null) {
					Object result = ((JavascriptExecutor) driver).executeScript("return window.locatorPickResult");
					if (result instanceof String) {
						String xpath = (String) result;
						System.out.println("LOCATOR_PICK result: " + xpath);
						callback.accept(xpath);

						// 3) очищаем и возвращаем обычные скрипты
						injectScriptsIntoCurrentTab();
						break;
					}
					Thread.sleep(150);
				}
			} catch (Exception e) {
				System.err.println("Error in locator pick: " + e.getMessage());
				callback.accept("");
				try { injectScriptsIntoCurrentTab(); } catch (Exception ignored) {}
			}
		}).start();
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
			record("switchTab", null, newUrl, "", "");
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
		String picker  = new String(Files.readAllBytes(Paths.get("src/main/resources/date_picker.js")));
		String select  = new String(Files.readAllBytes(Paths.get("src/main/resources/select.js")));

		js.executeScript(buttons + fields + script + picker + select);
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
			System.err.println("Error highlighting by xpath: " + e.getMessage());
		}
	}
}
