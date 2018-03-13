@echo off
REM --------------------------------------------------------------------------------
REM environment variable guide
REM --------------------------------------------------------------------------------
REM JAVA_HOME           - home directory of a valid JDK installation (1.6 or above)
REM PROJECT_HOME        - home directory of your project.
REM NEXIAL_OUT          - the output directory
REM FIREFOX_BIN         - the full path of firefox.exe
REM NEXIAL_RUNMODE      - determine screen capture image strategy (local or server)
REM --------------------------------------------------------------------------------

setlocal enableextensions enabledelayedexpansion
call :init
if NOT ERRORLEVEL 0 goto :exit

call :title "nexial script updater"
if NOT ERRORLEVEL 0 goto :exit

call :checkJava
if NOT ERRORLEVEL 0 goto :exit

call :resolveEnv
if NOT ERRORLEVEL 0 goto :exit

REM run nexial now
REM echo Runtime Option: %JAVA_OPT%
echo.

REM run now
%JAVA% -classpath %NEXIAL_CLASSES%;%NEXIAL_LIB%\nexial*.jar;%NEXIAL_LIB%\* %JAVA_OPT% org.nexial.core.tools.TestScriptUpdater %*

nexial-variable-update.cmd -d "sentry.3rdparty.logpath=nexial.3rdparty.logpath;sentry.assistantMode=nexial.assistantMode;sentry.breakCurrentIteration=nexial.breakCurrentIteration;sentry.browser=nexial.browser;sentry.browser.defaultWindowSize=nexial.browser.defaultWindowSize;sentry.browser.embedded.appLocation=nexial.browser.embedded.appLocation;sentry.browser.forceJSClick=nexial.browser.forceJSClick;sentry.browser.ie.requireWindowFocus=nexial.browser.ie.requireWindowFocus;sentry.browser.incognito=nexial.browser.incognito;sentry.browser.postCloseWaitMs=nexial.browser.postCloseWaitMs;sentry.browser.safari.cleanSession=nexial.browser.safari.cleanSession;sentry.browser.safari.useTechPreview=nexial.browser.safari.useTechPreview;sentry.browser.windowSize=nexial.browser.windowSize;sentry.browserConsoleLog=nexial.browserConsoleLog;sentry.browserLang=nexial.browserLang;sentry.browserstack.app.buildnumber=nexial.browserstack.app.buildnumber;sentry.browserstack.automatekey=nexial.browserstack.automatekey;sentry.browserstack.browser=nexial.browserstack.browser;sentry.browserstack.browser.version=nexial.browserstack.browser.version;sentry.browserstack.captureCrash=nexial.browserstack.captureCrash;sentry.browserstack.debug=nexial.browserstack.debug;sentry.browserstack.enablelocal=nexial.browserstack.enablelocal;sentry.browserstack.os=nexial.browserstack.os;sentry.browserstack.os.version=nexial.browserstack.os.version;sentry.browserstack.resolution=nexial.browserstack.resolution;sentry.browserstack.username=nexial.browserstack.username;sentry.chrome.profile=nexial.chrome.profile;sentry.commandDiscovery=nexial.commandDiscovery;sentry.commandDiscoveryWriteToFile=nexial.commandDiscoveryWriteToFile;sentry.compare.jsonReport=nexial.compare.jsonReport;sentry.compare.reportMatch=nexial.compare.reportMatch;sentry.compare.textReport=nexial.compare.textReport;sentry.dao.*=nexial.dao.*;sentry.dataBase=nexial.dataBase;sentry.delayBetweenStepsMs=nexial.delayBetweenStepsMs;sentry.delayBrowser=nexial.delayBrowser;sentry.desktop.app.version=nexial.desktop.app.version;sentry.desktop.application=nexial.desktop.application;sentry.desktop.autoClearModalDialog=nexial.desktop.autoClearModalDialog;sentry.desktop.container=nexial.desktop.container;sentry.desktop.container.name=nexial.desktop.container.name;sentry.desktop.dialogLookup=nexial.desktop.dialogLookup;sentry.desktop.fullScreenCapture=nexial.desktop.fullScreenCapture;sentry.desktop.hiertable=nexial.desktop.hiertable;sentry.desktop.hiertable.name=nexial.desktop.hiertable.name;sentry.desktop.list=nexial.desktop.list;sentry.desktop.list.name=nexial.desktop.list.name;sentry.desktop.process.id=nexial.desktop.process.id;sentry.desktop.session=nexial.desktop.session;sentry.desktop.table=nexial.desktop.table;sentry.desktop.table.name=nexial.desktop.table.name;sentry.desktop.table.row=nexial.desktop.table.row;sentry.desktop.table.row.name=nexial.desktop.table.row.name;sentry.desktop.tableeditable.column.found=nexial.desktop.tableeditable.column.found;sentry.desktop.tableeditable.column.name=nexial.desktop.tableeditable.column.name;sentry.desktop.textpane=nexial.desktop.textpane;sentry.desktopNotifyWaitMs=nexial.desktopNotifyWaitMs;sentry.devLogging=nexial.devLogging;sentry.elapsedTimeSLA=nexial.elapsedTimeSLA;sentry.enableEmail=nexial.enableEmail;sentry.endImmediate=nexial.endImmediate;sentry.enforcePageSourceStability=nexial.enforcePageSourceStability;sentry.excel=nexial.excel;sentry.excelVer=nexial.excelVer;sentry.executionFailCount=nexial.executionFailCount;sentry.failAfter=nexial.failAfter;sentry.failFast=nexial.failFast;sentry.failImmediate=nexial.failImmediate;sentry.forceIE32=nexial.forceIE32;sentry.generateReport=nexial.generateReport;sentry.highlight=nexial.highlight;sentry.highlightWaitMs=nexial.highlightWaitMs;sentry.home=nexial.home;sentry.httpTTL=nexial.httpTTL;sentry.ignoreBrowserAlert=nexial.ignoreBrowserAlert;sentry.imageTolerance=nexial.imageTolerance;sentry.inputExcel=nexial.inputExcel;sentry.inspectOnPause=nexial.inspectOnPause;sentry.interactive=nexial.interactive;sentry.interactive.debug=nexial.interactive.debug;sentry.io.compareIncludeAdded=nexial.io.compareIncludeAdded;sentry.io.compareIncludeMoved=nexial.io.compareIncludeMoved;sentry.io.compareIncludeRemoved=nexial.io.compareIncludeRemoved;sentry.lastAlertText=nexial.lastAlertText;sentry.lastOutcome=nexial.lastOutcome;sentry.lastScreenshot=nexial.lastScreenshot;sentry.lastTestScenario=nexial.lastTestScenario;sentry.lastTestStep=nexial.lastTestStep;sentry.lenientStringCompare=nexial.lenientStringCompare;sentry.logpath=nexial.logpath;sentry.mailTo=nexial.mailTo;sentry.manageMemory=nexial.manageMemory;sentry.minExecSuccessRate=nexial.minExecSuccessRate;sentry.nullValue=nexial.nullValue;sentry.openResult=nexial.openResult;sentry.outBase=nexial.outBase;sentry.outputCloudBase=nexial.outputCloudBase;sentry.outputToCloud=nexial.outputToCloud;sentry.pdfFormStrategy.*=nexial.pdfFormStrategy.*;sentry.pdfUseAscii=nexial.pdfUseAscii;sentry.planBase=nexial.planBase;sentry.pollWaitMs=nexial.pollWaitMs;sentry.project=nexial.project;sentry.projectBase=nexial.projectBase;sentry.proxyDirect=nexial.proxyDirect;sentry.proxyRequired=nexial.proxyRequired;sentry.proxy_password=nexial.proxy_password;sentry.proxy_user=nexial.proxy_user;sentry.rdbms.packSingleRow=nexial.rdbms.packSingleRow;sentry.recordingEnabled=nexial.recordingEnabled;sentry.reportServerBaseDir=nexial.reportServerBaseDir;sentry.reportServerUrl=nexial.reportServerUrl;sentry.resetFailFast=nexial.resetFailFast;sentry.runID=nexial.runID;sentry.runID.prefix=nexial.runID.prefix;sentry.scriptRef.*=nexial.scriptRef.*;sentry.scenarioRef.*=nexial.scenarioRef.*;sentry.scope.currentIteration=nexial.scope.currentIteration;sentry.scope.executionMode=nexial.scope.executionMode;sentry.scope.fallbackToPrevious=nexial.scope.fallbackToPrevious;sentry.scope.iteration=nexial.scope.iteration;sentry.scope.lastIteration=nexial.scope.lastIteration;sentry.scope.mailTo=nexial.scope.mailTo;sentry.screenRecorder=nexial.screenRecorder;sentry.screenshotOnError=nexial.screenshotOnError;sentry.script.*=nexial.scriptRef.*;sentry.scriptBase=nexial.scriptBase;sentry.soapui.storeResponse=nexial.soapui.storeResponse;sentry.spreadsheet.program=nexial.spreadsheet.program;sentry.spring.config=nexial.spring.config;sentry.startBaseUrl=nexial.startBaseUrl;sentry.startUrl=nexial.startUrl;sentry.stepByStep=nexial.stepByStep;sentry.suite=nexial.suite;sentry.testsuite.name=nexial.testsuite.name;sentry.textDelim=nexial.textDelim;sentry.uiRenderWaitMs=nexial.uiRenderWaitMs;sentry.verbose=nexial.verbose;sentry.waitSpeed=nexial.waitSpeed;sentry.web.alwaysWait=nexial.web.alwaysWait;sentry.winiumJoinExisting=nexial.winiumJoinExisting;sentry.winiumLogPath=nexial.winiumLogPath;sentry.winiumPort=nexial.winiumPort;sentry.winiumServiceActive=nexial.winiumServiceActive;sentry.winiumSoloMode=nexial.winiumSoloMode;sentry.worksheet=nexial.worksheet;sentry.ws.allowCircularRedirects=nexial.ws.allowCircularRedirects;sentry.ws.allowRelativeRedirects=nexial.ws.allowRelativeRedirects;sentry.ws.basic.password=nexial.ws.basic.password;sentry.ws.basic.user=nexial.ws.basic.user;sentry.ws.connectionTimeout=nexial.ws.connectionTimeout;sentry.ws.digest.nonce=nexial.ws.digest.nonce;sentry.ws.digest.password=nexial.ws.digest.password;sentry.ws.digest.realm=nexial.ws.digest.realm;sentry.ws.digest.user=nexial.ws.digest.user;sentry.ws.enableExpectContinue=nexial.ws.enableExpectContinue;sentry.ws.enableRedirects=nexial.ws.enableRedirects;sentry.ws.header.*=nexial.ws.header.*;sentry.ws.proxyHost=nexial.ws.proxyHost;sentry.ws.proxyPassword=nexial.ws.proxyPassword;sentry.ws.proxyPort=nexial.ws.proxyPort;sentry.ws.proxyRequired=nexial.ws.proxyRequired;sentry.ws.proxyUser=nexial.ws.proxyUser;sentry.ws.readTimeout=nexial.ws.readTimeout;sentry.ws.requestPayloadCompact=nexial.ws.requestPayloadCompact;" %*

endlocal
exit /b 0
goto :eof

:init
	.commons.cmd %*

:checkJava
	.commons.cmd %*

:title
	.commons.cmd %*

:resolveEnv
	.commons.cmd %*

:reportBadInputAndExit
	echo.
	echo ERROR: Required input not found.
	echo USAGE: %0 [project name] [optional: testcase id, testcase id, ...]
	echo.
	echo.
	goto :exit

:exit
	endlocal
	exit /b 1




