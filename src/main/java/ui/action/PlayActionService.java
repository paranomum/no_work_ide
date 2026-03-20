package ui.action;

import com.codeborne.selenide.WebDriverRunner;
import dto.ActionRecord;
import dto.UsersServiceSpec;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import model.UserAction;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import ru.rt.iqhr.framework.config.FrameworkConfig;
import ru.rt.iqhr.framework.pageobject.react.web_elements.buttons.Button;
import ru.rt.iqhr.framework.pageobject.react.web_elements.triggers.Dropdown;
import ru.rt.iqhr.framework.util.FormFiller;
import ru.rt.iqhr.framework.util.TabManager;
import ui.ActionWindow;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

import ru.rt.iqhr.framework.pageobject.react.web_elements.*;

import static com.codeborne.selenide.Selenide.$x;
import static com.codeborne.selenide.Selenide.open;
import static ru.rt.iqhr.framework.util.StringUtils.*;
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
	private final VariablesService variablesService;

	private Thread playThread;
	@Getter @Setter
	private volatile boolean stopRequested = false;
	private volatile int currentRow = -1;

	private static final Logger log = LoggerFactory.getLogger(PlayActionService.class);

	public PlayActionService(DefaultTableModel tableModel, UsersService usersService, CustomMethodsService customMethodsService, VariablesService variablesService) {
		FrameworkConfig.setSpeedMode(FrameworkConfig.SpeedMode.FAST);
		this.tableModel = tableModel;
		formFiller = new FormFiller();
		this.usersService = usersService;
		this.customMethodsService = customMethodsService;
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

		List<PlayStep> steps = buildStepsFromTable();
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

		playThread = new Thread(() -> {
			try {
				WebDriverRunner.setWebDriver(driver);
				runScenario(actionWindow, steps);
				if (!stopRequested) {
					showInfoOnUi(actionWindow, "Scenario finished successfully");
				}
			} catch (Throwable e) {
				showErrorOnUi(actionWindow, "Stopped on step " + currentRow + ".\n" + e.getMessage());
				log.error("Unexpected error in PlayScenarioThread", e);
			} finally {
				stopPlayback();
				currentRow = -1;
				SwingUtilities.invokeLater(actionWindow::repaintActionTable);
				actionWindow.onScenarioFinished();
			}
		}, "PlayScenarioThread");

		playThread.start();
	}

	public synchronized void stopPlayback() {
		stopRequested = true;
		if (playThread != null && playThread.isAlive()) {
			log.info("Stopping playback, interrupting PlayScenarioThread");
			playThread.interrupt();
		}
	}

	private List<PlayStep> buildStepsFromTable() {
		List<PlayStep> steps = new ArrayList<>();
		Map<String, String> nameToValue = new HashMap<>();

		int rowCount = tableModel.getRowCount();
		log.debug("buildStepsFromTable: rowCount={}", rowCount);

		for (int r = 0; r < rowCount; r++) {
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

			// вся магия теперь внутри VariablesService
			step.value = variablesService.resolveValue(rawValue, nameToValue);

			log.debug(
					"rowData={\"rowIndex\":{},\"actionCode\":\"{}\",\"javaClassName\":\"{}\",\"selector\":\"{}\",\"xpath\":\"{}\",\"name\":\"{}\",\"index\":\"{}\",\"byXpath\":\"{}\"}",
					r + 1,
					nullSafe(actionCode),
					nullSafe(step.javaClassName),
					nullSafe(step.selector),
					nullSafe(step.xpath),
					nullSafe(step.name),
					nullSafe(step.index),
					nullSafe(step.byXpath),
					nullSafe(step.url)
			);

			steps.add(step);
		}

		return steps;
	}

	private void runScenario(ActionWindow actionWindow, List<PlayStep> steps) {
		for (PlayStep step : steps) {
			if (stopRequested) {
				log.info("Playback stopped by user before step {}", step.rowIndex + 1);
				showInfoOnUi(actionWindow, "Playback stopped on step " + (step.rowIndex - 1));
				break;
			}

			currentRow = step.rowIndex;
			SwingUtilities.invokeLater(actionWindow::repaintActionTable);
			playOneStep(step);
		}
	}


	private void playOneStep(PlayStep step) throws RuntimeException {
		String action        = step.actionCode;
		String selector      = step.selector;
		String value         = step.value;
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
				|| action.contains("assertExists")
				|| action.contains("assertNotExists");

		if (isSpecial) {
			log.info("Row {}: special action '{}'", step.rowIndex + 1, action);
			playSpecialAction(action, selector, value);
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
							.map(String::trim)   // на случай лишних пробелов
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

	// ---- спец‑действия ----

	private void playSpecialAction(String action, String selector, String value){
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
			playCustomMethod(value);
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
			}
			else {
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

	private void playCustomMethod(String methodName) {
		log.info("playCustomMethod: '{}'", methodName);

		java.util.List<ActionRecord> methodSteps =
				customMethodsService.loadMethodSteps(methodName);
		if (methodSteps.isEmpty()) {
			log.warn("Custom method '{}' has no steps", methodName);
			return;
		}

		// конвертируем CustomMethodStepDto -> PlayStep и прогоняем той же логикой
		for (ActionRecord dto : methodSteps) {
			if (!stopRequested) {
				log.info("Playback stopped inside custom method '{}'", methodName);
				break;
			}

			PlayStep step = new PlayStep();
			step.rowIndex      = -1; // внешней строки нет, это внутренний шаг
			step.actionCode    = dto.getAction();
			step.selector      = dto.getSelector();
			step.value         = dto.getValue();
			step.javaClassName = dto.getElementType();
			step.xpath         = dto.getXpath();
			step.name          = dto.getName();
			step.index         = dto.getIndex();
			step.byXpath       = dto.getByXpath();

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

			// здесь важно: playOneStep уже умеет обработать и спец‑действия, и обычные
			playOneStep(step);
		}
	}


	private WebElement findByXpath(String xpath) {
		// Здесь можно навесить ожидания (WebDriverWait) и обработку ошибок.
		return driver.findElement(By.xpath(xpath)); // [web:51][web:60]
	}

	// ---- helpers ----

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
		String type     = step.javaClassName;
		String name     = step.name;      // col 7
		String xpath    = step.xpath;     // col 6
		String indexStr = step.index;  // col 8
		String selector = step.selector;  // col 2

		// --- 1. Вариант с name + xpath + index (как ты хочешь) ---
		if (hasText(name) && hasText(xpath) && hasText(indexStr)) {
			int index;
			try {
				index = Integer.parseInt(indexStr.trim());
			} catch (NumberFormatException e) {
				index = 1;
			}
			String indexedXpath = "(" + xpath + ")[" + index + "]";

			return switch (type) {
				case "Field"      -> new Field("", $x(indexedXpath));
				case "RichField"      -> new RichField("", $x(indexedXpath));
				case "Select"     -> new Select("", $x(indexedXpath));
				case "Dropdown"   -> new Dropdown("", $x(indexedXpath));
				case "DatePicker" -> new DatePicker("", $x(indexedXpath));

				case "Button", "TabButton", "LinkButton",
					 "CheckBoxButton", "RadioButton" -> new Button("", $x(indexedXpath));

				default -> null; // неизвестный тип — пусть разберётся вызывающий код
			};
		}

		// --- 2. Fallback: логика по selector, как в ветке !hasName ---

		if (!hasText(selector)) {
			return null;
		}

		String trimmed = selector.trim();

		// 2.1. Selector похож на xpath → $x(selector)
		if (isProbablyXPath(trimmed)) {
			return switch (type) {
				case "Field"      -> new Field("", $x(trimmed));
				case "RichField"  -> new RichField("", $x(trimmed));
				case "Select"     -> new Select("", $x(trimmed));
				case "Dropdown"   -> new Dropdown("", $x(trimmed));
				case "DatePicker" -> new DatePicker("", $x(trimmed));

				case "Button", "TabButton", "LinkButton",
					 "CheckBoxButton", "RadioButton" -> new Button("", $x(trimmed));

				default -> null;
			};
		}

		// 2.2. Формат "name, index"
		if (hasCommaSpacesDigitAndNoLettersAfter(trimmed)) {
			String[] parts = trimmed.split(",");
			if (parts.length >= 2) {
				String namePart  = parts[0].trim();
				String indexPart = parts[1].trim();

				int index;
				try {
					index = Integer.parseInt(indexPart);
				} catch (NumberFormatException e) {
					index = 1;
				}

				return switch (type) {
					case "Field"      -> new Field(namePart, index);
					case "RichField"  -> new RichField(namePart, index);
					case "Select"     -> new Select(namePart, index);
					case "Dropdown"   -> new Dropdown(namePart, index);
					case "DatePicker" -> new DatePicker(namePart, index);

					case "Button", "TabButton", "LinkButton",
						 "CheckBoxButton", "RadioButton" -> new Button(namePart, index);

					default -> null;
				};
			}
		}

		// 2.3. Иначе считаем, что selector — это просто name
		String nameFromSelector = trimmed;

		return switch (type) {
			case "Field"      -> new Field(nameFromSelector);
			case "RichField"  -> new RichField(nameFromSelector);
			case "Select"     -> new Select(nameFromSelector);
			case "Dropdown"   -> new Dropdown(nameFromSelector);
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
			String path = uri.getPath();      // /auth/login или /cabinet/users-and-groups/users
			String query = uri.getQuery();    // при желании можно добавить
			if (path != null && !path.isBlank()) {
				pageUrlPath = path;
			}
		} catch (Exception e) {
			// fallback без URI
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
}