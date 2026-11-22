package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "players")
class Player(
    @Id
    val id: ObjectId? = null,
    @Indexed(unique = true)
    val playerName: String,
    @Indexed(unique = true, sparse = true)
    val accountId: String? = null,
    val matches: PlayerMatches = PlayerMatches(),
    val isPrivacyProtected: Boolean = false,
) {

    fun updateAccountData(accountId: String, playerName: String, isPrivacyProtected: Boolean = this.isPrivacyProtected): Player {
        return Player(
            id = id,
            playerName = playerName,
            accountId = accountId,
            matches = matches,
            isPrivacyProtected = isPrivacyProtected,
        )
    }

    fun addMatches(matchIds: Collection<String>): Player {
        return Player(
            id = id,
            playerName = playerName,
            accountId = accountId,
            matches = PlayerMatches(matches.matchIds + matchIds),
            isPrivacyProtected = isPrivacyProtected,
        )
    }

    fun removeMatches(matchIds: Collection<String>): Player {
        val matchIdsToRemove = matchIds.toSet()

        return Player(
            id = id,
            playerName = playerName,
            accountId = accountId,
            matches = PlayerMatches(matches.matchIds.filterNot { it in matchIdsToRemove }.toSet()),
            isPrivacyProtected = isPrivacyProtected,
        )
    }

    fun withPrivacyProtected(isPrivacyProtected: Boolean): Player {
        return Player(
            id = id,
            playerName = playerName,
            accountId = accountId,
            matches = matches,
            isPrivacyProtected = isPrivacyProtected,
        )
    }

}

data class PlayerMatches(
    val matchIds: Set<String> = emptySet(),
)


