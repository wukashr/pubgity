package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.traanite.pubgity.config.MongoRepositoryTest
import org.traanite.pubgity.match.Match
import org.traanite.pubgity.match.MatchParticipant
import org.traanite.pubgity.match.MatchRoster
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsSnapshotServiceTest : MongoRepositoryTest() {
    @Autowired
    private lateinit var lifetimeRepository: LifetimeStatsSnapshotRepository

    @Autowired
    private lateinit var seasonRepository: SeasonStatsSnapshotRepository
    private val store get() = StatsSnapshotService(lifetimeRepository, seasonRepository)
    private val at = Instant.parse("2026-01-10T00:00:00Z")

    @Test
    fun `chooses nearest lifetime snapshot including after and prefers before on ties`() {
        val before = LifetimeStatsSnapshot(accountId = "a", capturedAt = at.minusSeconds(30), stats = LifetimeStats())
        val after = LifetimeStatsSnapshot(accountId = "a", capturedAt = at.plusSeconds(10), stats = LifetimeStats())
        lifetimeRepository.insert(listOf(before, after))
        assertEquals(after.id, store.closestLifetime("a", at)?.id)
        lifetimeRepository.delete(after)
        lifetimeRepository.insert(after.copy(capturedAt = at.plusSeconds(30)))
        assertEquals(before.id, store.closestLifetime("a", at)?.id)
        assertNull(store.closestLifetime("missing", at))
    }

    @Test
    fun `season lookup constrains account and season for both candidates`() {
        val snapshot = SeasonStatsSnapshot(
            accountId = "a",
            capturedAt = at.plusSeconds(1),
            stats = SeasonStats(seasonId = "season-a", capturedAt = at.plusSeconds(1))
        )
        seasonRepository.insert(snapshot)
        seasonRepository.insert(snapshot.copy(id = ObjectId(), stats = SeasonStats("other", at)))
        assertEquals(snapshot, store.closestSeason("a", "season-a", at))
        assertNull(store.closestSeason("other", "season-a", at))
    }

    @Test
    fun `snapshots captured at the same time use the lowest id deterministically`() {
        val first = LifetimeStatsSnapshot(
            id = ObjectId("000000000000000000000001"), accountId = "a", capturedAt = at, stats = LifetimeStats()
        )
        val second = first.copy(id = ObjectId("000000000000000000000002"))
        lifetimeRepository.insert(listOf(second, first))
        assertEquals(first.id, store.closestLifetime("a", at)?.id)
        assertEquals(first.id, store.latestLifetime("a")?.id)
    }

    @Test
    fun `loads referenced snapshots and rejects missing references`() {
        val lifetime = LifetimeStatsSnapshot(accountId = "a", capturedAt = at, stats = LifetimeStats())
        val season = SeasonStatsSnapshot(
            accountId = "a", capturedAt = at, stats = SeasonStats(seasonId = "s", capturedAt = at)
        )
        val participant =
            MatchParticipant("a", "A", lifetimeStatsSnapshotId = lifetime.id, seasonStatsSnapshotId = season.id)
        val match = Match(
            matchId = "m",
            createdAt = at,
            seasonId = "s",
            gameMode = "solo",
            mapName = "x",
            duration = 1,
            rosters = listOf(MatchRoster("r", 1, true, listOf(participant))),
            telemetryFileUri = null
        )
        lifetimeRepository.insert(lifetime)
        seasonRepository.insert(season)

        val expectedLifetime = mapOf(lifetime.id to lifetime)
        val expectedSeason = mapOf(season.id to season)
        val loaded = store.loadForMatches(listOf(match, match.copy(matchId = "m2")))
        assertEquals(expectedLifetime, loaded.lifetime)
        assertEquals(expectedSeason, loaded.season)

        lifetimeRepository.delete(lifetime)
        val seasonOnly = store.loadForMatches(listOf(match), includeLifetime = false)
        assertEquals(expectedSeason, seasonOnly.season)
        assertTrue(seasonOnly.lifetime.isEmpty())
        assertFailsWith<IllegalStateException> { store.loadForMatches(listOf(match)) }
    }

    @Test
    fun `loads large sets of distinct references`() {
        val snapshots = (1..501).map {
            LifetimeStatsSnapshot(accountId = "a", capturedAt = at, stats = LifetimeStats())
        }
        val participants = snapshots.map { MatchParticipant("a", "A", lifetimeStatsSnapshotId = it.id) }
        val match = Match(
            matchId = "m",
            createdAt = at,
            seasonId = "s",
            gameMode = "solo",
            mapName = "x",
            duration = 1,
            rosters = listOf(MatchRoster("r", 1, true, participants)),
            telemetryFileUri = null
        )
        lifetimeRepository.insert(snapshots)
        val expected = snapshots.associateBy { it.id }
        assertEquals(expected, store.loadForMatches(listOf(match)).lifetime)
    }

    @Test
    fun `rejects references belonging to a different player or season`() {
        val lifetime = LifetimeStatsSnapshot(accountId = "other", capturedAt = at, stats = LifetimeStats())
        val season = SeasonStatsSnapshot(accountId = "a", capturedAt = at, stats = SeasonStats("other", at))
        lifetimeRepository.insert(lifetime)
        seasonRepository.insert(season)
        val participant =
            MatchParticipant("a", "A", lifetimeStatsSnapshotId = lifetime.id, seasonStatsSnapshotId = season.id)
        val match = Match(
            matchId = "m",
            createdAt = at,
            seasonId = "s",
            gameMode = "solo",
            mapName = "x",
            duration = 1,
            rosters = listOf(MatchRoster("r", 1, true, listOf(participant))),
            telemetryFileUri = null
        )
        assertFailsWith<IllegalStateException> { store.loadForMatches(listOf(match), includeSeason = false) }
        assertFailsWith<IllegalStateException> { store.loadForMatches(listOf(match), includeLifetime = false) }
    }
}
