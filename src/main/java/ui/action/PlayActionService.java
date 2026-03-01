package ui.action;

import com.codeborne.selenide.WebDriverRunner;
import lombok.Setter;
import model.UserAction;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import ru.rt.iqhr.framework.pageobject.react.web_elements.buttons.*;
import ru.rt.iqhr.framework.pageobject.react.web_elements.triggers.Dropdown;
import ru.rt.iqhr.framework.util.FormFiller;
import ru.rt.iqhr.framework.util.TabManager;
import ui.ActionWindow;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;

import ru.rt.iqhr.framework.pageobject.react.web_elements.*;

import static com.codeborne.selenide.Selenide.$x;


public class PlayActionService {

	@Setter
	private WebDriver driver;

	private final DefaultTableModel tableModel;
	private final TabManager tabManager = new TabManager();
	private final FormFiller formFiller = new FormFiller();

	public PlayActionService(DefaultTableModel tableModel) {
		this.tableModel = tableModel;
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

		new Thread(() ->
		{
			WebDriverRunner.setWebDriver(driver);
			runScenario(actionWindow, steps);
		}).start();
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
			String comment  = val(r, 4);
			String javaData = val(r, 6);

			PlayStep step = new PlayStep();
			step.rowIndex   = r;
			step.actionCode = actionCode;
			step.selector   = selector;
			step.value      = value;
			step.comment    = comment;
			step.javaData   = javaData;

			steps.add(step);
		}

		return steps;
	}

	private void runScenario(ActionWindow actionWindow, List<PlayStep> steps) {
		for (PlayStep step : steps) {
			try {
				playOneStep(step);
			} catch (Exception ex) {
				ex.printStackTrace();
				String msg = "Error on step " + (step.rowIndex + 1) + " (" +
						step.actionCode + "): " + ex.getMessage();
				showErrorOnUi(actionWindow, msg);
				return;
			}

			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				showErrorOnUi(actionWindow,
						"Playback was interrupted on step " + (step.rowIndex + 1));
				return;
			}
		}

		showInfoOnUi(actionWindow, "Scenario finished successfully.");
	}

	private void playOneStep(PlayStep step) {
		String action      = step.actionCode;
		String selector    = step.selector;
		String value       = step.value;
		String javaClassName = val(step.rowIndex, 5); // колонка Element Type / Java class name

		boolean passValue = !action.contains("click")
				&& !action.contains("selectOption")
				&& !action.contains("fillDate");

		// 2) Специальные действия (switchTab / fillData / specialAction)
		boolean isSpecial = action.contains("switchTab")
				|| action.contains("fillData")
				|| action.contains("specialAction") || action.contains("open");

		if (isSpecial) {
			playSpecialAction(action, value);
			return;
		}

		// 3) Обычные действия по селектору и типу элемента
		if (!hasText(selector)) {
			// как и в buildJavaLinesFromTable: если нет селектора — шаг пропускаем
			return;
		}

		if (javaClassName != null && javaClassName.contains("Button")) {
			 Button button = new Button("", $x(selector));
			 if (action.contains("click")) {
			     button.click();
			 }
			return;
		}

		if (javaClassName != null && javaClassName.contains("TabButton")) {
			TabButton button = new TabButton("", $x(selector));
			if (action.contains("click")) {
				button.click();
			}
			return;
		}

		if (javaClassName != null && javaClassName.contains("LinkButton")) {
			LinkButton button = new LinkButton("", $x(selector));
			if (action.contains("click")) {
				button.click();
			}
			return;
		}

		if (javaClassName != null && javaClassName.contains("Field")) {
			 Field field = new Field("", $x(selector));
			 if (passValue && hasText(value)) {
			     field.fill(value);
			 }
			return;
		}

		if (javaClassName != null && javaClassName.contains("Select")) {
			Select field = new Select("", $x(selector));
			String valueToSelect = passValue && hasText(value) ? value : "";
			if (!valueToSelect.isEmpty()) {
				if (action.contains("selectOption")) {
					field.selectOption(valueToSelect);
				} else if (action.contains("selectExactOption")) {
					field.selectExactOption(valueToSelect);
				}
			} else if (action.contains("selectOption")) {
				field.selectOption();
			}
			return;
		}

		if (javaClassName != null && javaClassName.contains("Dropdown")) {
			Dropdown field = new Dropdown("", $x(selector));
			String valueToSelect = passValue && hasText(value) ? value : "";
			if (!valueToSelect.isEmpty()) {
				field.selectOption(valueToSelect);
			}
			return;
		}

		if (javaClassName != null && javaClassName.contains("CheckBoxButton")) {
			CheckBoxButton button = new CheckBoxButton("", $x(selector));
			if (action.contains("click")) {
				button.click();
			}
			return;
		}

		if (javaClassName != null && javaClassName.contains("RadioButton")) {
			RadioButton button = new RadioButton("", $x(selector));
			if (action.contains("click")) {
				button.click();
			}
			return;
		}

		if (javaClassName != null && javaClassName.contains("DatePicker")) {
			DatePicker field = new DatePicker("", $x(selector));
			if (action.contains("fillDate")) {
				field.fillDate();
			}
			return;
		}

		WebElement element = findByXpath(selector);

		if (action.contains("click")) {
			element.click();
		} else if (passValue && hasText(value)) {
			element.clear();
			element.sendKeys(value);
		}
	}

	// ---- спец‑действия ----

	private void playSpecialAction(String action, String value) {
		if (action.contains("switchTab")) {
			tabManager.switchToNewTab();
		} else if (action.contains("open")) {
			driver.get(value);
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
			formFiller.fillRequiredEmpty(toGet);
			formFiller.fillRequiredConfirmationSteps(toGet, "superuser_1@autotest.rt", "superuser_1@autotest.rt");
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
		String comment;
		String javaData;
	}
}