package app.plainlauncher.data

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/** Unread-notification counts per package, fed by NotifListener, observed by icon views. */
object NotifDots {
    @Volatile var counts: Map<String, Int> = emptyMap()
        private set
    val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun has(pkg: String) = (counts[pkg] ?: 0) > 0

    fun update(m: Map<String, Int>) {
        counts = m
        main.post { listeners.forEach { it() } }
    }
}
