package dev.microcord.questhome

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.discord.app.AppActivity
import com.discord.stores.StoreStream
import com.google.gson.JsonObject
import dev.mantikafasi.manticord.plugins.api.PluginScope
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import retrofit2.Call

internal class QuestsScreen(context: Context, private val scope: PluginScope) : LinearLayout(context) {
    private val request = QuestRequest()
    private val orbsRequest = QuestRequest()
    private val receiptRequest = QuestRequest()
    private var retryReceipt: Runnable? = null
    private val palette = QuestPalette(context)
    private val active = palette.button(context, "Current")
    private val claimed = palette.button(context, "Claimed")
    private val refresh = palette.button(context, "Refresh")
    private val banner = TextView(context).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        setTextColor(palette.pageInk)
        typeface = palette.regular
    }
    private val orbsItemId = View.generateViewId()
    private var orbsToolbar: Toolbar? = null
    private val orbsBalance = TextView(context).apply {
        typeface = palette.bold
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(palette.ink)
        setPadding(dp(12), dp(5), dp(12), dp(5))
        background = GradientDrawable().apply {
            setColor(palette.surface)
            setStroke(dp(1), palette.border)
            cornerRadius = dp(20).toFloat()
        }
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val balanceRow = LinearLayout(context).apply {
        gravity = Gravity.END
        addView(orbsBalance)
    }
    private val cards = LinearLayout(context).apply { orientation = VERTICAL }
    private var list: QuestList? = null
    private var history = false
    private var refreshOnReturn = false
    private var accountId: Long? = null
    private var lastBalance: Long? = null

    init {
        orientation = VERTICAL
        addView(balanceRow)
        renderBalance()
        addView(LinearLayout(context).apply {
            listOf(active, claimed, refresh).forEachIndexed { index, button ->
                addView(button, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(8)
                })
            }
        })
        addView(banner)
        addView(cards)
        active.setOnClickListener { selectHistory(false) }
        claimed.setOnClickListener { selectHistory(true) }
        refresh.setOnClickListener { load() }
        updateButtons()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachOrbsToToolbar()
        load()
    }

    override fun onDetachedFromWindow() {
        request.cancel()
        orbsRequest.cancel()
        receiptRequest.cancel()
        retryReceipt?.let(::removeCallbacks)
        retryReceipt = null
        orbsToolbar?.menu?.removeItem(orbsItemId)
        orbsToolbar = null
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && refreshOnReturn) {
            refreshOnReturn = false
            load()
        }
    }

    private fun selectHistory(value: Boolean) {
        if (history == value || request.busy) return
        history = value
        list = null
        cards.removeAllViews()
        load()
    }

    private fun load() {
        if (request.busy || !isAttachedToWindow) return
        val current = StoreStream.getUsers().getMe().id
        if (current != accountId) {
            accountId = current
            lastBalance = null
            renderBalance()
            list = null
            cards.removeAllViews()
        }
        refreshOrbs()
        perform(
            loading = "Loading quests…",
            makeCall = { if (history) questApi().claimed() else questApi().current() },
        ) { json ->
            list = readQuests(json, history)
            render()
        }
    }

    private fun attachOrbsToToolbar() {
        var host: Context = context
        while (host is ContextWrapper && host !is AppActivity && host.baseContext !== host) {
            host = host.baseContext
        }
        (orbsBalance.parent as? ViewGroup)?.removeView(orbsBalance)
        val toolbar = (host as? AppActivity)?.toolbar
        if (toolbar == null) {
            balanceRow.addView(orbsBalance)
            balanceRow.visibility = View.VISIBLE
            return
        }
        balanceRow.visibility = View.GONE
        orbsToolbar = toolbar
        toolbar.menu.add(Menu.NONE, orbsItemId, Menu.NONE, "Orbs balance")
            .setActionView(orbsBalance)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun refreshOrbs() {
        val expected = accountId ?: return
        if (!isAttachedToWindow) return
        orbsRequest.cancel()
        renderBalance(loading = lastBalance == null)
        orbsRequest.send(
            makeCall = { questApi().orbBalance() },
            onError = { renderBalance() },
        ) { payload ->
            if (StoreStream.getUsers().getMe().id == expected) {
                lastBalance = readOrbBalance(payload) ?: lastBalance
                renderBalance()
            }
        }
    }

    private fun renderBalance(loading: Boolean = false) {
        val balance = lastBalance
        orbsBalance.text = when {
            balance != null -> "${formatOrbs(balance)} Orbs"
            loading -> "Orbs …"
            else -> "Orbs —"
        }
        orbsBalance.contentDescription = when {
            balance != null -> "Orbs balance: ${formatOrbs(balance)}"
            loading -> "Loading Orbs balance"
            else -> "Orbs balance unavailable"
        }
    }

    private fun perform(
        loading: String,
        makeCall: () -> Call<JsonObject>,
        onSuccess: (JsonObject) -> Unit,
    ) {
        if (request.busy) return
        showMessage(loading)
        request.send(
            makeCall = makeCall,
            onError = { message ->
                updateButtons()
                showMessage(message)
            },
            onSuccess = { json ->
                if (StoreStream.getUsers().getMe().id != accountId) {
                    load()
                } else {
                    updateButtons()
                    onSuccess(json)
                }
            },
        )
        updateButtons()
    }

    private fun updateButtons() {
        active.isSelected = !history
        claimed.isSelected = history
        active.setTextColor(if (active.isSelected) palette.progress else palette.ink)
        claimed.setTextColor(if (claimed.isSelected) palette.progress else palette.ink)
        active.isEnabled = !request.busy && history
        claimed.isEnabled = !request.busy && !history
        refresh.isEnabled = !request.busy
    }

    private fun showMessage(message: String?) {
        banner.text = message
        banner.visibility = if (message == null) View.GONE else View.VISIBLE
        banner.setPadding(0, dp(8), 0, dp(8))
    }

    private fun render() {
        cards.removeAllViews()
        val data = list ?: return
        val now = System.currentTimeMillis()
        val visible = data.quests.filter { history || it.isCurrent(now) }
        val blockedUntil = data.blockedUntil?.takeIf { it > now }
        showMessage(when {
            visible.isEmpty() -> if (history) "No claimed quests yet." else "No quests are currently available."
            blockedUntil != null -> "You can accept more quests after ${formatDate(blockedUntil)}."
            else -> null
        })
        visible.sortedWith(compareBy<Quest> { it.claimed }.thenByDescending { it.completed }
            .thenByDescending { it.enrolled }.thenBy { it.endsAt ?: Long.MAX_VALUE })
            .forEach { quest -> addQuestCard(quest, now, blockedUntil != null) }
    }

    private fun addQuestCard(quest: Quest, now: Long, enrollmentBlocked: Boolean) {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(palette.surface)
                setStroke(dp(1), palette.border)
            }
        }
        cards.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        card.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            val icon = scope.resources.context?.getDrawable(R.drawable.ic_quests)?.mutate()?.apply {
                setTint(palette.progress)
            }
            addView(QuestArtwork.thumbnail(context, quest.artwork, icon, palette.border, dp(48), dp(12)))
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                label(quest.name, 20f, bold = true, top = 0)
                quest.game?.takeIf { it != quest.name }?.let { label(it, muted = true, top = 2) }
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        })
        val state = when {
            quest.claimed -> "Reward claimed"
            quest.canClaim(now) -> "Ready to claim"
            quest.completed -> "Claim period ended"
            quest.startsAt != null && now < quest.startsAt -> "Upcoming"
            quest.endsAt != null && now >= quest.endsAt -> "Expired"
            quest.enrolled -> "In progress"
            else -> "Available"
        }
        card.label(state, bold = true)
        val deadline = if (quest.completed) quest.rewardEndsAt else quest.endsAt
        if (!quest.claimed) {
            deadline?.let { card.label(if (quest.completed) "Claim by ${formatDate(it)}" else "Ends ${formatDate(it)}", muted = true) }
        }
        card.showRewards(quest)
        val tasks = if (quest.claimed) emptyList() else quest.visibleTasks
        if (!quest.completed && tasks.size > 1) {
            card.label(if (quest.requiresAll) "Complete all tasks" else "Complete one task", bold = true)
        }
        tasks.forEach { task ->
            card.label(task.label, bold = true)
            task.instructions?.takeIf { it != task.label }?.let { card.label(it) }
            task.platformHint?.let { card.label(it, muted = true) }
            if (task.target > 0) {
                val value = task.progress.coerceAtMost(task.target)
                val text = if (task.isTimed) "${duration(value)} / ${duration(task.target)}" else "$value / ${task.target}"
                card.label(text, muted = true)
                card.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = task.target
                    progress = value
                    progressTintList = ColorStateList.valueOf(palette.progress)
                    progressBackgroundTintList = ColorStateList.valueOf(palette.border)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LayoutParams(LayoutParams.MATCH_PARENT, dp(10)))
            }
            if (quest.enrolled && !quest.completed && !quest.claimed && (quest.endsAt == null || now < quest.endsAt) &&
                task.type == "WATCH_VIDEO_ON_MOBILE" && task.videos.isNotEmpty() && task.target > task.progress
            ) {
                card.button("Watch video") { openVideo(quest, task) }
            }
        }
        when {
            quest.claimed && quest.rewards.any { it.isCode } -> card.button("View reward code") { showCode(quest) }
            quest.canClaim(now) -> quest.platforms.forEach { platform ->
                val platformName = when (platform) {
                    0 -> null
                    1 -> "Xbox"
                    2 -> "PlayStation"
                    3 -> "Switch"
                    4 -> "PC"
                    else -> return@forEach
                }
                card.button(platformName?.let { "Claim for $it" } ?: "Claim reward") { claim(quest, platform) }
            }
            quest.canEnroll(now) && !enrollmentBlocked -> card.button("Accept quest") { enroll(quest) }
        }
    }

    private fun LinearLayout.showRewards(quest: Quest) {
        quest.orbsClaimed?.takeIf { quest.claimed }?.let { earned ->
            label("${formatOrbs(earned)} Orbs earned", bold = true)
        }
        quest.rewards.forEach { reward ->
            val orbs = reward.orbs
            when {
                orbs == null && reward.premiumOrbs != null && !quest.claimed ->
                    label("${formatOrbs(reward.premiumOrbs.toLong())} Orbs with eligible Nitro", bold = true)
                orbs == null -> label("Reward · ${reward.name}")
                quest.claimed && quest.orbsClaimed == null -> label("Orbs reward · amount unavailable")
                !quest.claimed -> {
                    val prefix = if (quest.completed) "Claim" else "Earn"
                    label("$prefix ${formatOrbs(orbs.toLong())} Orbs", bold = true)
                    reward.premiumOrbs?.takeIf { it > orbs }?.let { premium ->
                        label("${formatOrbs(premium.toLong())} Orbs with eligible Nitro", muted = true)
                    }
                }
            }
        }
    }

    private fun ensureCurrentAccount(): Boolean {
        if (StoreStream.getUsers().getMe().id == accountId) return true
        load()
        return false
    }

    private fun enroll(quest: Quest) {
        if (!ensureCurrentAccount() || !quest.canEnroll(System.currentTimeMillis())) return
        perform("Accepting quest…", { questApi().enroll(quest.id, quest.actionBody()) }) { json ->
            if (json.text("enrolled_at") == null) throw QuestException("Discord did not confirm enrollment.")
            updateQuest(quest.copy(enrolled = true))
            load()
        }
    }

    private fun claim(quest: Quest, platform: Int) {
        if (!ensureCurrentAccount() || !quest.canClaim(System.currentTimeMillis())) return
        perform("Claiming reward…", { questApi().claim(quest.id, quest.actionBody(platform)) }) { json ->
            if (json.text("claimed_at") == null) {
                val errors = json.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray
                val firstError = errors?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.text("message")
                throw QuestException(firstError?.take(180) ?: "Discord did not confirm the reward claim.")
            }
            val amount = readOrbsClaimed(json)
            updateQuest(quest.copy(claimed = true, orbsClaimed = amount))
            showClaimReceipt(quest, amount)
            load()
        }
    }

    private fun showClaimReceipt(quest: Quest, amount: Long?) {
        val expected = accountId ?: return
        receiptRequest.cancel()
        retryReceipt?.let(::removeCallbacks)
        retryReceipt = null
        val hasOrbs = quest.rewards.any { it.orbs != null || it.premiumOrbs != null }
        val show = { claimedOrbs: Long? ->
            if (isAttachedToWindow && StoreStream.getUsers().getMe().id == expected) {
                val message = when {
                    claimedOrbs != null -> "You earned ${formatOrbs(claimedOrbs)} Orbs for ${quest.name}."
                    hasOrbs -> "Discord confirmed the claim. The Orbs amount is pending in Claimed."
                    else -> "Your reward for ${quest.name} is ready in Claimed."
                }
                scope.dialogs.alert("Reward claimed", message)
            }
        }
        if (amount != null || !hasOrbs) {
            show(amount)
            return
        }
        fun loadReceipt(mayRetry: Boolean) {
            if (!isAttachedToWindow || StoreStream.getUsers().getMe().id != expected) return
            receiptRequest.send({ questApi().claimed() }, { show(null) }) { payload ->
                val credited = readQuests(payload, history = true).quests.firstOrNull { it.id == quest.id }?.orbsClaimed
                if (credited == null && mayRetry) {
                    retryReceipt = Runnable { loadReceipt(mayRetry = false) }.also { postDelayed(it, 900L) }
                } else {
                    show(credited)
                    refreshOrbs()
                }
            }
        }
        loadReceipt(mayRetry = true)
    }

    private fun updateQuest(quest: Quest) {
        list = list?.let { data -> data.copy(quests = data.quests.map { if (it.id == quest.id) quest else it }) }
        render()
    }

    private fun showCode(quest: Quest) {
        if (!ensureCurrentAccount()) return
        perform("Loading reward code…", { questApi().rewardCode(quest.id) }) { json ->
            val code = json.text("code") ?: throw QuestException("This quest has no available reward code.")
            showMessage(null)
            scope.dialogs.confirm("Reward code", code, "Copy code") {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Quest reward code", code))
                Toast.makeText(context, "Reward code copied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openVideo(quest: Quest, task: QuestTask) {
        if (!ensureCurrentAccount()) return
        val expectedAccount = accountId ?: return
        refreshOnReturn = true
        scope.dialogs.page(quest.name, "Video quest") { context ->
            QuestVideoView(context, quest.id, task, expectedAccount)
        }
    }

    private fun LinearLayout.label(
        value: String, size: Float = 14f, bold: Boolean = false, muted: Boolean = false, top: Int = 6,
    ) {
        addView(TextView(context).apply {
            text = value
            textSize = size
            typeface = if (bold) palette.bold else palette.regular
            setTextColor(if (muted) palette.muted else palette.ink)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) })
    }

    private fun LinearLayout.button(value: String, onClick: () -> Unit) {
        addView(palette.button(context, value, primary = true).apply {
            setOnClickListener { if (!request.busy) onClick() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
    }
}

internal fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
internal fun duration(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
private fun formatDate(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    .format(Date(millis))
private fun formatOrbs(amount: Long): String = NumberFormat.getIntegerInstance().format(amount)
