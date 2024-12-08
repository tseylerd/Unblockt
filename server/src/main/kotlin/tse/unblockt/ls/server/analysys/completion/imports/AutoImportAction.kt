// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.completion.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.name.FqName
import tse.unblockt.ls.server.analysys.completion.LsCompletionRequest

sealed class AutoImportAction {
    companion object {
        context(KaSession)
        fun ofCallable(request: LsCompletionRequest, symbol: KaCallableSymbol, isFunctionalVariableCall: Boolean = false): AutoImportAction {
            val symbolInsideObject = (symbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol)?.classKind?.isObject == true
            if (!symbolInsideObject && symbol.location == KaSymbolLocation.CLASS) {
                return DoNothing
            }
            val packageName = symbol.callableId?.packageName
            if (packageName == request.file.packageFqName) {
                return DoNothing
            }

            val callableId = symbol.callableId ?: return DoNothing
            val callableIdAsSingle = callableId.asSingleFqName()

            return when {
                symbol.isExtension || isFunctionalVariableCall && (symbol.returnType as? KaFunctionType)?.hasReceiver == true -> AddImport(callableId.packageName, callableIdAsSingle)
                else -> UseFullNameAndShorten(callableId.packageName, callableIdAsSingle)
            }
        }

        context (KaSession)
        fun ofClassLike(symbol: KaClassifierSymbol): AutoImportAction {
            if (symbol !is KaClassLikeSymbol) return DoNothing

            val classId = symbol.classId ?: return DoNothing

            val classIdAsSingle = classId.asSingleFqName()
            return UseFullNameAndShorten(classId.packageFqName, classIdAsSingle)
        }
    }

    data object DoNothing : AutoImportAction()
    data class AddImport(val packageName: FqName, val nameToImport: FqName) : AutoImportAction()
    data class UseFullNameAndShorten(val packageName: FqName, val nameToImport: FqName) : AutoImportAction()
}