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

package org.nexial.core.plugins.mobile

internal enum class Direction(val detail: String) {
    UP_10P("up 10%"),
    UP_20P("up 20%"),
    UP_30P("up 30%"),
    UP_40P("up 40%"),
    UP_50P("up 50%"),
    UP_60P("up 60%"),
    UP_70P("up 70%"),
    UP_80P("up 80%"),
    UP_90P("up 90%"),
    UP("up"),

    DOWN_10P("down 10%"),
    DOWN_20P("down 20%"),
    DOWN_30P("down 30%"),
    DOWN_40P("down 40%"),
    DOWN_50P("down 50%"),
    DOWN_60P("down 60%"),
    DOWN_70P("down 70%"),
    DOWN_80P("down 80%"),
    DOWN_90P("down 90%"),
    DOWN("down"),

    LEFT_10P("left 10%"),
    LEFT_20P("left 20%"),
    LEFT_30P("left 30%"),
    LEFT_40P("left 40%"),
    LEFT_50P("left 50%"),
    LEFT_60P("left 60%"),
    LEFT_70P("left 70%"),
    LEFT_80P("left 80%"),
    LEFT_90P("left 90%"),
    LEFT("left"),

    RIGHT_10P("right 10%"),
    RIGHT_20P("right 20%"),
    RIGHT_30P("right 30%"),
    RIGHT_40P("right 40%"),
    RIGHT_50P("right 50%"),
    RIGHT_60P("right 60%"),
    RIGHT_70P("right 70%"),
    RIGHT_80P("right 80%"),
    RIGHT_90P("right 90%"),
    RIGHT("right");

}