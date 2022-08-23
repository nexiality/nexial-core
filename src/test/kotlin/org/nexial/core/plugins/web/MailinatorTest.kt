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

@file:Suppress("invisible_reference", "invisible_member")
package org.nexial.core.plugins.web

import org.junit.Test
import org.nexial.commons.utils.ResourceUtils
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MailinatorTest {

	val fixture1: String = ResourceUtils.loadResource("/org/nexial/core/plugins/web/mailinator_content.txt")

	@Throws(Exception::class)
	@Test
	fun testHarvestLinks() {
		val subject = Mailinator()
		val links = subject.harvestLinks(fixture1)
		println("links = $links")

		assertNotNull(links)
		assertEquals(2, links.size)
		assertEquals("https://www.mailinator.com/linker?linkid=5d5a9544-506a-410c-ab0f-7d630da2a141", links[0])
		assertEquals("https://www.mailinator.com/linker?linkid=7e0f423b-ff49-4115-bda8-26d4afba93c3", links[1])
	}

	@Throws(Exception::class)
	@Test
	fun testHarvestLinkLabels() {
		val subject = Mailinator()
		val labels = subject.extractLinkLabels(fixture1)
		println("link labels = $labels")

		assertNotNull(labels)
		assertEquals(1, labels.size)
		assertEquals("Activate Account", labels[0])
	}
}