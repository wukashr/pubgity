package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.traanite.pubgity.player.Player
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.user.AppUser
import org.traanite.pubgity.user.AppUserService

@Service
class ProfileViewService(
    private val users: AppUserService,
    private val players: PlayerService,
    @Value("\${spring.security.oauth2.client.provider.oidc.issuer-uri}") private val issuerUri: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ProfileViewService::class.java)
    }

    fun profile(auth: Authentication): ProfileView? {
        val user = users.findByAuth(auth) ?: return null
        return ProfileView(user, user.linkedPlayerId?.let { players.findById(it) }, "$issuerUri/account")
    }

    fun updateUsername(auth: Authentication, username: String): ProfileActionResult {
        val user = users.findByAuth(auth) ?: return ProfileActionResult.NO_USER
        return try {
            users.updateUsername(user.id!!, username)
            ProfileActionResult.SUCCESS
        } catch (_: IllegalArgumentException) {
            ProfileActionResult.INVALID_USERNAME
        }
    }

    fun linkPlayer(auth: Authentication, playerName: String): ProfileActionResult {
        val user = users.findByAuth(auth) ?: return ProfileActionResult.NO_USER
        val player = players.findByPlayerName(playerName.trim()) ?: return ProfileActionResult.PLAYER_NOT_FOUND
        users.linkPlayer(user.id!!, player.id!!)
        logger.info("User {} linked to player '{}'", user.sub, playerName)
        return ProfileActionResult.SUCCESS
    }

    fun unlinkPlayer(auth: Authentication): ProfileActionResult {
        val user = users.findByAuth(auth) ?: return ProfileActionResult.NO_USER
        users.unlinkPlayer(user.id!!)
        return ProfileActionResult.SUCCESS
    }
}

data class ProfileView(val appUser: AppUser, val linkedPlayer: Player?, val oidcAccountUrl: String)

enum class ProfileActionResult { SUCCESS, NO_USER, INVALID_USERNAME, PLAYER_NOT_FOUND }
