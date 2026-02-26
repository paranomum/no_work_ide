function detectDropdown(element) {
  if (!element || !element.closest) return null;

  // ищем ближайшую кнопку-триггер Ant Design
  var root = element.closest('button.ant-dropdown-trigger');
  if (!root) return null;

  if (!root.className || !root.className.includes('ant-dropdown-trigger')) return null;

  // текст может быть пустым (иконка)
  var text = root.textContent ? root.textContent.trim() : '';
  text = sanitizeText(text || '');
  var safeText = text.replace(/'/g, "\\'");

  // XPath без завязки на data-testid
  var xpath;
  if (text) {
    xpath = "//button[contains(@class,'ant-dropdown-trigger') and contains(., '" + safeText + "')]";
  } else {
    xpath = "//button[contains(@class,'ant-dropdown-trigger')]";
  }

  return {
    isClickable: true,
    buttonInfo: {
      selectXpath: xpath,
      name: text || 'dropdown',
      type: 'dropdown',
      domElement: root
    },
    eventType: 'dropdown-open'
  };
}

function detectFormSelect(element) {
    if (!element.closest) return null;

    var selectRoot = element.closest("[data-testid='form-select']");
    if (!selectRoot) return null;

    var selectInfo = getSelectInfoFromRoot(selectRoot);
    if (!selectInfo) return null;

    return {
        isClickable: true,
        buttonInfo: {
            xpath: selectInfo.xpath,
            name: selectInfo.name,
            type: "select",
            domElement: selectRoot
        },
        eventType: "select-open"
    };
}

function detectSelectOption(element) {
    if (!element || !element.className) return null;

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    if (tagName !== 'DIV' || !element.className.includes('ant-select-item-option')) {
        return null;
    }

    var title = element.getAttribute('title') || (element.textContent || '').trim();
    if (!title) return null;

    title = sanitizeText(title);

    var dropdown = element.closest("div.ant-select-dropdown");
    if (!dropdown) return null;

    var listbox = dropdown.querySelector("div[role='listbox'][id]");
    if (!listbox) return null;

    var listId = listbox.getAttribute("id");
    if (!listId) return null;

    var input = document.querySelector(
        "[data-testid='form-select'] input[aria-controls='" + listId + "']"
    );
    if (!input) return null;

    var selectRoot = input.closest("[data-testid='form-select']");
    if (!selectRoot) return null;

    var selectInfo = getSelectInfoFromRoot(selectRoot);
    if (!selectInfo) return null;

    var safeTitle = title.replace(/'/g, "\\'");

    var xpath =
        "//div[./div[@role='listbox' and @id='" + listId + "']]//" +
        "div[contains(@class,'ant-select-item-option') and @title='" + safeTitle + "']";

    return {
        buttonInfo: {
            xpath: xpath,
            name: title,
            type: "select-option",
            selectXpath: selectInfo.xpath,
            selectName: selectInfo.name,
            domElement: element
        },
        eventType: "select-option"
    };
}

function detectDropdownOption(element) {
    if (!element || !element.className) return null;

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    if (tagName !== 'LI') return null;

    var cls = element.className;
    if (!cls.includes("ant-dropdown-menu-item") && !cls.includes("ant-menu-item")) {
        return null;
    }

    // текст опции — как в Java: span.ant-dropdown-menu-title-content / ant-menu-title-content
    var span = element.querySelector(
        "span.ant-dropdown-menu-title-content, span.ant-menu-title-content"
    ) || element.querySelector("span");

    var text = span ? (span.textContent || "").trim() : (element.textContent || "").trim();
    if (!text) return null;
    text = sanitizeText(text);

    var safeText = text.replace(/'/g, "\\'");

    // ul с нужным классом
    var ul = element.closest("ul");
    if (!ul || !ul.className) return null;
    if (!(
        ul.className.includes("ant-dropdown-menu") ||
        ul.className.includes("ant-menu")
    )) {
        return null;
    }

    // XPath в духе xpathLi + "/li[contains(., 'option')]"
    var xpath =
        "//ul[(contains(@class, 'ant-dropdown-menu') or contains(@class, 'ant-menu')) and " +
        ".//li[contains(@class, 'ant-dropdown-menu-item') or contains(@class, 'ant-menu-item')]]" +
        "/li[contains(., '" + safeText + "')]";

    return {
        buttonInfo: {
            selectXpath: xpath,
            name: text,
            type: "dropdown-option",
            domElement: element
        },
        eventType: "dropdown-option"
    };
}

function getSelectInfoFromRoot(selectRoot) {
    if (!selectRoot) return null;

    var name = "";

    // 1. Если есть label[title] — используем его (как во второй части условия)
    var label = selectRoot.querySelector("label[title]");
    if (label) {
        name = (label.getAttribute("title") || "").trim();
    }

    // 2. Иначе берём любой видимый текст внутри form-select (placeholder / выбранное значение),
    //    чтобы сработал contains(., 'name')
    if (!name) {
        // прицелимся в placeholder ant-select
        var placeholderEl = selectRoot.querySelector(".ant-select-placeholder");
        if (placeholderEl) {
            name = (placeholderEl.textContent || "").trim();
        }

        // если даже placeholder не нашли — можно попробовать общее textContent
        if (!name) {
            name = (selectRoot.textContent || "").trim();
        }
    }

    console.log("[SELECT-INFO] label:", label, "name:", name);

    if (!name) return null;

    var safeName = name.replace(/'/g, "\\'");

    // XPath ТОЧНО как в Select.java
    var xpath =
        "//*[@data-testid='form-select' and " +
        "(contains(., '" + safeName + "') or .//label[contains(@title, '" + safeName + "')])]";

    return {
        name: name,
        safeName: safeName,
        xpath: xpath
    };
}