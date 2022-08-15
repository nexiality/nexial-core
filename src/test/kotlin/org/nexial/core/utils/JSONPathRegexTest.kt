package org.nexial.core.utils

import org.json.JSONArray
import org.junit.Test
import org.nexial.commons.utils.ResourceUtils
import org.nexial.commons.utils.TextUtils
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class JSONPathRegexTest {

	private val testFile = "/org/nexial/core/utils/geckodrivers.json"
	private val targetJsonPath = "[tag_name=v0.31.0].assets[name=REGEX:.+v0.31.0-macos\\..*].browser_download_url"

	@Test
	fun testDriverSearch() {
		val json = JSONArray(ResourceUtils.loadResource(testFile))
		val matches = JSONPath.find(json, targetJsonPath)
		println("${this.javaClass.simpleName} matches = $matches")

		assertNotNull(matches)
		assertFalse(TextUtils.isBetween(matches, "[", "]"))
		assertEquals("https://github.com/mozilla/geckodriver/releases/download/v0.31.0/geckodriver-v0.31.0-macos.tar.gz",
		             matches)
	}
}