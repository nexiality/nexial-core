// g = resource???
// n = navigation
// r = resource
// p = paint
var firstUse = !localStorage.hasOwnProperty('initialized');
if (firstUse) {
  localStorage.setItem('g', '[]');
  localStorage.setItem('n', '[ { "requestStart":0} ]');
  localStorage.setItem('r', '[]');
  localStorage.setItem('p', '[]');
  localStorage.setItem('initialized', "true");
}

var isNavigation = JSON.parse(localStorage.getItem('n'))[0].requestStart ===
                   performance.getEntriesByType('navigation')[0].requestStart;
if (isNavigation) {
  localStorage.setItem('g', JSON.stringify(performance.getEntries()));
  localStorage.setItem('n', JSON.stringify(performance.getEntriesByType('navigation')));
  localStorage.setItem('r', JSON.stringify(performance.getEntriesByType('resource')));
  localStorage.setItem('p', JSON.stringify(performance.getEntriesByType('paint')));
} else {
  var g = JSON.parse(localStorage.getItem('g'));
  g.push(performance.getEntries());
  localStorage.setItem('g', JSON.stringify(g.flat(), function(key, value) {
    return key.indexOf('jQuery') === 0 ? null : value;
  }));

  var n = JSON.parse(localStorage.getItem('n'));
  n.push(performance.getEntriesByType('navigation'));
  localStorage.setItem('n', JSON.stringify(n.flat()));

  var r = JSON.parse(localStorage.getItem('r'));
  r.push(performance.getEntriesByType('resource'));
  localStorage.setItem('r', JSON.stringify(r.flat()));

  var p = JSON.parse(localStorage.getItem('p'));
  p.push(performance.getEntriesByType('paint'));
  localStorage.setItem('p', JSON.stringify(p.flat()));
}

function formatPerfNum(/*Number*/num) {
  if (!num || isNaN(num)) { return num; }

  if (num < 0) { num = num * -1; }
  var num1 = num.toFixed(2);
  return num1 ? parseFloat(num1) : num;
}

var metrics = {};
