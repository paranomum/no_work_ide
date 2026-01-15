package ui.action;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import javax.swing.table.DefaultTableModel;
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

		String data = """
				window.recordedClicks = [];
				window.recordedInputs = [];
				window.currentFocusedElement = null;
				window.currentFocusedXPath = null;
				window.currentFocusedValue = '';
				""";

		String getXPath = """
				function getXPath(element) {
					if (!element || element.nodeType !== 1) return '';
						if (element.id && document.getElementById(element.id) === element) {
							return "//*[@id='" + element.id + "']";
					}
					var paths = [];
					for (; element && element.nodeType === 1; element = element.parentNode) {
						var index = 0;
						var hasFollowingSiblings = false;
						if (element.id && document.getElementById(element.id) === element) {
							paths.splice(0, 0, "/*[@id='" + element.id + "']");
							break;
						}
						for (var sibling = element.previousSibling; sibling; sibling = sibling.previousSibling) {
							if (sibling.nodeType === 1 && sibling.nodeName === element.nodeName) {
								index++;
							}
						}
						for (var sibling = element.nextSibling; sibling && !hasFollowingSiblings; sibling = sibling.nextSibling) {
							if (sibling.nodeType === 1 && sibling.nodeName === element.nodeName) {
								hasFollowingSiblings = true;
							}
						}
						var tagName = element.nodeName.toLowerCase();
						var pathIndex = (index || hasFollowingSiblings) ? '[' + (index + 1) + ']' : '';
						paths.splice(0, 0, tagName + pathIndex);
						if (element.nodeName.toLowerCase() === 'html') break;
					}
					return paths.length ? '/' + paths.join('/') : '';
				}
				""";

		String isClickableElement = """
				function isClickableElement(element) {
					if (!element) return false;
					var tagName = element.tagName ? element.tagName.toUpperCase() : '';
					if (isButton(element)) return true;
					if (tagName === 'A' || tagName === 'LABEL') return true;
					if (tagName === 'INPUT' && element.getAttribute('aria-haspopup')) return true;
					var role = element.getAttribute('role');
					if (role === 'button' || role === 'tab' || role === 'menuitem') return true;
					return false;
				}
				""";

		String buttonConditions = """
				function buttonConditions(element) {
				    // Проверяем data-testid='button' + span + not class '-trigger'
				    if (element.getAttribute('data-testid') === 'button') {
				        var spanChild = element.querySelector('span');
				        if (spanChild && spanChild.textContent.trim() !== '' &&
				            !element.className.includes('-trigger')) {
				            return true;
				        }
				    }

				    // Проверяем button или любые элементы с нужными атрибутами/текстом
				    var conditions = [
				        'span',
				        'ng-reflect-message',
				        'aria-label',
				        'text()',
				        '.'
				    ];
				    // Проверяем наличие span с текстом
				    var spans = element.querySelectorAll('span');
				    for (var i = 0; i < spans.length; i++) {
				        if (spans[i].textContent.trim() !== '') return true;
				    }
				    // Проверяем aria-label на элементе и его детях
				    if (element.getAttribute('aria-label') && element.getAttribute('aria-label').trim() !== '') return true;
				    var ariaLabels = element.querySelectorAll('[aria-label]');
				    for (var j = 0; j < ariaLabels.length; j++) {
				        if (ariaLabels[j].getAttribute('aria-label').trim() !== '') return true;
				    }
				    // Проверяем ng-reflect-message
				    if (element.getAttribute('ng-reflect-message') && element.getAttribute('ng-reflect-message').trim() !== '') return true;
				    // Проверяем текст самого элемента
				    if (element.textContent.trim() !== '') return true;
				    return false;
				}
				""";

		String isButton = """
				function isButton(element) {
				    // Идем вверх по DOM до button или корня
				    var currentElement = element;
				    while (currentElement) {
				        var currentTagName = currentElement.tagName ? currentElement.tagName.toUpperCase() : '';
				        // Если нашли button - проверяем условия XPath
				        if (currentTagName === 'BUTTON') {
				            if (buttonConditions(currentElement)) {
				                return true;
				            }
				            break; // Если button не подходит - дальше не идем
				        }
				        currentElement = currentElement.parentElement;
				    }
				    return false;
				}
				""";

		String isEditableInput = """
				function isEditableInput(element) {
					if (!element) return false;
					var tagName = element.tagName ? element.tagName.toUpperCase() : '';
					if (tagName !== 'INPUT' && tagName !== 'TEXTAREA') return false;
					if (element.getAttribute('aria-haspopup')) return false;
					return true;
				}
				""";

		String findClickable = """
				function findClickable(element) {
					var current = element;
					var depth = 0;
					while (current && depth < 20) {
						if (isClickableElement(current)) return current;
						if (current.tagName && current.tagName.toUpperCase() === 'BODY') break;
						current = current.parentElement;
						depth++;
					}
					return null;
				}
				""";

		String addEventListenerClick = """
				document.addEventListener('click', function(e) {
								var element = e.target;
								  var tagName = element.tagName ? element.tagName.toUpperCase() : '';
								  var clickable = findClickable(element);
								  if (clickable) {
								    var xpath = getXPath(clickable);
								    if (xpath) {
								      window.recordedClicks.push({
								        xpath: xpath,
								        id: clickable.id || '',
								        tag: clickable.tagName.toUpperCase(),
								        text: clickable.textContent ? clickable.textContent.substring(0, 50) : ''
								      });
								    }
								    return;
								  }
								  if (isEditableInput(element)) {
								    var xpath = getXPath(element);
								    if (xpath) {
								      window.currentFocusedXPath = xpath;
								      window.currentFocusedElement = element;
								      window.currentFocusedValue = element.value || '';
								    }
								  }
								}, true);
				""";

		String addEventListenerBlur = """
				document.addEventListener('blur', function(e) {
					  if (isEditableInput(e.target) && window.currentFocusedXPath) {
							var currentValue = e.target.value || '';
							if (window.currentFocusedValue !== currentValue) {
								  window.recordedInputs.push({
									xpath: window.currentFocusedXPath,
									value: currentValue,
									id: e.target.id || '',
									timestamp: Date.now()
								  });
							} else {
								  window.recordedClicks.push({
									xpath: window.currentFocusedXPath,
									id: e.target.id || '',
									tag: e.target.tagName.toUpperCase(),
									text: ''
								  });
							}
							window.currentFocusedXPath = null;
							window.currentFocusedElement = null;
							window.currentFocusedValue = '';
					  }
				}, true);
				""";

		String script = data
				+ getXPath
				+ isClickableElement
				+ isButton
				+ buttonConditions
				+ isEditableInput
				+ findClickable
				+ addEventListenerClick
				+ addEventListenerBlur;

		js.executeScript(script);
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
							record("click", xpath, null);
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
