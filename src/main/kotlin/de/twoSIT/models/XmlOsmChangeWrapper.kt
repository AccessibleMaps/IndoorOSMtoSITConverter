package de.twoSIT.models

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import de.twoSIT.const.exportDir
import de.twoSIT.const.exportFile
import java.io.File

@JacksonXmlRootElement(localName = "osmChange")
class OsmChange {

    @JacksonXmlProperty(isAttribute = true)
    val version = "0.6"

    @JacksonXmlProperty(isAttribute = true)
    val generator = "2SIT"

    @JacksonXmlProperty(localName = "modify")
    var modify = Modify()
    @JacksonXmlProperty(localName = "create")
    var create = Create()
    @JacksonXmlProperty(localName = "delete")
    var delete = Delete()

    fun toXMLString(): String {
        // todo add some fault tolerance here too
        val xmlMapper = XmlMapper()
        xmlMapper.setDefaultUseWrapper(false)
        return xmlMapper.writeValueAsString(this)
    }

    fun createExportFile() {

        val xmlStr = toXMLString()

        File(exportDir).mkdir()
        val file = File("$exportDir/${exportFile()}")
        file.printWriter().use { out ->
            val lines = xmlStr.split("\n")
            for (line in lines) {
                out.println(line)
            }
        }
    }
}

abstract class OperationType {
    @JacksonXmlProperty(localName = "node")
    var nodes = mutableListOf<RawNode>()

    @JacksonXmlProperty(localName = "way")
    var ways = mutableListOf<RawWay>()

    @JacksonXmlProperty(localName = "relation")
    var relations = mutableListOf<RawRelation>()
}

@JacksonXmlRootElement(localName = "modify")
class Modify: OperationType()

@JacksonXmlRootElement(localName = "create")
class Create: OperationType()

@JacksonXmlRootElement(localName = "delete")
class Delete: OperationType()
