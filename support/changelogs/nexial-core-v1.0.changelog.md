# Release 1.0

[jms]
- missing client connection jar logic so we can provide instruction for missing client jars
- remove vendor-specific code (use Java reflection instead)

[rdbms]
- missing database driver logic so we can provide instruction for missing database driver
- remove driver-specific code (use Java reflection instead)

[redis]
- basic redis support added. Initial support include:
  - url based connection (ie. `redis://...`)
  - `append(profile,key,value)`: append value to existing key
  - `assertKeyExists(profile,key)`: assert if specified key exists
  - `delete(profile,key)`: delete a key and the associated value
  - `flushAll(profile)`:  flush all data of the connected redis server
  - `flushDb(profile)`: flush all the data of the connected redis database
  - `rename(profile,current,new)`: rename key name
  - `set(profile,key,value)`: add/overwrite value of a key
  - `store(var,profile,key)`: store the current value of a key in redis to nexial context
  - `storeKeys(var,profile,keyPattern)`: store all matching keys - based on `keyPattern` - to nexial context

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
