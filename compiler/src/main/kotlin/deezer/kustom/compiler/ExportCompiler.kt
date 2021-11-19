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

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import deezer.kustom.KustomExport
import deezer.kustom.KustomExportGenerics
import deezer.kustom.KustomGenerics
import deezer.kustom.compiler.js.ClassDescriptor
import deezer.kustom.compiler.js.EnumDescriptor
import deezer.kustom.compiler.js.InterfaceDescriptor
import deezer.kustom.compiler.js.SealedClassDescriptor
import deezer.kustom.compiler.js.pattern.`class`.transform
import deezer.kustom.compiler.js.pattern.`interface`.transform
import deezer.kustom.compiler.js.pattern.enum.transform
import deezer.kustom.compiler.js.pattern.parseClass

// Trick to share the Logger everywhere without injecting the dependency everywhere
internal lateinit var sharedLogger: KSPLogger

internal object Logger : KSPLogger by sharedLogger

internal object CompilerArgs {
    var erasePackage: Boolean = false
}

@KotlinPoetKspPreview
class ExportCompiler(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    init {
        sharedLogger = environment.logger
    }

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        CompilerArgs.erasePackage = environment.options["erasePackage"] == "true"

        val annotations = listOf(
            KustomExport::class,
            KustomExportGenerics::class
        )

        annotations.flatMap {
            resolver.getSymbolsWithAnnotation(
                annotationName = it.qualifiedName!!,
                inDepth = true
            )
        }
            //.filter { it is KSClassDeclaration || it is KSTypeAlias /*&& it.validate()*/ }
            .forEach {
                devLog("----- Symbol $it")
                it.accept(ExportVisitor(resolver), Unit)
                //it.accept(LoggerVisitor(environment), Unit)
            }

        /*return symbols.filter { !it.validate() }.toList()
            .also { list ->
                list.forEach {
                    devLog("Cannot handle $it $it.")
                }
            }*/
        return emptyList()
    }

    @KotlinPoetKspPreview
    inner class ExportVisitor(val resolver: Resolver) : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            //devLog("----- visitClassDeclaration $classDeclaration - classKind = ${classDeclaration.classKind}")
            parseAndWrite(classDeclaration, emptyList())
        }

        override fun visitFile(file: KSFile, data: Unit) {
            Logger.warn("visitFile ${file.fileName}")
            Logger.warn("annotations ${file.annotations.toList().joinToString { it.shortName.asString() }}")
            file.annotations// All file annotations
                // Get only the KustomExportGenerics one
                .filter { it.annotationType.resolve().declaration.qualifiedName?.asString() == KustomExportGenerics::class.qualifiedName }
                // Pick the first arguments matching 'exportGenerics' and flatmap all entries
                .flatMap { it.arguments.first { it.name?.asString() == KustomExportGenerics::exportGenerics.name }.value as List<KSAnnotation> }
                .forEach { generics ->
//file.annotations.toList()[0].arguments[0].value
                    Logger.warn("generics $generics")
                    val name =
                        generics.arguments.first { it.name?.asString() == KustomGenerics::name.name }.value as String
                    val kClass =
                        generics.arguments.first { it.name?.asString() == KustomGenerics::kClass.name }.value as KSType
                    val typeParameters = generics.arguments
                        .first { it.name?.asString() == KustomGenerics::typeParameters.name }
                        .value as List<KSType>

                    Logger.warn("name $name")
                    Logger.warn("kClass $kClass")
                    Logger.warn("typeParameters $typeParameters")

                    val targetClassDeclaration = kClass.declaration as KSClassDeclaration
                    val targetTypeParameters = targetClassDeclaration.typeParameters
                    val targetTypeNames = typeParameters
                        .map { it.toClassName() }
                        .mapIndexed { index, className -> targetTypeParameters[index].name.asString() to className }

                    // TODO: what if containingFile is null?
                    val sources = if (targetClassDeclaration.containingFile == null) {
                        arrayOf(file)
                    } else {
                        arrayOf(file, targetClassDeclaration.containingFile!!)
                    }
                    parseAndWrite(targetClassDeclaration, targetTypeNames, *sources)

                    /*
                    val qualifiedName = gen.kClass.qualifiedName
                    Logger.warn("qualifiedName $qualifiedName")
                    requireNotNull(qualifiedName)
                    val classDecl = resolver.getClassDeclarationByName(KSNameImpl.getCached(qualifiedName))
                    requireNotNull(classDecl)
                    Logger.warn("classDecl ${classDecl.qualifiedName?.asString() ?: classDecl.simpleName.asString()}")

                    val targetTypeNames: List<Pair<String, TypeName>> =
                        gen.typeParameters.mapIndexed { index, kClass ->
                            classDecl.typeParameters[index].name.asString() to
                                resolver.getClassDeclarationByName(KSNameImpl.getCached(kClass.qualifiedName!!))!!
                                    .toClassName()
                        }
                    Logger.warn("targetTypeNames $targetTypeNames")
                    parseClass(classDecl, targetTypeNames)
*/
                }
        }

        override fun visitTypeAlias(typeAlias: KSTypeAlias, data: Unit) {
            Logger.warn("visitTypeAlias - ${typeAlias.name}")
            val target = (typeAlias.type.element?.parent as? KSTypeReference)?.resolve() ?: return
            Logger.warn("visitTypeAlias resolved")
            // targetClassDeclaration is templated
            val targetClassDeclaration = target.declaration as? KSClassDeclaration ?: return

            Logger.warn(typeAlias.toString() + " = " + typeAlias.name.asString()) // TypeAliasLong (probably name.asString() too)

            // Contains "Template" list
            val targetTypeParameters = targetClassDeclaration.typeParameters
            val targetTypeNames = typeAlias.type.element?.typeArguments
                ?.map { it.type!!.resolve().toClassName() }
                ?.mapIndexed { index, className -> targetTypeParameters[index].name.asString() to className }
                ?: return

            parseAndWrite(targetClassDeclaration, targetTypeNames)
        }

        private fun parseAndWrite(
            classDeclaration: KSClassDeclaration,
            targetTypeNames: List<Pair<String, ClassName>>,
            vararg sources: KSFile
        ) {
            val allSources = if (sources.isNotEmpty()) sources else arrayOf(classDeclaration.containingFile!!)
            when (val descriptor = parseClass(classDeclaration, targetTypeNames)) {
                is ClassDescriptor -> descriptor.transform()
                    .writeCode(environment, *allSources)
                is SealedClassDescriptor -> descriptor.transform()
                    .writeCode(environment, *allSources)
                is EnumDescriptor -> descriptor.transform()
                    .writeCode(environment, *allSources)
                is InterfaceDescriptor -> descriptor.transform()
                    .writeCode(environment, *allSources)
                null -> {
                    // Cannot parse this class, parsing error already reported on the parser
                }
            }
        }
    }

    fun devLog(msg: String) {
        println(msg) // for unit tests
        environment.logger.warn(msg) // when compiling other modules
    }
}

@KotlinPoetKspPreview
class ExportCompilerProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) =
        ExportCompiler(environment)
}
