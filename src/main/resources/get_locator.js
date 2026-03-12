window.locatorPickResult = null;

// Глобальный подсвеченный элемент
let lastHighlighted = null;

function highlightElement(el) {
    if (!el) return;

    if (lastHighlighted && lastHighlighted !== el) {
        lastHighlighted.classList.remove('__locator-highlight');
    }

    lastHighlighted = el;
    el.classList.add('__locator-highlight');
}

function clearHighlight() {
    if (!lastHighlighted) return;
    lastHighlighted.classList.remove('__locator-highlight');
    lastHighlighted = null;
}

// общий метод выключения режима выбора
function stopLocatorPickMode() {
    clearHighlight();
    document.removeEventListener('mousemove', locatorMouseMoveHandler, true);
    document.removeEventListener('click', locatorClickHandler, true);
    document.removeEventListener('keydown', locatorKeydownHandler, true);
}

// гарантируем наличие стиля для подсветки
(function ensureLocatorHighlightStyle() {
    if (document.getElementById('__locator-highlight-style')) {
        return;
    }
    const style = document.createElement('style');
    style.id = '__locator-highlight-style';
    style.textContent = `
        .__locator-highlight {
            outline: 2px solid yellow !important;
            background-color: rgba(255, 255, 0, 0.2) !important;
        }
    `;
    document.head.appendChild(style);
})();

function findLocatorXpath(element) {
    var current = element;
    var depth = 0;

    while (current && depth < 20) {
        var clickable = isClickableElement(current);
        if (clickable && clickable.isClickable && clickable.buttonInfo && clickable.buttonInfo.xpath) {
            if (clickable.index > 1) {
                return "(" + clickable.buttonInfo.xpath + ")[" + (clickable.index) + "]";
            }
            return clickable.buttonInfo.xpath;
        }

        var field = getFieldInfoSmart(current);
        if (field && field.xpath) {
            if (field.index > 1) {
                return "(" + field.xpath + ")[" + (field.index) + "]";
            }
            return field.xpath;
        }

        if (current.tagName && current.tagName.toUpperCase() === 'BODY') break;
        current = current.parentElement;
        depth++;
    }

    return null;
}

function locatorMouseMoveHandler(e) {
    const el = document.elementFromPoint(e.clientX, e.clientY);
    highlightElement(el);
}

function locatorClickHandler(e) {
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();

    const el = e.target;
    console.log('target:', el, 'tag:', el.tagName, 'classes:', el.className);

    var xpath = findLocatorXpath(el);
    if (!xpath) {
        xpath = getClickableXPath(el); // Fallback
    }

    console.log('LOCATOR_PICK xpath:', xpath);
    window.locatorPickResult = xpath || '';

    stopLocatorPickMode();
}

function locatorKeydownHandler(e) {
    if (e.key === 'Escape' || e.key === 'Esc' || e.keyCode === 27) {
        console.log('LOCATOR_PICK cancelled by Esc');
        window.locatorPickResult = null;
        stopLocatorPickMode();
    }
}

document.addEventListener('mousemove', locatorMouseMoveHandler, true);
document.addEventListener('click', locatorClickHandler, true);
document.addEventListener('keydown', locatorKeydownHandler, true);
