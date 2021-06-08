package de.twoSIT.const

import de.twoSIT.models.Area
import java.io.File
import java.time.LocalDateTime

val configFile = File("config.json")

const val responseCacheDir = "response_cache"
const val exportDir = "export"
fun areaCacheFile(area: Area) : String{
    return "area_${area.minLatitude}_${area.minLongitude}_${area.maxLatitude}_${area.maxLongitude}.xml"
}
fun wayCacheFile(id: String) : String{
    return "way_${id}.xml"
}
fun nodeCacheFile(id: String) : String{
    return "node_${id}.xml"
}
fun relationCacheFile(id: String) : String{
    return "node_${id}.xml"
}

fun exportFile(): String {
    val date = LocalDateTime.now().toString().replace(":", "_")
    return "export_${date}.osc"
}

const val logDir = "logs"
const val defaultLogFile = "2sit.log"