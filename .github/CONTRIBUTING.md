## Contributing to Nexial Automation
Nexial Automation Platform (Nexial for short) is released under the Apache 2.0 license. If you would like to contribute 
something, or simply want to hack on the code this document should help you get started.

---

### Code of Conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you agreed 
to uphold this code. Please report unacceptable behavior to @nexiality/nexial-dev.

---

### Using GitHub issues
We use GitHub issues to track bugs and enhancements. If you have a general usage question or inquiry, please ask as an
[enquiry issue](https://github.com/nexiality/nexial-core/issues/new?template=enquiry.md) or use the Comments feature 
available to every page of our [documentation site](https://nexiality.github.io/documentation/).

If you are [reporting a bug](https://github.com/nexiality/nexial-core/issues/new?template=bug_report.md), please help 
to speed up problem diagnosis by providing as much information as possible. Screenshots or if possible, a sample 
automation project would be very helpful.

---

### Code Conventions and Housekeeping
(_shamelessly but gratefully_ adopted in parts from [Spring Boot](https://github.com/spring-projects/spring-boot/blob/master/CONTRIBUTING.adoc))

None of these is must for a pull request, but they will certainly help. They can also be added after the original pull 
request but before a merge.

- Use the [Nexial code format conventions](#nexial-code-conventions). If you use IntelliJ IDEA, you might want to check
  out the [Working on IntelliJ](#working-on-intellij) section on how you can import Nexial code style scheme to your 
  project.
- Make sure all new `.java` files to have a simple Javadoc class comment with at least an `@author` tag identifying 
  you, and preferably at least a paragraph on what the class is for.
- Add the ASF license header comment to all new `.java` files (copy from existing files in the project).
- Add yourself as an `@author` to the `.java` files that you modify substantially (more than cosmetic changes).
- Add some Javadocs, especially when significant changes are proposed.
- A few unit tests would help a lot as well — someone has to do it.
- If no one else is using your branch, please rebase it against the current master (or other target branch in the main 
  project).
- When writing a commit message consider following 
  [these conventions](http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html). If you are fixing an 
  existing issue please add `Fixes gh-XXXX` at the end of the commit message (where `XXXX` is the issue number).

---

### Nexial code conventions
Below are the code conventions uses in Nexial development. It is almost always a good idea to follow the same coding
convention.
1. Line separator: `\n` (Unix and OS X style) for all OS
2. Wrapping: 120. Line wraps at 120
3. Tab size: 4 with **whitespaces**
4. Whitespace: around keywords and operators.
   Keeping whitespace lines out method bodies can help make the code easier to scan. If blank lines are only included 
   between methods it becomes easier to see the overall structure of the class. If you find you need whitespace inside 
   your method, consider if extracting a private method might give a better result.
5. Braces: end of line
6. Comments
   Try to add javadoc for each public method and constant. Private methods shouldn’t generally need javadoc, unless it 
   provides a natural place to document unusual behavior.
7. Final
   Private members should be final whenever possible. Local variable and parameters should generally not be explicitly 
   declared as final since it adds so much noise.
8. Arrangement
   In general, we try to keep getters and setters of the same property together. Otherwise field and method arrangement
   are done in the order of public, protected and private.

---

### Working on IntelliJ
Nexial dev uses [IntelliJ IDEA IDE 2018.1](https://www.jetbrains.com/idea/) as its main development environment. As a
convenience, you can import the `${PROJECT}/.github/nexial.xml` as a IntelliJ Code Style configuration:
1. Open IntelliJ
2. Open Preferences
3. Go to `Editor | Code Style`
4. From the right side of the Preferences window, click on "cog" (or "gear") icon next to "Scheme:" label.
5. Select `Import Scheme | Intellij IDEA code style XML'
6. select `${PROJECT}/.github/nexial.xml`

You can also check with [IntelliJ's online help](https://www.jetbrains.com/help/idea/copying-code-style-settings.html) 
for the same.

---

### Working on other IDEs
Nexial, Java and Gradle works well with most popular Java IDEs. Please refer to vendor documentation for specifics.

We use the default project structure as laid out by [Gradle Java plugin](https://docs.gradle.org/current/userguide/java_plugin.html).

---

### Building from source
To build the source you will need to install [Gradle](https://gradle.org/install/) v4.7 or above and JDK 1.8_151 or 
above.

At a minimum, one would run the following gradle command in order to generate an usable distribution:

```bash
gradle clean installDist
```

By doing the above, one should find the distribution under the `${PROJECT_HOME}/build/install/nexial-core`. 

However, in order to enable newly developed Nexial commands in the Nexial scripts (spreadsheet), one would need to run 
the `support/nexial-command-generator.sh` and the `bin/nexial-script-update.sh` from the distribution directory. As a 
convenience, we have added a helper script in the `support` directory to orchestrate all these work:

```bash
cd ${PROJECT_HOME}
cd support
./build.sh
```

This will compile the project, copy the runtime artifacts to `${PROJECT_HOME}/build/install/nexial-core` and generate 
fresh templates in `${PROJECT_HOME}/build/install/template` with the latest available commands.

---

### Testing

Nexial development uses JUnit 4.x as its unit testing framework. Unit tests should be kept in `src/test` directory. 
When possible, test classes should be named similar to the target class in terms of package and classname.  For example, 
for a Java class `org.nexial.core.a.b.C`, which would be located in 
`${PROJECT}/src/main/java/org/nexial/core/a/b/MyClass.java`, the corresponding test class should be located in 
`${PROJECT}/src/test/java/org/nexial/core/a/b/MyClassTest.java`.

For spreadsheet-based testing - i.e. using Nexial script - such is the expectation:
- the corresponding Nexial artifacts (data files, scripts, plans) should be located in 
  `${PROJECT}/src/test/resources/unittesting/artifact/`. By convention, script name should be prefixed with `unitTest_`.
- the unit test class would be located somewhere under `${PROJECT}/src/test/java/org/nexial/core/plugins/`, depending on
  the target command. Test class name should end with `Test`.
- the unit test class for such purpose should extend `org.nexial.core.ExcelBasedTests`, which provides consistency and
  helper methods to execute corresponding Nexial scripts and to assert expected pass/fail counts. Here's an example of
  the test harness code excerpted from `${PROJECT}/src/test/java/org/nexial/core/plugins/base/HeadlessBaseTests.java`:
  
  ```java
  public class HeadlessBaseTests extends ExcelBasedTests {
        @Test
        public void baseCommandTests_part1() throws Exception {
            ExecutionSummary executionSummary = testViaExcel("unitTest_base_part1.xlsx");
            assertPassFail(executionSummary, "base_showcase", TestOutcomeStats.allPassed());
            assertPassFail(executionSummary, "function_projectfile", TestOutcomeStats.allPassed());
            assertPassFail(executionSummary, "function_array", TestOutcomeStats.allPassed());
            assertPassFail(executionSummary, "function_count", TestOutcomeStats.allPassed());
            assertPassFail(executionSummary, "function_date", TestOutcomeStats.allPassed());
            assertPassFail(executionSummary, "actual_in_output", TestOutcomeStats.allPassed());
        }
    
        @Test
        public void baseCommandTests_part2() throws Exception {
          ... ...
        }
    
        static {
            // prevent result automatically open after execution
            System.setProperty(OPT_OPEN_RESULT, "off");
        }
    }
    ```

---

### Cloning the git repository on Windows

Some files in the git repository may exceed the Windows maximum file path (260 characters), depending on where you 
clone the repository. If you get `Filename too long` errors, set the `core.longPaths=true` git option:

    git clone -c core.longPaths=true https://github.com/nexiality/nexial-core

---
