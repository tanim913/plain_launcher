package app.plainlauncher.ui

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.plainlauncher.R
import app.plainlauncher.data.AppEntry
import app.plainlauncher.data.AppRepository
import app.plainlauncher.data.GridModel
import app.plainlauncher.data.IconCache
import app.plainlauncher.data.IconPackManager
import app.plainlauncher.data.ItemInfo
import app.plainlauncher.data.LayoutStore
import app.plainlauncher.data.Prefs
import app.plainlauncher.data.StepRepo
import app.plainlauncher.data.Weather
import app.plainlauncher.data.WeatherRepo
import app.plainlauncher.gesture.LockAccessibilityService
import app.plainlauncher.settings.SettingsActivity
import app.plainlauncher.util.dp
import app.plainlauncher.util.dpF
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.abs

class LauncherActivity : Activity(), AppActions, SheetHost, Workspace.Host, DragController.Listener, FolderOverlay.Host {
    private lateinit var palette: Palette
    private lateinit var prefs: Prefs
    private lateinit var store: LayoutStore
    private lateinit var repo: AppRepository
    private lateinit var icons: IconCache
    private lateinit var packs: IconPackManager
    private lateinit var weatherRepo: WeatherRepo
    private lateinit var stepRepo: StepRepo
    private lateinit var widgets: WidgetHelper
    private lateinit var popup: ItemPopup
    private lateinit var launcherApps: LauncherApps
    private lateinit var drag: DragController

    private lateinit var root: HomeRoot
    private lateinit var home: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var detailText: TextView
    private lateinit var workspace: Workspace
    private lateinit var dots: PageDots
    private lateinit var dock: CellLayout
    private lateinit var pill: TextView
    private lateinit var banner: TextView
    private lateinit var sheet: AllAppsSheet
    private lateinit var folderOverlay: FolderOverlay
    private lateinit var dragLayer: FrameLayout
    private lateinit var dropBar: LinearLayout
    private lateinit var removeZone: TextView
    private lateinit var uninstallZone: TextView

    private var apps: List<AppEntry> = emptyList()
    private var appsByKey: Map<String, AppEntry> = emptyMap()
    private var weather: Weather? = null
    private var insetTop = 0
    private var insetBottom = 0
    private var sheetOpen = false
    private var sheetAnim: SpringAnimation? = null
    private lateinit var stepsPanel: StepsPanel
    private var panelOpen = false
    private var panelAnim: SpringAnimation? = null
    private var sheetProgress = 0f
    private var panelProgress = 0f
    private var appliedKey = ""
    private var appliedPack: String? = null
    private var lockHintShown = false
    private var askedSteps = false
    /** Set by a long-press on the detail line so the span tap that follows on finger-up is ignored. */
    private var detailLongPressed = false

    // drag state
    private enum class Zone { NONE, REMOVE, UNINSTALL, DOCK, WORKSPACE }
    private var draggingItem: ItemInfo? = null
    private var edgeDir = 0
    private val handler = Handler(Looper.getMainLooper())
    private val edgeRunnable = Runnable { flipPage(edgeDir) }
    private val io = Executors.newSingleThreadExecutor()

    // widget binding state
    private var pendingWidgetId = -1

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == Intent.ACTION_WALLPAPER_CHANGED) { palette.refresh(); applyTheme() }
            refreshHeader()
        }
    }

    // ---- lifecycle -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palette(this).also { it.refresh() }
        prefs = Prefs(this)
        store = LayoutStore(this).also { it.load() }
        launcherApps = getSystemService(LauncherApps::class.java)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        repo = AppRepository(this) { onAppsChanged(it) }
        icons = IconCache(this, repo, prefs)
        packs = IconPackManager(this)
        weatherRepo = WeatherRepo(this, prefs)
        weather = weatherRepo.cached()
        stepRepo = StepRepo(this, prefs).also { it.onChange = { refreshHeader() } }
        widgets = WidgetHelper(this)
        popup = ItemPopup(this, palette)
        buildViews()
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Edge-to-edge windows aren't resized for the keyboard, so pad the drawer by the IME inset.
            val bottomIncludingKeyboard = maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            insetTop = bars.top
            insetBottom = bars.bottom
            layoutHome()
            sheet.applyInsets(bars.top, bottomIncludingKeyboard)
            stepsPanel.applyInsets(bars.top, bars.bottom)
            (dropBar.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + dp(10)
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!sheetOpen && sheetAnim?.isRunning != true) parkSheet()
            if (!panelOpen && panelAnim?.isRunning != true) parkPanel()
        }
        applyTheme()
        registerBack()
        repo.start()
    }

    override fun onStart() {
        super.onStart()
        widgets.start()
        stepRepo.start()
        runCatching { contentResolver.registerContentObserver(Settings.System.getUriFor("next_alarm_formatted"), false, alarmObserver) }
        runCatching { contentResolver.registerContentObserver(Settings.System.getUriFor("next_alarm_clock_formatted"), false, alarmObserver) }
        // System broadcasts only, so the receiver need not be exported (required flag on Android 14+).
        ContextCompat.registerReceiver(this, clockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            @Suppress("DEPRECATION") addAction(Intent.ACTION_WALLPAPER_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        widgets.stop()
        stepRepo.stop()
        runCatching { contentResolver.unregisterContentObserver(alarmObserver) }
        runCatching { unregisterReceiver(clockReceiver) }
        popup.dismiss()
        store.flush()
    }

    override fun onResume() {
        super.onResume()
        applyPrefsIfChanged()
        ensureSteps()
        refreshHeader()
        refreshWeather(force = false)
        if (panelOpen) stepsPanel.reload()
        banner.visibility = if (isDefaultHome()) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        repo.stop()
        store.flush()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            popup.dismiss()
            folderOverlay.close()
            if (sheetOpen) settle(open = false, velocity = 0f)
            else if (panelOpen) settlePanel(open = false, velocity = 0f)
            else if (workspace.currentPage() != 0) workspace.scrollToPage(0, true)
        }
    }

    /**
     * Back: Android 13+ delivers it through OnBackInvokedCallback (and Android 16 no longer calls
     * onBackPressed for apps targeting it), older versions through onBackPressed.
     */
    private fun registerBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
        }
    }

    /** Android 8-12 only: on 13+ the manifest opts into OnBackInvokedCallback, so this is never called there. */
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = handleBack()

    private fun handleBack() {
        popup.dismiss()
        when {
            folderOverlay.isOpen -> folderOverlay.close()
            sheetOpen -> settle(open = false, velocity = 0f)
            panelOpen -> settlePanel(open = false, velocity = 0f)
            else -> if (workspace.currentPage() != 0) workspace.scrollToPage(0, true)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        palette.refresh()
        applyTheme()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        icons.trim(level)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STEPS) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                stepRepo.reconfigure { refreshHeader(); if (panelOpen) stepsPanel.reload() }
            } else {
                toast("Counting steps needs the Physical activity permission")
                refreshHeader()
            }
        }
        if (requestCode == REQ_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                prefs.weatherPlace = null
                if (!weatherRepo.locationEnabled()) locationOffDialog()
                refreshWeather(force = true)
            } else {
                chooseCity()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) afterBind(pendingWidgetId) else dropPendingWidget()
            REQ_CONFIGURE -> if (resultCode == RESULT_OK) placeWidget(pendingWidgetId) else dropPendingWidget()
        }
    }

    // ---- views -----------------------------------------------------------------------------

    private fun buildViews() {
        root = HomeRoot(this, this)
        home = FrameLayout(this)

        // "At a glance": date + weather on one line, details underneath.
        dateText = TextView(this).apply {
            textSize = 22f
            setOnClickListener { openCalendar() }
            setOnLongClickListener { showWeatherMenu(it); true }
        }
        weatherText = TextView(this).apply {
            textSize = 22f
            setPadding(dp(14), 0, 0, 0)
            setOnClickListener { if (weather != null) openWeatherApp(it) else showWeatherMenu(it) }
            setOnLongClickListener { showWeatherMenu(it); true }
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dateText); addView(weatherText)
        }
        detailText = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(4), 0, 0)
            // Each part (weather, steps, alarm) is its own tap target via ClickableSpans.
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            highlightColor = android.graphics.Color.TRANSPARENT
            setOnTouchListener { _, e -> if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) detailLongPressed = false; false }
            setOnLongClickListener { detailLongPressed = true; showWeatherMenu(it); true }
        }
        header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topRow); addView(detailText)
        }

        workspace = Workspace(this).also { it.host = this; it.onScrolledFraction = { f -> dots.position = f } }
        dots = PageDots(this)

        dock = CellLayout(this, 5, 1).apply { setPadding(dockSideMargin(), 0, dockSideMargin(), 0) }

        banner = TextView(this).apply {
            text = "Set Plain Launcher as your home app"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            visibility = View.GONE
            setOnClickListener { requestDefaultHome() }
        }
        pill = TextView(this).apply {
            text = "Search apps"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            compoundDrawablePadding = dp(14)
            setOnClickListener { openSheetForSearch() }
        }

        val wrap = FrameLayout.LayoutParams.WRAP_CONTENT
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        home.addView(workspace, FrameLayout.LayoutParams(match, match, Gravity.TOP))
        home.addView(header, FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.START))
        home.addView(dots, FrameLayout.LayoutParams(match, dp(16), Gravity.BOTTOM))
        home.addView(dock, FrameLayout.LayoutParams(match, dp(72), Gravity.BOTTOM))
        home.addView(banner, FrameLayout.LayoutParams(wrap, dp(40), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        home.addView(pill, FrameLayout.LayoutParams(match, dp(52), Gravity.BOTTOM))

        sheet = AllAppsSheet(this, this, icons, palette)
        sheet.visibility = View.INVISIBLE
        folderOverlay = FolderOverlay(this, palette, icons, this)

        removeZone = zone("Remove")
        uninstallZone = zone("Uninstall")
        dropBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(removeZone, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(16); marginEnd = dp(8) })
            addView(uninstallZone, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8); marginEnd = dp(16) })
        }
        dragLayer = FrameLayout(this)
        dragLayer.addView(dropBar, FrameLayout.LayoutParams(match, wrap, Gravity.TOP))
        drag = DragController(this, dragLayer, icons.sizePx, this)
        root.drag = drag

        stepsPanel = StepsPanel(this, palette, prefs, stepRepo, dialogTheme = { dialogTheme() }, askPermission = { askStepPermission() })
        stepsPanel.visibility = View.INVISIBLE

        root.addView(home, FrameLayout.LayoutParams(match, match))
        root.addView(stepsPanel, FrameLayout.LayoutParams(match, match))
        root.addView(sheet, FrameLayout.LayoutParams(match, match))
        root.addView(folderOverlay, FrameLayout.LayoutParams(match, match))
        root.addView(dragLayer, FrameLayout.LayoutParams(match, match))
    }

    private fun zone(label: String) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply { setColor(0xCC202020.toInt()); cornerRadius = dpF(24f) }
    }

    /** Everything lines up on the dock's 5-column grid. */
    private fun dockSideMargin() = dp(12)
    private fun slotWidth() = (resources.displayMetrics.widthPixels - 2 * dockSideMargin()) / 5
    private fun gridLeft() = dockSideMargin() + (slotWidth() - icons.sizePx) / 2

    /** Set margins as plain left/top/right/bottom and re-apply the params so they always take effect. */
    private fun View.setMargins(l: Int? = null, t: Int? = null, r: Int? = null, b: Int? = null) {
        val lp = layoutParams as FrameLayout.LayoutParams
        l?.let { lp.leftMargin = it }; t?.let { lp.topMargin = it }
        r?.let { lp.rightMargin = it }; b?.let { lp.bottomMargin = it }
        layoutParams = lp
    }

    private fun layoutHome() {
        val side = gridLeft()
        header.setMargins(l = side + dp(20), t = insetTop + dp(32), r = side + dp(20))
        val pillBottom = maxOf(insetBottom, dp(8)) + dp(14)
        pill.setMargins(l = side, r = side, b = pillBottom)
        val dockBottom = pillBottom + dp(52) + dp(14)
        dock.setMargins(b = dockBottom)
        val dotsBottom = dockBottom + dp(72) + dp(4)
        dots.setMargins(b = dotsBottom)
        workspace.setMargins(t = insetTop + dp(32) + dp(92), b = dotsBottom + dp(20))
        banner.setMargins(b = dotsBottom + dp(28))
    }

    private fun applyTheme() {
        val shadow = if (palette.wallpaperLight) 0 else 0x59000000
        for (t in listOf(dateText, weatherText, detailText)) {
            t.setTextColor(palette.homeText)
            t.setShadowLayer(dpF(3f), 0f, dpF(0.5f), shadow)
        }
        detailText.alpha = 0.85f
        if (::stepsPanel.isInitialized) stepsPanel.applyPalette()
        dots.activeColor = palette.homeText
        dots.idleColor = (palette.homeText and 0x00FFFFFF) or 0x66000000
        pill.background = GradientDrawable().apply { setColor(palette.pillFill); cornerRadius = dpF(26f) }
        pill.setTextColor(palette.pillText)
        ContextCompat.getDrawable(this, R.drawable.ic_search)?.mutate()?.let {
            it.setTint(palette.pillText)
            it.setBounds(0, 0, dp(24), dp(24))
            pill.setCompoundDrawables(it, null, null, null)
        }
        banner.background = GradientDrawable().apply { setColor(palette.accent); cornerRadius = dpF(20f) }
        banner.setTextColor(0xFFFFFFFF.toInt())
        sheet.applyTheme()
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = palette.wallpaperLight
            isAppearanceLightNavigationBars = palette.wallpaperLight
        }
        refreshHome()
    }

    // ---- data ------------------------------------------------------------------------------

    private fun onAppsChanged(list: List<AppEntry>) {
        apps = list
        appsByKey = list.associateBy { it.key }
        if (!store.initialized && list.isNotEmpty()) initStore()
        sheet.submit(visibleApps())
        refreshHome()
    }

    private fun visibleApps() = apps.filter { it.key !in prefs.hidden }

    private fun initStore() {
        val keys = if (prefs.hasDock) prefs.dock else defaultDockKeys()
        keys.take(5).forEachIndexed { i, k -> store.add(ItemInfo(0, ItemInfo.APP, ItemInfo.DOCK, cellX = i, appKey = k)) }
        store.initialized = true
        store.save()
    }

    private fun defaultDockKeys(): List<String> {
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(Settings.ACTION_SETTINGS),
        )
        val me = Process.myUserHandle()
        return probes.mapNotNull { probe ->
            val pkg = packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            if (pkg == null || pkg == "android") null else apps.firstOrNull { it.packageName == pkg && it.user == me }?.key
        }.distinct()
    }

    private fun applyPrefsIfChanged() {
        val key = "${prefs.cols}x${prefs.rows}|${prefs.iconShape}|${prefs.iconPack}|${prefs.homeLabels}|${prefs.hidden}"
        if (key == appliedKey) return
        val first = appliedKey.isEmpty()
        appliedKey = key
        if (!first) store.fitToGrid(prefs.cols, prefs.rows)
        if (prefs.iconPack != appliedPack || first) {
            appliedPack = prefs.iconPack
            val pkg = prefs.iconPack
            io.execute {
                val pack = pkg?.let { packs.load(it) }
                runOnUiThread { icons.pack = pack; icons.clear(); refreshHome(); sheet.applyTheme() }
            }
        } else {
            icons.clear()
        }
        sheet.submit(visibleApps())
        refreshHome()
        sheet.applyTheme()
    }

    // ---- workspace (pages) -----------------------------------------------------------------

    override val pageCount: Int get() = store.pages

    override fun newPage(): CellLayout = CellLayout(this, prefs.cols, prefs.rows).apply {
        setPadding(dockSideMargin(), 0, dockSideMargin(), 0)
        onEmptyLongPress = { x, y -> showHomeMenuAt(x.toInt(), y.toInt()) }
        onEmptyDoubleTap = { doubleTapLock() }
    }

    override fun bindPage(page: CellLayout, screen: Int) {
        page.cols = prefs.cols; page.rows = prefs.rows
        page.removeAllViews()
        for (item in store.home(screen)) {
            if (item === draggingItem) continue
            val v = itemView(item, dockMode = false) ?: continue
            page.addView(v, CellLayout.LP(item.cellX, item.cellY, item.spanX, item.spanY))
        }
    }

    private fun renderDock() {
        dock.removeAllViews()
        for (item in store.dock()) {
            if (item === draggingItem) continue
            val v = itemView(item, dockMode = true) ?: continue
            dock.addView(v, CellLayout.LP(item.cellX.coerceIn(0, 4), 0))
        }
    }

    private fun refreshHome() {
        if (!::workspace.isInitialized) return
        dots.count = store.pages
        workspace.refreshAll()
        renderDock()
    }

    private fun itemView(item: ItemInfo, dockMode: Boolean): View? {
        val labels = !dockMode && prefs.homeLabels
        val top = if (dockMode) dp(10) else dp(8)
        val shadow = !palette.wallpaperLight
        return when (item.type) {
            ItemInfo.APP -> {
                val app = appsByKey[item.appKey] ?: return null
                AppIconView(this).also { v ->
                    v.bind(app, icons, labels, palette.homeText, top, shadow)
                    v.setOnClickListener { launch(app, it) }
                    v.setOnLongClickListener { homeItemLongPress(it, item); true }
                }
            }
            ItemInfo.FOLDER -> {
                val kids = store.folderChildren(item.id).mapNotNull { it.appKey }
                val fill = if (palette.wallpaperLight) 0xB0FFFFFF.toInt() else 0x59FFFFFF
                AppIconView(this).also { v ->
                    v.bindFolder(item.title ?: "Folder", FolderDrawable(prefs.iconShape, fill, kids, icons, appsByKey), icons.sizePx, labels, palette.homeText, top, shadow)
                    v.setOnClickListener { openFolder(item) }
                    v.setOnLongClickListener { homeItemLongPress(it, item); true }
                }
            }
            ItemInfo.WIDGET -> widgets.createView(item)?.let { host ->
                host.addOnLayoutChangeListener { view, l, t, r, b, _, _, _, _ ->
                    val key = (r - l) * 100000 + (b - t)
                    if (view.tag != key) { view.tag = key; widgets.updateSize(view, r - l, b - t) }
                }
                WidgetFrame(this).also { f ->
                    f.addView(host, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    f.onLongPress = { homeItemLongPress(f, item) }
                }
            }
            else -> null
        }
    }

    // ---- header & weather ------------------------------------------------------------------

    private fun refreshHeader() {
        if (!::dateText.isInitialized) return
        dateText.text = DateFormat.format("EEE, d MMM", Date())

        val w = weather
        if (prefs.weatherEnabled && w != null) {
            weatherText.text = "${w.emoji} ${w.temp(weatherRepo.useFahrenheit)}"
            weatherText.visibility = View.VISIBLE
        } else {
            weatherText.visibility = View.GONE
        }

        val parts = ArrayList<Pair<String, (View) -> Unit>>()
        if (prefs.weatherEnabled) {
            if (w != null) {
                val f = weatherRepo.useFahrenheit
                val text = listOf(w.condition, "H ${w.temp(f, w.highC)}  L ${w.temp(f, w.lowC)}").filter { it.isNotBlank() }.joinToString("  ·  ")
                parts += text to { v -> openWeatherApp(v) }
            } else {
                val text = if (!weatherRepo.isConfigured()) "Tap to show weather" else when (weatherRepo.problem) {
                    WeatherRepo.Problem.LOCATION_OFF -> "Location is off · tap to fix"
                    WeatherRepo.Problem.NO_FIX -> "Can’t find your location · tap"
                    WeatherRepo.Problem.NETWORK -> "Weather unavailable · tap to retry"
                    null -> "Loading weather…"
                }
                parts += text to { v -> onDetailTapped(v) }
            }
        }
        if (prefs.stepsEnabled) stepRepo.steps()?.let {
            parts += ("\uD83D\uDC5F " + java.text.NumberFormat.getIntegerInstance().format(it) + " steps") to { _ -> if (!panelOpen) settlePanel(open = true, velocity = 0f) }
        }
        parts += ("\u23F0 " + (nextAlarmLabel() ?: "No alarm set")) to { _ -> openClock() }

        val sb = android.text.SpannableStringBuilder()
        for ((i, p) in parts.withIndex()) {
            if (i > 0) sb.append("  ·  ")
            val start = sb.length
            sb.append(p.first)
            sb.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) { if (!detailLongPressed) p.second(widget) }
                override fun updateDrawState(ds: android.text.TextPaint) {} // keep the plain look: no link colour or underline
            }, start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        detailText.text = sb
        detailText.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * The next real alarm, or null when none is set.
     *
     * Android's "next alarm clock" can belong to any app: on MIUI, Xiaomi Calendar registers its
     * widget refresh as an alarm clock at midnight, and Android also copies that into
     * `next_alarm_formatted`. So:
     *  1. MIUI Clock writes its own value to `next_alarm_clock_formatted` (empty = no alarm). Trust it.
     *  2. Elsewhere, use Android's next alarm clock only if a clock app set it.
     */
    private fun nextAlarmLabel(): String? {
        val miuiClock = runCatching { Settings.System.getString(contentResolver, "next_alarm_clock_formatted") }.getOrNull()
        if (miuiClock != null) return miuiClock.trim().ifEmpty { null }
        val next = getSystemService(AlarmManager::class.java).nextAlarmClock ?: return null
        val owner = next.showIntent?.creatorPackage
        if (owner != null && owner !in clockPackages()) return null
        val fmt = if (DateFormat.is24HourFormat(this)) "EEE HH:mm" else "EEE h:mm a"
        return DateFormat.format(fmt, Date(next.triggerTime)).toString()
    }

    /** Apps that can set alarms (the phone's clock apps). */
    private fun clockPackages(): Set<String> {
        val pm = packageManager
        return listOf(AlarmClock.ACTION_SET_ALARM, AlarmClock.ACTION_SHOW_ALARMS).flatMap { a ->
            runCatching { pm.queryIntentActivities(Intent(a), 0).map { it.activityInfo.packageName } }.getOrDefault(emptyList())
        }.toSet()
    }

    private val alarmObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshHeader()
    }

    private fun refreshWeather(force: Boolean) {
        if (!prefs.weatherEnabled || !weatherRepo.isConfigured()) { refreshHeader(); return }
        weatherRepo.refresh(force) { w -> weather = w; refreshHeader() }
    }

    private fun onDetailTapped(anchor: View) {
        when {
            prefs.weatherEnabled && weather == null && weatherRepo.problem == WeatherRepo.Problem.LOCATION_OFF && prefs.weatherPlace == null -> locationOffDialog()
            prefs.weatherEnabled && weather == null -> showWeatherMenu(anchor)
            else -> openAlarms()
        }
    }

    private fun showWeatherMenu(anchor: View) {
        val items = ArrayList<PopupItem>()
        if (weatherRepo.isConfigured()) items += PopupItem("Refresh weather") { refreshWeather(force = true) }
        items += PopupItem("Use my location") { useMyLocation() }
        items += PopupItem("Choose city…") { chooseCity() }
        if (prefs.weatherEnabled) items += PopupItem("Hide weather") { prefs.weatherEnabled = false; weather = null; refreshHeader() }
        else items += PopupItem("Show weather") { prefs.weatherEnabled = true; refreshWeather(force = true) }
        popup.show(anchor, items)
    }

    private fun useMyLocation() {
        prefs.weatherEnabled = true
        if (weatherRepo.hasLocationPermission()) {
            prefs.weatherPlace = null
            if (!weatherRepo.locationEnabled()) locationOffDialog() else refreshWeather(force = true)
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
    }

    private fun locationOffDialog() {
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Location is off")
            .setMessage("Turn on Location for local weather, or pick a city instead (no location needed).")
            .setPositiveButton("Turn on") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: Exception) {}
            }
            .setNeutralButton("Choose city") { _, _ -> chooseCity() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseCity() {
        val input = EditText(this).apply {
            hint = "City, e.g. Dhaka"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }
        val box = FrameLayout(this).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Weather location")
            .setView(box)
            .setPositiveButton("Search") { _, _ ->
                val q = input.text.toString()
                if (q.isBlank()) return@setPositiveButton
                weatherRepo.searchPlaces(q) { places ->
                    if (places.isEmpty()) {
                        AlertDialog.Builder(this, dialogTheme()).setMessage("No place found for “$q”, or no internet.").setPositiveButton("OK", null).show()
                    } else {
                        AlertDialog.Builder(this, dialogTheme())
                            .setTitle("Pick your place")
                            .setItems(places.map { it.name }.toTypedArray()) { _, i ->
                                prefs.weatherPlace = places[i]
                                prefs.weatherEnabled = true
                                refreshWeather(force = true)
                            }
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dialogTheme() =
        if (palette.night) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    // ---- home menu, gestures ---------------------------------------------------------------

    private fun ensureSteps() {
        if (!prefs.stepsEnabled) return
        if (!stepRepo.available) { prefs.stepsEnabled = false; toast("This phone has no step counter"); return }
        if (stepRepo.needsSensorPermission()) {
            if (!askedSteps) { askedSteps = true; askStepPermission() }
        } else {
            stepRepo.refreshToday()
        }
    }

    /** Physical activity permission, needed to record steps from the phone's sensor (Android 10+). */
    private fun askStepPermission() {
        if (Build.VERSION.SDK_INT >= 29) requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQ_STEPS)
    }

    private fun toggleSteps() {
        prefs.stepsEnabled = !prefs.stepsEnabled
        askedSteps = false
        if (prefs.stepsEnabled) { ensureSteps(); stepRepo.start() } else stepRepo.stop()
        refreshHeader()
    }

    private fun showHomeMenuAt(x: Int, y: Int) {
        popup.dismiss()
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val items = arrayListOf(
            PopupItem(if (prefs.stepsEnabled) "Hide step counter" else "Show step counter") { toggleSteps() },
            PopupItem("Add widget") { pickWidget() },
            PopupItem("Remove widget\u2026") { removeWidgetDialog() },
            PopupItem("Wallpaper") { try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) } catch (_: Exception) {} },
            PopupItem("Home settings") { startActivity(Intent(this, SettingsActivity::class.java)) },
        )
        if (!isDefaultHome()) items += PopupItem("Set as home app") { requestDefaultHome() }
        popup.showAt(root, x, y, items)
    }

    override fun onSwipeDown() {
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Exception) {
        }
    }

    private fun doubleTapLock() {
        if (!prefs.doubleTapLock) return
        if (LockAccessibilityService.lock()) return
        if (lockHintShown) return
        lockHintShown = true
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Double-tap to lock")
            .setMessage("Turn on “Plain Launcher” in Accessibility to lock the screen with a double-tap. It never reads your screen.")
            .setPositiveButton("Open Accessibility") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNeutralButton("Turn off gesture") { _, _ -> prefs.doubleTapLock = false }
            .setNegativeButton("Not now", null)
            .show()
    }

    // ---- launching and menus ---------------------------------------------------------------

    override fun launch(app: AppEntry, source: View) {
        popup.dismiss()
        try {
            val r = Rect().also { source.getGlobalVisibleRect(it) }
            val opts = ActivityOptions.makeScaleUpAnimation(source, 0, 0, source.width, source.height).toBundle()
            launcherApps.startMainActivity(app.component, app.user, r, opts)
            if (sheetOpen) root.postDelayed({ if (sheetOpen) closeSheetNow() }, 350)
        } catch (_: Exception) {
            // app was removed or is suspended; the list refreshes via LauncherApps.Callback
        }
    }

    /** Long-press in the drawer: arm a drag (move to start it) and show the menu (lift to keep it). */
    override fun showPopup(app: AppEntry, anchor: View) {
        armDrag(Payload.NewApp(app, anchor), anchor)
        val items = ArrayList<PopupItem>(popup.shortcutsFor(app))
        items += PopupItem("Add to home") { addToHome(app) }
        items += PopupItem("Hide app") { prefs.hidden = prefs.hidden + app.key; sheet.submit(visibleApps()) }
        items += appInfoItem(app)
        items += uninstallItem(app)
        popup.show(anchor, items)
    }

    private fun homeItemLongPress(view: View, item: ItemInfo) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (item.type == ItemInfo.WIDGET) { widgetMenu(view, item); return }
        armDrag(Payload.Existing(item, view), view)
        val items = ArrayList<PopupItem>()
        if (item.type == ItemInfo.APP) {
            val app = appsByKey[item.appKey] ?: return
            items += popup.shortcutsFor(app)
            items += appInfoItem(app)
            items += uninstallItem(app)
        } else {
            items += PopupItem("Open") { openFolder(item) }
            items += PopupItem("Dissolve folder") { dissolveFolder(item) }
        }
        items += PopupItem("Remove from home") { removeItem(item) }
        popup.show(view, items)
    }

    private fun appInfoItem(app: AppEntry) = PopupItem("App info") {
        try { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) } catch (_: Exception) {}
    }

    private fun uninstallItem(app: AppEntry) = PopupItem("Uninstall") { uninstall(app) }

    private fun uninstall(app: AppEntry) {
        try { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))) } catch (_: Exception) {}
    }

    private fun armDrag(p: Payload, view: View) {
        val d = (view as? AppIconView)?.iconDrawable ?: return
        val s = icons.sizePx
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, s, s)
        d.draw(Canvas(bmp))
        drag.press(p, bmp, root.downRawX, root.downRawY)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---- placing items ---------------------------------------------------------------------

    private fun gridFor(screen: Int, exclude: ItemInfo?): GridModel {
        val g = GridModel(prefs.cols, prefs.rows)
        store.home(screen).forEach { if (it !== exclude) g.occupy(it.cellX, it.cellY, it.spanX, it.spanY) }
        return g
    }

    private fun itemAt(screen: Int, x: Int, y: Int, exclude: ItemInfo?): ItemInfo? =
        store.home(screen).firstOrNull {
            it !== exclude && x in it.cellX until it.cellX + it.spanX && y in it.cellY until it.cellY + it.spanY
        }

    /** First free spot, starting on the visible page; adds a page when everything is full. */
    private fun findHomeSlot(sx: Int, sy: Int): Triple<Int, Int, Int> {
        val start = workspace.currentPage()
        val order = (start until store.pages) + (0 until start)
        for (s in order) gridFor(s, null).firstFree(sx, sy)?.let { return Triple(s, it[0], it[1]) }
        store.pages++
        return Triple(store.pages - 1, 0, 0)
    }

    private fun addToHome(app: AppEntry) {
        val (s, x, y) = findHomeSlot(1, 1)
        store.add(ItemInfo(0, ItemInfo.APP, ItemInfo.HOME, screen = s, cellX = x, cellY = y, appKey = app.key))
        refreshHome()
        toast("Added to home screen")
    }

    private fun removeItem(item: ItemInfo) {
        if (item.type == ItemInfo.WIDGET) widgets.delete(item.widgetId)
        store.remove(item)
        store.compactPages()
        refreshHome()
    }

    private fun appKeyOf(p: Payload): String? = when (p) {
        is Payload.NewApp -> p.app.key
        is Payload.Existing -> if (p.item.type == ItemInfo.APP) p.item.appKey else null
    }

    private fun place(p: Payload, container: Int, screen: Int, x: Int, y: Int) {
        when (p) {
            is Payload.NewApp -> store.add(ItemInfo(0, ItemInfo.APP, container, screen = screen, cellX = x, cellY = y, appKey = p.app.key))
            is Payload.Existing -> {
                p.item.container = container; p.item.containerId = -1
                p.item.screen = screen; p.item.cellX = x; p.item.cellY = y
                store.save()
            }
        }
    }

    /** Drop onto an occupied cell: make a folder, or add to the folder that is there. */
    private fun mergeInto(occ: ItemInfo, p: Payload): Boolean {
        if (appKeyOf(p) == null) return false
        if (p is Payload.Existing && p.item === occ) return false
        if (occ.type == ItemInfo.FOLDER) { addToFolder(occ, p); return true }
        if (occ.type != ItemInfo.APP) return false
        val folder = store.add(ItemInfo(0, ItemInfo.FOLDER, occ.container, -1, occ.screen, occ.cellX, occ.cellY, title = "Folder"))
        occ.container = ItemInfo.IN_FOLDER; occ.containerId = folder.id; occ.screen = 0; occ.cellX = 0; occ.cellY = 0
        addToFolder(folder, p)
        return true
    }

    private fun addToFolder(folder: ItemInfo, p: Payload) {
        val order = store.folderChildren(folder.id).size
        when (p) {
            is Payload.NewApp -> store.add(ItemInfo(0, ItemInfo.APP, ItemInfo.IN_FOLDER, folder.id, 0, order, 0, appKey = p.app.key))
            is Payload.Existing -> {
                p.item.container = ItemInfo.IN_FOLDER; p.item.containerId = folder.id
                p.item.screen = 0; p.item.cellX = order; p.item.cellY = 0
                store.save()
            }
        }
    }

    private fun dissolveFolder(folder: ItemInfo) {
        store.folderChildren(folder.id).forEach { k ->
            val (s, x, y) = findHomeSlot(1, 1)
            k.container = ItemInfo.HOME; k.containerId = -1; k.screen = s; k.cellX = x; k.cellY = y
        }
        store.items.remove(folder)
        store.save()
        refreshHome()
    }

    // ---- folders ---------------------------------------------------------------------------

    private fun openFolder(item: ItemInfo) {
        popup.dismiss()
        folderOverlay.open(item, store.folderChildren(item.id).mapNotNull { appsByKey[it.appKey] })
    }

    override fun launchFromFolder(app: AppEntry, v: View) {
        folderOverlay.close()
        launch(app, v)
    }

    override fun renameFolder(folderId: Long, title: String) {
        store.byId(folderId)?.title = title
        store.save()
        refreshHome()
    }

    override fun folderAppMenu(folderId: Long, app: AppEntry, v: View) {
        val items = ArrayList<PopupItem>(popup.shortcutsFor(app))
        items += PopupItem("Move out of folder") {
            val folder = store.byId(folderId) ?: return@PopupItem
            val child = store.folderChildren(folderId).firstOrNull { it.appKey == app.key } ?: return@PopupItem
            val (s, x, y) = findHomeSlot(1, 1)
            child.container = ItemInfo.HOME; child.containerId = -1; child.screen = s; child.cellX = x; child.cellY = y
            store.normalizeFolder(folder)
            folderOverlay.close()
            refreshHome()
        }
        items += appInfoItem(app)
        items += uninstallItem(app)
        popup.show(v, items)
    }

    // ---- drag and drop ---------------------------------------------------------------------

    private fun rectOf(v: View): Rect {
        val l = IntArray(2); v.getLocationOnScreen(l)
        return Rect(l[0], l[1], l[0] + v.width, l[1] + v.height)
    }

    private fun zoneAt(x: Float, y: Float): Zone {
        val xi = x.toInt(); val yi = y.toInt()
        if (dropBar.visibility == View.VISIBLE) {
            if (rectOf(removeZone).apply { inset(-dp(8), -dp(8)) }.contains(xi, yi)) return Zone.REMOVE
            if (rectOf(uninstallZone).apply { inset(-dp(8), -dp(8)) }.contains(xi, yi)) return Zone.UNINSTALL
        }
        val dr = rectOf(dock).apply { top -= dp(10) }
        if (dr.contains(xi, yi)) return Zone.DOCK
        val wr = rectOf(workspace).apply { bottom = dr.top }
        if (wr.contains(xi, yi)) return Zone.WORKSPACE
        return Zone.NONE
    }

    private fun targetCell(cl: CellLayout, x: Float, y: Float): IntArray {
        val loc = IntArray(2); cl.getLocationOnScreen(loc)
        return cl.cellAt(x - loc[0], y - drag.offsetY - loc[1])
    }

    override fun onDragStart(p: Payload) {
        popup.dismiss()
        draggingItem = (p as? Payload.Existing)?.item
        p.source?.visibility = View.INVISIBLE
        if (sheetOpen) settle(open = false, velocity = 0f)
        removeZone.alpha = if (p is Payload.Existing) 1f else 0.35f
        uninstallZone.alpha = if (appKeyOf(p) != null) 1f else 0.35f
        dropBar.alpha = 0f
        dropBar.visibility = View.VISIBLE
        dropBar.animate().alpha(1f).setDuration(150).start()
    }

    override fun onDragMove(p: Payload, x: Float, y: Float) {
        val zone = zoneAt(x, y)
        removeZone.scaleX = if (zone == Zone.REMOVE) 1.06f else 1f
        removeZone.scaleY = removeZone.scaleX
        uninstallZone.scaleX = if (zone == Zone.UNINSTALL) 1.06f else 1f
        uninstallZone.scaleY = uninstallZone.scaleX
        clearHighlights()
        val span = (p as? Payload.Existing)?.item
        when (zone) {
            Zone.WORKSPACE -> workspace.pageView(workspace.currentPage())?.let { cl ->
                val c = targetCell(cl, x, y)
                cl.setHighlight(c[0], c[1], span?.spanX ?: 1, span?.spanY ?: 1)
            }
            Zone.DOCK -> {
                val loc = IntArray(2); dock.getLocationOnScreen(loc)
                dock.setHighlight(dock.cellAt(x - loc[0], 0f)[0], 0, 1, 1)
            }
            else -> {}
        }
        val w = resources.displayMetrics.widthPixels
        val dir = if (zone != Zone.WORKSPACE) 0 else if (x < dp(28)) -1 else if (x > w - dp(28)) 1 else 0
        if (dir == 0) {
            edgeDir = 0
            handler.removeCallbacks(edgeRunnable)
        } else if (dir != edgeDir) {
            edgeDir = dir
            handler.removeCallbacks(edgeRunnable)
            handler.postDelayed(edgeRunnable, 550)
        }
    }

    private fun flipPage(dir: Int) {
        if (dir == 0 || !drag.isDragging) return
        val target = workspace.currentPage() + dir
        if (target < 0) return
        if (target >= store.pages) { store.pages++; workspace.pageAdded() }
        dots.count = store.pages
        workspace.scrollToPage(target, true)
        handler.postDelayed(edgeRunnable, 900)
    }

    private fun clearHighlights() {
        for (i in 0 until store.pages) workspace.pageView(i)?.clearHighlight()
        dock.clearHighlight()
    }

    override fun onDrop(p: Payload, x: Float, y: Float) {
        when (zoneAt(x, y)) {
            Zone.REMOVE -> if (p is Payload.Existing) removeItem(p.item)
            Zone.UNINSTALL -> appKeyOf(p)?.let { k -> appsByKey[k]?.let { uninstall(it) } }
            Zone.DOCK -> if (!dropOnDock(p, x)) toast("Dock is full")
            Zone.WORKSPACE -> if (!dropOnWorkspace(p, x, y)) toast("No room on this page")
            Zone.NONE -> {}
        }
    }

    private fun dropOnDock(p: Payload, x: Float): Boolean {
        if (p is Payload.Existing && p.item.type == ItemInfo.WIDGET) return false
        val moving = (p as? Payload.Existing)?.item
        val loc = IntArray(2); dock.getLocationOnScreen(loc)
        val slot = dock.cellAt(x - loc[0], 0f)[0]
        store.dock().firstOrNull { it !== moving && it.cellX == slot }?.let { if (mergeInto(it, p)) return true }
        val g = GridModel(5, 1)
        store.dock().forEach { if (it !== moving) g.occupy(it.cellX, 0, 1, 1) }
        val s = (if (g.isFree(slot, 0, 1, 1)) intArrayOf(slot, 0) else g.nearestFree(slot, 0, 1, 1))?.get(0) ?: return false
        place(p, ItemInfo.DOCK, 0, s, 0)
        return true
    }

    private fun dropOnWorkspace(p: Payload, x: Float, y: Float): Boolean {
        val page = workspace.currentPage()
        val cl = workspace.pageView(page) ?: return false
        val moving = (p as? Payload.Existing)?.item
        val cell = targetCell(cl, x, y)
        itemAt(page, cell[0], cell[1], moving)?.let { if (mergeInto(it, p)) return true }
        val sx = moving?.spanX ?: 1
        val sy = moving?.spanY ?: 1
        val g = gridFor(page, moving)
        val spot = (if (g.isFree(cell[0], cell[1], sx, sy)) cell else g.nearestFree(cell[0], cell[1], sx, sy)) ?: return false
        place(p, ItemInfo.HOME, page, spot[0], spot[1])
        return true
    }

    override fun onDragEnd(p: Payload) {
        handler.removeCallbacks(edgeRunnable)
        edgeDir = 0
        draggingItem = null
        clearHighlights()
        removeZone.scaleX = 1f; removeZone.scaleY = 1f; uninstallZone.scaleX = 1f; uninstallZone.scaleY = 1f
        dropBar.animate().alpha(0f).setDuration(120).withEndAction { dropBar.visibility = View.GONE }.start()
        if (p is Payload.NewApp) p.source?.visibility = View.VISIBLE
        store.items.filter { it.type == ItemInfo.FOLDER }.forEach { store.normalizeFolder(it) }
        store.compactPages()
        val last = (store.pages - 1).coerceAtLeast(0)
        if (workspace.currentPage() > last) workspace.scrollToPage(last, false)
        refreshHome()
    }

    // ---- widgets ---------------------------------------------------------------------------

    private fun pickWidget() {
        val pm = packageManager
        fun appLabel(i: AppWidgetProviderInfo) =
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(i.provider.packageName, 0)).toString() }.getOrDefault(i.provider.packageName)
        val providers = widgets.manager.installedProviders.sortedWith(compareBy({ appLabel(it).lowercase() }, { it.loadLabel(pm).lowercase() }))
        if (providers.isEmpty()) { toast("No widgets available"); return }
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Add a widget")
            .setItems(providers.map { "${it.loadLabel(pm)}  ·  ${appLabel(it)}" }.toTypedArray()) { _, i -> beginBind(providers[i]) }
            .show()
    }

    private fun beginBind(info: AppWidgetProviderInfo) {
        val id = widgets.host.allocateAppWidgetId()
        pendingWidgetId = id
        if (widgets.manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            afterBind(id)
        } else {
            try {
                startActivityForResult(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider),
                    REQ_BIND,
                )
            } catch (_: Exception) {
                dropPendingWidget(); toast("Couldn’t add that widget")
            }
        }
    }

    private fun afterBind(id: Int) {
        val info = widgets.manager.getAppWidgetInfo(id)
        if (info == null) { dropPendingWidget(); return }
        if (info.configure != null) {
            try {
                widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
                return
            } catch (_: Exception) {
                // configure screen not launchable: place it with defaults
            }
        }
        placeWidget(id)
    }

    private fun placeWidget(id: Int) {
        val info = widgets.manager.getAppWidgetInfo(id)
        val cl = workspace.pageView(workspace.currentPage())
        if (info == null || cl == null) { dropPendingWidget(); return }
        val span = widgets.spansFor(info, cl.cellW(), cl.cellH(), prefs.cols, prefs.rows)
        val (s, x, y) = findHomeSlot(span[0], span[1])
        store.add(ItemInfo(0, ItemInfo.WIDGET, ItemInfo.HOME, screen = s, cellX = x, cellY = y, spanX = span[0], spanY = span[1], widgetId = id))
        pendingWidgetId = -1
        refreshHome()
        workspace.scrollToPage(s, true)
    }

    private fun dropPendingWidget() {
        if (pendingWidgetId >= 0) widgets.delete(pendingWidgetId)
        pendingWidgetId = -1
    }

    private fun removeWidgetDialog() {
        val pm = packageManager
        val ws = store.items.filter { it.type == ItemInfo.WIDGET }
        if (ws.isEmpty()) { toast("No widgets on your home screen"); return }
        val names = ws.map { w ->
            val name = widgets.manager.getAppWidgetInfo(w.widgetId)?.loadLabel(pm) ?: "Widget"
            "$name  \u00B7  page ${w.screen + 1}"
        }
        AlertDialog.Builder(this, dialogTheme())
            .setTitle("Remove which widget?")
            .setItems(names.toTypedArray()) { _, i -> removeItem(ws[i]) }
            .show()
    }

    private fun widgetMenu(view: View, item: ItemInfo) {
        popup.show(view, listOf(
            PopupItem("Wider") { resizeWidget(item, 1, 0) },
            PopupItem("Narrower") { resizeWidget(item, -1, 0) },
            PopupItem("Taller") { resizeWidget(item, 0, 1) },
            PopupItem("Shorter") { resizeWidget(item, 0, -1) },
            PopupItem("Remove widget") { removeItem(item) },
        ))
    }

    private fun resizeWidget(item: ItemInfo, dx: Int, dy: Int) {
        val nsx = (item.spanX + dx).coerceIn(1, prefs.cols)
        val nsy = (item.spanY + dy).coerceIn(1, prefs.rows)
        if (nsx == item.spanX && nsy == item.spanY) return
        if (!gridFor(item.screen, item).isFree(item.cellX, item.cellY, nsx, nsy)) { toast("No room to resize"); return }
        item.spanX = nsx; item.spanY = nsy
        store.save()
        refreshHome()
    }

    // ---- misc actions ----------------------------------------------------------------------

    private fun openCalendar() {
        try {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    private fun openAlarms() = openClock()

    /** Open the phone's own Clock app (its normal launcher screen, which on MIUI starts on Alarms). */
    private fun openClock() {
        val pm = packageManager
        val pkg = pm.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val launch = pkg?.let { pm.getLaunchIntentForPackage(it) }
        try {
            startActivity((launch ?: Intent(AlarmClock.ACTION_SHOW_ALARMS)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast("No clock app found")
        }
    }

    /** Open the phone's built-in weather app (MIUI, ColorOS/Realme, Samsung…), else the weather menu. */
    private fun openWeatherApp(anchor: View) {
        val pm = packageManager
        val known = listOf("com.miui.weather2", "com.coloros.weather2", "com.oplus.weather", "com.oppo.weather", "com.realme.weather",
            "com.sec.android.daemonapp", "com.huawei.android.totemweather", "com.vivo.weather", "com.asus.weathertime")
        val launch = known.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?: pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .firstOrNull { ri ->
                    ri.activityInfo.packageName.contains("weather", ignoreCase = true) &&
                        (ri.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                }?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (launch == null) { showWeatherMenu(anchor); return }
        try { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { showWeatherMenu(anchor) }
    }

    private fun isDefaultHome(): Boolean {
        val ri = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    private fun requestDefaultHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    // ---- app drawer ------------------------------------------------------------------------

    override val isSheetOpen get() = sheetOpen
    override val sheetHeight get() = root.height
    override val sheetTranslation get() = sheet.translationY
    override fun canSheetScrollUp() = sheet.recycler.canScrollVertically(-1)

    override fun onSheetDragStart() {
        sheetAnim?.cancel()
        popup.dismiss()
        hideKeyboard()
        sheet.visibility = View.VISIBLE
        sheet.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    override fun onSheetDrag(translationY: Float) {
        sheet.translationY = translationY
        syncHome(translationY)
    }

    override fun onSheetRelease(velocityY: Float) {
        val h = sheetHeight.toFloat()
        val progress = if (h > 0) 1f - sheet.translationY / h else 0f
        val open = if (abs(velocityY) > 900f) velocityY < 0 else progress > 0.5f
        settle(open, velocityY)
    }

    private fun syncHome(ty: Float) {
        val h = sheetHeight.toFloat()
        sheetProgress = if (h > 0) (1f - ty / h).coerceIn(0f, 1f) else 0f
        applyHomeTransform()
    }

    /** Home recedes under whichever surface (drawer or steps page) is coming in. */
    private fun applyHomeTransform() {
        val s = sheetProgress
        val p = panelProgress
        home.scaleX = 1f - 0.06f * s - 0.04f * p
        home.scaleY = home.scaleX
        home.translationX = p * root.width * 0.18f
        home.alpha = (1f - 0.6f * s) * (1f - 0.5f * p)
    }

    // ---- steps page (swipe right on the first page) ------------------------------------------

    override val isPanelOpen get() = panelOpen
    override val panelWidth get() = root.width
    override val panelTranslation get() = stepsPanel.translationX

    override fun canOpenPanel() = prefs.stepsPage && !sheetOpen && !folderOverlay.isOpen &&
        workspace.scrollState == RecyclerView.SCROLL_STATE_IDLE && workspace.fraction() < 0.02f

    override fun panelOwnsDrag(rawX: Float, rawY: Float) = stepsPanel.ownsHorizontalDrag(rawX, rawY)

    override fun onPanelDragStart() {
        panelAnim?.cancel()
        popup.dismiss()
        if (!panelOpen && stepsPanel.visibility != View.VISIBLE) stepsPanel.reload()
        stepsPanel.visibility = View.VISIBLE
        stepsPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    override fun onPanelDrag(translationX: Float) {
        stepsPanel.translationX = translationX
        syncPanel(translationX)
    }

    override fun onPanelRelease(velocityX: Float) {
        val w = panelWidth.toFloat()
        val progress = if (w > 0) 1f + stepsPanel.translationX / w else 0f
        val open = if (abs(velocityX) > 900f) velocityX > 0 else progress > 0.4f
        settlePanel(open, velocityX)
    }

    private fun syncPanel(tx: Float) {
        val w = panelWidth.toFloat()
        panelProgress = if (w > 0) (1f + tx / w).coerceIn(0f, 1f) else 0f
        applyHomeTransform()
    }

    private fun settlePanel(open: Boolean, velocity: Float) {
        panelAnim?.cancel()
        val w = panelWidth.toFloat()
        if (w <= 0f) return
        if (open && !panelOpen && stepsPanel.visibility != View.VISIBLE) stepsPanel.reload()
        panelOpen = open
        stepsPanel.visibility = View.VISIBLE
        stepsPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setPanelBars(open)
        panelAnim = SpringAnimation(stepsPanel, DynamicAnimation.TRANSLATION_X).apply {
            setSpring(SpringForce(if (open) 0f else -w).setStiffness(500f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            setStartVelocity(velocity)
            setMinValue(-w)
            setMaxValue(0f)
            addUpdateListener { _, value, _ -> syncPanel(value) }
            addEndListener { _, canceled, _, _ ->
                if (canceled) return@addEndListener
                stepsPanel.setLayerType(View.LAYER_TYPE_NONE, null)
                if (!open) parkPanel()
            }
            start()
        }
    }

    private fun parkPanel() {
        if (!::stepsPanel.isInitialized) return
        stepsPanel.setLayerType(View.LAYER_TYPE_NONE, null)
        stepsPanel.translationX = -root.width.toFloat()
        stepsPanel.visibility = View.INVISIBLE
        stepsPanel.resetDay()
        syncPanel(-root.width.toFloat())
        setPanelBars(false)
    }

    /** Dark status-bar icons on the light steps page; back to the wallpaper's choice on home. */
    private fun setPanelBars(open: Boolean) {
        val light = if (open) !palette.night else palette.wallpaperLight
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun settle(open: Boolean, velocity: Float, focusSearch: Boolean = false) {
        sheetAnim?.cancel()
        sheetOpen = open
        val h = sheetHeight.toFloat()
        if (h <= 0f) return
        sheet.visibility = View.VISIBLE
        sheet.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        if (!open) hideKeyboard()
        val spring = SpringForce(if (open) 0f else h)
            .setStiffness(500f)
            .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
        sheetAnim = SpringAnimation(sheet, DynamicAnimation.TRANSLATION_Y).apply {
            setSpring(spring)
            setStartVelocity(velocity)
            setMinValue(0f)
            setMaxValue(h)
            addUpdateListener { _, value, _ -> syncHome(value) }
            addEndListener { _, canceled, _, _ ->
                if (canceled) return@addEndListener
                sheet.setLayerType(View.LAYER_TYPE_NONE, null)
                if (!open) { parkSheet(); sheet.resetSearch() } else if (focusSearch) showKeyboard()
            }
            start()
        }
    }

    private fun openSheetForSearch() = settle(open = true, velocity = 0f, focusSearch = true)

    private fun closeSheetNow() {
        sheetAnim?.cancel()
        sheetOpen = false
        hideKeyboard()
        parkSheet()
        sheet.resetSearch()
    }

    /** Hidden resting state: fully translated away, invisible, no layer. */
    private fun parkSheet() {
        sheet.setLayerType(View.LAYER_TYPE_NONE, null)
        sheet.translationY = root.height.toFloat()
        sheet.visibility = View.INVISIBLE
        syncHome(root.height.toFloat())
    }

    private fun showKeyboard() {
        sheet.searchField.requestFocus()
        getSystemService(InputMethodManager::class.java).showSoftInput(sheet.searchField, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        sheet.searchField.clearFocus()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(root.windowToken, 0)
    }

    companion object {
        private const val REQ_LOCATION = 7
        private const val REQ_STEPS = 8
        private const val REQ_BIND = 11
        private const val REQ_CONFIGURE = 12
    }
}
