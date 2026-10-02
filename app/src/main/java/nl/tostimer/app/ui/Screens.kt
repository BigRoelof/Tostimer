package nl.tostimer.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.tostimer.app.Doneness
import nl.tostimer.app.Ingredient
import nl.tostimer.app.describe
import nl.tostimer.app.Preset
import nl.tostimer.app.TimerState
import nl.tostimer.app.formatDuration

internal val ButtonShape = RoundedCornerShape(32.dp)
internal val TileShape = RoundedCornerShape(24.dp)

/** Everything the home screen needs to start a timer. */
data class StartRequest(
    val seconds: Int,
    val label: String,
    val ingredients: Set<Ingredient>,
    val brown: Float,
    val presetId: String,
    val baseSeconds: Int,
    val doneness: Doneness,
)

@Composable
fun MainScreen(
    state: TimerState,
    presets: List<Preset>,
    initialPresetId: String?,
    initialBaseSeconds: Int,
    initialDoneness: Doneness,
    onStart: (StartRequest) -> Unit,
    onAddTime: (Int) -> Unit,
    onStop: () -> Unit,
    onOpenStats: (() -> Unit)? = null,
    onOpenCustomize: (() -> Unit)? = null,
    banner: @Composable () -> Unit = {},
) {
    val finished = state is TimerState.Finished
    val dark = MaterialTheme.colorScheme.background == TostiPalette.NightCrust
    val background by animateColorAsState(
        when {
            finished && dark -> TostiPalette.NightCheese
            finished -> TostiPalette.CheeseOrange
            else -> MaterialTheme.colorScheme.background
        },
        label = "background",
    )

    Surface(Modifier.fillMaxSize(), color = background) {
        Column(
            Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tostimer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (state is TimerState.Idle) {
                    onOpenStats?.let { TextButton(onClick = it) { HeaderLink("Stats") } }
                    onOpenCustomize?.let { TextButton(onClick = it) { HeaderLink("Customize") } }
                }
            }
            banner()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (state) {
                    TimerState.Idle -> IdleContent(presets, initialPresetId, initialBaseSeconds, initialDoneness, onStart)
                    is TimerState.Running -> RunningContent(state, onAddTime, onStop)
                    is TimerState.Finished -> FinishedContent(state, onAddTime, onStop)
                }
            }
        }
    }
}

@Composable
private fun HeaderLink(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun IdleContent(
    presets: List<Preset>,
    initialPresetId: String?,
    initialBaseSeconds: Int,
    initialDoneness: Doneness,
    onStart: (StartRequest) -> Unit,
) {
    var base by rememberSaveable { mutableIntStateOf(initialBaseSeconds) }
    var doneness by rememberSaveable { mutableStateOf(initialDoneness) }
    // The chosen preset is tracked by id, not matched by time, so tweaking the time never loses its name.
    var selectedId by rememberSaveable { mutableStateOf(initialPresetId) }
    val selected = presets.firstOrNull { it.id == selectedId } ?: presets.firstOrNull()
    val seconds = doneness.apply(base)

    Column(
        Modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Shows the result you'll get, so the doneness choice is visible.
        TostiIllustration(
            progress = doneness.brown,
            ingredients = selected?.ingredients ?: setOf(Ingredient.CHEESE),
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(0.72f),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepButton("−") { base = (doneness.apply(base) - 15).coerceAtLeast(15).let { (it / doneness.timeFactor).toInt() } }
            Digits(formatDuration(seconds * 1000L), size = 76)
            StepButton("+") { base = (doneness.apply(base) + 15).coerceAtMost(30 * 60).let { (it / doneness.timeFactor).toInt() } }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Doneness.entries.forEach { d ->
                Segment(d.label, d == doneness, Modifier.weight(1f)) { doneness = d }
            }
        }

        val scrolls = presets.size > 2
        Row(
            Modifier.fillMaxWidth().let { if (scrolls) it.horizontalScroll(rememberScrollState()) else it },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            presets.forEach { p ->
                PresetTile(
                    name = p.name.ifBlank { "Tosti" },
                    time = formatDuration(doneness.apply(p.seconds) * 1000L),
                    detail = p.ingredients.describe(),
                    selected = selected?.id == p.id,
                    modifier = if (scrolls) Modifier.widthIn(min = 150.dp) else Modifier.weight(1f),
                ) {
                    selectedId = p.id
                    base = p.seconds
                }
            }
        }

        Button(
            onClick = {
                onStart(
                    StartRequest(
                        seconds = seconds,
                        label = selected?.name?.ifBlank { "Tosti" } ?: "Tosti",
                        ingredients = selected?.ingredients ?: setOf(Ingredient.CHEESE),
                        brown = doneness.brown,
                        presetId = selected?.id.orEmpty(),
                        baseSeconds = base,
                        doneness = doneness,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = ButtonShape,
        ) { Text("Start timer", style = BigButtonText) }
    }
}

@Composable
private fun RunningContent(state: TimerState.Running, onAddTime: (Int) -> Unit, onStop: () -> Unit) {
    val remaining by produceState(state.endAt - System.currentTimeMillis(), state) {
        while (true) {
            value = (state.endAt - System.currentTimeMillis()).coerceAtLeast(0)
            delay(200)
        }
    }
    val elapsed = if (state.totalMs > 0) 1f - remaining.toFloat() / state.totalMs else 1f

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TostiIllustration(
            progress = elapsed * state.brown,
            ingredients = state.ingredients,
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(0.85f),
        )
        Digits(formatDuration(remaining), size = 88)
        Text("${state.label} tosti is toasting", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("+30s", Modifier.weight(1f)) { onAddTime(30) }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f).height(60.dp),
                shape = ButtonShape,
            ) { Text("Stop", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }
        }
    }
}

@Composable
private fun FinishedContent(state: TimerState.Finished, onAddTime: (Int) -> Unit, onStop: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TostiIllustration(
            progress = state.brown,
            ingredients = state.ingredients,
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(0.85f),
            wobble = true,
        )
        Text(
            "Your tosti is ready!",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp),
            textAlign = TextAlign.Center,
        )
        Text("${state.label} tosti is done", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
            ),
        ) { Text("Got it", style = BigButtonText) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("+30s", Modifier.weight(1f)) { onAddTime(30) }
            SecondaryButton("+1 min", Modifier.weight(1f)) { onAddTime(60) }
        }
    }
}

// ---- shared building blocks (also used by the stats and customize screens) ----

internal val BigButtonText = TextStyle(fontFamily = TostiFont, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

@Composable
internal fun Digits(text: String, size: Int) {
    Text(
        text,
        style = TextStyle(
            fontFamily = TostiFont,
            fontSize = size.sp,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
        ),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
internal fun StepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    ) { Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = ButtonShape,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    ) { Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }
}

/** One option of a small segmented choice, e.g. Pale / Golden / Dark. */
@Composable
internal fun Segment(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) ink else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else ink,
        border = BorderStroke(2.dp, ink),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun PresetTile(name: String, time: String, detail: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = TileShape,
        color = if (selected) ink else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else ink,
        border = BorderStroke(2.dp, ink),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
            Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(time, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}
