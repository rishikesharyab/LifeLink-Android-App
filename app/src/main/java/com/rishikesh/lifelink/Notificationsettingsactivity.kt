package com.rishikesh.lifelink

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class NotificationSettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "notification_prefs"
        const val KEY_NEARBY_CAMPS = "nearby_camps"
        const val KEY_DONATION_REQUESTS = "donation_requests"
        const val KEY_DONATION_REMINDERS = "donation_reminders"
    }

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        findViewById<ImageView>(R.id.ivNotifBack).setOnClickListener { finish() }

        val switchNearbyCamps = findViewById<SwitchMaterial>(R.id.switchNearbyCamps)
        val switchDonationRequests = findViewById<SwitchMaterial>(R.id.switchDonationRequests)
        val switchDonationReminders = findViewById<SwitchMaterial>(R.id.switchDonationReminders)

        // All default to ON so users don't miss anything unless they opt out
        switchNearbyCamps.isChecked = prefs.getBoolean(KEY_NEARBY_CAMPS, true)
        switchDonationRequests.isChecked = prefs.getBoolean(KEY_DONATION_REQUESTS, true)
        switchDonationReminders.isChecked = prefs.getBoolean(KEY_DONATION_REMINDERS, true)

        switchNearbyCamps.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_NEARBY_CAMPS, checked).apply()
        }
        switchDonationRequests.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_DONATION_REQUESTS, checked).apply()
        }
        switchDonationReminders.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_DONATION_REMINDERS, checked).apply()
        }
    }
}