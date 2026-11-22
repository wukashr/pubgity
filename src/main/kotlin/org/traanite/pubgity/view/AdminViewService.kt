package org.traanite.pubgity.view

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.player.Player
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.user.AppUser
import org.traanite.pubgity.user.AppUserService

@Service
class AdminViewService(private val users: AppUserService, private val players: PlayerService) {
    companion object {
        private val logger = LoggerFactory.getLogger(AdminViewService::class.java)
    }

    fun listUsers() = AdminUsersView(users.findAll(), players.findAll())

    fun updateModeratorConstraints(id: String, allowedPlayerIds: List<String>?, maxQueueSize: Int) {
        val playerIds = allowedPlayerIds?.map { ObjectId(it) }?.toSet() ?: emptySet()
        users.updateModeratorConstraints(ObjectId(id), playerIds, maxQueueSize)
        logger.info("Admin updated moderator constraints for user {}: players={}, maxQueue={}", id, playerIds.size, maxQueueSize)
    }
}

data class AdminUsersView(val users: List<AppUser>, val players: List<Player>)
