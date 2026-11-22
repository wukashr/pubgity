package org.traanite.pubgity.view

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.traanite.pubgity.import.ImportJob
import org.traanite.pubgity.import.JobService
import org.traanite.pubgity.import.JobType
import org.traanite.pubgity.player.Player
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.user.AppRole
import org.traanite.pubgity.user.AppUserService

@Service
class JobsViewService(
    private val players: PlayerService,
    private val jobs: JobService,
    private val users: AppUserService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JobsViewService::class.java)
    }

    fun list(auth: Authentication): JobsView {
        val user = users.findByAuth(auth)
        val (visiblePlayers, visibleJobs) = when {
            user?.hasRole(AppRole.ADMIN) == true -> players.findAll() to jobs.getJobs()
            user?.hasRole(AppRole.MODERATOR) == true -> {
                val allowedPlayers = (user.moderatorConstraints?.allowedPlayerIds ?: emptySet())
                    .mapNotNull { players.findById(it) }
                val accountIds = allowedPlayers.mapNotNull { it.accountId }.toSet()
                allowedPlayers to if (accountIds.isNotEmpty()) jobs.getJobsForAccountIds(accountIds) else emptyList()
            }
            else -> players.findAll() to jobs.getJobs()
        }
        val isAdmin = user?.hasRole(AppRole.ADMIN) == true
        val capacity = if (!isAdmin && user?.hasRole(AppRole.MODERATOR) == true) {
            user.moderatorConstraints?.let { jobs.moderatorRemainingCapacity(it.allowedPlayerIds, it.maxQueueSize) } ?: 0
        } else null
        logger.debug("Jobs page: roles={}, {} players, {} jobs", user?.roles, visiblePlayers.size, visibleJobs.size)
        return JobsView(
            visiblePlayers,
            visibleJobs.filter { it.jobType == JobType.FETCH_MATCH_STATS },
            visibleJobs.filter { it.jobType == JobType.FETCH_PLAYER_MATCHES },
            isAdmin,
            capacity
        )
    }

    fun retry(jobId: ObjectId, auth: Authentication): JobActionResult {
        if (!canAccess(jobId, auth)) return JobActionResult("You are not allowed to retry this job.")
        jobs.retryJob(jobId)
        return JobActionResult()
    }

    fun cancel(id: String, auth: Authentication): JobActionResult {
        val jobId = ObjectId(id)
        if (!canAccess(jobId, auth)) return JobActionResult("You are not allowed to cancel this job.")
        jobs.cancelJob(jobId)
        return JobActionResult()
    }

    fun addPlayer(name: String) {
        players.addPlayer(name)
    }

    fun removePlayer(id: String) {
        players.removePlayer(ObjectId(id))
    }

    fun updatePlayer(id: ObjectId, matchCount: Int, auth: Authentication): JobActionResult {
        val user = users.findByAuth(auth)
        if (user?.hasRole(AppRole.MODERATOR) == true && !user.hasRole(AppRole.ADMIN)) {
            val constraints = user.moderatorConstraints
            if (constraints == null || !constraints.allowedPlayerIds.contains(id)) {
                return JobActionResult("You are not allowed to queue jobs for this player.")
            }
            val remaining = jobs.moderatorRemainingCapacity(constraints.allowedPlayerIds, constraints.maxQueueSize)
            if (remaining <= 0) {
                return JobActionResult(
                    "Queue limit reached (max ${constraints.maxQueueSize} active jobs). Wait for existing jobs to complete."
                )
            }
            val effectiveCount = matchCount.coerceIn(1, remaining)
            if (effectiveCount < matchCount) {
                logger.info(
                    "Coerced matchCount from {} to {} for moderator sub={} (remaining capacity={})",
                    matchCount, effectiveCount, user.sub, remaining
                )
            }
            jobs.queueJob(id, effectiveCount)
            return JobActionResult()
        }
        jobs.queueJob(id, matchCount)
        return JobActionResult()
    }

    private fun canAccess(jobId: ObjectId, auth: Authentication): Boolean {
        val user = users.findByAuth(auth) ?: return false
        if (user.hasRole(AppRole.ADMIN)) return true
        val allowedIds = user.moderatorConstraints?.allowedPlayerIds ?: return false
        val accountIds = allowedIds.mapNotNull { players.findById(it)?.accountId }.toSet()
        val job = jobs.findJobById(jobId) ?: return false
        return job.accountId != null && job.accountId in accountIds
    }
}

data class JobsView(
    val players: List<Player>,
    val fetchMatchStatsJobs: List<ImportJob>,
    val fetchPlayerMatchesJobs: List<ImportJob>,
    val canManagePlayers: Boolean,
    val moderatorMatchCountCap: Int?
)

data class JobActionResult(val error: String? = null)
