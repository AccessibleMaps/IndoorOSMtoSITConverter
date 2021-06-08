package de.twoSIT

import de.twoSIT.models.Area
import de.twoSIT.models.Building
import de.twoSIT.models.Config
import de.twoSIT.models.RawArea
import de.twoSIT.util.getLogger

private val logger = getLogger("main")

// bounding box examples for a buildings mapped as IndoorOSM and SIT
val indoorArea = Area(49.41689, 8.67180, 49.41969, 8.67695)
val indoorAreaOneBuilding = Area(49.4184885, 8.6765974, 49.4189534, 8.6772052)

val sitArea = Area(42.79609, -1.63938, 42.80234, -1.63280)

// server addresses to easy switch.
const val uniServer = "http://141.76.16.34:8084/api/0.6/"
const val officialServer = "https://api.openstreetmap.org/api/0.6/"


fun main(args: Array<String>) {
    logger.info("New run starts")
    val requester = Requester.getInstance(Config.serverUrl)

    val buildings = mutableSetOf<Building>()
    for (area in Config.areas) {
        val rawXmlString = requester.requestArea(area)
        val rawArea = RawArea.fromString(rawXmlString)

        buildings.addAll(Mapper.parseArea(rawArea))
    }

    val sitBuildings = Converter.convertBuildings(buildings)
    Mapper.exportBuildings()
}
