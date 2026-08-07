// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - Status Bar Widget
// =========================================================================
//
// Shows live connection status and detection stats in the IDE status bar.
// Mirrors the VS Code extension's StatusBarManager behavior.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Alarm
import java.util.concurrent.atomic.AtomicReference

/**
 * Status bar widget showing Rampart proxy connection status.
 *
 * Displays:
 *   - 🔴 Rampart: Disconnected (proxy unreachable)
 *   - 🟢 Rampart: Connected (proxy reachable, shows stats)
 *   - 🟡 Rampart: Checking... (during connection check)
 *
 * Auto-refreshes every 30 seconds (matching VS Code extension).
 */
class RampartStatusBar(private val project: Project) : StatusBarWidget, Disposable {

    private val logger = Logger.getInstance(RampartStatusBar::class.java)
    private val client = RampartClient()
    private val alarm = Alarm(this)
    private val statusText = AtomicReference("🟡 Rampart: Checking...")

    private var refreshIntervalMs = 30_000L

    override fun ID(): String = "RampartStatusBar"

    override fun install() {
        // Initial check
        refreshStatus()

        // Schedule periodic refreshes
        scheduleRefresh()
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return object : StatusBarWidget.TextPresentation {
            override fun getText(): String = statusText.get()
            override fun getTooltipText(): String = "AegisGate Rampart — Local AI Security Proxy"
            override fun getClickAction(): Runnable? = null
        }
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }

    /**
     * Set the Rampart proxy URL.
     */
    fun setUrl(url: String) {
        client.setUrl(url)
        refreshStatus()
    }

    /**
     * Set the refresh interval in milliseconds.
     */
    fun setRefreshIntervalMs(ms: Long) {
        refreshIntervalMs = ms
    }

    // =========================================================================
    // Private
    // =========================================================================

    private fun refreshStatus() {
        try {
            if (client.isAlive()) {
                val stats = client.getStats()
                if (stats != null) {
                    val detections = stats.detections
                    val blocked = stats.blockedRequests
                    statusText.set("🟢 Rampart: ${detections} detected, ${blocked} blocked")
                } else {
                    statusText.set("🟢 Rampart: Connected")
                }
            } else {
                statusText.set("🔴 Rampart: Disconnected")
            }
        } catch (_: Exception) {
            statusText.set("🔴 Rampart: Error")
        }

        // Update the status bar
        updateWidget()
    }

    private fun updateWidget() {
        try {
            val statusBar = WindowManager.getInstance().getStatusBar(project) ?: return
            statusBar.updateWidget(ID())
        } catch (_: Exception) {
            // Project may be disposed
        }
    }

    private fun scheduleRefresh() {
        alarm.addRequest({
            refreshStatus()
            scheduleRefresh()
        }, refreshIntervalMs)
    }
}