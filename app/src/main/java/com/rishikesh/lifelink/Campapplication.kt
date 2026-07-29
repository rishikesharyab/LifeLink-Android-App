package com.rishikesh.lifelink.model

import java.util.Date

/**
 * Stored at: BloodCamps/{campId}/applications/{applicationId}
 * Written when a donor registers for a camp (CampDetailActivity.registerForCamp),
 * with a snapshot of their profile at that time so the org doesn't need to
 * separately look up each donor's Users doc.
 */
data class CampApplication(
    val id: String = "",
    val donorId: String = "",
    val name: String = "",
    val age: Int = 0,
    val bloodGroup: String = "",
    val phone: String = "",
    val appliedAt: Date? = null,
    val donated: Boolean = false,
    val donatedAt: Date? = null
)