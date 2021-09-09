package com.xabber.android.ui.helper

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.util.TypedValue
import android.view.Display
import android.view.Surface
import android.view.inputmethod.InputMethodManager
import androidx.annotation.ColorInt

fun Activity.tryToHideKeyboardIfNeed() {
    this.currentFocus?.let { focusedView ->
        (this.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(
                focusedView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
    }
}

@ColorInt
fun @receiver:androidx.annotation.AttrRes Int.getAttrColor(context: Context?): Int {
    if (context == null) return 0
    val value = TypedValue()
    context.theme.resolveAttribute(this, value, true)
    return value.data
}

fun Activity.lockScreenRotation(isLock: Boolean) {
    this.requestedOrientation =
        if (isLock) {
            val display: Display = this.windowManager.defaultDisplay
            val rotation = display.rotation
            val size = Point()
            display.getSize(size)
            if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                if (size.x > size.y) {
                    //rotation is 0 or 180 deg, and the size of x is greater than y,
                    //so we have a tablet
                    if (rotation == Surface.ROTATION_0) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                } else {
                    //rotation is 0 or 180 deg, and the size of y is greater than x,
                    //so we have a phone
                    if (rotation == Surface.ROTATION_0) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    }
                }
            } else {
                if (size.x > size.y) {
                    //rotation is 90 or 270, and the size of x is greater than y,
                    //so we have a phone
                    if (rotation == Surface.ROTATION_90) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                } else {
                    //rotation is 90 or 270, and the size of y is greater than x,
                    //so we have a tablet
                    if (rotation == Surface.ROTATION_90) {
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
}

fun spToPxFloat(sp: Float, context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        sp,
        context.resources.displayMetrics
    )
}

fun dipToPx(dip: Float, context: Context): Int {
    return dipToPxFloat(dip, context).toInt()
}

fun dipToPxFloat(dip: Float, context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dip,
        context.resources.displayMetrics
    )
}