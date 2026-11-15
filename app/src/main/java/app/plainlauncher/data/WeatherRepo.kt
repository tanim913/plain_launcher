package app.plainlauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

data class Weather(
    val tempC: Double,
    val highC: Double,
    val lowC: Double,
    val code: Int,
    val isDay: Boolean,
    val fetchedAt: Long,
) {
    fun temp(fahrenheit: Boolean, c: Double = tempC) = "${Math.round(if (fahrenheit) c * 9 / 5 + 32 else c)}°"

    val emoji: String get() = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1, 2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67 -> "🌧️"
        80, 81, 82 -> if (isDay) "🌦️" else "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    val condition: String get() = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, hail"
        else -> ""
    }

    fun toJson(): String = JSONObject().put("t", tempC).put("h", highC).put("l", lowC).put("c", code).put("d", isDay).put("at", fetchedAt).toString()

    companion object {
        fun fromJson(s: String?): Weather? = runCatching {
            val o = JSONObject(s!!)
            Weather(o.getDouble("t"), o.getDouble("h"), o.getDouble("l"), o.getInt("c"), o.getBoolean("d"), o.getLong("at"))
        }.getOrNull()
    }
}

/**
 * Weather from Open-Meteo (free, open source, no API key). Coordinates are rounded to
 * 2 decimals (~1 km) before they leave the phone. Refreshes at most every 30 minutes.
 */
class WeatherRepo(private val ctx: Context, private val prefs: Prefs) {
    enum class Problem { LOCATION_OFF, NO_FIX, NETWORK }

    /** Why the last refresh produced no fresh weather (null = it worked). */
    @Volatile var problem: Problem? = null
        private set

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    val useFahrenheit: Boolean get() = Locale.getDefault().country in setOf("US", "LR", "MM")

    fun cached(): Weather? = Weather.fromJson(prefs.weatherCache)

    fun hasLocationPermission() =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isConfigured() = prefs.weatherPlace != null || hasLocationPermission()

    fun locationEnabled(): Boolean {
        val lm = ctx.getSystemService(LocationManager::class.java)
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun refresh(force: Boolean = false, done: (Weather?) -> Unit) {
        if (!prefs.weatherEnabled) return done(null)
        val c = cached()
        if (!force && c != null && System.currentTimeMillis() - c.fetchedAt < 30 * 60_000L) return done(c)
        val place = prefs.weatherPlace
        if (place != null) return fetch(place.lat, place.lon, done)
        if (!locationEnabled()) { problem = Problem.LOCATION_OFF; return done(c) }
        currentLocation { loc ->
            if (loc != null) fetch(loc.latitude, loc.longitude, done)
            else ipLocation { ip ->
                if (ip != null) fetch(ip.latitude, ip.longitude, done) else { problem = Problem.NO_FIX; done(c) }
            }
        }
    }

    private fun fetch(lat: Double, lon: Double, done: (Weather?) -> Unit) {
        io.execute {
            val w = runCatching {
                val url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                        "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                        "&forecast_days=1&timezone=auto",
                    lat, lon,
                )
                val o = JSONObject(get(url))
                val cur = o.getJSONObject("current")
                val daily = o.getJSONObject("daily")
                Weather(
                    cur.getDouble("temperature_2m"),
                    daily.getJSONArray("temperature_2m_max").getDouble(0),
                    daily.getJSONArray("temperature_2m_min").getDouble(0),
                    cur.getInt("weather_code"),
                    cur.getInt("is_day") == 1,
                    System.currentTimeMillis(),
                )
            }.getOrNull()
            problem = if (w == null) Problem.NETWORK else null
            if (w != null) prefs.weatherCache = w.toJson()
            main.post { done(w ?: cached()) }
        }
    }

    fun searchPlaces(query: String, done: (List<Place>) -> Unit) {
        io.execute {
            val list = runCatching {
                val q = URLEncoder.encode(query.trim(), "UTF-8")
                val arr = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=en&format=json"))
                    .optJSONArray("results")
                (0 until (arr?.length() ?: 0)).map { i ->
                    val r = arr!!.getJSONObject(i)
                    val label = listOf(r.optString("name"), r.optString("admin1"), r.optString("country"))
                        .filter { it.isNotBlank() }.distinct().joinToString(", ")
                    Place(label, r.getDouble("latitude"), r.getDouble("longitude"))
                }
            }.getOrDefault(emptyList())
            main.post { done(list) }
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "PlainLauncher")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /**
     * Ask network and GPS together and take whichever answers first; give up after 12 s.
     * MIUI's network provider sometimes never answers, so the timeout matters.
     */
    @SuppressLint("MissingPermission")
    private fun currentLocation(done: (Location?) -> Unit) {
        if (!hasLocationPermission()) return done(null)
        val lm = ctx.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3600_000L) return done(last)

        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        lateinit var listener: LocationListener
        val finish = { l: Location? ->
            if (finished.compareAndSet(false, true)) {
                runCatching { lm.removeUpdates(listener) }
                done(l ?: last)
            }
        }
        listener = object : LocationListener {
            override fun onLocationChanged(l: Location) = finish(l)
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        var asked = 0
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            if (runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) {
                runCatching { lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper()); asked++ }
            }
        }
        if (asked == 0) return finish(null)
        main.postDelayed({ finish(null) }, 12_000L)
    }

    /** Last resort: approximate (city-level) position from the network address, via GeoJS (no key). */
    private fun ipLocation(done: (Location?) -> Unit) {
        io.execute {
            val loc = runCatching {
                val o = JSONObject(get("https://get.geojs.io/v1/ip/geo.json"))
                Location("ip").apply {
                    latitude = o.getString("latitude").toDouble()
                    longitude = o.getString("longitude").toDouble()
                }
            }.getOrNull()
            main.post { done(loc) }
        }
    }
}
