"use strict";

// 1. prep
/*
 header-cell=css=.ui-grid-header-cell-row .ui-grid-cell-contents
 data-row=css=.ui-grid-row
 data-cell=css=.ui-grid-cell-contents
 data-viewport=css=.ui-grid-viewport
 data-container=css=.ui-grid-canvas
 limit=-1
 nexial.web.saveGrid.deepScan=true
 */
// assume arguments[0] is the grid row container
// assume arguments[1] is limit

// 2. loop through grid row:
//    - loop through its cell
//      - check against limit
//      - for each cell, collect meta
//        - if cell has visible text, use it
//        - if cell has no visible text, search for input/img element
//      - hide row

if (!arguments || arguments.length < 1) { return ''; }

var container = arguments[0];
if (!container) { return ''; }
var rowLocator  = arguments[1] || './/*[contains(@class,"ui-grid-row")]';
var cellLocator = arguments[2] || './/*[contains(@class,"ui-grid-cell-contents")]';
var inspectMeta = arguments[3] || true;
var limit       = arguments[4] || -1;
var recSep      = arguments[5] || '#$#';

// testing
var container   = $$('.ui-grid-viewport')[0];
var rowLocator  = './/*[contains(@class,"ui-grid-row")]';
var cellLocator = './/*[contains(@class,"ui-grid-cell-contents")]';
var inspectMeta = true;

var hash = function (s) {
  if (s) {
    var a = 0;
    for (var h = s.length - 1; h >= 0; h--) {
      var o = s.charCodeAt(h);
      a     = (a << 6 & 268435455) + o + (o << 14);
      var c = a & 266338304;
      a     = c !== 0 ? a ^ c >> 21 : a;
    }
    return a;
  } else {
    return 1;
  }
};

function getSelectedOptions(elem) {
  if (!elem.selectedOptions || elem.selectedOptions.length < 1) { return ''; }
  var text = '';
  for (var i = 0; i < elem.selectedOptions.length; i++) { text += elem.selectedOptions[i].text + '\n'; }
  return text;
}

// function collectRows(container, gridData, rowHashes, scannedRowHeights) {
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

    // maybe we've seen this before?
    var rowHash = hash(row.innerText);

    // we've done this one; skip it
    if (result.rowHashes.includes(rowHash)) {
      console.log("\t\tfound duplicate at " + row.offsetTop);
      result.scannedRowHeights += row.offsetHeight / 2;
      row = rows.iterateNext();
      continue;
    }

    result.scannedRowHeights += row.offsetHeight;
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
          cellData.tag      = cell.tagName.toLowerCase();
          cellData.type     = cell.getAttribute('type') || '';
          cellData.id       = cell.getAttribute('id') || '';
          cellData.name     = cell.getAttribute('name') || '';
          cellData.value    = cell.getAttribute('value') || '';
          cellData.alt      = cell.getAttribute('alt') || '';
          cellData.src      = cell.getAttribute('src') || '';
          cellData.checked  = cell.hasAttribute('checked');
          cellData.selected = getSelectedOptions(cell);
        }

        rowData.push(cellData);
        cell = cells.iterateNext();
      }

      result.data.push(rowData);
    }

    row = rows.iterateNext();
  }

  return result;
}

var collectionResults = {
  data:              [],
  rowHashes:         [],
  scannedRowHeights: 0,
  collected:         0
};

var totalCollected = 0;

function resumeCollection() {
  if (collectionResults.collected === 0) {
    console.log('stopping collection; so far, ' + totalCollected + ' rows were collected');
    clearInterval(collector);
  }

  totalCollected += collectionResults.collected;
  console.log("restart collection");

  container.scroll({top: collectionResults.scannedRowHeights, left: 0, behavior: 'smooth'});
  window.setTimeout(function () { collectRows(container, collectionResults); }, 500);
}

collectRows(container, collectionResults);
var collector = window.setInterval(resumeCollection, 1500);

while (collectionResults.collected !== 0) {
  window.setTimeout(function () {
    totalCollected += collectionResults.collected;
    container.scroll({top: collectionResults.scannedRowHeights, left: 0, behavior: 'smooth'});
    window.setTimeout(function () {
      collectRows(container, collectionResults);
    }, 500);
  }, 2000);

}

// var collected      = collectRows(container, gridData, rowHashes, scannedRowHeights);
// while (collected !== 0) {
//   totalCollected += collected;
//   container.scroll({top: scannedRowHeights, left: 0, behavior: 'smooth'});
//   collected = collectRows(container, gridData, rowHashes, scannedRowHeights);
// }

// 3. loop through grid row
//    - show row
// scannedRows.forEach(function (row) { row.style.display = ''; });

// 4. return / done
return gridData;
