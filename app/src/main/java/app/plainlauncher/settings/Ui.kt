package app.plainlauncher.settings

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import app.plainlauncher.ui.Palette
import app.plainlauncher.util.dp

/** Tiny helpers so the two settings screens share one look without a layout/XML dependency. */
fun Activity.settingsScaffold(palette: Palette, titleText: String): LinearLayout {
    // Edge-to-edge on every Android version (Android 15+ enforces it): draw behind the bars and
    // pad the content by their size, with bar icons that match the background.
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !palette.night
        isAppearanceLightNavigationBars = !palette.night
    }
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(24)) }
    val title = TextView(this).apply {
        text = titleText; textSize = 28f; setTextColor(palette.onSurface)
        setPadding(dp(24), dp(28), dp(24), dp(16))
    }
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(palette.surface or 0xFF000000.toInt())
        addView(title)
        addView(ScrollView(this@settingsScaffold).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
        v.setPadding(b.left, b.top, b.right, 0)
        list.setPadding(0, 0, 0, dp(24) + b.bottom)
        insets
    }
    setContentView(root)
    return list
}

fun Context.settingsRow(palette: Palette, title: String, subtitle: String?, onClick: (() -> Unit)?): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(14), dp(24), dp(14))
        addView(TextView(context).apply { text = title; textSize = 16f; setTextColor(palette.onSurface) })
        if (subtitle != null) addView(TextView(context).apply { text = subtitle; textSize = 13f; setTextColor(palette.onSurfaceVariant); setPadding(0, dp(2), 0, 0) })
        if (onClick != null) { isClickable = true; setOnClickListener { onClick() } }
    }

fun Context.settingsToggle(palette: Palette, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
    val sw = Switch(this).apply { isChecked = checked; setOnCheckedChangeListener { _, c -> onChange(c) } }
    val textCol = settingsRow(palette, title, subtitle, null)
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        addView(sw, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(24) })
        isClickable = true
        setOnClickListener { sw.toggle() }
    }
}

fun Context.sectionHeader(palette: Palette, text: String): View =
    TextView(this).apply {
        this.text = text.uppercase(); textSize = 12f; setTextColor(palette.accent)
        letterSpacing = 0.08f
        setPadding(dp(24), dp(20), dp(24), dp(4))
    }
