package app.plainlauncher.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Home layout, persisted as a small JSON file (atomic write, debounced). */
class LayoutStore(ctx: Context) {
    val items = ArrayList<ItemInfo>()
    var pages = 1
    var initialized = false
    private var nextId = 1L
    private val file = File(ctx.filesDir, "layout.json")
    private val handler = Handler(Looper.getMainLooper())
    private val writer = Runnable { writeNow() }

    fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            pages = o.optInt("pages", 1).coerceAtLeast(1)
            nextId = o.optLong("next", 1)
            initialized = o.optBoolean("init", false)
            val a = o.getJSONArray("items")
            for (i in 0 until a.length()) items.add(ItemInfo.fromJson(a.getJSONObject(i)))
        }.onFailure { items.clear(); pages = 1 }
    }

    fun save() {
        handler.removeCallbacks(writer)
        handler.postDelayed(writer, 300)
    }

    fun flush() {
        handler.removeCallbacks(writer)
        writeNow()
    }

    private fun writeNow() {
        runCatching {
            val a = JSONArray(); items.forEach { a.put(it.toJson()) }
            val tmp = File(file.parentFile, "layout.json.tmp")
            tmp.writeText(JSONObject().put("pages", pages).put("next", nextId).put("init", initialized).put("items", a).toString())
            tmp.renameTo(file)
        }
    }

    fun add(i: ItemInfo): ItemInfo { i.id = nextId++; items.add(i); save(); return i }

    /** Removes the item and anything inside it (folder contents). */
    fun remove(i: ItemInfo) {
        items.removeAll { it.id == i.id || (it.container == ItemInfo.IN_FOLDER && it.containerId == i.id) }
        save()
    }

    fun home(screen: Int) = items.filter { it.container == ItemInfo.HOME && it.screen == screen }
    fun dock() = items.filter { it.container == ItemInfo.DOCK }
    fun folderChildren(id: Long) = items.filter { it.container == ItemInfo.IN_FOLDER && it.containerId == id }.sortedBy { it.cellX }
    fun byId(id: Long) = items.firstOrNull { it.id == id }

    /** Drop empty pages (keeping at least one) and renumber the rest. */
    fun compactPages() {
        val used = items.filter { it.container == ItemInfo.HOME }.map { it.screen }.distinct().sorted()
        val remap = used.withIndex().associate { it.value to it.index }
        items.filter { it.container == ItemInfo.HOME }.forEach { it.screen = remap[it.screen] ?: 0 }
        pages = maxOf(1, used.size)
        save()
    }

    /** A folder with one app dissolves into that app; an empty folder disappears. */
    fun normalizeFolder(folder: ItemInfo) {
        val kids = folderChildren(folder.id)
        when {
            kids.isEmpty() -> items.remove(folder)
            kids.size == 1 -> {
                val k = kids[0]
                k.container = folder.container; k.containerId = -1
                k.screen = folder.screen; k.cellX = folder.cellX; k.cellY = folder.cellY
                items.remove(folder)
            }
        }
        save()
    }

    /** After a grid-size change, move anything that no longer fits to the first free cell. */
    fun fitToGrid(cols: Int, rows: Int) {
        val homeItems = items.filter { it.container == ItemInfo.HOME }
        homeItems.forEach { it.spanX = minOf(it.spanX, cols); it.spanY = minOf(it.spanY, rows) }
        val models = HashMap<Int, GridModel>()
        fun model(s: Int) = models.getOrPut(s) { GridModel(cols, rows) }
        val misplaced = ArrayList<ItemInfo>()
        for (it in homeItems.sortedWith(compareBy({ it.screen }, { it.cellY }, { it.cellX }))) {
            val g = model(it.screen)
            if (g.isFree(it.cellX, it.cellY, it.spanX, it.spanY)) g.occupy(it.cellX, it.cellY, it.spanX, it.spanY) else misplaced += it
        }
        for (it in misplaced) {
            var s = 0
            while (true) {
                val spot = model(s).firstFree(it.spanX, it.spanY)
                if (spot != null) { it.screen = s; it.cellX = spot[0]; it.cellY = spot[1]; model(s).occupy(spot[0], spot[1], it.spanX, it.spanY); break }
                s++
            }
        }
        compactPages()
    }
}
