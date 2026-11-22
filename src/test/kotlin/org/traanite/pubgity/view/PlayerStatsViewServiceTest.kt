package org.traanite.pubgity.view

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.traanite.pubgity.config.ApplicationFlowTest
import org.traanite.pubgity.match.*
import org.traanite.pubgity.player.Player
import org.traanite.pubgity.player.PlayerMatches
import org.traanite.pubgity.player.PlayerRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerStatsViewServiceTest : ApplicationFlowTest() {
    @Autowired
    private lateinit var views: PlayerStatsViewService

    @Autowired
    private lateinit var matchViews: MatchViewService

    @Autowired
    private lateinit var players: PlayerRepository

    @Autowired
    private lateinit var matches: MatchRepository

    @Test
    fun `list filters and orders players and detail sorts matches without inventing stats`() {
        val at = Instant.parse("2026-01-01T00:00:00Z")
        val player = players.insert(
            Player(
                playerName = "Alpha", accountId = "account.a", matches = PlayerMatches(setOf("old", "new"))
            )
        )
        players.insert(Player(playerName = "Alfred", accountId = "account.b"))
        players.insert(Player(playerName = "Beta", accountId = "account.c"))
        val old = Match(
            matchId = "old",
            createdAt = at,
            seasonId = "s",
            gameMode = "solo",
            mapName = "Baltic_Main",
            duration = 100,
            rosters = listOf(
                MatchRoster(
                    "r", 2, false, listOf(
                        MatchParticipant("account.a", "Alpha", matchStats = MatchParticipantStats(winPlace = 2))
                    )
                )
            ),
            telemetryFileUri = null
        )
        matches.insert(listOf(old, old.copy(matchId = "new", createdAt = at.plusSeconds(60))))

        val detail = views.detailByAccountId("account.a")
        val list1 = views.list("Al")
        val list2 = views.list(null)
        views.setPrivacyProtected("account.a", true)

        val expectedPlayers = listOf("Alpha", "Alfred")
        val expectedFilter = ""
        val expectedMatches = listOf("new", "old")
        val expectedMap = "Erangel"
        val expectedDuration = "1m 40s"
        val expectedPlace = 2

        assertEquals(expectedPlayers, list1.players.map { it.playerName })
        assertEquals(expectedFilter, list2.filter)

        assertEquals(player.id, detail.player.id)
        assertEquals(expectedMatches, detail.matches.map { it.matchId })
        assertEquals(expectedMap, detail.matches.first().mapName)
        assertEquals(expectedDuration, detail.matches.first().durationLabel)
        assertEquals(expectedPlace, detail.matches.first().placeTaken)
        assertNull(detail.latestStats)
        assertNull(detail.latestSeasonStats)
        assertNull(detail.playerAggregated)
        assertNull(detail.playerSeasonAggregated)

        assertTrue(players.findByAccountId("account.a")!!.isPrivacyProtected)
    }

    @Test
    fun `missing and unlinked players produce the intended errors`() {
        val expectedStatus = HttpStatus.NOT_FOUND
        assertEquals(
            expectedStatus, assertFailsWith<ResponseStatusException> { views.detailByAccountId("missing") }.statusCode
        )
        assertEquals(
            expectedStatus, assertFailsWith<ResponseStatusException> { views.lifetimeChart("missing") }.statusCode
        )
        assertEquals(
            expectedStatus, assertFailsWith<ResponseStatusException> { views.seasonChart("missing") }.statusCode
        )
        assertEquals(
            expectedStatus, assertFailsWith<ResponseStatusException> { matchViews.detail("missing", "m") }.statusCode
        )
        players.insert(Player(playerName = "A", accountId = "account.a"))
        assertEquals(
            expectedStatus,
            assertFailsWith<ResponseStatusException> { matchViews.detail("account.a", "missing") }.statusCode
        )
        assertNull(views.detail(Player(playerName = "unlinked")))
    }
}
