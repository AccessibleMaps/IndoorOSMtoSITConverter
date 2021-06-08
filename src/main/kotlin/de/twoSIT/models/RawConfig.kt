package de.twoSIT.models

import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import de.twoSIT.const.configFile
import de.twoSIT.util.getLogger
import kotlin.system.exitProcess

private val logger = getLogger(Config::class.java)

data class RawConfig(val areas: List<Area>, val username: String, val userId: String, val serverUrl: String, val changesetId: String, val maxWallWidthInM: Float, val maxLevelConnectionNodeOffsetInM: Float)

object Config {
    var areas: List<Area>
    var username: String
    var serverUrl: String
    var changesetId: String
    var userId: String
    var maxWallWidthInM: Float
    var maxLevelConnectionNodeOffsetInM: Float

    init {
        if (!configFile.exists()){
            logger.error("The config file ('${configFile.absolutePath}') does not exist")
            exitProcess(1)
        }
        try {
            val rawConfig = Klaxon().parse<RawConfig>(configFile)
            if (rawConfig == null){
                logger.error("No areas found in config file ('${configFile.absolutePath}')")
                exitProcess(1)
            }
            areas = rawConfig.areas
            username = rawConfig.username
            serverUrl = rawConfig.serverUrl
            changesetId = rawConfig.changesetId
            userId = rawConfig.userId
            maxWallWidthInM = rawConfig.maxWallWidthInM
            maxLevelConnectionNodeOffsetInM = rawConfig.maxLevelConnectionNodeOffsetInM
        } catch (e: KlaxonException){
            logger.error("${e::class.simpleName} occurred while parsing the config file ('${configFile.absolutePath}'):" +
                    "\n${e.stackTrace}")
            exitProcess(1)
        }
    }
}