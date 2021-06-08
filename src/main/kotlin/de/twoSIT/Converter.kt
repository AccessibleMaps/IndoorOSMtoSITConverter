package de.twoSIT

import de.twoSIT.const.LEVEL_CONNECTION_NODE_PROXY_THRESHOLD
import de.twoSIT.models.*
import de.twoSIT.util.IdGenerator
import de.twoSIT.util.getLogger

private val logger = getLogger(Converter::class.java)

/*
approach:
    1. We iterate over all buildings
    2. Within a building iterate over the levels
    3. In current level fill a list of all needed nodes, rooms
    4. Add additional nodes where they are needed for merging
        a. iterate over rooms
        b. in current room iterate over all subsections
        c. for current subsection iterate over all nodes on floor
        d. check whether a projection on the current subsection exists, if so add it to current room nodes
    5. merge all nodes that are close to each other
        a.
 */
object Converter {
    private val nodesLevelMap = mutableMapOf<Float, MutableSet<IndoorObject>>().withDefault { mutableSetOf() }
    private val roomsLevelMap = mutableMapOf<Float, MutableSet<WayOwner>>().withDefault { mutableSetOf() }

    /**
     * Converts a given [Iterable] of [Building]s. This includes the merge of the [Room]s as well as the
     * [LevelConnection]s of each building
     *
     * @param buildings a [Iterable] of [Building]s to convert
     */
    fun convertBuildings(buildings: Iterable<Building>) {
        for (building in buildings) {
            logger.debug("Start to convert building '${building.id}'")
            convertBuilding(building)
        }
    }

    /**
     * Converts a given [Building]. This includes the merge of the [Room]s as well as the [LevelConnection]s
     *
     * @param building the [Building] to convert
     */
    fun convertBuilding(building: Building) {
        nodesLevelMap.clear()
        roomsLevelMap.clear()

        val allNodes = mutableSetOf<IndoorObject>()
        building.rooms.map { allNodes.addAll(it.nodes) }
        building.connections.map { allNodes.addAll(it.nodes) }
        for (node in allNodes) {
            for (level in node.levels){
                val tmp = nodesLevelMap.getValue(level)
                tmp.add(node)
                nodesLevelMap[level] = tmp
            }
        }

        for (room in building.rooms) {
            for (level in room.levels) {
                val tmp = roomsLevelMap.getValue(level)
                tmp.add(room)
                roomsLevelMap[level] = tmp
            }
        }

        for (connection in building.connections) {
            for (level in connection.levels) {
                val tmp = roomsLevelMap.getValue(level)
                tmp.add(connection)
                roomsLevelMap[level] = tmp
            }
        }

        logger.debug("Found a total of ${nodesLevelMap.size} levels and ${nodesLevelMap.map { it.value }.size} nodes")
        for (level in nodesLevelMap.keys) {
            logger.debug("Start to convert level '${level}' of building ${building.id}")
            convertLevel(level)
        }

        val connectionsToMerge = verticalConnectionsToMerge(building)
        building.connections.clear()
        building.connections.addAll(mergeConnections(connectionsToMerge))
    }

    private fun mergeConnections(connectionsToMerge: MutableSet<MutableSet<LevelConnection>>): MutableSet<LevelConnection> {
        val newConnections = mutableSetOf<LevelConnection>()
        connectionsToMerge.map { newConnections.add(mergeLevelConnections(it)) }
        return newConnections
    }

    /** replace all occurrences of each node in oldNodes by the newNode */
    private fun updateNodeRefs(oldNodes: Set<IndoorObject>, newNode: IndoorObject) {
        for ((level, rooms) in roomsLevelMap.entries) {
            if (level !in newNode.levels) {
                continue
            }
            for (room in rooms) {
                if (room is LevelConnection) {
                    continue
                }
                val toReplace = mutableListOf<IndoorObject>()
                for (node in room.nodes) {
                    if (node in oldNodes) {
                        toReplace.add(node)
                    }
                }
                for (node in toReplace) {
                    room.nodes[room.nodes.indexOf(node)] = newNode
                }
            }
        }
    }

    private fun mergeLevelConnections(connections: MutableSet<LevelConnection>): LevelConnection {

        // new nodes in the correct order
        val newNodes = mutableListOf<IndoorObject>()
        // a list of nodes that have been merged into a new node
        val oldNodes = mutableListOf<IndoorObject>()
        // find connection with maximum number of simple nodes
        val baseConnection = connections.maxBy { it.simpleNodes.size }
        for (node in baseConnection!!.simpleNodes) {
            val nodesNearby = mutableSetOf<IndoorObject>()
            for (connection in connections) {
                for (otherNode in connection.simpleNodes) {
                    if (node.inProximity(otherNode, LEVEL_CONNECTION_NODE_PROXY_THRESHOLD)) {
                        nodesNearby.add(otherNode)
                    }
                }
            }
            val resultingNode = IndoorObject.getMerged(nodesNearby)
            oldNodes.addAll(nodesNearby)
            newNodes.add(resultingNode)
            updateNodeRefs(nodesNearby, resultingNode)
        }
        // last node should be the same as the first to generate a closed way, can be simply replaced since no nearby
        // nodes can be found after search of nearby nodes and replacement of the first node in this way
        newNodes[newNodes.size-1] = newNodes[0]

        // go over all level connections and nodes, if nodes are found that aren't part of the new way
        // project it onto all wall sections of the new way and choose the closest match -> insert projected
        // point there
        for (conn in connections) {
            for (node in conn.nodes) {
                if (node in oldNodes) {
                    continue
                }

                var closestWallSection: WallSection? = null
                var projectedPoint: IndoorObject? = null
                var closestDistance: Float = Float.MAX_VALUE
                for (wallStartInd in 0 until newNodes.size - 1) {
                    val section = WallSection(newNodes[wallStartInd], newNodes[wallStartInd + 1])
                    val intersection = section.getIntersection(node) ?: continue
                    if (node.distanceTo(intersection.first) < closestDistance) {
                        closestWallSection = section
                        projectedPoint = intersection.first
                        closestDistance = node.distanceTo(intersection.first)
                    }
                }
                if (closestDistance < Float.MAX_VALUE) {
                    newNodes.add(newNodes.indexOf(closestWallSection!!.start), projectedPoint!!)
                }
            }
        }

        // merge tags
        val mergedTags = mutableMapOf<String, String>()
        var heightEst = false
        for (conn in connections) {
            if (!conn.additionalTags.containsKey("height")) heightEst = true
            mergedTags.putAll(conn.additionalTags)
        }
        if (heightEst && mergedTags.containsKey("height")) {
            mergedTags["est_height"] = mergedTags.remove("height")!!
        }

        // mergelevels
        val mergedLevels = mutableSetOf<Float>()
        connections.map { mergedLevels.addAll(it.levels) }

        var consentType: LevelConnectionType? = null
        var consentTag: IndoorTag? = null
        for (conn in connections) {
            if (consentType == null) {
                consentType = conn.levelConnectionType
            } else if (conn.levelConnectionType != consentType) {
                logger.warn("Merging LevelConnections with different LevelConnectionTypes: " +
                        "'${conn.levelConnectionType}':'${consentType}'. Going with '$consentType'")
            }

            if (consentTag == null) {
                consentTag = conn.indoorTag
            } else if (conn.indoorTag != consentTag) {
                logger.warn("Merging LevelConnections with different IndoorTags: " +
                        "'$consentTag':'${conn.indoorTag}'. Going with '$consentTag'")
            }
        }
        return LevelConnection(IdGenerator.getNewId(), mergedLevels, mutableSetOf(), newNodes, consentTag!!, consentType!!, mergedTags)
    }


    private fun verticalConnectionsToMerge(building: Building): MutableSet<MutableSet<LevelConnection>> {
        val connectionsToMerge = mutableSetOf<MutableSet<LevelConnection>>()

        for (connection in building.connections) {
            val connectionsNearby = building.connections.filter { connection.overlays(it) }

            // if a overlaying connection is already processed and has therefore already a set in nodesToMerge the sets
            // need to be merged
            val setsOfOverlayingConnections = mutableSetOf<MutableSet<LevelConnection>>()
            val connectionsNotListedYet = mutableSetOf<LevelConnection>()
            for (connectionNearby in connectionsNearby) {
                var setFound = false
                for (s in connectionsToMerge) {
                    if (connectionNearby in s) {
                        setsOfOverlayingConnections.add(s)
                        setFound = true
                        break
                    }
                }
                if (!setFound) {
                    connectionsNotListedYet.add(connectionNearby)
                }
            }
            for (s in setsOfOverlayingConnections) {
                connectionsToMerge.remove(s)
            }
            val unitedSet = mutableSetOf<LevelConnection>()
            setsOfOverlayingConnections.add(connectionsNotListedYet)
            setsOfOverlayingConnections.map { unitedSet.addAll(it) }
            unitedSet.add(connection)
            connectionsToMerge.add(unitedSet)
        }
        return connectionsToMerge
    }

    private fun convertLevel(level: Float) {
        val nodeCount = nodesLevelMap.getValue(level).count()
        insertNodes(level)
        mergeNodes(level)
        logger.debug("Merged ${nodesLevelMap.getValue(level).count() - nodeCount} nodes")
    }

    /**
     * This function splits a [SubSection] into two following [SubSection]s if a [Node] of any [Room] of the level has a
     * projection-[Node] on the [SubSection], which is close to the [Node] itself.
     *
     * @param roomsOnLevel a [MutableSet] of all [Room]s on the level
     */
    private fun insertNodes(level: Float) {
        val nodesOnLevel = nodesLevelMap.getValue(level)

        for (room in roomsLevelMap.getValue(level)) {
            val newNodes = mutableListOf<IndoorObject>()
            newNodes.add(room.nodes[0])
            for (currentNodeInd in 0 until room.nodes.size - 1) {
                val wallSection = WallSection(room.nodes[currentNodeInd], room.nodes[currentNodeInd + 1])

                val intersectionPairs = mutableListOf<Pair<IndoorObject, Double>>()
                for (node in nodesOnLevel) {
                    val intersection = wallSection.getIntersection(node) ?: continue
                    if (intersection.first.inProximity(node) && intersection.second > 0 && intersection.second < 1) {
                        intersectionPairs.add(intersection)
                    }
                }
                intersectionPairs.sortBy { it.second }
                val intersectionNodes = intersectionPairs.map { it.first }
                newNodes.addAll(intersectionNodes)
                newNodes.add(wallSection.end)
            }
            val tmp = nodesLevelMap.getValue(level)
            tmp.addAll(newNodes)
            nodesLevelMap[level] = tmp
            room.nodes.clear()
            room.nodes.addAll(newNodes)
        }
    }

    private fun mergeNodes(level: Float) {
        val nodesToMerge = nodesToMerge(level)
        logger.debug("Merging ${nodesToMerge.map { it.size }.sum()} into ${nodesToMerge.size} nodes")
        replaceNodes(nodesToMerge, level)
    }

    /**
     *
     * This function returns a set of sets, in which all nodes are contained, that are close to each other and therefore
     * should be merged. These sets are distinct, so a node in one set will not appear in another.
     * The proximity of the nodes is calculated transitive, meaning if
     *      1. node A is close to node B
     *      2. node B is close to node C
     *          --> A is close to C
     *
     * @param roomsOnLevel a [MutableSet] of all [Room]s on the level
     * @return a [MutableSet] of distinct sets which contain [Node]s that are in proximity of each other
     *
     * approach:
     * 1. create a set of sets of nodes (nodesToMerge)
     * 2. iterate over all nodes on level
     * 3. for current node (curr):
     * a. iterate again over all nodes on level (node1) -> if curr and node1 are close to each other, store node1 in a set (nodesNearby)
     * b. find all sets in nodesToMerge that contain a node of nodesNearby and store them in (setsOfNearbyNodes)
     * c. remove all sets of setsOfNearbyNodes from nodesToMerge
     * d. add nodes of nodesNearby, that are not contained in a set and that set to setsOfNearbyNodes
     * e. untie all sets of setsOfNearbyNodes and add the united set to nodesToMerge
     *
     */
    private fun nodesToMerge(level: Float): MutableSet<MutableSet<IndoorObject>> {
        val nodesToMerge = mutableSetOf<MutableSet<IndoorObject>>()
        val nodesOnLevel = nodesLevelMap.getValue(level)
        val nodesRoomMap = mutableMapOf<IndoorObject, MutableSet<WayOwner>>().withDefault { mutableSetOf() }
        for (room in roomsLevelMap.getValue(level)) {
            room.nodes.map { nodesRoomMap.getValue(it).add(room) }
        }

        for (node in nodesOnLevel) {
            val nodesNearby = mutableSetOf<IndoorObject>()
            val rooms = nodesRoomMap.getValue(node)
            loop@ for (node1 in nodesOnLevel) {
                if (node === node1 || node.id == node1.id) {
                    continue
                }
                for (room in rooms) {
                    if (node1 in room.nodes) continue@loop
                }

                if (node.inProximity(node1)) {
                    nodesNearby.add(node1)
                }
            }

            if (nodesNearby.isEmpty()) {
                continue
            }

            // if a nearby node is already processed and has therefore already a set in nodesToMerge the sets
            // need to be merged
            val setsOfNearbyNodes = mutableSetOf<MutableSet<IndoorObject>>()
            val nodesNotListedYet = mutableSetOf<IndoorObject>()
            for (nodeNearby in nodesNearby) {
                var setFound = false
                for (s in nodesToMerge) {
                    if (nodeNearby in s) {
                        setsOfNearbyNodes.add(s)
                        setFound = true
                        break
                    }
                }
                if (!setFound) {
                    nodesNotListedYet.add(nodeNearby)
                }
            }
            for (s in setsOfNearbyNodes) {
                nodesToMerge.remove(s)
            }
            val unitedSet = mutableSetOf<IndoorObject>()
            setsOfNearbyNodes.add(nodesNotListedYet)
            setsOfNearbyNodes.map { unitedSet.addAll(it) }
            unitedSet.add(node)
            nodesToMerge.add(unitedSet)
        }
        return nodesToMerge
    }

    /**
     * This function merges the nodes in [nodesToMerge] and updates the references of each subsection.
     * It furthermore removes SubSections of len 0 and updates the references in the according rooms.
     *
     * @param nodesToMerge a [MutableSet] of distinct sets that contain [Node]s that should be merged
     * @param roomsOnLevel a [MutableSet] of all [Room]s on the level
     *
     */
    private fun replaceNodes(nodesToMerge: MutableSet<MutableSet<IndoorObject>>, level: Float) {
        val oldToNewNodes = mutableMapOf<IndoorObject, IndoorObject>()
        for (nodes in nodesToMerge) {
            val merged = IndoorObject.getMerged(nodes)
            nodes.map { oldToNewNodes[it] = merged }
        }

        for (room in roomsLevelMap.getValue(level)) {

            val toDeleteNodes = mutableListOf<IndoorObject>()
            for (nodeIdx in 0 until room.nodes.size) {
                val node = room.nodes[nodeIdx]
                if (oldToNewNodes.containsKey(node)) {
                    room.nodes[nodeIdx] = oldToNewNodes[node]!!
                }
            }

            // remove duplicates, except the last node which should be a duplicate of the first node
            var nodeIdx = 0
            while (nodeIdx < room.nodes.size - 1) {
                val node = room.nodes[nodeIdx]
                val nextNode = room.nodes[nodeIdx + 1]
                if (node === nextNode) {
                    room.nodes.removeAt(nodeIdx + 1)
                }
                nodeIdx += 1
            }
        }
    }

}