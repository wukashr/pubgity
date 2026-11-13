package org.traanite.pubgity

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.traanite.pubgity.config.TestSecurityConfig
import org.traanite.pubgity.import.ImportJobRepository
import org.traanite.pubgity.match.MatchRepository
import org.traanite.pubgity.player.PlayerRepository
import org.traanite.pubgity.user.AppUserRepository

@SpringBootTest
@Import(TestSecurityConfig::class)
class PubgityApplicationTests {

    // todo: for future tests using repos, add testcontainers
    @MockitoBean
    lateinit var matchRepository: MatchRepository

    @MockitoBean
    lateinit var appUserRepository: AppUserRepository

    @MockitoBean
    lateinit var importJobRepository: ImportJobRepository

    @MockitoBean
    lateinit var playerRepository: PlayerRepository

    @Test
    fun contextLoads() {
    }

}
