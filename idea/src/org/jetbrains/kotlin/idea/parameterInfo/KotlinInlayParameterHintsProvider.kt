/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.parameterInfo

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

enum class HintType(val showDesc: String, val doNotShowDesc: String, defaultEnabled: Boolean) {

    PROPERTY_HINT(
        KotlinBundle.message("hints.title.property.type.enabled"),
        KotlinBundle.message("hints.title.property.type.disabled"),
        false
    ) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            return providePropertyTypeHint(elem)
        }

        override fun isApplicable(elem: PsiElement): Boolean = elem is KtProperty && elem.getReturnTypeReference() == null && !elem.isLocal
    },

    LOCAL_VARIABLE_HINT(
        KotlinBundle.message("hints.title.locals.type.enabled"),
        KotlinBundle.message("hints.title.locals.type.disabled"),
        false
    ) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            return providePropertyTypeHint(elem)
        }

        override fun isApplicable(elem: PsiElement): Boolean =
            (elem is KtProperty && elem.getReturnTypeReference() == null && elem.isLocal) ||
                    (elem is KtParameter && elem.isLoopParameter && elem.typeReference == null) ||
                    (elem is KtDestructuringDeclarationEntry && elem.getReturnTypeReference() == null)
    },

    FUNCTION_HINT(
        KotlinBundle.message("hints.title.function.type.enabled"),
        KotlinBundle.message("hints.title.function.type.disabled"),
        false
    ) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            (elem as? KtNamedFunction)?.let { namedFunc ->
                namedFunc.valueParameterList?.let { paramList ->
                    return provideTypeHint(namedFunc, paramList.endOffset)
                }
            }
            return emptyList()
        }

        override fun isApplicable(elem: PsiElement): Boolean =
            elem is KtNamedFunction && !(elem.hasBlockBody() || elem.hasDeclaredReturnType())
    },

    PARAMETER_TYPE_HINT(
        KotlinBundle.message("hints.title.parameter.type.enabled"),
        KotlinBundle.message("hints.title.parameter.type.disabled"),
        false
    ) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            (elem as? KtParameter)?.let { param ->
                param.nameIdentifier?.let { ident ->
                    return provideTypeHint(param, ident.endOffset)
                }
            }
            return emptyList()
        }

        override fun isApplicable(elem: PsiElement): Boolean = elem is KtParameter && elem.typeReference == null && !elem.isLoopParameter
    },

    PARAMETER_HINT(
        KotlinBundle.message("hints.title.argument.name.enabled"),
        KotlinBundle.message("hints.title.argument.name.disabled"),
        true
    ) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val callElement = elem.getStrictParentOfType<KtCallElement>() ?: return emptyList()
            return provideArgumentNameHints(callElement)
        }

        override fun isApplicable(elem: PsiElement): Boolean = elem is KtValueArgumentList
    },

    LAMBDA_RETURN_EXPRESSION(
        KotlinBundle.message("hints.title.return.expression.enabled"),
        KotlinBundle.message("hints.title.return.expression.disabled"),
        true
    ) {
        override fun isApplicable(elem: PsiElement) =
            elem is KtExpression && elem !is KtFunctionLiteral && !elem.isNameReferenceInCall()

        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            // Will be painted with ReturnHintLinePainter

            // Enable/Disable setting will be present in the list with other hints.
            // Enable action will be provided by the platform.
            // Disable action need to be reimplemented as hints are not actually added, see DisableReturnLambdaHintOptionAction.

            return emptyList()
        }
    },

    LAMBDA_IMPLICIT_PARAMETER_RECEIVER(
        KotlinBundle.message("hints.title.implicit.parameters.enabled"),
        KotlinBundle.message("hints.title.implicit.parameters.disabled"),
        true
    ) {
        override fun isApplicable(elem: PsiElement) = elem is KtFunctionLiteral

        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            ((elem as? KtFunctionLiteral)?.parent as? KtLambdaExpression)?.let {
                return provideLambdaImplicitHints(it)
            }
            return emptyList()
        }
    },

    SUSPENDING_CALL(
        KotlinBundle.message("hints.title.suspend.calls.enabled"),
        KotlinBundle.message("hints.title.suspend.calls.disabled"),
        false
    ) {
        override fun isApplicable(elem: PsiElement) = elem.isNameReferenceInCall() && ApplicationManager.getApplication().isInternal

        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val callExpression = elem.parent as? KtCallExpression ?: return emptyList()
            return provideSuspendingCallHint(callExpression)?.let { listOf(it) } ?: emptyList()
        }
    };

    companion object {
        fun resolve(elem: PsiElement): HintType? {
            val applicableTypes = values().filter { it.isApplicable(elem) }
            return applicableTypes.firstOrNull()
        }

        fun resolveToEnabled(elem: PsiElement?): HintType? {

            val resolved = elem?.let { resolve(it) } ?: return null
            return if (resolved.enabled) {
                resolved
            } else {
                null
            }
        }
    }

    abstract fun isApplicable(elem: PsiElement): Boolean
    abstract fun provideHints(elem: PsiElement): List<InlayInfo>
    val option = Option("SHOW_${this.name}", this.showDesc, defaultEnabled)
    val enabled
        get() = option.get()
}

@Suppress("UnstableApiUsage")
class KotlinInlayParameterHintsProvider : InlayParameterHintsProvider {

    override fun getSupportedOptions(): List<Option> = HintType.values().map { it.option }

    override fun getDefaultBlackList(): Set<String> =
        setOf(
            "*listOf", "*setOf", "*arrayOf", "*ListOf", "*SetOf", "*ArrayOf", "*assert*(*)", "*mapOf", "*MapOf",
            "kotlin.require*(*)", "kotlin.check*(*)", "*contains*(value)", "*containsKey(key)", "kotlin.lazyOf(value)",
            "*SequenceBuilder.resume(value)", "*SequenceBuilder.yield(value)"
        )

    override fun getHintInfo(element: PsiElement): HintInfo? {
        return when (val hintType = HintType.resolve(element) ?: return null) {
            HintType.PARAMETER_HINT -> {
                val parent = (element as? KtValueArgumentList)?.parent
                (parent as? KtCallElement)?.let { getMethodInfo(it) }
            }
            else -> HintInfo.OptionInfo(hintType.option)
        }
    }

    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        val resolveToEnabled = HintType.resolveToEnabled(element) ?: return emptyList()
        return resolveToEnabled.provideHints(element)
    }

    override fun getBlackListDependencyLanguage(): Language = JavaLanguage.INSTANCE

    override fun getInlayPresentation(inlayText: String): String =
        if (inlayText.startsWith(TYPE_INFO_PREFIX)) {
            inlayText.substring(TYPE_INFO_PREFIX.length)
        } else {
            super.getInlayPresentation(inlayText)
        }

    private fun getMethodInfo(elem: KtCallElement): HintInfo.MethodInfo? {
        val resolvedCall = elem.resolveToCall()
        val resolvedCallee = resolvedCall?.candidateDescriptor
        if (resolvedCallee is FunctionDescriptor) {
            val paramNames =
                resolvedCallee.valueParameters.asSequence().map { it.name }.filter { !it.isSpecial }.map(Name::asString).toList()
            val fqName = if (resolvedCallee is ConstructorDescriptor)
                resolvedCallee.containingDeclaration.fqNameSafe.asString()
            else
                (resolvedCallee.fqNameOrNull()?.asString() ?: return null)
            return HintInfo.MethodInfo(fqName, paramNames)
        }
        return null
    }

    override fun getMainCheckboxText(): String {
        return KotlinBundle.message("hints.title.parameter")
    }
}

fun PsiElement.isNameReferenceInCall() =
    this is KtNameReferenceExpression && parent is KtCallExpression
