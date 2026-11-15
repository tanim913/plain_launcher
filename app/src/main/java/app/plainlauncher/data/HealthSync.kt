package app.plainlauncher.data

import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Health Connect: the separate app on Android 9-13, part of the system on Android 14+.
 *
 * Write (for MyNetDiary and similar apps): every walk of at least [Prefs.healthMinWalkMin] minutes
 * as a Walking (or Running) workout, which those apps turn into exercise calories from your weight,
 * plus the raw steps. Records carry stable client IDs, so sending a walk again updates it.
 *
 * Read (a step source for phones without MIUI's step store): hourly step totals, which Health
 * Connect de-duplicates across the apps that write steps (Samsung Health, Fit, the system itself…).
 *
 * Every call here is blocking IPC and must stay off the main thread: the client delivers results
 * on the main thread, so blocking it there deadlocks the launcher.
 */
object HealthSync {
    private const val TAG = "PlainHealth"
    private const val LOOKBACK_MS = 3 * 86_400_000L
    private const val MIN_INTERVAL_MS = 10 * 60_000L

    val WRITE_PERMISSIONS = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
    )
    val READ_PERMISSIONS = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    @Volatile private var lastPush = 0L

    fun status(ctx: Context): Int = runCatching { HealthConnectClient.getSdkStatus(ctx) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    fun installed(ctx: Context) = status(ctx) == HealthConnectClient.SDK_AVAILABLE

    /** The Play Store page to install (Android 9-13) or update Health Connect. */
    const val PROVIDER = "com.google.android.apps.healthdata"

    private fun client(ctx: Context) = HealthConnectClient.getOrCreate(ctx)

    private fun offMain() = check(Looper.myLooper() != Looper.getMainLooper()) { "Health Connect call on main thread" }

    fun granted(ctx: Context): Set<String> {
        offMain()
        if (!installed(ctx)) return emptySet()
        return runCatching {
            runBlocking { withTimeoutOrNull(8_000) { client(ctx).permissionController.getGrantedPermissions() } } ?: emptySet()
        }.getOrDefault(emptySet())
    }

    fun canWrite(ctx: Context) = granted(ctx).containsAll(WRITE_PERMISSIONS)
    fun canRead(ctx: Context) = granted(ctx).containsAll(READ_PERMISSIONS)

    /** Hourly step totals in [from, to) as [StepSegment.MODE_TOTAL] segments. Empty on failure. Blocking. */
    fun hourlySteps(ctx: Context, from: Long, to: Long): List<StepSegment> {
        offMain()
        if (!installed(ctx)) return emptyList()
        return try {
            val req = AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to)),
                timeRangeSlicer = Duration.ofHours(1),
            )
            val rows = runBlocking { withTimeout(20_000) { client(ctx).aggregateGroupByDuration(req) } }
            rows.mapNotNull { r ->
                val n = r.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
                if (n <= 0) null else StepSegment(r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), StepSegment.MODE_TOTAL, n.toInt())
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Health Connect read failed", t)
            emptyList()
        }
    }

    /** Push recent walks and steps if the user turned this on. Blocking; call off the main thread. */
    fun pushIfEnabled(ctx: Context, all: List<StepSegment>, force: Boolean = false) {
        if (Looper.myLooper() == Looper.getMainLooper()) return
        val prefs = Prefs(ctx)
        if (!prefs.healthSync) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPush < MIN_INTERVAL_MS) return
        if (!installed(ctx)) return
        lastPush = now
        try {
            val device = Device(type = Device.TYPE_PHONE, manufacturer = Build.MANUFACTURER, model = Build.MODEL)
            val recent = all.filter { it.end >= now - LOOKBACK_MS && it.mode != StepSegment.MODE_TOTAL }
            val walks = Walks.group(recent, prefs.healthMinWalkMin * 60_000L, now)
            val records = ArrayList<Record>()
            for (w in walks) {
                val b = Instant.ofEpochMilli(w.begin)
                val e = Instant.ofEpochMilli(w.end)
                records += ExerciseSessionRecord(
                    startTime = b, startZoneOffset = offset(b),
                    endTime = e, endZoneOffset = offset(e),
                    metadata = Metadata.autoRecorded(device, "plain-walk-${w.begin}", w.end),
                    exerciseType = if (w.running) ExerciseSessionRecord.EXERCISE_TYPE_RUNNING else ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                    title = if (w.running) "Running" else "Walking",
                    notes = "${w.steps} steps · from Plain Launcher",
                )
            }
            for (s in recent) {
                if (s.steps <= 0 || s.end <= s.begin) continue
                val b = Instant.ofEpochMilli(s.begin)
                val e = Instant.ofEpochMilli(s.end)
                records += StepsRecord(
                    startTime = b, startZoneOffset = offset(b),
                    endTime = e, endZoneOffset = offset(e),
                    count = s.steps.toLong(),
                    metadata = Metadata.autoRecorded(device, "plain-steps-${s.begin}", s.steps.toLong()),
                )
            }
            if (records.isEmpty()) return
            runBlocking { withTimeout(30_000) { records.chunked(500).forEach { client(ctx).insertRecords(it) } } }
            prefs.healthLastSync = now
            prefs.healthLastWalks = walks.count { it.begin >= StepRepo.dayStart(now) }
            Log.i(TAG, "sent ${walks.size} walks, ${records.size - walks.size} step records")
        } catch (t: Throwable) {
            Log.w(TAG, "Health Connect write failed", t)
        }
    }

    private fun offset(i: Instant) = ZoneId.systemDefault().rules.getOffset(i)
}
