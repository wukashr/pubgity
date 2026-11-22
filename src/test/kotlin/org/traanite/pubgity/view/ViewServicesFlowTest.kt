package org.traanite.pubgity.view

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.traanite.pubgity.config.ApplicationFlowTest
import org.traanite.pubgity.import.ImportJob
import org.traanite.pubgity.import.ImportJobRepository
import org.traanite.pubgity.import.JobStatus
import org.traanite.pubgity.import.JobType
import org.traanite.pubgity.player.Player
import org.traanite.pubgity.player.PlayerRepository
import org.traanite.pubgity.user.AppRole
import org.traanite.pubgity.user.AppUser
import org.traanite.pubgity.user.AppUserRepository
import java.time.Instant
import kotlin.test.*

class ViewServicesFlowTest : ApplicationFlowTest() {
    @Autowired
    private lateinit var users: AppUserRepository

    @Autowired
    private lateinit var players: PlayerRepository

    @Autowired
    private lateinit var jobs: ImportJobRepository

    @Autowired
    private lateinit var admin: AdminViewService

    @Autowired
    private lateinit var home: HomeViewService

    @Autowired
    private lateinit var profile: ProfileViewService

    @Autowired
    private lateinit var jobViews: JobsViewService

    @Autowired
    private lateinit var jobController: JobController

    @Test
    fun `profile and home follow persisted player links and report invalid inputs`() {
        val user = users.insert(AppUser(sub = "user", username = "User", email = "user@example.test"))
        val player = players.insert(Player(playerName = "A", accountId = "account.a"))
        val auth = authentication("user")
        val invalidUsername = ProfileActionResult.INVALID_USERNAME
        val playerNotFound = ProfileActionResult.PLAYER_NOT_FOUND
        val success = ProfileActionResult.SUCCESS
        val noUser = ProfileActionResult.NO_USER
        val expectedUsername = "New Name"

        assertTrue(home.home(auth).noLinkedPlayer)
        assertNull(home.home(null).detail)
        assertEquals(invalidUsername, profile.updateUsername(auth, " "))
        assertEquals(playerNotFound, profile.linkPlayer(auth, "missing"))
        assertEquals(success, profile.updateUsername(auth, " New Name "))
        assertEquals(success, profile.linkPlayer(auth, " A "))
        val view = assertNotNull(profile.profile(auth))
        assertEquals(expectedUsername, view.appUser.username)
        assertEquals(player.id, view.linkedPlayer?.id)
        assertTrue(view.oidcAccountUrl.endsWith("/account"))
        assertEquals(player.id, home.home(auth).detail?.player?.id)
        assertEquals(success, profile.unlinkPlayer(auth))
        assertNull(users.findById(user.id!!).get().linkedPlayerId)
        assertTrue(home.home(auth).noLinkedPlayer)
        assertEquals(noUser, profile.unlinkPlayer(authentication("missing")))
    }

    @Test
    fun `admin constraints and moderator actions respect permitted players and capacity`() {
        val allowed = players.insert(Player(playerName = "Allowed", accountId = "account.a"))
        val denied = players.insert(Player(playerName = "Denied", accountId = "account.b"))
        val moderator = users.insert(
            AppUser(
                sub = "mod", username = "Moderator", email = "mod@example.test", roles = setOf(AppRole.MODERATOR)
            )
        )
        val expectedAllowedIds = setOf(allowed.id!!)
        val expectedAllPlayers = setOf("Allowed", "Denied")
        val expectedModeratorPlayers = listOf("Allowed")
        val expectedQueuedStatus = JobStatus.QUEUED
        val expectedCancelledStatus = JobStatus.CANCELLED
        val expectedMaxCapacity = 2
        val expectedRemainingCapacity = 1
        val expectedFullCapacity = 0
        val expectedMatchCount = 2
        val expectedQueuedCount = 2
        admin.updateModeratorConstraints(moderator.id.toString(), listOf(allowed.id.toString()), 2)
        assertEquals(expectedAllowedIds, users.findById(moderator.id!!).get().moderatorConstraints?.allowedPlayerIds)
        assertEquals(expectedAllPlayers, admin.listUsers().players.map { it.playerName }.toSet())
        val auth = authentication("mod", "ROLE_MODERATOR")
        val deniedJob = jobs.insert(
            ImportJob(
                playerName = denied.playerName, accountId = denied.accountId, jobType = JobType.FETCH_MATCH_STATS
            )
        )
        users.insert(
            AppUser(
                sub = "admin", username = "Admin", email = "admin@example.test", roles = setOf(AppRole.ADMIN)
            )
        )
        val adminView = jobViews.list(authentication("admin", "ROLE_ADMIN"))
        val expectedAdminJobs = listOf(deniedJob.id)
        assertTrue(adminView.canManagePlayers)
        assertEquals(expectedAllPlayers, adminView.players.map { it.playerName }.toSet())
        assertEquals(expectedAdminJobs, adminView.fetchMatchStatsJobs.map { it.id })
        assertNull(adminView.moderatorMatchCountCap)
        assertEquals(expectedModeratorPlayers, jobViews.list(auth).players.map { it.playerName })
        assertTrue(jobViews.list(auth).fetchMatchStatsJobs.isEmpty())
        assertEquals(expectedMaxCapacity, jobViews.list(auth).moderatorMatchCountCap)
        assertNotNull(jobViews.updatePlayer(denied.id!!, 1, auth).error)
        assertNotNull(jobViews.cancel(deniedJob.id.toString(), auth).error)
        assertNotNull(jobViews.retry(deniedJob.id!!, auth).error)
        assertEquals(expectedQueuedStatus, jobs.findById(deniedJob.id!!).get().status)
        assertNull(jobViews.updatePlayer(allowed.id!!, 5, auth).error)
        val queued = jobs.findAll().single { it.jobType == JobType.FETCH_PLAYER_MATCHES }
        assertEquals(expectedMatchCount, queued.matchCount)
        assertEquals(expectedRemainingCapacity, jobViews.list(auth).moderatorMatchCountCap)
        assertNull(jobViews.updatePlayer(allowed.id, 1, auth).error)
        assertEquals(expectedFullCapacity, jobViews.list(auth).moderatorMatchCountCap)
        assertTrue(jobViews.updatePlayer(allowed.id, 1, auth).error!!.contains("Queue limit reached"))
        assertNull(jobViews.cancel(queued.id.toString(), auth).error)
        assertEquals(expectedCancelledStatus, jobs.findById(queued.id!!).get().status)
        assertNull(jobViews.retry(queued.id, auth).error)
        assertEquals(
            expectedQueuedCount, jobs.findAll().count { it.accountId == "account.a" && it.status == JobStatus.QUEUED })
        assertTrue(jobs.findById(queued.id).get().retried)
    }

    @Test
    fun `admin can manage players while moderator is rejected at method security boundary`() {
        val moderator = authentication("mod", "ROLE_MODERATOR")
        val administrator = authentication("admin", "ROLE_ADMIN")
        try {
            SecurityContextHolder.getContext().authentication = moderator
            assertFailsWith<AccessDeniedException> { jobController.addPlayer("A") }
            assertFalse(players.findAll().any { it.playerName == "A" })
            SecurityContextHolder.getContext().authentication = administrator
            jobController.addPlayer("A")
            assertNotNull(players.findByPlayerName("A"))
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun authentication(sub: String, role: String = "ROLE_USER"): Authentication {
        val authorities = listOf(SimpleGrantedAuthority(role))
        val token = OidcIdToken("token", Instant.EPOCH, Instant.parse("2100-01-01T00:00:00Z"), mapOf("sub" to sub))
        val principal = DefaultOidcUser(authorities, token)
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities)
    }
}
