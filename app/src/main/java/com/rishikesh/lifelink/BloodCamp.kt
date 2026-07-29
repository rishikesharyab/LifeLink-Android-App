package com.rishikesh.lifelink.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BloodCamp(
    val campId: String = "",
    val campName: String = "",
    val ngoName: String = "",
    val date: String = "",
    val location: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val startTime: String = "",
    val endTime: String = "",
    val endTimeMillis: Long = 0L,
    val distanceKm        : Double       = 0.0,
    val contactName       : String       = "",
    val designation       : String       = "",
    val phone             : String       = "",
    val email             : String       = "",
    val bloodGroupsNeeded : List<String> = emptyList(),
    val facilities        : List<String> = emptyList(),
    val registeredBy: List<String> = emptyList(),
    val orgId: String = ""
) : Parcelable