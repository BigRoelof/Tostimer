package nl.tostimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.tostimer.app.ui.MainScreen
import nl.tostimer.app.ui.TostimerTheme

/** Full-screen "tosti is ready" screen. Closes itself as soon as the alarm is handled. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        TimerManager.ensureLoaded(this)
        setContent {
            TostimerTheme {
                val state by TimerManager.state.collectAsStateWithLifecycle()
                LaunchedEffect(state) {
                    if (state !is TimerState.Finished) finish()
                }
                MainScreen(
                    state = state,
                    presets = emptyList(),
                    initialPresetId = null,
                    initialBaseSeconds = Presets.defaults.first().seconds,
                    initialDoneness = Doneness.GOLDEN,
                    onStart = {},
                    onAddTime = { TimerManager.addTime(this@AlarmActivity, it) },
                    onStop = { TimerManager.stop(this@AlarmActivity) },
                )
            }
        }
    }
}
