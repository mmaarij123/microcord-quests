package io.github.mmaarij123.quests

import com.google.gson.JsonObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale

internal data class QuestList(val quests: List<Quest>, val blockedUntil: Long?)

internal data class Quest(
    val id: String,
    val name: String,
    val game: String?,
    val startsAt: Long?,
    val endsAt: Long?,
    val rewardEndsAt: Long?,
    val tasks: List<QuestTask>,
    val requiresAll: Boolean,
    val rewards: List<QuestReward>,
    val platforms: List<Int>,
    val orbsClaimed: Long?,
    val enrolled: Boolean,
    val completed: Boolean,
    val claimed: Boolean,
    val artwork: List<String>,
    val metadata: String?,
    val trafficMetadata: String?,
) {
    fun actionBody(platform: Int? = null) = JsonObject().apply {
        addProperty("location", 12) // QUEST_HOME_MOBILE
        platform?.let { addProperty("platform", it) }
        metadata?.let { addProperty("metadata_sealed", it) }
        trafficMetadata?.let { addProperty("traffic_metadata_sealed", it) }
    }

    fun canEnroll(now: Long): Boolean = !enrolled && !completed && !claimed &&
        (startsAt == null || now >= startsAt) && (endsAt == null || now < endsAt)

    fun canClaim(now: Long): Boolean = completed && !claimed && (rewardEndsAt == null || now < rewardEndsAt)

    fun isCurrent(now: Long): Boolean = when {
        claimed -> false
        completed -> canClaim(now)
        else -> endsAt == null || now < endsAt
    }

    val visibleTasks: List<QuestTask>
        get() = if (completed && !requiresAll) {
            tasks.filter { it.target > 0 && it.progress >= it.target }.ifEmpty { tasks }
        } else {
            tasks
        }
}

internal data class QuestTask(
    val type: String,
    val title: String?,
    val instructions: String?,
    val target: Int,
    val progress: Int,
    val videos: List<QuestVideo>,
) {
    val isTimed: Boolean get() = type in TIMED_TASKS
    val label: String get() = title ?: when (type) {
        "WATCH_VIDEO" -> "Watch on desktop or browser"
        "WATCH_VIDEO_ON_MOBILE" -> "Watch on mobile"
        "PLAY_ON_DESKTOP", "PLAY_ON_DESKTOP_V2" -> "Play on desktop"
        "STREAM_ON_DESKTOP" -> "Stream on desktop"
        "PLAY_ON_XBOX" -> "Play on Xbox"
        "PLAY_ON_PLAYSTATION" -> "Play on PlayStation"
        "PLAY_ACTIVITY" -> "Play the Discord Activity"
        "ACHIEVEMENT_IN_GAME", "ACHIEVEMENT_IN_ACTIVITY" -> "Complete the required achievement"
        else -> "Complete the quest requirement"
    }

    val platformHint: String? get() = when (type) {
        "WATCH_VIDEO" -> if (title == null) null else "Desktop or browser video quest"
        "WATCH_VIDEO_ON_MOBILE" -> if (videos.isEmpty()) "The mobile video is unavailable." else null
        "PLAY_ON_DESKTOP", "PLAY_ON_DESKTOP_V2", "STREAM_ON_DESKTOP" -> "Tracked by Discord on desktop"
        "PLAY_ON_XBOX", "PLAY_ON_PLAYSTATION", "ACHIEVEMENT_IN_GAME" -> "Tracked by the linked game account"
        "PLAY_ACTIVITY", "ACHIEVEMENT_IN_ACTIVITY" -> "Tracked by the activity"
        else -> null
    }
}

internal data class QuestVideo(val url: String, val hls: Boolean)
internal data class QuestReward(val name: String, val orbs: Int?, val premiumOrbs: Int?, val isCode: Boolean)

internal fun readOrbBalance(body: JsonObject): Long? =
    body.text("balance")?.toLongOrNull()?.takeIf { it >= 0L }

internal fun readOrbsClaimed(body: JsonObject): Long? =
    listOfNotNull(body, body.obj("user_status"), body.obj("quest_user_status"))
        .firstNotNullOfOrNull { it.text("orb_quantity_claimed")?.toLongOrNull()?.takeIf { amount -> amount >= 0L } }

internal fun readQuests(body: JsonObject, history: Boolean = false): QuestList {
    require(body.get("quests")?.isJsonArray == true) { "Invalid quest catalog response" }
    return QuestList(body.objects("quests").mapNotNull { readQuest(it, history) }, body.date("quest_enrollment_blocked_until"))
}

private fun readQuest(item: JsonObject, history: Boolean): Quest? {
    val config = item.obj("config") ?: return null
    val id = item.text("id") ?: config.text("id") ?: return null
    if (!id.matches(Regex("[0-9]{1,20}"))) return null
    val status = item.obj("user_status")
    val messages = config.obj("messages")
    val taskConfig = config.obj("task_config_v2") ?: config.obj("task_config")
    val progress = status?.obj("progress")
    val tasks = taskConfig?.obj("tasks")?.entrySet().orEmpty().mapNotNull { (key, raw) ->
        if (!raw.isJsonObject) return@mapNotNull null
        val task = raw.asJsonObject
        val type = task.text("type") ?: key
        val taskMessages = task.obj("messages")
        val assets = task.obj("assets")
        val videos = listOf("video_low_res", "video_hls", "video").mapNotNull { format ->
            questAssetUrl(id, assets?.obj(format)?.text("url"))?.let { url ->
                QuestVideo(url, format == "video_hls" || URI(url).path.endsWith(".m3u8", ignoreCase = true))
            }
        }.distinctBy(QuestVideo::url)
        val value = progress?.obj(key)?.number("value") ?: progress?.obj(type)?.number("value")
        QuestTask(
            type = type,
            title = taskMessages?.displayText("task_title") ?: taskMessages?.displayText("video_title"),
            instructions = taskMessages?.displayText("task_description") ?: task.displayText("account_link_instructions"),
            target = (task.number("target") ?: 0).coerceAtLeast(0),
            progress = (value ?: 0).coerceAtLeast(0),
            videos = videos,
        )
    }
    val rewardsConfig = config.obj("rewards_config")
    val rewards = (rewardsConfig?.objects("rewards") ?: config.objects("rewards")).map { reward ->
        QuestReward(
            name = reward.obj("messages")?.text("name") ?: reward.text("name") ?: "Quest reward",
            orbs = reward.number("orb_quantity")?.takeIf { it > 0 },
            premiumOrbs = reward.number("premium_orb_quantity")?.takeIf { it > 0 },
            isCode = reward.number("type") == 1,
        )
    }
    val platforms = rewardsConfig?.get("platforms")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { it.takeIf { entry -> entry.isJsonPrimitive }?.asString?.toIntOrNull() }
        ?.distinct().orEmpty()
    return Quest(
        id = id,
        name = messages?.text("quest_name") ?: messages?.text("game_title") ?: "Quest",
        game = messages?.text("game_title") ?: config.obj("application")?.text("name"),
        startsAt = config.date("starts_at"),
        endsAt = config.date("expires_at"),
        rewardEndsAt = rewardsConfig?.date("rewards_expire_at"),
        tasks = tasks,
        requiresAll = taskConfig?.text("join_operator") == "and",
        rewards = rewards,
        platforms = platforms.ifEmpty { listOf(0) },
        orbsClaimed = status?.let(::readOrbsClaimed),
        enrolled = status?.text("enrolled_at") != null,
        completed = status?.text("completed_at") != null,
        claimed = history || status?.text("claimed_at") != null,
        artwork = config.obj("assets")?.let { assets ->
            listOf("game_tile", "game_tile_dark", "game_tile_light", "hero")
                .mapNotNull { questAssetUrl(id, assets.text(it)) }.distinct()
        }.orEmpty(),
        metadata = item.text("metadata_sealed"),
        trafficMetadata = item.text("traffic_metadata_sealed"),
    )
}

internal fun questAssetUrl(id: String, raw: String?): String? {
    val asset = raw?.trim()?.takeUnless { it.equals("placeholder", ignoreCase = true) } ?: return null
    val uri = runCatching { URI(asset) }.getOrNull() ?: return null
    if (uri.isAbsolute) return asset.takeIf {
        uri.scheme == "https" && uri.host in DISCORD_MEDIA_HOSTS && uri.userInfo == null && uri.port == -1
    }
    val path = uri.rawPath?.trimStart('/').orEmpty()
    if (uri.rawAuthority != null || uri.rawFragment != null || path.isBlank() ||
        '%' in path || path.split('/').any { it == "." || it == ".." }
    ) return null
    val cdnPath = if (path.startsWith("quests/") || path.startsWith("assets/quests/")) path
    else "assets/quests/$id/$path"
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "https://cdn.discordapp.com/$cdnPath$query"
}

internal fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }
    ?.asString?.takeIf { it.isNotBlank() }

private fun JsonObject.displayText(key: String): String? =
    text(key)?.trim()?.takeUnless { it.equals("placeholder", ignoreCase = true) }

private fun JsonObject.number(key: String) = text(key)?.toIntOrNull()
private fun JsonObject.obj(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonObject.objects(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    ?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }.orEmpty()

private fun JsonObject.date(key: String): Long? {
    val iso = text(key) ?: return null
    val normalized = iso.replace(Regex("\\.\\d+(?=Z|[+-])"), "")
        .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2").replace("Z", "+0000")
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { isLenient = false }.parse(normalized)?.time
    }.getOrNull()
}

private val DISCORD_MEDIA_HOSTS = setOf("discord.com", "cdn.discordapp.com", "media.discordapp.net")
private val TIMED_TASKS = setOf("WATCH_VIDEO", "WATCH_VIDEO_ON_MOBILE", "PLAY_ON_DESKTOP", "PLAY_ON_DESKTOP_V2",
    "STREAM_ON_DESKTOP", "PLAY_ON_XBOX", "PLAY_ON_PLAYSTATION", "PLAY_ACTIVITY")
