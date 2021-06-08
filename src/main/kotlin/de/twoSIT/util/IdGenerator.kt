package de.twoSIT.util

object IdGenerator {
    var lastId = 0
    fun getNewId(): String {
        lastId -= 1
        return lastId.toString()
    }
}