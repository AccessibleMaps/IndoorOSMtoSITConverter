package de.twoSIT

import de.twoSIT.models.*
import de.twoSIT.util.getLogger


private val logger = getLogger(Mapper::class.java)


object Mapper {
    private val allWays = mutableMapOf<String, Way>()
    private val allAreaWays = mutableMapOf<String, Way>()
    private val allAreaRelations = mutableMapOf<String, Relation>()
    private val allAreaNodes = mutableMapOf<String, Node>()

    private val buildings = mutableListOf<Building>()
    private val unparsedBuildings = mutableListOf<Relation>()

    fun parseArea(rawArea: RawArea): List<Building> {
        // clear and refill maps
        allAreaNodes.clear()
        allAreaWays.clear()
        allAreaRelations.clear()
        rawArea.nodes.map { allAreaNodes[it.id] = Node.fromRaw(it) }
        rawArea.ways.map { allAreaWays[it.id] = Way.fromRaw(it) }
        rawArea.relations.map { allAreaRelations[it.id] = Relation.fromRaw(it) }
        allWays.putAll(allAreaWays)

        // fetch missing stuff
        fillMissing()
        logger.debug("Parsing area with a total of ${allAreaNodes.size} nodes; ${allAreaWays.size} ways and " +
                "${allAreaRelations.size} relations")

        // parse it
        parseBuildingRel()

        logger.debug("Parsed area into ${buildings.size} buildings with a total of " +
                "${buildings.map { it.rooms }.size} ways/rooms; ${buildings.map { it.floors }.size} relations/floors and " +
                "${buildings.map { it.indoorObjects.size + it.rooms.map { it.toOsm().nodeReferences.size }.sum() }.sum()} nodes")
        if (unparsedBuildings.size > 0) {
            logger.warn("Could not parse ${unparsedBuildings.size} buildings")
        }
        return buildings
    }

    /**
     * Checks for unfetched but referenced and therefore necessary objects and fetches them from server.
     */
    private fun fillMissing() {
        val missingStuff = getMissingOSMElements()
        val requester = Requester.getInstance("https://api.openstreetmap.org/api/0.6/")

        val missingNodes = missingStuff.first
        val missingWays = missingStuff.second
        val missingRelations = missingStuff.third

        fun fetchNodes(missingNodes: List<String>) {
            if (missingNodes.isNotEmpty()) {
                logger.info("Fetching ${missingNodes.size} nodes")
                val nodeList = RawNode.multipleFromString(requester.requestNodes(missingNodes))
                for (rawNode in nodeList) allAreaNodes[rawNode.id] = Node.fromRaw(rawNode)
            }
        }
        if (missingWays.isNotEmpty()) {
            logger.debug("Fetching ${missingWays.size} ways")
            val wayList = RawWay.multipleFromString(requester.requestWays(missingWays))
            val missingNodes = mutableListOf<String>()
            for (rawWay in wayList) {
                for (nodeRef in rawWay.nds) {
                    if (!allAreaNodes.containsKey(nodeRef.ref)) missingNodes.add(nodeRef.ref)
                }
            }
            fetchNodes(missingNodes)
            for (rawWay in wayList) allAreaWays[rawWay.id] = Way.fromRaw(rawWay)
        }
        fetchNodes(missingNodes)
        if (missingRelations.isNotEmpty()) {
            logger.info("Fetching ${missingRelations.size} relations")
            val relationList = RawRelation.multipleFromString(requester.requestNodes(missingRelations))
            for (rawRelation in relationList) allAreaRelations[rawRelation.id] = Relation.fromRaw(rawRelation)
        }
    }

    /**
     * checks for all buildings if there are any unfetched but necessary objects.
     *
     * @return a [Triple] consisting of [MutableList] for unfetched [Node]s, [Way]s and [Relation]s
     */
    private fun getMissingOSMElements(): Triple<MutableList<String>, MutableList<String>, MutableList<String>> {
        val missingNodes = mutableListOf<String>()
        val missingWays = mutableListOf<String>()
        val missingRelations = mutableListOf<String>()

        val buildingRelations = getAllBuildingRel()
        for (buildingRelation in buildingRelations) {
            val missingStuff = getMissingStuffBuilding(buildingRelation)
            missingNodes.addAll(missingStuff.first)
            missingWays.addAll(missingStuff.second)
            missingRelations.addAll(missingStuff.third)
        }

        return Triple(missingNodes, missingWays, missingRelations)
    }

    /**
     * checks for a given building if there are any unfetched but necessary objects.
     *
     * @param buildingRelation a [Relation] that represents the building to check
     * @return a [Triple] consisting of [MutableList] for unfetched [Node]s, [Way]s and [Relation]s
     */
    private fun getMissingStuffBuilding(buildingRelation: Relation): Triple<MutableList<String>, MutableList<String>, MutableList<String>> {
        val missingNodes = mutableListOf<String>()
        val missingWays = mutableListOf<String>()
        val missingRelations = mutableListOf<String>()

        for (member in buildingRelation.relationMembers) {
            if (!allAreaRelations.containsKey(member.ref)) {
                missingRelations.add(member.ref)
            } else {
                val floorMissingStuff = getMissingStuffFloor(allAreaRelations[member.ref]!!)
                missingNodes.addAll(floorMissingStuff.first)
                missingWays.addAll(floorMissingStuff.second)
                missingRelations.addAll(floorMissingStuff.third)
            }
        }
        for (member in buildingRelation.nodeMembers) {
            if (!allAreaNodes.containsKey(member.ref)) missingNodes.add(member.ref)
        }
        for (member in buildingRelation.wayMembers) {
            if (!allAreaNodes.containsKey(member.ref)) missingWays.add(member.ref)
        }

        return Triple(missingNodes, missingWays, missingRelations)
    }

    /**
     * checks for a given floor if there are any unfetched but necessary objects.
     *
     * @param floorRelation a [Relation] that represents the floor to check
     * @return a [Triple] consisting of [MutableList] for unfetched [Node]s, [Way]s and [Relation]s
     */
    private fun getMissingStuffFloor(floorRelation: Relation): Triple<MutableList<String>, MutableList<String>, MutableList<String>> {
        val missingNodes = mutableListOf<String>()
        val missingWays = mutableListOf<String>()
        val missingRelations = mutableListOf<String>()

        for (member in floorRelation.nodeMembers) {
            if (!allAreaNodes.containsKey(member.ref)) missingNodes.add(member.ref)
        }
        for (member in floorRelation.wayMembers) {
            if (!allAreaWays.containsKey(member.ref)) missingWays.add(member.ref)
        }
        for (member in floorRelation.relationMembers) {
            if (!allAreaRelations.containsKey(member.ref)) missingWays.add(member.ref)
        }

        return Triple(missingNodes, missingWays, missingRelations)
    }

    /**
     * Extracts all [Relation]s that represent a building in the current [RawArea]
     *
     * @return a [MutableList] of [Relation]s that represent buildings
     */
    private fun getAllBuildingRel(): MutableList<Relation> {
        val buildingRelations = mutableListOf<Relation>()
        for (relation in allAreaRelations.values) {
            if (relation.additionalTags.containsKey("type") && relation.additionalTags["type"] == "building") {
                buildingRelations.add(relation)
            }
        }
        return buildingRelations
    }

    /**
     * Parses all [Relation]s that represent buildings into a [Building] POJO
     */
    private fun parseBuildingRel() {
        val buildingRelations = getAllBuildingRel()
        for (relation in buildingRelations) {
            val building = Building.fromOsm(relation, allAreaNodes, allAreaWays)

            if (building == null) {
                unparsedBuildings.add(relation)
                continue
            }

            for (member in relation.relationMembers) {
                parseFloor(allAreaRelations[member.ref]!!, building)
            }

            // make sure that we have a individual levelConnnection object on for each floor/level it connects
            // so that we have a clean horizontal merge in the first step
            val newConnections = mutableListOf<LevelConnection>()
            // first add connections that are directly referenced by a floor
            for (connection in building.connections) {
                for (referencedLevel in connection.levelReferences) {
                    val newNodes = connection.nodes.filter { referencedLevel in it.levels }
                    newNodes.map { it.levels = mutableSetOf(referencedLevel) }
                    val newLevelConn = LevelConnection(connection.id, mutableSetOf(referencedLevel), connection.levelReferences, connection.nodes, connection.indoorTag, connection.levelConnectionType, connection.additionalTags)
                    newConnections.add(newLevelConn)
                }
            }

            // in the second go check if some levels specified in verticalpassage:floorange dont have a connection in
            // newConnections yet, if so create one
            for (connection in building.connections) {
                levelAdd@ for (level in connection.levels) {
                    for (otherConnection in newConnections) {
                        if (otherConnection.levels == mutableSetOf(level) && connection.overlays(otherConnection)) {
                            continue@levelAdd
                        }
                    }
                    val newNodes = connection.nodes.filter { level in it.levels }
                    newNodes.map { it.levels = mutableSetOf(level) }

                    val newLevelConn = LevelConnection(connection.id, mutableSetOf(level), connection.levelReferences, connection.nodes, connection.indoorTag, connection.levelConnectionType, connection.additionalTags)
                    newConnections.add(newLevelConn)
                }
            }
            building.connections.clear()
            building.connections.addAll(newConnections)

            buildings.add(building)
        }

    }

    /**
     * Parses a [Relation] that represents a floor into a [Floor] and adds it to the [building]
     *
     * @param relation the [Relation] that represents the floor
     * @param building the [Building] to add the [Floor] to
     */
    private fun parseFloor(relation: Relation, building: Building) {
        val floor = Floor.fromOsm(relation, allAreaNodes, allAreaWays) ?: return

        // write indoorObj into building
        for (member in relation.nodeMembers) {
            parseIndoorObject(allAreaNodes[member.ref]!!, mutableSetOf(floor.level), building)
        }

        // write rooms to building
        for (member in relation.wayMembers) {
            val way = allAreaWays[member.ref]!!
            parseRoom(way, mutableSetOf(floor.level), building)
        }

        // check for stand alone doors and windows to flag
        for (indObj in building.indoorObjects){
            var isStandalone = true
            for (room in building.rooms.filter { floor.level in it.levels }){
                if (indObj.id in room.nodes.map { it.id }){
                    isStandalone = false
                    break
                }
            }
            if (isStandalone){
                when {
                    indObj.additionalTags.containsKey("door") -> {
                        logger.warn("found standalone door: ${indObj.id}")
                    }
                    indObj.additionalTags.containsKey("window") -> {
                        logger.warn("found standalone window: ${indObj.id}")
                    }
                }
            }
        }

        for (member in relation.relationMembers) {
            val rel = allAreaRelations[member.ref]!!
            for (relationMember in rel.wayMembers) {
                when (relationMember.role) {
                    "outer" -> building.outline = allAreaWays[relationMember.ref]!!
                    "inner" -> building.innerline = allAreaWays[relationMember.ref]!!
                }
            }
        }
        building.floors.add(floor)
    }

    /**
     * Parses a [Node] that represents a basic indoor object into a [IndoorObject] and adds it to the [building]
     *
     * @param node the [Node] that represents the indoor object
     * @param levels the level the indoor object is on
     * @param building the [Building] to add the [IndoorObject] to
     */
    private fun parseIndoorObject(node: Node, levels: MutableSet<Float>, building: Building) {
        val indoorObject = IndoorObject.fromOsm(node, levels) ?: return
        building.indoorObjects.add(indoorObject)
    }

    /**
     * Parses a [Way] that represents a level connection into a [LevelConnection] and adds it to the [building]
     *
     * @param way the [Way] that represents the level connection
     * @param building the [Building] to add the [LevelConnection] to
     */
    private fun parseLevelConnections(way: Way, building: Building, level: MutableSet<Float>) {
        val levelConnection = LevelConnection.fromOsm(way, allAreaNodes, level) ?: return
        building.connections.add(levelConnection)
    }

    /**
     * Parses a [Way] that represents a room into a [Room] POJO and adds it to the [building]
     *
     * @param way the [Way] that represents the room
     * @param level the level the room is in
     * @param building the [Building] to add the [Floor] to
     */
    private fun parseRoom(way: Way, level: MutableSet<Float>, building: Building) {
        for ((key, value) in way.additionalTags.entries) {
            if (key == "buildingpart" && value == "verticalpassage") {
                parseLevelConnections(way, building, level)
                return
            }
        }

        val room = Room.fromOsm(way, level, allAreaNodes) ?: return
        building.rooms.add(room)
    }

    fun getContainedElements(relation: Relation): Triple<List<Node>, List<Way>, List<Relation>> {
        /** get a list of all (recursively) referenced OSM Elements (Way, Node, Relation), including this */
        val containedNodes = mutableListOf<Node>()
        val containedWays = mutableListOf<Way>()
        val containedRelations = mutableListOf<Relation>()

        for (node in relation.nodeMembers) {
            if (allAreaNodes.containsKey(node.ref)) containedNodes.add(allAreaNodes[node.ref]!!)
        }

        for (way in relation.wayMembers) {
            if (allAreaWays.containsKey(way.ref)) {
                val currWay = allAreaWays[way.ref]!!
                containedNodes.addAll(getContainedElements(currWay))
                containedWays.add(currWay)
            }
        }

        for (innerRelation in relation.relationMembers) {
            if (allAreaRelations.containsKey(innerRelation.ref)) {
                val contained = getContainedElements(allAreaRelations[innerRelation.ref]!!)
                containedNodes.addAll(contained.first)
                containedWays.addAll(contained.second)
                containedRelations.addAll(contained.third)
                containedRelations.add(allAreaRelations[innerRelation.ref]!!)
            }
        }

        return Triple(containedNodes, containedWays, containedRelations)
    }

    fun getContainedElements(way: Way): List<Node> {
        return way.nodeReferences.map { allAreaNodes[it.ref]!! }
    }

    fun exportBuildings() {
        val osmChange = OsmChange()
        val modify = Modify()
        val create = Create()
        val delete = Delete()

        // Todo implement diff calculation
        val oldEntrances = mutableMapOf<String, IndoorObject>()
        for (building in buildings) {
            oldEntrances.putAll(building.entrances)
            logger.info("Building ${building.id} backparsing result")

            val resultingOsmElements = building.getContainedElements()
            val nodes = resultingOsmElements.first
            val ways = resultingOsmElements.second
            val relations = resultingOsmElements.third.toMutableList()
            relations.add(building.toOsm())

            // populate create elements
            val originalNodeIds = building.originalNodes.map { it.id }.toSet()
            val originalWayIds = building.originalWays.map { it.id }.toSet()
            val originalRelationIds = building.originalRelations.map { it.id }.toSet()

            for (node in nodes) {
                node.updateCommonTags()
                if (node.id !in originalNodeIds) create.nodes.add(node.toRaw())
            }
            for (way in ways) {
                way.updateCommonTags()
                if (way.id !in originalWayIds) create.ways.add(way.toRaw())
            }
            for (relation in relations) {
                relation.updateCommonTags()
                if (relation.id !in originalRelationIds) create.relations.add(relation.toRaw())
            }
            osmChange.create = create
            logger.info("\t${create.nodes.size} nodes, ${create.ways.size} ways, ${create.relations.size} relations that got created")

            // populate delete elements
            val newNodeIds = nodes.map { it.id }.toSet()
            val newWayIds = ways.map { it.id }.toSet()
            val newRelationIds = relations.map { it.id }.toSet()

            for (node in building.originalNodes) {
                if (node.id !in newNodeIds) { delete.nodes.add(node.toRaw()) }
            }
            for (way in building.originalWays) {
                if (way.id !in newWayIds) delete.ways.add(way.toRaw())
            }
            for (relation in building.originalRelations) {
                if (relation.id !in newRelationIds) delete.relations.add(relation.toRaw())
            }
            // FixMe this stops josm from showing ways to us - do we delete to much?
            osmChange.delete = delete
            logger.info("\t${delete.nodes.size} nodes, ${delete.ways.size} ways, ${delete.relations.size} relations that got deleted")

            // populate modify elements
            // currently we assume everything inside a building that's not created or deleted was modified
            // ToDo for commiting a new changeset metadata as changeset, version, ... needs to be updated
            for (node in nodes) {
                if (node.id in originalNodeIds) {
                    node.updateCommonTags()
                    modify.nodes.add(node.toRaw())
                }
            }
            for (way in ways) {
                if (way.id in originalWayIds) {
                    way.updateCommonTags()
                    modify.ways.add(way.toRaw())
                }
            }
            for (relation in relations) {
                if (relation.id in originalRelationIds) {
                    relation.updateCommonTags()
                    modify.relations.add(relation.toRaw())
                }
            }
            osmChange.modify = modify
            logger.info("\t${modify.nodes.size} nodes, ${modify.ways.size} ways, ${modify.relations.size} relations that got modified")
        }

        for (way in allWays.values){
            for (ref in way.nodeReferences.map { it.ref }){
                if (ref in oldEntrances){
                    way.nodeReferences.removeIf { nodeReference -> nodeReference.ref == ref }
                    way.nodeReferences.add(NodeReference(oldEntrances[ref]!!.id))
                    osmChange.modify.ways.add(way.toRaw())
                }
            }
        }

        osmChange.createExportFile()
    }
}