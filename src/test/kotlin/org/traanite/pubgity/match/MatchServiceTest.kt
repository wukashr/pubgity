package org.traanite.pubgity.match

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.traanite.pubgity.config.MongoRepositoryTest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatchServiceTest : MongoRepositoryTest() {
    @Autowired
    private lateinit var repository: MatchRepository

    @Test
    fun `match save uses insert and never replaces an existing match`() {
        val service = MatchService(repository)
        val match = Match(
            matchId = "m",
            createdAt = Instant.EPOCH,
            seasonId = "s",
            gameMode = "solo",
            mapName = "x",
            duration = 1,
            telemetryFileUri = null
        )
        val savedMatch = service.saveMatch(match)
        val replacement = savedMatch.copy(duration = 99)
        val duplicateMatchId = match.copy(duration = 99)

        assertFailsWith<DuplicateKeyException> { service.saveMatch(replacement) }
        assertFailsWith<DuplicateKeyException> { service.saveMatch(duplicateMatchId) }
        val actual = repository.findByMatchId(match.matchId)
        assertEquals(savedMatch, actual)
    }
}
