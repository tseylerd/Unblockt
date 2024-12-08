// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.server.analysys.completion.symbols

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.base.KaContextReceiver
import org.jetbrains.kotlin.analysis.api.base.KaContextReceiversOwner
import org.jetbrains.kotlin.analysis.api.renderer.base.KaKeywordsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaAnnotationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaAnnotationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KaAnnotationArgumentsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KaAnnotationListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KaAnnotationQualifierRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.renderers.KaAnnotationUseSiteTargetRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.KaContextReceiversRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.renderers.KaContextReceiverLabelRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.contextReceivers.renderers.KaContextReceiverListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaRendererBodyMemberScopeProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.KaDeclarationModifiersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.impl.KaDeclarationModifiersRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaModifierListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KaTypeParametersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.*
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens

@OptIn(KaExperimentalApi::class)
object Renderers {
    object Annotations {
        object UseSite {
            val NONE = object : KaAnnotationUseSiteTargetRenderer {
                override fun renderUseSiteTarget(analysisSession: KaSession, annotation: KaAnnotation, owner: KaAnnotated, annotationRenderer: KaAnnotationRenderer, printer: PrettyPrinter) {
                }
            }
        }
        object QualifiedName {
            val NONE = object : KaAnnotationQualifierRenderer {
                override fun renderQualifier(analysisSession: KaSession, annotation: KaAnnotation, owner: KaAnnotated, annotationRenderer: KaAnnotationRenderer, printer: PrettyPrinter) {
                }
            }
        }
        object List {
            val NONE = object : KaAnnotationListRenderer {
                override fun renderAnnotations(analysisSession: KaSession, owner: KaAnnotated, annotationRenderer: KaAnnotationRenderer, printer: PrettyPrinter) {
                }
            }
        }
        val NONE = KaAnnotationRendererForSource.WITH_SHORT_NAMES.with {
            annotationListRenderer = List.NONE
            annotationsQualifiedNameRenderer = QualifiedName.NONE
            annotationUseSiteTargetRenderer = UseSite.NONE
            annotationArgumentsRenderer = KaAnnotationArgumentsRenderer.NONE
        }
    }
    object SyntheticJavaProperty {
        val COMPLETION = object : KaSyntheticJavaPropertySymbolRenderer {
            override fun renderSymbol(analysisSession: KaSession, symbol: KaSyntheticJavaPropertySymbol, declarationRenderer: KaDeclarationRenderer, printer: PrettyPrinter) {
                printer {
                    val mutabilityKeyword = if (symbol.isVal) KtTokens.VAL_KEYWORD else KtTokens.VAR_KEYWORD
                    declarationRenderer.callableSignatureRenderer.renderCallableSignature(analysisSession, symbol, mutabilityKeyword, declarationRenderer, printer)
                }
            }
        }
    }
    object Declaration {
        object Modifiers {
            val NONE = KaDeclarationModifiersRendererForSource.NO_IMPLICIT_MODIFIERS.with {
                modifierListRenderer = object : KaModifierListRenderer {
                    override fun renderModifiers(analysisSession: KaSession, symbol: KaDeclarationSymbol, declarationModifiersRenderer: KaDeclarationModifiersRenderer, printer: PrettyPrinter) {
                    }
                }
                keywordsRenderer = KaKeywordsRenderer.NONE
            }
        }
        object CallableReceiver {
            val NONE = object : KaCallableReceiverRenderer {
                override fun renderReceiver(analysisSession: KaSession, symbol: KaReceiverParameterSymbol, declarationRenderer: KaDeclarationRenderer, printer: PrettyPrinter) {
                }
            }
        }

        object ContextReceiver {
            val NONE = KaContextReceiversRenderer {
                contextReceiverListRenderer = ContextReceiver.List.NONE
                contextReceiverLabelRenderer = ContextReceiver.Label.NONE
            }

            object List {
                val NONE = object : KaContextReceiverListRenderer {
                    override fun renderContextReceivers(analysisSession: KaSession, owner: KaContextReceiversOwner, contextReceiversRenderer: KaContextReceiversRenderer, typeRenderer: KaTypeRenderer, printer: PrettyPrinter) {
                    }
                }
            }

            object Label {
                val NONE = object : KaContextReceiverLabelRenderer {
                    override fun renderLabel(analysisSession: KaSession, contextReceiver: KaContextReceiver, contextReceiversRenderer: KaContextReceiversRenderer, printer: PrettyPrinter) {
                    }
                }
            }
        }

        object NamedFunction {
            object CallableSignature {
                val COMPLETION = object : KaCallableSignatureRenderer {
                    override fun renderCallableSignature(analysisSession: KaSession, symbol: KaCallableSymbol, keyword: KtKeywordToken?, declarationRenderer: KaDeclarationRenderer, printer: PrettyPrinter) {
                        printer {
                            " ".separated(
                                { declarationRenderer.typeParametersRenderer.renderTypeParameters(analysisSession, symbol, declarationRenderer, printer) },
                                {
                                    val receiverSymbol = symbol.receiverParameter
                                    if (receiverSymbol != null) {
                                        withSuffix(".") {
                                            declarationRenderer.callableReceiverRenderer
                                                .renderReceiver(analysisSession, receiverSymbol, declarationRenderer, printer)
                                        }
                                    }

                                    if (symbol is KaNamedSymbol) {
                                        declarationRenderer.nameRenderer.renderName(analysisSession, symbol, declarationRenderer, printer)
                                    }
                                },
                            )
                            declarationRenderer.valueParametersRenderer.renderValueParameters(analysisSession, symbol, declarationRenderer, printer)
                        }
                    }
                }
            }

            val COMPLETION = object : KaNamedFunctionSymbolRenderer {
                override fun renderSymbol(analysisSession: KaSession, symbol: KaNamedFunctionSymbol, declarationRenderer: KaDeclarationRenderer, printer: PrettyPrinter) {
                    NamedFunction.CallableSignature.COMPLETION.renderCallableSignature(analysisSession, symbol, KtTokens.FUN_KEYWORD, declarationRenderer, printer)
                }
            }
        }

        val CONCISE = KaDeclarationRendererForSource.WITH_SHORT_NAMES.with {
            typeParametersRenderer = KaTypeParametersRenderer.NO_TYPE_PARAMETERS
            keywordsRenderer = KaKeywordsRenderer.NONE
            modifiersRenderer = Modifiers.NONE
            annotationRenderer = Annotations.NONE
            syntheticJavaPropertyRenderer = SyntheticJavaProperty.COMPLETION
            callableReceiverRenderer = CallableReceiver.NONE
            contextReceiversRenderer = ContextReceiver.NONE
            namedFunctionRenderer = NamedFunction.COMPLETION
            propertyAccessorsRenderer = KaPropertyAccessorsRenderer.NONE
            bodyMemberScopeProvider = KaRendererBodyMemberScopeProvider.NONE
        }
    }

    object Type {
        val CONCISE = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
            annotationsRenderer = Annotations.NONE
            keywordsRenderer = KaKeywordsRenderer.NONE
        }
    }
}