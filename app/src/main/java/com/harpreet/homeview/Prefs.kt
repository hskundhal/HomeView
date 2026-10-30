package com.harpreet.homeview

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

object Prefs {
    const val DEFAULT_LAT = "43.6834"   // Brampton, ON
    const val DEFAULT_LON = "-79.7663"

    fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("homeview", Context.MODE_PRIVATE)

    data class Cam(
        val name: String,
        val host: String,
        val port: String,
        val user: String,
        val pass: String,
        val mainPath: String,
        val subPath: String
    ) {
        val configured: Boolean get() = host.isNotBlank()

        /** RTSP link for this camera. main = HD stream (full screen), otherwise low-res stream (grid). */
        fun url(main: Boolean): String? {
            if (!configured) return null
            val h = host.trim()
            if (h.startsWith("rtsp://", ignoreCase = true)) return h   // user pasted a full link
            val auth = if (user.isNotBlank()) Uri.encode(user.trim()) + ":" + Uri.encode(pass) + "@" else ""
            var path = (if (main) mainPath else subPath).trim()
            if (path.isEmpty()) path = if (main) "/11" else "/12"
            if (!path.startsWith("/")) path = "/$path"
            val p = port.trim().ifEmpty { "554" }
            return "rtsp://$auth$h:$p$path"
        }
    }

    fun cam(c: Context, i: Int): Cam {
        val s = sp(c)
        return Cam(
            name = s.getString("cam${i}_name", null) ?: "Camera ${i + 1}",
            host = s.getString("cam${i}_host", "") ?: "",
            port = s.getString("cam${i}_port", "554") ?: "554",
            user = s.getString("cam${i}_user", "admin") ?: "admin",
            pass = s.getString("cam${i}_pass", "") ?: "",
            mainPath = s.getString("cam${i}_main", "/11") ?: "/11",
            subPath = s.getString("cam${i}_sub", "/12") ?: "/12"
        )
    }

    fun calendarUrls(c: Context): List<String> =
        (sp(c).getString("cal_urls", "") ?: "")
            .split("\n", " ", ",")
            .map { it.trim() }
            .filter { it.startsWith("http", true) || it.startsWith("webcal", true) }

    fun lat(c: Context): String = (sp(c).getString("lat", null) ?: "").ifBlank { DEFAULT_LAT }
    fun lon(c: Context): String = (sp(c).getString("lon", null) ?: "").ifBlank { DEFAULT_LON }
}
