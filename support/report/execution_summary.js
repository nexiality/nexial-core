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
 */

"use strict";

function findTargetId(/*Event*/event) { return $(event.target.parentNode).attr('target'); }

function showSection() {
    let sectionId = findTargetId(event);
    if (sectionId) { $('#' + sectionId).show(250); }
}

function hideSection() {
    console.log(event);
    let sectionId = findTargetId(event);
    if (sectionId) { $('#' + sectionId).hide(250); }
}

function showAllSections() {
    $('.showhide').each(function (index, elem) { $('#' + $(elem).attr('target')).show(250); });
}

function hideAllSections() {
    $('.showhide').each(function (index, elem) { $('#' + $(elem).attr('target')).hide(250); });
}