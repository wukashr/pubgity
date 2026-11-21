package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.traanite.pubgity.match.Match
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.*

@Component
class MatchLabelFormatter(objectMapper: ObjectMapper) {
    private val logger = LoggerFactory.getLogger(MatchLabelFormatter::class.java)
    private val mapNames: Map<String, String> = ClassPathResource("map-names.json").inputStream.use {
        objectMapper.readValue(it, object : TypeReference<Map<String, String>>() {})
    }.also { names ->
        require(names.isNotEmpty() && names.all { (key, value) -> key.isNotBlank() && value.isNotBlank() }) {
            "map-names.json must contain nonblank map identifiers and labels"
        }
    }

    fun mapName(raw: String): String = mapNames[raw] ?: raw.also {
        logger.warn("No display name configured for PUBG map '{}'", raw)
    }

    fun gameMode(raw: String): String = raw.replace('-', ' ').split(' ').joinToString(" ") { word ->
        if (word.equals("fpp", ignoreCase = true)) {
            "FPP"
        } else {
            word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    fun chartLabel(match: Match): String =
        "${match.createdAt} - ${gameMode(match.gameMode)} - ${mapName(match.mapName)}"
}
