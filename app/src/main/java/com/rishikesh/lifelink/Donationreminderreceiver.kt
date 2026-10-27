package com.rishikesh.lifelink

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

/**
 * Fires once, 3 months after a donor's last donation, to tell them they're
 * eligible (and visible in donor search) again. Scheduled from
 * PatientHomeActivity.populateDashboardUi().
 */
class DonationReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val REQ_CODE = 3001

        fun schedule(context: Context, triggerAtMillis: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context)
            if (triggerAtMillis <= System.currentTimeMillis()) {
                am.cancel(pi)
                return
            }
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }

        private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
            context,
            REQ_CODE,
            Intent(context, DonationReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (FirebaseAuth.getInstance().currentUser == null) return

        val enabled = context
            .getSharedPreferences(NotificationSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(NotificationSettingsActivity.KEY_DONATION_REMINDERS, true)
        if (!enabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, PatientHomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = "It's been 3 months since your last donation. You're eligible again and visible to patients nearby."

        val notification = NotificationCompat.Builder(context, LifeLinkMessagingService.CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_blood_drop)
            .setContentTitle("You can donate again")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context).notify(REQ_CODE, notification)
    }
}