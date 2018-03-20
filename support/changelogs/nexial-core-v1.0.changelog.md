# Release 1.0

[jms]
- missing client connection jar logic so we can provide instruction for missing client jars
- remove vendor-specific code (use Java reflection instead)

[rdbms]
- missing database driver logic so we can provide instruction for missing database driver
- remove driver-specific code (use Java reflection instead)

[web]
- shipped with [chromedriver v2.37](https://chromedriver.storage.googleapis.com/2.37/notes.txt), this includes
	- Supports Chrome v63-65
	- Resolved issue 2221: Add command-line option to log INFO level to stderr [[Pri-2]]
	- Resolved issue  450: ChromeDriver hangs when switching to new window whose document is being overwritten [[Pri-2]]
- shipped with [geckodriver v0.20.0 (firefox)](https://github.com/mozilla/geckodriver/releases/tag/v0.20.0), 
 this includes:
	- fixes to shut down firefox process _more_ cleanly.
	- keyDown and keyUp action now support more than single primitive characters
- shipped with [selenium 3.11.0](https://raw.githubusercontent.com/SeleniumHQ/selenium/master/java/CHANGELOG),
 this includes:
 - Removed deprecated methods from `RemoteWebDriver`.

[ws]
- url query string encoding to properly ampersand
