// === Глобальные структуры ===
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

// === ИНИЦИАЛИЗАЦИЯ СЛУШАТЕЛЕЙ (ОДИН РАЗ) ===

if (!window.__iqhrActionsInitialized) {
  window.__iqhrActionsInitialized = true;

  // ----- CLICK -----
  if (window.__iqhrClickHandler) {
    document.removeEventListener('click', window.__iqhrClickHandler, true);
  }

  window.__iqhrClickHandler = function (e) {
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

    // 3. Режем дубль для input внутри checkbox/radio label
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
      console.log(window.recordedClicks);

      window.recordedClicks.push(clickRecord);

      if (isNewTab && element.href) {
        e.preventDefault();
        var href = element.href;
        setTimeout(function () {
          window.open(href, '_blank');
        }, 500);
        return;
      }
    }
  };

  document.addEventListener('click', window.__iqhrClickHandler, true);

  // ----- FOCUS -----
  if (window.__iqhrFocusHandler) {
    document.removeEventListener('focus', window.__iqhrFocusHandler, true);
  }

  window.__iqhrFocusHandler = function (e) {
    var element = e.target;
    if (isEditableInput(element)) {
      var fieldInfo = getFieldInfoFromInput(element); // из input.js
      window.currentFocusedXPath = fieldInfo ? fieldInfo.xpath : getXPath(element);
      window.currentFocusedElement = element;
      window.currentFocusedValue = element.value || '';
    }
  };

  document.addEventListener('focus', window.__iqhrFocusHandler, true);

  // ----- BLUR -----
  if (window.__iqhrBlurHandler) {
    document.removeEventListener('blur', window.__iqhrBlurHandler, true);
  }

  window.__iqhrBlurHandler = function (e) {
    if (isEditableInput(e.target) && window.currentFocusedXPath) {
      var currentValue = e.target.value || '';

      if (window.currentFocusedValue !== currentValue) {
        var picker = getFieldInfoFromDatePicker(e.target); // из date_picker-2.js
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
        window.currentFocusedXPath = null;
        window.currentFocusedElement = null;
        window.currentFocusedValue = '';
      }
    }
  };

  document.addEventListener('blur', window.__iqhrBlurHandler, true);

  // ----- VISIBILITYCHANGE -----
  if (window.__iqhrVisibilityHandler) {
    document.removeEventListener('visibilitychange', window.__iqhrVisibilityHandler);
  }

  window.__iqhrVisibilityHandler = function () {
    var state = document.visibilityState === 'visible' ? 'active' : 'inactive';
    window.currentTabState = state;

    setTimeout(function () {
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
  };

  document.addEventListener('visibilitychange', window.__iqhrVisibilityHandler);
}