/*
 * Copyright 2021 Deezer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package deezer.kustom.compiler

fun shortNamesForIndex(index: Int): String {
    fun letterForIndex(index: Int): Char = 'a' + index
    val letter = letterForIndex(index % 26)
    return if (index >= 26) {
        shortNamesForIndex((index / 26) - 1) + letter
    } else {
        letter.toString()
    }
}
