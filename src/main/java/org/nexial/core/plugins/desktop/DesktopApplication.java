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

/**
 * application object, to be kept in session, during test automation.
 */
public class DesktopApplication {
	protected String processId;
	protected String applicationVersion;
	protected DesktopElement activeContainer;

	public String getProcessId() { return processId; }

	public void setProcessId(String processId) { this.processId = processId; }

	public String getApplicationVersion() { return applicationVersion; }

	public void setApplicationVersion(String applicationVersion) { this.applicationVersion = applicationVersion; }

	public DesktopElement getActiveContainer() { return activeContainer; }

	public void setActiveContainer(DesktopElement activeContainer) { this.activeContainer = activeContainer; }
}
