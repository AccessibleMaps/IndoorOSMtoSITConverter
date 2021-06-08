package de.twoSIT.models

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader


abstract class RawAbstractElement {

    companion object {
        @JvmStatic
        protected fun fromString(rawXmlString: String, cls: Class<out RawAbstractElement>): RawAbstractElement {
            // todo add some fault tolerance here too
            val f = XMLInputFactory.newFactory()
            val sr: XMLStreamReader = f.createXMLStreamReader(StringReader(rawXmlString))
            sr.next()
            sr.next()

            val xmlMapper = XmlMapper()
            xmlMapper.setDefaultUseWrapper(false)
            xmlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

            return xmlMapper.readValue(sr, cls)
        }

        @JvmStatic
        protected fun multipleFromString(rawXmlString: String, cls: Class<out RawAbstractElement>): MutableList<RawAbstractElement> {
            val resultList = mutableListOf<RawAbstractElement>()

            val xmlMapper = XmlMapper()
            xmlMapper.setDefaultUseWrapper(false)
            xmlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            val f = XMLInputFactory.newFactory()
            val sr: XMLStreamReader = f.createXMLStreamReader(StringReader(rawXmlString))
            sr.next()

            try {
                while (sr.hasNext()) {
                    sr.next()
                    resultList.add(xmlMapper.readValue(sr, cls))
                }
            } catch (e: NoSuchElementException) {
                // stupid fucking xml parsing shit fuuuuu
            }

            return resultList
        }
    }

    @JacksonXmlProperty(isAttribute = true)
    var id: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var visible: Boolean = false

    @JacksonXmlProperty(isAttribute = true)
    var version: Int = 0

    @JacksonXmlProperty(isAttribute = true)
    var changeset: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var timestamp: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var user: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var uid: String = "not available"

    @JacksonXmlProperty(localName = "tag")
    var tags: MutableList<Tag> = mutableListOf()
}


class RawMember {
    @JacksonXmlProperty(isAttribute = true)
    var type: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var ref: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var role: String = "not available"
}

class RawNodeReference {
    @JacksonXmlProperty(isAttribute = true)
    var ref: String = "not available"
}

class Tag {
    @JacksonXmlProperty(isAttribute = true)
    var k: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var v: String = "not available"
}


class RawRelation : RawAbstractElement() {
    companion object {
        @JvmStatic
        fun fromString(rawXmlString: String): RawRelation {
            return fromString(rawXmlString, RawRelation::class.java) as RawRelation
        }

        @JvmStatic
        fun multipleFromString(rawXmlString: String): List<RawRelation> {
            return multipleFromString(rawXmlString, RawRelation::class.java).filterIsInstance<RawRelation>()
        }
    }

    @JacksonXmlProperty(localName = "member")
    var members: MutableList<RawMember> = mutableListOf()
}

@JacksonXmlRootElement(localName = "way")
class RawWay : RawAbstractElement() {
    companion object {
        @JvmStatic
        fun fromString(rawXmlString: String): RawWay {
            return fromString(rawXmlString, RawWay::class.java) as RawWay
        }

        @JvmStatic
        fun multipleFromString(rawXmlString: String): List<RawWay> {
            return multipleFromString(rawXmlString, RawWay::class.java).filterIsInstance<RawWay>()
        }
    }

    @JacksonXmlProperty(localName = "nd")
    var nds: MutableList<RawNodeReference> = mutableListOf()
}

@JacksonXmlRootElement(localName = "node")
class RawNode : RawAbstractElement() {
    companion object {
        @JvmStatic
        fun fromString(rawXmlString: String): RawNode {
            return fromString(rawXmlString, RawNode::class.java) as RawNode
        }

        @JvmStatic
        fun multipleFromString(rawXmlString: String): List<RawNode> {
            return multipleFromString(rawXmlString, RawNode::class.java).filterIsInstance<RawNode>()
        }
    }

    @JacksonXmlProperty(isAttribute = true)
    var lat: Double = 0.0

    @JacksonXmlProperty(isAttribute = true)
    var lon: Double = 0.0
}

class BoundingBox {
    @JacksonXmlProperty(isAttribute = true)
    var minlat: Float = 0f

    @JacksonXmlProperty(isAttribute = true)
    var minlon: Float = 0f

    @JacksonXmlProperty(isAttribute = true)
    var maxlat: Float = 0f

    @JacksonXmlProperty(isAttribute = true)
    var maxlon: Float = 0f
}

@JacksonXmlRootElement(localName = "osm")
class RawArea {
    companion object {
        @JvmStatic
        fun fromString(rawXmlString: String): RawArea {
            // todo add some fault tolerance here too
            val xmlMapper = XmlMapper()
            xmlMapper.setDefaultUseWrapper(false)

            return xmlMapper.readValue(rawXmlString, RawArea::class.java)
        }
    }

    @JacksonXmlProperty(isAttribute = true)
    var version: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var generator: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var copyright: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var attribution: String = "not available"

    @JacksonXmlProperty(isAttribute = true)
    var license: String = "not available"

    @JacksonXmlProperty(localName = "bounds")
    var bounds: BoundingBox = BoundingBox()

    @JacksonXmlProperty(localName = "node")
    var nodes: MutableList<RawNode> = mutableListOf()

    @JacksonXmlProperty(localName = "way")
    var ways: MutableList<RawWay> = mutableListOf()

    @JacksonXmlProperty(localName = "relation")
    var relations: MutableList<RawRelation> = mutableListOf()
}
