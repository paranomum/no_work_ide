window.recordedClicks = [];
window.recordedInputs = [];
window.currentFocusedElement = null;
window.currentFocusedXPath = null;
window.currentFocusedValue = '';

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

function isClickableElement(element) {
	if (!element) return { isClickable: false, buttonInfo: null };
	var tagName = element.tagName ? element.tagName.toUpperCase() : '';
	var buttonInfo = null;
	var isClickable = false;
	if (tagName === 'BUTTON') {
		buttonInfo = isButton(element);
		console.log("BUTTONINFO", buttonInfo);
		isClickable = true;
	}
	if (tagName === 'A') {
		buttonInfo = isAnchor(element);
		console.log("ANCHORINFO", buttonInfo);
		isClickable = true;
	}
	if (tagName === 'LABEL') {
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
		buttonInfo: buttonInfo
	};
}

function isButton(element) {
	// Идем вверх по DOM до button или корня
	var currentElement = element;
	while (currentElement) {
		var currentTagName = currentElement.tagName ? currentElement.tagName.toUpperCase() : '';
		// Если нашли button - проверяем условия XPath
		if (currentTagName === 'BUTTON') {
			var buttonInfo = buttonConditions(currentElement);
			if (buttonInfo != null) {
				return buttonInfo;
			}
			break; // Если button не подходит - дальше не идем
		}
		currentElement = currentElement.parentElement;
	}
	return null;
}

function buttonConditions(element) {
	// Проверяем на react xpath
	if (element.getAttribute('data-testid') === 'button') {
		var elText = element.querySelector('span');
		if (elText && elText.textContent.trim() !== '' &&
			!element.className.includes('-trigger')) {
				return {
					xpath: "//*[@data-testid='button' " +
						"and ./span[contains(text(), '" + elText.textContent.trim() + "')] " +
						"and not(contains(@class, '-trigger'))]",
					name: elText.textContent.trim()
				};
		}
	}

	// Проверяем на angular xpath
	// var conditions = [
	// 	'ng-reflect-message',
	// 	'aria-label',
	// 	'.'
	// ];
	// Проверяем aria-label на элементе и его детях
	if (element.getAttribute('aria-label') && element.getAttribute('aria-label').trim() !== '') {
		return {
			xpath: "//button[contains(@aria-label, '" + element.getAttribute('aria-label').trim() + "')]",
			name: element.getAttribute('aria-label').trim()
		};
	}
	var ariaLabels = element.querySelectorAll('[aria-label]');
	for (var j = 0; j < ariaLabels.length; j++) {
		if (ariaLabels[j].getAttribute('aria-label').trim() !== '') {
			return {
				xpath: "//button[contains(.//@aria-label, '" + ariaLabels[j].getAttribute('aria-label').trim() + "')]",
				name: ariaLabels[j].getAttribute('aria-label').trim()
			};
		}
	}
	// Проверяем ng-reflect-message
	if (element.getAttribute('ng-reflect-message') && element.getAttribute('ng-reflect-message').trim() !== '') {
		return {
			xpath: "//button[contains(@ng-reflect-message, '" + element.getAttribute('ng-reflect-message').trim() + "')]",
			name: element.getAttribute('ng-reflect-message').trim()
		};
	}
	// Проверяем текст самого элемента
	if (element.textContent.trim() !== '') {
		return {
			xpath: "//button[contains(., '" + element.textContent.trim() + "')]",
			name: element.textContent.trim()
		};
	};
	return null;
}

function isAnchor(element) {
	var currentElement = element;
	while (currentElement) {
		var currentTagName = currentElement.tagName ? currentElement.tagName.toUpperCase() : '';
		if (currentTagName === 'A') {
			var buttonInfo = anchorConditions(currentElement);
			if (buttonInfo != null) {
				return buttonInfo;
			}
			break;
		}
		currentElement = currentElement.parentElement;
	}
	return null;
}

function anchorConditions(element) {
	// Проверяем на react xpath
	if (element.getAttribute('data-testid') === 'link-button') {
		var elText = element.textContent.trim();
		if (elText && elText !== '') {
				return {
					xpath: "//*[@data-testid='link-button' and contains(., '" + elText + "')]",
					name: elText
				};
		}
	}

	// Проверяем на angular xpath
	// var conditions = [
	//	'ng-reflect-message',
	//	'.',
	// 	'href'
	// ];
	// Проверяем ng-reflect-message
	if (element.getAttribute('ng-reflect-message') && element.getAttribute('ng-reflect-message').trim() !== '') {
		return {
			xpath: "//a[contains(@ng-reflect-message, '" + element.getAttribute('ng-reflect-message').trim() + "')]",
			name: element.getAttribute('ng-reflect-message').trim()
		};
	}
	// Проверяем текст самого элемента
	if (element.textContent.trim() !== '') {
		return {
			xpath: "//a[contains(., '" + element.textContent.trim() + "')]",
			name: element.textContent.trim()
		};
	};
	// Проверяем href без id
	var hrefText = element.getAttribute('href');
	if (!/\d/.test(hrefText)) {
		return {
			xpath: "//a[contains(@href, '" + hrefText + "')]",
			name: hrefText
		};
	}
	return null;
}

function findButtonElement(element) {
	var currentElement = element;
	while (currentElement) {
		if (currentElement.tagName && currentElement.tagName.toUpperCase() === 'BUTTON') {
			return currentElement;
		}
		currentElement = currentElement.parentElement;
	}
	return null;
}

function getElementName(buttonElement) {
	if (isButton(buttonElement)) {
		var spans = buttonElement.querySelectorAll('span');
		for (var i = 0; i < spans.length; i++) {
			var spanText = spans[i].textContent.trim();
			if (spanText) {
				return spanText;
			}
		}
		var ariaLabels = buttonElement.querySelectorAll('[aria-label]');
		for (var i = 0; i < ariaLabels.length; i++) {
			var spanText = ariaLabels[i].getAttribute('aria-label').trim();
			if (spanText) {
				return spanText;
			}
		}
		var ngReflect = buttonElement.getAttribute('ng-reflect-message');
		if (ngReflect && ngReflect.trim()){
			return ngReflect.trim();
		}
		return buttonElement.textContent.trim();
	}
	return null;
}
	// // Стандартные селекторы
	// // var id = buttonElement.id;
	// // if (id) return `//button[@id='${id}']`;
	// var classes = buttonElement.className.trim().split(/\\s+/);
	// if (classes.length) {
	// 	var classSelector = classes.map(c => `[contains(@class, '${c}')]`)
	// 								.join('');
	// 	xpathParts.push(classSelector);
	// }
	// // aria-label
	// var ariaLabel = buttonElement.getAttribute('aria-label');
	// if (ariaLabel) {
	// 	xpathParts.push(`@aria-label='${ariaLabel}'`);
	// }
	// if (xpathParts.length) {
	// 	return `//button[${xpathParts.join(' and ')}]`;
	// }
	// // Fallback: позиция среди кнопок
	// return `(//button)[${getButtonIndex(buttonElement) + 1}]`;
function generateRootXPath(buttonElement) {
	// XPath от самого первого элемента (html/body)
	var parts = [];
	var current = buttonElement;
	while (current && current !== document.documentElement) {
		var index = getElementIndex(current);
		parts.unshift(`child::${current.tagName.toLowerCase()}[${index}]`);
		current = current.parentElement;
	}
	return `/html/body/${parts.join('/')}`;
}
function getElementIndex(element) {
	var siblings = Array.from(element.parentElement.children);
	return siblings.indexOf(element) + 1;
}
function getButtonIndex(buttonElement) {
	var buttons = document.querySelectorAll('button');
	return Array.from(buttons).indexOf(buttonElement);
}

function isEditableInput(element) {
	if (!element) return false;
	var tagName = element.tagName ? element.tagName.toUpperCase() : '';
	if (tagName !== 'INPUT' && tagName !== 'TEXTAREA') return false;
	if (element.getAttribute('aria-haspopup')) return false;
	return true;
}
function findClickable(element) {
	var current = element;
	var depth = 0;
	while (current && depth < 20) {
		var cur = isClickableElement(current);
		if (cur.isClickable) return cur;
		if (current.tagName && current.tagName.toUpperCase() === 'BODY') break;
		current = current.parentElement;
		depth++;
	}
	return null;
}

document.addEventListener('click', function(e) {
var element = e.target;
	var tagName = element.tagName ? element.tagName.toUpperCase() : '';
	var clickable = findClickable(element);
	if (clickable.isClickable) {
		window.recordedClicks.push({
		xpath: clickable.buttonInfo ? clickable.buttonInfo.xpath : getXPath(element),
		id: element.id || '',
		tag: tagName,
		text: clickable.buttonInfo ? clickable.buttonInfo.name : ''
		});
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
