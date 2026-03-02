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

/**
 * Нормализованный XPath: поднимаемся до ближайшего кликабельного контейнера
 * (button / input / textarea / select / div[@role='button'|'tab']),
 * но сам путь строим старым getXPath.
 */
function getClickableXPath(element) {
    if (!element || element.nodeType !== 1) return '';

    var root = element;
    var depth = 0;
    while (root && depth < 10) {
        var tag = root.tagName ? root.tagName.toUpperCase() : '';
        var role = root.getAttribute && root.getAttribute('role');

        if (
            tag === 'BUTTON' ||
            tag === 'INPUT' ||
            tag === 'TEXTAREA' ||
            tag === 'SELECT' ||
            (tag === 'DIV' && (role === 'button' || role === 'tab'))
        ) {
            break;
        }

        root = root.parentElement;
        depth++;
    }

    // если нашли подходящий контейнер — строим XPath от него,
    // иначе fallback к исходному элементу
    return getXPath(root || element);
}

function sanitizeText(text) {
  if (!text) return '';
  return text.replace('Created with Pixso.', '').trim();
}

function generateRootXPath(buttonElement) {
  var parts = [];
  var current = buttonElement;
  while (current && current !== document.documentElement) {
    var index = getElementIndex(current);
    parts.unshift("child::" + current.tagName.toLowerCase() + "[" + index + "]");
    current = current.parentElement;
  }
  return "/html/body/" + parts.join('/');
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
  return tagName === 'INPUT' || tagName === 'TEXTAREA' || tagName === 'MAT-FORM-FIELD';
}

function findClickable(element) {
  var current = element;
  var depth = 0;

  while (current && depth < 20) {
    var cur = isClickableElement(current); // из buttons-4.js
    if (cur && cur.isClickable) {
      return cur;
    }
    if (current.tagName && current.tagName.toUpperCase() === 'BODY') break;
    current = current.parentElement;
    depth++;
  }
  return { isClickable: false, buttonInfo: null, javaData: null };
}