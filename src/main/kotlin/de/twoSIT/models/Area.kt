package de.twoSIT.models

import kotlin.math.max
import kotlin.math.min

class Area(minLongitude: Double, minLatitude: Double, maxLongitude: Double, maxLatitude: Double) {
    val minLongitude: Double = min(minLongitude, maxLongitude)
    val minLatitude: Double = min(minLatitude, maxLatitude)
    val maxLongitude: Double = max(minLongitude, maxLongitude)
    val maxLatitude: Double = max(minLatitude, maxLatitude)
}