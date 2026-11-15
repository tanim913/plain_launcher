package app.plainlauncher.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.io.File

/**
 * Plain Launcher's own step history: a small CSV in app storage that the charts read.
 * It is fed by MIUI's step provider (a short buffer another system component keeps clearing; on
 * the Note 7 the oldest row was from the same morning) or, on other phones, by [StepRecorder].
 */
object StepArchive {
    private const val KEEP_DAYS = 400L
    private const val JOB_ID = 4201

    /** Union of [old] and [fresh], keyed by start time; a row MIUI has since extended replaces the shorter copy. */
    fun merge(old: Collection<StepSegment>, fresh: Collection<StepSegment>, cutoff: Long): Pair<List<StepSegment>, Boolean> {
        val map = HashMap<Long, StepSegment>(old.size + fresh.size)
        for (s in old) map[s.begin] = s
        var changed = false
        for (s in fresh) {
            val o = map[s.begin]
            if (o == null || s.steps > o.steps || s.end > o.end) { map[s.begin] = s; changed = true }
        }
        val before = map.size
        map.values.removeAll { it.end < cutoff }
        if (map.size != before) changed = true
        return map.values.sortedBy { it.begin } to changed
    }

    /** One history per source, so switching sources never double-counts. */
    const val MIUI = "miui"
    const val SENSOR = "sensor"

    private fun file(ctx: Context, which: String) = File(ctx.filesDir, if (which == MIUI) "steps.csv" else "steps-$which.csv")

    /** Copy MIUI's current rows into the archive and return everything archived. Blocking; call off the main thread. */
    fun sync(ctx: Context): List<StepSegment> = add(ctx, MiuiSteps.readAll(ctx), MIUI)

    /**
     * Merge [fresh] segments into the [which] history and return all of it. If that history is the
     * active step source, also send it to Health Connect (when the user turned that on). Blocking.
     */
    @Synchronized
    fun add(ctx: Context, fresh: List<StepSegment>, which: String): List<StepSegment> {
        val f = file(ctx, which)
        val (all, changed) = merge(read(f), fresh, System.currentTimeMillis() - KEEP_DAYS * 86_400_000L)
        if (changed) write(f, all)
        if (Prefs(ctx).activeArchive == which) HealthSync.pushIfEnabled(ctx, all)
        return all
    }

    /** Bring the active source's history up to date and return it (empty for Health Connect / none). Blocking. */
    fun refreshActive(ctx: Context): List<StepSegment> = when (Prefs(ctx).activeArchive) {
        MIUI -> if (MiuiSteps.available(ctx)) sync(ctx) else all(ctx, MIUI)
        SENSOR -> { StepRecorder.ensure(ctx); StepRecorder.flush(ctx); all(ctx, SENSOR) }
        else -> emptyList()
    }

    /** Everything in the [which] history. Blocking. */
    @Synchronized
    fun all(ctx: Context, which: String): List<StepSegment> = read(file(ctx, which))

    private fun read(f: File): List<StepSegment> = runCatching {
        if (!f.exists()) return emptyList()
        f.readLines().mapNotNull { line ->
            val p = line.split(',')
            if (p.size != 4) null
            else runCatching { StepSegment(p[0].toLong(), p[1].toLong(), p[2].toInt(), p[3].toInt()) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun write(f: File, segs: List<StepSegment>) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.bufferedWriter().use { w -> for (s in segs) w.append("${s.begin},${s.end},${s.mode},${s.steps}\n") }
        tmp.renameTo(f)
    }

    /**
     * Every ~30 minutes, including while home is in the background: copy MIUI's buffer, or on other
     * phones make sure the step recorder is still listening (the system may have restarted us).
     */
    fun schedule(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(JOB_ID) != null) return
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(ctx, StepSyncJob::class.java))
                .setPeriodic(30 * 60_000L)
                .setPersisted(true)
                .build()
        )
    }
}

class StepSyncJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { StepArchive.refreshActive(this) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
