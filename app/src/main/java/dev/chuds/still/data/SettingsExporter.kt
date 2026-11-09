package dev.chuds.still.data

import android.content.Context
import android.net.Uri
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plaintext persistence for the whole launcher configuration, per the still pact ("plaintext
 * export forever where the primitive owns user data"). Handoff between devices happens as one
 * JSON file the user picks with the system picker — no cloud, no sync holes.
 */
object SettingsExporter {

    private const val FORMAT = "still-settings"
    private const val VERSION = 1

    fun toJson(settings: LauncherSettings): String {
        val slots = JSONArray()
        settings.slots.forEach { slot ->
            if (!slot.isSet) return@forEach
            slots.put(
                JSONObject().apply {
                    put("index", slot.index)
                    put("pkg", slot.packageName)
                    put("cls", slot.className)
                    if (!slot.customLabel.isNullOrBlank()) put("label", slot.customLabel)
                    if (slot.useFriction) put("friction", true)
                },
            )
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("slotCount", settings.slotCount)
            put("clockFormat", settings.clockFormat.name)
            put("showDate", settings.showDate)
            put("showHomeHint", settings.showHomeHint)
            put("showBattery", settings.showBattery)
            put("showAppIcons", settings.showAppIcons)
            put("fontPreset", settings.fontPreset.name)
            put("hapticsEnabled", settings.hapticsEnabled)
            put("firstLaunchCompleted", settings.firstLaunchCompleted)
            put("drawerFrictionMode", settings.drawerFrictionMode.name)
            put(
                "drawerFrictionExceptions",
                JSONArray().apply {
                    settings.drawerFrictionExceptions.forEach { put(it) }
                },
            )
            put("slots", slots)
        }.toString()
    }

    /** Returns null when the text isn't a still settings file. Unknown fields are ignored. */
    fun fromJson(text: String): LauncherSettings? = try {
        val root = JSONObject(text)
        if (root.optString("format") != FORMAT) return null

        val slots = (0 until MAX_SLOT_COUNT).map { index ->
            HomeSlot(index = index)
        }.toMutableList()
        val slotArray = root.optJSONArray("slots")
        if (slotArray != null) {
            for (i in 0 until slotArray.length()) {
                val obj = slotArray.optJSONObject(i) ?: continue
                val index = obj.optInt("index", -1)
                if (index < 0 || index >= MAX_SLOT_COUNT) continue
                val packageName = obj.optString("pkg")
                val className = obj.optString("cls")
                if (packageName.isBlank() || className.isBlank()) continue
                slots[index] = HomeSlot(
                    index = index,
                    packageName = packageName,
                    className = className,
                    customLabel = obj.optString("label").takeIf { it.isNotBlank() },
                    useFriction = obj.optBoolean("friction", false),
                )
            }
        }

        LauncherSettings(
            slots = slots,
            slotCount = root.optInt("slotCount", DEFAULT_SLOT_COUNT)
                .coerceIn(1, MAX_SLOT_COUNT),
            clockFormat = enumOr(
                name = root.optString("clockFormat"),
                default = ClockFormat.Auto,
            ),
            showDate = root.optBoolean("showDate", true),
            showHomeHint = root.optBoolean("showHomeHint", true),
            showBattery = root.optBoolean("showBattery", false),
            showAppIcons = root.optBoolean("showAppIcons", false),
            fontPreset = enumOr(
                name = root.optString("fontPreset"),
                default = FontPreset.System,
            ),
            hapticsEnabled = root.optBoolean("hapticsEnabled", true),
            firstLaunchCompleted = root.optBoolean("firstLaunchCompleted", false),
            drawerFrictionMode = enumOr(
                name = root.optString("drawerFrictionMode"),
                default = DrawerFrictionMode.Allowlist,
            ),
            drawerFrictionExceptions = buildStringSet(root.optJSONArray("drawerFrictionExceptions")),
        )
    } catch (_: Exception) {
        null
    }

    private fun buildStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return default
        return try {
            enumValueOf(trimmed)
        } catch (_: IllegalArgumentException) {
            default
        }
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        }
        true
    } catch (_: IOException) {
        false
    }

    fun readFromUri(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        }
    } catch (_: IOException) {
        null
    }
}
