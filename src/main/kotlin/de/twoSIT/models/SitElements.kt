package de.twoSIT.models

import Coordinate
import com.google.gson.Gson
import de.twoSIT.Mapper
import de.twoSIT.const.LEVEL_CONNECTION_NODE_PROXY_THRESHOLD
import de.twoSIT.const.NODE_PROXY_THRESHOLD
import de.twoSIT.const.SIMPLE_NODE_BLACKLIST
import de.twoSIT.util.IdGenerator
import de.twoSIT.util.getLogger
import kotlin.math.abs
import kotlin.math.pow


private val logger = getLogger("SitElements")


enum class LevelConnectionType {
    STAIRS, CONVEYOR, ELEVATOR
}

enum class IndoorTag {
    ROOM, AREA, WALL, CORRIDOR, LEVEL
}

fun heightToString(height: Float): String {
    if (height % 1 == 0f) {
        return height.toInt().toString()
    }
    return height.toString()
}

fun levelFromStr(levelStr: String): MutableSet<Float> {
    val levelStrings = levelStr.replace(",", ";").replace(" ", "").split(";")
    val levels = mutableSetOf<Float>()
    for (str in levelStrings) {
        if (str.toFloatOrNull() != null) {
            levels.add(str.toFloat())
        } else if (str.contains("to")) {
            val values = str.split("to").map { it.toInt() }
            levels.addAll(IntRange(values.min()!!, values.max()!!).toMutableList().map { it.toFloat() }.toSet())
        } else if (str.contains('-')) {
            logger.warn("can't parse level str: $str")
        }
    }
    return levels
}

fun levelToStr(level: MutableSet<Float>): String {
    val levels = level.toMutableList().sorted().toMutableList()

    var currentRangeStart = levels.removeAt(0)
    var resultString = ""
    for ((levelInd, level_) in levels.withIndex()) {
        val prevLevel = if (levelInd != 0) levels[levelInd - 1] else currentRangeStart
        if (abs(prevLevel - level_) > 1) {
            // new range
            if (currentRangeStart == prevLevel) {
                resultString += "${heightToString(currentRangeStart)};"
            } else {
                resultString += "${heightToString(currentRangeStart)}-${heightToString(prevLevel)};"
            }
            currentRangeStart = level_
        }
    }
    if (levels.isEmpty() || currentRangeStart == levels.last()) {
        resultString += "${heightToString(currentRangeStart)};"
    } else {
        resultString += "${heightToString(currentRangeStart)}-${heightToString(levels.last())};"
    }
    resultString = resultString.removeSuffix(";")
    return resultString
}

abstract class AbstractSitElement(val id: String, val additionalTags: MutableMap<String, String>) {
    abstract fun toOsm(): AbstractOsmElement

    abstract fun toRaw(): RawAbstractElement
}

interface WayOwner {
    val nodes: MutableList<IndoorObject>
}

class LevelConnection(id: String, val levels: MutableSet<Float>, val levelReferences: MutableSet<Float>, override val nodes: MutableList<IndoorObject>,
                      val indoorTag: IndoorTag, val levelConnectionType: LevelConnectionType,
                      additionalTags: MutableMap<String, String>) : AbstractSitElement(id, additionalTags), WayOwner {
    val simpleNodes: MutableList<IndoorObject>
        get() {
            val resultList = mutableListOf<IndoorObject>()
            for (node in nodes) {
                for (tag in node.additionalTags) {
                    if (tag.key !in SIMPLE_NODE_BLACKLIST) {
                        resultList.add(node)
                        break
                    }
                }
            }
            return resultList
        }

    companion object {
        fun fromRaw(element: RawWay, allNodes: MutableMap<String, Node>, levelReference: MutableSet<Float>): LevelConnection? {
            return fromOsm(Way.fromRaw(element), allNodes, levelReference)
        }

        fun fromOsm(element: Way, allNodes: MutableMap<String, Node>, levelReference: MutableSet<Float>): LevelConnection? {
            if (element.additionalTags.containsKey("buildingpart")) element.additionalTags.remove("buildingpart")

            if (!element.additionalTags.containsKey("buildingpart:verticalpassage")) {
                logger.warn("Could not parse Way ${element.id} to LevelConnection: No LevelConnectionType " +
                        "('buildingpart:verticalpassage')")
                return null
            }
            var connectionType: LevelConnectionType? = null
            when (element.additionalTags["buildingpart:verticalpassage"]) {
                "stairway" -> connectionType = LevelConnectionType.STAIRS
                "elevator" -> connectionType = LevelConnectionType.ELEVATOR
            }
            if (connectionType == null) {
                logger.warn("Could not parse Way ${element.id} to LevelConnection: Invalid LevelConnectionType " +
                        "'${element.additionalTags["buildingpart:verticalpassage"]}' ('buildingpart:verticalpassage')")
                return null
            }
            element.additionalTags.remove("buildingpart:verticalpassage")

            if (!element.additionalTags.containsKey("buildingpart:" +
                            "verticalpassage:floorrange")) {
                logger.warn("Could not parse Way ${element.id} to LevelConnection: No FloorRange " +
                        "('buildingpart:verticalpassage:floorrange')")
                return null
            }
            val levels = levelFromStr(element.additionalTags["buildingpart:verticalpassage:floorrange"]!!)

            if (levels.isEmpty()) {
                logger.warn("Could not parse Way ${element.id} to LevelConnection: No FloorRange recognized " +
                        "'${element.additionalTags["buildingpart:verticalpassage:floorrange"]}' " +
                        "('buildingpart:verticalpassage:floorrange')")
                return null
            }
            element.additionalTags.remove("buildingpart:verticalpassage:floorrange")

            val nodes = mutableListOf<IndoorObject>()
            for (nodeRef in element.nodeReferences) {
                val indoorObject = IndoorObject.fromOsm(allNodes[nodeRef.ref]!!, levels) ?: continue
                nodes.add(indoorObject)
            }

            val indoorTag: IndoorTag = if (element.additionalTags.containsKey("door") && element.additionalTags["door"] == "no")
                IndoorTag.AREA
            else
                IndoorTag.ROOM

            return LevelConnection(element.id, levels, levelReference, nodes, indoorTag, connectionType, element.additionalTags)
        }
    }

    fun overlays(other: LevelConnection): Boolean {
        for (node in simpleNodes) {
            var foundProxy = false
            for (otherNode in other.simpleNodes) {
                // displacement over multiple levels is higher than default threshold
                if (node.inProximity(otherNode, LEVEL_CONNECTION_NODE_PROXY_THRESHOLD)) {
                    foundProxy = true
                    break
                }
            }
            if (!foundProxy) {
                return false
            }
        }
        return true
    }

    override fun toOsm(): Way {
        val tags = mutableMapOf<String, String>()
        tags.putAll(additionalTags)

        when (levelConnectionType) {
            LevelConnectionType.CONVEYOR -> {
                tags["stairs"] = "yes"
                tags["conveying"] = "yes"
            }
            LevelConnectionType.ELEVATOR -> tags["highway"] = "elevator"
            LevelConnectionType.STAIRS -> tags["stairs"] = "yes"
        }

        when (indoorTag) {
            IndoorTag.ROOM -> tags["indoor"] = "room"
            IndoorTag.AREA -> tags["indoor"] = "area"
            IndoorTag.LEVEL -> tags["indoor"] = "level"
        }
        tags["level"] = levelToStr(levels)
        val nodeRefs = nodes.map { it.toOsm().toNodeReference() }.toMutableList()
        return Way(id, nodeRefs, tags)
    }

    override fun toRaw(): RawAbstractElement {
        return toOsm().toRaw()
    }

}


class Room(id: String, val levels: MutableSet<Float>, val indoorTag: IndoorTag, override val nodes: MutableList<IndoorObject>,
           additionalTags: MutableMap<String, String>) : AbstractSitElement(id, additionalTags), WayOwner {
    var height: Float? = null
    var name: String? = null
    var ref: String? = null

    companion object {
        fun fromRaw(element: RawWay, allNodes: MutableMap<String, Node>, level: String): Room? {
            val way = Way.fromRaw(element)
            return fromOsm(way, levelFromStr(level), allNodes)
        }

        fun fromOsm(element: Way, floorLevel: MutableSet<Float>, allNodes: MutableMap<String, Node>): Room? {
            var roomLevel = mutableSetOf<Float>()
            var height: Float? = null
            var name: String? = null
            var ref: String? = null
            var indoorTag: IndoorTag? = null
            val additionalTags = mutableMapOf<String, String>()

            for ((key, value) in element.additionalTags.entries) {
                when (key) {
                    "level" -> roomLevel = levelFromStr(value)
                    "height" -> height = value.toFloatOrNull()
                    "name" -> name = value
                    "ref" -> ref = value
                    "buildingpart" -> {
                        when (value) {
                            "corridor" -> indoorTag = IndoorTag.CORRIDOR
                            "room" -> indoorTag = IndoorTag.ROOM
                            "hall" -> indoorTag = IndoorTag.AREA
                            "shell" -> indoorTag = IndoorTag.LEVEL
                            else -> logger.info("Unrecognized IndoorTag '${value}' in Way ${element.id}")
                        }
                    }
                    else -> additionalTags[key] = value
                }
            }

            // ToDo is this needed?
            if (indoorTag == null) {
                logger.warn("Could not parse Way ${element.id} to room: No IndoorTag'. Defaulting to corridor")
                indoorTag = IndoorTag.CORRIDOR
            }

            val nodes = element.nodeReferences.map { allNodes[it.ref]!! }
            val indoorObjects = mutableListOf<IndoorObject>()
            for (node in nodes) {
                val obj = IndoorObject.fromOsm(node, floorLevel)
                if (obj == null) {
                    logger.warn("Could not parse way ${element.id} to Room: Parse error on Node '${node.id}'")
                    return null
                }
                indoorObjects.add(obj)
            }
            if (nodes[0].id != nodes[nodes.size - 1].id) {
                logger.warn("Could not parse Way ${element.id} to room: Different beginning and end node'")
                return null
            }

            if (roomLevel.size == 0) {
                roomLevel = floorLevel
            }
            val room = Room(element.id, roomLevel, indoorTag, indoorObjects, additionalTags)
            room.height = height
            room.name = name
            room.ref = ref

            return room
        }
    }

    override fun toOsm(): Way {
        val way = Way(id, nodes.map { it.toOsm().toNodeReference() }.toMutableList(), additionalTags)
        way.additionalTags["level"] = levelToStr(levels)
        way.additionalTags["indoor"] = indoorTag.toString().toLowerCase()
        if (height != null) way.additionalTags["height"] = heightToString(height!!)
        if (name != null) way.additionalTags["name"] = name!!
        if (ref != null) way.additionalTags["ref"] = ref!!
        return way
    }

    override fun toRaw(): RawWay {
        return toOsm().toRaw()
    }
}


class Floor(id: String, val level: Float, additionalTags: MutableMap<String, String>) :
        AbstractSitElement(id, additionalTags) {
    var height: Float? = null
    var ref: String? = null
    var name: String? = null
    var shell: Room? = null

    // Todo should be part of building root relation
    val usages = mutableMapOf<String, Way>()

    companion object {
        fun fromRaw(element: RawRelation, allNodes: MutableMap<String, Node>, allWays: MutableMap<String, Way>): Floor? {
            return fromOsm(Relation.fromRaw(element), allNodes, allWays)
        }

        fun fromOsm(element: Relation, allNodes: MutableMap<String, Node>, allWays: MutableMap<String, Way>): Floor? {
            var level = listOf<Float>()
            var height: Float? = null
            var name: String? = null
            val ref: String? = null
            val additionalTags = mutableMapOf<String, String>()

            for ((tag, value) in element.additionalTags.entries) {
                when (tag) {
                    "level" -> level = levelFromStr(value).toList()
                    "height" -> height = value.toFloatOrNull()
                    "name" -> name = value
                    else -> additionalTags[tag] = value
                }
            }
            if (level.size != 1) {
                logger.warn("Could not parse Relation ${element.id} to Floor: Should have only one level but has ${level.size}'")
                return null
            }
            val floor = Floor(element.id, level[0], additionalTags)
            floor.height = height
            floor.name = name
            floor.ref = ref

            for (member in element.wayMembers) {
                val way = allWays[member.ref]!!
                if ("level:usage" in way.additionalTags.keys)
                    floor.usages[way.additionalTags["level:usage"]!!] = way
                if (member.role == "shell") {
                    allWays[member.ref]!!.additionalTags["buildingpart"] = "shell"
                    floor.shell = Room.fromOsm(allWays[member.ref]!!, mutableSetOf(level[0]), allNodes)
                }
            }

            for (member in element.nodeMembers) {
                if (member.role == "entrance") {
                    allNodes[member.ref]!!.additionalTags["entrance"] = member.role
                }
            }

            return floor
        }
    }

    override fun toRaw(): RawRelation {
        return toOsm().toRaw()
    }

    override fun toOsm(): Relation {
        val relation = Relation(id, additionalTags)
        relation.additionalTags["level"] = level.toString()

        if (height != null) relation.additionalTags["height"] = heightToString(height!!)
        if (ref != null) relation.additionalTags["ref"] = ref!!
        if (name != null) relation.additionalTags["name"] = name!!

        return relation
    }
}

class IndoorObject(id: String, val latitude: Double, val longitude: Double, var levels: MutableSet<Float>, additionalTags: MutableMap<String, String>) :
        AbstractSitElement(id, additionalTags) {

    companion object {
        fun fromRaw(element: RawNode, levels: MutableSet<Float>): IndoorObject? {
            return fromOsm(Node.fromRaw(element), levels)
        }

        fun fromOsm(element: Node, levels: MutableSet<Float>): IndoorObject? {
            if (element.additionalTags.containsKey("door"))
                print(element)
            return IndoorObject(element.id, element.latitude, element.longitude, levels, element.additionalTags)
        }

        fun getMerged(indoorObjects: Iterable<IndoorObject>, threshold: Float = NODE_PROXY_THRESHOLD): IndoorObject {
            val indoorObjectsAsList = indoorObjects.toList()

            if (indoorObjectsAsList.isEmpty()) {
                logger.info("Can't merge list of size 0")
            }
            for (i in 0 until indoorObjectsAsList.size - 1) {
                val node1 = indoorObjectsAsList[i]

                for (j in i + 1 until indoorObjectsAsList.size) {
                    val node2 = indoorObjectsAsList[j]
                    if (node1.distanceTo(node2) > threshold * 3) {
                        logger.warn("merging nodes ${node1.id}, ${node2.id} that are not in proximity! Distance: ${node1.distanceTo(node2)}")
                    }
                }
            }
            // ToDo tags contained in multiple nodes are overwritten - do we want that?
            val additionalTags = mutableMapOf<String, String>()
            indoorObjects.map { additionalTags.putAll(it.additionalTags) }

            val latitude = indoorObjects.map { it.latitude }.average()
            val longitude = indoorObjects.map { it.longitude }.average()

            val levels = mutableSetOf<Float>()
            var id: String? = null
            for (indoorObj in indoorObjects) {
                levels.addAll(indoorObj.levels)
                if (indoorObj.id[0] != '-') {
                    id = indoorObj.id
                }
            }
            return IndoorObject(id
                    ?: IdGenerator.getNewId(), latitude, longitude, levels.toMutableSet(), additionalTags)
        }

    }

    override fun toRaw(): RawNode {
        return toOsm().toRaw()
    }

    override fun toOsm(): Node {
        val node = Node(id, latitude, longitude, additionalTags)
        node.additionalTags["level"] = levelToStr(levels)
        return node
    }

    fun getMerged(other: IndoorObject): IndoorObject {
        return getMerged(setOf(this, other))
    }

    fun inProximity(other: IndoorObject, threshold: Float = NODE_PROXY_THRESHOLD): Boolean {
        return distanceTo(other) < threshold
    }

    fun distanceTo(other: IndoorObject): Float {
        /**
         * @return the haversine distance to [other] in meters
         */
        return GeoDistance.haversineDistanceInM(Coordinate(latitude, longitude), Coordinate(other.latitude, other.longitude)).toFloat()
    }

    fun deepCopy(): IndoorObject {
        val JSON = Gson().toJson(this)
        return Gson().fromJson(JSON, IndoorObject::class.java)
    }
}

class Building(id: String, val minLevel: Int, val maxLevel: Int, additionalTags: MutableMap<String, String>,
               val originalNodes: List<Node>, val originalWays: List<Way>, val originalRelations: List<Relation>,
               val entrances: MutableMap<String, IndoorObject>) :
        AbstractSitElement(id, additionalTags) {
    var height: Float? = null
    var name: String? = null

    val indoorObjects = mutableListOf<IndoorObject>()
    val rooms = mutableListOf<Room>()
    var floors = mutableListOf<Floor>()
    val connections = mutableListOf<LevelConnection>()

    var outline: Way? = null
    var innerline: Way? = null
    var mainWay: Way? = null

    val nonExistingLevels: MutableList<Int> = mutableListOf()

    companion object {
        fun fromRaw(element: RawRelation, allNodes: MutableMap<String, Node>, allWays: MutableMap<String, Way>): Building? {
            return fromOsm(Relation.fromRaw(element), allNodes, allWays)
        }

        fun fromOsm(element: Relation, allNodes: MutableMap<String, Node>, allWays: MutableMap<String, Way>): Building? {
            val contained = Mapper.getContainedElements(element)
            val originalNodes = contained.first.map { it.deepCopy() }
            val originalWays = contained.second.map { it.deepCopy() }
            val originalRelations = contained.third.map { it.deepCopy() }

            var maxLevel: Int? = null
            var minLevel: Int? = null
            var name: String? = null
            var height: Float? = null
            val additionalTags = mutableMapOf<String, String>()

            for ((tag, value) in element.additionalTags.entries) {
                when (tag) {
                    "building:max_level" -> maxLevel = value.toIntOrNull()
                    "building:min_level" -> {
                        minLevel = value.toIntOrNull()
                        // keep tag since it's used not only by indoorOSM
                        additionalTags["building:min_level"] = value
                    }
                    "name" -> name = value
                    "height" -> height = value.toFloatOrNull()
                    else -> additionalTags[tag] = value
                }
            }
            var mainWay: Way? = null
            for (member in element.wayMembers) {
                if (mainWay == null) mainWay = allWays[member.ref]
                else logger.info("Multiple mainWays for building-relation ${element.id}")
            }

            val entrances = mutableMapOf<String, IndoorObject>()
            for (member in element.nodeMembers) {
                if (member.role == "entrance") {
                    // todo this is a door
                    // fixme this is always level 0
                    IndoorObject.fromOsm(allNodes[member.ref]!!, mutableSetOf(0f))?.let {
                        entrances[member.ref] = it
                    }
                }
            }

            if (maxLevel == null) {
                logger.warn("Could not parse Relation '${element.id}' to building: No maxLevel")
                return null
            }
            if (minLevel == null) {
                logger.warn("Could not parse Relation '${element.id}' to building: No minLevel")
                return null
            }

            val building = Building(element.id, minLevel, maxLevel, additionalTags, originalNodes, originalWays,
                    originalRelations, entrances)
            building.name = name
            building.height = height
            building.mainWay = mainWay

            return building
        }
    }

    override fun toRaw(): RawRelation {
        return toOsm().toRaw()
    }

    override fun toOsm(): Relation {
        val relation = Relation(id, additionalTags)
        relation.additionalTags["minLevel"] = minLevel.toString()
        relation.additionalTags["maxLevel"] = maxLevel.toString()
        if (height != null) relation.additionalTags["height"] = heightToString(height!!)
        if (name != null) relation.additionalTags["name"] = name!!

        val nodeMembers = indoorObjects.map { it.toOsm().toMember() }
        val wayMembers = rooms.map { it.toOsm().toMember() }.toMutableList()
        wayMembers.addAll(connections.map { it.toOsm().toMember() })
        for (floor in floors) {
            if (floor.shell != null) {
                wayMembers.add(floor.shell!!.toOsm().toMember())
            }
        }

        val relationMembers = floors.map { it.toOsm().toMember() }
        relation.nodeMembers = nodeMembers.toMutableList()
        relation.wayMembers = wayMembers.toMutableList()
        relation.relationMembers = relationMembers.toMutableList()

        // todo i dont think thats done. nonExisting levels, innerline, outerline etc

        return relation
    }

    fun getContainedElements(): Triple<List<Node>, List<Way>, List<Relation>> {
        val containedNodes = indoorObjects.map { it.toOsm() }.toMutableList()
        containedNodes.addAll(entrances.values.map { it.toOsm() })

        val containedWays = rooms.map { it.toOsm() }.toMutableList()
        containedWays.addAll(connections.map { it.toOsm() })
        connections.map { containedNodes.addAll(it.nodes.map { it.toOsm() }) }
        rooms.map { containedNodes.addAll(it.nodes.map { it.toOsm() }) }
        for (floor in floors) {
            if (floor.shell != null) {
                containedWays.add(floor.shell!!.toOsm())
            }
        }

        val containedRelations = floors.map { it.toOsm() }

        return Triple(containedNodes, containedWays, containedRelations)
    }
}

data class WallSection(var start: IndoorObject, var end: IndoorObject) {
    /**
     * https://stackoverflow.com/questions/10301001/perpendicular-on-a-line-segment-from-a-given-point
     * @param node the [Node] that should be projected
     * @return a [Pair] of the projection [Node] as first and the distance to the [SubSection].node1 as second. The distance is 0 if the projected node is node1 and 1 if it is node2
     * @return null if no projection is found within the [SubSection]
     *
     */
    fun getIntersection(node: IndoorObject): Pair<IndoorObject, Double>? {
        if (node.inProximity(start)) return Pair(start, 0.0)
        if (node.inProximity(end)) return Pair(end, 1.0)

        val t = ((node.latitude - start.latitude) * (end.latitude - start.latitude) + (node.longitude - start.longitude) * (end.longitude - start.longitude)) / ((end.latitude - start.latitude).pow(2) + (end.longitude - start.longitude).pow(2))
        if (t < 0 || t > 1) {
            return null
        }

        val latitude = start.latitude + t * (end.latitude - start.latitude)
        val longitude = start.longitude + t * (end.longitude - start.longitude)

        val additionalTags = mutableMapOf<String, String>()
        additionalTags.putAll(node.additionalTags)
        additionalTags.putAll(additionalTags)
        val projectionPoint = IndoorObject(IdGenerator.getNewId(), latitude, longitude, start.levels, additionalTags)

        return Pair(projectionPoint, t)
    }
}
