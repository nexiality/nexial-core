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

package org.nexial.core.excel.ext;

import org.junit.Assert;
import org.junit.Test;

public class CipherHelperTest {

	@Test
	public void testCryptDecrypt() throws Exception {
		CipherHelper helper = new CipherHelper();

		Assert.assertEquals(helper.decrypt("crypt:544d412c09e4b62e026b2fb99e0b739e609e3c419b2488c0"), "sentryhAEd");
		Assert.assertEquals(helper.decrypt("crypt:750b06e5e720ed12cc3d8bfa9ef95082e208af35472fe25e"), "Drum Off!");
		Assert.assertEquals(helper.decrypt(
			"crypt:fbf9a5af96ba19e73fea99fd33dee150cdfe0541c8fb3c3941640a2ff2c00eaac126013c17262ea2"),
		                    "!@#$%^&*()_+1234567890-=");
		Assert.assertEquals("Wicked Guitar Riff!",
		                    helper.decrypt("crypt:6df412b77a8966fecac09215ce85548238c6464a25f9e3e3444f8937ba034ae6"));
		Assert.assertEquals("No Beginner's Luck",
		                    helper.decrypt("crypt:8bb744b4e50a144ae48cc8340b1cf0ff719d56543ab5f0d3a17cb7be56cf3e84"));
	}
}