package com.rishikesh.lifelink

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.rishikesh.lifelink.util.applyDynamicStatusBar

class LifeLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is ComponentActivity) {
                    // Default to dark icons on light background for the whole app
                    activity.applyDynamicStatusBar(isLightBackground = true)
                }
            }
            override fun onActivityStarted(activity: Activity) {
                if (activity is ComponentActivity) {
                    activity.applyDynamicStatusBar(isLightBackground = true)
                }
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
