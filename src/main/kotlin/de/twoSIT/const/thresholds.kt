package de.twoSIT.const

import de.twoSIT.models.Config

val LEVEL_CONNECTION_NODE_PROXY_THRESHOLD = Config.maxLevelConnectionNodeOffsetInM
val NODE_PROXY_THRESHOLD = Config.maxWallWidthInM

val SIMPLE_NODE_BLACKLIST = setOf<String>("window", "door")