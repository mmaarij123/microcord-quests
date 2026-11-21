@file:Suppress("DEPRECATION") // Microcord provides ExoPlayer 2.19.

package io.github.mmaarij123.quests

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.discord.stores.StoreStream
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.gson.JsonObject

internal class QuestVideoView(
    context: Context,
    private val questId: String,
    private val task: QuestTask,
    private val accountId: Long,
) : LinearLayout(context) {
    private val palette = QuestPalette(context)
    private val playerView = PlayerView(context).apply { useController = false }
    private val buffering = ProgressBar(context)
    private val timeline = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1_000
        progressTintList = ColorStateList.valueOf(palette.progress)
        progressBackgroundTintList = ColorStateList.valueOf(palette.border)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val playbackTime = TextView(context).apply {
        typeface = palette.regular
        setTextColor(palette.pageInk)
        textSize = 14f
        gravity = Gravity.END
    }
    private val status = TextView(context).apply {
        typeface = palette.regular
        setTextColor(palette.pageInk)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val control = palette.button(context, "Pause", primary = true)
    private val request = QuestRequest()
    private val watched = PlaybackProgress(task.progress)
    private val http = DefaultHttpDataSource.Factory().setConnectTimeoutMs(8_000).setReadTimeoutMs(8_000)
    private var player: ExoPlayer? = null
    private var sourceIndex = 0
    private var reported = task.progress
    private var lastSample = 0L
    private var wantsPlayback = true
    private var firstFrame = false
    private var sourceFailed = false
    private var ended = false
    private var completed = false
    private var progressError = false
    private val startupDeadline = Runnable {
        if (wantsPlayback && !firstFrame) switchSource(timedOut = true)
    }
    private val ticker = object : Runnable {
        override fun run() {
            val active = player ?: return
            if (!wantsPlayback || !hasWindowFocus() || !isShown || !isSameAccount()) return
            updateTimeline()
            if (active.isPlaying && firstFrame) {
                capturePosition(active.currentPosition)
                reportProgress(force = false)
            } else {
                lastSample = SystemClock.elapsedRealtime()
            }
            if (wantsPlayback) postDelayed(this, 500L)
        }
    }
    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            updateTimeline()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateTimeline()
            when (playbackState) {
                Player.STATE_BUFFERING -> if (wantsPlayback) {
                    buffering.visibility = View.VISIBLE
                    status.text = if (firstFrame) "Buffering video…" else "Loading video…"
                }
                Player.STATE_READY -> if (!wantsPlayback) buffering.visibility = View.GONE
                Player.STATE_ENDED -> finishPlayback()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) return
            lastSample = SystemClock.elapsedRealtime()
            if (firstFrame) showPlaying()
        }

        override fun onRenderedFirstFrame() {
            firstFrame = true
            removeCallbacks(startupDeadline)
            if (player?.isPlaying == true) showPlaying()
        }

        override fun onPlayerError(error: PlaybackException) {
            val httpStatus = generateSequence(error.cause) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
            switchSource(httpStatus = httpStatus)
        }
    }

    init {
        orientation = VERTICAL
        addView(FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(playerView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(buffering, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(224)))
        addView(timeline, LayoutParams(LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(8) })
        addView(playbackTime)
        updateTimeline()
        addView(status)
        addView(control)
        status.text = "Loading video…"
        status.setPadding(0, dp(10), 0, dp(4))
        control.setOnClickListener {
            if (!isSameAccount()) return@setOnClickListener
            when {
                ended && progressError -> reportProgress(force = true)
                sourceFailed -> {
                    wantsPlayback = true
                    openSource(0)
                }
                wantsPlayback -> {
                    player?.takeIf { it.isPlaying && firstFrame }?.let { capturePosition(it.currentPosition) }
                    pausePlayback()
                    reportProgress(force = true)
                }
                !ended && hasWindowFocus() -> startPlayback()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        player = ExoPlayer.Builder(context)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(5_000, 20_000, 750, 1_500).build())
            .build()
            .also {
                playerView.player = it
                it.addListener(listener)
            }
        if (!ended) openSource(sourceIndex) else showFinished()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus && player?.playWhenReady == true) pausePlayback()
        if (hasWindowFocus && wantsPlayback && !ended && !sourceFailed) startPlayback()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != View.VISIBLE && player?.playWhenReady == true) pausePlayback()
    }

    override fun onDetachedFromWindow() {
        request.cancel()
        removeCallbacks(ticker)
        removeCallbacks(startupDeadline)
        val active = player
        player = null
        playerView.player = null
        active?.removeListener(listener)
        active?.release()
        super.onDetachedFromWindow()
    }

    private fun isSameAccount(): Boolean {
        if (StoreStream.getUsers().getMe().id == accountId) return true
        pausePlayback()
        request.cancel()
        status.text = "Your Discord account changed. Reopen Quests."
        control.isEnabled = false
        return false
    }

    private fun openSource(index: Int) {
        val active = player ?: return
        val video = task.videos.getOrNull(index) ?: return
        sourceIndex = index
        sourceFailed = false
        firstFrame = false
        removeCallbacks(startupDeadline)
        val item = MediaItem.fromUri(video.url)
        val media = if (video.hls) HlsMediaSource.Factory(http).createMediaSource(item)
        else ProgressiveMediaSource.Factory(http).createMediaSource(item)
        active.setMediaSource(media, watched.seconds * 1_000L)
        active.prepare()
        if (wantsPlayback && hasWindowFocus()) startPlayback()
    }

    private fun switchSource(timedOut: Boolean = false, httpStatus: Int? = null) {
        if (sourceFailed || ended || !isSameAccount()) return
        if (sourceIndex + 1 < task.videos.size) {
            openSource(sourceIndex + 1)
            if (wantsPlayback) status.text = "Trying another video stream…"
            return
        }
        pausePlayback()
        player?.stop()
        sourceFailed = true
        status.text = when {
            timedOut -> "The video took too long to load. You can retry."
            httpStatus != null -> "Discord’s video stream returned HTTP $httpStatus. You can retry."
            else -> "Discord’s video stream couldn’t be opened. You can retry."
        }
        control.text = "Retry video"
    }

    private fun startPlayback() {
        val active = player ?: return
        wantsPlayback = true
        progressError = false
        control.text = "Pause"
        lastSample = SystemClock.elapsedRealtime()
        if (!firstFrame) {
            status.text = "Loading video…"
            buffering.visibility = View.VISIBLE
            removeCallbacks(startupDeadline)
            postDelayed(startupDeadline, 15_000L)
        }
        active.play()
        removeCallbacks(ticker)
        postDelayed(ticker, 500L)
    }

    private fun pausePlayback() {
        wantsPlayback = false
        player?.pause()
        updateTimeline()
        removeCallbacks(ticker)
        removeCallbacks(startupDeadline)
        buffering.visibility = View.GONE
        control.text = "Play"
        status.text = "Paused"
    }

    private fun showPlaying() {
        buffering.visibility = View.GONE
        status.text = if (completed) "Quest completed. Go back to claim the reward."
        else "Playing"
    }

    private fun updateTimeline() {
        val active = player
        val total = active?.duration?.takeIf { it > 0 }
        val position = active?.currentPosition?.coerceAtLeast(0) ?: watched.seconds * 1_000L
        val elapsed = when {
            total == null -> position
            active.playbackState == Player.STATE_ENDED -> total
            else -> position.coerceAtMost(total)
        }
        timeline.progress = if (total == null) 0 else (elapsed.toDouble() / total * timeline.max).toInt()
        val elapsedLabel = duration((elapsed / 1_000).toInt())
        val totalLabel = total?.let { duration((it / 1_000).toInt()) } ?: "–:––"
        val label = "$elapsedLabel / $totalLabel"
        if (playbackTime.text.toString() != label) playbackTime.text = label
    }

    private fun finishPlayback() {
        if (ended) return
        val current = player
        if (current != null && wantsPlayback && firstFrame && isShown && hasWindowFocus()) {
            capturePosition(current.currentPosition)
        }
        ended = true
        pausePlayback()
        control.isEnabled = false
        reportProgress(force = true)
        if (!request.busy) showFinished()
    }

    private fun capturePosition(positionMillis: Long) {
        val time = SystemClock.elapsedRealtime()
        watched.observe(positionMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), time - lastSample)
        lastSample = time
    }

    private fun reportProgress(force: Boolean) {
        if (request.busy || completed || !isSameAccount()) return
        val position = watched.seconds
        if (position <= reported || (!force && position - reported < 10)) return
        progressError = false
        request.send(
            makeCall = {
                questApi().videoProgress(questId, JsonObject().apply { addProperty("timestamp", position) })
            },
            onError = { error ->
                progressError = true
                pausePlayback()
                status.text = error
                if (ended) {
                    control.text = "Retry progress"
                    control.isEnabled = true
                }
            },
        ) { json ->
            if (!isSameAccount()) return@send
            reported = position
            completed = json.text("completed_at") != null
            if (ended && !completed && watched.seconds > reported) {
                reportProgress(force = true)
            } else if (ended) {
                showFinished()
            } else if (player?.isPlaying == true && firstFrame) {
                showPlaying()
            } else if (!wantsPlayback) {
                status.text = "Paused"
            }
        }
    }

    private fun showFinished() {
        if (progressError) return
        control.isEnabled = false
        status.text = if (completed) "Quest completed. Go back to claim the reward."
        else "Video ended. Go back to check Discord’s recorded progress."
    }
}
