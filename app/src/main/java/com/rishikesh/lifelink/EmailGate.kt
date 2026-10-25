package com.rishikesh.lifelink

import android.util.Log
import com.google.firebase.auth.FirebaseUser

object EmailGate {
    // Accounts created BEFORE this moment are exempt from verification (epoch millis).
    private const val ENFORCE_FROM_MILLIS = 1792923000000L // 25 Oct 2026, 10:10 UTC

    fun isAllowed(user: FirebaseUser): Boolean {
        val created = user.metadata?.creationTimestamp ?: return true
        val allowed = user.isEmailVerified || created < ENFORCE_FROM_MILLIS
        Log.d("EMAIL_GATE", "verified=${user.isEmailVerified} created=$created cutoff=$ENFORCE_FROM_MILLIS allowed=$allowed")
        return allowed
    }
}