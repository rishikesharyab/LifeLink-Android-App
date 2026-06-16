package com.rishikesh.lifelink.model         // ← your package
import android.os.Parcelable
 import kotlinx.parcelize.Parcelize
 import java.util.Date

 @Parcelize
data class Donor(
//    val name: String,
//    val bloodGroup: String,
//    val phone: String,
//    val latitude: Double,
//    val longitude: Double,
//    val distanceKm: Double,
//    val id: String = "",
//    val location: String = "",                    // ← default
//    val totalDonations: Int = 0,                  // ← default
//    val lastDonationDate: java.util.Date? = null, // ← default
//    val isAvailable: Boolean = true

     val id: String = "",
     val name: String = "",
     val location: String = "",
     val bloodGroup: String = "",

     val phone: String = "",
     val latitude: Double = 0.0,
     val longitude: Double = 0.0,
     val distanceKm: Double = 0.0,

     val totalDonations: Int = 0,
     val lastDonationDate: Date? = null,
     val isAvailable: Boolean = true
): Parcelable {
     fun initials(): String = name
         .split(" ")
         .filter { it.isNotBlank() }
         .take(2)
         .joinToString("") { it.first().uppercaseChar().toString() }
 }
