package app.plainlauncher.ui

import android.view.View
import app.plainlauncher.data.AppEntry

interface AppActions {
    fun launch(app: AppEntry, source: View)
    fun showPopup(app: AppEntry, anchor: View)
}
