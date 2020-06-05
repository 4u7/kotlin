/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.parameterInfo.custom

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.hints.InlayParameterHintsExtension
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage

class KotlinCodeHintsPass(private val myRootElement: PsiElement, editor: Editor) :
    EditorBoundHighlightingPass(editor, myRootElement.containingFile, true) {

    override fun doCollectInformation(progress: ProgressIndicator) {
        if (myFile.language != KotlinLanguage.INSTANCE) return
        if (myDocument == null) return

        val kotlinCodeHintsModel = KotlinCodeHintsModel.getInstance(myRootElement.project)

        val provider = InlayParameterHintsExtension.forLanguage(KotlinLanguage.INSTANCE)
        if (provider == null || !provider.canShowHintsWhenDisabled() && !isEnabled || DiffUtil.isDiffEditor(myEditor)) {
            kotlinCodeHintsModel.removeAll(myDocument)
            return
        }
    }

    override fun doApplyInformationToEditor() {
        // Information will be painted with org.jetbrains.kotlin.idea.parameterInfo.custom.ReturnHintLinePainter
    }

    /**
     * Adding hints on the borders of root element (at startOffset or endOffset)
     * is allowed only in the case when root element is a document
     *
     * @return true iff a given offset can be used for hint rendering
     */
    private fun canShowHintsAtOffset(offset: Int): Boolean {
        val rootRange = myRootElement.textRange

        if (rootRange.startOffset < offset && offset < rootRange.endOffset) {
            return true
        }

        return myDocument != null && myDocument.textLength == rootRange.length
    }


    class Registrar : TextEditorHighlightingPassFactoryRegistrar {
        override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
            registrar.registerTextEditorHighlightingPass(
                Factory(),
                null,
                null,
                false,
                -1
            )
        }
    }

    class Factory : TextEditorHighlightingPassFactory {
        override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
            if (file.language != KotlinLanguage.INSTANCE) return null
            return KotlinCodeHintsPass(file, editor)
        }
    }

    companion object {
        private val isEnabled: Boolean
            get() =
                EditorSettingsExternalizable.getInstance().isShowParameterNameHints
    }
}