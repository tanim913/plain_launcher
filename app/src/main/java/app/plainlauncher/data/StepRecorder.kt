package app.plainlauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

/**
 * Step history for phones without MIUI's step store. Android's step counter only counts while an
 * app is listening, so this stays registered for the life of the launcher process (home is
 * almost never killed, and [StepSyncJob] re-registers it every 30 minutes if it was).
 *
 * The sensor is registered with batching: the chip queues steps with their own timestamps and
 * hands them over every few minutes, so the phone isn't woken per step. Readings become timed
 * pieces ([RecorderMath]), nearby pieces join into one stretch, and stretches go into
 * [StepArchive]. The last saved reading is persisted, so steps counted while we weren't running
 * (and the counter kept counting) are recovered on the next reading.
 */
object StepRecorder : SensorEventListener {
    private const val BATCH_US = 3 * 60 * 1_000_000
    private const val FLUSH_MS = 2 * 60_000L

    private val thread by lazy { HandlerThread("steps").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }

    @Volatile private var app: Context? = null
    @Volatile private var registered = false
    private var last: RecorderMath.Last? = null // last reading seen (in memory)
    private var saved: RecorderMath.Last? = null // last reading whose steps are in the archive
    @Volatile private var pending: RecorderMath.Piece? = null

    fun hasSensor(ctx: Context) = ctx.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun hasPermission(ctx: Context) = Build.VERSION.SDK_INT < 29 ||
        ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Start listening if allowed and not already. Safe to call often, from any thread. */
    @Synchronized
    fun ensure(ctx: Context) {
        if (registered) return
        val c = ctx.applicationContext
        if (Prefs(c).activeArchive != StepArchive.SENSOR || !hasPermission(c)) return
        val sm = c.getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        app = c
        handler.post {
            if (saved == null) { saved = Prefs(c).recorderLast; last = saved }
        }
        registered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, BATCH_US, handler)
        if (registered) handler.postDelayed(periodicFlush, FLUSH_MS)
    }

    @Synchronized
    fun stop(ctx: Context) {
        if (!registered) return
        ctx.getSystemService(SensorManager::class.java)?.unregisterListener(this)
        registered = false
        flush(ctx)
    }

    /** Steps waiting to be saved (counted today on the home screen right away). */
    fun pendingPiece(): RecorderMath.Piece? = pending

    private val periodicFlush = object : Runnable {
        override fun run() {
            app?.let { c -> pending?.let { if (System.currentTimeMillis() - it.end > 60_000L) save(c) } }
            if (registered) handler.postDelayed(this, FLUSH_MS)
        }
    }

    /** Save any pending stretch now. Blocking until done when called off the recorder thread. */
    fun flush(ctx: Context) {
        val c = ctx.applicationContext
        if (Thread.currentThread() == thread) { save(c); return }
        val done = java.util.concurrent.CountDownLatch(1)
        handler.post { save(c); done.countDown() }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
    }

    // Recorder thread only. [upTo] is the reading whose steps the pending stretch includes.
    private fun save(c: Context, upTo: RecorderMath.Last? = last) {
        val p = pending ?: return
        pending = null
        StepArchive.add(c, listOf(StepSegment(p.begin, p.end, StepSegment.MODE_WALKING, p.steps)), StepArchive.SENSOR)
        saved = upTo
        upTo?.let { Prefs(c).recorderLast = it }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val c = app ?: return
        val raw = e.values.firstOrNull()?.toLong() ?: return
        val nowWall = System.currentTimeMillis()
        val bootWall = nowWall - SystemClock.elapsedRealtime()
        val wall = (nowWall - (SystemClock.elapsedRealtimeNanos() - e.timestamp) / 1_000_000L).coerceAtMost(nowWall)
        val prev = last
        val (next, piece) = RecorderMath.next(prev, raw, wall, bootWall)
        last = next
        if (piece == null) {
            if (saved == null) { saved = next; Prefs(c).recorderLast = next } // first baseline
            return
        }
        val (joined, done) = RecorderMath.join(pending, piece)
        if (done != null) {
            // A gap closed the previous stretch: save it (it covers readings up to [prev]), then start the new one.
            pending = done
            save(c, prev)
        }
        pending = joined
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
}
