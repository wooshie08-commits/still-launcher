package dev.chuds.still.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chuds.still.data.ClockFormat
import dev.chuds.still.ui.components.StillMenuItem
import dev.chuds.still.ui.theme.StillColors
import dev.chuds.still.ui.theme.StillTypography
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The home surface.
 *
 * No wordmark. Clock + optional date + optional battery, then only the slots the user has filled
 * (within the configured count). Slots that point at an uninstalled app stay listed as
 * `missing` so the mapping can be replaced with one tap. No `add app` placeholders — adding a
 * slot happens in settings.
 *
 * Long-press background → all apps. Tap date → intents journal.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onLaunchSlot: (Int) -> Unit,
    onEditSlot: (Int) -> Unit,
    onReplaceSlot: (Int) -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenIntents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var batteryPct by remember { mutableStateOf(-1) }
    val backgroundInteractionSource = remember { MutableInteractionSource() }
    val dateInteractionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }

    if (uiState.settings.showBattery) {
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    batteryPct = batteryPercent(i)
                }
            }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = context.registerReceiver(receiver, filter)
            if (sticky != null) batteryPct = batteryPercent(sticky)
            onDispose {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }

    val use24h = when (uiState.settings.clockFormat) {
        ClockFormat.Hours24 -> true
        ClockFormat.Hours12 -> false
        ClockFormat.Auto -> DateFormat.is24HourFormat(context)
    }
    val clockPattern = if (use24h) "HH:mm" else "h:mm"
    val clockFormatter = remember(clockPattern) {
        DateTimeFormatter.ofPattern(clockPattern, Locale.getDefault())
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StillColors.OledBlack)
            .combinedClickable(
                interactionSource = backgroundInteractionSource,
                indication = null,
                onClick = {},
                onLongClick = onOpenAllApps,
            )
            .systemBarsPadding()
            .padding(horizontal = 34.dp, vertical = 28.dp),
    ) {
        Text(
            text = now.format(clockFormatter),
            style = StillTypography.Clock,
            color = StillColors.SoftWhite,
        )

        if (uiState.settings.showDate) {
            Text(
                text = now.format(dateFormatter),
                style = StillTypography.Date,
                color = StillColors.Gray,
                modifier = Modifier.clickable(
                    interactionSource = dateInteractionSource,
                    indication = null,
                    onClick = onOpenIntents,
                ),
            )
        }

        if (uiState.settings.showBattery && batteryPct >= 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$batteryPct%",
                style = StillTypography.Caption,
                color = StillColors.DimGray,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        uiState.presentableSlots.forEach { resolved ->
            val missing = resolved.isMissing
            StillMenuItem(
                title = resolved.displayLabel ?: "slot ${resolved.slot.index + 1}",
                subtitle = when {
                    missing -> "not installed — tap to replace"
                    resolved.slot.useFriction -> "use intentionally"
                    else -> null
                },
                titleColor = if (missing) StillColors.Gray else StillColors.SoftWhite,
                onClick = {
                    if (missing) onReplaceSlot(resolved.slot.index) else onLaunchSlot(resolved.slot.index)
                },
                onLongClick = { onEditSlot(resolved.slot.index) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (uiState.settings.showHomeHint) {
            Text(
                text = "long press for all apps",
                style = StillTypography.Caption,
                color = StillColors.DimGray,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun batteryPercent(intent: Intent): Int {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else -1
}
