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

package org.nexial.commons.utils.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_SESSION;

/**
 * <p/>
 * Spring-related utilities for the Web tier.
 *
 * @author $Author: chandran $
 */
public final class SpringWebUtils { //implements ServletContextAware {
	public static final String LAST_ERR_MSG = "LastErrorMessage";
	public static final String SUCCESS_MSG = "SuccessMessage";
	public static final String NEXT_PAGE = "NextPage";
	private static final Logger LOGGER = LoggerFactory.getLogger(SpringWebUtils.class);

	private static int transitionScope = SCOPE_SESSION;

	private SpringWebUtils() { }

	public static void setTransitionScope(int transitionScope) {
		SpringWebUtils.transitionScope = transitionScope;
	}

	public static <T> T getSessionObject(String key, Class<T> requiredType) {
		return getObject(key, requiredType, SCOPE_SESSION);
	}

	public static double getSessionDouble(String key) {
		return getSessionObject(key, Double.class);
	}

	public static String getSessionString(String key) {
		return getSessionObject(key, String.class);
	}

	public static void setSessionObject(String key, Object value) {
		setObject(key, value, SCOPE_SESSION);
	}

	/**
	 * find object in HTTP session keyed by <code>key</code> and of type <code>requiredType</code>
	 * and "pop" such reference off the HTTP session.
	 */
	public static <T> T popSessionObject(String key, Class<T> requiredType) {
		return popObject(key, requiredType, SCOPE_SESSION);
	}

	/**
	 * remove objects in HTTP session (if exists) based on <code>expiredKeysRegex</code>, which
	 * are regex to match the targeted session keys.
	 */
	public static void removeSessionObjects(List<String> expiredKeysRegex) {
		RequestAttributes reqAttribs = RequestContextHolder.getRequestAttributes();
		if (reqAttribs == null) { return; }

		String[] attribNames = reqAttribs.getAttributeNames(SCOPE_SESSION);
		for (String attriName : attribNames) {
			for (String regex : expiredKeysRegex) {
				if (attriName.matches(regex)) {
					reqAttribs.removeAttribute(attriName, SCOPE_SESSION);
				}
			}
		}
	}

	/** remove objects in HTTP session (if exists) based on <code>key</code>. */
	public static void removeSessionObject(String key) { removeObject(key, SCOPE_SESSION); }

	public static void addSessionNumber(String key, double num) {
		Double currentValue = getSessionDouble(key);
		if (currentValue == null) {
			setSessionObject(key, num);
		} else {
			setSessionObject(key, currentValue + num);
		}
	}

	/**
	 * return session-id for currently in-progress request.  Based on Spring's documentation,
	 * a session will be created if it does not exist (or has expired) at the time of this
	 * invocation.
	 */
	public static String getSessionId() {
		RequestAttributes reqAttribs = RequestContextHolder.getRequestAttributes();
		if (reqAttribs == null) { return null; }
		return reqAttribs.getSessionId();
	}

	public static <T> T getRequestAttribObject(String key, Class<T> requiredType) {
		return getObject(key, requiredType, SCOPE_REQUEST);
	}

	public static String getRequestAttribString(String key) {
		return getObject(key, String.class, SCOPE_REQUEST);
	}

	public static void setRequestAttribObject(String key, Object value) {
		setObject(key, value, SCOPE_REQUEST);
	}

	/**
	 * find object in HTTP session keyed by <code>key</code> and of type <code>requiredType</code>
	 * and "pop" such reference off the HTTP session.
	 */
	public static <T> T popRequestAttribObject(String key, Class<T> requiredType) {
		return popObject(key, requiredType, SCOPE_REQUEST);
	}

	/** remove objects in HTTP session (if exists) based on <code>key</code>. */
	public static void removeRequestAttribObject(String key) { removeObject(key, SCOPE_REQUEST); }

	private static <T> T getObject(String key, Class<T> requiredType, int scope) {
		RequestAttributes reqAttribs = RequestContextHolder.getRequestAttributes();
		if (reqAttribs == null) { return null; }

		Object obj = reqAttribs.getAttribute(key, scope);
		if (obj == null) { return null; }

		return requiredType.isAssignableFrom(obj.getClass()) ? requiredType.cast(obj) : null;
	}

	private static void setObject(String key, Object value, int scope) {
		RequestAttributes reqAttribs = RequestContextHolder.getRequestAttributes();
		if (reqAttribs == null) { return; }

		reqAttribs.setAttribute(key, value, scope);
	}

	private static <T> T popObject(String key, Class<T> requiredType, int scope) {
		RequestAttributes reqAttribs = RequestContextHolder.getRequestAttributes();
		if (reqAttribs == null) { return null; }

		Object obj = reqAttribs.getAttribute(key, scope);
		if (obj == null) { return null; }

		reqAttribs.removeAttribute(key, scope);

		return requiredType.isAssignableFrom(obj.getClass()) ? requiredType.cast(obj) : null;
	}

	private static void removeObject(String key, int scope) {
		RequestAttributes reqAttribs = RequestContextHolder.getRequestAttributes();
		if (reqAttribs == null) { return; }
		reqAttribs.removeAttribute(key, scope);
	}
}
