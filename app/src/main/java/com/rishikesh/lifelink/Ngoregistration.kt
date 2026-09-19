package com.rishikesh.lifelink.model

data class NgoRegistration(
    val id: String = "",
    val uid: String = "",
    val orgName: String = "",
    val orgType: String = "",
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val panNumber: String = "",
    val ngoDarpanId: String = "",
    val certificateUrl: String = "",
    val verificationStatus: String = "pending",
    val rejectionReason: String = ""
)