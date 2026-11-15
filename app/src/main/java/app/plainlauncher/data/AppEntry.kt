package app.plainlauncher.data

import android.content.ComponentName
import android.os.UserHandle

/** One launchable activity for one user profile (personal or work). */
data class AppEntry(
    val component: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    val label: String,
) {
    val key: String = "${component.flattenToString()}#$userSerial"
    val packageName: String get() = component.packageName
}
