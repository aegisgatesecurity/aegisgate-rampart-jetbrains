// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - Editor Annotator
// =========================================================================
//
// Highlights detected PII/secrets/XSS in the editor with severity icons.
// Uses ExternalAnnotator for background scanning — see RampartExternalAnnotator.
//
// Privacy: Only sends document text to localhost Rampart proxy.
// No PII stored. No external communications.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Annotates detected threats in the editor.
 *
 * NOTE: This Annotator is registered but the primary scanning is done
 * by RampartExternalAnnotator (which runs in a background thread).
 * This Annotator provides immediate feedback for short text segments.
 */
class RampartAnnotator : Annotator {

    private val client = RampartClient()

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only annotate text-containing elements
        val text = element.text ?: return
        if (text.isBlank() || text.length < 10) return

        // Don't scan on every keystroke — let ExternalAnnotator handle it
        // This Annotator is intentionally lightweight; real scanning happens
        // in RampartExternalAnnotator via collectInformation/doAnnotate/apply
    }
}