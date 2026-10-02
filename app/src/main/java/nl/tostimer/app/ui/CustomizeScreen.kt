package nl.tostimer.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.tostimer.app.AlarmService
import nl.tostimer.app.AlarmSound
import nl.tostimer.app.Ingredient
import nl.tostimer.app.Preset
import nl.tostimer.app.PresetStore
import nl.tostimer.app.formatDuration
import java.util.UUID

/** Plays an alarm sound once so you can hear it before choosing. */
private class SoundPreviewer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(sound: AlarmSound) {
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                setDataSource(context, AlarmService.soundUri(context, sound))
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.getOrNull()
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
    }
}

@Composable
fun CustomizeScreen(
    presets: List<Preset>,
    onPresetsChange: (List<Preset>) -> Unit,
    sound: AlarmSound,
    onSoundChange: (AlarmSound) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val previewer = remember { SoundPreviewer(context) }
    DisposableEffect(Unit) { onDispose { previewer.stop() } }

    ScreenFrame("Customize", onBack = { previewer.stop(); onBack() }) {
        Text("Alarm sound", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        AlarmSound.entries.forEach { option ->
            SoundRow(option, option == sound, onSelect = { onSoundChange(option); previewer.play(option) }) {
                previewer.play(option)
            }
        }

        Text(
            "Presets",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        presets.forEachIndexed { index, preset ->
            PresetEditor(
                preset = preset,
                canRemove = presets.size > 1,
                onChange = { updated -> onPresetsChange(presets.toMutableList().also { it[index] = updated }) },
                onRemove = { onPresetsChange(presets.filterIndexed { i, _ -> i != index }) },
            )
        }
        if (presets.size < PresetStore.MAX) {
            Button(
                onClick = {
                    onPresetsChange(presets + Preset(UUID.randomUUID().toString(), "New tosti", 240, setOf(Ingredient.CHEESE)))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = ButtonShape,
            ) { Text("Add preset", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }
        }
    }
}

@Composable
private fun SoundRow(sound: AlarmSound, selected: Boolean, onSelect: () -> Unit, onPlay: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Surface(
        onClick = onSelect,
        shape = TileShape,
        color = if (selected) ink else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else ink,
        border = BorderStroke(2.dp, ink),
        modifier = Modifier.fillMaxWidth().height(60.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                sound.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPlay) {
                Text(
                    "▶ Play",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) MaterialTheme.colorScheme.surfaceVariant else ink,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetEditor(preset: Preset, canRemove: Boolean, onChange: (Preset) -> Unit, onRemove: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Surface(
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(2.dp, ink),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = preset.name,
                onValueChange = { onChange(preset.copy(name = it.take(14))) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ink,
                    unfocusedBorderColor = ink,
                    focusedLabelColor = ink,
                    unfocusedLabelColor = ink,
                    cursorColor = ink,
                ),
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                StepButton("−") { onChange(preset.copy(seconds = (preset.seconds - 15).coerceAtLeast(30))) }
                Digits(formatDuration(preset.seconds * 1000L), size = 40)
                StepButton("+") { onChange(preset.copy(seconds = (preset.seconds + 15).coerceAtMost(15 * 60))) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Ingredients are not exclusive: any combination works.
                Ingredient.entries.forEach { f ->
                    val on = f in preset.ingredients
                    FilterChip(
                        selected = on,
                        onClick = {
                            onChange(preset.copy(ingredients = if (on) preset.ingredients - f else preset.ingredients + f))
                        },
                        label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ink,
                            selectedLabelColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = ink,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = on,
                            borderColor = ink,
                            selectedBorderColor = ink,
                            borderWidth = 2.dp,
                            selectedBorderWidth = 2.dp,
                        ),
                    )
                }
            }
            if (canRemove) {
                TextButton(onClick = onRemove) {
                    Text("Remove", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
