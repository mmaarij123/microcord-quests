package dev.microcord.questhome

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class QuestTest {
    @Test
    fun parsesAvailableQuestAndMobileVideo() {
        val data = readQuests(FIXTURE)
        val quest = data.quests.single()
        assertEquals("Watch the trailer", quest.name)
        assertEquals(90, quest.tasks.single().target)
        assertEquals(15, quest.tasks.single().progress)
        assertEquals("Watch on mobile", quest.tasks.single().label)
        assertNull(quest.tasks.single().instructions)
        assertEquals(listOf(
            QuestVideo("https://cdn.discordapp.com/assets/quests/123/trailer-low.mp4", false),
            QuestVideo("https://cdn.discordapp.com/assets/quests/123/trailer.m3u8", true),
            QuestVideo("https://cdn.discordapp.com/assets/quests/123/trailer.mp4", false),
        ), quest.tasks.single().videos)
        assertEquals(700, quest.rewards.single().orbs)
        assertEquals(840, quest.rewards.single().premiumOrbs)
        assertEquals(1_793_404_800_000L, quest.endsAt)
        assertEquals(1_793_664_000_000L, data.blockedUntil)
        assertFalse(quest.canClaim(quest.endsAt!! - 1))
        assertFalse(quest.canEnroll(quest.endsAt!! - 1))
        assertTrue(quest.copy(enrolled = false).canEnroll(quest.endsAt!! - 1))
        assertFalse(quest.copy(enrolled = false).canEnroll(quest.endsAt!!))
        assertTrue(quest.copy(completed = true).canClaim(quest.endsAt!!))
        assertFalse(quest.copy(completed = true, claimed = true).canClaim(quest.endsAt!!))
    }

    @Test
    fun usesServerOrbAmountsIncludingZeroAndNeverUsesAdvertisedAmountAsClaimed() {
        fun json(value: String) = JsonParser.parseString(value).asJsonObject
        assertEquals(0L, readOrbBalance(json("""{"balance":0}""")))
        assertEquals(1_540L, readOrbBalance(json("""{"balance":1540}""")))
        assertNull(readOrbBalance(json("""{"balance":-1}""")))
        assertEquals(840L, readOrbsClaimed(json("""{"quest_user_status":{"orb_quantity_claimed":840}}""")))
        assertNull(readOrbsClaimed(json("""{"orb_quantity":700}""")))
        val claimed = FIXTURE.deepCopy()
        claimed.getAsJsonArray("quests")[0].asJsonObject.getAsJsonObject("user_status")
            .addProperty("orb_quantity_claimed", 840)
        assertEquals(840L, readQuests(claimed, history = true).quests.single().orbsClaimed)
    }

    @Test
    fun currentKeepsClaimableQuestsAndShowsTheCompletedAlternative() {
        val quest = readQuests(FIXTURE).quests.single()
        val ended = quest.endsAt!!
        assertFalse(quest.isCurrent(ended))
        val mobile = quest.tasks.single()
        val desktop = mobile.copy(type = "WATCH_VIDEO", progress = mobile.target)
        val completed = quest.copy(completed = true, requiresAll = false, tasks = listOf(mobile, desktop))
        assertTrue(completed.isCurrent(ended))
        assertEquals(listOf(desktop), completed.visibleTasks)
        assertFalse(completed.copy(claimed = true).isCurrent(ended))
        assertFalse(completed.isCurrent(completed.rewardEndsAt!!))
    }

    @Test
    fun unreadableThemeColorsFallBackToReadableInk() {
        val dark = 0xFF202024.toInt()
        val light = 0xFFF5F6F8.toInt()
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertEquals(white, accessibleColor(black, dark))
        assertEquals(black, accessibleColor(white, light))
        assertTrue(contrastRatio(white, 0xFF4752C4.toInt()) >= 4.5)
    }

    @Test
    fun readsClaimedCatalogAndRejectsUntrustedAssets() {
        assertTrue(readQuests(FIXTURE, history = true).quests.single().claimed)
        assertNull(questAssetUrl("123", "//another.example/video.mp4"))
        assertNull(questAssetUrl("123", "https://cdn.discordapp.com@another.example/video.mp4"))
        assertNull(questAssetUrl("123", "../video.mp4"))
        assertNull(questAssetUrl("123", "%2e%2e/video.mp4"))
    }

    @Test
    fun preservesTheCdnPathsReturnedForBadgesVideoAndArtwork() {
        val id = "1571722578827939971"
        val video = "1571724715142160404"
        val art = "1571723351993622538.png"
        val body = JsonParser.parseString(
            """
            {"quests":[{
              "id":"$id",
              "config":{
                "messages":{"quest_name":"Badges 2.0"},
                "assets":{"game_tile":"PLACEHOLDER","game_tile_dark":"quests/$id/$art"},
                "task_config_v2":{"tasks":{"WATCH_VIDEO_ON_MOBILE":{"target":50,"assets":{
                  "video_low_res":{"url":"quests/$id/${video}_720.mp4"},
                  "video_hls":{"url":"/quests/$id/$video.m3u8"},
                  "video":{"url":"https://cdn.discordapp.com/quests/$id/${video}_1080.mp4"}
                }}}}
              }
            }]}
            """.trimIndent()
        ).asJsonObject
        val quest = readQuests(body).quests.single()
        val base = "https://cdn.discordapp.com/quests/$id"
        assertEquals(listOf("$base/$art"), quest.artwork)
        assertEquals(listOf(
            QuestVideo("$base/${video}_720.mp4", false),
            QuestVideo("$base/$video.m3u8", true),
            QuestVideo("$base/${video}_1080.mp4", false),
        ), quest.tasks.single().videos)
        assertEquals("$base/$art?quality=lossless%2Dhigh", questAssetUrl(id, "quests/$id/$art?quality=lossless%2Dhigh"))
        assertEquals("https://cdn.discordapp.com/assets/quests/$id/legacy.png", questAssetUrl(id, "/assets/quests/$id/legacy.png"))
    }

    @Test
    fun reusesHostConnectionForCatalogAndUserActions() {
        MockWebServer().use { server ->
            val host = Retrofit.Builder()
                .baseUrl(server.url("/api/v9/"))
                .client(OkHttpClient.Builder().addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("X-Fixture-Session", "host").build())
                }.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val api = createQuestApi(host.create(QuestApi::class.java))
            val quest = readQuests(FIXTURE).quests.single()

            server.enqueue(MockResponse().setBody(FIXTURE.toString()))
            assertTrue(api.current().execute().isSuccessful)
            server.takeRequest().also { request ->
                assertEquals("/api/v9/quests/@me", request.path)
                assertEquals("host", request.getHeader("X-Fixture-Session"))
            }
            server.enqueue(MockResponse().setBody("""{"balance":1540}"""))
            assertEquals(1_540L, readOrbBalance(api.orbBalance().execute().body()!!))
            assertEquals("/api/v9/users/@me/virtual-currency/balance", server.takeRequest().path)

            server.enqueue(MockResponse().setBody("{}"))
            api.enroll(quest.id, quest.actionBody()).execute()
            server.takeRequest().also { request ->
                assertEquals("POST", request.method)
                assertEquals("/api/v9/quests/123/enroll", request.path)
                val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                assertEquals(12, body["location"].asInt)
                assertEquals("sealed-fixture", body["metadata_sealed"].asString)
                assertFalse(body.has("platform"))
            }

            server.enqueue(MockResponse().setBody("{}"))
            api.claim(quest.id, quest.actionBody(platform = 0)).execute()
            server.takeRequest().also { request ->
                assertEquals("/api/v9/quests/123/claim-reward", request.path)
                assertEquals(0, JsonParser.parseString(request.body.readUtf8()).asJsonObject["platform"].asInt)
            }
        }
    }

    @Test
    fun playbackCreditsObservedPositionWithoutJumpingAhead() {
        val progress = PlaybackProgress(resumeSeconds = 15)
        progress.observe(16_000, elapsedMillis = 1_000)
        assertEquals(16, progress.seconds)
        progress.observe(90_000, elapsedMillis = 1_000)
        assertEquals(16, progress.seconds)
        progress.observe(17_000, elapsedMillis = 1_000)
        assertEquals(17, progress.seconds)
        progress.observe(16_500, elapsedMillis = 1_000)
        assertEquals(17, progress.seconds)
    }

    private companion object {
        val FIXTURE: JsonObject = JsonParser.parseString(
            """
            {
              "quest_enrollment_blocked_until": "2026-11-03T00:00:00+00:00",
              "quests": [{
                "id": "123",
                "metadata_sealed": "sealed-fixture",
                "user_status": {
                  "enrolled_at": "2026-10-28T01:00:00Z",
                  "completed_at": null,
                  "progress": {"WATCH_VIDEO_ON_MOBILE": {"value": 15}}
                },
                "config": {
                  "expires_at": "2026-10-31T00:00:00.12345Z",
                  "messages": {"quest_name": "Watch the trailer", "game_title": "Fixture game"},
                  "task_config_v2": {"join_operator": "and", "tasks": {
                    "WATCH_VIDEO_ON_MOBILE": {
                      "target": 90,
                      "messages": {"task_title": "PLACEHOLDER", "task_description": " placeholder "},
                      "assets": {
                        "video": {"url": "trailer.mp4"},
                        "video_low_res": {"url": "trailer-low.mp4"},
                        "video_hls": {"url": "trailer.m3u8"}
                      }
                    }
                  }},
                  "rewards_config": {
                    "platforms": [0],
                    "rewards_expire_at": "2026-11-10T00:00:00Z",
                    "rewards": [{"type": 4, "orb_quantity": 700, "premium_orb_quantity": 840, "messages": {"name": "Discord Orbs"}}]
                  }
                }
              }]
            }
            """.trimIndent()
        ).asJsonObject
    }
}
