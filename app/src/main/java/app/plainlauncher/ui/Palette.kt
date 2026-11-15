package app.plainlauncher.ui

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.core.graphics.ColorUtils

/** Wallpaper- and system-theme-derived colours. Recomputed when the wallpaper or night mode changes. */
class Palette(private val ctx: Context) {
    var night = false; private set
    var wallpaperLight = false; private set
    var accent = 0xFF6750A4.toInt(); private set
    var surface = 0; private set
    var surfaceVariant = 0; private set
    var onSurface = 0; private set
    var onSurfaceVariant = 0; private set
    var homeText = Color.WHITE; private set

    fun refresh() {
        night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        var primary = 0xFF6750A4.toInt()
        wallpaperLight = false
        if (Build.VERSION.SDK_INT >= 27) {
            try {
                val wc = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                if (wc != null) {
                    primary = wc.primaryColor.toArgb()
                    wallpaperLight = ColorUtils.calculateLuminance(primary) > 0.55
                }
            } catch (_: Exception) {
            }
        }
        val base = if (night) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()
        surface = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(base, primary, if (night) 0.10f else 0.06f), 0xF5)
        surfaceVariant = ColorUtils.blendARGB(base, primary, if (night) 0.22f else 0.14f)
        onSurface = if (night) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
        onSurfaceVariant = if (night) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
        accent = if (night) ColorUtils.blendARGB(primary, Color.WHITE, 0.35f) else ColorUtils.blendARGB(primary, Color.BLACK, 0.2f)
        homeText = if (wallpaperLight) 0xFF1C1B1F.toInt() else Color.WHITE
    }

    /**
     * A saturated version of the wallpaper accent for charts and rings. Grey wallpapers give a
     * grey accent that disappears on a dark card, so those fall back to a fresh green.
     */
    val vivid: Int
        get() {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(accent, hsl)
            if (hsl[1] < 0.25f) hsl[0] = 150f
            hsl[1] = maxOf(hsl[1], 0.62f)
            hsl[2] = if (night) 0.64f else 0.40f
            return ColorUtils.HSLToColor(hsl)
        }

    /** Glassy pill fill that reads on both light and dark wallpapers. */
    val pillFill: Int
        get() = if (wallpaperLight) 0xC8FFFFFF.toInt() else 0x2EFFFFFF

    val pillText: Int
        get() = if (wallpaperLight) 0xFF49454F.toInt() else 0xEBFFFFFF.toInt()
}
