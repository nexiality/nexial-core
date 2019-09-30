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
  localStorage.setItem('g', JSON.stringify(g.flat()));

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

var metrics = {};

// var a                 = window.performance.timing;
// var pageCompleteTime  = a.domComplete - a.navigationStart;
// var timeToInteractive = a.domInteractive - a.navigationStart;
// var ttlb              = a.responseEnd - a.navigationStart;
// var ttfb              = a.responseStart - a.navigationStart;
// var latency           = a.responseStart - a.fetchStart;
// var dns               = a.domainLookupEnd - a.domainLookupStart;
// var tcp               = a.connectEnd - a.connectStart;
// var firstResponseTime = a.responseStart - a.requestStart;
// var downloadTime      = a.responseEnd - a.responseStart;
// var firstInteractive  = a.domInteractive - a.domLoading;
// var pageReady         = a.domComplete - a.domInteractive;
// var domContentLoaded  = a.domComplete - a.domLoading;
// var onloadTime        = a.loadEventEnd - a.loadEventStart;
//
// var textContent =
//       '(loaded) page-complete-time : ' + pageCompleteTime + 'ms\n' +
//       'time-to-interactive: ' + timeToInteractive + 'ms\n' +
//       'time-to-last-byte  : ' + ttlb + 'ms\n' +
//       'time-to-first-byte : ' + ttfb + 'ms\n' +
//       'latency            : ' + latency + 'ms\n' +
//       'network-overhead   : ' + (dns + tcp) + 'ms\n' +
//       'first-response-time: ' + firstResponseTime + 'ms\n' +
//       'download-time      : ' + downloadTime + 'ms\n' +
//       'first-interactive  : ' + firstInteractive + 'ms\n' +
//       'page-ready         : ' + pageReady + 'ms\n' +
//       'dom-content-loaded : ' + domContentLoaded + 'ms\n' +
//       'on-load            : ' + onloadTime + 'ms\n';
// console.log(textContent);