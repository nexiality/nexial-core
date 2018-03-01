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

import java.net.URL;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.util.ClassUtils;

/**
 *
 */
public class ResourceUtilsTest {

	@Test
	public void testLoadResource() throws Exception {
		String resName = "org/springframework/web/servlet/DispatcherServlet.properties";

		//InputStream rs = this.getClass().getClassLoader().getResourceAsStream(resName);
		//BufferedInputStream bis = new BufferedInputStream(rs);
		//
		//StringBuilder sb = new StringBuilder();
		//byte[] buffer = new byte[4096];
		//do {
		//	int read= bis.read(buffer);
		//	if (read == -1) { break; }
		//
		//	sb.append(new String(buffer));
		//	buffer= new byte[4096];
		//} while (true);
		//
		//System.out.println("sb.toString() = " + sb.toString());
		//
		//resName = "org/springframework/security/messages.properties";
		URL resource = ClassUtils.getDefaultClassLoader().getResource(resName);

		String content = ResourceUtils.loadResource(resource);

		System.out.println("content = " + content);
		Assert.assertNotNull(content);
		Assert.assertTrue(content.length() > 1);
	}
}
