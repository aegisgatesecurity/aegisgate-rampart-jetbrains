// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - External Annotator
// =========================================================================
//
// IntelliJ's ExternalAnnotator runs after internal passes and can add
// highlights based on external tool output. This is the primary way
// Rampart detection results appear in the editor.
//
// Flow: collect() → doAnnotate() → apply()
//   1. collectInformation — sends file text to Rampart /detect
//   2. doAnnotate — passes data through (already collected)
//   3. apply — creates annotations in the editor
//
// Privacy: Only sends document text to localhost. Zero external comms.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * External annotator that sends file text to the Rampart proxy
 * and creates in-editor highlights for detected threats.
 *
 * This runs in a background thread (not EDT), so HTTP calls are safe.
 */
class RampartExternalAnnotator :
    ExternalAnnotator<RampartExternalAnnotator.AnnotationInfo, RampartExternalAnnotator.AnnotationInfo>() {

    private val logger = Logger.getInstance(RampartExternalAnnotator::class.java)

    data class AnnotationInfo(
        val detections: List<DetectionResult>,
        val fileText: String,
    )

    override fun collectInformation(file: PsiFile): AnnotationInfo? {
        val text = file.text
        if (text.isBlank() || text.length < 10) return null

        val settings = RampartSettingsState.getInstance()
        if (!settings.autoScanEnabled) return null

        val client = RampartClient(settings.url)
        if (!client.isAlive()) {
            logger.debug("Rampart proxy not reachable, skipping annotation")
            return null
        }

        val summary = client.detect(text) ?: return null
        if (summary.totalDetections == 0) return null

        val minSeverity = RampartSeverity.fromString(settings.minSeverity)
        val filtered = summary.results.filter {
            RampartSeverity.fromString(it.severity).level >= minSeverity.level
        }

        return AnnotationInfo(detections = filtered, fileText = text)
    }

    override fun doAnnotate(collected: AnnotationInfo?): AnnotationInfo? = collected

    override fun apply(file: PsiFile, annotationResult: AnnotationInfo?, holder: AnnotationHolder) {
        if (annotationResult == null) return

        val fileText = annotationResult.fileText

        for ((idx, result) in annotationResult.detections.withIndex()) {
            if (idx >= 50) break // Cap at 50 annotations to avoid editor lag

            val severity = RampartSeverity.fromString(result.severity)
            val icon = CATEGORY_ICONS[result.category] ?: "⚠️"
            val message = "${icon} ${result.category}: ${result.rule}" +
                (if (result.confidence > 0) " (${(result.confidence * 100).toInt()}%)" else "") +
                (if (result.isThreat) " [THREAT]" else "") +
                (if (result.blocked) " [BLOCKED]" else "") +
                (if (result.mlScore != null && result.mlScore > 0) " ML:${(result.mlScore * 100).toInt()}%" else "")

            val highlightSeverity = when (severity) {
                RampartSeverity.CRITICAL -> HighlightSeverity.ERROR
                RampartSeverity.HIGH -> HighlightSeverity.ERROR
                RampartSeverity.MEDIUM -> HighlightSeverity.WARNING
                RampartSeverity.LOW -> HighlightSeverity.WEAK_WARNING
                RampartSeverity.INFO -> HighlightSeverity.INFORMATION
            }

            // Try to find exact text location
            val detectedText = result.text
            if (detectedText != null && detectedText.isNotBlank()) {
                val startIdx = fileText.indexOf(detectedText)
                if (startIdx >= 0) {
                    holder.newAnnotation(highlightSeverity, message)
                        .range(TextRange(startIdx, startIdx + detectedText.length))
                        .create()
                    continue
                }
            }

            // Fallback: whole-file annotation at line 0
            val doc = file.viewProvider.document
            if (doc != null && doc.lineCount > 0) {
                val lineStart = doc.getLineStartOffset(0)
                val lineEnd = doc.getLineEndOffset(0)
                holder.newAnnotation(highlightSeverity, message)
                    .range(TextRange(lineStart, lineEnd.coerceAtMost(fileText.length)))
                    .create()
            }
        }
    }
}