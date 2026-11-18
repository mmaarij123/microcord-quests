package dev.microcord.questhome

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.discord.R as HostR
import com.discord.app.AppActivity
import com.discord.utilities.color.ColorCompat
import com.discord.widgets.settings.WidgetSettings
import dev.mantikafasi.manticord.plugins.api.PluginScope

internal class SettingsQuestShortcut(
    private val application: Application,
    private val scope: PluginScope,
    private val openQuests: () -> Unit,
) : Application.ActivityLifecycleCallbacks {
    private val main = Handler(Looper.getMainLooper())
    private val activities = mutableSetOf<AppActivity>()
    private val buttons = mutableMapOf<WidgetSettings, View>()
    private var registered = false
    @Volatile private var stopped = false

    private val fragments = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(manager: FragmentManager, fragment: Fragment) {
            if (fragment is WidgetSettings) attach(fragment)
        }

        override fun onFragmentViewDestroyed(manager: FragmentManager, fragment: Fragment) {
            if (fragment is WidgetSettings) remove(fragment)
        }
    }

    fun start() {
        scope.runOnMainThread { context ->
            if (!stopped && !registered) {
                application.registerActivityLifecycleCallbacks(this)
                registered = true
                (context as? AppActivity)?.let(::observe)
            }
        }
    }

    fun stop() {
        stopped = true
        main.post {
            if (registered) application.unregisterActivityLifecycleCallbacks(this)
            registered = false
            activities.forEach { it.supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragments) }
            activities.clear()
            buttons.keys.toList().forEach(::remove)
        }
    }

    private fun observe(activity: AppActivity) {
        if (stopped || !activities.add(activity)) return
        val manager = activity.supportFragmentManager
        manager.registerFragmentLifecycleCallbacks(fragments, true)
        attachExisting(manager)
    }

    private fun attachExisting(manager: FragmentManager) {
        manager.fragments.filter { it.isAdded }.forEach { fragment ->
            if (fragment is WidgetSettings && fragment.isResumed) attach(fragment)
            attachExisting(fragment.childFragmentManager)
        }
    }

    private fun attach(fragment: WidgetSettings) {
        if (stopped || buttons.containsKey(fragment)) return
        val root = fragment.view ?: return
        val toolbar = root.findViewById<Toolbar>(HostR.id.action_bar_toolbar) ?: return
        val pluginContext = scope.resources.context ?: return
        val icon = pluginContext.getDrawable(R.drawable.ic_quests)?.mutate() ?: return
        val tint = runCatching {
            ColorCompat.getThemedColorStateListDefault(toolbar.context, HostR.attr.colorInteractiveNormal)
        }.getOrNull()?.takeIf { Color.alpha(it) == 255 } ?: QuestPalette(toolbar.context).pageInk
        icon.setTint(tint)
        val label = pluginContext.getString(R.string.quests_shortcut)
        val button = ImageButton(toolbar.context).apply {
            val ripple = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
                setBackgroundResource(ripple.resourceId)
            } else {
                background = null
            }
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = label
            TooltipCompat.setTooltipText(this, label)
            setImageDrawable(icon)
            setOnClickListener {
                if (!stopped && fragment.isResumed && root.isShown) openQuests()
            }
        }
        // Toolbar lays out its native action menu first, then its end-aligned custom controls.
        toolbar.addView(button, Toolbar.LayoutParams(button.dp(48), button.dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
        buttons[fragment] = button
    }

    private fun remove(fragment: WidgetSettings) {
        val button = buttons.remove(fragment) ?: return
        (button.parent as? ViewGroup)?.removeView(button)
    }

    override fun onActivityCreated(activity: Activity, savedState: Bundle?) {
        if (activity is AppActivity) observe(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is AppActivity) observe(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is AppActivity && activities.remove(activity)) {
            activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragments)
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
