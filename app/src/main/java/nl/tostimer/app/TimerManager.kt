package nl.tostimer.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the timer. State is persisted so it survives process death; the
 * actual wake-up is an AlarmManager alarm, so nothing here has to stay alive while counting down.
 */
object TimerManager {
    const val ACTION_FINISH = "nl.tostimer.app.FINISH"
    const val ACTION_HEADS_UP = "nl.tostimer.app.HEADS_UP"
    const val ACTION_WIDGET_TICK = "nl.tostimer.app.WIDGET_TICK"
    const val ACTION_ADD_TIME = "nl.tostimer.app.ADD_TIME"
    const val ACTION_STOP = "nl.tostimer.app.STOP"
    const val ACTION_START = "nl.tostimer.app.START"
    const val EXTRA_SECONDS = "seconds"
    const val EXTRA_LABEL = "label"
    const val EXTRA_PRESET_ID = "preset_id"

    /** A tosti whose alarm went unanswered this long counts as overcooked in the stats. */
    const val OVERCOOK_AFTER_MS = 2 * 60_000L

    private const val PREFS = "tostimer"
    private const val HEADS_UP_FRACTION = 0.8
    private const val MIN_HEADS_UP_LEAD_MS = 10_000L
    private const val WIDGET_STEPS = 12

    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = prefs(context)
            val ingredients = if (p.contains("ingredients")) decodeIngredients(p.getString("ingredients", ""))
            else migrateFilling(p.getString("filling", null))
            val brown = p.getFloat("brown", Doneness.GOLDEN.brown)
            _state.value = when (p.getString("phase", "idle")) {
                "running" -> TimerState.Running(
                    p.getLong("endAt", 0), p.getLong("totalMs", 0), p.getString("label", "") ?: "", ingredients, brown,
                )
                "finished" -> TimerState.Finished(
                    p.getString("label", "") ?: "", ingredients, brown, p.getLong("finishedAt", 0), p.getLong("totalMs", 0),
                )
                else -> TimerState.Idle
            }
            loaded = true
        }
    }

    fun start(context: Context, seconds: Int, label: String, ingredients: Set<Ingredient>, brown: Float) {
        val app = context.applicationContext
        ensureLoaded(app)
        val totalMs = seconds * 1000L
        begin(app, TimerState.Running(System.currentTimeMillis() + totalMs, totalMs, label, ingredients, brown))
    }

    /** Used by the widget: start a saved preset at the doneness chosen in the app. */
    fun startPreset(context: Context, presetId: String) {
        val app = context.applicationContext
        val preset = PresetStore.load(app).firstOrNull { it.id == presetId } ?: return
        val doneness = AppSettings.doneness(app)
        start(app, doneness.apply(preset.seconds), preset.name, preset.ingredients, doneness.brown)
    }

    /** Adds time to a running timer, or restarts from now if the alarm already went off. */
    fun addTime(context: Context, seconds: Int) {
        val app = context.applicationContext
        ensureLoaded(app)
        val extra = seconds * 1000L
        when (val s = _state.value) {
            is TimerState.Running -> begin(app, s.copy(endAt = s.endAt + extra, totalMs = s.totalMs + extra))
            is TimerState.Finished ->
                begin(app, TimerState.Running(System.currentTimeMillis() + extra, extra, s.label, s.ingredients, s.brown))
            TimerState.Idle -> Unit
        }
    }

    /** Stops everything. If the tosti was done, it is logged in the stats. */
    fun stop(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        (_state.value as? TimerState.Finished)?.let { f ->
            val now = System.currentTimeMillis()
            StatsStore.record(
                app,
                TostiRecord(
                    finishedAt = f.finishedAt,
                    seconds = (f.totalMs / 1000).toInt(),
                    overcooked = now - f.finishedAt > OVERCOOK_AFTER_MS,
                    label = f.label,
                ),
            )
        }
        cancelAlarms(app)
        AlarmService.stop(app)
        Notifier.cancelAll(app)
        publish(app, TimerState.Idle)
    }

    /** Called when the exact alarm fires. */
    fun onFinished(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        val s = _state.value as? TimerState.Running ?: return
        cancelAlarms(app)
        Notifier.cancelRunning(app)
        publish(app, TimerState.Finished(s.label, s.ingredients, s.brown, System.currentTimeMillis(), s.totalMs))
        AlarmService.start(app, s.label)
    }

    fun onHeadsUp(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        val s = _state.value as? TimerState.Running ?: return
        Notifier.showHeadsUp(app, s)
    }

    /** The widget's tosti browns in steps; this redraws it when the next step is reached. */
    fun onWidgetTick(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        val s = _state.value as? TimerState.Running ?: return
        TostiWidgetProvider.updateAll(app)
        scheduleWidgetTick(app, s)
    }

    /** Re-arms alarms after a reboot, since AlarmManager forgets them. */
    fun restore(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        when (val s = _state.value) {
            is TimerState.Running ->
                if (s.endAt > System.currentTimeMillis()) begin(app, s) else onFinished(app)
            else -> Unit
        }
    }

    private fun begin(app: Context, running: TimerState.Running) {
        cancelAlarms(app)
        AlarmService.stop(app)
        Notifier.cancelAlert(app)
        scheduleAlarms(app, running)
        publish(app, running)
    }

    private fun publish(app: Context, state: TimerState) {
        _state.value = state
        prefs(app).edit {
            when (state) {
                is TimerState.Running -> {
                    putString("phase", "running")
                    putLong("endAt", state.endAt)
                    putLong("totalMs", state.totalMs)
                    putString("label", state.label)
                    putString("ingredients", state.ingredients.encode())
                    putFloat("brown", state.brown)
                }
                is TimerState.Finished -> {
                    putString("phase", "finished")
                    putString("label", state.label)
                    putString("ingredients", state.ingredients.encode())
                    putFloat("brown", state.brown)
                    putLong("finishedAt", state.finishedAt)
                    putLong("totalMs", state.totalMs)
                }
                TimerState.Idle -> putString("phase", "idle")
            }
        }
        if (state is TimerState.Running) Notifier.showRunning(app, state)
        TostiWidgetProvider.updateAll(app)
        if (state is TimerState.Running) scheduleWidgetTick(app, state)
    }

    private fun scheduleAlarms(app: Context, s: TimerState.Running) {
        val am = app.getSystemService(AlarmManager::class.java)
        // setAlarmClock is exact and survives Doze. It needs USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM.
        val showIntent = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(s.endAt, showIntent), alarmIntent(app, ACTION_FINISH))

        val headsUpAt = s.endAt - (s.totalMs * (1 - HEADS_UP_FRACTION)).toLong()
        if (headsUpAt - System.currentTimeMillis() > MIN_HEADS_UP_LEAD_MS) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, headsUpAt, alarmIntent(app, ACTION_HEADS_UP))
        }
    }

    private fun scheduleWidgetTick(app: Context, s: TimerState.Running) {
        if (!TostiWidgetProvider.hasWidgets(app)) return
        val am = app.getSystemService(AlarmManager::class.java)
        val startAt = s.endAt - s.totalMs
        val step = s.totalMs / WIDGET_STEPS
        if (step <= 0) return
        val k = ((System.currentTimeMillis() - startAt) / step).toInt() + 1
        val at = startAt + k * step
        if (at < s.endAt - 1000) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(app, ACTION_WIDGET_TICK))
        }
    }

    private fun cancelAlarms(app: Context) {
        val am = app.getSystemService(AlarmManager::class.java)
        am.cancel(alarmIntent(app, ACTION_FINISH))
        am.cancel(alarmIntent(app, ACTION_HEADS_UP))
        am.cancel(alarmIntent(app, ACTION_WIDGET_TICK))
    }

    private fun alarmIntent(app: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            app, action.hashCode(),
            Intent(app, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
