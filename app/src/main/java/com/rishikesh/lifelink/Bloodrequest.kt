package com.rishikesh.lifelink.model

import java.util.Date

/**
 * Stored in Firestore at: BloodRequests/{fromUserId}_{toUserId}
 * One document per (patient, donor) pair — resending just updates
 * status back to "pending" and bumps createdAt, rather than creating
 * a new document. This makes the 3-minute resend cooldown a simple
 * check against this single document's createdAt.
 */
data class BloodRequest(
    val id: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val fromUserPhone: String = "",
    val fromUserLocation: String = "",
    val toUserId: String = "",
    val toUserName: String = "",
    val toUserLocation: String = "",
    val bloodGroup: String = "",
    val distanceKm: Double = 0.0,
    val status: String = STATUS_PENDING, // pending | accepted | declined
    val donated: Boolean = false,
    val createdAt: Date? = null,
    val respondedAt: Date? = null,
    val donatedAt: Date? = null
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"

        const val RESEND_COOLDOWN_MS = 3 * 60 * 1000L // 3 minutes

        fun docId(fromUserId: String, toUserId: String) = "${fromUserId}_$toUserId"
    }
}