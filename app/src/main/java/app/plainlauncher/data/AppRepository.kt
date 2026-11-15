package app.plainlauncher.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

/** Loads every launchable activity across all profiles and keeps the list live. */
class AppRepository(
    private val ctx: Context,
    private val onChanged: (List<AppEntry>) -> Unit,
) {
    private val la = ctx.getSystemService(LauncherApps::class.java)
    private val um = ctx.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val infos = ConcurrentHashMap<String, LauncherActivityInfo>()
    private var job: Job? = null

    @Volatile
    var apps: List<AppEntry> = emptyList()
        private set

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
    }

    fun start() {
        la.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh(immediate = true)
    }

    fun stop() {
        la.unregisterCallback(callback)
        job?.cancel()
    }

    fun refresh(immediate: Boolean = false) {
        job?.cancel()
        job = scope.launch {
            if (!immediate) delay(150) // coalesce bursts (an update fires several callbacks)
            val list = load()
            apps = list
            withContext(Dispatchers.Main) { onChanged(list) }
        }
    }

    fun iconFor(key: String): Drawable? =
        infos[key]?.getBadgedIcon(ctx.resources.displayMetrics.densityDpi)

    private fun load(): List<AppEntry> {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val out = ArrayList<AppEntry>()
        val seen = HashSet<String>()
        for (user in um.userProfiles) {
            val serial = um.getSerialNumberForUser(user)
            for (info in la.getActivityList(null, user)) {
                if (info.componentName.packageName == ctx.packageName) continue
                val entry = AppEntry(info.componentName, user, serial, info.label?.toString() ?: info.componentName.packageName)
                if (seen.add(entry.key)) {
                    infos[entry.key] = info
                    out.add(entry)
                }
            }
        }
        infos.keys.retainAll(seen)
        out.sortWith { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.key.compareTo(b.key) }
        return out
    }
}
