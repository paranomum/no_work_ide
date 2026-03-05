function getFieldInfoFromInput(element) {
    if (!element) return null;

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    var init_by_xpath = false;
    if (tagName !== 'INPUT' && tagName !== 'TEXTAREA') return null;
    var index = 0;
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
    var xpath = "";

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

    if (xpath !== "") {
        index = getIndexByXPathAndElement(xpath, element);
    }

    return {
        xpath: xpath,
        name: safeName,
        type: 'Field',
        javaData: "",
        indexIndex: (index + 1),
        init_by_xpath: init_by_xpath
    };
}

function getFieldInfoFromRichField(element) {
    if (!element) return null;

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    // richTextEditor обычно DIV с role="textbox"
    if (tagName !== 'DIV') return null;

    var init_by_xpath = false;
    var index = 0;

    // 1. Ищем контейнер React richTextEditor
    var current = element;
    var richContainer = null;
    while (current && current !== document.body) {
        if (current.getAttribute && current.getAttribute('data-testid') === 'form-richTextEditor') {
            richContainer = current;
            break;
        }
        current = current.parentElement;
    }

    if (!richContainer) {
        return null;
    }

    var name = null;
    var safeName;
    var xpath = "";

    // 2. Ищем label внутри form-richTextEditor
    var label =
        richContainer.querySelector('label[title]') ||
        richContainer.querySelector('label');

    if (label) {
        var labelText = (label.textContent || '').trim();
        var titleText = (label.getAttribute('title') || '').trim();
        name = labelText || titleText || null;
    }

    if (!name) {
        // без нормального имени лучше не строить xpath
        return null;
    }

    safeName = name.replace(/'/g, "\\'");

    // 3. Строим xpath для DIV[@role='textbox'] внутри form-richTextEditor
    xpath =
        "//*[@data-testid='form-richTextEditor' and " +
        ".//label[contains(.,'" + safeName + "') or contains(@title, '" + safeName + "')]]" +
        "//div[@role='textbox']";

    if (xpath !== "") {
        index = getIndexByXPathAndElement(xpath, element);
    }

    return {
        xpath: xpath,
        name: safeName,
        type: 'RichField',
        javaData: "",
        indexIndex: (index + 1),
        init_by_xpath: init_by_xpath
    };
}


function getFieldInfoFromDatePicker(element) {
    if (!element) return null;

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    if (tagName !== 'INPUT') return null;
    var index = 0;

    // ===== React: form-picker =====
    var reactRoot = element.closest && element.closest("[data-testid='form-picker']");
    if (reactRoot) {
        var label =
            reactRoot.querySelector("label[title]") ||
            reactRoot.querySelector("label");
        var name = "";
        if (label) {
            var labelText = (label.textContent || "").trim();
            var titleText = (label.getAttribute("title") || "").trim();
            name = labelText || titleText || "";
        }
        name = sanitizeText(name || "");
        if (!name) {
            return null;
        }
        var safeName = name.replace(/'/g, "\\'");

        var xpath =
            "//*[@data-testid='form-picker' " +
            "and .//label[contains(@title, '" + safeName + "') " +
            "or contains(., '" + safeName + "')]]";

        return {
            xpath: xpath,
            name: name,
            type: 'DatePicker',
            javaData: "new DatePicker(\"" + safeName + "\")",
            index: index
        };
    }

    // ===== Angular: mat-form-field с input[data-mat-calendar] =====
    var matField = element.closest && element.closest("mat-form-field");
    if (matField) {
        var inputWithCalendar =
            matField.querySelector("input[data-mat-calendar]") ||
            matField.querySelector("input[data-mat-calendar='']");
        if (!inputWithCalendar || inputWithCalendar !== element) {
            return null;
        }

        var placeholder =
            inputWithCalendar.getAttribute("data-placeholder") ||
            inputWithCalendar.getAttribute("placeholder") || "";
        var name = sanitizeText((placeholder || "").trim());
        if (!name) {
            return null;
        }
        var safeName = name.replace(/'/g, "\\'");

        var xpath =
            "//mat-form-field[.//input[@data-mat-calendar and " +
            "(contains(@data-placeholder, '" + safeName + "') " +
            "or contains(@placeholder, '" + safeName + "'))]]";

        return {
            xpath: xpath,
            name: name,
            type: 'DatePicker',
            javaData: "new DatePicker(\"" + safeName + "\")",
            index: index
        };
    }

    return null;
}
