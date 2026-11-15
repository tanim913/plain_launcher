package app.plainlauncher.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    val hasDock: Boolean get() = sp.contains("dock")

    var dock: List<String>
        get() = sp.getString("dock", null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) = sp.edit().putString("dock", v.joinToString("\n")).apply()

    var iconShape: IconShape
        get() = runCatching { IconShape.valueOf(sp.getString("icon_shape", null)!!) }.getOrDefault(IconShape.CIRCLE)
        set(v) = sp.edit().putString("icon_shape", v.name).apply()

    var cols: Int
        get() = sp.getInt("cols", 5)
        set(v) = sp.edit().putInt("cols", v).apply()

    var rows: Int
        get() = sp.getInt("rows", 5)
        set(v) = sp.edit().putInt("rows", v).apply()

    var homeLabels: Boolean
        get() = sp.getBoolean("home_labels", true)
        set(v) = sp.edit().putBoolean("home_labels", v).apply()

    var doubleTapLock: Boolean
        get() = sp.getBoolean("double_tap_lock", true)
        set(v) = sp.edit().putBoolean("double_tap_lock", v).apply()

    var iconPack: String?
        get() = sp.getString("icon_pack", null)
        set(v) = sp.edit().putString("icon_pack", v).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    var stepsEnabled: Boolean
        get() = sp.getBoolean("steps_enabled", false)
        set(v) = sp.edit().putBoolean("steps_enabled", v).apply()

    var stepGoal: Int
        get() = sp.getInt("step_goal", 8000)
        set(v) = sp.edit().putInt("step_goal", v).apply()

    /** Send walks and steps to Health Connect (for MyNetDiary and similar apps). */
    var healthSync: Boolean
        get() = sp.getBoolean("health_sync", false)
        set(v) = sp.edit().putBoolean("health_sync", v).apply()

    var healthMinWalkMin: Int
        get() = sp.getInt("health_min_walk", 5)
        set(v) = sp.edit().putInt("health_min_walk", v).apply()

    var healthLastSync: Long
        get() = sp.getLong("health_last_sync", 0)
        set(v) = sp.edit().putLong("health_last_sync", v).apply()

    var healthLastWalks: Int
        get() = sp.getInt("health_last_walks", 0)
        set(v) = sp.edit().putInt("health_last_walks", v).apply()

    /** Where steps come from: "auto", "miui", "health" (Health Connect) or "sensor". */
    var stepSource: String
        get() = sp.getString("step_source", "auto") ?: "auto"
        set(v) = sp.edit().putString("step_source", v).apply()

    /** Which [StepArchive] history the current step source feeds ("miui", "sensor", or "" for none). */
    var activeArchive: String
        get() = sp.getString("active_archive", "") ?: ""
        set(v) = sp.edit().putString("active_archive", v).apply()

    /** Last step-counter reading saved by [StepRecorder]. */
    var recorderLast: RecorderMath.Last?
        get() = if (!sp.contains("rec_raw")) null
            else RecorderMath.Last(sp.getLong("rec_raw", 0), sp.getLong("rec_wall", 0), sp.getLong("rec_boot", 0))
        set(v) {
            val e = sp.edit()
            if (v == null) e.remove("rec_raw").remove("rec_wall").remove("rec_boot")
            else e.putLong("rec_raw", v.raw).putLong("rec_wall", v.wall).putLong("rec_boot", v.bootWall)
            e.apply()
        }

    var stepsPage: Boolean
        get() = sp.getBoolean("steps_page", true)
        set(v) = sp.edit().putBoolean("steps_page", v).apply()

    // ---- weather ----
    var weatherEnabled: Boolean
        get() = sp.getBoolean("wx_enabled", true)
        set(v) = sp.edit().putBoolean("wx_enabled", v).apply()

    /** Manually chosen place; null = use device location. */
    var weatherPlace: Place?
        get() {
            val name = sp.getString("wx_place", null) ?: return null
            return Place(name, sp.getFloat("wx_lat", 0f).toDouble(), sp.getFloat("wx_lon", 0f).toDouble())
        }
        set(v) {
            val e = sp.edit()
            if (v == null) e.remove("wx_place").remove("wx_lat").remove("wx_lon")
            else e.putString("wx_place", v.name).putFloat("wx_lat", v.lat.toFloat()).putFloat("wx_lon", v.lon.toFloat())
            e.apply()
        }

    var weatherCache: String?
        get() = sp.getString("wx_cache", null)
        set(v) = sp.edit().putString("wx_cache", v).apply()
}

data class Place(val name: String, val lat: Double, val lon: Double)
