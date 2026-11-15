package app.plainlauncher.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import app.plainlauncher.data.IconPackManager
import app.plainlauncher.data.IconShape
import app.plainlauncher.data.HealthSync
import app.plainlauncher.data.MiuiSteps
import app.plainlauncher.data.StepRecorder
import app.plainlauncher.data.Prefs
import app.plainlauncher.data.StepArchive
import app.plainlauncher.gesture.LockAccessibilityService
import app.plainlauncher.ui.Palette

class SettingsActivity : Activity() {
    private companion object { const val REQ_HEALTH = 21 }
    private lateinit var prefs: Prefs
    private lateinit var palette: Palette
    private lateinit var list: android.widget.LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        palette = Palette(this).also { it.refresh() }
        list = settingsScaffold(palette, "Home settings")
        build()
    }

    override fun onResume() {
        super.onResume()
        hcGranted = null // re-check in the background; permissions may have changed in Health Connect
        if (::list.isInitialized) build()
    }

    private fun dialogTheme() =
        if (palette.night) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    private fun build() {
        list.removeAllViews()
        fun add(v: android.view.View) = list.addView(v)

        add(sectionHeader(palette, "Look"))
        add(settingsRow(palette, "Icon shape", prefs.iconShape.title) {
            val shapes = IconShape.values()
            AlertDialog.Builder(this, dialogTheme()).setTitle("Icon shape")
                .setSingleChoiceItems(shapes.map { it.title }.toTypedArray(), prefs.iconShape.ordinal) { d, i -> prefs.iconShape = shapes[i]; d.dismiss(); build() }
                .show()
        })
        add(settingsRow(palette, "Icon pack", prefs.iconPack?.let { pk -> IconPackManager(this).installed().firstOrNull { it.first == pk }?.second ?: pk } ?: "System default") {
            val packs = IconPackManager(this).installed()
            val names = (listOf("System default") + packs.map { it.second }).toTypedArray()
            val sel = prefs.iconPack?.let { pk -> packs.indexOfFirst { it.first == pk } + 1 } ?: 0
            AlertDialog.Builder(this, dialogTheme()).setTitle(if (packs.isEmpty()) "No icon packs installed" else "Icon pack")
                .setSingleChoiceItems(names, sel) { d, i -> prefs.iconPack = if (i == 0) null else packs[i - 1].first; d.dismiss(); build() }
                .show()
        })
        add(settingsToggle(palette, "App names on home screen", null, prefs.homeLabels) { prefs.homeLabels = it })

        add(sectionHeader(palette, "Home screen"))
        add(settingsRow(palette, "Grid size", "${prefs.cols} × ${prefs.rows}") {
            val opts = listOf(4 to 5, 5 to 5, 5 to 6, 6 to 6)
            val cur = opts.indexOf(prefs.cols to prefs.rows)
            AlertDialog.Builder(this, dialogTheme()).setTitle("Grid size (columns × rows)")
                .setSingleChoiceItems(opts.map { "${it.first} × ${it.second}" }.toTypedArray(), cur) { d, i -> prefs.cols = opts[i].first; prefs.rows = opts[i].second; d.dismiss(); build() }
                .show()
        })
        add(settingsRow(palette, "Hidden apps", "${prefs.hidden.size} hidden") { hiddenAppsDialog() })

        add(sectionHeader(palette, "Gestures"))
        add(settingsRow(palette, "Swipe down", "Opens notifications (works anywhere on the home screen)", null))
        add(settingsToggle(palette, "Double-tap to lock", "Needs the accessibility service below", prefs.doubleTapLock) { prefs.doubleTapLock = it })
        add(settingsRow(palette, "Lock service", if (LockAccessibilityService.isEnabled()) "On" else "Off · tap to turn on in Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        add(settingsRow(palette, "Notification dots", "Tap to allow Plain Launcher in Notification access") {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })

        add(sectionHeader(palette, "Weather"))
        add(settingsToggle(palette, "Show weather", "Long-press the date on the home screen to pick a city", prefs.weatherEnabled) { prefs.weatherEnabled = it })

        add(sectionHeader(palette, "Health"))
        add(settingsToggle(palette, "Show daily steps", "Step count under the date. Swipe right on the first home page for charts.", prefs.stepsEnabled) { prefs.stepsEnabled = it })
        addStepSource(::add)
        addHealthConnect(::add)

        add(sectionHeader(palette, "Ads"))
        add(settingsRow(palette, "Ad Shield", "See which MIUI / Realme ad packages are active") {
            startActivity(Intent(this, AdShieldActivity::class.java))
        })
    }

    // ---- Steps source & Health Connect (MyNetDiary) ------------------------------------------

    /** Health Connect permissions we hold, checked on a background thread; null = not known yet. */
    private var hcGranted: Set<String>? = null
    private var hcChecking = false
    /** What the pending Health Connect permission request was for. */
    private var hcRequestFor = ""

    private fun checkHealthAsync() {
        if (hcChecking) return
        hcChecking = true
        val app = applicationContext
        Thread {
            val g = runCatching { HealthSync.granted(app) }.getOrDefault(emptySet())
            runOnUiThread { hcChecking = false; hcGranted = g; if (!isFinishing && !isDestroyed) build() }
        }.start()
    }

    private fun addStepSource(add: (android.view.View) -> Unit) {
        val miui = MiuiSteps.available(this)
        val sensor = StepRecorder.hasSensor(this)
        val options = buildList {
            add("auto" to "Automatic (best available)")
            if (miui) add("miui" to "MIUI step history")
            add("health" to "Health Connect (Samsung Health, Fit, Android…)")
            if (sensor) add("sensor" to "Phone step sensor (recorded by Plain Launcher)")
        }
        val current = options.firstOrNull { it.first == prefs.stepSource } ?: options[0]
        val hint = when {
            current.first != "auto" -> current.second
            miui -> "Automatic · MIUI step history"
            else -> "Automatic · Health Connect if allowed, else the phone's step sensor"
        }
        add(settingsRow(palette, "Step source", hint) {
            AlertDialog.Builder(this, dialogTheme()).setTitle("Where steps come from")
                .setSingleChoiceItems(options.map { it.second }.toTypedArray(), options.indexOf(current)) { d, i ->
                    d.dismiss()
                    val choice = options[i].first
                    if (choice == "health") chooseHealthSource() else { prefs.stepSource = choice; build() }
                }
                .show()
        })
    }

    private fun chooseHealthSource() {
        when {
            !HealthSync.installed(this) -> { toast("Install Health Connect first"); openHealthConnectStore() }
            hcGranted?.containsAll(HealthSync.READ_PERMISSIONS) == true -> { prefs.stepSource = "health"; build() }
            else -> requestHealthPermissions(HealthSync.READ_PERMISSIONS, "read")
        }
    }

    private fun addHealthConnect(add: (android.view.View) -> Unit) {
        val installed = HealthSync.installed(this)
        if (installed && hcGranted == null) checkHealthAsync()
        val known = hcGranted
        val granted = installed && known?.containsAll(HealthSync.WRITE_PERMISSIONS) == true
        val on = prefs.healthSync && granted
        val sub = when {
            !installed -> "Sends your walks to MyNetDiary and other apps. Tap to install Health Connect from the Play Store first."
            known == null -> "Checking Health Connect…"
            prefs.stepSource == "health" -> "Off while steps come from Health Connect itself"
            !granted -> "Sends each walk as a Walking workout plus your steps. Tap to allow."
            on && prefs.healthLastSync > 0 -> "On · last sent ${android.text.format.DateFormat.format("d MMM, HH:mm", prefs.healthLastSync)}"
            on -> "On · sends every 30 minutes and when you go home"
            else -> "Off"
        }
        add(settingsToggle(palette, "Send walks to Health Connect", sub, on) { want ->
            when {
                installed && known == null -> build()
                !want -> { prefs.healthSync = false; build() }
                !installed -> { openHealthConnectStore(); build() }
                prefs.stepSource == "health" -> { toast("Steps already come from Health Connect"); build() }
                !granted -> requestHealthPermissions(HealthSync.WRITE_PERMISSIONS, "write")
                else -> { prefs.healthSync = true; syncNow(); build() }
            }
        })
        if (on) {
            add(settingsRow(palette, "Walks to send", "At least ${prefs.healthMinWalkMin} min of walking") {
                val opts = intArrayOf(1, 5, 10, 15)
                val labels = arrayOf("All walking (1 min or more)", "5 minutes or more", "10 minutes or more", "15 minutes or more")
                AlertDialog.Builder(this, dialogTheme()).setTitle("Send walks of at least")
                    .setSingleChoiceItems(labels, opts.indexOf(prefs.healthMinWalkMin)) { d, i -> prefs.healthMinWalkMin = opts[i]; d.dismiss(); syncNow(); build() }
                    .show()
            })
            add(settingsRow(palette, "Send now", "MyNetDiary picks it up the next time it syncs") { syncNow(); toast("Sending…") })
        }
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

    private fun openHealthConnectStore() {
        val pkg = HealthSync.PROVIDER
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg")).setPackage("com.android.vending")) }
            .onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) } }
    }

    private fun requestHealthPermissions(perms: Set<String>, purpose: String) {
        hcRequestFor = purpose
        val contract = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
        runCatching { @Suppress("DEPRECATION") startActivityForResult(contract.createIntent(this, perms), REQ_HEALTH) }
            .onFailure { toast("Couldn't open Health Connect") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_HEALTH) return
        val got = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract().parseResult(resultCode, data)
        hcGranted = (hcGranted ?: emptySet()) + got
        when (hcRequestFor) {
            "write" -> if (got.containsAll(HealthSync.WRITE_PERMISSIONS)) { prefs.healthSync = true; syncNow() } else toast("Health Connect permission not given")
            "read" -> if (got.containsAll(HealthSync.READ_PERMISSIONS)) prefs.stepSource = "health" else toast("Health Connect permission not given")
        }
        build()
    }

    private fun syncNow() {
        val app = applicationContext
        Thread {
            HealthSync.pushIfEnabled(app, StepArchive.refreshActive(app), force = true)
            runOnUiThread { if (!isFinishing) build() }
        }.start()
    }

    private fun hiddenAppsDialog() {
        val la = getSystemService(LauncherApps::class.java)
        val serial = getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val apps = la.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != packageName }
            .map { "${it.componentName.flattenToString()}#$serial" to (it.label?.toString() ?: it.componentName.packageName) }
            .sortedBy { it.second.lowercase() }
        val hidden = prefs.hidden.toMutableSet()
        AlertDialog.Builder(this, dialogTheme()).setTitle("Hidden apps")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), BooleanArray(apps.size) { apps[it].first in hidden }) { _, i, on ->
                if (on) hidden.add(apps[i].first) else hidden.remove(apps[i].first)
            }
            .setPositiveButton("Done") { _, _ -> prefs.hidden = hidden; build() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
