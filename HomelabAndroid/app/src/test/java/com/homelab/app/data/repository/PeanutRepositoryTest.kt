package com.homelab.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeanutRepositoryTest {

    private val json = """
        [
          {
            "battery.charge": "100",
            "battery.runtime": "2430",
            "battery.voltage": "13.6",
            "device.mfr": "EATON",
            "device.model": "Ellipse PRO 650",
            "input.voltage": "231.0",
            "output.voltage": "230.0",
            "ups.load": "18",
            "ups.realpower.nominal": "400",
            "ups.status": "OL CHRG",
            "peanut.device_id": "eaton",
            "peanut.server": "192.168.1.10:3493"
          },
          {
            "battery.charge": "35",
            "ups.status": "OB LB RB",
            "device.model": "Back-UPS 700"
          }
        ]
    """.trimIndent()

    @Test
    fun `devices are parsed from flattened NUT variables`() {
        val devices = PeanutRepository.parseDevices(json)
        assertEquals(2, devices.size)

        val eaton = devices[0]
        assertEquals("eaton", eaton.id)
        assertEquals("EATON", eaton.manufacturer)
        assertEquals(PeanutPowerState.ONLINE, eaton.powerState)
        assertTrue(eaton.isCharging)
        assertEquals(2430L, eaton.runtimeSeconds)
        assertEquals(72.0, eaton.estimatedWatts!!, 0.001)

        val apc = devices[1]
        assertEquals(PeanutPowerState.LOW_BATTERY, apc.powerState)
        assertTrue(apc.needsBatteryReplacement)
        assertEquals("Back-UPS 700", apc.name)
    }

    @Test
    fun `dashboard aggregates battery and outage counts`() {
        val data = PeanutDashboardData(version = "5.10.0", devices = PeanutRepository.parseDevices(json))
        assertEquals(1, data.onlineCount)
        assertEquals(1, data.onBatteryCount)
        assertEquals(35.0, data.lowestBattery!!, 0.001)
    }

    @Test
    fun `invalid payload yields no devices`() {
        assertEquals(0, PeanutRepository.parseDevices("not json").size)
    }
}
