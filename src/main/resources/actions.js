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

window.pageUrlPath = (function() {
  try {
    var url = new URL(window.location.href);
    var path = url.pathname || '/';
    // если хочешь включать query:
    // return path + (url.search || '');
    return path;
  } catch (e) {
    // fallback без URL
    var href = window.location.href || '';
    var idx = href.indexOf('://');
    if (idx >= 0) {
      var slash = href.indexOf('/', idx + 3);
      return slash >= 0 ? href.substring(slash) : '/';
    }
    return href || '/';
  }
})();

// === ИНИЦИАЛИЗАЦИЯ СЛУШАТЕЛЕЙ (ОДИН РАЗ) ===

if (!window.__iqhrActionsInitialized) {
  window.__iqhrActionsInitialized = true;

  // ----- CLICK -----
  if (window.__iqhrClickHandler) {
    document.removeEventListener('click', window.__iqhrClickHandler, true);
  }

window.__lastClickXPath = window.__lastClickXPath || null;
window.__lastClickTime  = window.__lastClickTime  || 0;

  window.__iqhrClickHandler = function (e) {
    var rawTarget = e.target;
    var element = rawTarget;

    // --- НОРМАЛИЗАЦИЯ ---

    // 1) ant-radio: если кликнули по input внутри label.ant-radio-wrapper,
    //    считаем, что клик по label
    var radioLabel = element.closest &&
                     element.closest('label.ant-radio-wrapper');
    if (radioLabel) {
      element = radioLabel;
    }

    // 2) ant-checkbox: если кликнули по input/span внутри label.ant-checkbox-wrapper,
    //    считаем, что клик по label
    var checkboxLabel = element.closest &&
                        element.closest('label.ant-checkbox-wrapper');
    if (checkboxLabel) {
      element = checkboxLabel;
    }

    // 3) ant-segmented: поднимаем до контейнера radiogroup
    var segmentedRoot = element.closest &&
                        element.closest('div.ant-segmented[role="radiogroup"]');
    if (segmentedRoot) {
      element = segmentedRoot;
    }

    console.log('[ACTIONS] RAW CLICK target:', rawTarget.tagName, rawTarget.className);
    console.log('[ACTIONS] NORMALIZED target:', element.tagName, element.className);

    var tagName = element.tagName ? element.tagName.toUpperCase() : '';
    var clickable = findClickable(element);
    console.log('[ACTIONS] findClickable result:', clickable);

    if (clickable.isClickable) {
      var info = clickable.buttonInfo || {};
      console.log("CLICKED ON ", info);
      var isNewTab = isOpenNewTab(info);

      var xpath = info.xpath || getXPath(element);
          var now = Date.now();

          if (window.__lastClickXPath === xpath && now - window.__lastClickTime < 200) {
            console.log('[ACTIONS] skip duplicate click for', xpath);
            return;
          }
          window.__lastClickXPath = xpath;
          window.__lastClickTime  = now;

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
        xpath: xpath,
        id: element.id || '',
        tag: tagName,
        text: info.name || '',
        eventType: clickable.eventType || 'click',
        elementType: clickable.type,
        index: clickable.index || 0,
        initByXpath: clickable.init_by_xpath,
        selectXpath: info.selectXpath || null,
        selectName: info.selectName || null,
        newTab: isNewTab,
        pageUrlPath: window.pageUrlPath || null
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
    var fieldInfo = getFieldInfoSmart(element);

    window.currentFocusedXPath = fieldInfo ? fieldInfo.xpath : getXPath(element);
    window.currentFocusedElement = element;
    window.currentFocusedValue = getElementValueLikeInput(element);
  }
};

  document.addEventListener('focus', window.__iqhrFocusHandler, true);

  // ----- BLUR -----
  if (window.__iqhrBlurHandler) {
    document.removeEventListener('blur', window.__iqhrBlurHandler, true);
  }

  window.__iqhrBlurHandler = function (e) {
    if (isEditableInput(e.target) && window.currentFocusedXPath) {
      var element = e.target;
      var currentValue = getElementValueLikeInput(element);

      if (window.currentFocusedValue !== currentValue) {
        var picker = getFieldInfoFromDatePicker(element);
        if (picker === null) {
          var fieldInfo = getFieldInfoSmart(element);
          var clickRecord = {
            xpath: fieldInfo ? fieldInfo.xpath : window.currentFocusedXPath,
            value: currentValue,
            id: element.id || '',
            type: fieldInfo ? fieldInfo.type : 'Field',   // тут уже придёт 'RichField'
            name: fieldInfo ? fieldInfo.name : '',
            timestamp: Date.now(),
            javaData: '',
            index: fieldInfo ? fieldInfo.indexIndex : 0,
            initByXpath: fieldInfo ? fieldInfo.init_by_xpath : false,
            pageUrlPath: window.pageUrlPath || null
          };
          console.log(clickRecord);
          window.recordedInputs.push(clickRecord);
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