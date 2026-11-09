package dev.chuds.still.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chuds.still.data.ClockFormat
import dev.chuds.still.data.DrawerFrictionMode
import dev.chuds.still.data.FontPreset
import dev.chuds.still.data.MAX_SLOT_COUNT
import dev.chuds.still.data.SettingsExporter
import dev.chuds.still.ui.components.StillMenuItem
import dev.chuds.still.ui.components.StillSectionCard
import dev.chuds.still.ui.home.HomeUiState
import dev.chuds.still.ui.theme.StillColors
import dev.chuds.still.ui.theme.StillTypography

@Composable
fun SettingsScreen(
    uiState: HomeUiState,
    onChooseSlot: (Int) -> Unit,
    onChangeSlotCount: (Int) -> Unit,
    onCycleClockFormat: () -> Unit,
    onToggleShowDate: () -> Unit,
    onToggleShowHomeHint: () -> Unit,
    onToggleShowAppIcons: () -> Unit,
    onToggleShowBattery: () -> Unit,
    onCycleFontPreset: () -> Unit,
    onToggleHaptics: () -> Unit,
    onCycleDrawerFrictionMode: () -> Unit,
    onOpenDrawerExceptions: () -> Unit,
    onOpenIntents: () -> Unit,
    onImportSettings: (String) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = uiState.settings
    val context = LocalContext.current
    val currentSettings by rememberUpdatedState(settings)
    var dataStatus by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val content = SettingsExporter.toJson(currentSettings)
        dataStatus = if (SettingsExporter.writeToUri(context, uri, content)) "exported" else "could not write file"
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = SettingsExporter.readFromUri(context, uri)
        if (text == null) {
            dataStatus = "could not read file"
        } else {
            dataStatus = if (onImportSettings(text)) "imported" else "not a still settings file"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StillColors.OledBlack)
            .systemBarsPadding()
            .padding(horizontal = 34.dp, vertical = 28.dp),
    ) {
        item {
            Text(
                text = "still settings",
                style = StillTypography.Kicker,
                color = StillColors.Gray,
            )
            Spacer(modifier = Modifier.height(28.dp))
        }

        item {
            SectionHeading("launcher")
            StillSectionCard {
                StillMenuItem(
                    title = "slot count",
                    subtitle = "${settings.slotCount}  —  tap +1, long-press -1",
                    style = StillTypography.SecondaryMenu,
                    onClick = {
                        val next = if (settings.slotCount >= MAX_SLOT_COUNT) 1 else settings.slotCount + 1
                        onChangeSlotCount(next)
                    },
                    onLongClick = {
                        val next = if (settings.slotCount <= 1) MAX_SLOT_COUNT else settings.slotCount - 1
                        onChangeSlotCount(next)
                    },
                )
                StillMenuItem(
                    title = "clock format",
                    subtitle = clockFormatLabel(settings.clockFormat),
                    style = StillTypography.SecondaryMenu,
                    onClick = onCycleClockFormat,
                )
                StillMenuItem(
                    title = "show date",
                    subtitle = if (settings.showDate) "on  —  taps open intents" else "off",
                    style = StillTypography.SecondaryMenu,
                    onClick = onToggleShowDate,
                )
                StillMenuItem(
                    title = "show home hint",
                    subtitle = if (settings.showHomeHint) "on  —  \"long press for all apps\"" else "off",
                    style = StillTypography.SecondaryMenu,
                    onClick = onToggleShowHomeHint,
                )
                StillMenuItem(
                    title = "app icons",
                    subtitle = if (settings.showAppIcons) "on  —  shown in all apps + picker" else "off",
                    style = StillTypography.SecondaryMenu,
                    onClick = onToggleShowAppIcons,
                )
                StillMenuItem(
                    title = "battery line",
                    subtitle = if (settings.showBattery) "on  —  percent under the date" else "off",
                    style = StillTypography.SecondaryMenu,
                    onClick = onToggleShowBattery,
                )
                StillMenuItem(
                    title = "font",
                    subtitle = fontPresetLabel(settings.fontPreset),
                    style = StillTypography.SecondaryMenu,
                    onClick = onCycleFontPreset,
                )
                StillMenuItem(
                    title = "haptic feedback",
                    subtitle = if (settings.hapticsEnabled) "on  —  subtle vibration on taps" else "off",
                    style = StillTypography.SecondaryMenu,
                    onClick = onToggleHaptics,
                )
                StillMenuItem(
                    title = "all apps intent prompt",
                    subtitle = drawerFrictionModeLabel(settings.drawerFrictionMode),
                    style = StillTypography.SecondaryMenu,
                    onClick = onCycleDrawerFrictionMode,
                )
                StillMenuItem(
                    title = "configure app exceptions",
                    subtitle = "${settings.drawerFrictionExceptions.size} apps",
                    style = StillTypography.SecondaryMenu,
                    onClick = onOpenDrawerExceptions,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            SectionHeading("slots")
            StillSectionCard {
                uiState.configuredSlots.forEach { resolved ->
                    val isFilled = resolved.isLaunchable
                    val title = resolved.displayLabel ?: "slot ${resolved.slot.index + 1}"
                    val titleColor = if (isFilled) StillColors.SoftWhite else StillColors.DimGray
                    val appLabel = resolved.app?.label
                    val subtitle = when {
                        !resolved.slot.isSet -> "empty"
                        !isFilled -> "missing"
                        resolved.slot.useFriction -> "use intentionally  —  ${appLabel.orEmpty()}"
                        appLabel != null && appLabel != title -> appLabel
                        else -> null
                    }
                    StillMenuItem(
                        title = title,
                        subtitle = subtitle,
                        style = StillTypography.SecondaryMenu,
                        titleColor = titleColor,
                        onClick = { onChooseSlot(resolved.slot.index) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            SectionHeading("data")
            StillSectionCard {
                StillMenuItem(
                    title = "export settings",
                    subtitle = "one json file — slots + prefs",
                    style = StillTypography.SecondaryMenu,
                    onClick = { exportLauncher.launch("still-settings.json") },
                )
                StillMenuItem(
                    title = "import settings",
                    subtitle = "replaces slots and prefs",
                    style = StillTypography.SecondaryMenu,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                )
            }
            if (dataStatus != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dataStatus.orEmpty(),
                    style = StillTypography.Caption,
                    color = StillColors.DimGray,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            SectionHeading("journal")
            StillSectionCard {
                StillMenuItem(
                    title = "intents",
                    style = StillTypography.SecondaryMenu,
                    onClick = onOpenIntents,
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            StillMenuItem(
                title = "back",
                style = StillTypography.SecondaryMenu,
                bordered = true,
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = StillTypography.Kicker,
        color = StillColors.Gray,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

private fun clockFormatLabel(format: ClockFormat): String = when (format) {
    ClockFormat.Auto -> "auto  —  follows system"
    ClockFormat.Hours12 -> "12-hour"
    ClockFormat.Hours24 -> "24-hour"
}

private fun fontPresetLabel(preset: FontPreset): String = when (preset) {
    FontPreset.System -> "system  —  platform fallbacks"
    FontPreset.Editorial -> "editorial  —  cormorant + inter"
    FontPreset.Terminal -> "terminal  —  ibm plex mono"
    FontPreset.Grotesk -> "grotesk  —  instrument + space grotesk"
}

private fun drawerFrictionModeLabel(mode: DrawerFrictionMode): String = when (mode) {
    DrawerFrictionMode.Off -> "off  —  drawer launches go straight through"
    DrawerFrictionMode.Allowlist -> "allowlist  —  prompt unless app is excepted"
    DrawerFrictionMode.Blocklist -> "blocklist  —  prompt only for excepted apps"
}
