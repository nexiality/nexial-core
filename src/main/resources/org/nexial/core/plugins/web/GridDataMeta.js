if (!arguments || arguments.length < 1) { return ''; }

var elem = arguments[0];
if (!elem) { return ''; }
if (elem.offsetParent === null) { return ''; }
if ((elem.offsetWidth || elem.offsetHeight || elem.getClientRects().length) === 0) { return ''; }
if (window.getComputedStyle(elem).visibility !== 'visible') { return ''; }

var recSep = arguments[1] || '#$#';

function getSelectedOptions(elem) {
  if (!elem.selectedOptions || elem.selectedOptions.length < 1) { return ''; }
  var text = '';
  for (var i = 0; i < elem.selectedOptions.length; i++) { text += elem.selectedOptions[i].text + '\n'; }
  return text;
}

return 'tag=' + elem.tagName.toLowerCase() + recSep +
       'type=' + (elem.getAttribute('type') || '') + recSep +
       'id=' + (elem.getAttribute('id') || '') + recSep +
       'name=' + (elem.getAttribute('name') || '') + recSep +
       'value=' + (elem.getAttribute('value') || '') + recSep +
       'alt=' + (elem.getAttribute('alt') || '') + recSep +
       'src=' + (elem.getAttribute('src') || '') + recSep +
       'checked=' + (elem.hasAttribute('checked') ? 'true' : 'false') + recSep +
       'selected=' + (getSelectedOptions(elem));
