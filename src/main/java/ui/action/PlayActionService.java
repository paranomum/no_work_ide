package ui.action;

import com.codeborne.selenide.WebDriverRunner;
import com.google.gson.*;
import dto.*;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.val;
import model.UserAction;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rt.iqhr.framework.config.FrameworkConfig;
import ru.rt.iqhr.framework.pageobject.react.web_elements.DatePicker;
import ru.rt.iqhr.framework.pageobject.react.web_elements.Field;
import ru.rt.iqhr.framework.pageobject.react.web_elements.RichField;
import ru.rt.iqhr.framework.pageobject.react.web_elements.Select;
import ru.rt.iqhr.framework.pageobject.react.web_elements.buttons.Button;
import ru.rt.iqhr.framework.pageobject.react.web_elements.triggers.Dropdown;
import ru.rt.iqhr.framework.util.FormFiller;
import ru.rt.iqhr.framework.util.TabManager;
import ui.ActionWindow;
import ui.action.iqhr_only.FunnelMoveRequestDef;
import ui.action.iqhr_only.FunnelMoveService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Selenide.$x;
import static com.codeborne.selenide.Selenide.open;
import static ru.rt.iqhr.framework.util.WebElementUtil.*;
import static ru.rt.iqhr.framework.util.XPathUtils.isProbablyXPath;
import static ui.action.ActionFileService.hasCommaSpacesDigitAndNoLettersAfter;

public class PlayActionService {

	private static final Logger log = LoggerFactory.getLogger(PlayActionService.class);
	private final DefaultTableModel tableModel;
	private final TabManager tabManager = new TabManager();
	private final FormFiller formFiller;
	private final UsersService usersService;
	private final CustomMethodsService customMethodsService;
	private final BackendRequestsService backendRequestsService;
	private final JagaBugReportsService jagaBugReportsService;
	private final VariablesService variablesService;
	private final List<BackendExecutionResult> backendExecutionResults =
			Collections.synchronizedList(new ArrayList<>());
	@Setter
	private WebDriver driver;
	private Map<String, ScenarioBackendConfig> currentScenarioOverrides;
	private Thread playThread;
	@Getter
	@Setter
	private volatile boolean stopRequested = false;
	private volatile int currentRow = -1;
	private volatile ActionWindow currentActionWindow;

	// НОВОЕ: задержка перед каждым шагом в миллисекундах (0 = выключено)
	@Getter
	@Setter
	private volatile long stepDelayMs = 0;

	public PlayActionService(DefaultTableModel tableModel,
							 UsersService usersService,
							 CustomMethodsService customMethodsService,
							 BackendRequestsService backendRequestsService,
							 VariablesService variablesService,
							 JagaBugReportsService jagaBugReportsService) {
		FrameworkConfig.setSpeedMode(FrameworkConfig.SpeedMode.FAST);
		this.tableModel = tableModel;
		formFiller = new FormFiller();
		this.usersService = usersService;
		this.customMethodsService = customMethodsService;
		this.backendRequestsService = backendRequestsService;
		this.variablesService = variablesService;
		this.jagaBugReportsService = jagaBugReportsService;
		log.info("PlayActionService created, speedMode=FAST");
	}

	private static String truncate(String s, int maxLen) {
		if (s == null) return "";
		return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
	}

	public void setCurrentScenarioOverrides(Map<String, ScenarioBackendConfig> overrides) {
		this.currentScenarioOverrides = overrides;
	}

	public void playActionsFromTable(ActionWindow actionWindow, int startRowIndex, boolean onlyOne) {
		if (driver == null) {
			log.warn("playActionsFromTable: WebDriver is null, showing browser-required dialog");
			JOptionPane.showMessageDialog(
					actionWindow,
					"Браузер не открыт. Для начала нажмите на кнопку \"Открыть браузер\".",
					"Требуется запуск браузера",
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

		// УЛУЧШЕНИЕ 1: запоминаем окно и сбрасываем старые backend-метки
		currentActionWindow = actionWindow;
		actionWindow.clearBackendMarks();

		Object actionObj = tableModel.getValueAt(startRowIndex, 1);
		if (actionObj instanceof UserAction ua && ua == UserAction.CUSTOM_METHOD) {

			String methodName = Objects.toString(tableModel.getValueAt(startRowIndex, 3), "").trim();

			if (onlyOne) {
				Map<String, String> nameToValue = variablesService.buildAllVariableValuesMap();
				System.out.println("PlayActionService variablesService.getVariables() = " + variablesService.getVariables());
				System.out.println("PlayActionService nameToValue = " + nameToValue);
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
		System.out.println("PlayActionService variablesService.getVariables() = " + variablesService.getVariables());
		System.out.println("PlayActionService nameToValue = " + nameToValue);
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

			// БАГ 3 FIX: сохраняем сырое значение, резолвинг переносим в playOneStep
			step.rawValue = rawValue;
			step.value = rawValue; // оставляем для совместимости, перезапишется в playOneStep

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

			if (stepDelayMs > 0) {
				try {
					Thread.sleep(stepDelayMs);
				} catch (InterruptedException e) {
					log.warn("Step delay interrupted before row {}", step.rowIndex + 1, e);
				} catch (Exception e) {
					log.warn("Failed to apply step delay before row {}", step.rowIndex + 1, e);
				}
			}

			currentRow = step.rowIndex;
			SwingUtilities.invokeLater(actionWindow::repaintActionTable);
			playOneStep(step, nameToValue);
		}
	}

	private void playOneStep(PlayStep step, Map<String, String> nameToValue) throws RuntimeException {
		String action = step.actionCode;
		String selector = step.selector;
		String javaClassName = step.javaClassName;
		String url = step.url;

		// БАГ 3 FIX: резолвим rawValue прямо перед использованием,
		// чтобы подхватить значения переменных, извлечённых предыдущими шагами
		String value;
		if (step.rawValue != null && !step.rawValue.isBlank()) {
			value = variablesService.resolveValue(step.rawValue, nameToValue);
		} else {
			value = step.value;
		}

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
//				|| action.contains("auth")
				|| action.contains("open")
				|| action.contains("customMethod")
				|| action.contains("useBackendMethod")
				|| action.contains("assertExists")
				|| action.contains("assertNotExists")
				|| action.contains("refreshPage")
				|| action.contains("moveCandidateFinal")
				|| action.contains("moveCandidateToJr");

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

		if (action.equals(UserAction.MOVE_FULL.getCode())
				|| action.equals(UserAction.MOVE_TO_JR.getCode())) {
			playFunnelMove(action, value, nameToValue);
			return;
		}
		else if (action.contains("switchTab")) {
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

	private void playFunnelMove(
			String action,
			String rawValue,
			Map nameToValue
	) {
		FunnelMoveRequestDef request = parseFunnelMoveRequest(rawValue);

		Long jrId = resolveFunnelLong("jrId", request.getJrId(), nameToValue);
		Long candidateId = resolveFunnelLong("candidateId", request.getCandidateId(), nameToValue);
		Long vacancyId = resolveFunnelLong("vacancyId", request.getVacancyId(), nameToValue);

		String username = resolveFunnelString("username", request.getUsername(), nameToValue);
		String password = resolveFunnelString("password", request.getPassword(), nameToValue);

		String domain = getCurrentDomain();

		if (UserAction.MOVE_FULL.getCode().equals(action)) {
			FunnelMoveService.processFullCandidateMass(
					jrId, candidateId, vacancyId, username, password, domain
			);
			return;
		}

		if (UserAction.MOVE_TO_JR.getCode().equals(action)) {
			FunnelMoveService.processTillJrCandidateMass(
					jrId, candidateId, vacancyId, username, password, domain
			);
			return;
		}

		throw new IllegalArgumentException("Неизвестное funnel action: " + action);
	}

	private FunnelMoveRequestDef parseFunnelMoveRequest(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"Value для funnel action пустой. Ожидается формат: "
							+ "jrId=...;candidateId=...;vacancyId=...;username=...;password=..."
			);
		}

		Map<String, String> params = new LinkedHashMap<>();

		for (String part : value.split(";")) {
			String[] keyValue = part.split("=", 2);

			if (keyValue.length != 2) {
				throw new IllegalArgumentException(
						"Некорректный параметр: '" + part + "'"
				);
			}

			String key = keyValue[0].trim();
			String fieldValue = keyValue[1].trim();

			if (key.isBlank()) {
				throw new IllegalArgumentException(
						"Обнаружен параметр без имени: '" + part + "'"
				);
			}

			params.put(key, fieldValue);
		}

		validateFunnelParameter(params, "jrId");
		validateFunnelParameter(params, "candidateId");
		validateFunnelParameter(params, "vacancyId");
		validateFunnelParameter(params, "username");
		validateFunnelParameter(params, "password");

		return new FunnelMoveRequestDef(
				params.get("jrId"),
				params.get("candidateId"),
				params.get("vacancyId"),
				params.get("username"),
				params.get("password")
		);
	}

	private void validateFunnelParameter(
			Map<String, String> params,
			String parameterName
	) {
		String value = params.get(parameterName);

		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"Не заполнен обязательный параметр '" + parameterName + "'."
			);
		}
	}

	private String resolveFunnelString(
			String fieldName,
			String rawValue,
			Map nameToValue
	) {
		if (rawValue == null || rawValue.isBlank()) {
			throw new IllegalArgumentException(
					"Не заполнено поле '" + fieldName + "' для funnel action."
			);
		}

		String resolvedValue = variablesService.resolveValue(rawValue, nameToValue);

		if (resolvedValue == null || resolvedValue.isBlank()) {
			throw new IllegalArgumentException(
					"Не удалось получить значение поля '" + fieldName
							+ "'. Исходное значение: " + rawValue
			);
		}

		return resolvedValue.trim();
	}

	private Long resolveFunnelLong(
			String fieldName,
			String rawValue,
			Map nameToValue
	) {
		String resolvedValue = resolveFunnelString(fieldName, rawValue, nameToValue);

		try {
			return Long.valueOf(resolvedValue);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Поле '" + fieldName + "' должно быть числом типа Long. Получено: "
							+ resolvedValue,
					e
			);
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

			// БАГ 3 FIX: сохраняем сырое значение — playOneStep сам резолвит через nameToValue
			String rawValue = dto.getValue();
			step.rawValue = rawValue;
			step.value = rawValue;

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

	private void markBackendRow(int rowIndex, BackendExecutionResult result) {
		if (currentActionWindow == null || rowIndex < 0) return;

		Color color = result.success
				? new Color(0xC8, 0xF0, 0xC8)   // зелёный — успех
				: new Color(0xF7, 0xB7, 0xB7);  // красный — ошибка

		StringBuilder tip = new StringBuilder("<html>");
		tip.append("<b>").append(result.method != null ? result.method : "").append("</b> ");
		tip.append(result.url != null ? result.url : "").append("<br/>");
		tip.append("Status: <b>").append(result.status).append("</b>");
		if (!result.success) {
			tip.append(" ❌");
		} else {
			tip.append(" ✓");
		}
		if (result.extractedVars != null && !result.extractedVars.isEmpty()) {
			tip.append("<br/><i>Extracted:</i>");
			for (Map.Entry<String, String> e : result.extractedVars.entrySet()) {
				tip.append("<br/>&nbsp;&nbsp;${").append(e.getKey()).append("} = ")
						.append(truncate(e.getValue(), 60));
			}
		}
		if (result.warnings != null && !result.warnings.isEmpty()) {
			tip.append("<br/><font color='orange'>");
			for (String w : result.warnings) {
				tip.append(truncate(w, 80)).append("<br/>");
			}
			tip.append("</font>");
		}
		tip.append("</html>");

		final String tooltip = tip.toString();
		SwingUtilities.invokeLater(() -> {
			currentActionWindow.setRowMark(rowIndex, color);
			currentActionWindow.setRowTooltip(rowIndex, tooltip);
		});
	}

	private void showErrorWithBugReportButton(
			ActionWindow parent,
			String message,
			BackendExecutionResult backendResult
	) {
		SwingUtilities.invokeLater(() -> {
			JDialog dialog = new JDialog(
					SwingUtilities.getWindowAncestor(parent),
					"Playback error",
					Dialog.ModalityType.APPLICATION_MODAL
			);
			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

			String dialogText = buildReadableErrorText(message, backendResult);

			JTextArea textArea = new JTextArea(dialogText, 24, 100);
			textArea.setEditable(false);
			textArea.setLineWrap(false);
			textArea.setWrapStyleWord(false);
			textArea.setCaretPosition(0);
			textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
			textArea.setMargin(new Insets(8, 8, 8, 8));

			JScrollPane scrollPane = new JScrollPane(textArea);
			scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			scrollPane.setPreferredSize(new Dimension(900, 500));

			JButton createBugReportButton = new JButton("Создать баг-репорт");
			createBugReportButton.addActionListener(e -> {
				dialog.dispose();

				if (jagaBugReportsService == null) {
					JOptionPane.showMessageDialog(
							parent,
							"JagaBugReportsService недоступен",
							"Ошибка",
							JOptionPane.ERROR_MESSAGE
					);
					return;
				}

				String popupError = safeExtractPopupError();
				String finalError = resolveBugReportError(message, popupError);
				Path curlAttachment = createCurlAttachmentForBackendError(backendResult);

				Integer failedStep = null;
				if (backendResult != null && backendResult.step != null) {
					failedStep = backendResult.step + 1;
				} else if (currentRow >= 0) {
					failedStep = currentRow + 1;
				}

				if (finalError == null) {
					if (curlAttachment != null) {
						jagaBugReportsService.createBugReport(null, curlAttachment, failedStep);
					} else {
						jagaBugReportsService.createBugReport(null, null, failedStep);
					}
				} else {
					if (curlAttachment != null) {
						jagaBugReportsService.createBugReport(finalError, curlAttachment, failedStep);
					} else {
						jagaBugReportsService.createBugReport(finalError, null, failedStep);
					}
				}
			});

			JButton closeButton = new JButton("Закрыть");
			closeButton.addActionListener(e2 -> dialog.dispose());

			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			buttons.add(createBugReportButton);
			buttons.add(closeButton);

			JPanel root = new JPanel(new BorderLayout(10, 10));
			root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
			root.add(scrollPane, BorderLayout.CENTER);
			root.add(buttons, BorderLayout.SOUTH);

			dialog.setContentPane(root);
			dialog.setMinimumSize(new Dimension(700, 300));
			dialog.pack();
			dialog.setSize(Math.min(dialog.getWidth(), 900), Math.min(dialog.getHeight(), 520));
			dialog.setLocationRelativeTo(parent);
			dialog.setVisible(true);
		});
	}

	private String buildReadableErrorText(String message, BackendExecutionResult backendResult) {
		String lineSeparator = System.lineSeparator();
		StringBuilder sb = new StringBuilder();

		String safeMessage = message == null ? "" : message.trim();

		if (backendResult == null) {
			return safeMessage;
		}

		sb.append("Ошибка выполнения backend-запроса").append(lineSeparator);
		sb.append("==================================================").append(lineSeparator);

		if (backendResult.requestName != null && !backendResult.requestName.isBlank()) {
			sb.append("requestName: ").append(backendResult.requestName).append(lineSeparator);
		}

		if (backendResult.step != null) {
			sb.append("stepNumber: ").append(backendResult.step + 1).append(lineSeparator);
		}

		sb.append("method: ").append(backendResult.method != null ? backendResult.method : "").append(lineSeparator);
		sb.append("url: ").append(backendResult.url != null ? backendResult.url : "").append(lineSeparator);
		sb.append("status: ").append(backendResult.status).append(lineSeparator);
		sb.append("success: ").append(backendResult.success).append(lineSeparator);

		if (!safeMessage.isBlank()) {
			sb.append(lineSeparator);
			sb.append("MESSAGE:").append(lineSeparator);
			sb.append("--------------------------------------------------").append(lineSeparator);
			sb.append(safeMessage).append(lineSeparator);
		}

		String responseBody = prettyBackendBody(backendResult.responseBody);
		sb.append(lineSeparator);
		sb.append("RESPONSE BODY:").append(lineSeparator);
		sb.append("--------------------------------------------------").append(lineSeparator);
		sb.append(responseBody.isBlank() ? "<empty response>" : responseBody);

		if (backendResult.warnings != null && !backendResult.warnings.isEmpty()) {
			sb.append(lineSeparator).append(lineSeparator);
			sb.append("WARNINGS:").append(lineSeparator);
			sb.append("--------------------------------------------------").append(lineSeparator);
			for (String warning : backendResult.warnings) {
				sb.append("- ").append(warning).append(lineSeparator);
			}
		}

		return sb.toString().trim();
	}


	private String prettyBackendBody(String body) {
		if (body == null) {
			return "";
		}

		String trimmed = body.trim();
		if (trimmed.isBlank()) {
			return "";
		}

		try {
			JsonElement json = JsonParser.parseString(trimmed);
			return new GsonBuilder()
					.setPrettyPrinting()
					.create()
					.toJson(json);
		} catch (Exception ignored) {
			return trimmed;
		}
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

		List<String> warnings = new ArrayList<>();

		String url = buildFinalBackendUrl(def.getUrl());
		url = resolveBackendTemplate(url, nameToValue);

		String method = def.getMethod() != null ? def.getMethod().toUpperCase(Locale.ROOT) : "GET";
		String bodyType = safeTrim(def.getBodyType()).isEmpty() ? "JSON" : def.getBodyType().trim().toUpperCase(Locale.ROOT);

		String headers = def.getRequestHeaders() != null && !def.getRequestHeaders().isBlank()
				? def.getRequestHeaders()
				: "{}";

		String body;
		if ("FORM_URLENCODED".equals(bodyType)) {
			body = buildResolvedFormBodyWithOverrides(def, nameToValue, warnings);
		} else {
			body = def.getRequestBody() != null ? def.getRequestBody() : "";
			body = applyFieldOverrides(body, def, nameToValue, warnings);
			body = resolveBackendTemplate(body, nameToValue);
		}

		headers = resolveBackendTemplate(headers, nameToValue);
		String resolvedToken = resolveBackendTemplate(def.getToken(), nameToValue);

		BackendExecutionResult result = new BackendExecutionResult();
		result.requestName = requestName;
		result.method = method;
		result.url = url;
		result.success = false;
		result.status = 0L;
		result.responseBody = "";
		result.warnings = new ArrayList<>(warnings);
		result.requestBody = body;
		result.step = getCurrentRow();

		try {
			driver.manage().timeouts().scriptTimeout(java.time.Duration.ofSeconds(30));

			Map<String, String> mergedHeaders = new LinkedHashMap<>();

			try {
				java.lang.reflect.Type headersType =
						new com.google.gson.reflect.TypeToken<Map<String, String>>() {}.getType();
				Map<String, String> parsed = new com.google.gson.Gson().fromJson(headers, headersType);
				if (parsed != null) {
					mergedHeaders.putAll(parsed);
				}
			} catch (Exception ex) {
				warnings.add(
						"WARNING: request headers JSON parse failed, original headers text was ignored. Reason: "
								+ ex.getMessage()
				);
				log.warn("Failed to parse backend request headers JSON for '{}': {}", requestName, ex.getMessage());
			}

			Map<String, String> cookieMap = readBrowserCookies();
			if (cookieMap.isEmpty()) {
				warnings.add("WARNING: browser cookies were not found, backend request may return 401.");
			}

			if (resolvedToken != null && !resolvedToken.isBlank()) {
				cookieMap.put("token", resolvedToken);
			}

			String cookieHeader = buildCookieHeader(cookieMap);
			if (!cookieHeader.isBlank()) {
				mergedHeaders.put("Cookie", cookieHeader);
			}

			if ("FORM_URLENCODED".equals(bodyType)) {
				mergedHeaders.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
			} else if (!mergedHeaders.containsKey("Content-Type") && !body.isBlank()) {
				mergedHeaders.put("Content-Type", "application/json;charset=UTF-8");
			}

			result.requestHeaders = new LinkedHashMap<>(mergedHeaders);
			String finalHeadersJson = new com.google.gson.Gson().toJson(mergedHeaders);
			result.warnings = new ArrayList<>(warnings);

			log.info(
					"Executing backend request '{}'. method={}, url={}, bodyType={}, cookieCount={}, warningsCount={}",
					requestName,
					method,
					url,
					bodyType,
					cookieMap.size(),
					warnings.size()
			);

			Object raw = ((JavascriptExecutor) driver).executeAsyncScript(
					"var callback = arguments[arguments.length - 1];"
							+ "try {"
							+ " var parsedHeaders = JSON.parse(" + toJsString(finalHeadersJson) + ");"
							+ " fetch(" + toJsString(url) + ", {"
							+ "   method: " + toJsString(method) + ","
							+ "   headers: parsedHeaders,"
							+ (body.isBlank() ? "" : "   body: " + toJsString(body) + ",")
							+ "   credentials: 'include'"
							+ " })"
							+ " .then(async function(response) {"
							+ "   var text = '';"
							+ "   var contentType = response.headers.get('content-type') || '';"
							+ "   var statusText = response.statusText || '';"
							+ "   try {"
							+ "     text = await response.text();"
							+ "   } catch (readError) {"
							+ "     text = 'ERROR_READING_BODY: ' + String(readError);"
							+ "   }"
							+ "   if (text && contentType.toLowerCase().indexOf('application/json') >= 0) {"
							+ "     try {"
							+ "       text = JSON.stringify(JSON.parse(text), null, 2);"
							+ "     } catch (ignore) {}"
							+ "   }"
							+ "   callback({"
							+ "     ok: response.ok,"
							+ "     status: response.status,"
							+ "     statusText: statusText,"
							+ "     contentType: contentType,"
							+ "     url: response.url || " + toJsString(url) + ","
							+ "     method: " + toJsString(method) + ","
							+ "     body: text != null ? String(text) : ''"
							+ "   });"
							+ " })"
							+ " .catch(function(error) {"
							+ "   callback({"
							+ "     ok: false,"
							+ "     status: 0,"
							+ "     statusText: 'FETCH_ERROR',"
							+ "     contentType: '',"
							+ "     url: " + toJsString(url) + ","
							+ "     method: " + toJsString(method) + ","
							+ "     body: 'ERROR: ' + String(error)"
							+ "   });"
							+ " });"
							+ "} catch (e) {"
							+ " callback({"
							+ "   ok: false,"
							+ "   status: 0,"
							+ "   statusText: 'SCRIPT_ERROR',"
							+ "   contentType: '',"
							+ "   url: " + toJsString(url) + ","
							+ "   method: " + toJsString(method) + ","
							+ "   body: 'ERROR: ' + String(e)"
							+ " });"
							+ "}"
			);

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
				result.status = statusObj instanceof Number n ? n.longValue() : 0L;

				log.info(
						"Backend request '{}' executed. method={}, url={}, status={}, ok={}, bodyLength={}, warningsCount={}",
						requestName,
						result.method,
						result.url,
						result.status,
						result.success,
						result.responseBody != null ? result.responseBody.length() : 0,
						result.warnings != null ? result.warnings.size() : 0
				);

				if (!result.success) {
					log.warn(
							"Backend request '{}' failed. method={}, url={}, status={}, body={}",
							requestName, result.method, result.url, result.status, result.responseBody
					);
				}
			} else {
				result.responseBody = raw != null ? String.valueOf(raw) : "";
				result.success = true;
				log.info("Backend request '{}' executed. Raw result={}", requestName, raw);
			}

			backendExecutionResults.add(result);

			if (result.responseBody != null && !result.responseBody.isBlank()) {
				extractResponseVariables(def, result.responseBody, nameToValue);
			}

			markBackendRow(currentRow, result);

			if (!result.success) {
				throw new RuntimeException(
						"Backend request '" + requestName + "' failed with status " + result.status
				);
			}

		} catch (Exception ex) {
			result.success = false;
			if (result.responseBody == null || result.responseBody.isBlank()) {
				result.responseBody = "ERROR: " + ex.getMessage();
			}

			if (!backendExecutionResults.contains(result)) {
				backendExecutionResults.add(result);
			}

			markBackendRow(currentRow, result);

			throw new RuntimeException(
					"Failed to execute backend request '" + requestName + "': " + ex.getMessage(),
					ex
			);
		}
	}

	private Map<String, String> readBrowserCookies() {
		Map<String, String> cookieMap = new LinkedHashMap<>();

		driver.manage().getCookies().stream()
				.filter(Objects::nonNull)
				.filter(c -> c.getName() != null && !c.getName().isBlank())
				.forEach(c -> cookieMap.put(c.getName(), c.getValue() != null ? c.getValue() : ""));

		return cookieMap;
	}

	private String buildCookieHeader(Map<String, String> cookieMap) {
		if (cookieMap == null || cookieMap.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.isBlank()) {
				continue;
			}

			if (!sb.isEmpty()) {
				sb.append("; ");
			}
			sb.append(key).append("=").append(entry.getValue() != null ? entry.getValue() : "");
		}

		return sb.toString();
	}

	private String buildResolvedFormBodyWithOverrides(BackendRequestDef def,
													  Map<String, String> nameToValue,
													  List<String> warnings) {
		if (def == null || def.getFormData() == null || def.getFormData().isEmpty()) {
			return "";
		}

		List<FormDataParam> source = def.getFormData();
		List<DtoFieldOverride> overridesToApply = def.getFieldOverrides();

		if ((overridesToApply == null || overridesToApply.isEmpty()) && def.getName() != null) {
			List<DtoFieldOverride> scenarioFo = getScenarioFieldOverrides(def.getName());
			if (scenarioFo != null && !scenarioFo.isEmpty()) {
				overridesToApply = scenarioFo;
			}
		}

		Map<String, String> overrideValues = new LinkedHashMap<>();
		if (overridesToApply != null) {
			for (DtoFieldOverride override : overridesToApply) {
				if (override == null) {
					continue;
				}

				String fieldPath = safeTrim(override.getFieldPath());
				if (fieldPath.isEmpty()) {
					continue;
				}

				String methodExpr;
				try {
					methodExpr = resolveOverrideMethodExpression(override);
				} catch (Exception ex) {
					warnings.add("WARNING form field " + fieldPath
							+ " was not substituted: failed to build method expression. Reason: "
							+ ex.getMessage());
					continue;
				}

				if (methodExpr == null || methodExpr.isBlank()) {
					warnings.add("WARNING form field " + fieldPath
							+ " was not substituted: method expression is blank.");
					continue;
				}

				String generatedValue;
				try {
					generatedValue = variablesService.resolveValue(methodExpr, nameToValue);
				} catch (Exception ex) {
					warnings.add("WARNING form field " + fieldPath
							+ " was not substituted: resolve error for expression "
							+ methodExpr + ". Reason: " + ex.getMessage());
					continue;
				}

				if (generatedValue == null) {
					warnings.add("WARNING form field " + fieldPath
							+ " was not substituted: resolved value is null for expression "
							+ methodExpr + ".");
					continue;
				}

				overrideValues.put(fieldPath, generatedValue);
			}
		}

		StringBuilder sb = new StringBuilder();
		Set<String> seenKeys = new HashSet<>();

		for (FormDataParam param : source) {
			if (param == null) {
				continue;
			}

			String key = param.getKey() != null ? param.getKey().trim() : "";
			if (key.isEmpty()) {
				continue;
			}

			seenKeys.add(key);

			String rawValue = overrideValues.containsKey(key)
					? overrideValues.get(key)
					: (param.getValue() != null ? param.getValue() : "");

			String resolvedValue = variablesService.resolveValue(rawValue, nameToValue);

			if (!sb.isEmpty()) {
				sb.append("&");
			}
			sb.append(java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8));
			sb.append("=");
			sb.append(java.net.URLEncoder.encode(resolvedValue, java.nio.charset.StandardCharsets.UTF_8));
		}

		for (String overrideKey : overrideValues.keySet()) {
			if (!seenKeys.contains(overrideKey)) {
				warnings.add("WARNING form field " + overrideKey
						+ " was not substituted: field not found in form-data.");
			}
		}

		return sb.toString();
	}

	private String applyFieldOverrides(String body,
									   BackendRequestDef def,
									   Map<String, String> nameToValue,
									   List<String> warnings) {
		if (body == null || body.isBlank() || def == null) {
			return body;
		}

		List<DtoFieldOverride> overridesToApply = def.getFieldOverrides();

		if (overridesToApply == null || overridesToApply.isEmpty()) {
			List<DtoFieldOverride> scenarioFo = getScenarioFieldOverrides(def.getName());
			if (scenarioFo != null && !scenarioFo.isEmpty()) {
				overridesToApply = scenarioFo;
			}
		}

		if (overridesToApply == null || overridesToApply.isEmpty()) {
			return body;
		}

		String result = body;

		for (DtoFieldOverride override : overridesToApply) {
			if (override == null) continue;

			String fieldPath = safeTrim(override.getFieldPath());
			if (fieldPath.isEmpty()) continue;

			String methodExpr;
			try {
				methodExpr = resolveOverrideMethodExpression(override);
			} catch (Exception ex) {
				warnings.add("WARNING: field " + fieldPath + " was not substituted: failed to build method expression. Reason: " + ex.getMessage());
				continue;
			}

			if (methodExpr == null || methodExpr.isBlank()) {
				warnings.add("WARNING: field " + fieldPath + " was not substituted: method expression is blank.");
				continue;
			}

			String generatedValue;
			try {
				generatedValue = variablesService.resolveValue(methodExpr, nameToValue);
			} catch (Exception ex) {
				warnings.add("WARNING: field " + fieldPath + " was not substituted: resolve error for expression " + methodExpr + ". Reason: " + ex.getMessage());
				continue;
			}

			if (generatedValue == null) {
				warnings.add("WARNING: field " + fieldPath + " was not substituted: resolved value is null for expression " + methodExpr + ".");
				continue;
			}

			String type = safeTrim(override.getType());
			if (type.isEmpty()) {
				type = "string";
			}

			try {
				String replaced = replaceJsonFieldValue(result, fieldPath, generatedValue, type);
				if (Objects.equals(replaced, result)) {
					warnings.add("WARNING: field " + fieldPath + " was not substituted: field not found in DTO body.");
					continue;
				}
				result = replaced;
			} catch (Exception ex) {
				warnings.add("WARNING: field " + fieldPath + " was not substituted: replace error. Reason: " + ex.getMessage());
			}
		}

		return result;
	}

	private String resolveOverrideMethodExpression(DtoFieldOverride override) {
		String method = safeTrim(override.getMethod());
		if (method.isEmpty()) {
			return null;
		}

		String arg = override.getMethodArg();
		if (arg == null) {
			arg = "";
		}

		if ("value".equals(method) || "use variable".equals(method)) {
			return arg.isBlank() ? null : arg;
		}

		if ("addUuid".equals(method)) {
			return "addUuid(" + arg + ")";
		}

		if (method.endsWith("()") || method.contains("(")) {
			return method;
		}

		return method + "()";
	}

	private String replaceJsonFieldValue(String json, String fieldPath, String newValue, String type) {
		if (json == null || json.isBlank()) {
			return json;
		}
		if (fieldPath == null || fieldPath.isBlank()) {
			return json;
		}

		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject() && !root.isJsonArray()) {
				return json;
			}

			String[] parts = fieldPath.split("\\.");
			JsonElement current = root;

			for (int i = 0; i < parts.length - 1; i++) {
				PathToken token = parsePathToken(parts[i]);
				if (token == null) {
					return json;
				}

				current = navigateToChild(current, token);
				if (current == null || current.isJsonNull()) {
					return json;
				}
			}

			PathToken lastToken = parsePathToken(parts[parts.length - 1]);
			if (lastToken == null) {
				return json;
			}

			boolean replaced = replaceAtTarget(current, lastToken, buildJsonElement(newValue, type));
			if (!replaced) {
				return json;
			}

			return root.toString();
		} catch (Exception e) {
			log.debug("replaceJsonFieldValue failed for path {}, error: {}", fieldPath, e.getMessage());
			return json;
		}
	}

	private JsonElement buildJsonElement(String value, String type) {
		String normalizedType = safeTrim(type).toLowerCase(Locale.ROOT);

		if ("number".equals(normalizedType)) {
			String trimmed = safeTrim(value);
			if (trimmed.isEmpty()) {
				return new JsonPrimitive(0);
			}
			try {
				return new JsonPrimitive(Integer.parseInt(trimmed));
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("Value " + value + " is not a valid integer");
			}
		}

		return new JsonPrimitive(value != null ? value : "");
	}

	private JsonElement navigateToChild(JsonElement current, PathToken token) {
		JsonElement next = current;

		if (!token.key.isEmpty()) {
			if (!next.isJsonObject()) {
				return null;
			}
			next = next.getAsJsonObject().get(token.key);
			if (next == null || next.isJsonNull()) {
				return null;
			}
		}

		if (token.index != null) {
			if (!next.isJsonArray()) {
				return null;
			}
			JsonArray arr = next.getAsJsonArray();
			if (token.index < 0 || token.index >= arr.size()) {
				return null;
			}
			next = arr.get(token.index);
		}

		return next;
	}

	private boolean replaceAtTarget(JsonElement current, PathToken token, JsonElement replacement) {
		if (current == null || current.isJsonNull()) {
			return false;
		}

		if (!token.key.isEmpty()) {
			if (!current.isJsonObject()) {
				return false;
			}

			JsonObject obj = current.getAsJsonObject();
			JsonElement child = obj.get(token.key);
			if (child == null || child.isJsonNull()) {
				return false;
			}

			if (token.index != null) {
				if (!child.isJsonArray()) {
					return false;
				}
				JsonArray arr = child.getAsJsonArray();
				if (token.index < 0 || token.index >= arr.size()) {
					return false;
				}
				arr.set(token.index, replacement);
				return true;
			}

			obj.add(token.key, replacement);
			return true;
		}

		if (token.index != null) {
			if (!current.isJsonArray()) {
				return false;
			}
			JsonArray arr = current.getAsJsonArray();
			if (token.index < 0 || token.index >= arr.size()) {
				return false;
			}
			arr.set(token.index, replacement);
			return true;
		}

		return false;
	}

	private PathToken parsePathToken(String part) {
		if (part == null || part.isBlank()) {
			return null;
		}

		String trimmed = part.trim();

		if (trimmed.contains("[")) {
			int bracket = trimmed.indexOf('[');
			int closeBracket = trimmed.indexOf(']', bracket);
			if (closeBracket < 0) {
				return null;
			}

			String key = trimmed.substring(0, bracket).trim();
			String indexStr = trimmed.substring(bracket + 1, closeBracket).trim();
			if (indexStr.isEmpty()) {
				return null;
			}

			try {
				return new PathToken(key, Integer.parseInt(indexStr));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		int splitPos = trimmed.length();
		while (splitPos > 0 && Character.isDigit(trimmed.charAt(splitPos - 1))) {
			splitPos--;
		}

		if (splitPos < trimmed.length() && splitPos > 0) {
			String key = trimmed.substring(0, splitPos);
			String indexStr = trimmed.substring(splitPos);
			try {
				return new PathToken(key, Integer.parseInt(indexStr));
			} catch (NumberFormatException e) {
				return new PathToken(trimmed, null);
			}
		}

		return new PathToken(trimmed, null);
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

	private void onScenarioFinishedWithBackendAnswers(
			ActionWindow actionWindow,
			String message,
			int messageType
	) {
		SwingUtilities.invokeLater(() -> {
			// Показываем стандартный диалог завершения
			Object[] options = {"OK", "View backend responses", "Create bug report"};
			int choice = JOptionPane.showOptionDialog(
					actionWindow,
					message,
					"Playback finished",
					JOptionPane.YES_NO_CANCEL_OPTION,
					messageType,
					null,
					options,
					options[0]
			);

			if (choice == 1) { // "View backend responses"
				showBackendAnswersDialog(actionWindow);
			} else if (choice == 2) { // "Create bug report"
				BackendExecutionResult lastFailed = findLastFailedBackendResult();
				// вот здесь ПРЯМО ИСПОЛЬЗУЕМ твой showErrorWithBugReportButton
				showErrorWithBugReportButton(actionWindow, message, lastFailed);
			}
			// choice == 0 или закрытие диалога — просто ничего не делаем
		});
	}

	private void showBackendAnswersDialog(ActionWindow parent) {
		JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);

		synchronized (backendExecutionResults) {
			for (int i = 0; i < backendExecutionResults.size(); i++) {
				BackendExecutionResult r = backendExecutionResults.get(i);

				String requestName = r.requestName != null && !r.requestName.isBlank()
						? r.requestName
						: "backend";

				int stepNumber = r.step != null ? r.step : i;
				String tabTitle = stepNumber + ". " + requestName;

				JTextArea textArea = new JTextArea(buildBackendAnswerText(r), 28, 110);
				textArea.setEditable(false);
				textArea.setCaretPosition(0);
				textArea.setLineWrap(false);
				textArea.setWrapStyleWord(false);
				textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

				JScrollPane scrollPane = new JScrollPane(textArea);
				scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
				scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

				tabs.addTab(tabTitle, scrollPane);
			}
		}

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

		JButton closeButton = new JButton("Закрыть");
		closeButton.addActionListener(e -> dialog.dispose());

		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bottomPanel.add(closeButton);

		dialog.add(title, BorderLayout.NORTH);
		dialog.add(tabs, BorderLayout.CENTER);
		dialog.add(bottomPanel, BorderLayout.SOUTH);

		dialog.setSize(1000, 700);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	private String buildBackendAnswerText(BackendExecutionResult r) {
		StringBuilder sb = new StringBuilder();

		sb.append("==================================================\n");
		sb.append(r.method != null ? r.method : "UNKNOWN");
		sb.append(" ");
		sb.append(r.url != null ? r.url : "");
		sb.append("\n");
		sb.append("==================================================\n");
		sb.append("status: ").append(r.status).append("\n");
		sb.append("success: ").append(r.success).append("\n");

		if (r.requestName != null && !r.requestName.isBlank()) {
			sb.append("requestName: ").append(r.requestName).append("\n");
		}

		if (r.step != null) {
			sb.append("stepNumber: ").append(r.step).append("\n");
		}

		if (r.warnings != null && !r.warnings.isEmpty()) {
			sb.append("\nWARNINGS:\n");
			for (String warning : r.warnings) {
				sb.append("- ").append(warning).append("\n");
			}
		}

		if (r.status >= 400 && r.status <= 599) {
			sb.append("\nREQUEST BODY:\n");
			sb.append("--------------------------------------------------\n");

			String requestBody = r.requestBody == null ? "" : r.requestBody.trim();
			if (requestBody.isEmpty()) {
				sb.append("<empty request body>");
			} else {
				sb.append(requestBody);
			}

			sb.append("\n");
		}

		sb.append("\nRESPONSE BODY:\n");
		sb.append("--------------------------------------------------\n");

		String responseBody = r.responseBody == null ? "" : r.responseBody.trim();
		if (responseBody.isEmpty()) {
			sb.append("<empty response>");
		} else {
			sb.append(responseBody);
		}

		return sb.toString();
	}

	private String toJsString(String s) {
		if (s == null) return "''";
		return "'" + s
				.replace("\\", "\\\\")
				.replace("'", "\\'")
				.replace("\n", "\\n")
				.replace("\r", "\\r") + "'";
	}

	private void extractResponseVariables(BackendRequestDef def,
										  String responseBody,
										  Map<String, String> nameToValue) {
		if (def == null || responseBody == null || responseBody.isBlank()) {
			return;
		}

		List<ResponseFieldExtractor> extractors = getScenarioExtractors(def.getName());
		if (extractors == null || extractors.isEmpty()) {
			extractors = def.getResponseExtractors();
		}

		if (extractors == null || extractors.isEmpty()) {
			return;
		}

		BackendExecutionResult lastResult = null;
		synchronized (backendExecutionResults) {
			for (int i = backendExecutionResults.size() - 1; i >= 0; i--) {
				BackendExecutionResult candidate = backendExecutionResults.get(i);
				if (candidate != null && Objects.equals(def.getName(), candidate.requestName)) {
					lastResult = candidate;
					break;
				}
			}
		}

		try {
			JsonElement root = JsonParser.parseString(responseBody);

			for (ResponseFieldExtractor extractor : extractors) {
				if (extractor == null) {
					continue;
				}

				String fieldPath = extractor.getFieldPath();
				if (fieldPath == null || fieldPath.isBlank()) {
					continue;
				}

				String varName = extractor.getVariableName();
				if (varName == null || varName.isBlank()) {
					varName = util.VariableNameUtil.buildUniqueVariableName(def, fieldPath);
				}

				String value = extractJsonValue(root, fieldPath);
				if (value == null) {
					log.warn("Response field '{}' was not found for request '{}'", fieldPath, def.getName());
					if (lastResult != null) {
						lastResult.warnings.add("Field not found in response: " + fieldPath);
					}
					continue;
				}

				// БАГ 1 FIX: значение сохраняется ТОЛЬКО локально для текущего прогона,
				// глобальный VariablesService (таблица настроек) больше не перезаписывается.
				nameToValue.put(varName, value);

				if (lastResult != null) {
					lastResult.extractedVars.put(varName, value);
				}

				log.info("Extracted response variable: {} = {}", varName, value);
			}

		} catch (JsonSyntaxException ex) {
			log.warn("Response body for '{}' is not valid JSON, cannot extract variables: {}",
					def.getName(), ex.getMessage());
		} catch (Exception ex) {
			log.warn("Failed to extract response variables for '{}': {}",
					def.getName(), ex.getMessage(), ex);
		}
	}

	private String extractJsonValue(JsonElement root, String fieldPath) {
		try {
			String[] parts = fieldPath.split("\\.");
			JsonElement current = root;
			for (String part : parts) {
				if (current == null || current.isJsonNull()) return null;
				if (part.contains("[")) {
					String key = part.substring(0, part.indexOf('['));
					int idx = Integer.parseInt(part.replaceAll("[^0-9]", ""));
					if (!key.isEmpty() && current.isJsonObject()) {
						current = current.getAsJsonObject().get(key);
					}
					if (current != null && current.isJsonArray() && idx < current.getAsJsonArray().size()) {
						current = current.getAsJsonArray().get(idx);
					} else {
						return null;
					}
				} else {
					if (current.isJsonObject()) {
						current = current.getAsJsonObject().get(part);
					} else {
						return null;
					}
				}
			}
			return (current != null && !current.isJsonNull()) ? current.getAsString() : null;
		} catch (Exception e) {
			log.debug("extractJsonValue failed for path '{}': {}", fieldPath, e.getMessage());
			return null;
		}
	}

	private List<ResponseFieldExtractor> getScenarioExtractors(String requestName) {
		if (currentScenarioOverrides == null) return null;
		ScenarioBackendConfig cfg = currentScenarioOverrides.get(requestName);
		return cfg != null ? cfg.getResponseExtractors() : null;
	}

	private List<DtoFieldOverride> getScenarioFieldOverrides(String requestName) {
		if (currentScenarioOverrides == null) return null;
		ScenarioBackendConfig cfg = currentScenarioOverrides.get(requestName);
		return cfg != null ? cfg.getFieldOverrides() : null;
	}

	@SneakyThrows
	private Path createCurlAttachmentForBackendError(BackendExecutionResult result) {
		if (result == null) {
			return null;
		}
		if (result.status < 400 || result.status >= 600) {
			return null;
		}

		String fileSafeRequestName = safeFileName(
				result.requestName != null && !result.requestName.isBlank()
						? result.requestName
						: "backend-request"
		);

		String fileName = "backend-error-step-" +
				(result.step != null ? result.step : "unknown") +
				"-" + fileSafeRequestName +
				".curl.txt";

		Path file = Files.createTempDirectory("jaga-backend-curl-").resolve(fileName);

		String curlText = buildCurlText(result);
		Files.writeString(file, curlText, StandardCharsets.UTF_8);

		return file;
	}

	private String buildCurlText(BackendExecutionResult result) {
		String lineSeparator = System.lineSeparator();
		StringBuilder sb = new StringBuilder();

		sb.append("# Backend request debug").append(lineSeparator);
		sb.append("# requestName: ").append(nullToEmpty(result.requestName)).append(lineSeparator);
		sb.append("# step: ").append(result.step != null ? result.step : "").append(lineSeparator);
		sb.append("# status: ").append(result.status).append(lineSeparator);
		sb.append(lineSeparator);

		sb.append("curl --location").append(" \\").append(lineSeparator);
		sb.append("  --request ").append(shellQuote(safeHttpMethod(result.method))).append(" \\").append(lineSeparator);
		sb.append("  ").append(shellQuote(nullToEmpty(result.url)));

		Map<String, String> headers = result.requestHeaders != null
				? result.requestHeaders
				: Map.of();

		for (Map.Entry<String, String> entry : headers.entrySet()) {
			String headerName = entry.getKey();
			String headerValue = entry.getValue();

			if (headerName == null || headerName.isBlank()) {
				continue;
			}
			if (isSensitiveHeader(headerName)) {
				continue;
			}

			sb.append(" \\").append(lineSeparator);
			sb.append("  --header ").append(shellQuote(headerName + ": " + nullToEmpty(headerValue)));
		}

		String requestBody = result.requestBody != null ? result.requestBody : "";
		if (!requestBody.isBlank()) {
			sb.append(" \\").append(lineSeparator);
			sb.append("  --data-raw ").append(shellQuote(requestBody));
		}

		sb.append(lineSeparator).append(lineSeparator);

		if (result.warnings != null && !result.warnings.isEmpty()) {
			sb.append("# warnings").append(lineSeparator);
			for (String warning : result.warnings) {
				sb.append("# - ").append(nullToEmpty(warning)).append(lineSeparator);
			}
		}

		return sb.toString();
	}

	private boolean isSensitiveHeader(String headerName) {
		if (headerName == null) {
			return false;
		}
		String normalized = headerName.trim().toLowerCase(Locale.ROOT);
		return normalized.equals("cookie")
				|| normalized.equals("set-cookie")
				|| normalized.equals("authorization");
	}

	private String safeHttpMethod(String method) {
		String normalized = method != null ? method.trim().toUpperCase(Locale.ROOT) : "";
		return switch (normalized) {
			case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" -> normalized;
			default -> "GET";
		};
	}

	private String shellQuote(String value) {
		if (value == null) {
			return "''";
		}
		return "'" + value.replace("'", "'\"'\"'") + "'";
	}

	private String safeFileName(String value) {
		if (value == null || value.isBlank()) {
			return "file";
		}
		return value.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String buildFinalBackendUrl(String storedPathOrUrl) {
		String value = storedPathOrUrl != null ? storedPathOrUrl.trim() : "";
		if (value.isEmpty()) {
			throw new IllegalStateException("URL backend-метода пустой.");
		}
		return value;
	}

	private BackendExecutionResult findLastFailedBackendResult() {
		synchronized (backendExecutionResults) {
			for (int i = backendExecutionResults.size() - 1; i >= 0; i--) {
				BackendExecutionResult result = backendExecutionResults.get(i);
				if (result != null && !result.success) {
					return result;
				}
			}
		}
		return null;
	}

	private String resolveBugReportError(String message, String popupError) {
		val back = findLastFailedBackendResult();

		if (back != null) {
			String responseBody = prettyBackendBody(back.responseBody);
			return responseBody.isBlank() ? null : responseBody;
		}

		if (popupError != null && !popupError.isBlank()) {
			return popupError.trim();
		}

		if (isElementNotVisibleError(message)) {
			return "Элемент не виден на странице";
		}

		return null;
	}

	private boolean isElementNotVisibleError(String message) {
		if (message == null || message.isBlank()) {
			return false;
		}

		String text = message.toLowerCase(Locale.ROOT);

		return text.contains("no such element")
				|| text.contains("element not found")
				|| text.contains("not exists on page")
				|| text.contains("element not created")
				|| text.contains("element is not attached")
				|| text.contains("stale element")
				|| text.contains("element click intercepted")
				|| text.contains("element not interactable")
				|| text.contains("invalid selector");
	}

	private String safeExtractPopupError() {
		try {
			String value = extractNotificationTextByXpathWithRetry();
			return value != null && !value.isBlank() ? value.trim() : null;
		} catch (Exception ex) {
			log.debug("Не удалось получить текст всплывашки", ex);
			return null;
		}
	}

	private String extractNotificationTextByXpathWithRetry() {
		for (int attempt = 0; attempt < 3; attempt++) {
			String text = extractNotificationTextByXpath();
			if (!text.isBlank()) {
				return text;
			}

			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return "";
			}
		}
		return "";
	}

	private String extractNotificationTextByXpath() {
		if (driver == null) {
			return "";
		}

		String xpath =
				"(" +
						"//mat-snack-bar-container[contains(@class,'mat-mdc-snack-bar-container')]"
						+ "//div[contains(@class,'mat-mdc-snack-bar-label') and contains(@class,'mdc-snackbar__label')]" +
						" | " +
						"//li[contains(@class,'iqhr-notification') and @data-sonner-toast and @data-visible='true']" +
						")[last()]";

		List<WebElement> elements = driver.findElements(By.xpath(xpath));
		for (int i = elements.size() - 1; i >= 0; i--) {
			WebElement element = elements.get(i);
			if (element == null || !element.isDisplayed()) {
				continue;
			}
			String text = element.getText();
			if (text != null && !text.isBlank()) {
				return text.trim();
			}
		}
		return "";
	}

	private String safeTrim(String value) {
		return value == null ? "" : value.trim();
	}

	private static class PlayStep {
		int rowIndex;
		String actionCode;
		String selector;
		String value;
		String rawValue;   // БАГ 3 FIX: сырое значение до резолвинга
		FunnelMoveRequestDef funnelMoveRequest;
		String javaClassName;
		String xpath;
		String name;
		String index;
		String byXpath;
		String url;
	}

	private static class BackendExecutionResult {
		String requestName;
		String method;
		String url;
		String responseBody;
		String requestBody;
		boolean success;
		long status;
		List<String> warnings = new ArrayList<>();
		Map<String, String> extractedVars = new LinkedHashMap<>();
		Integer step;
		Map<String, String> requestHeaders = new LinkedHashMap<>();
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

	private static class PathToken {
		private final String key;
		private final Integer index;

		private PathToken(String key, Integer index) {
			this.key = key != null ? key : "";
			this.index = index;
		}
	}

	private String getCurrentDomain() {
		if (driver == null) {
			throw new IllegalStateException("Browser driver is not initialized.");
		}

		String currentUrl = driver.getCurrentUrl();

		if (currentUrl == null || currentUrl.isBlank()) {
			throw new IllegalStateException("Current browser URL is empty.");
		}

		try {
			URI uri = URI.create(currentUrl);

			if (uri.getScheme() == null || uri.getAuthority() == null) {
				throw new IllegalArgumentException("Invalid URL: " + currentUrl);
			}

			return uri.getScheme() + "://" + uri.getAuthority();
		} catch (Exception e) {
			throw new IllegalStateException(
					"Cannot resolve domain from browser URL: " + currentUrl,
					e
			);
		}
	}
}