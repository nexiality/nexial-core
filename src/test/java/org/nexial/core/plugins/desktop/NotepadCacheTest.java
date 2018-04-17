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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import org.nexial.commons.utils.ResourceUtils;

import static org.nexial.core.plugins.desktop.ElementType.Button;
import static org.nexial.core.plugins.desktop.ElementType.MenuItem;
import static java.io.File.separator;

public class NotepadCacheTest {

	@Test
	public void testNewInstance() throws Exception {
		DesktopConfig config = DesktopConfig.parseJsonByAppId("notepad");
		Assert.assertNotNull(config);
		Assert.assertEquals(config.getDefaultWaitMs(), 800);
		Assert.assertEquals(config.getAppId(), "notepad");
		Assert.assertNotNull(config.getApp());

		DesktopSession session = new DesktopSession();
		session.config = config;
		session.reloadFromCache();
		// session.reloadCache(config, "commons");
		Assert.assertNotNull(session);

		Assert.assertEquals(session.getAppId(), config.getAppId());

		DesktopElement app = session.getApp();
		Assert.assertNotNull(app);
		// Assert.assertEquals(app.getControlType(), ElementType.WINDOW);
		// Assert.assertEquals(app.getName(), "Untitled - Notepad");

		Map<String, DesktopElement> components = app.getComponents();
		DesktopElement titleBar = components.get("TitleBar");
		Assert.assertNotNull(titleBar);
		testButton(titleBar.getComponents().get("Maximize"), "Maximize");
		testButton(titleBar.getComponents().get("Minimize"), "Minimize");
		testButton(titleBar.getComponents().get("Close"), "Close");

		DesktopElement menuBar = components.get("MenuBar");
		Assert.assertNotNull(menuBar);

		DesktopElement file = menuBar.getComponents().get("File");
		testMenuItem(file, "File", 7);
		testMenuItem(file.getComponents().get("New"), "New", -1);
		testMenuItem(file.getComponents().get("Open..."), "Open...", -1);
		testMenuItem(file.getComponents().get("Save"), "Save", -1);
		testMenuItem(file.getComponents().get("Save As..."), "Save As...", -1);
		testMenuItem(file.getComponents().get("Page Setup..."), "Page Setup...", -1);
		testMenuItem(file.getComponents().get("Print..."), "Print...", -1);
		testMenuItem(file.getComponents().get("Exit"), "Exit", -1);
		DesktopElement edit = menuBar.getComponents().get("Edit");
		testMenuItem(edit, "Edit", 11);
		testMenuItem(edit.getComponents().get("Undo"), "Undo", -1);
		testMenuItem(edit.getComponents().get("Cut"), "Cut", -1);
		testMenuItem(edit.getComponents().get("Copy"), "Copy", -1);
		testMenuItem(edit.getComponents().get("Paste"), "Paste", -1);
		testMenuItem(edit.getComponents().get("Delete"), "Delete", -1);
		testMenuItem(edit.getComponents().get("Find..."), "Find...", -1);
		testMenuItem(edit.getComponents().get("Find Next"), "Find Next", -1);
		testMenuItem(edit.getComponents().get("Replace..."), "Replace...", -1);
		testMenuItem(edit.getComponents().get("Go To..."), "Go To...", -1);
		testMenuItem(edit.getComponents().get("Select All"), "Select All", -1);
		testMenuItem(edit.getComponents().get("Time/Date"), "Time/Date", -1);
		DesktopElement format = menuBar.getComponents().get("Format");
		testMenuItem(format, "Format", 2);
		testMenuItem(format.getComponents().get("Word Wrap"), "Word Wrap", -1);
		testMenuItem(format.getComponents().get("Font..."), "Font...", -1);
		DesktopElement view = menuBar.getComponents().get("View");
		testMenuItem(view, "View", 1);
		testMenuItem(view.getComponents().get("Status Bar"), "Status Bar", -1);
		DesktopElement help = menuBar.getComponents().get("Help");
		testMenuItem(help, "Help", 2);
		testMenuItem(help.getComponents().get("View Help"), "View Help", -1);
		testMenuItem(help.getComponents().get("About Notepad"), "About Notepad", -1);

		DesktopElement dialog = components.get("Dialog");
		Assert.assertNotNull(dialog);
		Assert.assertEquals(dialog.getComponents().get("Open").getComponents().get("File name").getLabel(),
		                    "File name");
	}

	protected void testButton(DesktopElement button, String label) {
		Assert.assertNotNull(button);
		Assert.assertEquals(button.getAutomationId(), label);
		Assert.assertEquals(button.getLabel(), label);
		Assert.assertEquals(button.getName(), label);
		Assert.assertEquals(button.getControlType(), ElementType.BUTTON);
		Assert.assertEquals(button.getElementType(), Button);
		Assert.assertEquals(button.getComponents().size(), 0);
	}

	protected void testMenuItem(DesktopElement menuItem, String label, int numberOfChildMenu) {
		Assert.assertNotNull(menuItem);
		Assert.assertEquals(menuItem.getLabel(), label);
		Assert.assertEquals(menuItem.getName(), label);
		Assert.assertEquals(menuItem.getControlType(), ElementType.MENU_ITEM);
		Assert.assertEquals(menuItem.getElementType(), MenuItem);
		if (numberOfChildMenu != -1) { Assert.assertEquals(menuItem.getComponents().size(), numberOfChildMenu); }
	}

	static {
		String projectBase = StringUtils.removeEnd(ResourceUtils.getResourceFilePath("/unittesting/"), separator);
		System.out.println("projectBase = " + projectBase);
		System.setProperty("nexial.projectBase", projectBase);
	}
}
