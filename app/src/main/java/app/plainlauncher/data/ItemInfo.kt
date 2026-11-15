package app.plainlauncher.data

import org.json.JSONObject

/** One thing placed on the home screen, in the dock, or inside a folder. */
class ItemInfo(
    var id: Long,
    var type: Int,
    var container: Int,
    var containerId: Long = -1,
    var screen: Int = 0,
    var cellX: Int = 0,
    var cellY: Int = 0,
    var spanX: Int = 1,
    var spanY: Int = 1,
    var appKey: String? = null,
    var title: String? = null,
    var widgetId: Int = -1,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type).put("container", container).put("containerId", containerId)
        .put("screen", screen).put("x", cellX).put("y", cellY).put("sx", spanX).put("sy", spanY)
        .put("app", appKey).put("title", title).put("widget", widgetId)

    companion object {
        const val APP = 0
        const val FOLDER = 1
        const val WIDGET = 2

        const val HOME = 0
        const val DOCK = 1
        const val IN_FOLDER = 2

        fun fromJson(o: JSONObject) = ItemInfo(
            o.getLong("id"), o.getInt("type"), o.getInt("container"), o.optLong("containerId", -1),
            o.optInt("screen"), o.optInt("x"), o.optInt("y"), o.optInt("sx", 1), o.optInt("sy", 1),
            if (o.isNull("app")) null else o.getString("app"),
            if (o.isNull("title")) null else o.getString("title"),
            o.optInt("widget", -1),
        )
    }
}
