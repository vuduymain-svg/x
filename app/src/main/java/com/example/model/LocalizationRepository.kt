package com.example.model

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class LocalizedEntry(
    val nameRef: String,
    val name: String,
    val description: String = ""
)

private data class GlobalsTermJson(
    val nameRef: String = "",
    val name: String = "",
    val description: String = ""
)

private data class GlobalsRegionJson(
    val nameRef: String = "",
    val name: String = "",
    val abbreviation: String = ""
)

private data class GlobalsLabelJson(
    val nameRef: String = "",
    val name: String = ""
)

private data class GlobalsViVnPayload(
    val vocabTerms: List<GlobalsTermJson> = emptyList(),
    val keywords: List<GlobalsTermJson> = emptyList(),
    val regions: List<GlobalsRegionJson> = emptyList(),
    val spellSpeeds: List<GlobalsLabelJson> = emptyList(),
    val rarities: List<GlobalsLabelJson> = emptyList(),
    val adventureRarities: List<GlobalsLabelJson> = emptyList()
)

/**
 * Runtime Vietnamese localization dictionary backed by `assets/globals-vi_vn.json`.
 * Maps official LoR `nameRef` keys (e.g., `Overwhelm`, `Frostbite`, `QuickStrike`, `Elusive`,
 * `Slow`, `Fast`, `Burst`, `Common`, `Rare`, `Epic`, `PiltoverZaun`) to localized names and descriptions.
 */
object LocalizationRepository {

    const val ASSET_FILE_NAME = "globals-vi_vn.json"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val payloadAdapter by lazy {
        moshi.adapter(GlobalsViVnPayload::class.java)
    }

    @Volatile
    private var keywordsByRef: Map<String, LocalizedEntry> = emptyMap()

    @Volatile
    private var vocabTermsByRef: Map<String, LocalizedEntry> = emptyMap()

    @Volatile
    private var regionsByRef: Map<String, LocalizedEntry> = emptyMap()

    @Volatile
    private var spellSpeedsByRef: Map<String, LocalizedEntry> = emptyMap()

    @Volatile
    private var raritiesByRef: Map<String, LocalizedEntry> = emptyMap()

    @Volatile
    var isLoaded: Boolean = false
        private set

    fun ensureLoaded(context: Context): Boolean {
        if (isLoaded) return true
        return synchronized(this) {
            if (isLoaded) return@synchronized true
            try {
                val jsonText = context.assets.open(ASSET_FILE_NAME)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                loadFromJson(jsonText)
            } catch (_: Exception) {
                false
            }
        }
    }

    fun loadFromJson(jsonText: String): Boolean {
        val payload = try {
            payloadAdapter.fromJson(jsonText)
        } catch (_: Exception) {
            null
        } ?: return false

        val kwMap = LinkedHashMap<String, LocalizedEntry>()
        payload.keywords.forEach { item ->
            if (item.nameRef.isNotBlank()) {
                kwMap[item.nameRef] = LocalizedEntry(
                    nameRef = item.nameRef,
                    name = item.name.trim(),
                    description = item.description.trim()
                )
            }
        }

        val vocabMap = LinkedHashMap<String, LocalizedEntry>()
        payload.vocabTerms.forEach { item ->
            if (item.nameRef.isNotBlank()) {
                vocabMap[item.nameRef] = LocalizedEntry(
                    nameRef = item.nameRef,
                    name = item.name.trim(),
                    description = item.description.trim()
                )
            }
        }

        val regionMap = LinkedHashMap<String, LocalizedEntry>()
        payload.regions.forEach { item ->
            if (item.nameRef.isNotBlank()) {
                regionMap[item.nameRef] = LocalizedEntry(
                    nameRef = item.nameRef,
                    name = item.name.trim(),
                    description = ""
                )
            }
        }

        val speedMap = LinkedHashMap<String, LocalizedEntry>()
        payload.spellSpeeds.forEach { item ->
            if (item.nameRef.isNotBlank()) {
                val kwEntry = kwMap[item.nameRef]
                speedMap[item.nameRef] = LocalizedEntry(
                    nameRef = item.nameRef,
                    name = item.name.trim(),
                    description = kwEntry?.description.orEmpty()
                )
            }
        }
        // Include Focus & Skill descriptions which appear under keywords in globals-vi_vn.json
        listOf("Focus", "Skill").forEach { extraSpeedRef ->
            if (!speedMap.containsKey(extraSpeedRef)) {
                kwMap[extraSpeedRef]?.let { speedMap[extraSpeedRef] = it }
            }
        }

        val rarityMap = LinkedHashMap<String, LocalizedEntry>()
        payload.adventureRarities.forEach { item ->
            if (item.nameRef.isNotBlank()) {
                rarityMap[item.nameRef] = LocalizedEntry(item.nameRef, item.name.trim())
            }
        }
        payload.rarities.forEach { item ->
            if (item.nameRef.isNotBlank()) {
                rarityMap[item.nameRef] = LocalizedEntry(item.nameRef, item.name.trim())
            }
        }

        keywordsByRef = kwMap
        vocabTermsByRef = vocabMap
        regionsByRef = regionMap
        spellSpeedsByRef = speedMap
        raritiesByRef = rarityMap
        isLoaded = true
        return true
    }

    internal fun clearForTesting() {
        keywordsByRef = emptyMap()
        vocabTermsByRef = emptyMap()
        regionsByRef = emptyMap()
        spellSpeedsByRef = emptyMap()
        raritiesByRef = emptyMap()
        isLoaded = false
    }

    fun findKeywordEntry(nameRef: String): LocalizedEntry? =
        keywordsByRef[nameRef] ?: vocabTermsByRef[nameRef]

    fun findVocabEntry(nameRef: String): LocalizedEntry? =
        vocabTermsByRef[nameRef] ?: keywordsByRef[nameRef]

    fun keywordName(nameRef: String, fallback: String): String =
        findKeywordEntry(nameRef)?.name?.takeIf { it.isNotBlank() } ?: fallback

    fun keywordDescription(nameRef: String, fallback: String): String =
        findKeywordEntry(nameRef)?.description?.takeIf { it.isNotBlank() } ?: fallback

    fun termName(nameRef: String, fallback: String): String =
        findVocabEntry(nameRef)?.name?.takeIf { it.isNotBlank() } ?: fallback

    fun termDescription(nameRef: String, fallback: String): String =
        findVocabEntry(nameRef)?.description?.takeIf { it.isNotBlank() } ?: fallback

    fun spellSpeedName(nameRef: String, fallback: String): String =
        spellSpeedsByRef[nameRef]?.name?.takeIf { it.isNotBlank() }
            ?: findKeywordEntry(nameRef)?.name?.takeIf { it.isNotBlank() }
            ?: fallback

    fun spellSpeedDescription(nameRef: String, fallback: String): String =
        spellSpeedsByRef[nameRef]?.description?.takeIf { it.isNotBlank() }
            ?: findKeywordEntry(nameRef)?.description?.takeIf { it.isNotBlank() }
            ?: fallback

    fun rarityName(nameRef: String, fallback: String): String =
        raritiesByRef[nameRef]?.name?.takeIf { it.isNotBlank() } ?: fallback

    fun regionName(nameRef: String, fallback: String): String =
        regionsByRef[nameRef]?.name?.takeIf { it.isNotBlank() } ?: fallback
}
