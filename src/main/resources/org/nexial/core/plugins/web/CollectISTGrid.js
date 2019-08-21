// "use strict";

if (!arguments || arguments.length < 1) { return ''; }

var container = arguments[0];
if (!container) { return ''; }

var rowLocator = arguments[1];
if (!rowLocator) { return ''; }

var cellLocator = arguments[2];
if (!cellLocator) { return ''; }

var inspectMeta = arguments[3] || true;
console.log(inspectMeta);
if (inspectMeta === 'true') { inspectMeta = true; }

var limit            = arguments[4] || -1;
var cellInputLocator = ".//*[name()='input' or name()='submit' or name()='button' or name()='textarea' or name()='select' or name()='img']";

// var rowLocator       = arguments[1] || './/*[contains(@class,"ui-grid-row")]';
// var cellLocator      = arguments[2] || './/*[contains(@class,"ui-grid-cell-contents")]';

// testing
// var container   = $$('.ui-grid-viewport')[0];
// var rowLocator  = './/*[contains(@class,"ui-grid-row")]';
// var cellLocator = './/*[contains(@class,"ui-grid-cell-contents")]';
// var inspectMeta = true;
// var limit = -1;

collectionResults    = {data: [], rowHashes: [], scannedRowHeights: 0, collected: 0};
totalCollected       = 0;
collectionInProgress = true;

var hash = function (s) {
  if (!s) { return 1; }

  var a = 0;
  for (var h = s.length - 1; h >= 0; h--) {
    var o = s.charCodeAt(h);
    a     = (a << 6 & 268435455) + o + (o << 14);
    var c = a & 266338304;
    a     = c !== 0 ? a ^ c >> 21 : a;
  }
  return a;
};

function getSelectedOptions(elem) {
  if (!elem.selectedOptions || elem.selectedOptions.length < 1) { return ''; }
  var text = '';
  for (var i = 0; i < elem.selectedOptions.length; i++) { text += elem.selectedOptions[i].text + '\n'; }
  return text;
}

function collectRows(container, result) {
  result.collected = 0;

  var rows = document.evaluate(rowLocator, container, null, XPathResult.ANY_TYPE, null);
  if (rows === null) { return result; }

  var row = rows.iterateNext();
  while (row !== null) {
    if (row.offsetParent === null) {
      row = rows.iterateNext();
      continue;
    }

    result.scannedRowHeights += row.offsetHeight - 15;

    // maybe we've seen this before?
    var rowHash = hash(row.innerText);

    // we've done this one; skip it
    if (result.rowHashes.includes(rowHash)) {
      console.log("\t\tfound duplicate at " + row.offsetTop);
      row = rows.iterateNext();
      continue;
    }

    result.rowHashes.push(rowHash);
    result.collected++;
    console.log('scanning row ' + (totalCollected + result.collected) + ': ' + row);

    var rowData = [];
    var cells   = document.evaluate(cellLocator, row, null, XPathResult.ANY_TYPE, null);
    if (cells !== null) {
      var cell = cells.iterateNext();
      while (cell !== null) {
        // console.log('\tscanning cell ' + cell);
        var cellData = {text: cell.textContent || ''};
        if (cellData.text === '' && inspectMeta) {
          var childElements = document.evaluate(cellInputLocator, cell, null, XPathResult.ANY_TYPE, null);
          if (childElements) {
            var child         = childElements.iterateNext();
            cellData.tag      = child.tagName.toLowerCase();
            cellData.type     = child.getAttribute('type') || '';
            cellData.id       = child.getAttribute('id') || '';
            cellData.name     = child.getAttribute('name') || '';
            cellData.value    = child.getAttribute('value') || '';
            cellData.alt      = child.getAttribute('alt') || '';
            cellData.src      = child.getAttribute('src') || '';
            cellData.checked  = child.hasAttribute('checked');
            cellData.selected = getSelectedOptions(child);
          }
        }

        rowData.push(cellData);
        cell = cells.iterateNext();
      }

      result.data.push(rowData);
    }

    if (limit !== -1 && (totalCollected + result.collected) >= limit) { return result; }
    row = rows.iterateNext();
  }

  return result;
}

function resumeCollection() {
  collectRows(container, collectionResults);

  if (collectionResults.collected === 0) {
    console.log('stopping collection; so far, ' + totalCollected + ' rows were collected');
    collectionInProgress        = false;
    collectionResults.rowHashes = null;
    clearInterval(collector);
    return;
  }

  totalCollected += collectionResults.collected;
  if (limit !== -1 && totalCollected >= limit) {
    console.log('stopping collection due to limit reached ' + totalCollected);
    collectionInProgress        = false;
    collectionResults.rowHashes = null;
    clearInterval(collector);
    return;
  }

  console.log("restart collection");
  container.scroll({top: collectionResults.scannedRowHeights, left: 0, behavior: 'smooth'});
}

var collector = window.setInterval(resumeCollection, 600);
