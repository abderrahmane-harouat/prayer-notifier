package com.example.prayernotifier.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives exact-alarm fires, Do Not Disturb starts and ends, the daily re-plan alarm, and reboots. */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleAlarm(AppGraph(context).handler, intent)
            } finally {
                pending.finish()
            }
        }
    }
}

/** What each alarm means; shared with the multi-day simulation tests. */
internal suspend fun handleAlarm(handler: PrayerAlarmHandler, intent: Intent) {
    when (intent.action) {
        ExactAlarmScheduler.ACTION_PRAYER_ALARM -> handler.onAlarmFired(
            intent.getStringExtra(ExactAlarmScheduler.EXTRA_PRAYER).orEmpty(),
            intent.getStringExtra(ExactAlarmScheduler.EXTRA_TIME).orEmpty(),
            intent.getStringExtra(ExactAlarmScheduler.EXTRA_LEAD)?.toIntOrNull() ?: 0,
            intent.silenceUntil()
        )
        ExactAlarmScheduler.ACTION_SILENCE_START ->
            intent.silenceUntil()?.let { handler.startSilence(it) }
        ExactAlarmScheduler.ACTION_SILENCE_END -> handler.endSilence()
        ExactAlarmScheduler.ACTION_REPLENISH,
        Intent.ACTION_BOOT_COMPLETED -> handler.onDayChanged()
    }
}

private fun Intent.silenceUntil(): Long? =
    getStringExtra(ExactAlarmScheduler.EXTRA_SILENCE_UNTIL)?.toLongOrNull()
