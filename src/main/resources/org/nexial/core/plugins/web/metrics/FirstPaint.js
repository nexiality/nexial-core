metrics.FirstPaint = JSON.parse(localStorage.getItem('p'))
                         .filter(x => x.name === 'first-paint')
                         .map(y => y.startTime)[0];
