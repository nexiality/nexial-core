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

package org.nexial.core.plugins.web

import org.apache.commons.collections4.CollectionUtils
import org.openqa.selenium.By
import org.openqa.selenium.SearchContext
import org.openqa.selenium.WebElement
import java.io.Serializable

class LayeredFindBy(val locators: List<String>) : By(), Serializable {
    // very first locator should be absolute while all subsequent locators should be relative
    private val findBys = locators.mapIndexed {
        index, locator -> LocatorHelper.LocatorType.build(locator, index != 0)
    }.toList()

    override fun findElement(context: SearchContext): WebElement =
        findElements(context, findBys[0], findBys.drop(1)).first()

    override fun findElements(context: SearchContext): MutableList<WebElement> =
        findElements(context, findBys[0], findBys.drop(1))

    private fun findElements(context: SearchContext, findBy: By, childFindBy: List<By>): MutableList<WebElement> {
        val matches = findBy.findElements(context)
        if (CollectionUtils.isEmpty(matches)) return mutableListOf()

        if (CollectionUtils.isEmpty(childFindBy)) return matches

        return matches.map { container ->
            if (childFindBy.size == 1) {
                findElements(container, childFindBy[0], listOf())
            } else {
                findElements(container, childFindBy[0], childFindBy.drop(1))
            }
        }.flatten().toMutableList()
    }

    override fun toString() = locators.joinToString(prefix = "{", separator = " -> ", postfix = "}")
}