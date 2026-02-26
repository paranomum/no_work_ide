window.recordedClicks = window.recordedClicks || [];
window.recordedInputs = window.recordedInputs || [];
window.currentFocusedElement = window.currentFocusedElement || null;
window.currentFocusedXPath = window.currentFocusedXPath || null;
window.currentFocusedValue = window.currentFocusedValue || '';
window.currentTabState = document.visibilityState === 'visible' ? 'active' : 'inactive';
window.datepickerState = window.datepickerState || {
    lastOpenXPath: null,
    clickIndex: 0
};

function sanitizeText(text) {
    if (!text) return '';
    return text.replace('Created with Pixso.', '').trim();
}

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

    var tagName = element.tagName ? element.tagName.toUpperCase().trim() : '';
    var isEditable = tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'MAT-FORM-FIELD';
    return tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'MAT-FORM-FIELD';
}

function findClickable(element) {
    var current = element;
    var depth = 0;

    while (current && depth < 20) {
        var cur = isClickableElement(current);
        if (cur && cur.isClickable) {
            return cur;
        }
        if (current.tagName && current.tagName.toUpperCase() === 'BODY') break;
        current = current.parentElement;
        depth++;
    }

    return { isClickable: false, buttonInfo: null, javaData: null };
}

document.addEventListener('click', function(e) {
    var element = e.target;
    console.log('[ACTIONS] RAW CLICK target:', element.tagName, element.className);
     // 1. Режем дубль для обычных ant-radio
        if (
            element.tagName &&
            element.tagName.toUpperCase() === 'INPUT' &&
            element.classList &&
            element.classList.contains('ant-radio-input') &&
            element.closest &&
            element.closest('label.ant-radio-wrapper')
        ) {
            console.log("[ACTIONS] skip ant-radio input");
            return;
        }

        // 2. Режем дубль для ant-segmented
        if (
            element.tagName &&
            element.tagName.toUpperCase() === 'INPUT' &&
            element.classList &&
            element.classList.contains('ant-segmented-item-input') &&
            element.closest &&
            element.closest('div.ant-segmented[role=\"radiogroup\"]')
        ) {
            console.log("[ACTIONS] skip ant-segmented input");
            return;
        }

    if (
            element.tagName &&
            element.tagName.toUpperCase() === 'INPUT' &&
            element.closest &&
            (element.closest('label.ant-checkbox-wrapper') || element.closest('label.ant-radio-wrapper'))
        ) {
             console.log("[ACTIONS] skip input inside ant-checkbox-wrapper");
            return;
        }

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    var clickable = findClickable(element);
    console.log('[ACTIONS] findClickable result:', clickable);

    if (clickable.isClickable) {
        var info = clickable.buttonInfo || {};
        console.log("CLICKED ON ", info);
        var isNewTab = isOpenNewTab(info);

        console.log("[ACTIONS] CLICK captured:", {
            tag: tagName,
            info: info,
            eventType: clickable.eventType,
            isNewTab: isNewTab
        });

        if (clickable.eventType === 'datepicker-open') {
                    window.datepickerState.lastOpenXPath = info.xpath || getXPath(element);
                    window.datepickerState.clickIndex = 0;
        }

        var extra = {};

        if (clickable.eventType === 'datepicker-date') {
            extra.rangeIndex = window.datepickerState.clickIndex;
            window.datepickerState.clickIndex += 1;

            // при желании — использовать xpath открытого datepicker
            extra.selectXpath = window.datepickerState.lastOpenXPath || info.selectXpath || null;
        }

        var clickRecord = {
            xpath: info.xpath || getXPath(element),
            id: element.id || '',
            tag: tagName,
            text: info.name || '',
            eventType: clickable.eventType || 'click',
            elementType: clickable.type,
            selectXpath: info.selectXpath || null,
            selectName: info.selectName || null,
            javaData: clickable.javaData || '',
            newTab: isNewTab
        };

        if (extra.rangeIndex !== undefined) {
            clickRecord.rangeIndex = extra.rangeIndex;
        }
        if (extra.selectXpath) {
            clickRecord.selectXpath = extra.selectXpath;
        }

        console.log('[ACTIONS] findClickable result:', clickable);
        console.log(window.recordedClicks)

        window.recordedClicks.push(clickRecord);

        // Если ссылка открывает новую вкладку, тормозим переход,
        // чтобы Java успела забрать записанный клик
        if (isNewTab && element.href) {
            e.preventDefault();
            var href = element.href;
            setTimeout(function() {
                window.open(href, '_blank');
            }, 500);
        }

        return;
    }
}, true);

document.addEventListener('focus', function(e) {
    var element = e.target;
    if (isEditableInput(element)) {
        var fieldInfo = getFieldInfoFromInput(element); // из input.js
        window.currentFocusedXPath = fieldInfo ? fieldInfo.xpath : getXPath(element);
        window.currentFocusedElement = element;
        window.currentFocusedValue = element.value || '';
    }
}, true);

document.addEventListener('blur', function(e) {
    if (isEditableInput(e.target) && window.currentFocusedXPath) {
        var currentValue = e.target.value || '';

        // Логируем только изменение значения
        if (window.currentFocusedValue !== currentValue) {
            var picker = getFieldInfoFromDatePicker(e.target);
            console.log("DATA - ", picker);
            if (picker === null) {
            var fieldInfo = getFieldInfoFromInput(e.target);
            window.recordedInputs.push({
                xpath: fieldInfo ? fieldInfo.xpath : window.currentFocusedXPath,
                value: currentValue,
                id: e.target.id || '',
                type: fieldInfo ? fieldInfo.type : 'field',
                name: fieldInfo ? fieldInfo.name : '',
                timestamp: Date.now(),
                javaData: fieldInfo.javaData ? fieldInfo.javaData : ''
            });
        }
        }

        window.currentFocusedXPath = null;
        window.currentFocusedElement = null;
        window.currentFocusedValue = '';
    }
}, true);

document.addEventListener('visibilitychange', function() {
    var state = document.visibilityState === 'visible' ? 'active' : 'inactive';
    window.currentTabState = state;

    // ставим событие таба в очередь после обработки клика
    setTimeout(function() {
        window.recordedClicks.push({
            xpath: null,
            id: '',
            tag: 'DOCUMENT',
            text: '',
            eventType: state === 'active' ? 'tab-active' : 'tab-inactive',
            selectXpath: null,
            selectName: null
        });
    }, 0);
});
