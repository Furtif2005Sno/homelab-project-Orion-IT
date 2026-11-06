package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.domain.model.ServiceInstance
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** AMP application state (CubeCoders `ApplicationState`). */
enum class AmpAppState {
    RUNNING, STARTING, STOPPING, RESTARTING, STOPPED, SLEEPING, UPDATING, FAILED, SUSPENDED, OFFLINE, UNKNOWN;

    companion object {
        fun from(code: Int?, instanceRunning: Boolean): AmpAppState {
            if (!instanceRunning) return OFFLINE
            return when (code) {
                20 -> RUNNING
                5, 7, 10 -> STARTING
                40 -> STOPPING
                30 -> RESTARTING
                0 -> STOPPED
                45, 50, 60 -> SLEEPING
                70, 75, 80 -> UPDATING
                100 -> FAILED
                200 -> SUSPENDED
                else -> UNKNOWN
            }
        }
    }
}

enum class AmpAction { START, STOP, RESTART }

data class AmpMetric(val current: Double, val max: Double?, val percent: Double?, val units: String?)

data class AmpGameInstance(
    val id: String,
    val name: String,
    val friendlyName: String,
    val module: String?,
    val state: AmpAppState,
    val cpu: AmpMetric?,
    val memory: AmpMetric?,
    val users: AmpMetric?,
    /** False for a standalone (non-ADS) AMP instance, controlled through Core instead of ADSModule. */
    val managedByAds: Boolean
)

data class AmpDashboardData(val instances: List<AmpGameInstance>) {
    val runningCount: Int get() = instances.count { it.state == AmpAppState.RUNNING }
}

data class AmpSummary(val running: Int, val total: Int)

class AmpAuthException(message: String) : IllegalStateException(message)

/**
 * AMP by CubeCoders. Every call is a JSON POST to `/API/<Module>/<Method>` carrying the session id
 * from `/API/Core/Login`; an expired session is renewed once from the stored credentials.
 */
@Singleton
class AmpRepository @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val tlsClientSelector: TlsClientSelector
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Logs in and returns the session id. */
    suspend fun authenticate(
        url: String,
        username: String,
        password: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val client = tlsClientSelector.forAllowSelfSigned(allowSelfSigned)
        var lastError: Exception? = null
        for (base in baseCandidates(url, fallbackUrl)) {
            try {
                return@withContext login(client, base, username, password)
            } catch (error: AmpAuthException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("AMP login failed.")
    }

    suspend fun getDashboard(instanceId: String): AmpDashboardData = withSession(instanceId) { client, base, session ->
        val instances = runCatching {
            parseAdsInstances(call(client, base, "ADSModule/GetInstances", session))
        }.getOrElse { error ->
            if (error is AmpAuthException) throw error
            // Standalone AMP (no ADS controller): report the single instance through Core.
            listOf(parseStandalone(call(client, base, "Core/GetStatus", session)))
        }
        AmpDashboardData(instances)
    }

    suspend fun getSummary(instanceId: String): AmpSummary {
        val data = getDashboard(instanceId)
        return AmpSummary(running = data.runningCount, total = data.instances.size)
    }

    suspend fun runAction(instanceId: String, target: AmpGameInstance, action: AmpAction) {
        withSession(instanceId) { client, base, session ->
            if (target.managedByAds) {
                val method = when (action) {
                    AmpAction.START -> "ADSModule/StartInstance"
                    AmpAction.STOP -> "ADSModule/StopInstance"
                    AmpAction.RESTART -> "ADSModule/RestartInstance"
                }
                call(client, base, method, session) { put("InstanceName", target.name) }
            } else {
                val method = when (action) {
                    AmpAction.START -> "Core/Start"
                    AmpAction.STOP -> "Core/Stop"
                    AmpAction.RESTART -> "Core/Restart"
                }
                call(client, base, method, session)
            }
        }
    }

    private suspend fun <T> withSession(
        instanceId: String,
        block: (client: OkHttpClient, base: String, session: String) -> T
    ): T = withContext(Dispatchers.IO) {
        var instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")
        val client = tlsClientSelector.forInstance(instanceId)
        val bases = baseCandidates(instance.url, instance.fallbackUrl)
        var lastError: Exception? = null
        for (base in bases) {
            try {
                if (instance.token.isBlank()) instance = refreshSession(client, base, instance)
                return@withContext try {
                    block(client, base, instance.token)
                } catch (expired: AmpAuthException) {
                    instance = refreshSession(client, base, instance)
                    block(client, base, instance.token)
                }
            } catch (error: AmpAuthException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("AMP is unreachable.")
    }

    private suspend fun refreshSession(client: OkHttpClient, base: String, instance: ServiceInstance): ServiceInstance {
        val username = instance.username.orEmpty()
        val password = instance.password.orEmpty()
        if (username.isBlank() || password.isBlank()) {
            throw AmpAuthException("AMP session expired and credentials are unavailable.")
        }
        val refreshed = instance.copy(token = login(client, base, username, password))
        serviceInstancesRepository.saveInstance(refreshed)
        return refreshed
    }

    private fun login(client: OkHttpClient, base: String, username: String, password: String): String {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
            put("token", "")
            put("rememberMe", false)
        }
        val response = post(client, base, "Core/Login", body) as? JsonObject
            ?: throw IllegalStateException("AMP returned an unexpected login response.")
        val success = (response["success"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val session = (response["sessionID"] as? JsonPrimitive)?.contentOrNull
        if (!success || session.isNullOrBlank()) {
            val reason = (response["resultReason"] as? JsonPrimitive)?.contentOrNull
            throw AmpAuthException(reason?.takeIf { it.isNotBlank() } ?: "AMP rejected the username or password.")
        }
        return session
    }

    private fun call(
        client: OkHttpClient,
        base: String,
        method: String,
        session: String,
        params: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}
    ): JsonElement = post(client, base, method, buildJsonObject {
        put("SESSIONID", session)
        params()
    })

    private fun post(client: OkHttpClient, base: String, method: String, body: JsonObject): JsonElement {
        val request = Request.Builder()
            .url("$base/API/$method")
            .post(body.toString().toRequestBody(jsonType))
            .addHeader("Accept", "application/json")
            .addHeader("X-Homelab-Bypass", "true")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) throw AmpAuthException("AMP session expired.")
            if (!response.isSuccessful) throw IllegalStateException("AMP returned HTTP ${response.code}.")
            val element = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: JsonPrimitive(text)
            // AMP reports an invalid session as a 200 with an "Unauthorized Access" error object.
            val title = ((element as? JsonObject)?.get("Title") as? JsonPrimitive)?.contentOrNull
            if (title != null && title.contains("Unauthorized", ignoreCase = true)) {
                throw AmpAuthException("AMP session expired.")
            }
            return element
        }
    }

    private fun baseCandidates(url: String, fallbackUrl: String?): List<String> =
        listOf(url, fallbackUrl)
            .mapNotNull { it?.trim()?.trimEnd('/')?.removeSuffix("/API")?.takeIf(String::isNotBlank) }
            .distinct()

    companion object {
        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        private fun JsonObject.flag(key: String): Boolean? = text(key)?.lowercase()?.toBooleanStrictOrNull()

        private fun metric(metrics: JsonObject?, vararg names: String): AmpMetric? {
            val node = names.firstNotNullOfOrNull { metrics?.get(it) as? JsonObject } ?: return null
            val current = node.text("RawValue")?.toDoubleOrNull() ?: return null
            return AmpMetric(
                current = current,
                max = node.text("MaxValue")?.toDoubleOrNull()?.takeIf { it > 0 },
                percent = node.text("Percent")?.toDoubleOrNull(),
                units = node.text("Units")
            )
        }

        /** `GetInstances` returns ADS targets, each with its `AvailableInstances`. */
        internal fun parseAdsInstances(element: JsonElement): List<AmpGameInstance> {
            val targets = when (element) {
                is JsonArray -> element
                is JsonObject -> (element["result"] as? JsonArray) ?: JsonArray(listOf(element))
                else -> throw IllegalStateException("AMP returned an unexpected instance list.")
            }
            return targets.flatMap { target ->
                ((target as? JsonObject)?.get("AvailableInstances") as? JsonArray).orEmpty()
            }.mapNotNull { item ->
                val instance = item as? JsonObject ?: return@mapNotNull null
                val name = instance.text("InstanceName") ?: return@mapNotNull null
                // The ADS controller itself is listed as an instance; it is not a game server.
                if (instance.text("Module").equals("ADS", ignoreCase = true)) return@mapNotNull null
                val metrics = instance["Metrics"] as? JsonObject
                AmpGameInstance(
                    id = instance.text("InstanceID") ?: name,
                    name = name,
                    friendlyName = instance.text("FriendlyName") ?: name,
                    module = instance.text("ModuleDisplayName") ?: instance.text("Module"),
                    state = if (instance.flag("Suspended") == true) {
                        AmpAppState.SUSPENDED
                    } else {
                        AmpAppState.from(instance.text("AppState")?.toIntOrNull(), instance.flag("Running") ?: false)
                    },
                    cpu = metric(metrics, "CPU Usage"),
                    memory = metric(metrics, "Memory Usage"),
                    users = metric(metrics, "Active Users"),
                    managedByAds = true
                )
            }
        }

        internal fun parseStandalone(element: JsonElement): AmpGameInstance {
            val status = element as? JsonObject ?: throw IllegalStateException("AMP returned an unexpected status.")
            val metrics = status["Metrics"] as? JsonObject
            return AmpGameInstance(
                id = "amp",
                name = "AMP",
                friendlyName = "AMP",
                module = null,
                state = AmpAppState.from(status.text("State")?.toIntOrNull(), instanceRunning = true),
                cpu = metric(metrics, "CPU Usage"),
                memory = metric(metrics, "Memory Usage"),
                users = metric(metrics, "Active Users"),
                managedByAds = false
            )
        }
    }
}
