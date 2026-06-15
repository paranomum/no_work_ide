package ui.action;

import com.codeborne.selenide.WebDriverRunner;
import dto.ActionRecord;
import dto.BackendRequestDef;
import dto.UsersServiceSpec;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import model.UserAction;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import ru.rt.iqhr.framework.config.FrameworkConfig;
import ru.rt.iqhr.framework.pageobject.react.web_elements.buttons.Button;
import ru.rt.iqhr.framework.pageobject.react.web_elements.triggers.Dropdown;
import ru.rt.iqhr.framework.util.FormFiller;
import ru.rt.iqhr.framework.util.TabManager;
import ui.ActionWindow;

import java.awt.*;
import java.net.URI;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import ru.rt.iqhr.framework.pageobject.react.web_elements.*;

import static com.codeborne.selenide.Selenide.$x;
import static com.codeborne.selenide.Selenide.open;
import static ru.rt.iqhr.framework.util.WebElementUtil.*;
import static ru.rt.iqhr.framework.util.XPathUtils.isProbablyXPath;
import static ui.action.ActionFileService.hasCommaSpacesDigitAndNoLettersAfter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayActionService {

	@Setter
	private WebDriver driver;

	private final DefaultTableModel tableModel;
	private final TabManager tabManager = new TabManager();
	private final FormFiller formFiller;
	private final UsersService usersService;
	private final CustomMethodsService customMethodsService;
	private final BackendRequestsService backendRequestsService;
	private final VariablesService variablesService;

	private Thread playThread;
	@Getter @Setter
	private volatile boolean stopRequested = false;
	private volatile int currentRow = -1;

	private final List<BackendExecutionResult> backendExecutionResults =
			Collections.synchronizedList(new ArrayList<>());

	private static final Logger log = LoggerFactory.getLogger(PlayActionService.class);

	public PlayActionService(DefaultTableModel tableModel,
							 UsersService usersService,
							 CustomMethodsService customMethodsService,
							 BackendRequestsService backendRequestsService,
							 VariablesService variablesService) {
		FrameworkConfig.setSpeedMode(FrameworkConfig.SpeedMode.FAST);
		this.tableModel = tableModel;
		formFiller = new FormFiller();
		this.usersService = usersService;
		this.customMethodsService = customMethodsService;
		this.backendRequestsService = backendRequestsService;
		this.variablesService = variablesService;
		log.info("PlayActionService created, speedMode=FAST");
	}

	public void playActionsFromTable(ActionWindow actionWindow, int startRowIndex, boolean onlyOne) {
		if (driver == null) {
			log.warn("playActionsFromTable: WebDriver is null, showing browser-required dialog");
			JOptionPane.showMessageDialog(
					actionWindow,
					"Browser is not open. Please open browser first.",
					"Browser Required",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		if (playThread != null && playThread.isAlive()) {
			log.warn("playActionsFromTable: scenario already running");
			JOptionPane.showMessageDialog(
					actionWindow,
					"Scenario is already running.",
					"Already running",
					JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}

		Object actionObj = tableModel.getValueAt(startRowIndex, 1);
		if (actionObj instanceof UserAction ua && ua == UserAction.CUSTOM_METHOD) {

			String methodName = Objects.toString(tableModel.getValueAt(startRowIndex, 3), "").trim();

			if (onlyOne) {
				Map<String, String> nameToValue = variablesService.buildAllVariableValuesMap();
				stopRequested = false;
				currentRow = startRowIndex;
				backendExecutionResults.clear();

				playThread = new Thread(() -> {
					String finalMessage = "Playback finished.";
					int finalMessageType = JOptionPane.INFORMATION_MESSAGE;

					try {
						WebDriverRunner.setWebDriver(driver);
						SwingUtilities.invokeLater(actionWindow::repaintActionTable);
						playCustomMethod(methodName, nameToValue);

						if (stopRequested) {
							finalMessage = "Custom method '" + methodName + "' was stopped.";
							finalMessageType = JOptionPane.WARNING_MESSAGE;
						} else {
							finalMessage = "Custom method '" + methodName + "' finished.";
							finalMessageType = JOptionPane.INFORMATION_MESSAGE;
						}
					} catch (Throwable e) {
						TestRecorderErrorLogger.logError(
								"Unexpected error in PlayScenarioThread (customMethod only)", e
						);
						log.error("Unexpected error in PlayScenarioThread (customMethod only)", e);

						finalMessage = "Stopped in custom method '" + methodName + "'.\n" + e.getMessage();
						finalMessageType = JOptionPane.ERROR_MESSAGE;
					} finally {
						stopPlayback();
						currentRow = -1;
						SwingUtilities.invokeLater(actionWindow::repaintActionTable);
						onScenarioFinishedWithBackendAnswers(actionWindow, finalMessage, finalMessageType);
					}
				}, "PlayScenarioThread");

				playThread.start();
				return;
			}
		}

		Map<String, String> nameToValue = variablesService.buildAllVariableValuesMap();
		List<PlayStep> steps = buildStepsFromTable(nameToValue);
		steps.removeIf(step ->
				step.rowIndex < startRowIndex
						|| (onlyOne && step.rowIndex > startRowIndex)
		);

		if (steps.isEmpty()) {
			JOptionPane.showMessageDialog(
					actionWindow,
					"Table has no executable actions.",
					"Nothing to play",
					JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}

		stopRequested = false;
		currentRow = startRowIndex > 0 ? startRowIndex : -1;
		backendExecutionResults.clear();

		playThread = new Thread(() -> {
			String finalMessage = "Playback finished.";
			int finalMessageType = JOptionPane.INFORMATION_MESSAGE;

			try {
				WebDriverRunner.setWebDriver(driver);
				runScenario(actionWindow, steps, nameToValue);

				if (stopRequested) {
					finalMessage = "Scenario was stopped.";
					finalMessageType = JOptionPane.WARNING_MESSAGE;
				} else {
					finalMessage = "Scenario finished successfully.";
					finalMessageType = JOptionPane.INFORMATION_MESSAGE;
				}
			} catch (Throwable e) {
				TestRecorderErrorLogger.logError(
						"Unexpected error in PlayScenarioThread", e
				);
				log.error("Unexpected error in PlayScenarioThread", e);

				finalMessage = "Stopped on step " + currentRow + ".\n" + e.getMessage();
				finalMessageType = JOptionPane.ERROR_MESSAGE;
			} finally {
				stopPlayback();
				currentRow = -1;
				SwingUtilities.invokeLater(actionWindow::repaintActionTable);
				onScenarioFinishedWithBackendAnswers(actionWindow, finalMessage, finalMessageType);
			}
		}, "PlayScenarioThread");

		playThread.start();
		stopRequested = false;
	}

	public synchronized void stopPlayback() {
		stopRequested = true;
		if (playThread != null && playThread.isAlive()) {
			log.info("Stopping playback, interrupting PlayScenarioThread");
			playThread.interrupt();
		}
		stopRequested = false;
	}

	private List<PlayStep> buildStepsFromTable(Map<String, String> nameToValue) {
		List<PlayStep> steps = new ArrayList<>();

		int rowCount = tableModel.getRowCount();
		log.debug("buildStepsFromTable: rowCount={}", rowCount);

		Map<Integer, Boolean> customMethodExpanded = new HashMap<>();

		List<String> indices = new ArrayList<>(rowCount);
		for (int r = 0; r < rowCount; r++) {
			indices.add(Objects.toString(tableModel.getValueAt(r, 0), "").trim());
		}

		for (int r = 0; r < rowCount; r++) {
			Object actionObj = tableModel.getValueAt(r, 1);
			if (!(actionObj instanceof UserAction ua) || ua != UserAction.CUSTOM_METHOD) {
				continue;
			}

			String idx = indices.get(r);
			if (idx.isEmpty()) continue;

			String prefix = idx + ".";

			boolean hasChildren = false;
			for (int rr = r + 1; rr < rowCount; rr++) {
				String childIdx = indices.get(rr);
				if (!childIdx.startsWith(prefix)) {
					break;
				}
				Object refObj = tableModel.getValueAt(rr, 11);
				Object valObj = tableModel.getValueAt(r, 3);
				String methodName = Objects.toString(valObj, "").trim();

				if (refObj != null && refObj.equals(methodName)) {
					hasChildren = true;
					break;
				}
			}

			customMethodExpanded.put(r, hasChildren);
		}

		for (int r = 0; r < rowCount; r++) {
			Object actionObj = tableModel.getValueAt(r, 1);

			if (actionObj instanceof UserAction ua && ua == UserAction.CUSTOM_METHOD) {
				Boolean expanded = customMethodExpanded.get(r);
				if (expanded != null && expanded) {
					log.trace("Row {}: CUSTOM_METHOD with expanded steps, skip top-level", r + 1);
					continue;
				}
			}

			String actionCode = extractAction(r);
			if (actionCode == null || actionCode.isBlank()) {
				log.trace("Row {}: empty actionCode, skip", r + 1);
				continue;
			}

			PlayStep step = new PlayStep();
			step.rowIndex = r;
			step.actionCode = actionCode;
			step.selector = val(r, 2);
			String rawValue = val(r, 3);
			step.javaClassName = val(r, 5);
			step.xpath = val(r, 6);
			step.name = val(r, 7);
			step.index = val(r, 8);
			step.byXpath = val(r, 9);
			step.url = val(r, 10);

			if (rawValue != null && !rawValue.isBlank()) {
				step.value = variablesService.resolveValue(rawValue, nameToValue);
			}

			steps.add(step);
		}

		return steps;
	}

	private void runScenario(ActionWindow actionWindow, List<PlayStep> steps, Map<String, String> nameToValue) {
		for (PlayStep step : steps) {
			if (stopRequested) {
				log.info("Playback stopped by user before step {}", step.rowIndex + 1);
				break;
			}

			currentRow = step.rowIndex;
			SwingUtilities.invokeLater(actionWindow::repaintActionTable);
			playOneStep(step, nameToValue);
		}
	}

	private void playOneStep(PlayStep step, Map<String, String> nameToValue) throws RuntimeException {
		String action = step.actionCode;
		String selector = step.selector;
		String value = step.value;
		String javaClassName = step.javaClassName;
		String url = step.url;

		boolean passValue = !action.contains("click")
				&& !action.contains("fillDate")
				&& !action.contains("clear");

		log.debug("playOneStep row={}, action={}, class={}, selector={}, value={}",
				step.rowIndex + 1, action, javaClassName, selector, value);

		boolean isSpecial = action.contains("switchTab")
				|| action.contains("fillData")
				|| action.contains("specialAction")
				|| action.contains("waitLoadingPage")
				|| action.contains("pause")
				|| action.contains("auth")
				|| action.contains("open")
				|| action.contains("customMethod")
				|| action.contains("useBackendMethod")
				|| action.contains("assertExists")
				|| action.contains("assertNotExists")
				|| action.contains("refreshPage");

		if (isSpecial) {
			log.info("Row {}: special action '{}'", step.rowIndex + 1, action);
			playSpecialAction(action, selector, value, nameToValue);
			return;
		}

		if (!hasText(selector)) {
			log.warn("Row {}: empty selector, skip", step.rowIndex + 1);
			return;
		}
		if (!hasText(javaClassName)) {
			log.warn("Row {}: empty javaClassName, skip", step.rowIndex + 1);
			return;
		}

		Object element = createElementFromStep(step);
		if (element == null) {
			throw new RuntimeException(
					"Element not created for javaClassName='" + javaClassName +
							"', selector='" + selector + "'"
			);
		}

		switch (javaClassName) {
			case "Button", "TabButton", "LinkButton",
				 "CheckBoxButton", "RadioButton" -> {
				Button button = (Button) element;
				if (action.contains("click")) {
					button.click();
				}
			}
			case "Field", "RichField" -> {
				Field field = (Field) element;
				if (passValue && hasText(value)) {
					field.fill(value);
				} else if (action.contains("clear")) {
					field.clear();
				}
			}
			case "Select" -> {
				Select field = (Select) element;
				String valueToSelect = passValue && hasText(value) ? value : "";
				if (action.contains("selectOptions")) {
					List<String> parts = Arrays.stream(value.split(", "))
							.map(String::trim)
							.filter(p -> !p.isEmpty())
							.toList();
					field.selectOptions(parts);
				} else if (action.contains("selectOption")) {
					field.selectOption(valueToSelect);
				} else if (action.contains("selectExactOption") && !valueToSelect.isEmpty()) {
					field.selectExactOption(valueToSelect);
				} else if (action.contains("clear")) {
					field.clear();
				}
			}
			case "Dropdown" -> {
				Dropdown field = (Dropdown) element;
				String valueToSelect = passValue && hasText(value) ? value : "";
				if (!valueToSelect.isEmpty()) {
					field.selectOption(valueToSelect);
				}
			}
			case "DatePicker" -> {
				DatePicker field = (DatePicker) element;
				if (action.contains("fillDate")) {
					field.fillDate();
				}
			}
			default -> {
				log.warn("Row {}: unknown javaClassName='{}', selector='{}'",
						step.rowIndex + 1, javaClassName, selector);
			}
		}
		if (url == null || url.isEmpty() || url.isBlank()) {
			val urlNow = parseUrl();
			tableModel.setValueAt(urlNow, currentRow, 10);
		}
	}

	private void playSpecialAction(String action, String selector, String value, Map<String, String> nameToValue) {
		boolean hasValue = value != null && !value.isEmpty() && !value.isBlank();
		log.info("playSpecialAction: action='{}', value='{}'", action, value);

		if (action.contains("switchTab")) {
			tabManager.switchToNewTab();
		} else if (action.contains("open")) {
			open(value);
		} else if (action.contains("fillData")) {
			String url = driver.getCurrentUrl();
			log.debug("fillData: currentUrl={}", url);
			String parent = "";
			if (url.contains("/cabinet/requisitions"))
				parent = formFiller.getXpathRequisition();
			else if (url.contains("/personal-account/questionary"))
				parent = formFiller.getXpathLk();
			else if (url.contains("/cabinet/offers/"))
				parent = formFiller.getXpathOffer();
			log.debug("fillData: parent xpath='{}'", parent);
			formFiller.fillRequiredEmptyByLabel(parent);
			formFiller.fillRequiredConfirmationSteps(parent,
					"superuser_1@autotest.rt", "superuser_1@autotest.rt");
		} else if (action.contains("customMethod")) {
			if (!hasValue) {
				throw new IllegalArgumentException("value for action 'customMethod' must be a method name");
			}
			playCustomMethod(value, nameToValue);
		} else if (action.contains("useBackendMethod")) {
			if (!hasValue) {
				throw new IllegalArgumentException("value for action 'useBackendMethod' must be a backend request name");
			}
			String requestName = value.trim();
			playBackendRequest(requestName, nameToValue);
		} else if (action.contains("waitLoadingPage")) {
			if (hasValue) {
				int timeout = Integer.parseInt(value.replaceAll("[\\D]", ""));
				log.debug("waitLoadingPage({})", timeout);
				waitLoadingPage(timeout);
			} else {
				log.debug("waitLoadingPage() default");
				waitLoadingPage();
			}
		} else if (action.contains("refreshPage")) {
			refreshPage();
		} else if (action.equals("assertExists")) {
			if (!isProbablyXPath(selector)) {
				throw new IllegalArgumentException("select must not be null and must be xpath");
			}
			assertExists($x(selector), "element " + selector + " not exists on page");
		} else if (action.equals("assertNotExists")) {
			if (!isProbablyXPath(selector)) {
				throw new IllegalArgumentException("select must not be null and must be xpath");
			}
			assertNotExists($x(selector), "element " + selector + " exists on page");
		} else if (action.equals("auth")) {
			if (hasValue) {
				log.info("auth with user '{}'", value);
				UsersServiceSpec user = usersService.getUser(value);
				new Auth().logIN(user.username, user.password);
			} else {
				throw new IllegalArgumentException("value for action 'auth' must not be null");
			}
		} else if (action.contains("pause")) {
			try {
				if (hasValue) {
					int seconds = Integer.parseInt(value.replaceAll("[\\D]", ""));
					log.debug("pause {} seconds", seconds);
					TimeUnit.MILLISECONDS.sleep(seconds * 1000L);
				} else {
					log.debug("pause default 300 ms");
					TimeUnit.MILLISECONDS.sleep(300);
				}
			} catch (InterruptedException e) {
				log.warn("pause interrupted", e);
				throw new RuntimeException(e);
			}
		} else if (action.contains("specialAction")) {
			log.info("specialAction placeholder executed");
		}
	}

	private void playCustomMethod(String methodName, Map<String, String> nameToValue) {
		log.info("playCustomMethod: '{}'", methodName);

		java.util.List<ActionRecord> methodSteps =
				customMethodsService.loadMethodSteps(methodName);
		if (methodSteps.isEmpty()) {
			log.warn("Custom method '{}' has no steps", methodName);
			return;
		}

		for (ActionRecord dto : methodSteps) {
			if (stopRequested) {
				log.info("Playback stopped inside custom method '{}'", methodName);
				break;
			}

			PlayStep step = new PlayStep();
			step.rowIndex = -1;
			step.actionCode = dto.getAction();
			step.selector = dto.getSelector();
			String rawValue = dto.getValue();
			step.javaClassName = dto.getElementType();
			step.xpath = dto.getXpath();
			step.name = dto.getName();
			step.index = dto.getIndex();
			step.byXpath = dto.getByXpath();

			log.debug(
					"customStep={\"method\":\"{}\",\"action\":\"{}\",\"javaClassName\":\"{}\",\"selector\":\"{}\",\"xpath\":\"{}\",\"name\":\"{}\",\"index\":\"{}\",\"byXpath\":\"{}\"}",
					methodName,
					nullSafe(step.actionCode),
					nullSafe(step.javaClassName),
					nullSafe(step.selector),
					nullSafe(step.xpath),
					nullSafe(step.name),
					nullSafe(step.index),
					nullSafe(step.byXpath)
			);

			if (rawValue != null && !rawValue.isBlank()) {
				step.value = variablesService.resolveValue(rawValue, nameToValue);
			}

			playOneStep(step, nameToValue);
		}
	}

	private WebElement findByXpath(String xpath) {
		return driver.findElement(By.xpath(xpath));
	}

	private String val(int row, int col) {
		Object v = tableModel.getValueAt(row, col);
		return v == null ? null : v.toString();
	}

	private String extractAction(int row) {
		Object actionObj = tableModel.getValueAt(row, 1);
		if (actionObj instanceof UserAction) {
			return ((UserAction) actionObj).getCode();
		}
		return actionObj != null ? actionObj.toString() : null;
	}

	private boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	private void showErrorOnUi(ActionWindow parent, String message) {
		SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(
						parent,
						message,
						"Playback error",
						JOptionPane.ERROR_MESSAGE
				)
		);
	}

	private void showInfoOnUi(ActionWindow parent, String message) {
		SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(
						parent,
						message,
						"Playback",
						JOptionPane.INFORMATION_MESSAGE
				)
		);
	}

	private Object createElementFromStep(PlayStep step) {
		String type = step.javaClassName;
		String name = step.name;
		String xpath = step.xpath;
		String indexStr = step.index;
		String selector = step.selector;

		if (hasText(name) && hasText(xpath) && hasText(indexStr)) {
			int index;
			try {
				index = Integer.parseInt(indexStr.trim());
			} catch (NumberFormatException e) {
				index = 1;
			}
			String indexedXpath = "(" + xpath + ")[" + index + "]";

			return switch (type) {
				case "Field" -> new Field("", $x(indexedXpath));
				case "RichField" -> new RichField("", $x(indexedXpath));
				case "Select" -> new Select("", $x(indexedXpath));
				case "Dropdown" -> new Dropdown("", $x(indexedXpath));
				case "DatePicker" -> new DatePicker("", $x(indexedXpath));
				case "Button", "TabButton", "LinkButton",
					 "CheckBoxButton", "RadioButton" -> new Button("", $x(indexedXpath));
				default -> null;
			};
		}

		if (!hasText(selector)) {
			return null;
		}

		String trimmed = selector.trim();

		if (isProbablyXPath(trimmed)) {
			return switch (type) {
				case "Field" -> new Field("", $x(trimmed));
				case "RichField" -> new RichField("", $x(trimmed));
				case "Select" -> new Select("", $x(trimmed));
				case "Dropdown" -> new Dropdown("", $x(trimmed));
				case "DatePicker" -> new DatePicker("", $x(trimmed));
				case "Button", "TabButton", "LinkButton",
					 "CheckBoxButton", "RadioButton" -> new Button("", $x(trimmed));
				default -> null;
			};
		}

		if (hasCommaSpacesDigitAndNoLettersAfter(trimmed)) {
			String[] parts = trimmed.split(",");
			if (parts.length >= 2) {
				String namePart = parts[0].trim();
				String indexPart = parts[1].trim();

				int index;
				try {
					index = Integer.parseInt(indexPart);
				} catch (NumberFormatException e) {
					index = 1;
				}

				return switch (type) {
					case "Field" -> new Field(namePart, index);
					case "RichField" -> new RichField(namePart, index);
					case "Select" -> new Select(namePart, index);
					case "Dropdown" -> new Dropdown(namePart, index);
					case "DatePicker" -> new DatePicker(namePart, index);
					case "Button", "TabButton", "LinkButton",
						 "CheckBoxButton", "RadioButton" -> new Button(namePart, index);
					default -> null;
				};
			}
		}

		String nameFromSelector = trimmed;

		return switch (type) {
			case "Field" -> new Field(nameFromSelector);
			case "RichField" -> new RichField(nameFromSelector);
			case "Select" -> new Select(nameFromSelector);
			case "Dropdown" -> new Dropdown(nameFromSelector);
			case "DatePicker" -> new DatePicker(nameFromSelector);
			case "Button", "TabButton", "LinkButton",
				 "CheckBoxButton", "RadioButton" -> new Button(nameFromSelector);
			default -> null;
		};
	}

	private String nullSafe(String v) {
		if (v == null) return "";
		return v.replace("\"", "\\\"");
	}

	public int getCurrentRow() {
		return currentRow;
	}

	private static class PlayStep {
		int rowIndex;
		String actionCode;
		String selector;
		String value;
		String javaClassName;
		String xpath;
		String name;
		String index;
		String byXpath;
		String url;
	}

	private class Auth {
		private final Field emailField = new Field("E-mail");
		private final Field passwordField = new Field("Пароль");
		private final Button authButton = new Button("Далее");

		public void logIN(String username, String password) {
			emailField.fill(username);
			passwordField.fill(password);
			authButton.click();
		}
	}

	private String parseUrl() {
		val cur = WebDriverRunner.getWebDriver().getCurrentUrl();
		String pageUrlPath = "";
		try {
			URI uri = new URI(cur);
			String path = uri.getPath();
			if (path != null && !path.isBlank()) {
				pageUrlPath = path;
			}
		} catch (Exception e) {
			int idx = cur.indexOf("://");
			if (idx >= 0) {
				int slash = cur.indexOf('/', idx + 3);
				pageUrlPath = (slash >= 0) ? cur.substring(slash) : "/";
			} else {
				pageUrlPath = cur;
			}
		}
		return pageUrlPath;
	}

	private void playBackendRequest(String requestName, Map<String, String> nameToValue) {
		BackendRequestDef def = backendRequestsService.findByName(requestName);
		if (def == null) {
			throw new IllegalArgumentException("Backend request not found: " + requestName);
		}

		String url = resolveBackendTemplate(def.getUrl(), nameToValue);
		String method = def.getMethod() != null ? def.getMethod().toUpperCase() : "GET";
		String body = def.getRequestBody() != null ? def.getRequestBody() : "";
		String headers = def.getRequestHeaders() != null && !def.getRequestHeaders().isBlank()
				? def.getRequestHeaders()
				: "{}";

		body = applyUniqueFieldMethods(body, def, nameToValue);
		body = resolveBackendTemplate(body, nameToValue);
		headers = resolveBackendTemplate(headers, nameToValue);

		try {
			Object raw = ((JavascriptExecutor) driver).executeAsyncScript(
					"var callback = arguments[arguments.length - 1];" +
							"fetch(" + toJsString(url) + ", {" +
							"  method: '" + method + "'," +
							"  headers: " + headers + "," +
							(body.isBlank() ? "" : "  body: " + toJsString(body) + ",") +
							"  credentials: 'include'" +
							"})" +
							".then(async function(response) {" +
							"  var text;" +
							"  try {" +
							"    var cloned = response.clone();" +
							"    var json = await cloned.json();" +
							"    text = JSON.stringify(json, null, 2);" +
							"  } catch (e) {" +
							"    text = await response.text();" +
							"  }" +
							"  callback({" +
							"    ok: response.ok," +
							"    status: response.status," +
							"    url: response.url || " + toJsString(url) + "," +
							"    method: " + toJsString(method) + "," +
							"    body: text" +
							"  });" +
							"})" +
							".catch(function(error) {" +
							"  callback({" +
							"    ok: false," +
							"    status: 0," +
							"    url: " + toJsString(url) + "," +
							"    method: " + toJsString(method) + "," +
							"    body: 'ERROR: ' + String(error)" +
							"  });" +
							"});"
			);

			BackendExecutionResult result = new BackendExecutionResult();
			result.requestName = requestName;
			result.method = method;
			result.url = url;
			result.success = true;
			result.responseBody = "";

			if (raw instanceof Map<?, ?> map) {
				Object methodObj = map.get("method");
				Object urlObj = map.get("url");
				Object bodyObj = map.get("body");
				Object okObj = map.get("ok");
				Object statusObj = map.get("status");

				result.method = methodObj != null ? String.valueOf(methodObj) : method;
				result.url = urlObj != null ? String.valueOf(urlObj) : url;
				result.responseBody = bodyObj != null ? String.valueOf(bodyObj) : "";
				result.success = okObj instanceof Boolean ? (Boolean) okObj : true;

				log.info(
						"Backend request '{}' executed. method={}, url={}, status={}, ok={}",
						requestName,
						result.method,
						result.url,
						statusObj,
						result.success
				);
			} else {
				result.responseBody = raw != null ? String.valueOf(raw) : "";
				log.info("Backend request '{}' executed. Raw result={}", requestName, raw);
			}

			backendExecutionResults.add(result);

		} catch (Exception ex) {
			BackendExecutionResult result = new BackendExecutionResult();
			result.requestName = requestName;
			result.method = method;
			result.url = url;
			result.success = false;
			result.responseBody = "ERROR: " + ex.getMessage();
			backendExecutionResults.add(result);

			throw new RuntimeException(
					"Failed to execute backend request '" + requestName + "': " + ex.getMessage(),
					ex
			);
		}
	}

	private String applyUniqueFieldMethods(String body, BackendRequestDef def, Map<String, String> nameToValue) {
		if (body == null || body.isBlank() || def == null) {
			return body;
		}

		try {
			Object fieldOverridesObj = def.getClass().getMethod("getFieldOverrides").invoke(def);
			if (!(fieldOverridesObj instanceof List<?> overrides) || overrides.isEmpty()) {
				return body;
			}

			String result = body;
			for (Object override : overrides) {
				if (override == null) {
					continue;
				}

				Boolean unique = invokeBooleanGetter(override, "isUnique", "getUnique");
				if (!Boolean.TRUE.equals(unique)) {
					continue;
				}

				String fieldPath = invokeStringGetter(override, "getFieldPath", "getName", "getFieldName", "getJsonPath");
				if (fieldPath == null || fieldPath.isBlank()) {
					continue;
				}

				String methodExpr = resolveOverrideMethodExpression(override);
				if (methodExpr == null || methodExpr.isBlank()) {
					continue;
				}

				String generatedValue = variablesService.resolveValue(methodExpr, nameToValue);
				result = replaceJsonFieldValue(result, fieldPath, generatedValue);
			}
			return result;
		} catch (NoSuchMethodException ignored) {
			return body;
		} catch (Exception ex) {
			log.warn("Failed to apply unique field methods for backend DTO '{}': {}", def.getName(), ex.getMessage(), ex);
			return body;
		}
	}

	private String resolveOverrideMethodExpression(Object override) {
		String raw = invokeStringGetter(override,
				"getValue",
				"getMethodExpression",
				"getMethodValue",
				"getExpression");
		if (raw != null && !raw.isBlank()) {
			return raw.trim();
		}

		String method = invokeStringGetter(override, "getMethod", "getGeneratorMethod", "getAction");
		if (method == null || method.isBlank()) {
			return null;
		}
		method = method.trim();

		String arg = invokeStringGetter(override, "getMethodArg", "getArgument", "getArg");
		if ("addUuid".equals(method)) {
			return "addUuid(" + (arg == null ? "" : arg) + ")";
		}
		if (method.endsWith("()") || method.contains("(")) {
			return method;
		}
		return method + "()";
	}

	private String replaceJsonFieldValue(String json, String fieldPath, String newValue) {
		if (json == null || json.isBlank() || fieldPath == null || fieldPath.isBlank()) {
			return json;
		}

		String fieldName = fieldPath;
		int dotIndex = fieldPath.lastIndexOf('.');
		if (dotIndex >= 0 && dotIndex + 1 < fieldPath.length()) {
			fieldName = fieldPath.substring(dotIndex + 1);
		}

		String escapedFieldName = Pattern.quote(fieldName);
		String escapedValue = Matcher.quoteReplacement(escapeJsonValue(newValue));

		String stringPattern = "(\\\"" + escapedFieldName + "\\\"\\s*:\\s*\\\")([^\\\"]*)(\\\")";
		String replaced = json.replaceAll(stringPattern, "$1" + escapedValue + "$3");
		if (!replaced.equals(json)) {
			return replaced;
		}

		String nullPattern = "(\\\"" + escapedFieldName + "\\\"\\s*:\\s*)null";
		replaced = json.replaceAll(nullPattern, "$1\"" + escapedValue + "\"");
		if (!replaced.equals(json)) {
			return replaced;
		}

		String primitivePattern = "(\\\"" + escapedFieldName + "\\\"\\s*:\\s*)(true|false|-?\\d+(?:\\.\\d+)?)";
		return json.replaceAll(primitivePattern, "$1\"" + escapedValue + "\"");
	}

	private String resolveBackendTemplate(String raw, Map<String, String> nameToValue) {
		if (raw == null || raw.isBlank()) {
			return raw;
		}

		String resolved = raw;
		Pattern p = Pattern.compile("\\$\\{([^}]+)}");
		Matcher matcher = p.matcher(resolved);
		StringBuffer sb = new StringBuffer();
		boolean changed = false;

		while (matcher.find()) {
			String varName = matcher.group(1);
			String varValue = nameToValue.get(varName);
			if (varValue == null) {
				try {
					String formatted = variablesService.getVariableValueByNameFormatted(varName);
					varValue = variablesService.resolveValue(formatted, nameToValue);
					nameToValue.put(varName, varValue);
				} catch (Exception ignored) {
					varValue = matcher.group(0);
				}
			}
			matcher.appendReplacement(sb, Matcher.quoteReplacement(varValue));
			changed = true;
		}
		matcher.appendTail(sb);

		if (!changed) {
			return variablesService.resolveValue(resolved, nameToValue);
		}
		return sb.toString();
	}

	private String invokeStringGetter(Object target, String... methodNames) {
		for (String methodName : methodNames) {
			try {
				Object value = target.getClass().getMethod(methodName).invoke(target);
				return value == null ? null : String.valueOf(value);
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private Boolean invokeBooleanGetter(Object target, String... methodNames) {
		for (String methodName : methodNames) {
			try {
				Object value = target.getClass().getMethod(methodName).invoke(target);
				if (value instanceof Boolean b) {
					return b;
				}
				if (value != null) {
					return Boolean.parseBoolean(String.valueOf(value));
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private String escapeJsonValue(String s) {
		if (s == null) {
			return "";
		}
		return s
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	private void onScenarioFinishedWithBackendAnswers(ActionWindow actionWindow, String message, int messageType) {
		SwingUtilities.invokeLater(() -> {
			actionWindow.onScenarioFinished();

			if (backendExecutionResults.isEmpty()) {
				JOptionPane.showMessageDialog(
						actionWindow,
						message,
						"Playback finished",
						messageType
				);
				return;
			}

			Object[] options = {"OK", "View backend answers"};
			int choice = JOptionPane.showOptionDialog(
					actionWindow,
					message,
					"Playback finished",
					JOptionPane.YES_NO_OPTION,
					messageType,
					null,
					options,
					options[0]
			);

			if (choice == 1) {
				showBackendAnswersDialog(actionWindow);
			}
		});
	}

	private void showBackendAnswersDialog(ActionWindow parent) {
		StringBuilder sb = new StringBuilder();

		synchronized (backendExecutionResults) {
			for (int i = 0; i < backendExecutionResults.size(); i++) {
				BackendExecutionResult r = backendExecutionResults.get(i);

				if (i > 0) {
					sb.append("\n\n");
				}

				sb.append("==================================================\n");
				sb.append(r.method != null ? r.method : "UNKNOWN");
				sb.append(" ");
				sb.append(r.url != null ? r.url : "");
				sb.append("\n");
				sb.append("==================================================\n");

				String responseBody = r.responseBody == null ? "" : r.responseBody.trim();
				if (responseBody.isEmpty()) {
					sb.append("<empty response>");
				} else {
					sb.append(responseBody);
				}
			}
		}

		JTextArea textArea = new JTextArea(sb.toString(), 28, 110);
		textArea.setEditable(false);
		textArea.setCaretPosition(0);
		textArea.setLineWrap(false);
		textArea.setWrapStyleWord(false);
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		JDialog dialog = new JDialog(
				SwingUtilities.getWindowAncestor(parent),
				"Backend answers log",
				Dialog.ModalityType.APPLICATION_MODAL
		);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setLayout(new BorderLayout());

		JLabel title = new JLabel("Executed backend methods responses");
		title.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dialog.dispose());

		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bottomPanel.add(closeButton);

		dialog.add(title, BorderLayout.NORTH);
		dialog.add(scrollPane, BorderLayout.CENTER);
		dialog.add(bottomPanel, BorderLayout.SOUTH);

		dialog.setSize(1000, 700);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	private String toJsString(String s) {
		if (s == null) return "''";
		return "'" + s
				.replace("\\", "\\\\")
				.replace("'", "\\'")
				.replace("\n", "\\n")
				.replace("\r", "\\r") + "'";
	}

	private static class BackendExecutionResult {
		String requestName;
		String method;
		String url;
		String responseBody;
		boolean success;
	}
}