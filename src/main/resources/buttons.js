function isClickableElement(element) {

    if (!element) return { isClickable: false, buttonInfo: null, javaData: null, eventType: null };

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    var buttonInfo = null;
    var javaData = null;
    var isClickable = false;
    var role = element.getAttribute('role');
    var eventType = null;
    var elType = null;

    console.log("INFO: TAGNAME =", tagName, ", ROLE = ", role);

    // ===== Кнопки и ссылки =====
    if (tagName === 'BUTTON' || tagName === 'A'  || role === 'button') {

        console.log("INFO: TAGNAME =", tagName, ", ROLE = ", role);

        buttonInfo = isButtonOrLink(element);
        if (buttonInfo != null) {

            console.log("[CLICKABLE] BUTTON/LINK", buttonInfo);
            isClickable = true;

            elType = "Button";
            if (tagName === 'BUTTON') {
                javaData = "new Button(\"" + buttonInfo.name + "\")";
            } else if (tagName === 'A') {
                javaData = "new LinkButton(\"" + buttonInfo.name + "\")";
                elType = "LinkButton";
            } else {
                javaData = "new Button(\"" + buttonInfo.name + "\", $x(\"" + buttonInfo.xpath + "\"))";
            }
        }

    // ===== Радио/чекбоксы =====
    } else if (tagName === 'LABEL' || tagName === 'INPUT') {

        var radioOrCheck = isRadioOrCheckBox(element);
        if (radioOrCheck != null) {
            buttonInfo = radioOrCheck.buttonInfo;
            console.log("[CLICKABLE] RADIO/CHECKBOX", buttonInfo);
            isClickable = true;
            elType = radioOrCheck.type;
            javaData = radioOrCheck.javaData;
        }

    // ===== input с aria-haspopup =====
    } else if (tagName === 'INPUT' && element.getAttribute('aria-haspopup')) {

        console.log("[CLICKABLE] INPUT aria-haspopup", element);
        isClickable = true;

    // ===== Табы =====
    } else if (
        tagName === 'DIV' &&
        element.className &&
        element.className.includes('ant-tabs-tab') &&
        (role == 'tab' || element.getAttribute('data-node-key') != null)
    ) {

        buttonInfo = isTab(element);
        if (buttonInfo != null) {

            console.log("[CLICKABLE] TAB", buttonInfo);
            isClickable = true;
            javaData = "new TabButton(\"" + buttonInfo.name + "\")";
            elType = "TabButton";
        }
    }

    // ===== form-select (открытие селекта) =====
    if (!isClickable) {
        var selectResult = detectFormSelect(element);
        if (selectResult) {
            isClickable = true;
            buttonInfo = selectResult.buttonInfo;
            eventType = selectResult.eventType;
            javaData = "new Select(\"" + buttonInfo.name + "\")";
            elType = "Select";
        }
    }

    // ===== dropdown (открытие) =====
    if (!isClickable) {
        var dropdownResult = detectDropdown(element);
        if (dropdownResult) {
            isClickable = true;
            buttonInfo = dropdownResult.buttonInfo;
            eventType = dropdownResult.eventType;
            javaData = "new Dropdown(\"" + buttonInfo.name + "\")";
            elType = "Dropdown";
        }
    }

    // ===== элемент списка для этого селекта =====
    if (
        !isClickable &&
        tagName === 'DIV' &&
        element.className &&
        element.className.includes('ant-select-item-option')
    ) {
        var selectOptionResult = detectSelectOption(element);
        if (selectOptionResult) {
            isClickable = true;
            buttonInfo = selectOptionResult.buttonInfo;
            eventType = selectOptionResult.eventType;
            elType = "Select";
        }
    }

    // ===== элемент списка для dropdown =====
    if (!isClickable) {
        var dropdownOptionResult = detectDropdownOption(element);
        if (dropdownOptionResult) {
            isClickable = true;
            buttonInfo = dropdownOptionResult.buttonInfo;
            eventType = dropdownOptionResult.eventType;
            elType = "Dropdown";
        }
    }

    return {
        isClickable: isClickable,
        buttonInfo: buttonInfo,
        javaData: javaData,
        eventType: eventType,
        type: elType
    };
}

function detectDropdown(element) {
  if (!element || !element.closest) return null;

  // ищем ближайшую кнопку-триггер Ant Design
  var root = element.closest('button.ant-dropdown-trigger');
  if (!root) return null;

  if (!root.className || !root.className.includes('ant-dropdown-trigger')) return null;

  // текст может быть пустым (иконка)
  var text = root.textContent ? root.textContent.trim() : '';
  text = sanitizeText(text || '');
  var safeText = text.replace(/'/g, "\\'");

  // XPath без завязки на data-testid
  var xpath;
  if (text) {
    xpath = "//button[contains(@class,'ant-dropdown-trigger') and contains(., '" + safeText + "')]";
  } else {
    xpath = "//button[contains(@class,'ant-dropdown-trigger')]";
  }

  return {
    isClickable: true,
    buttonInfo: {
      selectXpath: xpath,
      name: text || 'dropdown',
      type: 'dropdown',
      domElement: root
    },
    eventType: 'dropdown-open'
  };
}

function detectFormSelect(element) {
    if (!element.closest) return null;

    var selectRoot = element.closest("[data-testid='form-select']");
    if (!selectRoot) return null;

    var selectInfo = getSelectInfoFromRoot(selectRoot);
    if (!selectInfo) return null;

    return {
        isClickable: true,
        buttonInfo: {
            xpath: selectInfo.xpath,
            name: selectInfo.name,
            type: "select",
            domElement: selectRoot
        },
        eventType: "select-open"
    };
}

function detectSelectOption(element) {
    if (!element || !element.className) return null;

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    if (tagName !== 'DIV' || !element.className.includes('ant-select-item-option')) {
        return null;
    }

    var title = element.getAttribute('title') || (element.textContent || '').trim();
    if (!title) return null;

    title = sanitizeText(title);

    var dropdown = element.closest("div.ant-select-dropdown");
    if (!dropdown) return null;

    var listbox = dropdown.querySelector("div[role='listbox'][id]");
    if (!listbox) return null;

    var listId = listbox.getAttribute("id");
    if (!listId) return null;

    var input = document.querySelector(
        "[data-testid='form-select'] input[aria-controls='" + listId + "']"
    );
    if (!input) return null;

    var selectRoot = input.closest("[data-testid='form-select']");
    if (!selectRoot) return null;

    var selectInfo = getSelectInfoFromRoot(selectRoot);
    if (!selectInfo) return null;

    var safeTitle = title.replace(/'/g, "\\'");

    var xpath =
        "//div[./div[@role='listbox' and @id='" + listId + "']]//" +
        "div[contains(@class,'ant-select-item-option') and @title='" + safeTitle + "']";

    return {
        buttonInfo: {
            xpath: xpath,
            name: title,
            type: "select-option",
            selectXpath: selectInfo.xpath,
            selectName: selectInfo.name,
            domElement: element
        },
        eventType: "select-option"
    };
}

function detectDropdownOption(element) {
    if (!element || !element.className) return null;

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    if (tagName !== 'LI') return null;

    var cls = element.className;
    if (!cls.includes("ant-dropdown-menu-item") && !cls.includes("ant-menu-item")) {
        return null;
    }

    // текст опции — как в Java: span.ant-dropdown-menu-title-content / ant-menu-title-content
    var span = element.querySelector(
        "span.ant-dropdown-menu-title-content, span.ant-menu-title-content"
    ) || element.querySelector("span");

    var text = span ? (span.textContent || "").trim() : (element.textContent || "").trim();
    if (!text) return null;
    text = sanitizeText(text);

    var safeText = text.replace(/'/g, "\\'");

    // ul с нужным классом
    var ul = element.closest("ul");
    if (!ul || !ul.className) return null;
    if (!(
        ul.className.includes("ant-dropdown-menu") ||
        ul.className.includes("ant-menu")
    )) {
        return null;
    }

    // XPath в духе xpathLi + "/li[contains(., 'option')]"
    var xpath =
        "//ul[(contains(@class, 'ant-dropdown-menu') or contains(@class, 'ant-menu')) and " +
        ".//li[contains(@class, 'ant-dropdown-menu-item') or contains(@class, 'ant-menu-item')]]" +
        "/li[contains(., '" + safeText + "')]";

    return {
        buttonInfo: {
            selectXpath: xpath,
            name: text,
            type: "dropdown-option",
            domElement: element
        },
        eventType: "dropdown-option"
    };
}

function isOpenNewTab(element) {
    if (element === null || element.domElement === null) return false;
    var dom = element.domElement
    var tag = dom.tagName ? dom.tagName.toUpperCase() : '';
    if (tag !== 'A') return false;
    var target = dom.getAttribute('target');
    return target === '_blank';
}

function getSelectInfoFromRoot(selectRoot) {
    if (!selectRoot) return null;

    var name = "";

    // 1. Если есть label[title] — используем его (как во второй части условия)
    var label = selectRoot.querySelector("label[title]");
    if (label) {
        name = (label.getAttribute("title") || "").trim();
    }

    // 2. Иначе берём любой видимый текст внутри form-select (placeholder / выбранное значение),
    //    чтобы сработал contains(., 'name')
    if (!name) {
        // прицелимся в placeholder ant-select
        var placeholderEl = selectRoot.querySelector(".ant-select-placeholder");
        if (placeholderEl) {
            name = (placeholderEl.textContent || "").trim();
        }

        // если даже placeholder не нашли — можно попробовать общее textContent
        if (!name) {
            name = (selectRoot.textContent || "").trim();
        }
    }

    console.log("[SELECT-INFO] label:", label, "name:", name);

    if (!name) return null;

    var safeName = name.replace(/'/g, "\\'");

    // XPath ТОЧНО как в Select.java
    var xpath =
        "//*[@data-testid='form-select' and " +
        "(contains(., '" + safeName + "') or .//label[contains(@title, '" + safeName + "')])]";

    return {
        name: name,
        safeName: safeName,
        xpath: xpath
    };
}



function isButtonOrLink(element) {
	// Идем вверх по DOM до button или корня
	var currentElement = element;
	while (currentElement) {
		var currentTagName = currentElement.tagName ? currentElement.tagName.toUpperCase() : '';
        var role = element.getAttribute('role');
		// Если нашли button - проверяем условия XPath
		if (currentTagName === 'BUTTON' || currentTagName === 'A' || role === 'button') {
			var buttonInfo = getElementConditions(currentElement, currentTagName);
			if (buttonInfo != null) {
				return buttonInfo;
			}
			break; // Если button не подходит - дальше не идем
		}
		currentElement = currentElement.parentElement;
	}
	return null;
}

function isTab(element) {
    var tabBtn = element;
	if (!tabBtn.className.includes('ant-tabs-tab-btn')) {
		tabBtn = element.querySelector('.ant-tabs-tab-btn') || element;
	}
	 // Подстрахуемся: таб должен иметь role="tab"
    var role = tabBtn.getAttribute && tabBtn.getAttribute('role');
    if (role !== 'tab') {
        return null;
    }

    return getTabConditions(tabBtn);
}

function getTabConditions(tabBtn) {
    var titleSpan =
        tabBtn.querySelector('.ant-typography') ||
        tabBtn.querySelector('.ant-tabs-tab-btn span');

    var text = titleSpan
        ? (titleSpan.textContent || '').trim()
        : (tabBtn.textContent || '').trim();

    if (!text) return null;

    text = sanitizeText(text); // если хочешь фильтрацию и тут

    var safeText = text.replace(/'/g, "\\'");
    var xpath = "//div[@role='tab' and contains(., '" + safeText + "')]";

    return {
        xpath: xpath,
        name: safeText,
        type: 'tab',
        domElement: tabBtn   // ← вот так, не element
    };
}

function getElementConditions(element, tagName) {
	var selectors = {
		'BUTTON': 'button',
		'A': 'a'
	};
	var baseTag = selectors[tagName] || '*';
	var dataTestIds = {
		'BUTTON': 'button',
		'A': 'link-button'
	};
	var dataTestId = dataTestIds[tagName];

	// 1. data-testid (React)
	if (element.getAttribute('data-testid') === dataTestId) {
		var elText = tagName === 'BUTTON' ?
			element.querySelector('span')?.textContent?.trim() || element.textContent.trim() :
			element.textContent.trim();

		if (elText && elText !== '' &&
			!(tagName === 'BUTTON' && element.className.includes('-trigger'))) {
			    elText = sanitizeText(elText);
			return {
				xpath: "//*[@data-testid='" + dataTestId + "' and contains(., '" + elText + "')]" +
						(tagName === 'BUTTON' ? " and not(contains(@class, '-trigger'))" : ""),
				name: elText,
				domElement: element
			};
		}
	}

	// 2. Общие проверки (по приоритету)
	var checks = [
		{ attr: 'aria-label', xpath: "@aria-label" },
		{ attr: 'ng-reflect-message', xpath: "@ng-reflect-message" }
	];

	// Проверяем атрибуты на элементе и потомках
	for (var i = 0; i < checks.length; i++) {
		var attrValue = element.getAttribute(checks[i].attr);
		if (attrValue && attrValue.trim() !== '') {
		    var name = sanitizeText(attrValue.trim());
			return {
				xpath: "//" + baseTag + "[contains(" + checks[i].xpath + ", '" + name + "')]",
				name: name,
				domElement: element
			};
		}
	}

	// Проверяем aria-label у детей
	if (tagName === 'BUTTON') {
		var ariaLabels = element.querySelectorAll('[aria-label]');
		for (var j = 0; j < ariaLabels.length; j++) {
			var childAria = ariaLabels[j].getAttribute('aria-label');
			if (childAria && childAria.trim() !== '') {
               var name = sanitizeText(childAria.trim());
				return {
					xpath: "//" + baseTag + "[contains(.//@aria-label, '" + name + "')]",
					name: name,
					domElement: element
				};
			}
		}
	}

	if (element.tagName &&
            element.tagName.toUpperCase() === 'MAT-EXPANSION-PANEL-HEADER') {

            var nameSpan =
                element.querySelector('.iqhr-menu-list__item-name') ||
                element.querySelector('.mat-content span');

            var text = nameSpan
                ? (nameSpan.textContent || "").trim()
                : (element.textContent || "").trim();

            if (text) {
                text = sanitizeText(text);
                var safeText = text.replace(/'/g, "\\'");

                return {
                    xpath: "//mat-expansion-panel-header[contains(., '" + safeText + "')]",
                    name: text,
                    domElement: element
                };
            }
        }

        // 4. Текст элемента (универсально)
        var textContent = element.textContent.trim();
        if (textContent !== '') {
            textContent = sanitizeText(textContent.trim());
            return {
                xpath: "//" + baseTag + "[contains(., '" + textContent + "')]",
                name: textContent,
                domElement: element
            };
        }

	// 4. Специально для ссылок - href без цифр
	if (tagName === 'A') {
		var hrefText = element.getAttribute('href');
		if (hrefText && !/\d/.test(hrefText)) {
			return {
				xpath: "//a[contains(@href, '" + hrefText + "')]",
				name: hrefText,
				domElement: element
			};
		}
	}

	return null;
}

function isRadioOrCheckBox(element) {
    var currentElement = element;

    while (currentElement) {
        var currentTagName = currentElement.tagName ? currentElement.tagName.toUpperCase() : '';
        var currentClass = currentElement.getAttribute('class') || '';

        var isAntCheckboxLabel =
            currentTagName === 'LABEL' && currentClass.includes('ant-checkbox-wrapper');

        var isAntRadioLabel =
            currentTagName === 'LABEL' && (
                currentClass.includes('ant-radio-wrapper') ||
                currentClass.includes('ant-radio-button-wrapper') ||
                currentClass.includes('ant-segmented-item')
            );

        var isMatCheckbox = currentTagName === 'MAT-CHECKBOX';
        var isMatRadio = currentTagName === 'MAT-RADIO-BUTTON';

        if (isAntCheckboxLabel || isMatCheckbox) {
            var checkbox = checkboxConditions(currentElement);
            if (checkbox !== null) {
                if (checkbox.type === 'checkbox-group') {
                    return {
                        buttonInfo: checkbox,
                        type: 'CheckBoxGroup',
                        javaData: "new CheckBoxGroup(" + checkbox.name + ")"
                    };
                    }
                    else {
                    return {
                            buttonInfo: checkbox,
                            type: 'CheckBoxButton',
                            javaData: "new CheckBoxButton(" + checkbox.name + ")"
                        };
                    }
                }
            break;
        }

        if (isAntRadioLabel || isMatRadio) {
            var radio = radioConditions(currentElement);
            if (radio != null) {
                    if (radio.type === 'radio-group') {
                        return {
                            buttonInfo: radio,
                            type: 'RadioGroup',
                            javaData: "new RadioGroup(" + radio.name + ")"
                        };
                    } else {
                        return {
                                buttonInfo: radio,
                                type: 'RadioButton',
                                javaData: "new RadioButton(" + radio.name + ")"
                            };
                    }
                }
            break;
        }

        currentElement = currentElement.parentElement;
    }

    return null;
}

function checkboxConditions(element) {
    // Нормализация: если кликнули по вложенному input/span внутри ant-checkbox-wrapper,
    // всегда поднимаемся до самого label.ant-checkbox-wrapper
    var rootLabel = element.closest &&
        element.closest("label.ant-checkbox-wrapper");

    if (rootLabel) {
        element = rootLabel;
    }

    var textContent = (element.textContent || "").trim();
    var currentElement = element;

    // Если это label и ещё не поднялись до data-testid='form-checkbox',
    // идём вверх, как и раньше
    if (element.tagName.toUpperCase() === 'LABEL') {
        while (!currentElement.getAttribute('data-testid') &&
               currentElement.parentElement != null) {

            if (currentElement.getAttribute('data-testid') === 'form-checkbox') {
                element = currentElement;
                break;
            }

            currentElement = currentElement.parentElement;
        }
    }

    // Группа чекбоксов form-checkbox
    if (element.getAttribute('data-testid') === 'form-checkbox') {
        var label =
            element.closest('.ant-form-item-row')?.querySelector('.ant-form-item-label label') ||
            element.querySelector('label[title]') ||
            element.querySelector('label') ||
            element.closest('label');

        if (label) {
            var labelText = (label.textContent || "").trim();
            var titleText = (label.getAttribute('title') || "").trim();
            var nameText = labelText || titleText;
            nameText = sanitizeText(nameText);

            if (nameText) {
                return {
                    xpath: "//*[@data-testid='form-checkbox' and (.//label[contains(text(), '" + nameText + "') or contains(@title, '" + nameText + "')])]",
                    name: nameText,
                    type: 'checkbox-group',
                    domElement: element
                };
            }
        }
    }

    // Обычный чекбокс (одиночный)
    if (textContent !== '') {
        textContent = sanitizeText(textContent);

        if (element.tagName.toUpperCase() === 'LABEL') {
            return {
                xpath: "//label[contains(@class, 'ant-checkbox-wrapper') and contains(., '" + textContent + "')]",
                name: textContent,
                type: 'checkbox-single',
                domElement: element
            };
        } else {
            return {
                xpath: "//mat-checkbox[contains(., '" + textContent + "')]",
                name: textContent,
                type: 'checkbox-single',
                domElement: element
            };
        }
    }

    return null;
}

function radioConditions(element) {
    var textContent = (element.textContent || "").trim();
    var currentElement = element;

    var radioRoot = element.closest && element.closest("[data-testid='form-radio']");
    if (radioRoot) {
        element = radioRoot;
        currentElement = radioRoot;
    }

    if (element.tagName.toUpperCase() === 'LABEL') {
        while (!currentElement.getAttribute('data-testid') &&
               currentElement.parentElement != null) {

            if (currentElement.getAttribute('data-testid') === 'form-radio') {
                element = currentElement;
                break;
            }

            currentElement = currentElement.parentElement;
        }
    }

    if (element.getAttribute('data-testid') === 'form-radio') {
        var label =
            element.closest('.ant-form-item-row')?.querySelector('.ant-form-item-label label') ||
            element.querySelector('label[title]') ||
            element.querySelector('label') ||
            element.closest('label');

        if (label) {
            var labelText = (label.textContent || "").trim();
            var titleText = (label.getAttribute('title') || "").trim();
            var nameText = labelText || titleText;
            nameText = sanitizeText(nameText);

            if (nameText) {
                return {
                    xpath: "//*[@data-testid='form-radio' and (.//label[contains(text(), '" + nameText + "') or contains(@title, '" + nameText + "')])]",
                    name: nameText,
                    type: 'radio-group',
                    domElement: element
                };
            }
        }
    }

    var segmentedRoot = element.closest &&
        element.closest("div.ant-segmented[role='radiogroup']");
    if (segmentedRoot) {
        var nameText =
            (segmentedRoot.getAttribute("aria-label") || "").trim() ||
            (segmentedRoot.getAttribute("id") || "").trim();

        nameText = sanitizeText(nameText);

        if (!nameText) {
            var activeLabel = segmentedRoot.querySelector(
                ".ant-segmented-item.ant-segmented-item-selected .ant-segmented-item-label"
            );
            if (activeLabel) {
                nameText = sanitizeText((activeLabel.textContent || "").trim());
            }
        }

        if (nameText) {
            var safeName = nameText.replace(/'/g, "\\'");
            var xpath =
                "//div[@role='radiogroup' and contains(@class, 'ant-segmented') and " +
                "(@id='" + safeName + "' or @aria-label='" + safeName + "')]";

            return {
                xpath: xpath,
                name: nameText,
                type: 'radio-group',
                domElement: segmentedRoot
            };
        }
    }

    if (textContent !== '') {
        textContent = sanitizeText(textContent);

        return {
            xpath: "(//mat-radio-button[contains(., '" + textContent + "')] | " +
                   "//label[(contains(@class, 'ant-radio-wrapper') " +
                   "or contains(@class, 'ant-radio-button-wrapper') " +
                   "or contains(@class, 'ant-segmented-item')) " +
                   "and (contains(., '" + textContent + "') " +
                   "or .//div[@class='ant-segmented-item-label' and contains(@title, '" + textContent + "')])])",
            name: textContent,
            type: 'radio-single',
            domElement: element
        };
    }

    return null;
}

