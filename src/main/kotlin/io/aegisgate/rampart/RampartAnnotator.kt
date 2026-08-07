// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - Editor Annotator
// =========================================================================
//
// Highlights detected PII/secrets/XSS in the editor with severity icons.
// Mirrors the VS Code DiagnosticManager behavior.
//
// Privacy: Only sends document text to localhost Rampart proxy.
// No PII stored. No external communications.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.codeInsight.daemon.HighlightInfoType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Annotates detected threats in the editor.
 *
 * Runs as a standard IntelliJ Annotator — no special permissions needed.
 * Only triggers when the Rampart proxy is reachable on localhost.
 */
class RampartAnnotator : Annotator {

    private val client = RampartClient()

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only annotate text-containing elements
        val text = element.text ?: return
        if (text.isBlank() || text.length < 10) return

        // Query the local Rampart proxy
        val summary = client.detect(text) ?: return
        if (summary.totalDetections == 0) return

        // Filter results: only show medium+ severity (matching VS Code extension)
        val filteredResults = summary.results
            .filter { RampartSeverity.fromString(it.severity).level >= RampartSeverity.MEDIUM.level }
            .sortedByDescending { RampartSeverity.fromString(it.severity).level }

        // Apply annotations for each detection
        val fileText = element.containingFile?.text ?: text
        val elementStart = element.textOffset

        for (result in filteredResults.take(50)) { // Cap at 50 to avoid editor lag
            val severity = RampartSeverity.fromString(result.severity)
            val icon = CATEGORY_ICONS[result.category] ?: "⚠️"
            val message = "${icon} ${result.category}: ${result.rule}" +
                (if (result.confidence > 0) " (${(result.confidence * 100).toInt()}%)" else "") +
                (if (result.isThreat) " [THREAT]" else "") +
                (if (result.blocked) " [BLOCKED]" else "") +
                (if (result.mlScore != null && result.mlScore > 0) " ML:${(result.mlScore * 100).toInt()}%" else "")

            // Try to find the detected text in the element for precise highlighting
            val detectedText = result.text
            val range = if (detectedText != null && detectedText.isNotBlank()) {
                findTextRange(fileText, detectedText, elementStart, element.textLength)
            } else null

            val highlightSeverity = when (severity) {
                RampartSeverity.CRITICAL -> HighlightSeverity.ERROR
                RampartSeverity.HIGH -> HighlightSeverity.ERROR
                RampartSeverity.MEDIUM -> HighlightSeverity.WARNING
                RampartSeverity.LOW -> HighlightSeverity.WEAK_WARNING
                RampartSeverity.INFO -> HighlightSeverity.INFORMATION
            }

            val textAttributes = when (severity) {
                RampartSeverity.CRITICAL -> CRITICAL_ATTR
                RampartSeverity.HIGH -> HIGH_ATTR
                RampartSeverity.MEDIUM -> MEDIUM_ATTR
                else -> null // Use default for LOW/INFO
            }

            if (range != null) {
                holder.newAnnotation(highlightSeverity, message)
                    .range(range)
                    .let { b -> if (textAttributes != null) b.textAttributes(textAttributes) else b }
                    .create()
            } else {
                // Line-level annotation if we can't locate the exact text
                holder.newAnnotation(highlightSeverity, message)
                    .onLine(element.documentModel.getLineNumber(elementStart))
                    .create()
            }
        }
    }

    /**
     * Find the text range for detected content within the file.
     * Returns null if the text cannot be found.
     */
    private fun findTextRange(fileText: String, searchText: String, startOffset: Int, length: Int): TextRange? {
        // Search within the element's range first
        val elementText = fileText.substring(startOffset, minOf(startOffset + length, fileText.length))
        val idx = elementText.indexOf(searchText)
        if (idx >= 0) {
            return TextRange(startOffset + idx, startOffset + idx + searchText.length)
        }
        return null
    }

    companion object {
        private val CRITICAL_ATTR = TextAttributesKey.createTextAttributesKey(
            "RAMPART_CRITICAL",
            DefaultLanguageHighlighterColors.CONSTANT
        )
        private val HIGH_ATTR = TextAttributesKey.createTextAttributesKey(
            "RAMPART_HIGH",
            DefaultLanguageHighlighterColors.KEYWORD
        )
        private val MEDIUM_ATTR = TextAttributesKey.createTextAttributesKey(
            "RAMPART_MEDIUM",
            DefaultLanguageHighlighterColors.STRING
        )
    }
}