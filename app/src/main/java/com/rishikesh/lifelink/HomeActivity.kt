package com.rishikesh.lifelink

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rishikesh.lifelink.util.applySystemBarInsets
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        applySystemBarInsets()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        val india = LatLng(28.6139, 77.2090) // Default: Delhi
        googleMap.addMarker(MarkerOptions().position(india).title("LifeLink Map"))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(india, 12f))
    }
}
