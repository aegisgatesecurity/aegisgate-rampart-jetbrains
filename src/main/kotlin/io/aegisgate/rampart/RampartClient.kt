// SPDX-License-Identifier: Apache-2.0
// =========================================================================
// AegisGate Rampart - HTTP Client
// =========================================================================
//
// Talks ONLY to localhost. Zero external communications.
// Mirrors the VS Code extension's RampartClient interface.
//
// =========================================================================

package io.aegisgate.rampart

import com.intellij.openapi.diagnostic.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP client for the Rampart local proxy.
 *
 * Talks ONLY to localhost. No external communications.
 * Uses java.net.http.HttpClient (JDK 11+ built-in, zero dependencies).
 */
class RampartClient(url: String = DEFAULT_RAMPART_URL) {

    private var baseUrl: String = url.trimEnd('/')

    private val logger = Logger.getInstance(RampartClient::class.java)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    /**
     * Set the Rampart proxy URL (e.g., from IDE settings).
     */
    fun setUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    /**
     * Get the current base URL.
     */
    fun getUrl(): String = baseUrl

    /**
     * Send text to the Rampart /detect endpoint.
     * Returns a DetectionSummary with all findings.
     */
    fun detect(text: String): DetectionSummary? {
        return try {
            val requestBody = """{"text":${escapeJson(text)}}"""
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/detect"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                logger.warn("Rampart /detect returned ${response.statusCode()}")
                return null
            }

            parseDetectionSummary(response.body())
        } catch (e: Exception) {
            logger.debug("Rampart /detect failed: ${e.message}")
            null
        }
    }

    /**
     * Get proxy stats from the /stats endpoint.
     */
    fun getStats(): ProxyStats? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/stats"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                return null
            }

            parseProxyStats(response.body())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if the Rampart proxy is alive.
     */
    fun isAlive(): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/stats"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // JSON parsing — minimal, zero-dependency (no kotlinx.serialization needed)
    // =========================================================================

    private fun parseDetectionSummary(json: String): DetectionSummary? {
        return try {
            // Extract top-level fields
            val totalDetections = extractInt(json, "total_detections") ?: 0
            val blocked = extractBool(json, "blocked") ?: false
            val blockReason = extractString(json, "block_reason")
            val latencyMs = extractLong(json, "latency_ms") ?: 0L
            val mlScore = extractDouble(json, "ml_score")

            // Extract results array
            val results = parseResultsArray(json)

            // Extract arrays
            val piiCategories = extractStringArray(json, "pii_categories")
            val secretTypes = extractStringArray(json, "secret_types")

            DetectionSummary(
                totalDetections = totalDetections,
                blocked = blocked,
                blockReason = blockReason,
                results = results,
                piiCategories = piiCategories,
                secretTypes = secretTypes,
                mlScore = mlScore,
                latencyMs = latencyMs,
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse DetectionSummary: ${e.message}")
            null
        }
    }

    private fun parseResultsArray(json: String): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        val resultsStart = json.indexOf("\"results\":") ?: return emptyList()
        val arrayStart = json.indexOf('[', resultsStart) ?: return emptyList()
        val arrayEnd = json.lastIndexOf(']') ?: return emptyList()
        if (arrayStart < 0 || arrayEnd <= arrayStart) return emptyList()

        val arrayContent = json.substring(arrayStart + 1, arrayEnd)

        // Split on }{ boundaries (objects in the array)
        var depth = 0
        var objStart = -1
        for (i in arrayContent.indices) {
            if (arrayContent[i] == '{') {
                if (depth == 0) objStart = i
                depth++
            } else if (arrayContent[i] == '}') {
                depth--
                if (depth == 0 && objStart >= 0) {
                    val obj = arrayContent.substring(objStart, i + 1)
                    parseDetectionResult(obj)?.let { results.add(it) }
                    objStart = -1
                }
            }
        }

        return results
    }

    private fun parseDetectionResult(json: String): DetectionResult? {
        return try {
            val category = extractString(json, "category") ?: "unknown"
            val severity = extractString(json, "severity") ?: "info"
            val confidence = extractDouble(json, "confidence") ?: 0.0
            val text = extractString(json, "text")
            val rule = extractString(json, "rule") ?: ""
            val isThreat = extractBool(json, "is_threat") ?: false
            val blocked = extractBool(json, "blocked") ?: false
            val blockReason = extractString(json, "block_reason")
            val mlScore = extractDouble(json, "ml_score")

            DetectionResult(
                category = category,
                severity = severity,
                confidence = confidence,
                text = text,
                rule = rule,
                isThreat = isThreat,
                blocked = blocked,
                blockReason = blockReason,
                mlScore = mlScore,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseProxyStats(json: String): ProxyStats? {
        return try {
            ProxyStats(
                totalRequests = extractLong(json, "total_requests") ?: 0L,
                intercepted = extractLong(json, "intercepted") ?: 0L,
                passedThrough = extractLong(json, "passed_through") ?: 0L,
                detections = extractLong(json, "detections") ?: 0L,
                blockedRequests = extractLong(json, "blocked_requests") ?: 0L,
                mlDetections = extractLong(json, "ml_detections") ?: 0L,
                startTime = extractString(json, "start_time") ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // Minimal JSON extractors — no external JSON library needed
    // =========================================================================

    private fun extractString(json: String, key: String): String? {
        // Match "key": to avoid false matches (e.g., "missing" inside "missing_key")
        val searchPattern = "\"$key\":"
        val idx = json.indexOf(searchPattern)
        if (idx < 0) return null
        val colonIdx = idx + searchPattern.length - 1 // points to ':'
        val valueStart = json.indexOf('"', colonIdx)
        if (valueStart < 0) return null
        val valueEnd = json.indexOf('"', valueStart + 1)
        if (valueEnd < 0) return null
        return json.substring(valueStart + 1, valueEnd)
    }

    private fun extractInt(json: String, key: String): Int? = extractLong(json, key)?.toInt()

    private fun extractLong(json: String, key: String): Long? {
        val searchPattern = "\"$key\":"
        val idx = json.indexOf(searchPattern)
        if (idx < 0) return null
        var numStart = idx + searchPattern.length
        while (numStart < json.length && (json[numStart] == ' ' || json[numStart] == '\t')) numStart++
        if (numStart >= json.length) return null
        var numEnd = numStart
        while (numEnd < json.length && (json[numEnd].isDigit() || json[numEnd] == '-' || json[numEnd] == '.')) numEnd++
        if (numEnd == numStart) return null
        return try { json.substring(numStart, numEnd).toLong() } catch (_: Exception) { null }
    }

    private fun extractDouble(json: String, key: String): Double? {
        val searchPattern = "\"$key\":"
        val idx = json.indexOf(searchPattern)
        if (idx < 0) return null
        var numStart = idx + searchPattern.length
        while (numStart < json.length && (json[numStart] == ' ' || json[numStart] == '\t')) numStart++
        if (numStart >= json.length) return null
        var numEnd = numStart
        while (numEnd < json.length && (json[numEnd].isDigit() || json[numEnd] == '-' || json[numEnd] == '.')) numEnd++
        if (numEnd == numStart) return null
        return try { json.substring(numStart, numEnd).toDouble() } catch (_: Exception) { null }
    }

    private fun extractBool(json: String, key: String): Boolean? {
        val searchPattern = "\"$key\":"
        val idx = json.indexOf(searchPattern)
        if (idx < 0) return null
        val rest = json.substring(idx + searchPattern.length).trimStart()
        return when {
            rest.startsWith("true") -> true
            rest.startsWith("false") -> false
            else -> null
        }
    }

    private fun extractStringArray(json: String, key: String): List<String>? {
        val searchPattern = "\"$key\":"
        val idx = json.indexOf(searchPattern)
        if (idx < 0) return null
        val arrStart = json.indexOf('[', idx + searchPattern.length)
        if (arrStart < 0) return null
        val arrEnd = json.indexOf(']', arrStart)
        if (arrEnd < 0) return null
        val content = json.substring(arrStart + 1, arrEnd)
        return content.split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .ifEmpty { null }
    }

    /** Escape a string for JSON embedding. */
    private fun escapeJson(s: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}