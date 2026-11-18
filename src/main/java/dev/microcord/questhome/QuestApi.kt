package dev.microcord.questhome

import com.discord.utilities.rest.RestAPI
import com.google.gson.JsonObject
import java.lang.reflect.Proxy
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal interface QuestApi {
    @GET("quests/@me")
    fun current(): Call<JsonObject>

    @GET("quests/@me/claimed")
    fun claimed(): Call<JsonObject>

    @GET("users/@me/virtual-currency/balance")
    fun orbBalance(): Call<JsonObject>

    @POST("quests/{id}/enroll")
    fun enroll(@Path("id") id: String, @Body body: JsonObject): Call<JsonObject>

    @POST("quests/{id}/claim-reward")
    fun claim(@Path("id") id: String, @Body body: JsonObject): Call<JsonObject>

    @GET("quests/{id}/reward-code")
    fun rewardCode(@Path("id") id: String): Call<JsonObject>

    @POST("quests/{id}/video-progress")
    fun videoProgress(@Path("id") id: String, @Body body: JsonObject): Call<JsonObject>
}

internal fun questApi(): QuestApi {
    val hostApi = RestAPI.apiOrNull ?: throw QuestException("Sign in to Microcord to see your quests.")
    return createQuestApi(hostApi)
}

// Extend the host Retrofit service so authentication, rate limits, and verification stay with Microcord.
internal fun createQuestApi(hostApi: Any): QuestApi {
    val handler = if (Proxy.isProxyClass(hostApi.javaClass)) Proxy.getInvocationHandler(hostApi) else null
    val field = handler?.javaClass?.declaredFields?.singleOrNull { it.type == Retrofit::class.java }
        ?: throw QuestException("This Microcord build does not expose a compatible Discord connection.")
    field.isAccessible = true
    val host = field.get(handler) as Retrofit
    return host.create(QuestApi::class.java)
}

internal class QuestException(message: String) : RuntimeException(message)
