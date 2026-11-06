package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.domain.model.ServiceInstance
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** SafeLine site protection mode (0 protect, 1 observe/audit, 2 maintenance). */
enum class SafelineSiteMode { PROTECT, OBSERVE, MAINTENANCE, UNKNOWN }

data class SafelineSite(
    val id: String,
    val title: String,
    val serverNames: List<String>,
    val ports: List<String>,
    val upstreams: List<String>,
    val enabled: Boolean,
    val mode: SafelineSiteMode
)

data class SafelineAttackEvent(
    val id: String,
    val ip: String,
    val host: String?,
    val port: Long?,
    val denyCount: Long,
    val passCount: Long,
    val location: String?,
    val startAtEpochSeconds: Long?,
    val finished: Boolean
)

data class SafelineDashboardData(
    val version: String?,
    val todayRequests: Long?,
    val todayVisitors: Long?,
    val todayIps: Long?,
    val blocked: Long?,
    val attackIps: Long?,
    val sites: List<SafelineSite>,
    val events: List<SafelineAttackEvent>
)

data class SafelineSummary(val blocked: Long?, val sites: Int)

/**
 * SafeLine WAF (chaitin/SafeLine) through its Open API: token in `X-SLCE-API-TOKEN`,
 * responses wrapped as `{ "data": ..., "err": ..., "msg": ... }`.
 * Payload shapes are not officially documented, so parsing is deliberately lenient.
 */
@Singleton
class SafelineRepository @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val tlsClientSelector: TlsClientSelector
) {

    suspend fun authenticate(
        url: String,
        apiKey: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val client = tlsClientSelector.forAllowSelfSigned(allowSelfSigned)
        var lastError: Exception? = null
        for (base in baseCandidates(url, fallbackUrl)) {
            try {
                get(client, base, apiKey, "/api/open/system")
                return@withContext
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("SafeLine validation failed.")
    }

    suspend fun getDashboard(instanceId: String): SafelineDashboardData = withContext(Dispatchers.IO) {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")
        val token = instance.apiKey.orEmpty()
        val client = tlsClientSelector.forInstance(instanceId)
        val base = resolveBase(client, instance, token)

        coroutineScope {
            val system = async { runCatching { get(client, base, token, "/api/open/system") }.getOrNull() }
            val counts = async { runCatching { get(client, base, token, "/api/open/dashboard/user/counts") }.getOrNull() }
            val attack = async { runCatching { get(client, base, token, "/api/stat/basic/attack") }.getOrNull() }
            val sites = async { runCatching { get(client, base, token, "/api/open/site") }.getOrNull() }
            val events = async { runCatching { get(client, base, token, "/api/open/events?page=1&page_size=20") }.getOrNull() }

            val countsObj = counts.await() as? JsonObject
            val attackObj = attack.await() as? JsonObject
            SafelineDashboardData(
                version = (system.await() as? JsonObject)?.string("version"),
                todayRequests = countsObj?.long("today_pv"),
                todayVisitors = countsObj?.long("today_uv"),
                todayIps = countsObj?.long("today_ip"),
                blocked = attackObj?.get("intercept")?.let(::sumNodes),
                attackIps = attackObj?.get("attack_ip")?.let(::sumNodes),
                sites = parseSites(sites.await()),
                events = parseEvents(events.await())
            )
        }
    }

    suspend fun getSummary(instanceId: String): SafelineSummary {
        val data = getDashboard(instanceId)
        return SafelineSummary(blocked = data.blocked, sites = data.sites.size)
    }

    /** First base URL that answers, so the fallback URL is used when the primary is unreachable. */
    private fun resolveBase(client: OkHttpClient, instance: ServiceInstance, token: String): String {
        val candidates = baseCandidates(instance.url, instance.fallbackUrl)
        var lastError: Exception? = null
        for (base in candidates) {
            try {
                get(client, base, token, "/api/open/system")
                return base
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("SafeLine is unreachable.")
    }

    private fun baseCandidates(url: String, fallbackUrl: String?): List<String> =
        listOf(url, fallbackUrl)
            .mapNotNull { it?.trim()?.trimEnd('/')?.removeSuffix("/api")?.takeIf(String::isNotBlank) }
            .distinct()

    /** GET a SafeLine API path and return the unwrapped `data` element. */
    private fun get(client: OkHttpClient, base: String, token: String, path: String): JsonElement {
        val request = Request.Builder()
            .url(base + path)
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("X-SLCE-API-TOKEN", token)
            .addHeader("X-Homelab-Bypass", "true")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                in 200..299 -> Unit
                401, 403 -> throw IllegalStateException("SafeLine rejected the API token.")
                else -> throw IllegalStateException("SafeLine returned HTTP ${response.code}.")
            }
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: throw IllegalStateException("SafeLine returned an unexpected response.")
            val err = root["err"]
            val hasError = err != null && err !is JsonNull && (err as? JsonPrimitive)?.contentOrNull?.isNotBlank() != false
            if (hasError) {
                val message = root.string("msg") ?: (err as? JsonPrimitive)?.contentOrNull ?: "SafeLine API error"
                throw IllegalStateException(message)
            }
            return root["data"] ?: JsonNull
        }
    }

    companion object {
        internal fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        internal fun JsonObject.long(key: String): Long? = string(key)?.toDoubleOrNull()?.toLong()

        private fun JsonObject.bool(key: String): Boolean? = when (val raw = string(key)?.lowercase()) {
            null -> null
            "true", "1" -> true
            "false", "0" -> false
            else -> raw.toBooleanStrictOrNull()
        }

        private fun JsonObject.strings(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()

        /** Sum of `{label, value}` stat nodes. */
        internal fun sumNodes(element: JsonElement): Long? {
            val nodes = element as? JsonArray ?: return (element as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()
            return nodes.sumOf { (it as? JsonObject)?.long("value") ?: 0L }
        }

        /** Unwraps `{data:[...]}`, `{nodes:[...]}` or a bare array. */
        private fun arrayIn(element: JsonElement?): JsonArray? = when (element) {
            is JsonArray -> element
            is JsonObject -> (element["data"] ?: element["nodes"] ?: element["items"]) as? JsonArray
            else -> null
        }

        internal fun parseSites(element: JsonElement?): List<SafelineSite> =
            arrayIn(element)?.mapIndexedNotNull { index, item ->
                val site = item as? JsonObject ?: return@mapIndexedNotNull null
                val serverNames = site.strings("server_names")
                SafelineSite(
                    id = site.string("id") ?: "site-$index",
                    title = site.string("title") ?: site.string("comment") ?: serverNames.firstOrNull() ?: "Site ${index + 1}",
                    serverNames = serverNames,
                    ports = site.strings("ports"),
                    upstreams = site.strings("upstreams"),
                    enabled = site.bool("is_enabled") ?: true,
                    mode = when (site.long("mode")) {
                        0L -> SafelineSiteMode.PROTECT
                        1L -> SafelineSiteMode.OBSERVE
                        2L -> SafelineSiteMode.MAINTENANCE
                        else -> SafelineSiteMode.UNKNOWN
                    }
                )
            }.orEmpty()

        internal fun parseEvents(element: JsonElement?): List<SafelineAttackEvent> =
            arrayIn(element)?.mapIndexedNotNull { index, item ->
                val event = item as? JsonObject ?: return@mapIndexedNotNull null
                val location = listOfNotNull(event.string("city"), event.string("province"), event.string("country"))
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { null }
                SafelineAttackEvent(
                    id = event.string("id") ?: "event-$index",
                    ip = event.string("ip") ?: "-",
                    host = event.string("host"),
                    port = event.long("dst_port"),
                    denyCount = event.long("deny_count") ?: 0L,
                    passCount = event.long("pass_count") ?: 0L,
                    location = location,
                    startAtEpochSeconds = event.long("start_at"),
                    finished = event.bool("finished") ?: false
                )
            }.orEmpty()
    }
}
