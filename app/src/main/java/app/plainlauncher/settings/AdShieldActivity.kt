package app.plainlauncher.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import app.plainlauncher.ui.Palette
import app.plainlauncher.util.dp

/**
 * Read-only status of the known ad/bloat packages, plus the exact commands to turn them off.
 * Disabling system packages needs shell access, so that step stays on the PC (adshield.sh).
 */
class AdShieldActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = Palette(this).also { it.refresh() }
        val list = settingsScaffold(palette, "Ad Shield")

        val brand = Build.MANUFACTURER.lowercase()
        val file = if (brand.contains("realme") || brand.contains("oppo")) "realme.txt" else "miui.txt"
        val rows = runCatching {
            assets.open(file).bufferedReader().readLines().mapNotNull { line ->
                val t = line.substringBefore('#').trim().split(Regex("\\s+"))
                if (t.size >= 2) t[0] to t[1] else null
            }
        }.getOrDefault(emptyList())

        val pm = packageManager
        fun state(pkg: String): String = try {
            when (pm.getApplicationEnabledSetting(pkg)) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "Disabled"
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "Active"
                else -> if (pm.getApplicationInfo(pkg, 0).enabled) "Active" else "Disabled"
            }
        } catch (_: Exception) { "Not installed" }

        val states = rows.map { Triple(it.first, it.second, state(it.second)) }
        val active = states.count { it.third == "Active" && it.first == "ads" }
        list.addView(settingsRow(palette,
            if (active == 0) "No ad packages are active" else "$active ad packages are still active",
            "Showing the ${if (file == "realme.txt") "Realme UI" else "MIUI"} list, ${states.size} known packages.", null))

        for (tier in listOf("ads" to "Ad & analytics services", "bloat" to "Apps with ads (optional)")) {
            list.addView(sectionHeader(palette, tier.second))
            for ((t, pkg, st) in states.filter { it.first == tier.first }) {
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                list.addView(settingsRow(palette, label, "$pkg · $st", null))
            }
        }

        list.addView(sectionHeader(palette, "To turn them off"))
        val cmd = "cd PlainLauncher/adshield && ./adshield.sh apply --bloat --yes"
        list.addView(settingsRow(palette, "Run on your PC (USB debugging on)", cmd) { copy(cmd) })
        val dns = "dns.adguard-dns.com"
        list.addView(settingsRow(palette, "Block ads in every app", "Settings › Connection & sharing › Private DNS › hostname: $dns (tap to copy)") { copy(dns) })
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Plain Launcher", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
}
