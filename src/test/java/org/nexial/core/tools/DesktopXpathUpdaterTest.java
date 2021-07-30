/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.tools;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class DesktopXpathUpdaterTest {
	@Test
	public void treatXpath() {

		DesktopXpathUpdater updater = new DesktopXpathUpdater();
		String xpath1 = "/*[@AutomationId='PayrollApplicationClient']/*[@ControlType='ControlType.Pane' and not(contains(@AutomationId, 'splitter'))]/*[@AutomationId='OfficeLocation' and @ControlType='ControlType.Window']/*[@AutomationId='EaseBaseForm_Fill_Panel' and @ControlType='ControlType.Pane']/*[@AutomationId='splitContainer_Main' and @ControlType='ControlType.Pane']/*[@ControlType='ControlType.Pane' and position()=2]/*[@AutomationId='panel2' and @ControlType='ControlType.Pane']/*[@AutomationId='easeTabControl_Union' and @ControlType='ControlType.Tab']/*[@AutomationId='ultraTabPageControl_Union' and @ControlType='ControlType.Pane']";
		String actualXpath1 = updater.treatXpath(xpath1);
		String expectedXpath1 = "/*[@AutomationId='PayrollApplicationClient']/*[@ControlType='ControlType.Pane' and not(contains(@AutomationId, 'splitter'))]/*[@AutomationId='OfficeLocation']/*[@AutomationId='EaseBaseForm_Fill_Panel']/*[@AutomationId='splitContainer_Main']/*[@ControlType='ControlType.Pane' and position()=2]/*[@AutomationId='panel2']/*[@AutomationId='easeTabControl_Union']/*[@AutomationId='ultraTabPageControl_Union']";
		Assert.assertEquals(expectedXpath1, actualXpath1);

		String xpath2 = "/*[@AutomationId='AccountingApplicationClient']/*[@ControlType='ControlType.Pane' and not(contains(@AutomationId, 'splitter'))]/*[@ControlType='ControlType.Window' and @AutomationId='Invoice']/*[@AutomationId='EaseBaseForm_Fill_Panel']/*[@AutomationId='splitContainer_Main']/*[@ControlType='ControlType.Pane' and position()=2]/*[@ControlType='ControlType.Pane' and @AutomationId='panel2']/*[@AutomationId='splitContainer_Detail']/*[@ControlType='ControlType.Pane' and position()=2]/*[@AutomationId='easeDataGrid_invoice' and @ControlType='ControlType.Table']";
		String actualXpath2 = updater.treatXpath(xpath2);
		String expectedXpath2 = "/*[@AutomationId='AccountingApplicationClient']/*[@ControlType='ControlType.Pane' and not(contains(@AutomationId, 'splitter'))]/*[@AutomationId='Invoice']/*[@AutomationId='EaseBaseForm_Fill_Panel']/*[@AutomationId='splitContainer_Main']/*[@ControlType='ControlType.Pane' and position()=2]/*[@AutomationId='panel2']/*[@AutomationId='splitContainer_Detail']/*[@ControlType='ControlType.Pane' and position()=2]/*[@AutomationId='easeDataGrid_invoice']";
		Assert.assertEquals(expectedXpath2, actualXpath2);

		String xpath3 = "/*[@AutomationId='AccountingApplicationClient']";
		String actualXpath3 = updater.treatXpath(xpath3);
		String expectedXpath3 = "/*[@AutomationId='AccountingApplicationClient']";
		Assert.assertEquals(expectedXpath3, actualXpath3);

		String xpath4 = "/*[@ControlType='ControlType.Pane' and not(contains(@AutomationId, 'splitter'))]";
		String actualXpath4 = updater.treatXpath(xpath4);
		String expectedXpath4 = "/*[@ControlType='ControlType.Pane' and not(contains(@AutomationId, 'splitter'))]";
		Assert.assertEquals(expectedXpath4, actualXpath4);

		String xpath5 = "/*[@ControlType='ControlType.Window' and @AutomationId='Invoice']";
		String actualXpath5 = updater.treatXpath(xpath5);
		String expectedXpath5 = "/*[@AutomationId='Invoice']";
		Assert.assertEquals(expectedXpath5, actualXpath5);

		String xpath6 = "/*[@ControlType='ControlType.Window'      and @AutomationId='Invoice']";
		String actualXpath6 = updater.treatXpath(xpath6);
		String expectedXpath6 = "/*[@AutomationId='Invoice']";
		Assert.assertEquals(expectedXpath6, actualXpath6);

		String xpath7 = "/*[@AutomationId='EaseBaseForm_Fill_Panel' and          @ControlType='ControlType.Pane'] text";
		String actualXpath7 = updater.treatXpath(xpath7);
		String expectedXpath7 = "/*[@AutomationId='EaseBaseForm_Fill_Panel'] text";
		Assert.assertEquals(expectedXpath7, actualXpath7);

		String xpath8 = "/*text /* text [text @AutomationId='EaseBaseForm_Fill_Panel'     and   @ControlType='ControlType.Pane'] text";
		String actualXpath8 = updater.treatXpath(xpath8);
		String expectedXpath8 = "/*text /* text [text @AutomationId='EaseBaseForm_Fill_Panel'] text";
		Assert.assertEquals(expectedXpath8, actualXpath8);

		String xpath9 = "/*text [   @AutomationId='EaseBaseForm_Fill_Panel' and @ControlType='ControlType.Pane' text] text";
		String actualXpath9 = updater.treatXpath(xpath9);
		String expectedXpath9 = "/*text [   @AutomationId='EaseBaseForm_Fill_Panel' text] text";
		Assert.assertEquals(expectedXpath9, actualXpath9);
	}

}