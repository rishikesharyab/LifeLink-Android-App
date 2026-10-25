package com.rishikesh.lifelink

import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView

fun EditText.attachPasswordToggle(toggle: ImageView) {
    var visible = false
    toggle.setOnClickListener {
        visible = !visible
        transformationMethod = if (visible) HideReturnsTransformationMethod.getInstance()
        else PasswordTransformationMethod.getInstance()
        setSelection(text.length)
        toggle.setImageResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye)
    }
}