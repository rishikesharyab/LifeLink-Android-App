package com.rishikesh.lifelink

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class LifeLinkMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_REQUESTS = "blood_requests_v1"
        const val CHANNEL_GENERAL = "general_updates"
        const val TYPE_BLOOD_REQUEST = "blood_request"
        const val TYPE_NEARBY_CAMP = "nearby_camp"
        const val TYPE_DONATION_REMINDER = "donation_reminder"

        /** Stored at FcmTokens/{uid}. Separate collection so other users can't read tokens. */
        fun saveToken(token: String) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection("FcmTokens")
                .document(uid)
                .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
        }
    }

    override fun onNewToken(token: String) {
        saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return

        // Honour the in-app Notifications settings toggles
        val prefKey = when (type) {
            TYPE_BLOOD_REQUEST -> NotificationSettingsActivity.KEY_DONATION_REQUESTS
            TYPE_NEARBY_CAMP -> NotificationSettingsActivity.KEY_NEARBY_CAMPS
            TYPE_DONATION_REMINDER -> NotificationSettingsActivity.KEY_DONATION_REMINDERS
            else -> null
        }
        if (prefKey != null) {
            val enabled = getSharedPreferences(NotificationSettingsActivity.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(prefKey, true)
            if (!enabled) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val isRequest = type == TYPE_BLOOD_REQUEST
        val channelId = if (isRequest) CHANNEL_REQUESTS else CHANNEL_GENERAL

        // Tap → Home, with Receive Request on top (back goes to Home, not out of the app)
        val home = Intent(this, PatientHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val target = if (isRequest) Intent(this, ReceiveRequestActivity::class.java) else home
        val pendingIntent = PendingIntent.getActivities(
            this,
            System.currentTimeMillis().toInt(),
            arrayOf(home, target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_blood_drop)
            .setContentTitle(data["title"] ?: "LifeLink")
            .setContentText(data["body"] ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"] ?: ""))
            .setPriority(if (isRequest) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // Pre-Oreo devices ignore channels, so set the sound here too
        if (isRequest) {
            builder.setSound(Uri.parse("android.resource://$packageName/${R.raw.request_alert}"))
        }

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), builder.build())
    }
}