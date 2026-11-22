package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface LifetimeStatsSnapshotRepository : MongoRepository<LifetimeStatsSnapshot, ObjectId> {
    fun findFirstByAccountIdOrderByCapturedAtDescIdAsc(accountId: String): LifetimeStatsSnapshot?

    fun findFirstByAccountIdAndCapturedAtLessThanEqualOrderByCapturedAtDescIdAsc(
        accountId: String, capturedAt: Instant
    ): LifetimeStatsSnapshot?

    fun findFirstByAccountIdAndCapturedAtGreaterThanOrderByCapturedAtAscIdAsc(
        accountId: String, capturedAt: Instant
    ): LifetimeStatsSnapshot?
}
