package com.example.prayernotifier.data.notifications

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import androidx.annotation.RequiresApi
import com.example.prayernotifier.MainActivity
import com.example.prayernotifier.R
import com.example.prayernotifier.data.persistence.KeyValueStore
import com.example.prayernotifier.i18n.AppLanguage

/** Turns the phone's Do Not Disturb on and off for prayers. */
interface Silencer {
    /** Android 10+ with Do Not Disturb access granted to the app. */
    fun available(): Boolean
    fun start()
    fun stop()
    /** Removes the app's rule from the system settings (feature switched off). */
    fun remove()
}

/**
 * [Silencer] through an automatic Do Not Disturb rule named "Prayer time",
 * owned by the app and visible in the system's Do Not Disturb settings
 * (Modes on Android 15+), where the user can see it.
 *
 * The rule is separate from the user's own Do Not Disturb: switching the
 * rule off never turns off Do Not Disturb the user turned on, and if the
 * user turns it off early, Android keeps it off. While it is on, the phone
 * is totally silent: no ringing, notification sounds, alarms, media or
 * vibration. Calls and notifications still arrive, silently.
 */
class DoNotDisturbSilencer(context: Context) : Silencer {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)

    override fun available(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && manager?.isNotificationPolicyAccessGranted == true

    override fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !available()) return
        runCatching {
            val id = ruleId()?.also(::upgradeRule) ?: addRule()
            manager.setAutomaticZenRuleState(id, condition(Condition.STATE_TRUE))
        }
    }

    override fun stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !available()) return
        runCatching { ruleId()?.let { manager.setAutomaticZenRuleState(it, condition(Condition.STATE_FALSE)) } }
    }

    override fun remove() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !available()) return
        runCatching { ruleId()?.let(manager::removeAutomaticZenRule) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ruleId(): String? =
        manager.automaticZenRules.entries.firstOrNull { it.value.conditionId == CONDITION_ID }?.key

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun addRule(): String = manager.addAutomaticZenRule(rule())

    /**
     * Rules made before 0.3.3 let alarms and media (and before 0.3.2, repeat
     * callers) make sound; make them totally silent, and keep them so.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun upgradeRule(id: String) {
        val existing = manager.getAutomaticZenRule(id) ?: return
        if (existing.interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) return
        existing.interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE
        existing.zenPolicy = null
        manager.updateAutomaticZenRule(id, existing)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun rule() = AutomaticZenRule(
        ruleName(),
        null,
        ComponentName(app, MainActivity::class.java),
        CONDITION_ID,
        null, // Android rejects a policy on any filter but "priority only"
        NotificationManager.INTERRUPTION_FILTER_NONE,
        true
    )

    private fun condition(state: Int) = Condition(CONDITION_ID, ruleName(), state)

    private fun ruleName(): String =
        AppLanguage.localizedContext(app).resources.getString(R.string.dnd_rule_name)

    private companion object {
        val CONDITION_ID: Uri = Uri.Builder()
            .scheme(Condition.SCHEME)
            .authority("com.example.prayernotifier")
            .appendPath("prayer")
            .build()
    }
}

/**
 * When the current silence ends, kept across reboots so a silence that was
 * on when the phone restarted is ended (or resumed) correctly.
 */
class SilenceState(private val store: KeyValueStore) {
    suspend fun until(): Long? = store.get(KEY)?.toLongOrNull()
    suspend fun set(untilMillis: Long) = store.put(KEY, untilMillis.toString())
    suspend fun clear() = store.remove(KEY)

    private companion object {
        const val KEY = "silence_until"
    }
}
