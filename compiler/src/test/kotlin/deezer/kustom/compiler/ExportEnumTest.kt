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

import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import org.junit.Test

@KotlinPoetKspPreview
class ExportEnumTest {

    @Test
    fun basicEnum() {
        assertCompilationOutput(
            """
            package foo.bar
            
            import deezer.kustom.KustomExport

            @KustomExport
            enum class Season {
                SPRING,
                SUMMER,
                AUTUMN,
                WINTER
            }
    """,
            ExpectedOutputFile(
                path = "foo/bar/js/Season.kt",
                content = """
        package foo.bar.js

        import kotlin.String
        import kotlin.js.JsExport
        import foo.bar.Season as CommonSeason

        @JsExport
        public class Season internal constructor(
            internal val `value`: CommonSeason
        ) {
            public val name: String = value.name
        }
        
        public fun Season.importSeason(): CommonSeason = value
        
        public fun CommonSeason.exportSeason(): Season = Season(this)
        
        @JsExport
        public object Seasons {
            public val SPRING: Season = CommonSeason.SPRING.exportSeason()
        
            public val SUMMER: Season = CommonSeason.SUMMER.exportSeason()
        
            public val AUTUMN: Season = CommonSeason.AUTUMN.exportSeason()
        
            public val WINTER: Season = CommonSeason.WINTER.exportSeason()
        }
                """.trimIndent()
            )
        )
    }
}
