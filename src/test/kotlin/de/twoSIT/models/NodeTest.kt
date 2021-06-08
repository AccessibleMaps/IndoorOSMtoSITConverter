package de.twoSIT.models

import org.junit.Test
import kotlin.math.roundToInt
import kotlin.test.assertTrue

class NodeTest {

    @Test
    fun testDistanceToSameCoordinates() {
        val node1 = IndoorObject("1", 10.020134, 10.2356, mutableSetOf(1f), mutableMapOf())
        val node2 = IndoorObject("2", 10.020134, 10.2356, mutableSetOf(1f), mutableMapOf())

        assertTrue { node1.distanceTo(node2) == 0.0f }
        assertTrue { node2.distanceTo(node1) == 0.0f }
    }

    @Test
    fun testDistanceToDifferentCoordinates() {
        val node1 = IndoorObject("1", 10.0, 10.0, mutableSetOf(1f), mutableMapOf())
        val node2 = IndoorObject("2", 0.0, 0.0, mutableSetOf(1f), mutableMapOf())

        assertTrue { node1.distanceTo(node2).roundToInt() == 1568521 }
        assertTrue { node2.distanceTo(node1).roundToInt() == 1568521 }
    }
}