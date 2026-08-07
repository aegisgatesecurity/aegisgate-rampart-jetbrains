// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - JetBrains IDE Plugin
// =========================================================================
//
// Local HTTPS proxy integration for detecting PII/secrets/XSS in AI API traffic.
// Talks ONLY to localhost. Zero external communications. Zero PII stored.
//
// =========================================================================

package io.aegisgate.rampart

// =========================================================================
// Plugin descriptor constants
// =========================================================================

const val PLUGIN_ID = "io.aegisgate.rampart"
const val PLUGIN_NAME = "AegisGate Rampart"
const val DEFAULT_RAMPART_URL = "http://localhost:9090"

// Category icons matching VS Code extension
val CATEGORY_ICONS = mapOf(
    "pii" to "🔐",
    "credit_card" to "💳",
    "ssn" to "💳",
    "phone" to "💳",
    "email" to "💳",
    "xss" to "⚔️",
    "secret" to "🔑",
    "api_key" to "🔑",
    "token" to "🔑",
    "password" to "🔑",
    "prompt_injection" to "🧠",
    "compliance" to "📋",
)

// Severity mapping to IntelliJ highlight levels
enum class RampartSeverity(val level: Int, val icon: String) {
    CRITICAL(4, "🔴"),
    HIGH(3, "🟠"),
    MEDIUM(2, "🟡"),
    LOW(1, "🟢"),
    INFO(0, "ℹ️");

    companion object {
        fun fromString(s: String): RampartSeverity =
            entries.find { it.name.equals(s, ignoreCase = true) } ?: INFO
    }
}

// =========================================================================
// Data models — mirror VS Code extension types
// =========================================================================

data class DetectionResult(
    val category: String,
    val severity: String,
    val confidence: Double,
    val text: String? = null,
    val rule: String,
    val isThreat: Boolean,
    val blocked: Boolean,
    val blockReason: String? = null,
    val mlScore: Double? = null,
)

data class DetectionSummary(
    val totalDetections: Int,
    val blocked: Boolean,
    val blockReason: String? = null,
    val results: List<DetectionResult>,
    val piiCategories: List<String>? = null,
    val secretTypes: List<String>? = null,
    val compliance: Map<String, Boolean>? = null,
    val mlScore: Double? = null,
    val latencyMs: Long,
)

data class ProxyStats(
    val totalRequests: Long,
    val intercepted: Long,
    val passedThrough: Long,
    val detections: Long,
    val blockedRequests: Long,
    val mlDetections: Long,
    val startTime: String,
)