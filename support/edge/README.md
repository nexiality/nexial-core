### Introduction
The purpose of this directory is to act as a "lookup" service for Microsoft Edge webdriver.

Microsoft Edge browser is shipped with Windows 10, where each release is bundled with a different installation of the 
browser. As such, a different version of webdriver is likely required per release of Windows 10 (technically, per OS 
Build).

Hence this directory, which provides a lookup service for Nexial to download the appropriate Edge webdriver based on
the target Windows 10 OS Build number.  Here's how it works:

1. Nexial detects that web automation on Edge browser is to commence.
2. Nexial checks that the OS of the current execution is Windows 10. If not, error will be thrown and the execution will
   likely terminate. Edge browser can only work in Windows 10 at this time.
3. Nexial checks the [OS Build number of the current Windows 10](https://support.microsoft.com/en-us/help/13443/windows-which-operating-system) 
   (that runs the Nexial execution).
4. Next it checks to see if a previously download Edge webdriver is available on the system. The location of the Edge
   webdriver is `%USERPROFILE%\.nexial\edge\%OS_BUILD%`, where `%USERPROFILE%` is the home directory of the current 
   user, and `%OS_BUILD%` is the OS Build of the current Windows 10.
5. If a suitable webdriver is available, it will be used for Edge browser automation.
6. If no suitable webdriver can be found on the current OS, Nexial will inquire this directory (the lookup service) for 
   the download URL of the appropriate webdriver. It does so by 
   1. Issuing a GET on `https://raw.githubusercontent.com/nexiality/nexial-core/master/support/edge/%OS_BUILD%.txt`.
   2. The content of one of the files in this directory will be the response, which contains the URL to download
      the appropriate Edge webdriver.
   3. Upon receiving the download URL, Nexial will proceed to download the Edge webdriver and store it locally in
      `%USERPROFILE%\.nexial\edge\%OS_BUILD%`.
   4. Nexial will proceed to use the newly downloaded webdriver for Edge browser automation.

Note that the webdriver download process is an one-time activity. Step 4 above will always favor previously downloaded
webdriver, if it can be found, rather than downloading the same webdriver again.
