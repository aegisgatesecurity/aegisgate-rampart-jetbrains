// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - Status Bar Widget
// =========================================================================
//
// Shows live connection status and detection stats in the IDE status bar.
// Auto-refreshes every 30 seconds.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Alarm

/**
 * Status bar widget showing Rampart proxy connection status.
 *
 * Displays:
 *   - 🔴 Rampart: Disconnected
 *   - 🟢 Rampart: Connected (with stats)
 *   - 🟡 Rampart: Checking...
 */
class RampartStatusBar(private val project: Project) : StatusBarWidget, Disposable {

    private val logger = Logger.getInstance(RampartStatusBar::class.java)
    private val client = RampartClient()
    private val alarm = Alarm(this)
    private var currentStatus = "🟡 Rampart: Checking..."
    private var refreshIntervalMs = 30_000L

    override fun ID(): String = "RampartStatusBar"

    override fun install(statusBar: com.intellij.openapi.wm.StatusBar) {
        refreshStatus()
        scheduleRefresh()
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return RampartStatusBarPresentation(this)
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }

    fun setUrl(url: String) {
        client.setUrl(url)
        refreshStatus()
    }

    fun setRefreshIntervalMs(ms: Long) {
        refreshIntervalMs = ms
    }

    fun getStatus(): String = currentStatus

    // =========================================================================
    // Private
    // =========================================================================

    private fun refreshStatus() {
        try {
            if (client.isAlive()) {
                val stats = client.getStats()
                if (stats != null) {
                    val mode = stats.mode ?: "monitor"
                    if (mode == "block") {
                        currentStatus = "Rampart: BLOCK 🚫"
                    } else {
                        currentStatus = "Rampart: MONITOR"
                    }
                } else {
                    currentStatus = "🟢 Rampart: Connected"
                }
            } else {
                currentStatus = "🔴 Rampart: Disconnected"
            }
        } catch (_: Exception) {
            currentStatus = "🔴 Rampart: Error"
        }

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

    private class RampartStatusBarPresentation(private val widget: RampartStatusBar) :
        StatusBarWidget.TextPresentation {

        override fun getText(): String = widget.getStatus()
        override fun getTooltipText(): String = "AegisGate Rampart — Local AI Security Proxy"
        override fun getAlignment(): Float = java.awt.Component.RIGHT_ALIGNMENT.toFloat()
    }
}