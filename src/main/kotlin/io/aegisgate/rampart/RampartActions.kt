// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - Plugin Actions
// =========================================================================
//
// IDE actions: Scan Current File, Check Connection, Open Settings.
// Registered in plugin.xml.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.Messages

/**
 * Scan the current file for PII/secrets/XSS detections.
 */
class ScanCurrentFileAction : AnAction() {
    private val logger = Logger.getInstance(ScanCurrentFileAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val document = editor.document

        val service = project.getService(RampartAutoScan::class.java)
        service.scanNow(document)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Check if the Rampart proxy is reachable and show result.
 */
class CheckConnectionAction : AnAction() {
    private val logger = Logger.getInstance(CheckConnectionAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val client = RampartClient()

        val alive = client.isAlive()
        val message = if (alive) {
            val stats = client.getStats()
            if (stats != null) {
                "✅ Rampart proxy is connected.\n\n" +
                    "Requests: ${stats.totalRequests}\n" +
                    "Intercepted: ${stats.intercepted}\n" +
                    "Detections: ${stats.detections}\n" +
                    "Blocked: ${stats.blockedRequests}\n" +
                    "ML Detections: ${stats.mlDetections}"
            } else {
                "✅ Rampart proxy is connected."
            }
        } else {
            "❌ Rampart proxy is NOT reachable at ${client.getUrl()}.\n\n" +
                "Make sure rampart is running:\n" +
                "  rampart serve"
        }

        Messages.showInfoMessage(project, message, "AegisGate Rampart — Connection Status")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Open Rampart settings.
 */
class OpenSettingsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, RampartSettings::class.java)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}