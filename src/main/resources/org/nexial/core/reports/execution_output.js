"use strict";

let ExecutionChart = function(summary) {
  const DEFAULT_LABEL = "EXECUTION"
  const dataSetJSON = {
    "passPercentage": {
       "label": "pass %",
       "backgroundColor": "#aff",
       "borderColor": "#09f",
       "borderWidth": 2,
       "tension": 0.3,
       "fill": false,
       "radius": 4,
       "pointStyle": "rectRounded",
       "yAxisID": "y-axis-1",
       "type": "line"
    },
    "fail": {
       "label": "fail",
       "borderColor": "rgba(100,10,10,1)",
       "backgroundColor": "rgba(200,0,0,0.60)",
       "borderWidth": 1,
       "tension": 0.3,
       "fill": true,
       "yAxisID": "y-axis-1"
    },
    "pass": {
       "label": "pass",
       "borderColor": "rgba(10,100,10,0.5)",
       "backgroundColor": "rgba(30,204,30,0.35)",
       "borderWidth": 1,
       "tension": 0.3,
       "fill": true,
       "yAxisID": "y-axis-1"
    },
    "total": {
       "label": "total",
       "borderColor": "rgba(120,120,120,0.8)",
       "backgroundColor": "rgba(0,0,0,0.80)",
       "borderWidth": 2,
       "tension": 0.3,
       "fill": false,
       "yAxisID": "y-axis-1"
    },
    "duration": {
       "label": "duration (min)",
       "borderColor": "rgba(225,30,225,1)",
       "backgroundColor": "rgba(225,30,225,1)",
       "borderWidth": 2,
       "spanGaps": false,
       "tension": 0.1,
       "borderDash": [ 2, 2 ],
       "pointBorderWidth": 1,
       "pointRadius": 4,
       "pointStyle": "rectRounded",
       "fill": false,
       "yAxisID": "y-axis-2",
       "type": "line"
    }
  };

  let trace = [];
  let current = summary;
  let heading = summary.name;
  let chartDetails = getChartDetails(DEFAULT_LABEL);
  let executionLevel = "EXECUTION";

  this.initializeChart = function() {
    document.getElementById("rollUpResultsChart").style.visibility = "hidden";
    document.getElementById("resetResultsChart").style.visibility = "hidden";
    document.getElementById("resultsChart").onclick = drillDown;

    document.getElementById("resetResultsChart").onclick = resetChart;
    document.getElementById("rollUpResultsChart").onclick = rollUp;
  }

  function getChartDetails(label) {
    return new Chart(document.getElementById("resultsChart"), {
             type: "bar",
             data: getChartData(summary, [label]),
             options: getOptions(summary)});
  }

  function getChartData(obj, labels) {
    return {
      labels: labels,
      datasets: [
        {...dataSetJSON.passPercentage, data: [(obj.passCount / summary.totalSteps) * 100]},
        {...dataSetJSON.fail, data: [obj.failCount]},
        {...dataSetJSON.pass, data: [obj.passCount]},
        {...dataSetJSON.total, data: [obj.totalSteps]},
        {...dataSetJSON.duration, data: [getDuration(obj.endTime, obj.startTime)]}
      ]
    };
  }

  function getDuration(startTime, endTime) {
    return (new Date(startTime).getTime() - new Date(endTime).getTime()) / 60000;
  }

  function drillDown(click) {
    let points = chartDetails.getElementsAtEventForMode(click, "nearest", { intersect: true }, true);
    if (points.length) {
      if (current.executionLevel === "SCENARIO") {
         return;
      }

      let currentIndex = 0;
      if (executionLevel !== "EXECUTION") {
        currentIndex = points[0].index;
        current = current.nestedExecutions[currentIndex];
        executionLevel = current.executionLevel;
        trace.push(currentIndex);
      } else if (executionLevel === "EXECUTION") {
        executionLevel = "SCRIPT";
      }

      heading = getHeading(trace);
      updateChart();

      document.getElementById("resetResultsChart").style.visibility = "visible";
      document.getElementById("rollUpResultsChart").style.visibility = "visible";
    }
  }

  function updateChart() {
    let labels = [];
    let datasets = [];
    let chartData = {};

    let passCountData = [];
    let failCountData = [];
    let totalData = [];
    let percentageData = [];
    let durationData = [];

    for(let execution in current.nestedExecutions) {
      let currentExecution = current.nestedExecutions[execution];
      labels.push(currentExecution.name);

      percentageData.push((currentExecution.passCount/currentExecution.totalSteps) * 100);
      failCountData.push(currentExecution.failCount);
      passCountData.push(currentExecution.passCount);
      totalData.push(currentExecution.totalSteps);
      durationData.push(getDuration(currentExecution.endTime, currentExecution.startTime));
    }

    datasets.push({...dataSetJSON.passPercentage, data: percentageData});
    datasets.push({...dataSetJSON.fail, data: failCountData});
    datasets.push({...dataSetJSON.pass, data: passCountData});
    datasets.push({...dataSetJSON.total, data: totalData});
    datasets.push({...dataSetJSON.duration, data: durationData});

    chartData.labels = labels;
    chartData.datasets = datasets;

    let config = {
      data: {
        labels: chartData.labels,
         datasets: chartData.datasets
      },
      options: getOptions(current)
    }

    chartDetails.data = config.data;
    chartDetails.options = config.options;
    chartDetails.update();
  }

  function getHeading(trace) {
    if (trace.length > 0) {
      let headings = [summary.name];
      let executionElements = summary;

      for (let i = 0; i < trace.length; i++) {
        executionElements = executionElements.nestedExecutions[trace[i]];
        headings.push(executionElements.name);
      }
      return headings.join(" - ");
    }
    return summary.name;
  }

  function resetChart() {
    executionLevel = "EXECUTION";
    heading = summary.name;
    current = summary;
    chartDetails.data = getChartData(current, [DEFAULT_LABEL]);
    chartDetails.options = getOptions(current);
    chartDetails.update();
    trace = [];
    document.getElementById("rollUpResultsChart").style.visibility = "hidden";
    document.getElementById("resetResultsChart").style.visibility = "hidden";
  }

  function rollUp() {
    if (trace.length < 0 ) {
      alert("Invalid operation.");
      return;
    }

    if (trace.length < 1) {
      executionLevel = "EXECUTION";
      resetChart();
      return;
    }

    current = summary;
    for (let i = 0; i < trace.length -1; i++ ) {
       current = current.nestedExecutions[trace[i]];
       executionLevel = current.executionLevel;
    }

    trace.pop();
    heading = getHeading(trace);
    updateChart();

    document.getElementById("resetResultsChart").style.visibility = "visible";
    document.getElementById("rollUpResultsChart").style.visibility = "visible";
  }

  function getOptions(obj) {
    return {
      scales: {
         x: {
            ticks: {
              stepSize: 1,
              beginAtZero: true
            }
         },
         "y-axis-1": {
            ticks: {
              stepSize: 1,
              beginAtZero: true,
            },
            title: {
              display: true,
              text: "steps / %"
            }
         },
         "y-axis-2": {
             type: "linear",
             display: true,
             position: "right",

             title: {
              display: true,
              text: "duration(min)"
            },

             ticks: {
              stepSize: 1,
              beginAtZero: true
             },

             grid: {
                drawOnChartArea: false
             },
         }
      },
      responsive: true,
      interaction: {
        intersect: false,
      },
      plugins: {
        tooltip: {
          mode: "index",
          position: "nearest",
        },
        title: {
          display: true,
          text: heading
        },
        "legend": {
          "display": true,
          "position": "top",
          "labels": {
              "boxWidth": 10,
              "padding": 25,
              "usePointStyle": true
          }
        },
      }
   };
  }
}
