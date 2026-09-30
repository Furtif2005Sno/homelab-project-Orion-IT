package com.homelab.app.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/** PeaNUT (Brandawg93/PeaNUT), a dashboard for Network UPS Tools. */
interface PeanutApi {

    /** Every UPS with its flattened NUT variables; `meta=true` adds `peanut.device_id`. */
    @GET("api/v1/devices")
    suspend fun getDevices(
        @Header("X-Homelab-Service") service: String = "PeaNUT",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("meta") meta: Boolean = true
    ): ResponseBody

    @GET("api/v1/info")
    suspend fun getInfo(
        @Header("X-Homelab-Service") service: String = "PeaNUT",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ResponseBody
}
