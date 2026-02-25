window.locatorPickResult = null;

function findLocatorXpath(element) {
    var current = element;
    var depth = 0;

    while (current && depth < 20) {
        // 1) пробуем кликабельный элемент (кнопки, ссылки, табы, селекты, чекбоксы...)
        var clickable = isClickableElement(current);
        if (clickable && clickable.isClickable && clickable.buttonInfo && clickable.buttonInfo.xpath) {
            return clickable.buttonInfo.xpath;
        }

        // 2) пробуем поле ввода (input, textarea, form-select)
        var field = getFieldInfoFromInput(current);
        if (field && field.xpath) {
            return field.xpath;
        }

        if (current.tagName && current.tagName.toUpperCase() === 'BODY') break;
        current = current.parentElement;
        depth++;
    }

    return null;
}

document.addEventListener('click', function(e) {
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();

    var element = e.target;
    var xpath = findLocatorXpath(element);

    if (!xpath) {
        xpath = getXPath(element);
    }

    console.log('LOCATOR_PICK xpath:', xpath);
    window.locatorPickResult = xpath || '';
}, true);