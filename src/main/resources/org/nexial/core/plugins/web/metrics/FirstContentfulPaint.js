metrics.FirstContentfulPaint = formatPerfNum(JSON.parse(localStorage.getItem('p'))
                                                 .filter(x => x.name === 'first-contentful-paint')
                                                 .map(y => y.startTime)[0]);
