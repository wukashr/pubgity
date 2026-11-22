package org.traanite.pubgity.import

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.traanite.pubgity.config.ApplicationFlowTest
import org.traanite.pubgity.match.MatchRepository
import org.traanite.pubgity.player.*
import org.traanite.pubgity.pubgapi.*
import org.traanite.pubgity.view.MatchViewService
import org.traanite.pubgity.view.PlayerStatsViewService
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FetchMatchStatsJobExecutorTest : ApplicationFlowTest() {
    @Autowired
    private lateinit var playerJobs: FetchPlayerMatchesJobExecutor

    @Autowired
    private lateinit var matchJobs: FetchMatchStatsJobExecutor

    @Autowired
    private lateinit var jobs: ImportJobRepository

    @Autowired
    private lateinit var players: PlayerRepository

    @Autowired
    private lateinit var matches: MatchRepository

    @Autowired
    private lateinit var lifetimes: LifetimeStatsSnapshotRepository

    @Autowired
    private lateinit var seasons: SeasonStatsSnapshotRepository

    @Autowired
    private lateinit var playerViews: PlayerStatsViewService

    @Autowired
    private lateinit var matchViews: MatchViewService

    @Test
    fun `player and match jobs persist historical snapshots used by views despite later captures`() {
        val at = Instant.ofEpochMilli(Instant.now().toEpochMilli()).minusSeconds(30)
        val player = players.insert(Player(playerName = "A"))
        players.insert(Player(playerName = "B", accountId = "account.b"))
        players.insert(Player(playerName = "Private", accountId = "account.private", isPrivacyProtected = true))
        val lifetimeBefore = lifetime("account.a", at.minusSeconds(30), 1)
        val lifetimeAfter = lifetime("account.a", at.plusSeconds(10), 10)
        lifetimes.insert(listOf(lifetimeBefore, lifetimeAfter))
        lifetimes.insert(lifetime("account.b", at.minusSeconds(5), 40))
        val seasonBefore = season("account.a", "s", at.minusSeconds(10), 20)
        seasons.insert(
            listOf(
                seasonBefore,
                season("account.a", "s", at.plusSeconds(10), 30),
                season("account.a", "other", at, 99),
                season("account.other", "s", at, 99),
                season("account.b", "s", at.minusSeconds(5), 50)
            )
        )
        Mockito.`when`(api.getPlayerByName("A")).thenReturn(
            PlayerData(
                "account.a", PlayerAttributes("A"), PlayerRelationships(
                    RelationshipData(listOf(RelationshipItem("match", "m")))
                )
            )
        )
        stubMatch(at, includeSecond = true)
        val job = jobs.insert(ImportJob(playerName = "A", matchCount = 1, jobType = JobType.FETCH_PLAYER_MATCHES))

        val expectedJobStatus = JobStatus.COMPLETED
        val expectedAccountId = "account.a"
        val expectedMatchId = "m"
        val expectedMatchIds = setOf("m")
        val expectedLifetimeKills = 10.0
        val expectedSeasonKills = 20.0
        val expectedParticipantCount = 2
        val expectedSecondSeasonKills = 50.0
        val expectedSecondLifetimeKills = 40.0
        val expectedTableKills = 10
        val expectedLatestKills = 100
        playerJobs.processNextJob()
        assertEquals(expectedJobStatus, jobs.findById(job.id!!).get().status)
        val child = jobs.findAll().single { it.jobType == JobType.FETCH_MATCH_STATS }
        assertEquals(expectedAccountId, child.accountId)
        assertEquals(expectedMatchId, child.matchId)

        matchJobs.processNextJob()
        assertEquals(expectedJobStatus, jobs.findById(child.id!!).get().status)
        val match = assertNotNull(matches.findByMatchId("m"))
        val byAccount = match.rosters.flatMap { it.participants }.associateBy { it.accountId }
        assertEquals(lifetimeAfter.id, byAccount.getValue("account.a").lifetimeStatsSnapshotId)
        assertEquals(seasonBefore.id, byAccount.getValue("account.a").seasonStatsSnapshotId)
        assertTrue(byAccount.getValue("account.private").isPrivacyProtected)
        assertNull(byAccount.getValue("account.private").lifetimeStatsSnapshotId)
        assertNull(byAccount.getValue("account.private").seasonStatsSnapshotId)
        assertEquals(expectedMatchIds, players.findById(player.id!!).get().matches.matchIds)
        assertEquals(expectedMatchIds, players.findByAccountId("account.private")!!.matches.matchIds)
        assertEquals(expectedMatchIds, players.findByAccountId("account.b")!!.matches.matchIds)

        val lifetimeChart = playerViews.lifetimeChart("account.a").perMatchStats
        val seasonChart = playerViews.seasonChart("account.a").perMatchSeasonStats
        val table = matchViews.detail("account.a", "m")
        assertEquals(expectedLifetimeKills, lifetimeChart.single().playerSnapshotKills)
        assertEquals(expectedSeasonKills, seasonChart.single().playerSeasonKills)
        assertEquals(expectedParticipantCount, seasonChart.single().seasonParticipantCount)
        val secondPlayerChart = playerViews.seasonChart("account.b").perMatchSeasonStats
        assertEquals(expectedSecondSeasonKills, secondPlayerChart.single().playerSeasonKills)
        val secondPlayerLifetime = playerViews.lifetimeChart("account.b").perMatchStats
        assertEquals(expectedSecondLifetimeKills, secondPlayerLifetime.single().playerSnapshotKills)
        assertEquals(expectedTableKills, table.participants.first { it.accountId == "account.a" }.kills)
        assertNull(playerViews.lifetimeChart("account.private").perMatchStats.single().playerSnapshotKills)
        assertNull(playerViews.seasonChart("account.private").perMatchSeasonStats.single().playerSeasonKills)

        lifetimes.insert(lifetime("account.a", at.plusSeconds(60), 100))
        lifetimes.insert(lifetime("account.b", at.plusSeconds(60), 140))
        seasons.insert(season("account.a", "s", at.plusSeconds(60), 100))
        seasons.insert(season("account.b", "s", at.plusSeconds(60), 150))
        assertEquals(lifetimeChart, playerViews.lifetimeChart("account.a").perMatchStats)
        assertEquals(seasonChart, playerViews.seasonChart("account.a").perMatchSeasonStats)
        assertEquals(secondPlayerChart, playerViews.seasonChart("account.b").perMatchSeasonStats)
        assertEquals(secondPlayerLifetime, playerViews.lifetimeChart("account.b").perMatchStats)
        val tableAfter = matchViews.detail("account.a", "m")
        assertEquals(table.match, tableAfter.match)
        assertEquals(table.participants, tableAfter.participants)
        assertEquals(
            expectedLatestKills,
            playerViews.detailByAccountId("account.a").latestStats!!.stats.gameModeStats.solo!!.kills
        )
    }

    @Test
    fun `missing season fails job without saving a match`() {
        stubMatch(Instant.now(), seasonState = "finished")
        val job = jobs.insert(ImportJob(playerName = "A", matchId = "m", jobType = JobType.FETCH_MATCH_STATS))
        val expectedStatus = JobStatus.FAILED
        matchJobs.processNextJob()
        val result = jobs.findById(job.id!!).get()
        assertEquals(expectedStatus, result.status)
        assertTrue(result.errorMessage!!.contains("No season ID"))
        assertNull(matches.findByMatchId("m"))
    }

    @Test
    fun `failed stats fetch fails job without saving a match`() {
        stubMatch(Instant.now())
        Mockito.`when`(api.getLifetimeStats("account.a")).thenThrow(IllegalStateException("API unavailable"))
        val job = jobs.insert(ImportJob(playerName = "A", matchId = "m", jobType = JobType.FETCH_MATCH_STATS))
        val expectedStatus = JobStatus.FAILED
        matchJobs.processNextJob()
        val result = jobs.findById(job.id!!).get()
        assertEquals(expectedStatus, result.status)
        assertTrue(result.errorMessage!!.contains("Lifetime stats update failed"))
        assertNull(matches.findByMatchId("m"))
    }

    @Test
    fun `match job fetches and persists missing snapshots before saving their references`() {
        val at = Instant.ofEpochMilli(Instant.now().toEpochMilli()).minusSeconds(10)
        players.insert(Player(playerName = "Private", accountId = "account.private", isPrivacyProtected = true))
        stubMatch(at)
        Mockito.`when`(api.getLifetimeStats("account.a")).thenReturn(
            LifetimeStatsResponse(
                LifetimeStatsData(
                    LifetimeStatsAttributes(
                        gameModeStats = ApiGameModeStats(solo = ApiModeStats(kills = 7))
                    )
                )
            )
        )
        Mockito.`when`(api.getSeasonStats("s", "account.a")).thenReturn(
            SeasonStats("s", at.plusSeconds(5), modes(8))
        )
        val job = jobs.insert(ImportJob(playerName = "A", matchId = "m", jobType = JobType.FETCH_MATCH_STATS))
        val expectedStatus = JobStatus.COMPLETED
        val expectedLifetimeKills = 7
        val expectedSeasonKills = 8
        val expectedMatchIds = setOf("m")

        matchJobs.processNextJob()

        assertEquals(expectedStatus, jobs.findById(job.id!!).get().status)
        val participant =
            matches.findByMatchId("m")!!.rosters.single().participants.first { it.accountId == "account.a" }
        assertEquals(
            expectedLifetimeKills,
            lifetimes.findById(participant.lifetimeStatsSnapshotId!!).get().stats.gameModeStats.solo!!.kills
        )
        assertEquals(
            expectedSeasonKills,
            seasons.findById(participant.seasonStatsSnapshotId!!).get().stats.gameModeStats.solo!!.kills
        )
        assertEquals(expectedMatchIds, players.findByAccountId("account.a")!!.matches.matchIds)
    }

    @Test
    fun `existing match makes job skipped without replacing it`() {
        val existing = matches.insert(
            org.traanite.pubgity.match.Match(
                matchId = "m",
                createdAt = Instant.EPOCH,
                seasonId = "s",
                gameMode = "solo",
                mapName = "Baltic_Main",
                duration = 1,
                telemetryFileUri = null
            )
        )
        val job = jobs.insert(ImportJob(playerName = "A", matchId = "m", jobType = JobType.FETCH_MATCH_STATS))
        val expectedStatus = JobStatus.SKIPPED
        matchJobs.processNextJob()
        assertEquals(expectedStatus, jobs.findById(job.id!!).get().status)
        assertEquals(existing, matches.findByMatchId("m"))
    }

    @Test
    fun `player job does not enqueue matches already stored`() {
        val at = Instant.ofEpochMilli(Instant.now().toEpochMilli())
        players.insert(Player(playerName = "A"))
        matches.insert(
            org.traanite.pubgity.match.Match(
                matchId = "m",
                createdAt = at,
                seasonId = "s",
                gameMode = "solo",
                mapName = "Baltic_Main",
                duration = 1,
                telemetryFileUri = null
            )
        )
        Mockito.`when`(api.getPlayerByName("A")).thenReturn(
            PlayerData(
                "account.a",
                PlayerAttributes("A"),
                PlayerRelationships(RelationshipData(listOf(RelationshipItem("match", "m"))))
            )
        )
        stubMatch(at)
        val job = jobs.insert(ImportJob(playerName = "A", matchCount = 1, jobType = JobType.FETCH_PLAYER_MATCHES))
        val expectedStatus = JobStatus.COMPLETED
        val expectedMatchJobCount = 0
        val expectedAccountId = "account.a"

        playerJobs.processNextJob()

        assertEquals(expectedStatus, jobs.findById(job.id!!).get().status)
        assertEquals(expectedMatchJobCount, jobs.findAll().count { it.jobType == JobType.FETCH_MATCH_STATS })
        assertEquals(expectedAccountId, players.findByPlayerName("A")!!.accountId)
    }

    @Test
    fun `player job records API failures without queuing match jobs`() {
        Mockito.`when`(api.getPlayerByName("A")).thenThrow(IllegalStateException("API unavailable"))
        val job = jobs.insert(ImportJob(playerName = "A", matchCount = 1, jobType = JobType.FETCH_PLAYER_MATCHES))
        val expectedStatus = JobStatus.FAILED
        val expectedError = "API unavailable"
        val expectedJobCount = 1L

        playerJobs.processNextJob()

        val result = jobs.findById(job.id!!).get()
        assertEquals(expectedStatus, result.status)
        assertEquals(expectedError, result.errorMessage)
        assertEquals(expectedJobCount, jobs.count())
        assertNull(players.findByPlayerName("A"))
    }

    private fun stubMatch(at: Instant, seasonState: String = "progress", includeSecond: Boolean = false) {
        Mockito.`when`(api.getCurrentSeason()).thenReturn(SeasonData("season", "s"))
        val participantIds = mutableListOf(RelationshipItem("participant", "p1"), RelationshipItem("participant", "p2"))
        val second = if (includeSecond) {
            participantIds.add(RelationshipItem("participant", "p3"))
            listOf(
                MatchIncluded(
                    "participant", "p3", MatchIncludedAttributes(
                        stats = IncludedStats(playerId = "account.b", name = "B", winPlace = 2)
                    )
                )
            )
        } else emptyList()
        Mockito.`when`(api.getMatch("m")).thenReturn(
            MatchResponse(
                MatchData("m", MatchAttributes(at.toString(), "solo", "Baltic_Main", 600, seasonState)), listOf(
                    MatchIncluded(
                        "participant", "p1", MatchIncludedAttributes(
                            stats = IncludedStats(playerId = "account.a", name = "A", winPlace = 2)
                        )
                    ), MatchIncluded(
                        "participant", "p2", MatchIncludedAttributes(
                            stats = IncludedStats(playerId = "account.private", name = "Private", winPlace = 2)
                        )
                    ), MatchIncluded(
                        "roster",
                        "r",
                        MatchIncludedAttributes(stats = IncludedStats(rank = 2)),
                        MatchIncludedRelationships(RelationshipData(participantIds))
                    )
                ) + second
            )
        )
    }

    private fun lifetime(accountId: String, at: Instant, kills: Int) = LifetimeStatsSnapshot(
        accountId = accountId, capturedAt = at, stats = LifetimeStats(gameModeStats = modes(kills))
    )

    private fun season(accountId: String, seasonId: String, at: Instant, kills: Int) =
        SeasonStatsSnapshot(accountId = accountId, capturedAt = at, stats = SeasonStats(seasonId, at, modes(kills)))

    private fun modes(kills: Int) = GameModeStats(solo = ModeStats(kills = kills, roundsPlayed = 2))
}
