package dev.microcord.questhome

import android.app.Service
import android.app.Application
import android.content.Intent
import android.os.IBinder
import dev.mantikafasi.manticord.plugins.api.ManticordPlugin
import dev.mantikafasi.manticord.plugins.api.PluginScope
import dev.mantikafasi.manticord.plugins.api.pluginSettings
import rx.Completable

class QuestHomePlugin : ManticordPlugin {
    private data class Runtime(val scope: PluginScope, val shortcut: SettingsQuestShortcut?)

    @Volatile private var runtime: Runtime? = null

    override val settingsSchema = pluginSettings {
        action("quests", "Quests") { openQuests() }
    }

    override fun start(scope: PluginScope): Completable = Completable.fromAction {
        val application = scope.applicationContext.applicationContext as? Application
        val shortcut = application?.let { SettingsQuestShortcut(it, scope, ::openQuests) }
        runtime = Runtime(scope, shortcut)
        shortcut?.start()
    }

    override fun stop(): Completable = Completable.fromAction {
        val active = runtime
        runtime = null
        active?.shortcut?.stop()
    }

    private fun openQuests() {
        val scope = runtime?.scope ?: return
        scope.dialogs.page("Quests", "") { context -> QuestsScreen(context, scope) }
    }
}

class QuestDiscoveryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
