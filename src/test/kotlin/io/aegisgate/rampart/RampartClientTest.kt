// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - JetBrains Plugin Tests
// =========================================================================

package io.aegisgate.rampart

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for RampartClient JSON parsing.
 * Tests the minimal JSON parser against real /detect and /stats responses.
 */
class RampartClientTest {

    private val client = RampartClient()

    // =========================================================================
    // JSON extraction tests
    // =========================================================================

    @Test
    fun `extractString finds value`() {
        val json = """{"category":"pii","severity":"high"}"""
        assertEquals("pii", extractStringViaReflection(json, "category"))
        assertEquals("high", extractStringViaReflection(json, "severity"))
    }

    @Test
    fun `extractString returns null for missing key`() {
        val json = """{"category":"pii"}"""
        assertNull(extractStringViaReflection(json, "missing"))
    }

    @Test
    fun `extractLong finds value`() {
        val json = """{"total_detections":5,"latency_ms":123}"""
        assertEquals(5L, extractLongViaReflection(json, "total_detections"))
        assertEquals(123L, extractLongViaReflection(json, "latency_ms"))
    }

    @Test
    fun `extractBool finds value`() {
        val json = """{"blocked":true,"is_threat":false}"""
        assertEquals(true, extractBoolViaReflection(json, "blocked"))
        assertEquals(false, extractBoolViaReflection(json, "is_threat"))
    }

    @Test
    fun `extractDouble finds value`() {
        val json = """{"confidence":0.95,"ml_score":0.87}"""
        assertEquals(0.95, extractDoubleViaReflection(json, "confidence")!!, 0.001)
        assertEquals(0.87, extractDoubleViaReflection(json, "ml_score")!!, 0.001)
    }

    @Test
    fun `extractStringArray finds values`() {
        val json = """{"pii_categories":["ssn","email","phone"]}"""
        val result = extractStringArrayViaReflection(json, "pii_categories")
        assertNotNull(result)
        assertEquals(listOf("ssn", "email", "phone"), result)
    }

    // =========================================================================
    // Severity mapping tests
    // =========================================================================

    @Test
    fun `severity from string maps correctly`() {
        assertEquals(RampartSeverity.CRITICAL, RampartSeverity.fromString("critical"))
        assertEquals(RampartSeverity.HIGH, RampartSeverity.fromString("high"))
        assertEquals(RampartSeverity.MEDIUM, RampartSeverity.fromString("medium"))
        assertEquals(RampartSeverity.LOW, RampartSeverity.fromString("low"))
        assertEquals(RampartSeverity.INFO, RampartSeverity.fromString("info"))
    }

    @Test
    fun `severity from unknown string defaults to INFO`() {
        assertEquals(RampartSeverity.INFO, RampartSeverity.fromString("unknown"))
        assertEquals(RampartSeverity.INFO, RampartSeverity.fromString(""))
    }

    // =========================================================================
    // Category icons tests
    // =========================================================================

    @Test
    fun `category icons contain expected mappings`() {
        assertEquals("🔐", CATEGORY_ICONS["pii"])
        assertEquals("💳", CATEGORY_ICONS["credit_card"])
        assertEquals("⚔️", CATEGORY_ICONS["xss"])
        assertEquals("🔑", CATEGORY_ICONS["secret"])
        assertEquals("🧠", CATEGORY_ICONS["prompt_injection"])
        assertEquals("📋", CATEGORY_ICONS["compliance"])
    }

    // =========================================================================
    // Data model tests
    // =========================================================================

    @Test
    fun `DetectionResult data class holds values`() {
        val result = DetectionResult(
            category = "pii",
            severity = "high",
            confidence = 0.95,
            text = "555-55-5555",
            rule = "ssn_pattern",
            isThreat = true,
            blocked = false,
            blockReason = null,
            mlScore = 0.87,
        )
        assertEquals("pii", result.category)
        assertEquals("high", result.severity)
        assertEquals(0.95, result.confidence, 0.001)
        assertEquals("555-55-5555", result.text)
        assertEquals("ssn_pattern", result.rule)
        assertTrue(result.isThreat)
        assertFalse(result.blocked)
        assertNull(result.blockReason)
        assertEquals(0.87, result.mlScore!!, 0.001)
    }

    @Test
    fun `DetectionSummary data class holds values`() {
        val summary = DetectionSummary(
            totalDetections = 3,
            blocked = true,
            blockReason = "PII detected",
            results = emptyList(),
            piiCategories = listOf("ssn", "email"),
            secretTypes = null,
            compliance = mapOf("gdpr" to true),
            mlScore = 0.92,
            latencyMs = 45,
        )
        assertEquals(3, summary.totalDetections)
        assertTrue(summary.blocked)
        assertEquals("PII detected", summary.blockReason)
        assertEquals(listOf("ssn", "email"), summary.piiCategories)
        assertNull(summary.secretTypes)
        assertEquals(mapOf("gdpr" to true), summary.compliance)
        assertEquals(0.92, summary.mlScore!!, 0.001)
        assertEquals(45L, summary.latencyMs)
    }

    @Test
    fun `ProxyStats data class holds values`() {
        val stats = ProxyStats(
            totalRequests = 100L,
            intercepted = 80L,
            passedThrough = 20L,
            detections = 15L,
            blockedRequests = 3L,
            mlDetections = 7L,
            startTime = "2026-08-07T00:00:00Z",
        )
        assertEquals(100L, stats.totalRequests)
        assertEquals(80L, stats.intercepted)
        assertEquals(20L, stats.passedThrough)
        assertEquals(15L, stats.detections)
        assertEquals(3L, stats.blockedRequests)
        assertEquals(7L, stats.mlDetections)
    }

    // =========================================================================
    // URL normalization tests
    // =========================================================================

    @Test
    fun `client URL strips trailing slash`() {
        val client = RampartClient("http://localhost:9090/")
        assertEquals("http://localhost:9090", client.getUrl())
    }

    @Test
    fun `client URL preserves no-trailing-slash`() {
        val client = RampartClient("http://localhost:9090")
        assertEquals("http://localhost:9090", client.getUrl())
    }

    @Test
    fun `client URL setUrl updates base`() {
        val client = RampartClient()
        client.setUrl("http://localhost:8080")
        assertEquals("http://localhost:8080", client.getUrl())
    }

    // =========================================================================
    // Connection failure test (proxy not running)
    // =========================================================================

    @Test
    fun `isAlive returns false when proxy not reachable`() {
        val client = RampartClient("http://localhost:59999")
        assertFalse(client.isAlive())
    }

    @Test
    fun `detect returns null when proxy not reachable`() {
        val client = RampartClient("http://localhost:59999")
        assertNull(client.detect("test text"))
    }

    @Test
    fun `getStats returns null when proxy not reachable`() {
        val client = RampartClient("http://localhost:59999")
        assertNull(client.getStats())
    }

    // =========================================================================
    // Reflection helpers to test private parsing methods
    // =========================================================================

    private fun extractStringViaReflection(json: String, key: String): String? {
        val method = RampartClient::class.java.getDeclaredMethod("extractString", String::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(client, json, key) as String?
    }

    private fun extractLongViaReflection(json: String, key: String): Long? {
        val method = RampartClient::class.java.getDeclaredMethod("extractLong", String::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(client, json, key) as Long?
    }

    private fun extractBoolViaReflection(json: String, key: String): Boolean? {
        val method = RampartClient::class.java.getDeclaredMethod("extractBool", String::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(client, json, key) as Boolean?
    }

    private fun extractDoubleViaReflection(json: String, key: String): Double? {
        val method = RampartClient::class.java.getDeclaredMethod("extractDouble", String::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(client, json, key) as Double?
    }

    private fun extractStringArrayViaReflection(json: String, key: String): List<String>? {
        val method = RampartClient::class.java.getDeclaredMethod("extractStringArray", String::class.java, String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(client, json, key) as List<String>?
    }
}