package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface SeasonStatsSnapshotRepository : MongoRepository<SeasonStatsSnapshot, ObjectId> {
    fun findFirstByAccountIdOrderByCapturedAtDescIdAsc(accountId: String): SeasonStatsSnapshot?

    fun findFirstByAccountIdAndStats_SeasonIdOrderByCapturedAtDescIdAsc(
        accountId: String, seasonId: String
    ): SeasonStatsSnapshot?

    fun findFirstByAccountIdAndStats_SeasonIdAndCapturedAtLessThanEqualOrderByCapturedAtDescIdAsc(
        accountId: String, seasonId: String, capturedAt: Instant
    ): SeasonStatsSnapshot?

    fun findFirstByAccountIdAndStats_SeasonIdAndCapturedAtGreaterThanOrderByCapturedAtAscIdAsc(
        accountId: String, seasonId: String, capturedAt: Instant
    ): SeasonStatsSnapshot?
}
