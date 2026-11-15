package app.plainlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Reads MIUI's step records: the provider in com.miui.powerkeeper that App Vault's step card
 * uses. Its read permission (miui.permission.READ_STEPS) is a normal permission, so declaring it
 * is enough. Querying also asks the sensor hub to flush, so counts are as fresh as MIUI's.
 * MIUI only keeps recent rows here; [StepArchive] keeps the long history. Nothing leaves the phone.
 */
object MiuiSteps {
    private const val AUTHORITY = "com.miui.providers.steps"
    private const val PERMISSION = "miui.permission.READ_STEPS"
    private val URI: Uri = Uri.parse("content://$AUTHORITY/item")

    fun available(ctx: Context): Boolean =
        ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED &&
            runCatching { ctx.packageManager.resolveContentProvider(AUTHORITY, 0) != null }.getOrDefault(false)

    /** Every row MIUI currently holds. Empty on any failure. Call off the main thread. */
    fun readAll(ctx: Context): List<StepSegment> {
        val out = ArrayList<StepSegment>()
        try {
            ctx.contentResolver.query(URI, arrayOf("_begin_time", "_end_time", "_mode", "_steps"), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    var b = c.getLong(0)
                    var e = c.getLong(1)
                    if (b in 1 until 100_000_000_000L) { b *= 1000; e *= 1000 } // seconds on some builds
                    out += StepSegment(b, e, c.getInt(2), c.getInt(3))
                }
            }
        } catch (t: Throwable) {
            Log.w("PlainSteps", "MIUI step query failed", t)
        }
        return out
    }
}
