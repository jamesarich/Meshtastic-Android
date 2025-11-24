/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshtastic.feature.node.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.DeviceOrientation
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.LocationServices
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.node.metrics.MetricsViewModel
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.math.*

private const val HAPTIC_FEEDBACK_INTERVAL = 2.0f
private const val FULL_CIRCLE = 360f

enum class CardinalDirection(val label: String, val degrees: Float) {
    N("N", 0f), NE("NE", 45f), E("E", 90f), SE("SE", 135f),
    S("S", 180f), SW("SW", 225f), W("W", 270f), NW("NW", 315f);

    companion object {
        fun fromDegrees(degrees: Float): CardinalDirection {
            val normalized = ((degrees % FULL_CIRCLE) + FULL_CIRCLE) % FULL_CIRCLE
            return when {
                normalized < 22.5f || normalized >= 337.5f -> N
                normalized < 67.5f -> NE
                normalized < 112.5f -> E
                normalized < 157.5f -> SE
                normalized < 202.5f -> S
                normalized < 247.5f -> SW
                normalized < 292.5f -> W
                else -> NW
            }
        }
    }
}

data class Azimuth(val degrees: Float) {
    val normalizedDegrees: Float get() = ((degrees % FULL_CIRCLE) + FULL_CIRCLE) % FULL_CIRCLE
    val roundedDegrees: Int get() = normalizedDegrees.roundToInt() % 360
    val cardinalDirection: CardinalDirection get() = CardinalDirection.fromDegrees(normalizedDegrees)
    operator fun plus(other: Float): Azimuth = Azimuth(degrees + other)
    operator fun minus(other: Float): Azimuth = Azimuth(degrees - other)
}

private fun isAzimuthBetweenTwoPoints(azimuth: Azimuth, start: Azimuth, end: Azimuth): Boolean {
    val az = azimuth.normalizedDegrees
    val s = start.normalizedDegrees
    val e = end.normalizedDegrees
    return if (s <= e) az in s..e else az >= s || az <= e
}

private fun getClosestNumberFromInterval(value: Float, interval: Float): Float =
    (value / interval).roundToInt() * interval

@Composable
fun CompassScreen(
    metricsVM: MetricsViewModel,
    onNavigateUp: () -> Unit,
    waypointName: String,
    waypointLat: Double,
    waypointLon: Double,
    waypointColor: Color
) {
    val state by metricsVM.state.collectAsStateWithLifecycle()
    val nodeName = state.node?.user?.longName ?: ""
    
    Scaffold(
        topBar = {
            MainAppBar(
                title = nodeName,
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CompassView(
                modifier = Modifier.fillMaxWidth(),
                compassRoseColor = MaterialTheme.colorScheme.primary,
                tickColor = MaterialTheme.colorScheme.onSurface,
                cardinalColor = MaterialTheme.colorScheme.primary,
                degreeTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                northPointerColor = Color.Red,
                waypointName = waypointName,
                waypointLat = waypointLat,
                waypointLon = waypointLon,
                waypointColor = waypointColor
            )
        }
    }
}

@Composable
fun CompassView(
    modifier: Modifier = Modifier,
    compassRoseColor: Color = MaterialTheme.colorScheme.primary,
    tickColor: Color = MaterialTheme.colorScheme.onSurface,
    cardinalColor: Color = MaterialTheme.colorScheme.primary,
    degreeTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    northPointerColor: Color = Color.Red,
    waypointName: String,
    waypointLat: Double,
    waypointLon: Double,
    waypointColor: Color
) {
    val context = LocalContext.current
    val view = LocalView.current

    var rawAzimuth by remember { mutableFloatStateOf(0f) }
    var lastHapticFeedbackPoint by remember { mutableFloatStateOf(Float.NaN) }
    var deviceLat by remember { mutableStateOf<Double?>(null) }
    var deviceLon by remember { mutableStateOf<Double?>(null) }

    val fusedOrientationClient = remember { LocationServices.getFusedOrientationProviderClient(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    val orientationListener = remember {
        DeviceOrientationListener { orientation: DeviceOrientation ->
            rawAzimuth = orientation.headingDegrees
        }
    }

    DisposableEffect(Unit) {
        val request = DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT).build()
        fusedOrientationClient.requestOrientationUpdates(request, executor, orientationListener)

        fusedLocationClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    deviceLat = loc.latitude
                    deviceLon = loc.longitude
                }
            }

        onDispose {
            fusedOrientationClient.removeOrientationUpdates(orientationListener)
        }
    }

    val trueAzimuth = remember(rawAzimuth) { Azimuth(rawAzimuth) }

    LaunchedEffect(trueAzimuth.degrees) {
        val az = trueAzimuth.degrees
        if (lastHapticFeedbackPoint.isNaN()) lastHapticFeedbackPoint = getClosestNumberFromInterval(az, HAPTIC_FEEDBACK_INTERVAL)
        else {
            val lastPoint = Azimuth(lastHapticFeedbackPoint)
            val boundaryStart = lastPoint - HAPTIC_FEEDBACK_INTERVAL
            val boundaryEnd = lastPoint + HAPTIC_FEEDBACK_INTERVAL
            if (!isAzimuthBetweenTwoPoints(trueAzimuth, boundaryStart, boundaryEnd)) {
                lastHapticFeedbackPoint = getClosestNumberFromInterval(az, HAPTIC_FEEDBACK_INTERVAL)
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompassStatusDisplay(azimuth = trueAzimuth, modifier = Modifier.padding(bottom = 16.dp))

        Box(
            modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            CompassRose(
                rotation = -trueAzimuth.normalizedDegrees,
                compassRoseColor = compassRoseColor,
                tickColor = tickColor,
                cardinalColor = cardinalColor,
                degreeTextColor = degreeTextColor,
                northPointerColor = northPointerColor,
                modifier = Modifier.fillMaxSize(),
                deviceLat = deviceLat,
                deviceLon = deviceLon,
                waypointLat = waypointLat,
                waypointLon = waypointLon,
                waypointColor = waypointColor,
                azimuthDegrees = trueAzimuth.degrees
            )
            NorthIndicator(color = northPointerColor, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun CompassStatusDisplay(azimuth: Azimuth, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${azimuth.roundedDegrees}°", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(azimuth.cardinalDirection.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompassRose(
    rotation: Float,
    compassRoseColor: Color,
    tickColor: Color,
    cardinalColor: Color,
    degreeTextColor: Color,
    northPointerColor: Color,
    modifier: Modifier = Modifier,
    deviceLat: Double?,
    deviceLon: Double?,
    waypointLat: Double,
    waypointLon: Double,
    waypointColor: Color,
    azimuthDegrees: Float
) {
    val textMeasurer = rememberTextMeasurer()
    val cardinalStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    val degreeStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.9f

        // Draw outer and inner circles
        drawCircle(color = compassRoseColor, radius = radius, center = center, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = compassRoseColor.copy(alpha = 0.3f), radius = radius * 0.85f, center = center, style = Stroke(width = 1.dp.toPx()))

        // Draw ticks
        rotate(rotation, center) {
            for (degree in 0 until 360 step 5) {
                val isCardinal = degree % 90 == 0
                val isMajor = degree % 30 == 0
                val tickLength = when {
                    isCardinal -> radius * 0.15f
                    isMajor -> radius * 0.1f
                    else -> radius * 0.05f
                }
                val tickWidth = when {
                    isCardinal -> 3.dp.toPx()
                    isMajor -> 2.dp.toPx()
                    else -> 1.dp.toPx()
                }
                val angleRad = Math.toRadians(degree.toDouble() - 90)
                val outerX = center.x + (radius * cos(angleRad)).toFloat()
                val outerY = center.y + (radius * sin(angleRad)).toFloat()
                val innerX = center.x + ((radius - tickLength) * cos(angleRad)).toFloat()
                val innerY = center.y + ((radius - tickLength) * sin(angleRad)).toFloat()
                drawLine(color = if (degree == 0) northPointerColor else tickColor, start = Offset(innerX, innerY), end = Offset(outerX, outerY), strokeWidth = tickWidth, cap = StrokeCap.Round)
            }
            drawCompassRoseCenter(center, radius * 0.15f, compassRoseColor, northPointerColor)
        }

        // Draw degrees and cardinal letters upright
        val degreeRadius = radius * 0.68f
        val labelRotationDegrees = rotation
        val degreeList = listOf(0,30,60,90,120,150,180,210,240,270,300,330)
        for (degree in degreeList) {
            if (degree % 90 != 0) {
                val effectiveAngle = degree - 90 + labelRotationDegrees
                val angleRad = Math.toRadians(effectiveAngle.toDouble())
                val textX = center.x + (degreeRadius * cos(angleRad)).toFloat()
                val textY = center.y + (degreeRadius * sin(angleRad)).toFloat()
                val textLayoutResult = textMeasurer.measure(text = degree.toString(), style = degreeStyle.copy(color = degreeTextColor))
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(textX - textLayoutResult.size.width / 2f,
                        textY - textLayoutResult.size.height / 2f)
                )
            }
        }
        val cardinalRadius = radius * 0.55f
        val cardinals = listOf(Triple("N",0,northPointerColor), Triple("E",90,cardinalColor), Triple("S",180,cardinalColor), Triple("W",270,cardinalColor))
        for ((label, degree, color) in cardinals) {
            val effectiveAngle = degree - 90 + labelRotationDegrees
            val angleRad = Math.toRadians(effectiveAngle.toDouble())
            val textX = center.x + (cardinalRadius * cos(angleRad)).toFloat()
            val textY = center.y + (cardinalRadius * sin(angleRad)).toFloat()
            val textLayoutResult = textMeasurer.measure(text = label, style = cardinalStyle.copy(color = color))
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(textX - textLayoutResult.size.width / 2f,
                    textY - textLayoutResult.size.height / 2f)
            )
        }

        // Draw waypoint
        if (deviceLat != null && deviceLon != null) {
            val bearingToWaypoint = computeBearing(deviceLat, deviceLon, waypointLat, waypointLon)
            val waypointAngleRad = Math.toRadians(bearingToWaypoint.toDouble() - azimuthDegrees.toDouble() - 90)
            val waypointRadius = radius * 0.75f
            val wpX = center.x + (waypointRadius * cos(waypointAngleRad)).toFloat()
            val wpY = center.y + (waypointRadius * sin(waypointAngleRad)).toFloat()
            drawCircle(color = waypointColor, radius = 8.dp.toPx(), center = Offset(wpX, wpY))
        }
    }
}

// Compute distance in meters
private fun computeDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat/2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1-a))
    return R * c
}

// Compute bearing in degrees
private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLon = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLon)
    return ((Math.toDegrees(atan2(y,x)) + 360) % 360).toFloat()
}

private fun DrawScope.drawCompassRoseCenter(center: Offset, size: Float, color: Color, northColor: Color) {
    val path = Path()
    path.moveTo(center.x, center.y - size)
    path.lineTo(center.x + size * 0.3f, center.y)
    path.lineTo(center.x, center.y + size * 0.3f)
    path.lineTo(center.x - size * 0.3f, center.y)
    path.close()
    drawPath(path, color.copy(alpha = 0.5f))
    drawCircle(color = color, radius = size * 0.2f, center = center)
}

@Composable
private fun NorthIndicator(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.padding(top = 4.dp)) {
        val triangleSize = 16.dp.toPx()
        val path = Path().apply {
            moveTo(0f, triangleSize)
            lineTo(triangleSize / 2f, 0f)
            lineTo(triangleSize, triangleSize)
            close()
        }
        drawPath(path, color)
    }
}
