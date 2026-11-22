package org.traanite.pubgity.view

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.user.AppUserService

@Service
class HomeViewService(
    private val users: AppUserService,
    private val players: PlayerService,
    private val stats: PlayerStatsViewService
) {
    fun home(auth: Authentication?): HomeView {
        if (auth == null || !auth.isAuthenticated || auth is AnonymousAuthenticationToken) return HomeView()
        val user = users.findByAuth(auth) ?: return HomeView()
        val player = user.linkedPlayerId?.let { players.findById(it) } ?: return HomeView(noLinkedPlayer = true)
        return HomeView(detail = stats.detail(player))
    }
}

data class HomeView(val noLinkedPlayer: Boolean = false, val detail: PlayerDetailStatsView? = null)
