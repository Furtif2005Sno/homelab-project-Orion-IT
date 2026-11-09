package com.homelab.app.data.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafelineAmpParsingTest {

    private fun json(text: String) = Json.parseToJsonElement(text.trimIndent())

    @Test
    fun `safeline sites accept the wrapped list and map modes`() {
        val sites = SafelineRepository.parseSites(
            json(
                """
                {"data": [
                  {"id": 1, "title": "Cloud", "server_names": ["cloud.example.com"], "ports": ["443_ssl"],
                   "upstreams": ["http://192.168.1.20:8080"], "is_enabled": true, "mode": 0},
                  {"id": 2, "title": "", "comment": "Wiki", "server_names": ["wiki.example.com"], "ports": ["80"],
                   "upstreams": [], "is_enabled": false, "mode": 1}
                ], "total": 2}
                """
            )
        )
        assertEquals(2, sites.size)
        assertEquals("Cloud", sites[0].title)
        assertEquals(SafelineSiteMode.PROTECT, sites[0].mode)
        assertEquals("Wiki", sites[1].title)
        assertFalse(sites[1].enabled)
        assertEquals(SafelineSiteMode.OBSERVE, sites[1].mode)
    }

    @Test
    fun `safeline events and stat nodes are parsed`() {
        val events = SafelineRepository.parseEvents(
            json(
                """
                {"nodes": [{"id": 7, "ip": "203.0.113.9", "host": "cloud.example.com", "dst_port": 443,
                  "deny_count": 12, "pass_count": 0, "country": "FR", "city": "Lyon", "start_at": 1730000000,
                  "finished": true}], "total": 1}
                """
            )
        )
        assertEquals(1, events.size)
        assertEquals(12L, events[0].denyCount)
        assertEquals("Lyon, FR", events[0].location)
        assertTrue(events[0].finished)

        val blocked = SafelineRepository.sumNodes(json("""[{"label": "00", "value": 3}, {"label": "01", "value": 4}]"""))
        assertEquals(7L, blocked)
    }

    @Test
    fun `amp ads instances skip the controller and read metrics`() {
        val instances = AmpRepository.parseAdsInstances(
            json(
                """
                [{"FriendlyName": "Local", "AvailableInstances": [
                  {"InstanceID": "ads", "InstanceName": "ADS01", "Module": "ADS", "Running": true, "AppState": 20},
                  {"InstanceID": "a1", "InstanceName": "Minecraft01", "FriendlyName": "Survie", "Module": "Minecraft",
                   "Running": true, "AppState": 20, "Suspended": false,
                   "Metrics": {"CPU Usage": {"RawValue": 12, "MaxValue": 100, "Percent": 12, "Units": "%"},
                               "Memory Usage": {"RawValue": 2048, "MaxValue": 4096, "Percent": 50, "Units": "MB"},
                               "Active Users": {"RawValue": 3, "MaxValue": 20, "Percent": 15, "Units": "Users"}}},
                  {"InstanceID": "a2", "InstanceName": "Valheim01", "Module": "GenericModule", "Running": false, "AppState": 0}
                ]}]
                """
            )
        )
        assertEquals(2, instances.size)
        val survival = instances[0]
        assertEquals("Survie", survival.friendlyName)
        assertEquals(AmpAppState.RUNNING, survival.state)
        assertEquals(50.0, survival.memory!!.percent!!, 0.001)
        assertEquals(3.0, survival.users!!.current, 0.001)
        assertTrue(survival.managedByAds)
        assertEquals(AmpAppState.OFFLINE, instances[1].state)
    }

    @Test
    fun `amp standalone status maps state codes`() {
        val instance = AmpRepository.parseStandalone(json("""{"State": 0, "Metrics": {}}"""))
        assertEquals(AmpAppState.STOPPED, instance.state)
        assertFalse(instance.managedByAds)
    }

    @Test
    fun `safeline totals accept every statistics shape`() {
        assertEquals(42L, SafelineRepository.total(json("42")))
        assertEquals(7L, SafelineRepository.total(json("""{"total": 7}""")))
        assertEquals(15L, SafelineRepository.total(json("""[{"label": "a", "value": 5}, {"label": "b", "value": 10}]""")))
        assertEquals(9L, SafelineRepository.total(json("""{"nodes": [{"value": 4}, {"value": 5}]}""")))
        assertEquals(3L, SafelineRepository.total(json("""{"data": {"count": 3}}""")))
        assertEquals(null, SafelineRepository.total(json("""{"foo": "bar"}""")))
    }

    @Test
    fun `safeline intercept counts by kind are summed`() {
        val intercept = json("""{"auth_defense": 1, "blacklist": 2, "block": 30, "challenge": 4, "offline": 0, "rate_limit": 3}""")
        assertEquals(40L, SafelineRepository.sumValues(intercept as kotlinx.serialization.json.JsonObject))
    }
}
