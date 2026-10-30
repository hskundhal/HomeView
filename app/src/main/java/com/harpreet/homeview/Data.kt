package com.harpreet.homeview

import biweekly.Biweekly
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.max

object Net {
    fun get(link: String): String {
        var u = link.trim()
        if (u.startsWith("webcal://", ignoreCase = true)) u = "https://" + u.substring(9)
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "HomeView/1.0 (Android)")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

object Weather {
    fun url(lat: String, lon: String): String =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day,uv_index,wind_speed_10m,wind_gusts_10m" +
            "&hourly=weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=2"

    /** Returns raw Open-Meteo JSON (validated). */
    fun fetch(lat: String, lon: String): String {
        val text = Net.get(url(lat, lon))
        JSONObject(text).getJSONObject("current") // throws if the response is not what we expect
        return text
    }
}

/**
 * Reads Google Calendar "secret iCal" feeds and returns every event happening
 * today or tomorrow (recurring events expanded) as JSON:
 * [{"t": title, "s": startMillis, "e": endMillis, "a": allDay}, ...]
 */
object CalendarFeed {
    private const val DAY = 24L * 60 * 60 * 1000

    fun fetch(urls: List<String>): JSONArray {
        val out = JSONArray()
        for (u in urls) {
            try {
                collect(Net.get(u), out)
            } catch (e: Exception) {
                // one bad calendar shouldn't hide the others
            }
        }
        return out
    }

    private fun collect(text: String, out: JSONArray) {
        val local = TimeZone.getDefault()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val winStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 2)
        val winEnd = cal.timeInMillis

        for (ical in Biweekly.parse(text).all()) {
            val tzInfo = ical.timezoneInfo
            val events = ical.events

            // Single changed/cancelled instances of a repeating event
            val overridden = HashSet<String>()
            for (ev in events) {
                val rid = ev.recurrenceId?.value ?: continue
                overridden.add((ev.uid?.value ?: "") + "|" + rid.time)
            }

            for (ev in events) {
                try {
                    val ds = ev.dateStart ?: continue
                    val start = ds.value ?: continue
                    if (ev.status?.value?.equals("CANCELLED", ignoreCase = true) == true) continue

                    val allDay = !start.hasTime()
                    val endDate = ev.dateEnd?.value
                    val dur: Long = max(0L, when {
                        endDate != null -> endDate.time - start.time
                        ev.duration?.value != null -> ev.duration.value.toMillis()
                        allDay -> DAY
                        else -> 0L
                    })
                    val title = ev.summary?.value?.trim().orEmpty().ifEmpty { "Busy" }
                    val uid = ev.uid?.value ?: ""
                    val repeating = ev.recurrenceRule != null || ev.recurrenceDates.isNotEmpty()

                    if (!repeating) {
                        add(out, title, start.time, dur, allDay, winStart, winEnd)
                        continue
                    }

                    val tz: TimeZone = when {
                        allDay -> local
                        else -> tzInfo.getTimezone(ds)?.timeZone
                            ?: if (tzInfo.isFloating(ds)) local else TimeZone.getTimeZone("UTC")
                    }
                    val iter = ev.getDateIterator(tz)
                    iter.advanceTo(Date(winStart - dur - DAY))
                    var guard = 0
                    while (iter.hasNext() && guard++ < 500) {
                        val s = iter.next().time
                        if (s >= winEnd) break
                        if (ev.recurrenceId == null && overridden.contains("$uid|$s")) continue
                        add(out, title, s, dur, allDay, winStart, winEnd)
                    }
                } catch (e: Exception) {
                    // skip events we can't understand
                }
            }
        }
    }

    private fun add(out: JSONArray, title: String, s: Long, dur: Long, allDay: Boolean, winStart: Long, winEnd: Long) {
        val e = s + dur
        val inWindow = s < winEnd && (e > winStart || (dur == 0L && s >= winStart))
        if (!inWindow) return
        out.put(JSONObject().put("t", title).put("s", s).put("e", e).put("a", allDay))
    }
}
