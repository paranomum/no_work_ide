package ui.action;

import com.codeborne.selenide.WebDriverRunner;
import dto.UsersServiceSpec;
import lombok.Setter;
import model.UserAction;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import ru.rt.iqhr.framework.config.FrameworkConfig;
import ru.rt.iqhr.framework.pageobject.react.web_elements.buttons.Button;
import ru.rt.iqhr.framework.pageobject.react.web_elements.triggers.Dropdown;
import ru.rt.iqhr.framework.util.FormFiller;
import ru.rt.iqhr.framework.util.TabManager;
import ui.ActionWindow;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ru.rt.iqhr.framework.pageobject.react.web_elements.*;

import static com.codeborne.selenide.Selenide.$x;
import static com.codeborne.selenide.Selenide.open;
import static ru.rt.iqhr.framework.util.StringUtils.isEmail;
import static ru.rt.iqhr.framework.util.WebElementUtil.waitLoadingPage;


public class PlayActionService {

	@Setter
	private WebDriver driver;

	private final DefaultTableModel tableModel;
	private final TabManager tabManager = new TabManager();
	private final FormFiller formFiller;
	private final UsersService usersService;

	private Thread playThread;
	private volatile boolean stopped = false;


	public PlayActionService(DefaultTableModel tableModel, UsersService usersService) {
		FrameworkConfig.setSpeedMode(FrameworkConfig.SpeedMode.FAST);
		this.tableModel = tableModel;
		formFiller = new FormFiller();
		this.usersService = usersService;
	}

	public void playActionsFromTable(ActionWindow actionWindow) {
		if (driver == null) {
			JOptionPane.showMessageDialog(
					actionWindow,
					"Browser is not open. Please open browser first.",
					"Browser Required",
					JOptionPane.WARNING_MESSAGE
			);
			return;
		}

		if (playThread != null && playThread.isAlive()) {
			JOptionPane.showMessageDialog(
					actionWindow,
					"Scenario is already running.",
					"Already running",
					JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}

		List<PlayStep> steps = buildStepsFromTable();
		if (steps.isEmpty()) {
			JOptionPane.showMessageDialog(
					actionWindow,
					"Table has no executable actions.",
					"Nothing to play",
					JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}

		stopped = false;

		playThread = new Thread(() -> {
			WebDriverRunner.setWebDriver(driver);
			runScenario(actionWindow, steps);
		}, "PlayScenarioThread");

		playThread.start();
	}

	public synchronized void stopPlayback() {
		stopped = true;
		if (playThread != null && playThread.isAlive()) {
			playThread.interrupt();
		}
	}

	private List<PlayStep> buildStepsFromTable() {
		List<PlayStep> steps = new ArrayList<>();

		int rowCount = tableModel.getRowCount();
		for (int r = 0; r < rowCount; r++) {
			String actionCode = extractAction(r);
			if (actionCode == null || actionCode.isBlank()) {
				continue;
			}

			String selector = val(r, 2);
			String value    = val(r, 3);
			String javaClassName = val(r, 5);

			PlayStep step = new PlayStep();
			step.rowIndex   = r;
			step.actionCode = actionCode;
			step.selector   = selector;
			step.value      = value;
			step.javaClassName = javaClassName;

			steps.add(step);
		}

		return steps;
	}

	private void runScenario(ActionWindow actionWindow, List<PlayStep> steps) {
		long prevStepEnd = System.currentTimeMillis();

		for (PlayStep step : steps) {
			if (stopped) { // мягкая остановка
				System.out.println("Playback stopped before step " + (step.rowIndex + 1));
				break;
			}

			long now = System.currentTimeMillis();
			long gap = now - prevStepEnd;
			System.out.println("GAP BEFORE STEP " + (step.rowIndex + 1) + " = " + gap + " ms");

			long start = System.currentTimeMillis();
			System.out.println("STEP START " + (step.rowIndex + 1));

			try {
				playOneStep(step);
			} catch (RuntimeException e) {
				e.printStackTrace();
				showErrorOnUi(actionWindow, "Error at step " + (step.rowIndex) + ": " + e.getMessage());
				break;
			}

			long took = System.currentTimeMillis() - start;
			System.out.println("STEP END   " + (step.rowIndex + 1) + " took " + took + " ms");

			prevStepEnd = System.currentTimeMillis();
		}

		if (!stopped) {
			showInfoOnUi(actionWindow, "Scenario finished successfully.");
		} else {
			showInfoOnUi(actionWindow, "Scenario was stopped by user.");
		}
		stopPlayback();
	}


	private void playOneStep(PlayStep step) {
		String action        = step.actionCode;
		String selector      = step.selector;
		String value         = step.value;
		String javaClassName = step.javaClassName; // уже прочитано из таблицы
		boolean passValue = !action.contains("click")
				&& !action.contains("selectOption")
				&& !action.contains("fillDate");

		// спец‑действия
		boolean isSpecial = action.contains("switchTab")
				|| action.contains("fillData")
				|| action.contains("specialAction")
				|| action.contains("waitLoadingPage")
				|| action.contains("pause")
				|| action.contains("auth")
				|| action.contains("open");
		if (isSpecial) {
			playSpecialAction(action, value);
			return;
		}

		if (!hasText(selector)) {
			return;
		}
		if (!hasText(javaClassName)) {
			System.out.println("Skip row " + (step.rowIndex + 1) + " – empty javaClassName");
			return;
		}

		switch (javaClassName) {
			case "Button", "TabButton", "LinkButton",
				 "CheckBoxButton", "RadioButton" -> {
				if (action.contains("click")) {
					$x(selector).click();
				}
			}
			case "Field" -> {
				Field field = new Field("", $x(selector));
				if (passValue && hasText(value)) {
					field.fill(value);
				}
			}
			case "Select" -> {
				Select field = new Select("", $x(selector));
				String valueToSelect = passValue && hasText(value) ? value : "";
				field.selectOption(valueToSelect);
			}
			case "Dropdown" -> {
				Dropdown field = new Dropdown("", $x(selector));
				String valueToSelect = passValue && hasText(value) ? value : "";
				if (!valueToSelect.isEmpty()) {
					field.selectOption(valueToSelect);
				}
			}
			case "DatePicker" -> {
				DatePicker field = new DatePicker("", $x(selector));
				if (action.contains("fillDate")) {
					field.fillDate();
				}
			}
			default -> {
				System.out.println(
						"Unknown javaClassName='" + javaClassName +
								"' at row " + (step.rowIndex + 1) +
								", selector=" + selector
				);
			}
		}
		System.out.println("TYPE - " + javaClassName);
	}

	// ---- спец‑действия ----

	private void playSpecialAction(String action, String value){
		boolean hasValue = value != null && !value.isEmpty() && !value.isBlank();
		if (action.contains("switchTab")) {
			tabManager.switchToNewTab();
		} else if (action.contains("open")) {
			open(value);
		} else if (action.contains("fillData")) {
			//get xpath by url
			String url = driver.getCurrentUrl();
			String toGet = "";
			if (url.contains("/cabinet/requisitions"))
				toGet = formFiller.getXpathRequisition();
			else if (url.contains("/personal-account/questionary"))
				toGet = formFiller.getXpathLk();
			else if (url.contains("/cabinet/offers/"))
				toGet = formFiller.getXpathOffer();
			formFiller.fillRequiredEmptyByLabel(toGet);
			formFiller.fillRequiredConfirmationSteps(toGet, "superuser_1@autotest.rt", "superuser_1@autotest.rt");
		} else if (action.contains("waitLoadingPage")) {
			if (hasValue)
				waitLoadingPage(Integer.parseInt(value.replaceAll("[\\D]", "")));
			else
				waitLoadingPage();
		} else if (action.equals("auth")) {
			if (hasValue) {
				UsersServiceSpec user = usersService.getUser(value);
				new Auth().logIN(user.username, user.password);
			}
			else
				throw new IllegalArgumentException("value for action 'auth' must not be null");
		} else if (action.contains("pause")) {
			try {
				if (hasValue)
					TimeUnit.MILLISECONDS.sleep(Integer.parseInt(value.replaceAll("[\\D]", "")) * 1000);
				else {
					TimeUnit.MILLISECONDS.sleep(300);
				}
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		} else if (action.contains("specialAction")) {
			// Заглушка под твои кастомные «особые» шаги.
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

	private static class PlayStep {
		int rowIndex;
		String actionCode;
		String selector;
		String value;
		String javaClassName;
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
}