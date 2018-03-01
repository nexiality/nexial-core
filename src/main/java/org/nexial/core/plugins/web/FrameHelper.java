/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.web;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.nexial.core.model.StepResult;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.WebDriver;

import static org.nexial.core.utils.CheckUtils.requires;
import static org.nexial.core.utils.CheckUtils.requiresNotBlank;

class FrameHelper {
	private WebCommand delegator;
	private WebDriver driver;

	FrameHelper(WebCommand delegator, WebDriver driver) {
		this.delegator = delegator;
		this.driver = driver;
	}

	protected StepResult assertFramePresent(String frameName) {
		requiresNotBlank(frameName, "invalid frame name", frameName);

		return execScript("return window.frames['" + frameName + "']!=null", Boolean.class) ?
               StepResult.success("frame '" + frameName + "' found") :
               StepResult.fail("frame '" + frameName + "' DOES NOT exists.");
	}

	protected StepResult assertFrameCount(String count) {
		// ensuring that count can be converted to an integer
		int countInt = delegator.toInt(count, "count");
		Long frameCount = execScript("return window.frames.length", Long.class);
		String actual = frameCount != null ? frameCount.toString() : "0";
		delegator.assertEquals(countInt + "", actual);

		return StepResult.success("EXPECTS " + count + " frames; found " + actual);
	}

	protected StepResult selectFrame(String locator) {
		locator = delegator.locatorHelper.validateLocator(locator);

		// dom=frames["main"].frames["subframe"]
		//selenium.selectFrame(locator);

		try {
			if (StringUtils.equals(locator, "relative=top")) {
				driver = driver.switchTo().defaultContent();
				return StepResult.success("selected frame '" + locator + "'");
			}

			if (StringUtils.startsWith(locator, "index=")) {
				String frameIndex = StringUtils.substringAfter(locator, "index=");
				requires(NumberUtils.isDigits(frameIndex), "invalid frame index", frameIndex);
				int index = NumberUtils.toInt(frameIndex);
				driver = driver.switchTo().frame(index);
				return StepResult.success("selected frame '" + locator + "'");
			}

			if (StringUtils.startsWith(locator, "id=")) {
				String frameId = StringUtils.substringAfter(locator, "id=");
				driver = driver.switchTo().frame(frameId);
				return StepResult.success("selected frame '" + locator + "'");
			}

			if (StringUtils.startsWith(locator, "name=")) {
				String frameName = StringUtils.substringAfter(locator, "name=");
				driver = driver.switchTo().frame(frameName);
				return StepResult.success("selected frame '" + locator + "'");
			}

			driver = driver.switchTo().frame(delegator.findElement(locator));
			return StepResult.success("selected frame '" + locator + "'");
		} catch (NoSuchFrameException e) {
			return StepResult.fail("Cannot switch to target frame '" + locator + "': " + e.getMessage());
		}
	}

	private <T> T execScript(String script, Class<T> returnType) {
		return (T) ((JavascriptExecutor) driver).executeScript(script);
	}

}
