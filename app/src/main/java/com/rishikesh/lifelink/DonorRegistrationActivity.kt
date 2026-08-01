package com.rishikesh.lifelink
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rishikesh.lifelink.R
import com.rishikesh.lifelink.util.applySystemBarInsets

class DonorRegistrationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_donor_profile)
        applySystemBarInsets()
    }
}