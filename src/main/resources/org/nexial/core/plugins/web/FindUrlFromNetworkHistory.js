let perf = window.performance || window.mozPerformance || window.msPerformance || window.webkitPerformance || {};
if (!perf) { return []; }

let network = perf.getEntries() || {};
if (!network) { return []; }

let matchBy = !arguments || arguments.length < 1 ? '' : arguments[0];
let matchAsRegex = !arguments || arguments.length < 2 ? false : arguments[1] === "true";

return network.filter(function (n) {
  return !n.name.match(/^(http|mailto|file)/) ? false : matchAsRegex ? n.name.match(matchBy) : n.name.contains(matchBy);
}).map(function (n) { return n.name })
