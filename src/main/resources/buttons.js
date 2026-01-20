function isClickableElement(element) {
	if (!element) return { isClickable: false, buttonInfo: null };
	var tagName = element.tagName ? element.tagName.toUpperCase() : '';
	var buttonInfo = null;
	var javaData = null;
	var isClickable = false;
	if (tagName === 'BUTTON' || tagName === 'A') {
		buttonInfo = isButtonOrLink(element);
		console.log("BUTTONAINFO", buttonInfo);
		isClickable = true;
		tagName === 'BUTTON' ?
		javaData = {
			init_string: buttonInfo === null ? "new Button(" + buttonInfo.name + ")" : ""
		} :
		javaData = {
			init_string: buttonInfo === null ? "new LinkButton(" + buttonInfo.name + ")" : ""
		};
	}
	if (tagName === 'LABEL' || tagName === 'INPUT') {
		buttonInfo = isRadioOrCheckBox(element);
		isClickable = true;
	}
	if (tagName === 'INPUT' && element.getAttribute('aria-haspopup')) {
		isClickable = true;
	}
	var role = element.getAttribute('role');
	if (role === 'button' || role === 'tab' || role === 'menuitem') {
		isClickable = true;
	}
	return {
		isClickable: isClickable,
		buttonInfo: buttonInfo,
		javaData: javaData
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
			return {
				xpath: "//*[@data-testid='" + dataTestId + "' and contains(., '" + elText + "')]" +
						(tagName === 'BUTTON' ? " and not(contains(@class, '-trigger'))" : ""),
				name: elText
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
			return {
				xpath: "//" + baseTag + "[contains(" + checks[i].xpath + ", '" + attrValue.trim() + "')]",
				name: attrValue.trim()
			};
		}
	}

	// Проверяем aria-label у детей
	if (tagName === 'BUTTON') {
		var ariaLabels = element.querySelectorAll('[aria-label]');
		for (var j = 0; j < ariaLabels.length; j++) {
			var childAria = ariaLabels[j].getAttribute('aria-label');
			if (childAria && childAria.trim() !== '') {
				return {
					xpath: "//" + baseTag + "[contains(.//@aria-label, '" + childAria.trim() + "')]",
					name: childAria.trim()
				};
			}
		}
	}

	// 3. Текст элемента (универсально)
	var textContent = element.textContent.trim();
	if (textContent !== '') {
		return {
			xpath: "//" + baseTag + "[contains(., '" + textContent + "')]",
			name: textContent
		};
	}

	// 4. Специально для ссылок - href без цифр
	if (tagName === 'A') {
		var hrefText = element.getAttribute('href');
		if (hrefText && !/\d/.test(hrefText)) {
			return {
				xpath: "//a[contains(@href, '" + hrefText + "')]",
				name: hrefText
			};
		}
	}

	return null;
}

//todo понять на что тыкаем в чекбоксах и радио. кажись инпут, но я не уверена
function isRadioOrCheckBox(element) {
	var currentElement = element;
	while (currentElement) {
		var currentTagName = currentElement.tagName ? currentElement.tagName.toUpperCase() : '';
		var currentClass = currentElement.getAttribute('class')
		if (currentTagName === 'MAT-RADIO-BUTTON' || currentTagName === 'MAT-CHECKBOX'
			|| (currentTagName === 'LABEL' && currentClass &&
				(currentClass.includes('ant-checkbox-wrapper') ||
				currentClass.includes('ant-radio-wrapper') ||
				currentClass.includes('ant-radio-button-wrapper') ||
				currentClass.includes('ant-segmented-item')))) {
			if (currentClass.includes('checkbox') || currentTagName.includes('CHECKBOX')) {
				var checkbox = checkboxConditions(currentElement);
				if (checkbox != null) {
					return checkbox;
				}
			} else {
				var radio = checkboxConditions(currentElement);
				if (radio != null) {
					return radio
				}
			}
			break;
		}
		currentElement = currentElement.parentElement;
	}
	return null;
}

//todo здесь надо проработать то, что мы поднимаемся наверх, чтобы найти группу и вытащить ее, а не элемент!
function checkboxConditions(element) {
	var textContent = element.textContent.trim();
	var currentElement = element;
	if (element.tagName.toUpperCase() === 'LABEL') {
		while (!currentElement.getAttribute('data-testid') && currentElement.parentElement != null) {
			if (currentElement.getAttribute('data-testid') === 'form-checkbox') {
				element = currentElement;
				break;
			}
			currentElement = currentElement.parentElement
		}
	}

	if (element.getAttribute('data-testid') === 'form-checkbox') {
		// Ищем label внутри или рядом
		var label = element.querySelector('label') ||
				   element.querySelector('.ant-checkbox-wrapper') ||
				   element.closest('label');

		if (label) {
			var labelText = label.textContent.trim();
			var titleText = label.getAttribute('title')?.trim();

			if (labelText) {
				return {
					xpath: "//*[@data-testid='form-checkbox' and (.//label[contains(text(), '" + labelText + "') or contains(@title, '" + labelText + "')])]",
					name: labelText,
					type: 'checkbox-group'  // Маркер группы
				};
			}
		}
	}
	// Обычный чекбокс (fallback)
	if (textContent !== '') {
		if (element.tagName === 'LABEL') {
			return {
			xpath: "//label[contains(@class, 'ant-checkbox-wrapper') and contains(., '" + textContent + "')]",
			name: textContent,
			type: 'checkbox-single'  // Маркер одиночного
		};
		} else {
			return {
				xpath: "//mat-checkbox[contains(., '" + textContent + "')]",
				name: textContent,
				type: 'checkbox-single'  // Маркер одиночного
			};
		}
	}

	return null;
}

//todo здесь надо проработать то, что мы поднимаемся наверх, чтобы найти группу и вытащить ее, а не элемент!
function radioConditions(element) {
    var textContent = element.textContent.trim();

	var currentElement = element;
	if (element.tagName.toUpperCase() === 'LABEL') {
		while (!currentElement.getAttribute('data-testid') && currentElement.parentElement != null) {
			if (currentElement.getAttribute('data-testid') === 'form-radio') {
				element = currentElement;
				break;
			}
			currentElement = currentElement.parentElement
		}
	}

    // Проверяем data-testid='form-radio' + label/title (группа радио)
    if (element.getAttribute('data-testid') === 'form-radio') {
        var label = element.querySelector('label') ||
                   element.closest('label');

        if (label) {
            var labelText = label.textContent.trim();
            var titleText = label.getAttribute('title')?.trim();
            var nameText = labelText || titleText;

            if (nameText) {
                return {
                    xpath: "//*[@data-testid='form-radio' and (.//label[contains(text(), '" + nameText + "') or contains(@title, '" + nameText + "')])]",
                    name: nameText,
                    type: 'radio-group'
                };
            }
        }
    }

    // Одиночная радио кнопка (fallback)
    if (textContent !== '') {
        return {
            xpath: "(//mat-radio-button[contains(., '" + textContent + "')] | //label[(contains(@class, 'ant-radio-wrapper') or contains(@class, 'ant-radio-button-wrapper') or contains(@class, 'ant-segmented-item')) and (contains(., '" + textContent + "') or .//div[@class='ant-segmented-item-label' and contains(@title, '" + textContent + "')])])",
            name: textContent,
            type: 'radio-single'
        };
    }

    return null;
}
