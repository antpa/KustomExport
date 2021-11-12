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
package deezer.kustom.compiler.js.mapping

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BOOLEAN_ARRAY
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.CHAR_ARRAY
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.DOUBLE_ARRAY
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FLOAT_ARRAY
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.INT_ARRAY
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.SHORT_ARRAY
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import deezer.kustom.compiler.firstParameterizedType
import deezer.kustom.compiler.js.ALL_KOTLIN_EXCEPTIONS
import deezer.kustom.compiler.js.ERROR
import deezer.kustom.compiler.js.mapping.TypeMapping.MappingOutput
import deezer.kustom.compiler.js.mapping.TypeMapping.exportMethod
import deezer.kustom.compiler.js.mapping.TypeMapping.exportedType
import deezer.kustom.compiler.js.mapping.TypeMapping.importMethod
import deezer.kustom.compiler.js.pattern.isKotlinFunction
import deezer.kustom.compiler.js.pattern.qdot
import deezer.kustom.compiler.shortNamesForIndex

const val INDENTATION = "    "

fun initCustomMapping() {
    // Doc interop: https://kotlinlang.org/docs/js-to-kotlin-interop.html#primitive-arrays
    // No special mappings
    TypeMapping.mappings += listOf(
        // TODO: Check that 'char' is properly re-interpreted
        BOOLEAN, BYTE, CHAR, SHORT, INT, FLOAT, DOUBLE, // => Js "number"
        STRING, // => Js "string"
        BOOLEAN_ARRAY, BYTE_ARRAY, // => Js "Int8Array"
        SHORT_ARRAY, // => Js "Int16Array"
        INT_ARRAY, // => Js "Int32Array"
        FLOAT_ARRAY, // => Js "Float32Array"
        DOUBLE_ARRAY, // => Js "Float64Array"
        CHAR_ARRAY, // => Js "UInt16Array"
        ANY, // => Js "Object"
        UNIT, // => disappear?
    ).map { exportableType ->
        exportableType to MappingOutput(
            exportType = { exportableType },
            importMethod = { targetName, _ -> targetName },
            exportMethod = { targetName, _ -> targetName },
        )
    }

    TypeMapping.mappings += ALL_KOTLIN_EXCEPTIONS.map { exportableType ->
        exportableType to MappingOutput(
            exportType = { ERROR },
            importMethod = { targetName, _ -> "$targetName.cause as ${exportableType.simpleName}" },
            exportMethod = { targetName, _ -> "Error($targetName)" },
        )
    }

    TypeMapping.mappings += mapOf<TypeName, MappingOutput>(
        // https://kotlinlang.org/docs/js-to-kotlin-interop.html#kotlin-types-in-javascript
        // doc: kotlin.Long is not mapped to any JavaScript object, as there is no 64-bit integer number type in JavaScript. It is emulated by a Kotlin class.
        LONG to MappingOutput(
            exportType = { DOUBLE },
            importMethod = { targetName, typeName -> "$targetName${typeName.qdot}toLong()" },
            exportMethod = { targetName, typeName -> "$targetName${typeName.qdot}toDouble()" },
        ),

        LONG_ARRAY to MappingOutput(
            exportType = { ARRAY.parameterizedBy(exportedType(LONG)) },
            // TODO: improve perf by avoiding useless double transformation
            // LongArray(value.size) { index -> value[index].toLong() }
            importMethod = { targetName, typeName ->
                targetName +
                    "${typeName.qdot}map { ${importMethod("it", LONG)} }" +
                    "${typeName.qdot}toLongArray()"
            },
            exportMethod = { targetName, typeName ->
                targetName +
                    "${typeName.qdot}map { ${exportMethod("it", LONG)} }" +
                    "${typeName.qdot}toTypedArray()"
            },
        ),

        ARRAY to MappingOutput(
            exportType = { ARRAY.parameterizedBy(exportedType(it.firstParameterizedType())) },
            importMethod = { targetName, typeName ->
                val importMethod = importMethod("it", typeName.firstParameterizedType())
                if (importMethod == "it") targetName else {
                    "$targetName${typeName.qdot}map { $importMethod }${typeName.qdot}toTypedArray()"
                }
            },
            exportMethod = { targetName, typeName ->
                val exportMethod = exportMethod("it", typeName.firstParameterizedType())
                if (exportMethod == "it") targetName else {
                    "$targetName${typeName.qdot}map { $exportMethod }${typeName.qdot}toTypedArray()"
                }
            },
        ),

        LIST to MappingOutput(
            exportType = { ARRAY.parameterizedBy(exportedType(it.firstParameterizedType())) },
            importMethod = { targetName, typeName ->
                targetName +
                    "${typeName.qdot}map { ${importMethod("it", typeName.firstParameterizedType())} }"
            },
            exportMethod = { targetName, typeName ->
                targetName +
                    "${typeName.qdot}map { ${exportMethod("it", typeName.firstParameterizedType())} }" +
                    "${typeName.qdot}toTypedArray()"
            },
        ),
        // TODO: Handle other collections
    )

    TypeMapping.advancedMappings += mapOf<(TypeName) -> Boolean, MappingOutput>(
        { type: TypeName -> type.isKotlinFunction() } to MappingOutput(
            exportType = {
                val lambda = it as ParameterizedTypeName
                val args = lambda.typeArguments.dropLast(1)
                val returnType = lambda.typeArguments.last()
                LambdaTypeName.get(
                    parameters = args.map { arg ->
                        ParameterSpec.builder("", exportedType(arg)).build()
                    },
                    returnType = returnType
                )
            },
            importMethod = { targetName, typeName ->
                val lambda = typeName as ParameterizedTypeName
                val returnType = lambda.typeArguments.last()
                val namedArgs = lambda.typeArguments.dropLast(1)
                    .mapIndexed { index, typeName -> typeName to shortNamesForIndex(index) }
                val signature = namedArgs
                    .joinToString { (type, name) -> "$name: $type" }
                val importedArgs: String = namedArgs
                    .joinToString { (type, name) -> exportMethod(name, type) }

                """
                    |{ $signature ->
                    |$INDENTATION${importMethod("$targetName($importedArgs)", returnType)}
                    |}
                """.trimMargin()
            },
            exportMethod = { targetName, typeName ->
                val lambda = typeName as ParameterizedTypeName
                val returnType = lambda.typeArguments.last()
                val namedArgs = lambda.typeArguments.dropLast(1)
                    .mapIndexed { index, typeName -> typeName to shortNamesForIndex(index) }
                val signature = namedArgs
                    .joinToString { (type, name) -> "$name: ${exportedType(type)}" }
                val importedArgs: String = namedArgs
                    .joinToString { (type, name) -> importMethod(name, type) }

                """
                    |{ $signature ->
                    |$INDENTATION${exportMethod("$targetName($importedArgs)", returnType)}
                    |}
                """.trimMargin()
            },
        )
    )
}
