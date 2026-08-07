// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - Plugin Settings
// =========================================================================
//
// Persistent settings for the Rampart JetBrains plugin.
// Stored in IDE preferences. Mirrors VS Code extension settings.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JPanel
import javax.swing.JComponent

/**
 * Application-level settings for the Rampart plugin.
 * Stored in IDE preferences (not project-specific).
 */
class RampartSettings : SearchableConfigurable {

    private var urlField: JBTextField? = null
    private var autoScanCheckBox: JBCheckBox? = null
    private var minSeverityField: JBTextField? = null

    override fun getId(): String = "rampart.settings"

    override fun getDisplayName(): String = "AegisGate Rampart"

    override fun createComponent(): JComponent {
        val state = RampartSettingsState.getInstance()

        urlField = JBTextField(state.url, 30)
        autoScanCheckBox = JBCheckBox("Enable auto-scan on file save", state.autoScanEnabled)
        minSeverityField = JBTextField(state.minSeverity, 10)

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Rampart proxy URL:"), urlField!!)
            .addLabeledComponent(JBLabel("Minimum severity level:"), minSeverityField!!)
            .addComponent(autoScanCheckBox!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val state = RampartSettingsState.getInstance()
        return urlField?.text != state.url ||
            autoScanCheckBox?.isSelected != state.autoScanEnabled ||
            minSeverityField?.text != state.minSeverity
    }

    override fun apply() {
        val state = RampartSettingsState.getInstance()
        state.url = urlField?.text ?: DEFAULT_RAMPART_URL
        state.autoScanEnabled = autoScanCheckBox?.isSelected ?: true
        state.minSeverity = minSeverityField?.text ?: "medium"
    }

    override fun reset() {
        val state = RampartSettingsState.getInstance()
        urlField?.text = state.url
        autoScanCheckBox?.isSelected = state.autoScanEnabled
        minSeverityField?.text = state.minSeverity
    }
}

/**
 * Persistent state for Rampart plugin settings.
 * Stored as application-level properties.
 */
class RampartSettingsState : com.intellij.openapi.components.PersistentStateComponent<RampartSettingsState.State> {

    data class State(
        var url: String = DEFAULT_RAMPART_URL,
        var autoScanEnabled: Boolean = true,
        var minSeverity: String = "medium",
    )

    var url: String = DEFAULT_RAMPART_URL
    var autoScanEnabled: Boolean = true
    var minSeverity: String = "medium"

    override fun getState(): State = State(url, autoScanEnabled, minSeverity)

    override fun loadState(state: State) {
        url = state.url
        autoScanEnabled = state.autoScanEnabled
        minSeverity = state.minSeverity
    }

    companion object {
        fun getInstance(): RampartSettingsState {
            return com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .getService(RampartSettingsState::class.java)
        }
    }
}