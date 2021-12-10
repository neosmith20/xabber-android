package com.xabber.android.ui.helper.osm

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.annotation.ColorInt
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class CustomMyLocationOsmOverlay @JvmOverloads constructor(
    mapView: MapView,
    myLocationProvider: IMyLocationProvider = GpsMyLocationProvider(mapView.context),
    @ColorInt indicatorColor: Int = 0,
    pointer: Bitmap? = null
): MyLocationNewOverlay(myLocationProvider, mapView) {


    init {
        mCirclePaint.color = indicatorColor
        pointer?.let {
            setDirectionArrow(it, it)
        }
        setPersonHotspot(
            20.0f * mScale + 0.5f,
            20.0f * mScale + 0.5f
        )
    }
}