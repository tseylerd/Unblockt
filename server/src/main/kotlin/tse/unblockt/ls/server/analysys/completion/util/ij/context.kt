// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package tse.unblockt.ls.server.analysys.completion.util.ij

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import tse.unblockt.ls.server.analysys.completion.provider.impl.ij.isInsideAnnotationEntryArgumentList
import tse.unblockt.ls.server.analysys.completion.provider.impl.ij.isJavaSourceOrLibrary
import tse.unblockt.ls.server.analysys.psi.languageVersionSettings


sealed class KotlinRawPositionContext {
    abstract val position: PsiElement
}

class KotlinClassifierNamePositionContext(
    override val position: PsiElement,
    val classLikeDeclaration: KtClassLikeDeclaration,
) : KotlinRawPositionContext()

sealed class KotlinValueParameterPositionContext : KotlinRawPositionContext() {
    abstract val ktParameter: KtParameter
}

class KotlinSimpleParameterPositionContext(
    override val position: PsiElement,
    override val ktParameter: KtParameter,
) : KotlinValueParameterPositionContext()

class KotlinPrimaryConstructorParameterPositionContext(
    override val position: PsiElement,
    override val ktParameter: KtParameter,
) : KotlinValueParameterPositionContext()

class KotlinIncorrectPositionContext(
    override val position: PsiElement
) : KotlinRawPositionContext()

class KotlinTypeConstraintNameInWhereClausePositionContext(
    override val position: PsiElement,
    val typeParametersOwner: KtTypeParameterListOwner
) : KotlinRawPositionContext()

sealed class KotlinNameReferencePositionContext : KotlinRawPositionContext() {
    abstract val reference: KtReference
    abstract val nameExpression: KtElement
    abstract val explicitReceiver: KtElement?

    abstract fun getName(): Name
}

sealed class KotlinSimpleNameReferencePositionContext : KotlinNameReferencePositionContext() {
    abstract override val reference: KtSimpleNameReference
    abstract override val nameExpression: KtSimpleNameExpression
    abstract override val explicitReceiver: KtExpression?

    override fun getName(): Name = nameExpression.getReferencedNameAsName()
}

class KotlinImportDirectivePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
) : KotlinSimpleNameReferencePositionContext()

class KotlinPackageDirectivePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
) : KotlinSimpleNameReferencePositionContext()


class KotlinTypeNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val typeReference: KtTypeReference?,
) : KotlinSimpleNameReferencePositionContext()

class KotlinAnnotationTypeNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val annotationEntry: KtAnnotationEntry,
) : KotlinSimpleNameReferencePositionContext()

class KotlinSuperTypeCallNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val superExpression: KtSuperExpression,
) : KotlinSimpleNameReferencePositionContext()

class KotlinSuperReceiverNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val superExpression: KtSuperExpression,
) : KotlinSimpleNameReferencePositionContext()

class KotlinExpressionNameReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?
) : KotlinSimpleNameReferencePositionContext()

class KotlinInfixCallPositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?
) : KotlinSimpleNameReferencePositionContext()


class KotlinWithSubjectEntryPositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?,
    val subjectExpression: KtExpression,
    val whenCondition: KtWhenCondition,
) : KotlinSimpleNameReferencePositionContext()

class KotlinCallableReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtSimpleNameExpression,
    override val explicitReceiver: KtExpression?
) : KotlinSimpleNameReferencePositionContext()

class KotlinLabelReferencePositionContext(
    override val position: PsiElement,
    override val reference: KtSimpleNameReference,
    override val nameExpression: KtLabelReferenceExpression,
    override val explicitReceiver: KtExpression?,
) : KotlinSimpleNameReferencePositionContext()

class KotlinMemberDeclarationExpectedPositionContext(
    override val position: PsiElement,
    val classBody: KtClassBody
) : KotlinRawPositionContext()

sealed class KDocNameReferencePositionContext : KotlinNameReferencePositionContext() {
    abstract override val reference: KDocReference
    abstract override val nameExpression: KDocName
    abstract override val explicitReceiver: KDocName?

    override fun getName(): Name = nameExpression.getQualifiedNameAsFqName().shortName()
}

class KDocParameterNamePositionContext(
    override val position: PsiElement,
    override val reference: KDocReference,
    override val nameExpression: KDocName,
    override val explicitReceiver: KDocName?,
) : KDocNameReferencePositionContext()

class KDocLinkNamePositionContext(
    override val position: PsiElement,
    override val reference: KDocReference,
    override val nameExpression: KDocName,
    override val explicitReceiver: KDocName?,
) : KDocNameReferencePositionContext()

class KotlinUnknownPositionContext(
    override val position: PsiElement
) : KotlinRawPositionContext()

object KotlinPositionContextDetector {
    fun detect(position: PsiElement): KotlinRawPositionContext {
        return detectForPositionWithSimpleNameReference(position)
            ?: detectForPositionWithKDocReference(position)
            ?: detectForPositionWithoutReference(position)
            ?: KotlinUnknownPositionContext(position)
    }

    private fun detectForPositionWithoutReference(position: PsiElement): KotlinRawPositionContext? {
        val parent = position.parent ?: return null
        val grandparent = parent.parent
        return when {
            parent is KtClassLikeDeclaration && parent.nameIdentifier == position -> {
                KotlinClassifierNamePositionContext(position, parent)
            }

            parent is KtParameter -> {
                if (parent.ownerFunction is KtPrimaryConstructor) {
                    KotlinPrimaryConstructorParameterPositionContext(position, parent)
                } else {
                    KotlinSimpleParameterPositionContext(position, parent)
                }
            }

            parent is PsiErrorElement && grandparent is KtClassBody -> {
                KotlinMemberDeclarationExpectedPositionContext(position, grandparent)
            }

            else -> null
        }
    }

    private fun detectForPositionWithSimpleNameReference(position: PsiElement): KotlinRawPositionContext? {
        val reference = (position.parent as? KtSimpleNameExpression)?.mainReference
            ?: return null
        val nameExpression = reference.expression
        val explicitReceiver = nameExpression.getReceiverExpression()
        val parent = nameExpression.parent
        val subjectExpressionForWhenCondition = (parent as? KtWhenCondition)?.getSubjectExpression()

        return when {
            parent is KtUserType -> {
                detectForTypeContext(parent, position, reference, nameExpression, explicitReceiver)
            }

            parent is KtCallableReferenceExpression -> {
                KotlinCallableReferencePositionContext(
                    position, reference, nameExpression, parent.receiverExpression
                )
            }

            parent is KtWhenCondition && subjectExpressionForWhenCondition != null -> {
                KotlinWithSubjectEntryPositionContext(
                    position,
                    reference,
                    nameExpression,
                    explicitReceiver,
                    subjectExpressionForWhenCondition,
                    whenCondition = parent,
                )
            }

            nameExpression.isReferenceExpressionInImportDirective() -> {
                KotlinImportDirectivePositionContext(
                    position,
                    reference,
                    nameExpression,
                    explicitReceiver,
                )
            }

            nameExpression.isNameExpressionInsidePackageDirective() -> {
                KotlinPackageDirectivePositionContext(
                    position,
                    reference,
                    nameExpression,
                    explicitReceiver,
                )
            }

            parent is KtTypeConstraint -> KotlinTypeConstraintNameInWhereClausePositionContext(
                position,
                position.parentOfType()!!,
            )

            parent is KtBinaryExpression && parent.operationReference == nameExpression -> {
                KotlinInfixCallPositionContext(
                    position, reference, nameExpression, explicitReceiver
                )
            }

            explicitReceiver is KtSuperExpression -> KotlinSuperReceiverNameReferencePositionContext(
                position,
                reference,
                nameExpression,
                explicitReceiver,
                explicitReceiver
            )

            nameExpression is KtLabelReferenceExpression -> {
                KotlinLabelReferencePositionContext(position, reference, nameExpression, explicitReceiver)
            }

            else -> {
                KotlinExpressionNameReferencePositionContext(position, reference, nameExpression, explicitReceiver)
            }
        }
    }

    private fun detectForPositionWithKDocReference(position: PsiElement): KotlinNameReferencePositionContext? {
        val kDocName = position.getStrictParentOfType<KDocName>() ?: return null
        val kDocLink = kDocName.getStrictParentOfType<KDocLink>() ?: return null
        val kDocReference = kDocName.mainReference
        val kDocNameQualifier = kDocName.getQualifier()

        return when (kDocLink.getTagIfSubject()?.knownTag) {
            KDocKnownTag.PARAM -> KDocParameterNamePositionContext(position, kDocReference, kDocName, kDocNameQualifier)
            else -> KDocLinkNamePositionContext(position, kDocReference, kDocName, kDocNameQualifier)
        }
    }

    private fun KtWhenCondition.getSubjectExpression(): KtExpression? {
        val whenEntry = (parent as? KtWhenEntry) ?: return null
        val whenExpression = whenEntry.parent as? KtWhenExpression ?: return null
        return whenExpression.subjectExpression
    }

    private tailrec fun KtExpression.isReferenceExpressionInImportDirective(): Boolean = when (val parent = parent) {
        is KtImportDirective -> parent.importedReference == this
        is KtDotQualifiedExpression -> parent.isReferenceExpressionInImportDirective()

        else -> false
    }

    private tailrec fun KtExpression.isNameExpressionInsidePackageDirective(): Boolean = when (val parent = parent) {
        is KtPackageDirective -> parent.packageNameExpression == this
        is KtDotQualifiedExpression -> parent.isNameExpressionInsidePackageDirective()

        else -> false
    }

    private fun detectForTypeContext(
        userType: KtUserType,
        position: PsiElement,
        reference: KtSimpleNameReference,
        nameExpression: KtSimpleNameExpression,
        explicitReceiver: KtExpression?
    ): KotlinRawPositionContext {
        val typeReference = (userType.parent as? KtTypeReference)?.takeIf { it.typeElement == userType }
        val typeReferenceOwner = typeReference?.parent
        return when {
            typeReferenceOwner is KtConstructorCalleeExpression -> {
                val constructorCall = typeReferenceOwner.takeIf { it.typeReference == typeReference }
                val annotationEntry = (constructorCall?.parent as? KtAnnotationEntry)?.takeIf { it.calleeExpression == constructorCall }
                annotationEntry?.let {
                    KotlinAnnotationTypeNameReferencePositionContext(position, reference, nameExpression, explicitReceiver, it)
                }
            }

            typeReferenceOwner is KtSuperExpression -> {
                val superTypeCallEntry = typeReferenceOwner.takeIf { it.superTypeQualifier == typeReference }
                superTypeCallEntry?.let {
                    KotlinSuperTypeCallNameReferencePositionContext(position, reference, nameExpression, explicitReceiver, it)
                }
            }

            typeReferenceOwner is KtTypeConstraint && typeReferenceOwner.children.any { it is PsiErrorElement } -> {
                KotlinIncorrectPositionContext(position)
            }

            else -> null
        } ?: KotlinTypeNameReferencePositionContext(position, reference, nameExpression, explicitReceiver, typeReference)
    }
}


context(KaSession)
fun KotlinExpressionNameReferencePositionContext.allowsOnlyNamedArguments(): Boolean {
    if (explicitReceiver != null) return false

    val valueArgument = findValueArgument(nameExpression) ?: return false
    val valueArgumentList = valueArgument.parent as? KtValueArgumentList ?: return false
    val callElement = valueArgumentList.parent as? KtCallElement ?: return false

    if (valueArgument.getArgumentName() != null) return false

    val call = callElement.resolveToCall()?.singleCallOrNull<KaFunctionCall<*>>() ?: return false

    if (isJavaArgumentWithNonDefaultName(
            call.partiallyAppliedSymbol.signature,
            call.argumentMapping,
            valueArgument
        )
    ) return true

    val firstArgumentInNamedMode = firstArgumentInNamedMode(
        callElement,
        call.partiallyAppliedSymbol.signature,
        call.argumentMapping,
        callElement.languageVersionSettings
    ) ?: return false

    return with(valueArgumentList.arguments) { indexOf(valueArgument) >= indexOf(firstArgumentInNamedMode) }
}

private fun findValueArgument(expression: KtExpression): KtValueArgument? {
    return expression.parent as? KtValueArgument ?: expression.parent.parent as? KtValueArgument
}

context(KaSession)
private fun isJavaArgumentWithNonDefaultName(
    signature: KaFunctionSignature<*>,
    argumentMapping: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>>,
    currentArgument: KtValueArgument,
): Boolean {
    if (!currentArgument.isInsideAnnotationEntryArgumentList()) return false

    if (!signature.symbol.origin.isJavaSourceOrLibrary()) return false

    val parameter = argumentMapping[currentArgument.getArgumentExpression()] ?: return false
    return parameter.name != JvmAnnotationNames.DEFAULT_ANNOTATION_MEMBER_NAME
}

context(KaSession)
private fun firstArgumentInNamedMode(
    sourceCallElement: KtCallElement,
    signature: KaFunctionSignature<*>,
    argumentMapping: Map<KtExpression, KaVariableSignature<KaValueParameterSymbol>>,
    languageVersionSettings: LanguageVersionSettings
): KtValueArgument? {
    val valueArguments = sourceCallElement.valueArgumentList?.arguments ?: return null
    val parameterToIndex = mapParametersToIndices(signature)
    val supportsMixedNamedArguments = languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)

    var afterNonNamedVararg = false

    for ((argumentIndex, valueArgument) in valueArguments.withIndex()) {
        val parameter = argumentMapping[valueArgument.getArgumentExpression()]
        val parameterIndex = parameterToIndex[parameter]
        val isVararg = parameter?.symbol?.isVararg

        if (valueArgument.isNamed()) {
            if (parameterIndex == null || parameterIndex != argumentIndex || !supportsMixedNamedArguments) return valueArgument
        }
        if (isVararg == false && afterNonNamedVararg) return valueArgument

        if (isVararg == true && !valueArgument.isNamed()) afterNonNamedVararg = true
    }
    return null
}

context(KaSession)
private fun mapParametersToIndices(
    signature: KaFunctionSignature<*>
): Map<KaVariableSignature<KaValueParameterSymbol>, Int> {
    val valueParameters = signature.valueParameters
    return valueParameters.mapIndexed { index, parameter -> parameter to index }.toMap()
}