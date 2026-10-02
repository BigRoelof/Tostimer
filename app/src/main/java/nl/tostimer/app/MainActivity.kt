package nl.tostimer.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.tostimer.app.ui.CustomizeScreen
import nl.tostimer.app.ui.MainScreen
import nl.tostimer.app.ui.StatsScreen
import nl.tostimer.app.ui.TostimerTheme

private enum class Screen { Home, Stats, Customize }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        TimerManager.ensureLoaded(this)

        setContent {
            TostimerTheme {
                val state by TimerManager.state.collectAsStateWithLifecycle()
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                var presets by remember { mutableStateOf(PresetStore.load(this)) }
                var sound by remember { mutableStateOf(AppSettings.sound(this)) }

                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                when (screen) {
                    Screen.Stats -> StatsScreen(records = StatsStore.all(this), onBack = { screen = Screen.Home })
                    Screen.Customize -> CustomizeScreen(
                        presets = presets,
                        onPresetsChange = { presets = it; PresetStore.save(this, it) },
                        sound = sound,
                        onSoundChange = { sound = it; AppSettings.setSound(this, it) },
                        onBack = {
                            // If the preset you last used was retimed, open on its new time.
                            presets.firstOrNull { it.id == AppSettings.lastPresetId(this) }
                                ?.let { AppSettings.setLastBaseSeconds(this, it.seconds) }
                            TostiWidgetProvider.updateAll(this)
                            screen = Screen.Home
                        },
                    )
                    Screen.Home -> MainScreen(
                        state = state,
                        presets = presets,
                        initialPresetId = AppSettings.lastPresetId(this),
                        initialBaseSeconds = AppSettings.lastBaseSeconds(this),
                        initialDoneness = AppSettings.doneness(this),
                        onStart = { r ->
                            if (r.presetId.isNotBlank()) AppSettings.setLastPresetId(this, r.presetId)
                            AppSettings.setLastBaseSeconds(this, r.baseSeconds)
                            AppSettings.setDoneness(this, r.doneness)
                            TimerManager.start(this, r.seconds, r.label, r.ingredients, r.brown)
                        },
                        onAddTime = { TimerManager.addTime(this, it) },
                        onStop = { TimerManager.stop(this) },
                        onOpenStats = { screen = Screen.Stats },
                        onOpenCustomize = { screen = Screen.Customize },
                        banner = {
                            // On Android 14+ the lock-screen alarm needs the user to allow full-screen intents.
                            if (Build.VERSION.SDK_INT >= 34 &&
                                !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                            ) {
                                TextButton(onClick = {
                                    startActivity(
                                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")),
                                    )
                                }) { Text("Allow the alarm to show on the lock screen") }
                            }
                        },
                    )
                }
            }
        }
    }
}
