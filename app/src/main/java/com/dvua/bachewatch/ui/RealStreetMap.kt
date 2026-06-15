package com.dvua.bachewatch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.dvua.bachewatch.data.BacheReport
import com.dvua.bachewatch.ui.theme.SafetyAmber
import com.dvua.bachewatch.ui.theme.SlateCard
import com.dvua.bachewatch.ui.theme.SlateDark
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

//Mapa con OSMdroid deidad
@SuppressLint("MissingPermission")
@Composable
fun RealStreetMap(
    reports: List<BacheReport>,
    selectedReport: BacheReport?,
    onSelectReport: (BacheReport) -> Unit,
    modifier: Modifier = Modifier,
    enableUserLocation: Boolean = false
) {
    val context = LocalContext.current

    var followUser by remember { mutableStateOf(false) }
    var rotateWithCompass by remember { mutableStateOf(true) }
    var heading by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 4.0
            maxZoomLevel = 21.0
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(19.4326, -99.1332))
        }
    }

    val myLocationOverlay = remember(mapView) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            setDrawAccuracyEnabled(true)
        }
    }

    var hasCenteredOnStartup by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(enableUserLocation) {
        if (enableUserLocation && !hasCenteredOnStartup) {
            hasCenteredOnStartup = true

            myLocationOverlay.enableMyLocation()

            myLocationOverlay.runOnFirstFix {
                mapView.post {
                    val currentPoint = myLocationOverlay.myLocation

                    if (currentPoint != null) {
                        mapView.controller.setZoom(18.0)
                        mapView.controller.animateTo(currentPoint)
                        mapView.invalidate()
                    }
                }
            }
        }
    }

    var lastFocusedReportId by remember {
        mutableStateOf<Int?>(null)
    }

    DisposableEffect(mapView) {
        mapView.onResume()

        onDispose {
            myLocationOverlay.disableFollowLocation()
            myLocationOverlay.disableMyLocation()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(enableUserLocation, followUser) {
        if (enableUserLocation) {
            myLocationOverlay.enableMyLocation()

            if (followUser) {
                myLocationOverlay.enableFollowLocation()
                myLocationOverlay.runOnFirstFix {
                    mapView.post {
                        myLocationOverlay.myLocation?.let { point ->
                            mapView.controller.setZoom(18.0)
                            mapView.controller.animateTo(point)
                        }
                    }
                }
            } else {
                myLocationOverlay.disableFollowLocation()
            }
        } else {
            followUser = false
            myLocationOverlay.disableFollowLocation()
            myLocationOverlay.disableMyLocation()
        }
    }

    DisposableEffect(rotateWithCompass) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                heading = (azimuth + 360f) % 360f

                if (rotateWithCompass) {
                    mapView.setMapOrientation(-heading)
                    mapView.invalidate()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotateWithCompass && rotationSensor != null) {
            sensorManager.registerListener(
                listener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        } else {
            mapView.setMapOrientation(0f)
            mapView.invalidate()
        }

        onDispose {
            sensorManager.unregisterListener(listener)

            if (!rotateWithCompass) {
                mapView.setMapOrientation(0f)
                mapView.invalidate()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDark)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.overlays.clear()

                if (enableUserLocation) {
                    view.overlays.add(myLocationOverlay)
                }

                reports.forEach { report ->
                    val marker = Marker(view).apply {
                        position = GeoPoint(report.latitude, report.longitude)
                        title = report.title
                        snippet = "${report.severity} - ${report.status}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        val markerColor = when {
                            report.id == selectedReport?.id -> android.graphics.Color.WHITE
                            report.status == "Reparado" -> android.graphics.Color.GREEN
                            report.severity == "Crítico" -> android.graphics.Color.RED
                            report.severity == "Moderado" -> android.graphics.Color.YELLOW
                            else -> android.graphics.Color.CYAN
                        }

                        ContextCompat.getDrawable(
                            context,
                            org.osmdroid.library.R.drawable.marker_default
                        )?.let { drawable ->
                            val wrappedIcon = DrawableCompat.wrap(drawable).mutate()
                            DrawableCompat.setTint(wrappedIcon, markerColor)
                            icon = wrappedIcon
                        }

                        setOnMarkerClickListener { clickedMarker, _ ->
                            onSelectReport(report)
                            clickedMarker.showInfoWindow()
                            true
                        }
                    }

                    view.overlays.add(marker)
                }

                selectedReport?.let { report ->
                    view.controller.animateTo(
                        GeoPoint(report.latitude, report.longitude)
                    )
                }

                view.invalidate()
            }
        )

        MapFloatingButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            selected = rotateWithCompass,
            onClick = {
                rotateWithCompass = !rotateWithCompass
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Explore,
                contentDescription = "Orientación del mapa",
                tint = if (rotateWithCompass) SafetyAmber else Color.White,
                modifier = Modifier.rotate(
                    if (rotateWithCompass) heading else 0f
                )
            )
        }

        MapFloatingButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 76.dp, end = 16.dp),
            selected = followUser,
            enabled = enableUserLocation,
            onClick = {
                if (enableUserLocation) {
                    followUser = true

                    myLocationOverlay.myLocation?.let { point ->
                        mapView.controller.setZoom(18.0)
                        mapView.controller.animateTo(point)
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "Centrar en mi ubicación",
                tint = if (enableUserLocation) {
                    SafetyAmber
                } else {
                    Color.White.copy(alpha = 0.35f)
                }
            )
        }

        if (rotateWithCompass) {
            Surface(
                color = SlateCard.copy(alpha = 0.88f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = SafetyAmber,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(heading)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "${heading.toInt()}°",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MapFloatingButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (selected) {
            SlateCard.copy(alpha = 0.96f)
        } else {
            SlateCard.copy(alpha = 0.82f)
        },
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier.size(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}