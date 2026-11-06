package com.homelab.app.util

import com.homelab.app.domain.model.BackupEnvelope
import com.homelab.app.domain.model.BackupServiceEntry
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The web dashboard (HomelabWeb/src/lib/backup-format.ts) reads and writes the same backup format.
 * The fixture was encrypted by the web module; this keeps both implementations byte-compatible.
 */
class BackupWebCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `backup encrypted by the web dashboard decrypts in the app`() {
        val fixture = requireNotNull(javaClass.classLoader?.getResourceAsStream("web_backup_fixture.homelab")).readBytes()
        val envelope = json.decodeFromString<BackupEnvelope>(String(BackupCrypto.decrypt(fixture, "fixture-password")))

        assertEquals("homelab-web", envelope.appVersion)
        assertEquals(1, envelope.services.size)
        assertEquals("radarr", envelope.services[0].type)
        assertEquals("fixture-api-key", envelope.services[0].apiKey)
    }

    @Test
    fun `app backup is written for the web round-trip check`() {
        val envelope = BackupEnvelope(
            version = BackupEnvelope.CURRENT_VERSION,
            exportedAt = "2026-11-06T10:00:00Z",
            appVersion = "android-test",
            services = listOf(
                BackupServiceEntry(type = "peanut", label = "UPS", url = "http://ups.lan", allowSelfSigned = true, isPreferred = true)
            )
        )
        val encrypted = BackupCrypto.encrypt(json.encodeToString(envelope).toByteArray(), "app-password")
        // Read by the web module in the cross-implementation check; harmless otherwise.
        File("build/app_backup_for_web.homelab").apply { parentFile?.mkdirs() }.writeBytes(encrypted)
        assertEquals(envelope, json.decodeFromString<BackupEnvelope>(String(BackupCrypto.decrypt(encrypted, "app-password"))))
    }
}
