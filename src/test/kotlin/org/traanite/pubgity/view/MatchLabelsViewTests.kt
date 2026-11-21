package org.traanite.pubgity.view

import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.web.csrf.DefaultCsrfToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.traanite.pubgity.config.TestSecurityConfig
import org.traanite.pubgity.import.ImportJobRepository
import org.traanite.pubgity.match.*
import org.traanite.pubgity.player.*
import org.traanite.pubgity.user.AppUser
import org.traanite.pubgity.user.AppUserRepository
import org.traanite.pubgity.view.aggregation.PerMatchSeasonSkillData
import org.traanite.pubgity.view.aggregation.PerMatchSkillData
import java.time.Instant
import java.util.*
import kotlin.test.*

@SpringBootTest
@Import(TestSecurityConfig::class)
class MatchLabelsViewTests {
    @MockitoBean lateinit var matchRepository: MatchRepository
    @MockitoBean lateinit var playerRepository: PlayerRepository
    @MockitoBean lateinit var appUserRepository: AppUserRepository
    @MockitoBean lateinit var importJobRepository: ImportJobRepository

    @Autowired lateinit var context: WebApplicationContext

    private lateinit var mvc: MockMvc
    private val accountId = "account.test"
    private val createdAt = Instant.parse("2026-01-01T12:00:00Z")
    private val seasonId = "season.test"
    private val matches = listOf("Baltic_Main", "Erangel_Main").mapIndexed { index, map ->
        Match(
            matchId = "match.$index",
            createdAt = createdAt.plusSeconds(index.toLong()),
            seasonId = seasonId,
            gameMode = "solo-fpp",
            mapName = map,
            duration = 600,
            botCount = 3,
            rosters = listOf(
                MatchRoster(
                    rosterId = "roster.test", rank = 1, won = true,
                    participants = listOf(
                        MatchParticipant(
                            accountId = accountId, playerName = "TestPlayer",
                            lifetimeStatsSnapshot = ParticipantLifetimeStats(
                                gameModeStats = ParticipantGameModeStats(
                                    soloFpp = ParticipantModeStats(kills = 20, roundsPlayed = 4),
                                    squadFpp = ParticipantModeStats(kills = 99, roundsPlayed = 1)
                                )
                            )
                        )
                    )
                )
            ),
            telemetryFileUri = null
        )
    }
    private val player = Player(
        id = ObjectId(),
        playerName = "TestPlayer",
        accountId = accountId,
        matches = PlayerMatches(matches.map { it.matchId }.toSet()),
        seasonStats = PlayerSeasonStats(
            mapOf(
                seasonId to SeasonStats(
                    seasonId = seasonId,
                    gameModeStats = GameModeStats(
                        soloFpp = ModeStats(kills = 12, roundsPlayed = 4),
                        squadFpp = ModeStats(kills = 99, roundsPlayed = 1)
                    )
                )
            )
        )
    )

    @BeforeEach
    fun setup() {
        `when`(matchRepository.findByMatchIdIn(player.matches.matchIds.toList())).thenReturn(matches)
        matches.forEach { `when`(matchRepository.findByMatchId(it.matchId)).thenReturn(it) }
        `when`(playerRepository.findByAccountId(accountId)).thenReturn(player)
        `when`(playerRepository.findByAccountIdIn(setOf(accountId))).thenReturn(listOf(player))
        `when`(playerRepository.findById(assertNotNull(player.id))).thenReturn(Optional.of(player))
        `when`(appUserRepository.findBySub("subject.test")).thenReturn(
            AppUser(sub = "subject.test", username = "TestUser", email = "", linkedPlayerId = player.id)
        )
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `all rendered surfaces use readable labels without changing stored matches or statistics`() {
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val token = OidcIdToken("test-token", createdAt, createdAt.plusSeconds(3600), mapOf("sub" to "subject.test"))
        val auth = UsernamePasswordAuthenticationToken(DefaultOidcUser(authorities, token), null, authorities)
        val pages = listOf(
            "/" to "home",
            "/players/$accountId" to "player-detail",
            "/players/$accountId/charts/lifetime" to "player-lifetime-charts",
            "/players/$accountId/charts/season" to "player-season-charts",
            "/players/$accountId/matches/match.0" to "match-detail"
        )
        pages.forEach { (path, view) ->
            val result = mvc.perform(
                get(path).principal(auth)
                    .requestAttr("_csrf", DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf"))
            ).andReturn()
            assertEquals(200, result.response.status, path)
            val modelAndView = assertNotNull(result.modelAndView)
            assertEquals(view, modelAndView.viewName)
            val html = result.response.contentAsString
            assertTrue(html.contains("Solo FPP"), path)
            assertTrue(html.contains("Erangel"), path)
            assertFalse(html.contains("solo-fpp"), path)
            assertFalse(html.contains("Baltic_Main"), path)
            assertFalse(html.contains("Erangel_Main"), path)

            if (view == "match-detail") {
                val detail = assertIs<MatchDetailView>(modelAndView.model["match"])
                assertEquals("Solo FPP", detail.gameMode)
                assertEquals("Erangel", detail.mapName)
                assertEquals(600, detail.duration)
                assertEquals(3, detail.botCount)
                val participant = assertIs<ParticipantView>(
                    assertIs<List<*>>(modelAndView.model["participants"]).single()
                )
                assertEquals(5.0, participant.kd)
            } else {
                val list = assertIs<List<*>>(modelAndView.model["matches"])
                assertEquals(2, list.size)
                list.forEach {
                    val matchView = assertIs<PlayerMatchView>(it)
                    assertEquals("Solo FPP", matchView.gameMode)
                    assertEquals("Erangel", matchView.mapName)
                }
                val lifetime = assertIs<List<*>>(modelAndView.model["perMatchStats"])
                val season = assertIs<List<*>>(modelAndView.model["perMatchSeasonStats"])
                matches.forEachIndexed { index, match ->
                    val lifetimePoint = assertIs<PerMatchSkillData>(lifetime[index])
                    val seasonPoint = assertIs<PerMatchSeasonSkillData>(season[index])
                    val expectedLabel = "${match.createdAt} - Solo FPP - Erangel"
                    assertEquals(expectedLabel, lifetimePoint.label)
                    assertEquals(expectedLabel, seasonPoint.label)
                    assertEquals(listOf(match.createdAt.toString(), "Solo FPP", "Erangel"), expectedLabel.split(" - "))
                    assertEquals(match.matchId, lifetimePoint.matchId)
                    assertEquals(match.matchId, seasonPoint.matchId)
                    assertEquals(5.0, lifetimePoint.playerSnapshotKD)
                    assertEquals(3.0, seasonPoint.playerSeasonKD)
                }
            }
        }
        assertEquals(listOf("Baltic_Main", "Erangel_Main"), matches.map { it.mapName })
        assertTrue(matches.all { it.gameMode == "solo-fpp" })
    }
}
