// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - External Annotator
// =========================================================================
//
// IntelliJ's ExternalAnnotator runs after internal passes and can add
// highlights based on external tool output. This is the primary way
// Rampart detection results appear in the editor.
//
// Flow: collect() → annotate() → apply()
//   1. collect() — sends file text to Rampart /detect, returns summary
//   2. annotate() — converts DetectionSummary to AnnotationInfo list
//   3. apply() — creates annotations in the editor (handled by IntelliJ)
//
// Privacy: Only sends document text to localhost. Zero external comms.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * External annotator that sends file text to the Rampart proxy
 * and creates in-editor highlights for detected threats.
 *
 * This runs in a background thread (not EDT), so HTTP calls are safe.
 */
class RampartExternalAnnotator : ExternalAnnotator<RampartExternalAnnotator.AnnotationInfo, RampartExternalAnnotator.AnnotationInfo>() {

    private val logger = Logger.getInstance(RampartExternalAnnotator::class.java)

    /**
     * Collected detection data for annotation.
     */
    data class AnnotationInfo(
        val detections: List<DetectionResult>,
        val fileText: String,
    )

    // =========================================================================
    // Step 1: Collect — called in background thread
    // =========================================================================

    override fun collectInformation(file: PsiFile): AnnotationInfo? {
        val text = file.text
        if (text.isBlank() || text.length < 10) return null

        // Check if auto-scan is enabled
        val settings = RampartSettingsState.getInstance()
        if (!settings.autoScanEnabled) return null

        val client = RampartClient(settings.url)
        if (!client.isAlive()) {
            logger.debug("Rampart proxy not reachable, skipping annotation")
            return null
        }

        val summary = client.detect(text) ?: return null
        if (summary.totalDetections == 0) return null

        // Filter by minimum severity from settings
        val minSeverity = RampartSeverity.fromString(settings.minSeverity)
        val filtered = summary.results.filter {
            RampartSeverity.fromString(it.severity).level >= minSeverity.level
        }

        return AnnotationInfo(detections = filtered, fileText = text)
    }

    // =========================================================================
    // Step 2: Annotate — called in background thread
    // =========================================================================

    override fun doAnnotate(collected: AnnotationInfo?): AnnotationInfo? {
        // Pass through — we already have the data from collectInformation
        return collected
    }

    // =========================================================================
    // Step 3: Apply — called on EDT
    // =========================================================================

    override fun apply(file: PsiFile, annotationResult: AnnotationInfo?, holder: AnnotationHolder) {
        if (annotationResult == null) return

        val fileText = annotationResult.fileText
        val minSeverity = RampartSeverity.fromString(RampartSettingsState.getInstance().minSeverity)

        for (result in annotationResult.detections.take(50)) {
            val severity = RampartSeverity.fromString(result.severity)
            if (severity.level < minSeverity.level) continue

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
                val idx = fileText.indexOf(detectedText)
                if (idx >= 0) {
                    holder.newAnnotation(highlightSeverity, message)
                        .range(TextRange(idx, idx + detectedText.length))
                        .create()
                    continue
                }
            }

            // Fallback: line-level annotation
            holder.newAnnotation(highlightSeverity, message)
                .onLine(0)
                .create()
        }
    }
}