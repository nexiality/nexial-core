# nexial-core - Nexial Automation

#### Test Automation Platform for _Everyone_!<br/>
![logo](https://nexiality.github.io/documentation/image/logo-x.png)
&nbsp;&nbsp;&nbsp;&nbsp;
![logo](https://nexiality.github.io/documentation/image/nexial-caption.png)

The main motivation of Nexial is to provide a set of automation capabilities for software testing.  

- ***Test automation for everyone*** - no coding required (but supported), simple and 
  relevant to all project participants
- Hybrid, Keyword-driven and standards-based; simple/familiar spreadsheet (Excel) interface
- Supports many application architecture and interface - web (browser), desktop, API/REST/SOAP, database, batch, ssh
- Extensive and flexible test and data management
- Local and remote (Jenkins, BrowserStack) support
- Supports FRIP (functional, regression, integration, performance testing)

---

To see Nexial in action, watch this short demo showcasing how Nexial 
1. performs a few Google searches (browser automation), 
2. uses one of the search results to invoke a few REST API calls (API automation),
3. bulk generates a series of SQL INSERT statements (text manipulation via Nexial expression),
4. stores API responses and Google search stats into database (database automation, with sqlite),
5. finally, performs a few simple data analytical queries and reports (database automation, Nexial expression). 

[Nexial in Action](https://www.youtube.com/watch?v=b372XikN1YU)

Wanna try? Have at it!
1. Download [Nexial distro](https://github.com/nexiality/nexial-core/releases)
2. Follow the [installation guide](https://nexiality.github.io/documentation/userguide/InstallingNexial) (most steps are one-time work)
3. Download the ["nexial-in-action" project](https://nexiality.github.io/documentation/nexial-in-action.zip) and unzip to local directory
4. Run it:
   Mac/Linux:
	```
	cd <NEXIAL_HOME>/bin
	./nexial.sh -plan <MY_NEXIAL_IN_ACTION_PROJECT>/artifact/plan/demo1-plan.xlsx
	```
	
   Windows:
   ```
	cd <NEXIAL_HOME>\bin
	nexial.cmd -plan <MY_NEXIAL_IN_ACTION_PROJECT>\artifact\plan\demo1-plan.xlsx
   ```

---

More introductory information can be found at our 
**[Introduction](https://nexiality.github.io/documentation/userguide/IntroductionAndFAQ)** page.

For more information, please visit [Our Site](https://nexiality.github.io/documentation/).

[Tutorials](https://nexiality.github.io/tutorials/)

---
