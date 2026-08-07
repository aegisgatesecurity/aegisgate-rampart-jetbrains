// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - Status Bar Widget Factory
// =========================================================================

package io.aegisgate.rampart

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget

/**
 * Factory for creating RampartStatusBar widgets.
 * Registered in plugin.xml as a statusBarWidgetFactory extension.
 */
class RampartStatusBarWidgetFactory : com.intellij.openapi.wm.StatusBarWidgetFactory {

    override fun getId(): String = "RampartStatusBar"

    override fun getDisplayName(): String = "AegisGate Rampart"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        return RampartStatusBar(project)
    }

    override fun canBeEnabledOn(statusBar: com.intellij.openapi.wm.StatusBar): Boolean = true

    override fun disposeWidget(widget: StatusBarWidget) {
        if (widget is Disposable) {
            (widget as Disposable).dispose()
        }
    }
}