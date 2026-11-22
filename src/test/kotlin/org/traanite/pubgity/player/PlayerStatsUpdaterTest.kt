package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.traanite.pubgity.config.ApplicationFlowTest
import org.traanite.pubgity.pubgapi.LifetimeStatsAttributes
import org.traanite.pubgity.pubgapi.LifetimeStatsData
import org.traanite.pubgity.pubgapi.LifetimeStatsResponse
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class PlayerStatsUpdaterTest : ApplicationFlowTest() {
    @Autowired
    private lateinit var players: PlayerRepository

    @Autowired
    private lateinit var lifetimeRepository: LifetimeStatsSnapshotRepository

    @Autowired
    private lateinit var seasonRepository: SeasonStatsSnapshotRepository

    @Autowired
    private lateinit var snapshots: StatsSnapshotService

    @Autowired
    private lateinit var updater: PlayerStatsUpdater
    private val player = Player(accountId = "a", playerName = "A")

    @Test
    fun `recent snapshots use TTL while expired snapshots append fresh data`() {
        players.insert(player)
        val now = Instant.ofEpochMilli(Instant.now().toEpochMilli())
        val lifetime = LifetimeStatsSnapshot(accountId = "a", capturedAt = now, stats = LifetimeStats())
        val season = SeasonStatsSnapshot(accountId = "a", capturedAt = now, stats = SeasonStats("s", now))
        lifetimeRepository.insert(lifetime)
        seasonRepository.insert(season)
        players.insert(Player(accountId = "b", playerName = "B"))
        val oldLifetime = lifetime.copy(id = ObjectId(), accountId = "b", capturedAt = now.minus(Duration.ofDays(8)))
        val oldAt = now.minus(Duration.ofDays(8))
        val oldSeason =
            season.copy(id = ObjectId(), accountId = "b", capturedAt = oldAt, stats = SeasonStats("s", oldAt))
        lifetimeRepository.insert(oldLifetime)
        seasonRepository.insert(oldSeason)
        val fetchedSeason = SeasonStats("s", now, GameModeStats(solo = ModeStats(kills = 10)))
        Mockito.`when`(api.getSeasonStats("s", "b")).thenReturn(fetchedSeason)
        Mockito.`when`(api.getLifetimeStats("b"))
            .thenReturn(LifetimeStatsResponse(LifetimeStatsData(LifetimeStatsAttributes())))

        val expectedSkipped = StatsUpdateResult.SKIPPED
        val expectedUpdated = StatsUpdateResult.UPDATED
        val expectedCount = 3L
        assertEquals(expectedSkipped, updater.updateLifetimeStats("a", "A"))
        assertEquals(expectedSkipped, updater.updateSeasonPlayerStats("a", "A", "s"))
        assertEquals(lifetime, snapshots.latestLifetime("a"))
        assertEquals(season, snapshots.latestSeason("a", "s"))

        assertEquals(expectedUpdated, updater.updateLifetimeStats("b", "B"))
        assertEquals(expectedUpdated, updater.updateSeasonPlayerStats("b", "B", "s"))
        assertEquals(expectedCount, lifetimeRepository.count())
        assertEquals(expectedCount, seasonRepository.count())
        assertEquals(oldLifetime, lifetimeRepository.findById(oldLifetime.id).get())
        assertEquals(oldSeason, seasonRepository.findById(oldSeason.id).get())
        assertEquals(fetchedSeason, snapshots.latestSeason("b", "s")?.stats)
    }

    @Test
    fun `privacy protection skips stats lookups and API calls`() {
        players.insert(Player(accountId = "a", playerName = "A", isPrivacyProtected = true))
        val expectedResult = StatsUpdateResult.PRIVACY_PROTECTED
        val expectedCount = 0L
        assertEquals(expectedResult, updater.updateLifetimeStats("a", "A"))
        assertEquals(expectedResult, updater.updateSeasonPlayerStats("a", "A", "s"))
        assertEquals(expectedCount, lifetimeRepository.count())
        assertEquals(expectedCount, seasonRepository.count())
    }
}
