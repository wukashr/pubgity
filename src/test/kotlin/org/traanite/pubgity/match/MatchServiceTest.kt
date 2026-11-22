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
        val match = Match(matchId = "m", createdAt = Instant.EPOCH, seasonId = "s", gameMode = "solo",
            mapName = "x", duration = 1, telemetryFileUri = null)
        val saved = service.saveMatch(match)
        assertEquals(match.matchId, saved.matchId)
        assertFailsWith<DuplicateKeyException> { service.saveMatch(match) }
        assertEquals(saved, repository.findByMatchId(match.matchId))
    }
}
