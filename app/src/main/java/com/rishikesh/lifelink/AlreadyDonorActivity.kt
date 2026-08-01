package com.rishikesh.lifelink
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rishikesh.lifelink.R
import com.rishikesh.lifelink.util.applySystemBarInsets

class AlreadyDonorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_already_donor)
        applySystemBarInsets()
    }
}