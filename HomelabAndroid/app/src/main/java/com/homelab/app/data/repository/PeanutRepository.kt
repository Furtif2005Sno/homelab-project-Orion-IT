package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.PeanutApi
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request

/** Power state derived from NUT's space-separated `ups.status` flags. */
enum class PeanutPowerState {
    ONLINE,
    ON_BATTERY,
    LOW_BATTERY,
    OFFLINE,
    UNKNOWN
}

data class PeanutDevice(
    val id: String,
    val name: String,
    val manufacturer: String?,
    val model: String?,
    val statusFlags: List<String>,
    val batteryCharge: Double?,
    val runtimeSeconds: Long?,
    val load: Double?,
    val inputVoltage: Double?,
    val outputVoltage: Double?,
    val batteryVoltage: Double?,
    val realPowerNominal: Double?
) {
    val powerState: PeanutPowerState
        get() = when {
            "LB" in statusFlags -> PeanutPowerState.LOW_BATTERY
            "OB" in statusFlags -> PeanutPowerState.ON_BATTERY
            "OL" in statusFlags -> PeanutPowerState.ONLINE
            "OFF" in statusFlags -> PeanutPowerState.OFFLINE
            else -> PeanutPowerState.UNKNOWN
        }
    val isCharging: Boolean get() = "CHRG" in statusFlags
    val needsBatteryReplacement: Boolean get() = "RB" in statusFlags
    val isOverloaded: Boolean get() = "OVER" in statusFlags

    /** Estimated draw in watts when NUT exposes the nominal real power and the load. */
    val estimatedWatts: Double?
        get() = if (realPowerNominal != null && load != null) realPowerNominal * load / 100.0 else null
}

data class PeanutDashboardData(
    val version: String?,
    val devices: List<PeanutDevice>
) {
    val onlineCount: Int get() = devices.count { it.powerState == PeanutPowerState.ONLINE }
    val onBatteryCount: Int
        get() = devices.count { it.powerState == PeanutPowerState.ON_BATTERY || it.powerState == PeanutPowerState.LOW_BATTERY }
    /** Lowest battery charge across devices: the one that matters first during an outage. */
    val lowestBattery: Double? get() = devices.mapNotNull { it.batteryCharge }.minOrNull()
}

data class PeanutSummary(
    val lowestBattery: Double?,
    val deviceCount: Int,
    val onBatteryCount: Int
)

@Singleton
class PeanutRepository @Inject constructor(
    private val api: PeanutApi,
    private val tlsClientSelector: TlsClientSelector
) {

    /** Checks the URL and optional Basic credentials against `/api/v1/devices`. */
    suspend fun authenticate(
        url: String,
        username: String? = null,
        password: String? = null,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val bases = listOf(url, fallbackUrl)
            .mapNotNull { it?.trim()?.trimEnd('/')?.takeIf(String::isNotBlank) }
            .distinct()
        var lastError: Exception? = null
        for (base in bases) {
            try {
                val builder = Request.Builder()
                    .url("$base/api/v1/devices")
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("X-Homelab-Bypass", "true")
                if (!password.isNullOrBlank()) {
                    val encoded = Base64.getEncoder()
                        .encodeToString("${username.orEmpty()}:$password".toByteArray(Charsets.UTF_8))
                    builder.addHeader("Authorization", "Basic $encoded")
                }
                tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(builder.build()).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> {
                            val body = response.body?.string().orEmpty().trim()
                            if (!body.startsWith("[")) throw IllegalStateException("PeaNUT did not return a device list.")
                        }
                        401, 403 -> throw IllegalStateException("PeaNUT authentication failed.")
                        else -> throw IllegalStateException("PeaNUT returned HTTP ${response.code}.")
                    }
                }
                return@withContext
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("PeaNUT validation failed.")
    }

    suspend fun getDashboard(instanceId: String): PeanutDashboardData {
        val devices = parseDevices(api.getDevices(instanceId = instanceId).string())
        val version = runCatching {
            val info = Json.parseToJsonElement(api.getInfo(instanceId = instanceId).string()) as? JsonObject
            (info?.get("version") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return PeanutDashboardData(version = version, devices = devices)
    }

    suspend fun getSummary(instanceId: String): PeanutSummary {
        val data = getDashboard(instanceId)
        return PeanutSummary(
            lowestBattery = data.lowestBattery,
            deviceCount = data.devices.size,
            onBatteryCount = data.onBatteryCount
        )
    }

    companion object {
        fun parseDevices(json: String): List<PeanutDevice> {
            // NUT values arrive as strings or numbers; kotlinx.serialization keeps this JVM-testable.
            val array = runCatching { Json.parseToJsonElement(json.trim()) as? JsonArray }.getOrNull() ?: return emptyList()
            return array.mapIndexedNotNull { index, element ->
                val row = element as? JsonObject ?: return@mapIndexedNotNull null
                fun text(key: String) = (row[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                fun number(key: String) = text(key)?.toDoubleOrNull()

                val id = text("peanut.device_id") ?: text("device.serial") ?: text("ups.serial") ?: "ups-$index"
                val manufacturer = text("device.mfr") ?: text("ups.mfr")
                val model = text("device.model") ?: text("ups.model")
                PeanutDevice(
                    id = id,
                    name = text("peanut.device_id") ?: model ?: "UPS ${index + 1}",
                    manufacturer = manufacturer,
                    model = model,
                    statusFlags = text("ups.status")?.uppercase()?.split(' ')?.filter { it.isNotBlank() }.orEmpty(),
                    batteryCharge = number("battery.charge"),
                    runtimeSeconds = number("battery.runtime")?.toLong(),
                    load = number("ups.load"),
                    inputVoltage = number("input.voltage"),
                    outputVoltage = number("output.voltage"),
                    batteryVoltage = number("battery.voltage"),
                    realPowerNominal = number("ups.realpower.nominal")
                )
            }
        }
    }
}
