// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

@file:Suppress("FunctionName", "UnstableApiUsage")
@file:OptIn(KaExperimentalApi::class)

package tse.unblockt.ls.server.analysys.completion.util

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.types.Variance
import tse.unblockt.ls.protocol.*
import tse.unblockt.ls.server.Commands
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest
import tse.unblockt.ls.server.analysys.completion.imports.AutoImportAction
import tse.unblockt.ls.server.analysys.completion.provider.ij.CallableInsertionOptions
import tse.unblockt.ls.server.analysys.completion.provider.ij.CallableInsertionStrategy
import tse.unblockt.ls.server.analysys.completion.provider.ij.detectCallableOptions
import tse.unblockt.ls.server.analysys.completion.symbols.Renderers

context(KaSession)
fun ProperCompletionItem(
    request: LsCompletionRequest,
    symbol: KaNamedSymbol,
): CompletionItem = when (symbol) {
    is KaCallableSymbol -> CallableCompletionItem(
        request,
        signature = symbol.asSignature(),
        options = detectCallableOptions(request, symbol)
    )

    is KaClassLikeSymbol -> ClassCompletionItem(request, symbol, AutoImportAction.ofClassLike(symbol))

    is KaTypeParameterSymbol -> TypeParameterCompletionItem(symbol)
    else -> throw IllegalArgumentException("Cannot create a lookup element for $symbol")
}

context(KaSession)
fun CallableCompletionItem(
    request: LsCompletionRequest,
    signature: KaCallableSignature<*>,
    options: CallableInsertionOptions
): CompletionItem {
    val symbol = signature.symbol
    val label = symbol.render(Renderers.Declaration.CONCISE)
    val insertText = functionInsertText(signature, options)
    return CompletionItem(
        label = label,
        insertTextFormat = InsertTextFormat.SNIPPET,
        insertText = insertText,
        data = arguments(request, signature.symbol, options),
        kind = CompletionItemKind.FUNCTION,
        deprecated = symbol.deprecationStatus != null,
        labelDetails = CompletionItemLabelDetails(
            detail = symbol.callableId?.packageName?.asString()?.let { " $it" },
            description = symbol.returnType.render(Renderers.Type.CONCISE, Variance.INVARIANT),
        ),
        command = command(options, signature.symbol, request)
    )
}

context(KaSession)
fun ClassCompletionItem(
    request: LsCompletionRequest,
    signature: KaClassLikeSymbol,
    importStrategy: AutoImportAction
): CompletionItem {
    val label = signature.name.toString()
    return CompletionItem(
        label = label,
        insertText = insertTextOrLabel(importStrategy, label),
        kind = CompletionItemKind.CLASS,
        data = arguments(request, signature, CallableInsertionOptions(importStrategy, CallableInsertionStrategy.AsIdentifier)),
        deprecated = signature.deprecationStatus != null,
        labelDetails = CompletionItemLabelDetails(
            detail = signature.classId?.packageFqName?.asString()?.let { " $it" },
            description = signature.render(Renderers.Declaration.CONCISE),
        ),
        command = command(CallableInsertionOptions(importStrategy, CallableInsertionStrategy.AsIdentifier), signature, request)
    )
}

context(KaSession)
fun TypeParameterCompletionItem(signature: KaTypeParameterSymbol): CompletionItem {
    return CompletionItem(signature.name.toString(), kind = CompletionItemKind.TYPEPARAMETER)
}

context(KaSession)
fun ClassifierCompletionItem(
    request: LsCompletionRequest,
    symbol: KaClassifierSymbol,
    importingStrategy: AutoImportAction,
): CompletionItem? {
    if (symbol !is KaNamedSymbol) return null

    val lookup = when (symbol) {
        is KaClassLikeSymbol -> ClassCompletionItem(request, symbol, importingStrategy)
        is KaTypeParameterSymbol -> ProperCompletionItem(request, symbol)
    }
    return lookup
}

fun KeywordCompletionItem(label: String): CompletionItem {
    return CompletionItem(
        label,
        insertText = label,
        kind = CompletionItemKind.KEYWORD
    )
}

private fun insertTextOrLabel(
    importAction: AutoImportAction,
    label: String
) = when (importAction) {
    is AutoImportAction.UseFullNameAndShorten -> importAction.nameToImport.asString()
    else -> label
}

private fun functionInsertText(
    signature: KaCallableSignature<*>,
    options: CallableInsertionOptions
): String {
    val fqName = insertTextOrLabel(options.action, signature.symbol.name.toString())
    return fqName + functionParametersText(signature.symbol, options.insertionStrategy)
}

private fun functionParametersText(
    symbol: KaSymbol,
    options: CallableInsertionStrategy
): String {
    return when (options) {
        CallableInsertionStrategy.AsCall -> {
            when(symbol) {
                is KaFunctionSymbol -> {
                    when {
                        shouldBeLambdaOutsideParenthesis(symbol) -> " { $0 }"
                        else -> when {
                            symbol.valueParameters.isEmpty() -> "()"
                            else -> "($0)"
                        }
                    }
                }
                else -> "()"
            }
        }
        else -> ""
    }
}

private fun shouldBeLambdaOutsideParenthesis(symbol: KaFunctionSymbol): Boolean {
    val valueParameters = symbol.valueParameters
    if (valueParameters.isEmpty()) return false
    if (valueParameters.size == 1) {
        return valueParameters.first().returnType is KaFunctionType
    }

    val last = valueParameters.last()
    if (last.returnType !is KaFunctionType) {
        return false
    }
    val other = valueParameters.dropLast(1)
    return other.all { it.hasDefaultValue }
}

private fun command(
    options: CallableInsertionOptions,
    symbol: KaSymbol,
    request: LsCompletionRequest
) = when (options.action) {
    is AutoImportAction.UseFullNameAndShorten, is AutoImportAction.AddImport -> Commands.handleInsert.copy(
        arguments = arguments(request, symbol, options)?.let { listOf(it) }
    )

    else -> null
}

private fun arguments(request: LsCompletionRequest, symbol: KaSymbol, options: CallableInsertionOptions): AdditionalCompletionData? {
    val start = request.offset - request.prefix.length
    val data = when (options.action) {
        is AutoImportAction.UseFullNameAndShorten -> {
            AdditionalCompletionData(
                document = request.uri,
                start,
                request.offset,
                shortenReference = ShortenReferenceData(
                    packageName = options.action.packageName.asString(),
                    shortenName = options.action.nameToImport.shortName().asString() + functionParametersText(symbol, options.insertionStrategy)
                )
            )
        }
        is AutoImportAction.AddImport -> AdditionalCompletionData(
            request.uri,
            start,
            request.offset,
            addImport = AddImportData(
                options.action.packageName.asString(),
                options.action.nameToImport.asString()
            )
        )
        else -> null
    }
    return data
}