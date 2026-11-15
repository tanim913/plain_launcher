package app.plainlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.view.View
import app.plainlauncher.data.ItemInfo
import kotlin.math.ceil

/** Thin wrapper over AppWidgetHost: create, size, delete. Binding UI lives in the activity. */
class WidgetHelper(private val act: Activity) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(act)
    val host = AppWidgetHost(act, 1024)

    fun start() = runCatching { host.startListening() }
    fun stop() = runCatching { host.stopListening() }

    fun createView(item: ItemInfo): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(item.widgetId) ?: return null
        return runCatching { host.createView(act, item.widgetId, info).also { it.setAppWidget(item.widgetId, info) } }.getOrNull()
    }

    fun delete(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    /** Tell the widget how big it is so it can pick a layout. */
    fun updateSize(v: View, widthPx: Int, heightPx: Int) {
        val hv = v as? AppWidgetHostView ?: return
        val d = act.resources.displayMetrics.density
        val w = (widthPx / d).toInt(); val h = (heightPx / d).toInt()
        runCatching { hv.updateAppWidgetSize(null, w, h, w, h) }
    }

    fun spansFor(info: AppWidgetProviderInfo, cellW: Float, cellH: Float, cols: Int, rows: Int): IntArray {
        val sx = ceil(info.minWidth / cellW).toInt().coerceIn(1, cols)
        val sy = ceil(info.minHeight / cellH).toInt().coerceIn(1, rows)
        return intArrayOf(sx, sy)
    }
}
