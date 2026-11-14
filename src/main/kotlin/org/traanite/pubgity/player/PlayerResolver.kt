package org.traanite.pubgity.player

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.pubgapi.PubgApiClient

@Service
class PlayerResolver(
    private val pubgApiClient: PubgApiClient, private val playerRepository: PlayerRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(PlayerResolver::class.java)
    }

    fun resolve(accountId: String?, playerName: String): ResolvedPlayer {
        val playerData = if (accountId != null) {
            pubgApiClient.getPlayerByAccountId(accountId)
        } else {
            pubgApiClient.getPlayerByName(playerName)
        }

        val accountId = playerData.id
        val matchIds = playerData.relationships?.matches?.data?.map { it.id } ?: emptyList()
        val name = playerData.attributes.name
        val isPrivacyProtected = playerData.attributes.isPrivacyProtected

        logger.info(
            "Resolved '{}' -> accountId={}, {} matches from API, isPrivacyProtected={}",
            name, accountId, matchIds.size, isPrivacyProtected
        )

        val resolvedPlayer = resolveOrCreatePlayer(accountId, name, playerName, isPrivacyProtected)
        return ResolvedPlayer(
            resolvedPlayer.accountId!!,
            resolvedPlayer.playerName,
            matchIds,
            resolvedPlayer.isPrivacyProtected
        )
    }

    private fun resolveOrCreatePlayer(
        accountId: String,
        currentName: String,
        originalName: String,
        isPrivacyProtected: Boolean = false
    ): Player {
        val player = playerRepository.findByAccountId(accountId)
            ?: playerRepository.findByPlayerName(originalName)
            ?: Player(playerName = currentName)

        val updatedPlayer = player.updateAccountData(accountId, currentName, isPrivacyProtected)
        playerRepository.save(updatedPlayer)
        logger.info("Resolved or created player: accountId={}, name='{}'", updatedPlayer.accountId, updatedPlayer.playerName)
        return updatedPlayer
    }
}

data class ResolvedPlayer(
    val accountId: String, val playerName: String, val matchIds: List<String>, val isPrivacyProtected: Boolean = false
)
