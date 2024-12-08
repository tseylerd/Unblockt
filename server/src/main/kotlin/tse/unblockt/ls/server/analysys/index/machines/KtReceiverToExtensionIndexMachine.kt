// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.index.machines

import com.google.common.collect.HashMultimap
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import tse.unblockt.ls.server.analysys.index.common.IndexFileEntry
import tse.unblockt.ls.server.analysys.index.model.PsiEntry
import tse.unblockt.ls.server.analysys.storage.DB
import tse.unblockt.ls.server.analysys.storage.PersistentStorage

class KtReceiverToExtensionIndexMachine(project: Project): PsiIndexMachine<String, KtCallableDeclaration>(
    KtCallableDeclaration::class,
    config = DB.Store.Config.UNIQUE_KEY_VALUE,
    attributeName = "receiver_to_extension",
    project
) {
    override fun keyToString(key: String): String {
        return key
    }

    override fun stringToKey(string: String): String {
        return string
    }

    override val namespace: PersistentStorage.Namespace
        get() = Namespaces.ourKotlinNamespace

    override fun support(entry: IndexFileEntry): Boolean {
        return entry.isKotlin && entry.psiFile is KtFile
    }

    override fun producePairs(entry: IndexFileEntry, elements: List<PsiEntry<KtCallableDeclaration>>): List<Pair<String, PsiEntry<KtCallableDeclaration>>> {
        val ktFile = entry.psiFile as KtFile
        return elements.flatMap { el ->
            val receiverType = el.element.receiverTypeReference ?: return@flatMap emptyList()
            val occurences = mutableSetOf<String>()
            receiverType.typeElement!!.index(ktFile, el.element, receiverType) { occ ->
                occurences += occ
            }
            occurences.map { it to el }
        }
    }

    private fun KtTypeElement.index(
        file: KtFile,
        declaration: KtTypeParameterListOwner,
        containingTypeReference: KtTypeReference,
        occurrence: (String) -> Unit
    ) {
        fun KtTypeElement.indexWithVisited(
            declaration: KtTypeParameterListOwner,
            containingTypeReference: KtTypeReference,
            visited: MutableSet<KtTypeElement>,
            occurrence: (String) -> Unit
        ) {
            if (this in visited) return

            visited.add(this)

            when (this) {
                is KtUserType -> {
                    val referenceName = referencedName ?: return

                    val typeParameter = declaration.typeParameters.firstOrNull { it.name == referenceName }
                    if (typeParameter != null) {
                        val bound = typeParameter.extendsBound
                        if (bound != null) {
                            bound.typeElement?.indexWithVisited(declaration, containingTypeReference, visited, occurrence)
                        } else {
                            occurrence("Any")
                        }
                        return
                    }

                    occurrence(referenceName)

                    unwrapImportAlias(file, referenceName).forEach { occurrence(it) }
                }

                is KtNullableType -> innerType?.indexWithVisited(declaration, containingTypeReference, visited, occurrence)

                is KtFunctionType -> {
                    val arity = parameters.size + (if (receiverTypeReference != null) 1 else 0)
                    val suspendPrefix =
                        if (containingTypeReference.modifierList?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true)
                            "Suspend"
                        else
                            ""
                    occurrence("${suspendPrefix}Function$arity")
                }

                is KtDynamicType -> occurrence("Any")

                is KtIntersectionType ->
                    getLeftTypeRef()?.typeElement?.indexWithVisited(declaration, containingTypeReference, visited, occurrence)

                else -> error("Unsupported type: $this")
            }
        }

        indexWithVisited(declaration, containingTypeReference, mutableSetOf(), occurrence)
    }

    fun unwrapImportAlias(file: KtFile, aliasName: String): Collection<String> {
        if (!kotlin.runCatching { file.hasImportAlias() }.getOrElse { false }) return emptyList()

        return importMap(file)[aliasName]
    }

    private fun importMap(file: KtFile): HashMultimap<String, String> {
        return HashMultimap.create<String, String>().apply {
            for (import in kotlin.runCatching { file.importList }.getOrNull()?.imports.orEmpty()) {
                val aliasName = import.aliasName ?: continue
                @Suppress("UnstableApiUsage")
                val name = import.importPath?.fqName?.shortName()?.asString() ?: continue
                put(aliasName, name)
            }
        }
    }
}