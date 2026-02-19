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
    console.log("MY TAAAAAGNAAAAME is '" + tagName + "'");
    var isEditable = tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'MAT-FORM-FIELD';
    console.log("IS EDITSBLE = ", isEditable)
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
 console.log("I AM BLUUUR!");
    if (isEditableInput(e.target) && window.currentFocusedXPath) {
        var currentValue = e.target.value || '';

        // Логируем только изменение значения
        console.log("I AM EDITABLE!");
        if (window.currentFocusedValue !== currentValue) {
            var fieldInfo = getFieldInfoFromInput(e.target);
            console.log("FIELD INFO!", fieldInfo);
            window.recordedInputs.push({
                xpath: fieldInfo ? fieldInfo.xpath : window.currentFocusedXPath,
                value: currentValue,
                id: e.target.id || '',
                type: fieldInfo ? fieldInfo.type : 'field',
                name: fieldInfo ? fieldInfo.name : '',
                timestamp: Date.now()
            });
        }

        window.currentFocusedXPath = null;
        window.currentFocusedElement = null;
        window.currentFocusedValue = '';
    }
}, true);

