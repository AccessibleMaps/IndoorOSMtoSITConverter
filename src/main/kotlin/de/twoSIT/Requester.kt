package de.twoSIT

import de.twoSIT.const.*
import de.twoSIT.models.Area
import de.twoSIT.util.SingletonHolder
import de.twoSIT.util.getLogger
import org.apache.http.client.fluent.Request
import java.io.File

/**
 * Handles all the requests to a OSM server
 *
 * This is a Singleton
 *
 * @param baseUrl The url of the server to use. This is useful to quickly change the server or the api level.
 * Example: "https://api.openstreetmap.org/api/0.6/"
 */
class Requester private constructor(private val baseUrl: String) {
    companion object : SingletonHolder<Requester, String>(::Requester) {
        @JvmStatic
        private val logger = getLogger(Requester::class.java)
    }

    /**
     * Fetches a single relation based on the id
     *
     * @param id the id of the relation to fetch
     * @param useCache if set to true, it will persistently cache the response. If a cache file for this request exist,
     * it will be loaded, else it will fetch the request and save the file for later usage
     * @return XML-String containing the content
     */
    fun requestRelation(id: String, useCache: Boolean = true): String {
        if (useCache) {
            val dirName = responseCacheDir
            File(dirName).mkdir()
            val cached = File("$dirName/${relationCacheFile(id)}")

            if (cached.exists()) {
                logger.debug("Loaded cached relation $cached")
                return cached.readText()
            }

            val rawXml = requestRelation(id)
            cached.printWriter().use { out ->
                val lines = rawXml.split("\n")
                for (line in lines) {
                    out.println(line)
                }
            }
            logger.debug("Saved relation in $cached")
            return rawXml
        }
        return requestRelation(id)
    }

    private fun requestRelation(id: String): String {
        val url = "${baseUrl}node/$id"
        return Request.Get(url).execute().returnContent().asString()
    }

    fun requestRelations(ids: Iterable<String>): String {
        val idString = ids.joinToString(separator = ",") { it -> it }
        val url = "${baseUrl}relations?relations=$idString"
        return Request.Get(url).execute().returnContent().asString()
    }

    fun requestNode(id: String, useCache: Boolean = true): String {
        if (useCache) {
            val dirName = responseCacheDir
            File(dirName).mkdir()
            val cached = File("$dirName/${nodeCacheFile(id)}")

            if (cached.exists()) {
                logger.debug("Loaded cached node $cached")
                return cached.readText()
            }

            val rawXml = requestNode(id)
            cached.printWriter().use { out ->
                val lines = rawXml.split("\n")
                for (line in lines) {
                    out.println(line)
                }
            }
            logger.debug("Saved node in $cached")
            return rawXml
        }
        return requestNode(id)
    }

    private fun requestNode(id: String): String {
        val url = "${baseUrl}node/$id"
        return Request.Get(url).execute().returnContent().asString()
    }

    fun requestNodes(ids: Iterable<String>): String {
        val idString = ids.joinToString(separator = ",") { it -> it }
        val url = "${baseUrl}nodes?nodes=$idString"
        return Request.Get(url).execute().returnContent().asString()
    }

    fun requestWay(id: String, useCache: Boolean = true): String {
        if (useCache) {
            val dirName = responseCacheDir
            File(dirName).mkdir()
            val cached = File("$dirName/${wayCacheFile(id)}")

            if (cached.exists()) {
                logger.debug("Loaded cached way $cached")
                return cached.readText()
            }

            val rawXml = requestWay(id)
            cached.printWriter().use { out ->
                val lines = rawXml.split("\n")
                for (line in lines) {
                    out.println(line)
                }
            }
            logger.debug("Saved way in $cached")
            return rawXml
        }
        return requestWay(id)
    }

    private fun requestWay(id: String): String {
        val url = "${baseUrl}way/$id"
        return Request.Get(url).execute().returnContent().asString()
    }

    fun requestWays(ids: Iterable<String>): String {
        val idString = ids.joinToString(separator = ",") { it -> it }
        val url = "${baseUrl}ways?ways=$idString"
        return Request.Get(url).execute().returnContent().asString()
    }

    fun requestAreas(areas: Iterable<Area>, useCache: Boolean = true): MutableMap<Area, String> {
        val map = mutableMapOf<Area, String>()
        for (area in areas) {
            map[area] = requestArea(area, useCache)
        }
        return map
    }

    fun requestArea(area: de.twoSIT.models.Area, useCache: Boolean = true): String {
        if (useCache) {
            val dirName = responseCacheDir
            File(dirName).mkdir()
            val cached = File("$dirName/${areaCacheFile(area)}")

            if (cached.exists()) {
                logger.debug("Loaded cached area $cached")
                return cached.readText()
            }

            val rawXml = requestArea(area)
            cached.printWriter().use { out ->
                val lines = rawXml.split("\n")
                for (line in lines) {
                    out.println(line)
                }
            }
            logger.debug("Saved area in $cached")
            return rawXml
        }

        return requestArea(area)
    }

    private fun requestArea(area: Area): String {
        val url = "${baseUrl}map?bbox=${area.minLatitude},${area.minLongitude},${area.maxLatitude},${area.maxLongitude}"
        return Request.Get(url).execute().returnContent().asString()
    }
}