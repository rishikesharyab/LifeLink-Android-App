package com.rishikesh.lifelink

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Public, search-safe copy of a donor's profile at Donors/{uid}.
 * Never contains phone or email. Lat/lng are rounded to ~1 km unless the
 * donor has "Show exact location" on. Always rebuilt from the owner's Users doc.
 */
object DonorDirectory {

    private const val PREFS = "donor_directory_prefs"

    /** Rebuilds Donors/{uid} from Users/{uid}. Call after any change to a public field. */
    fun sync(uid: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { doc -> write(db, uid, doc) }
            .addOnFailureListener { Log.w("DONOR_DIR", "Couldn't read Users/$uid for sync", it) }
    }

    /** One-time backfill for donors who registered before Donors/{uid} existed. */
    fun migrateIfNeeded(context: Context, uid: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "migrated_$uid"
        if (prefs.getBoolean(key, false)) return

        val db = FirebaseFirestore.getInstance()
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { doc ->
                val task = write(db, uid, doc)
                if (task == null) {
                    // Not a donor yet — CompleteDonorProfileActivity will sync when they become one
                    prefs.edit().putBoolean(key, true).apply()
                } else {
                    task.addOnSuccessListener { prefs.edit().putBoolean(key, true).apply() }
                        .addOnFailureListener { Log.w("DONOR_DIR", "Migration write failed", it) }
                }
            }
            .addOnFailureListener { Log.w("DONOR_DIR", "Migration read failed", it) }
    }

    private fun write(db: FirebaseFirestore, uid: String, doc: DocumentSnapshot): Task<Void>? {
        if (!doc.exists() || doc.getBoolean("profileCompleted") != true) return null

        val precise = doc.getBoolean("preciseLocation") ?: true
        val lat = doc.getDouble("latitude") ?: 0.0
        val lng = doc.getDouble("longitude") ?: 0.0

        val data = hashMapOf<String, Any?>(
            "name" to (doc.getString("name") ?: ""),
            "bloodGroup" to (doc.getString("bloodGroup") ?: ""),
            "location" to (doc.getString("location") ?: ""),
            "available" to (doc.getBoolean("available") ?: true),
            "lastDonationDate" to doc.getTimestamp("lastDonationDate"),
            "latitude" to if (precise) lat else approx(lat),
            "longitude" to if (precise) lng else approx(lng),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return db.collection("Donors").document(uid).set(data)
    }

    // 2 decimals ≈ 1.1 km
    private fun approx(v: Double): Double = Math.round(v * 100) / 100.0
}