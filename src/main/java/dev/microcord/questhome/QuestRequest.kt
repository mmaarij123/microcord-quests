package dev.microcord.questhome

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal class QuestRequest {
    private var current: Call<JsonObject>? = null
    val busy: Boolean get() = current != null

    fun cancel() {
        current?.cancel()
        current = null
    }

    fun send(
        makeCall: () -> Call<JsonObject>,
        onError: (String) -> Unit,
        onSuccess: (JsonObject) -> Unit,
    ) {
        if (busy) return
        val call = try {
            makeCall()
        } catch (error: Exception) {
            onError(errorMessage(error))
            return
        } catch (_: LinkageError) {
            onError("This Microcord build does not support the Quests connection.")
            return
        }
        current = call
        try {
            call.enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    if (current !== call) {
                        response.errorBody()?.close()
                        return
                    }
                    current = null
                    if (!response.isSuccessful) {
                        onError(response.errorMessage())
                        return
                    }
                    try {
                        onSuccess(response.body() ?: throw QuestException("Discord returned an empty response."))
                    } catch (error: Exception) {
                        onError(errorMessage(error))
                    }
                }

                override fun onFailure(call: Call<JsonObject>, error: Throwable) {
                    if (current !== call) return
                    current = null
                    onError(errorMessage(error))
                }
            })
        } catch (error: Exception) {
            current = null
            onError(errorMessage(error))
        }
    }
}

private fun errorMessage(error: Throwable): String = when (error) {
    is QuestException -> error.message.orEmpty()
    is IOException -> "Couldn’t reach Discord. Check your connection and try again."
    else -> "Quests couldn’t process Discord’s response. Try refreshing."
}

private fun Response<JsonObject>.errorMessage(): String {
    val json = errorBody()?.use { body ->
        runCatching { JsonParser.parseString(body.string()).asJsonObject }.getOrNull()
    }
    if (json?.has("captcha_key") == true) return "Discord requires verification for this action."
    return when (code()) {
        401 -> "Discord couldn’t authorize this request for your Microcord session."
        403 -> json?.text("message")?.take(180) ?: "Discord restricted this action for your account or device."
        404 -> "Discord no longer offers this quest or endpoint."
        429 -> "Discord is rate limiting requests. Try again shortly."
        in 500..599 -> "Discord is having trouble. Try again shortly."
        else -> json?.text("message")?.take(180) ?: "Discord rejected this action (HTTP ${code()})."
    }
}
