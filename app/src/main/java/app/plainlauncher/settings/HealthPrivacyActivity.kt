package app.plainlauncher.settings

import android.app.Activity
import android.os.Bundle
import app.plainlauncher.ui.Palette

/** Shown by Health Connect when it asks for permission: what Plain Launcher writes and why. */
class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = Palette(this).also { it.refresh() }
        val list = settingsScaffold(palette, "Health data")
        list.addView(settingsRow(palette, "What is written",
            "Only if you turn on \"Send walks to Health Connect\": your walks (start, end and type, as Walking or Running workouts) and step counts.", null))
        list.addView(settingsRow(palette, "What is read",
            "Only if you choose Health Connect as the step source: your step counts, to show them on the home screen and the Steps page.", null))
        list.addView(settingsRow(palette, "Why",
            "So apps you choose, such as MyNetDiary, can count your walking as exercise without you typing it in.", null))
        list.addView(settingsRow(palette, "Where it goes",
            "Only to Health Connect on this phone. Plain Launcher has no account and sends nothing to the internet. Turn it off any time in Home settings › Send walks to Health Connect.", null))
    }
}
