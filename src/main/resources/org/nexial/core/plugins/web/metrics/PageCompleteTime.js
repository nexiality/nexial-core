metrics.PageCompleteTime = formatPerfNum(JSON.parse(localStorage.getItem('n')).map(x => x.domComplete - x.startTime).pop());