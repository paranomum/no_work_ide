function isClickableElement(element) {

    if (!element) return { isClickable: false, buttonInfo: null, javaData: null, eventType: null };

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    var buttonInfo = null;
    var init_by_xpath = false;
    var isClickable = false;
    var role = element.getAttribute('role');
    var eventType = null;
    var elType = null;
    var index = 0;

    console.log("INFO: TAGNAME =", tagName, ", ROLE = ", role);

    // ===== Кнопки и ссылки =====
    if (tagName === 'BUTTON' || tagName === 'A'  || role === 'button') {

        console.log("INFO: TAGNAME =", tagName, ", ROLE = ", role);

        isClickable = true;
        buttonInfo = isButtonOrLink(element);
        if (buttonInfo != null) {

            console.log("[CLICKABLE] BUTTON/LINK", buttonInfo);
            isClickable = true;

            elType = tagName === 'A' ? "LinkButton" : "Button";
            init_by_xpath = tagName !== 'BUTTON' && tagName !== 'A';
        }

    // ===== Радио/чекбоксы =====
    } else if (tagName === 'LABEL' || tagName === 'INPUT') {
        var radioOrCheck = isRadioOrCheckBox(element);
        if (radioOrCheck != null) {
            buttonInfo = radioOrCheck.buttonInfo;
            console.log("[CLICKABLE] RADIO/CHECKBOX", buttonInfo);
            isClickable = true;
            elType = radioOrCheck.type;
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

        isClickable = true;
        buttonInfo = isTab(element);
        if (buttonInfo != null) {

            console.log("[CLICKABLE] TAB", buttonInfo);
            isClickable = true;
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
            elType = "Dropdown";
        }
    }

    // ... после dropdown / select, до return
    if (!isClickable) {
        var dateResult = detectDatePickerOpen(element);
        if (dateResult) {
            isClickable = true;
            buttonInfo = dateResult.buttonInfo;
            eventType = dateResult.eventType;
            elType = dateResult.uiType || "DatePicker";
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

    // ===== выбор даты в datepicker =====
    if (!isClickable) {
        var dateDayResult = detectDatePickerDay(element);
        if (dateDayResult) {
            isClickable = true;
            buttonInfo = dateDayResult.buttonInfo;
            eventType = dateDayResult.eventType;
            elType = "DatePicker";
        }
    }

    if (isClickable && (!buttonInfo || !buttonInfo.domElement || !buttonInfo.xpath)) {
        buttonInfo = {
            domElement: element,
            name: buttonInfo && buttonInfo.name ? buttonInfo.name : '',
            xpath: getXPath(element)
        };
        elType = 'Unknown';
    }

    if (isClickable && !init_by_xpath && buttonInfo && buttonInfo.xpath && buttonInfo.domElement) {
        index = getIndexByXPathAndElement(buttonInfo.xpath, buttonInfo.domElement);
    }

    return {
        isClickable: isClickable,
        buttonInfo: buttonInfo,
        init_by_xpath: init_by_xpath,
        eventType: eventType,
        type: elType,
        index: (index + 1),
        javaData: ""
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
        domElement: tabBtn
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
				xpath:
                  "//*[@data-testid='" + dataTestId + "'" +
                  " and contains(., '" + elText + "')" +
                  (tagName === 'BUTTON' ? " and not(contains(@class, '-trigger'))" : "") +
                  "]",
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
                        javaData: "new CheckBoxGroup(\"" + checkbox.name + "\")"
                    };
                    }
                    else {
                    return {
                            buttonInfo: checkbox,
                            type: 'CheckBoxButton',
                            javaData: "new CheckBoxButton(\"" + checkbox.name + "\")"
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
                            javaData: "new RadioGroup(\"" + radio.name + "\")"
                        };
                    } else {
                        return {
                                buttonInfo: radio,
                                type: 'RadioButton',
                                javaData: "new RadioButton(\"" + radio.name + "\")"
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

    textContent = sanitizeText(textContent);

if (textContent !== '') {
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

