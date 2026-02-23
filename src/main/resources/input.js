function getFieldInfoFromInput(element) {
    if (!element) return null;

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    if (tagName !== 'INPUT' && tagName !== 'TEXTAREA') return null;

    // 1. Пытаемся найти React form-input
    var current = element;
    var formInput = null;
    while (current && current !== document.body) {
        if (current.getAttribute && current.getAttribute('data-testid') === 'form-input') {
            formInput = current;
            break;
        }
        current = current.parentElement;
    }

    var name = null;
    var safeName;
    var angularXpath;
    var reactXpath;
    var xpath;

    if (formInput) {
        // ===== React-ветка =====
        var label =
            formInput.querySelector('label[title]') ||
            formInput.querySelector('label');

        if (label) {
            var labelText = (label.textContent || '').trim();
            var titleText = (label.getAttribute('title') || '').trim();
            name = labelText || titleText || null;
        }

        if (!name) {
            // если вдруг label кривой — дальше смысла продолжать нет,
            // пусть вернётся null, чем странное имя
            return null;
        }

        safeName = name.replace(/'/g, "\\'");

        reactXpath =
            "//*[@data-testid='form-input' and " +
            ".//label[contains(.,'" + safeName + "') or contains(@title, '" + safeName + "')]]//input | " +
            "//*[@data-testid='form-input' and " +
            ".//label[contains(.,'" + safeName + "') or contains(@title, '" + safeName + "')]]//textarea";

        xpath = reactXpath;
    } else {
        // ===== Angular-ветка (form-input не нашли вообще) =====
        var placeholder =
            element.getAttribute('data-placeholder') ||
            element.getAttribute('placeholder');

        if (!placeholder || placeholder.trim() === '') {
            return null;
        }

        name = placeholder.trim();
        safeName = name.replace(/'/g, "\\'");

        angularXpath =
            "//input[contains(@data-placeholder,'" + safeName + "') " +
            "or contains(@placeholder, '" + safeName + "')] | " +
            "//textarea[contains(@data-placeholder, '" + safeName + "') " +
            "or contains(@placeholder, '" + safeName + "')]";

        // для чистого Angular reactXpath можно не добавлять вообще
        xpath = angularXpath;
    }

    return {
        xpath: xpath,
        name: safeName,
        type: 'field',
        javaData: {
            init_string: "new Field(" + safeName + ")"
        }
    };
}
