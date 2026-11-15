package app.plainlauncher.util

import android.content.Context

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpF(v: Float): Float = v * resources.displayMetrics.density
