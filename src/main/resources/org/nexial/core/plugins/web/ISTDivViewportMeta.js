if (!arguments || arguments.length < 1) { return ''; }

var elem = arguments[0];
if (!elem) { return ''; }

var recSep = arguments[1] || '#$#';

return 'top=' + elem.offsetTop + recSep +
       'height=' + elem.offsetHeight;
