package app.plainlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** An Adw/Nova/Go-style icon pack: maps app components to drawables via appfilter.xml. */
class IconPack(val pkg: String, private val res: Resources, private val map: Map<String, String>) {
    fun drawableFor(c: ComponentName): Drawable? {
        val name = map[c.flattenToString()] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", pkg).takeIf { it != 0 } ?: return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }
}

class IconPackManager(private val ctx: Context) {
    private val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")

    fun installed(): List<Pair<String, String>> {
        val pm = ctx.packageManager
        return actions.flatMap { pm.queryIntentActivities(Intent(it), 0) }
            .map { it.activityInfo.packageName }.distinct()
            .map { it to (runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it)) }
            .sortedBy { it.second.lowercase() }
    }

    fun load(pkg: String): IconPack? = runCatching {
        val pc = ctx.createPackageContext(pkg, 0)
        val res = pc.resources
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (xmlId != 0) res.getXml(xmlId)
        else XmlPullParserFactory.newInstance().newPullParser().apply { setInput(pc.assets.open("appfilter.xml"), null) }
        val map = HashMap<String, String>()
        val re = Regex("ComponentInfo\\{(.+)\\}")
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                val m = comp?.let { re.find(it) }
                if (m != null && drawable != null) map.putIfAbsent(m.groupValues[1], drawable)
            }
            ev = parser.next()
        }
        IconPack(pkg, res, map)
    }.getOrNull()
}
