package org.traanite.pubgity.player

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.pubgapi.PubgApiClient
import org.traanite.pubgity.pubgapi.PubgApiNotFoundException
import org.traanite.pubgity.pubgapi.toLifetimeStats
import java.time.Duration
import java.time.Instant

@Service
class PlayerStatsUpdater(
    private val pubgApiClient: PubgApiClient,
    private val playerProperties: PlayerProperties,
    private val playerRepository: PlayerRepository,
    private val playerResolver: PlayerResolver,
    private val snapshots: StatsSnapshotService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerStatsUpdater::class.java)
    }

    fun updateLifetimeStats(accountId: String, playerName: String): StatsUpdateResult {
        val player =
            playerRepository.findByAccountId(accountId) ?: Player(accountId = accountId, playerName = playerName)

        if (player.isPrivacyProtected) {
            logger.debug(
                "Skipping lifetime stats update for '{}' - player is privacy protected",
                player.playerName
            )
            return StatsUpdateResult.PRIVACY_PROTECTED
        }

        logger.info("Updating lifetime stats for player '{}'", player.playerName)
        val latestSnapshot = snapshots.latestLifetime(accountId)

        if (latestSnapshot != null && isWithinCacheThreshold(latestSnapshot.capturedAt)) {
            logger.debug(
                "Skipping lifetime stats update for '{}' - latest snapshot captured at {} is within cache threshold",
                player.playerName,
                latestSnapshot.capturedAt
            )
            if (player.id == null) playerRepository.save(player)
            return StatsUpdateResult.SKIPPED
        }

        try {
            val stats = pubgApiClient.getLifetimeStats(accountId).data.attributes.toLifetimeStats()
            if (player.id == null) playerRepository.save(player)
            snapshots.insertLifetime(accountId, stats)
            logger.info("Lifetime stats updated: {}", accountId)
            return StatsUpdateResult.UPDATED
        } catch (e: PubgApiNotFoundException) {
            return resolveNotFoundFallback(accountId, playerName, e)
        } catch (e: Exception) {
            logger.warn("Failed to fetch lifetime stats for {}: {}", accountId, e.message)
            return StatsUpdateResult.FAILED
        }
    }

    fun updateSeasonPlayerStats(accountId: String, playerName: String, seasonId: String): StatsUpdateResult {
        val player =
            playerRepository.findByAccountId(accountId) ?: Player(accountId = accountId, playerName = playerName)

        if (player.isPrivacyProtected) {
            logger.debug(
                "Skipping season stats update for '{}' - player is privacy protected",
                player.playerName
            )
            return StatsUpdateResult.PRIVACY_PROTECTED
        }

        try {
            val latestSnapshot = snapshots.latestSeason(accountId, seasonId)
            if (latestSnapshot != null && isWithinCacheThreshold(latestSnapshot.capturedAt)) {
                logger.info(
                    "Skipping season stats update for '{}' - latest snapshot for season {} is within cache threshold",
                    player.playerName,
                    seasonId
                )
                if (player.id == null) playerRepository.save(player)
                return StatsUpdateResult.SKIPPED
            }

            val playerSeasonStats = pubgApiClient.getSeasonStats(seasonId, accountId)
            check(playerSeasonStats.seasonId == seasonId) { "Unexpected season ${playerSeasonStats.seasonId} for $seasonId" }
            if (player.id == null) playerRepository.save(player)
            snapshots.insertSeason(accountId, playerSeasonStats)
            logger.info("Season stats updated: {}", accountId)
            return StatsUpdateResult.UPDATED
        } catch (e: PubgApiNotFoundException) {
            return resolveNotFoundFallback(accountId, playerName, e)
        } catch (e: Exception) {
            logger.warn("Failed to fetch season stats for {}: {}", accountId, e.message)
            return StatsUpdateResult.FAILED
        }
    }

    private fun resolveNotFoundFallback(
        accountId: String,
        playerName: String,
        cause: PubgApiNotFoundException
    ): StatsUpdateResult {
        logger.info("Stats fetch 404 for accountId={}, resolving player to check privacy status", accountId)
        val resolved = playerResolver.resolve(accountId, playerName)
        if (resolved.isPrivacyProtected) {
            logger.debug("Confirmed '{}' is privacy protected after resolving on 404", resolved.playerName)
            return StatsUpdateResult.PRIVACY_PROTECTED
        }
        throw cause
    }

    // todo split these ttl in properties
    private fun isWithinCacheThreshold(capturedAt: Instant): Boolean {
        return Duration.between(capturedAt, Instant.now()) < playerProperties.statsTtl
    }
}

enum class StatsUpdateResult {
    UPDATED, SKIPPED, FAILED, PRIVACY_PROTECTED
}