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

package org.nexial.commons.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;

import static org.nexial.core.NexialConst.DEF_CHARSET;

/**
 * utility for resources located in classpath
 */
public final class ResourceUtils {
	private static final int BUFFER_SIZE = 8192;

	private ResourceUtils() { }

	public static InputStream getInputStream(String resource) {
		if (StringUtils.isBlank(resource)) { return null; }
		if (!StringUtils.startsWith(resource, "/")) {
			resource = "/" + resource;
		}

		return ResourceUtils.class.getResourceAsStream(resource);
	}

	public static String getResourceFilePath(String resource) {
		if (StringUtils.isBlank(resource)) { return null; }
		if (!StringUtils.startsWith(resource, "/")) { resource = "/" + resource; }

		URL url = ResourceUtils.class.getResource(resource);
		if (url != null && StringUtils.isNotBlank(url.getFile())) { return url.getFile(); }
		return null;
	}

	/** load a {@link Properties} object based on {@code resource}, which is assumed to be in classpath. */
	public static Properties loadProperties(String resource) throws IOException {
		if (StringUtils.isBlank(resource)) { return null; }
		InputStream inputStream = getInputStream(resource);
		if (inputStream == null) { return null; }

		Properties prop = new Properties();
		try {
			prop.load(inputStream);
		} finally {
			inputStream.close();
		}

		return prop;
	}

	/** load a {@link Properties} object based on {@code resource}, which is assumed to be in classpath. */
	public static Properties loadProperties(File file) throws IOException {
		if (file == null) { return null; }

		InputStream fis = new FileInputStream(file);
		Properties prop = new Properties();
		try {
			prop.load(fis);
		} finally {
			if (fis != null) { fis.close(); }
		}

		return prop;
	}

	/** return the file content as <code >String</code> from the provided {@code resource}. */
	public static String loadResource(URL resource) throws IOException, JSONException {
		if (resource == null) { throw new IOException("resource is null"); }

		String msg = "Classpath resource '" + resource.getFile() + "' ";

		String fullpath = resource.getFile();
		if (StringUtils.isBlank(fullpath)) { throw new IOException(msg + "did not resolve to proper path."); }
		if (StringUtils.contains(fullpath, ".jar!") || StringUtils.contains(fullpath, ".zip!")) {
			// resource found in a jar/zip in the classpath... redirect to a more appropriate method
			return loadClasspathResource(resource);
		}

		File file = new File(URLDecoder.decode(fullpath, "UTF-8"));
		msg += "resolve to '" + fullpath + "' ";
		if (!file.exists()) { throw new IOException(msg + "but does not exists."); }
		if (!file.canRead()) { throw new IOException(msg + "but cannot be read."); }

		String content = FileUtils.readFileToString(file, DEF_CHARSET);
		if (StringUtils.isBlank(content)) { throw new JSONException(msg + "but has no content"); }
		return content;
	}

	/**
	 * load resource content as {@link String}.
	 */
	public static String loadClasspathResource(URL resource) throws IOException {
		if (resource == null) { throw new IOException("resource is null"); }

		boolean fromClasspath = false;
		String resourcePath = resource.toString();
		if (StringUtils.contains(resourcePath, ".jar!")) {
			fromClasspath = true;
			resourcePath = StringUtils.substringAfter(resourcePath, ".jar!");
		}

		if (StringUtils.contains(resourcePath, ".zip!")) {
			fromClasspath = true;
			resourcePath = StringUtils.substringAfter(resourcePath, ".zip!");
		}

		if (fromClasspath && StringUtils.startsWithAny(resourcePath, "\\", "/")) {
			resourcePath = StringUtils.substring(resourcePath, 1);
		}

		return loadContent(ResourceUtils.class.getClassLoader().getResourceAsStream(resourcePath));
	}

	public static String loadResource(String resource) throws IOException {
		if (StringUtils.isBlank(resource)) { return null; }
		InputStream inputStream = getInputStream(resource);
		if (inputStream == null) { return null; }
		return loadContent(inputStream);
	}

	protected static String loadContent(InputStream rs) throws IOException {
		BufferedInputStream bis = new BufferedInputStream(rs);

		StringBuilder sb = new StringBuilder();
		byte[] buffer = new byte[BUFFER_SIZE];
		do {
			int read = bis.read(buffer);
			if (read == -1) { break; }

			sb.append(new String(buffer, 0, read, "UTF-8"));
			buffer = new byte[BUFFER_SIZE];
		} while (true);

		return sb.toString();
	}
}
