function isClickableElement(element) {

    if (!element) return { isClickable: false, buttonInfo: null, javaData: null, eventType: null };

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    var buttonInfo = null;
    var javaData = null;
    var isClickable = false;
    var role = element.getAttribute('role');
    var eventType = null;

    console.log("INFO: TAGNAME =", tagName, ", ROLE = ", role);

    // ===== Кнопки и ссылки =====
    if (tagName === 'BUTTON' || tagName === 'A'  || role === 'button') {

        console.log("INFO: TAGNAME =", tagName, ", ROLE = ", role);

        buttonInfo = isButtonOrLink(element);
        if (buttonInfo != null) {

            console.log("[CLICKABLE] BUTTON/LINK", buttonInfo);
            isClickable = true;

            if (tagName === 'BUTTON') {
                javaData = {
                    init_string: buttonInfo === null ? "new Button(" + buttonInfo.name + ")" : ""
                };
            } else if (tagName === 'A') {
                javaData = {
                    init_string: buttonInfo === null ? "new LinkButton(" + buttonInfo.name + ")" : ""
                };
            } else {
                    javaData = {
                        init_string: buttonInfo === null ? "new Button(" + buttonInfo.name + ", $x(" + buttonInfo.xpath + "))" : ""
                    };
            }
        }

    // ===== Радио/чекбоксы =====
    } else if (tagName === 'LABEL' || tagName === 'INPUT') {

        buttonInfo = isRadioOrCheckBox(element);
        if (buttonInfo != null) {
            console.log("[CLICKABLE] RADIO/CHECKBOX", buttonInfo);
            isClickable = true;
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
            javaData = {
                init_string: buttonInfo === null ? "new TabButton(" + buttonInfo.name + ")" : ""
            };
        }
    }

    // ===== form-select (открытие селекта) =====
    if (!isClickable) {
        var selectRoot = element.closest && element.closest("[data-testid='form-select']");
        if (selectRoot) {
            var selectInfo = getSelectInfoFromRoot(selectRoot);
            if (selectInfo) {
                isClickable = true;
                eventType = "select-open";
                buttonInfo = {
                    xpath: selectInfo.xpath,
                    name: selectInfo.name,
                    type: "select",
                    domElement: null
                };
            }
        }
    }

    // ===== элемент списка для этого селекта =====
    if (
        !isClickable &&
        tagName === 'DIV' &&
        element.className &&
        element.className.includes('ant-select-item-option')
    ) {
        var title = element.getAttribute('title') || (element.textContent || '').trim();
        if (title) {
            var dropdown = element.closest("div.ant-select-dropdown");
            if (dropdown) {
                var listbox = dropdown.querySelector("div[role='listbox'][id]");
                if (listbox) {
                    var listId = listbox.getAttribute("id");
                    var input = document.querySelector(
                        "[data-testid='form-select'] input[aria-controls='" + listId + "']"
                    );
                    if (input) {
                        var selectRoot2 = input.closest("[data-testid='form-select']");
                        var selectInfo2 = getSelectInfoFromRoot(selectRoot2);
                        if (selectInfo2) {
                            var safeTitle = title.replace(/'/g, "\\'");
                            buttonInfo = {
                                xpath: "//div[./div[@role='listbox' and @id='" + listId + "']]//" +
                                       "div[contains(@class,'ant-select-item-option') and @title='" + safeTitle + "']",
                                name: title,
                                type: "select-option",
                                selectXpath: selectInfo2.xpath,
                                selectName: selectInfo2.name,
                                domElement: null
                            };
                            isClickable = true;
                            eventType = "select-option";
                        }
                    }
                }
            }
        }
    }

    return {
        isClickable: isClickable,
        buttonInfo: buttonInfo,
        javaData: javaData,
        eventType: eventType
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
		// Если нашли button - проверяем условия XPath
		if (currentTagName === 'BUTTON' || currentTagName === 'A') {
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
            if (checkbox != null) return checkbox;
            break;
        }

        if (isAntRadioLabel || isMatRadio) {
            var radio = radioConditions(currentElement);
            if (radio != null) return radio;
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

