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

package org.nexial.core.plugins.desktop;

import org.junit.Assert;
import org.junit.Test;

public class DesktopCommandTest {
	@Test
	public void addShortcut() throws Exception {
		String expected = "<[{Hello how's it going? Why don't we }]><[F2]><[CTRL-SPACE]><[CTRL-END]><[{just take this on right now!}]><[ENTER]>";

		String shortcuts = "";
		shortcuts = DesktopUtils.addShortcut(shortcuts, "Hello ");
		shortcuts = DesktopUtils.addShortcut(shortcuts, "how's it going? ");
		shortcuts = DesktopUtils.addShortcut(shortcuts, "Why don't we ");
		shortcuts = DesktopUtils.addShortcut(shortcuts, "[F2]]");
		shortcuts = DesktopUtils.addShortcut(shortcuts, "[CTRL-SPACE]");
		shortcuts = DesktopUtils.addShortcut(shortcuts, "just take this ");
		shortcuts = DesktopUtils.addShortcut(shortcuts, "on right no");
		shortcuts = DesktopUtils.addShortcut(shortcuts, "w!");
		shortcuts = DesktopUtils.addShortcut(shortcuts, "[ENTER]");
		Assert.assertEquals(expected, shortcuts);
	}
}