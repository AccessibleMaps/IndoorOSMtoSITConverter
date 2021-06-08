package de.twoSIT.models

import Coordinate
import GeoDistance
import com.google.gson.Gson
import de.twoSIT.util.IdGenerator
import de.twoSIT.util.getLogger
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val logger = getLogger("OsmElement")

data class NodeReference(val ref: String)   {
    fun toRaw(): RawNodeReference {
        val rawRef = RawNodeReference()
        rawRef.ref = ref
        return rawRef
    }
}


data class Member(val ref: String, val role: String) {
    fun toRaw(type: String): RawMember {
        val rawMember = RawMember()
        rawMember.type = type
        rawMember.ref = ref
        rawMember.role = role
        return rawMember
    }
}

abstract class AbstractOsmElement(val id: String, val additionalTags: MutableMap<String, String>) {

    companion object {
        fun fromRaw(element: RawAbstractElement): AbstractOsmElement {
            throw NotImplementedError()
        }
    }

    fun updateCommonTags() {
        additionalTags["generated"] = "true"
        additionalTags["user"] = Config.username
        additionalTags["changeset"] = Config.changesetId
        additionalTags["uid"] = Config.userId
        additionalTags["timestamp"] = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now()).replace(" ", "T") + "Z"
    }

    abstract fun toRaw(): RawAbstractElement

    fun mapCommonTags(rawElement: RawAbstractElement) {
        additionalTags["visible"] = rawElement.visible.toString()
        additionalTags["version"] = rawElement.version.toString()
        additionalTags["changeset"] = rawElement.changeset
        additionalTags["timestamp"] = rawElement.timestamp
        additionalTags["user"] = rawElement.user
        additionalTags["uid"] = rawElement.uid
    }

    fun enrichWithCommonTags(element: RawAbstractElement) {
        element.id = id
        // ToDo confirm that defaults are usefull for new created elements, check if some of these need to be updated
        //      for modified elements
        element.visible = if (additionalTags.containsKey("visible")) additionalTags.remove("visible")!!.toBoolean() else true
        element.version = if (additionalTags.containsKey("version")) additionalTags.remove("version")!!.toInt() else 1
        element.changeset = if (additionalTags.containsKey("changeset")) additionalTags.remove("changeset")!! else "42"
        element.timestamp = if (additionalTags.containsKey("timestamp")) additionalTags.remove("timestamp")!! else ""
        element.user = if (additionalTags.containsKey("user")) additionalTags.remove("user")!! else "2SIT"
        element.uid = if (additionalTags.containsKey("uid")) additionalTags.remove("uid")!! else "-1"

        element.tags = mutableListOf()
        for (tag in additionalTags) {
            val newTag = Tag()
            newTag.k = tag.key
            newTag.v = tag.value
            element.tags.add(newTag)
        }
    }
}

open class Node(id: String, var latitude: Double, var longitude: Double, additionalTags: MutableMap<String, String>) :
        AbstractOsmElement(id, additionalTags) {

    companion object {
        fun fromRaw(rawNode: RawNode): Node {
            val additionalTags = mutableMapOf<String, String>()
            for (tag in rawNode.tags) {
                additionalTags[tag.k] = tag.v
            }
            val node = Node(rawNode.id, rawNode.lat, rawNode.lon, additionalTags)
            node.mapCommonTags(rawNode)
            return node
        }

    }


    override fun toRaw(): RawNode {
        val rawNode = RawNode()
        enrichWithCommonTags(rawNode)
        rawNode.lat = this.latitude
        rawNode.lon = this.longitude
        return rawNode
    }

    fun toNodeReference(): NodeReference {
        return NodeReference(id)
    }

    fun toMember(): Member {
        return Member(id, "")
    }

    fun deepCopy(): Node {
        val JSON = Gson().toJson(this)
        return Gson().fromJson(JSON, Node::class.java)
    }
}

open class Way(id: String, val nodeReferences: MutableList<NodeReference>, additionalTags: MutableMap<String, String>) : AbstractOsmElement(id, additionalTags) {

    companion object {
        fun fromRaw(rawWay: RawWay): Way {
            val additionalTags = mutableMapOf<String, String>()
            for (tag in rawWay.tags) {
                additionalTags[tag.k] = tag.v
            }

            val nodes = rawWay.nds.map { NodeReference(it.ref) }.toMutableList()

            val way = Way(rawWay.id, nodes, additionalTags)
            way.mapCommonTags(rawWay)
            return way
        }
    }

    override fun toRaw(): RawWay {
        val rawWay = RawWay()
        enrichWithCommonTags(rawWay)
        rawWay.nds = mutableListOf()
        for (nodeReference in nodeReferences) {
            rawWay.nds.add(nodeReference.toRaw())
        }
        return rawWay
    }

    fun deepCopy(): Way {
        val JSON = Gson().toJson(this)
        return Gson().fromJson(JSON, Way::class.java)
    }

    fun toMember(): Member {
        return Member(id, "")
    }
}

open class Relation(id: String, additionalTags: MutableMap<String, String>) :
        AbstractOsmElement(id, additionalTags) {
    var nodeMembers = mutableListOf<Member>()
    var wayMembers = mutableListOf<Member>()
    var relationMembers = mutableListOf<Member>()

    companion object {
        fun fromRaw(rawRelation: RawRelation): Relation {
            val additionalTags = mutableMapOf<String, String>()
            for (tag in rawRelation.tags) {
                additionalTags[tag.k] = tag.v
            }

            val relation = Relation(rawRelation.id, additionalTags)
            relation.mapCommonTags(rawRelation)

            for (rawMember in rawRelation.members) {
                val member = Member(rawMember.ref, rawMember.role)
                when (rawMember.type) {
                    "node" -> relation.nodeMembers.add(member)
                    "way" -> relation.wayMembers.add(member)
                    "relation" -> relation.relationMembers.add(member)
                    else -> logger.info("unexpected membertype found: ${rawMember.type}")
                }
            }

            return relation
        }
    }

    fun toMember(): Member {
        return Member(id, "")
    }


    override fun toRaw(): RawRelation {
        val rawRelation = RawRelation()
        enrichWithCommonTags(rawRelation)
        rawRelation.members = mutableListOf()
        for (member in nodeMembers) rawRelation.members.add(member.toRaw("node"))
        for (member in wayMembers) rawRelation.members.add(member.toRaw("way"))
        for (member in relationMembers) rawRelation.members.add(member.toRaw("relation"))

        return rawRelation
    }

    fun deepCopy(): Relation {
        val JSON = Gson().toJson(this)
        return Gson().fromJson(JSON, Relation::class.java)
    }
}

