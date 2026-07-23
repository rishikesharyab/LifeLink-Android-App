package com.rishikesh.lifelink.model

import java.util.Date

data class DonationRecord(
    val id: String = "",
    val date: Date? = null,
    val campName: String = "",
    val location: String = "",
    val bloodGroup: String = "",
    val unitsDonated: Int = 1,
    val receiverName: String = "",
    val receiverLocation: String = ""
)