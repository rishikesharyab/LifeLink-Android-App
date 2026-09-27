package com.rishikesh.lifelink

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.rishikesh.lifelink.util.applyDynamicStatusBar

class LifeLinkApp : Application() {

    private var askedNotificationPermission = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannels()

        // Save/refresh FCM token whenever a user signs in (login, signup, app restart)
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser == null) return@addAuthStateListener
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                LifeLinkMessagingService.saveToken(token)
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is ComponentActivity) {
                    // Default to dark icons on light background for the whole app
                    activity.applyDynamicStatusBar(isLightBackground = true)
                }
            }
            override fun onActivityStarted(activity: Activity) {
                if (activity is ComponentActivity) {
                    activity.applyDynamicStatusBar(isLightBackground = true)
                }
            }
            override fun onActivityResumed(activity: Activity) {
                // Ask for POST_NOTIFICATIONS (Android 13+) once, AFTER location permission is
                // settled, so it doesn't collide with PatientHomeActivity's location request.
                if (activity is PatientHomeActivity &&
                    !askedNotificationPermission &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    askedNotificationPermission = true
                    ActivityCompat.requestPermissions(
                        activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002
                    )
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        val sound = Uri.parse("android.resource://$packageName/${R.raw.request_alert}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val requests = NotificationChannel(
            LifeLinkMessagingService.CHANNEL_REQUESTS,
            "Blood requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent blood requests from patients"
            setSound(sound, attrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
        }

        val general = NotificationChannel(
            LifeLinkMessagingService.CHANNEL_GENERAL,
            "Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        nm.createNotificationChannels(listOf(requests, general))
    }
}