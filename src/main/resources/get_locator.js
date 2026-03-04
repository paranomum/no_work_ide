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

// Гарантируем наличие стиля для подсветки
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
        // 1) пробуем кликабельный элемент (кнопки, ссылки, табы, селекты, чекбоксы...)
        var clickable = isClickableElement(current);
        if (clickable && clickable.isClickable && clickable.buttonInfo && clickable.buttonInfo.xpath) {
            if (clickable.index >= 1) {
                return "(" + clickable.buttonInfo.xpath + ")[" + (clickable.index + 1) + "]";
            }
            return clickable.buttonInfo.xpath;
        }

        // 2) пробуем поле ввода (input, textarea, form-select)
        var field = getFieldInfoFromInput(current);
        if (field && field.xpath) {
            if (field.index >= 1) {
                        return "(" + field.xpath + ")[" + (clickable.index + 1) + "]";
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
        xpath = getClickableXPath(el); // Fallback для любых элементов
    }

    console.log('LOCATOR_PICK xpath:', xpath);
    window.locatorPickResult = xpath || '';

    clearHighlight();
    document.removeEventListener('mousemove', locatorMouseMoveHandler, true);
    document.removeEventListener('click', locatorClickHandler, true);
}

// режим выбора: подсветка при движении мыши, фиксация по клику
document.addEventListener('mousemove', locatorMouseMoveHandler, true);
document.addEventListener('click', locatorClickHandler, true);
