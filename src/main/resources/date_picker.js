function detectDatePickerOpen(element) {
    if (!element || !element.closest) return null;

    var reactRoot = element.closest("[data-testid='form-picker']");
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
            "//*[@data-testid='form-picker' and " +
            "(.//label[contains(@title, '" + safeName + "') or contains(., '" + safeName + "')])]";

        return {
            isClickable: true,
            buttonInfo: {
                xpath: xpath,
                name: name,
                type: "datepicker",
                domElement: reactRoot
            },
            eventType: "datepicker-open",
            javaData: "new DatePicker(\"" + safeName + "\")",
            uiType: "DatePicker"
        };
    }

    // 2) Angular: mat-form-field + кнопка открытия календаря
    var matField = element.closest("mat-form-field");
    if (matField) {
        var inputWithCalendar =
            matField.querySelector("input[data-mat-calendar]") ||
            matField.querySelector("input[data-mat-calendar='']");
        if (!inputWithCalendar) {
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
            "(contains(@data-placeholder, '" + safeName + "') or contains(@placeholder, '" + safeName + "'))]]";

        return {
            isClickable: true,
            buttonInfo: {
                xpath: xpath,
                name: name,
                type: "datepicker",
                domElement: matField
            },
            eventType: "datepicker-open",
            javaData: "new DatePicker(\"" + safeName + "\")",
            uiType: "DatePicker"
        };
    }

    return null;
}

function detectDatePickerDayReact(element) {
    if (!element) return null;

    var td = element;
    if (td.tagName && td.tagName.toUpperCase() !== 'TD') {
        td = element.closest('td');
    }
    if (!td || !td.getAttribute) return null;

    var title = td.getAttribute('title'); // ожидаем yyyy-MM-dd
    if (!title) return null;

    // найдём ближайший открытый ant-picker-dropdown
    var dropdown = td.closest("div.ant-picker-dropdown");
    if (!dropdown || dropdown.className.includes("ant-picker-dropdown-hidden")) {
        return null;
    }

    var safeTitle = title.replace(/'/g, "\\'");
    // общий XPath дня, по мотивам твоего Java
    var dayXpath =
        "//div[contains(@class,'ant-picker-dropdown') and " +
        "not(contains(@class,'ant-picker-dropdown-hidden'))]" +
        "//td[@title='" + safeTitle + "']";

    return {
        buttonInfo: {
            xpath: dayXpath,
            name: title,          // yyyy-MM-dd
            type: "datepicker-day",
            domElement: td
        },
        eventType: "datepicker-date"
    };
}

function detectDatePickerDayAngular(element) {
    if (!element) return null;

    var btn = element;
    if (btn.tagName && btn.tagName.toUpperCase() !== 'BUTTON') {
        btn = element.closest('button');
    }
    if (!btn || !btn.getAttribute) return null;

    var aria = btn.getAttribute('aria-label') || '';
    if (!aria) return null;

    // проверим, что это внутри mat-datepicker-content
    var picker = btn.closest("mat-datepicker-content");
    if (!picker) return null;

    // будем использовать aria-label как name
    var name = sanitizeText(aria);
    if (!name) return null;

    var safeName = name.replace(/'/g, "\\'");
    var dayXpath =
        "//mat-datepicker-content//mat-calendar" +
        "//button[contains(@aria-label, '" + safeName + "')]";

    return {
        buttonInfo: {
            xpath: dayXpath,
            name: name,
            type: "datepicker-day",
            domElement: btn
        },
        eventType: "datepicker-date"
    };
}

function detectDatePickerDay(element) {
    // сначала React, потом Angular
    var res = detectDatePickerDayReact(element);
    if (res) return res;
    return detectDatePickerDayAngular(element);
}