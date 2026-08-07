// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - Auto-Scan Service
// =========================================================================
//
// Background service that scans documents on save/change (configurable).
// Debounced to avoid excessive API calls — matches VS Code extension behavior.
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
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
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
    private val pendingScan = AtomicReference<Runnable?>(null)
    private var enabled = true
    private var debounceMs = 300L

    private val connectionListener = ApplicationManager.getApplication().messageBus
        .connect(Disposer.newDisposable())

    init {
        // Register document listener for on-save scanning
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
                override fun beforeDocumentSaving(document: Document) {
                    if (!enabled) return
                    scheduleScan(document)
                }
            })
    }

    /**
     * Enable or disable auto-scanning.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Set the debounce interval in milliseconds.
     */
    fun setDebounceMs(ms: Long) {
        this.debounceMs = ms
    }

    /**
     * Manually trigger a scan of the given document.
     */
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

            // Trigger re-annotation of the current file
            triggerReAnnotation(document)
        } catch (e: Exception) {
            logger.debug("Rampart scan failed: ${e.message}")
        }
    }

    /**
     * Set the Rampart proxy URL.
     */
    fun setUrl(url: String) {
        client.setUrl(url)
    }

    override fun dispose() {
        scheduler.shutdownNow()
    }

    // =========================================================================
    // Private
    // =========================================================================

    private fun scheduleScan(document: Document) {
        val scan = Runnable { scanNow(document) }

        // Replace pending scan (debounce: only latest document gets scanned)
        val old = pendingScan.getAndSet(scan)
        if (old != null) {
            // Already have a pending scan — it will be replaced
        }

        // Schedule execution after debounce period
        scheduler.schedule({
            val current = pendingScan.getAndSet(null)
            current?.run()
        }, debounceMs, TimeUnit.MILLISECONDS)
    }

    private fun triggerReAnnotation(document: Document) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val psiFile = PsiManager.getInstance(project).findFile(
                FileDocumentManager.getInstance().getFile(document) ?: return@invokeLater
            ) ?: return@invokeLater

            DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
        }
    }
}