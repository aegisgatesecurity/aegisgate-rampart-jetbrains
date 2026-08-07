// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - Auto-Scan Service
// =========================================================================
//
// Background service that scans documents on save (debounced).
// Triggers IntelliJ's external annotator to refresh highlights.
//
// Privacy: Only sends document text to localhost Rampart proxy.
// No PII stored. No external communications.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Auto-scan service: scans documents on save (debounced).
 *
 * Mirrors the VS Code extension's Scanner behavior:
 *   - Debounces rapid changes (300ms default)
 *   - Only scans when Rampart proxy is reachable
 *   - Triggers IntelliJ's external annotator to refresh highlights
 */
class RampartAutoScan(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(RampartAutoScan::class.java)
    private val client = RampartClient()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var enabled = true
    private var debounceMs = 300L

    @Volatile
    private var scheduledFuture: ScheduledFuture<*>? = null

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setDebounceMs(ms: Long) {
        this.debounceMs = ms
    }

    fun scanNow(document: Document) {
        if (!client.isAlive()) {
            logger.debug("Rampart proxy not reachable, skipping scan")
            return
        }

        val text = document.text
        if (text.isBlank() || text.length < 10) return

        try {
            val summary = client.detect(text) ?: return
            logger.info("Rampart scan: ${summary.totalDetections} detection(s)" +
                (if (summary.blocked) ", BLOCKED" else ""))
            triggerReAnnotation(document)
        } catch (e: Exception) {
            logger.debug("Rampart scan failed: ${e.message}")
        }
    }

    fun setUrl(url: String) {
        client.setUrl(url)
    }

    override fun dispose() {
        scheduler.shutdownNow()
    }

    // =========================================================================
    // Private
    // =========================================================================

    fun scheduleScan(document: Document) {
        if (!enabled) return
        scheduledFuture?.cancel(false)
        scheduledFuture = scheduler.schedule({
            scanNow(document)
        }, debounceMs, TimeUnit.MILLISECONDS)
    }

    private fun triggerReAnnotation(document: Document) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@invokeLater
            DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
        }
    }
}