package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.springframework.stereotype.Service
import org.traanite.pubgity.match.Match
import java.time.Duration
import java.time.Instant

@Service
class StatsSnapshotService(
    private val lifetimeRepository: LifetimeStatsSnapshotRepository,
    private val seasonRepository: SeasonStatsSnapshotRepository
) {
    fun insertLifetime(accountId: String, stats: LifetimeStats): LifetimeStatsSnapshot =
        lifetimeRepository.insert(LifetimeStatsSnapshot(accountId = accountId, stats = stats))

    fun insertSeason(accountId: String, stats: SeasonStats): SeasonStatsSnapshot =
        seasonRepository.insert(SeasonStatsSnapshot(accountId = accountId, capturedAt = stats.capturedAt, stats = stats))

    fun latestLifetime(accountId: String): LifetimeStatsSnapshot? =
        lifetimeRepository.findFirstByAccountIdOrderByCapturedAtDescIdAsc(accountId)

    fun latestSeason(accountId: String, seasonId: String? = null): SeasonStatsSnapshot? {
        return if (seasonId == null) seasonRepository.findFirstByAccountIdOrderByCapturedAtDescIdAsc(accountId)
        else seasonRepository.findFirstByAccountIdAndStats_SeasonIdOrderByCapturedAtDescIdAsc(accountId, seasonId)
    }

    fun closestLifetime(accountId: String, at: Instant): LifetimeStatsSnapshot? =
        closest(
            lifetimeRepository.findFirstByAccountIdAndCapturedAtLessThanEqualOrderByCapturedAtDescIdAsc(accountId, at),
            lifetimeRepository.findFirstByAccountIdAndCapturedAtGreaterThanOrderByCapturedAtAscIdAsc(accountId, at),
            at
        ) { it.capturedAt to it.id }

    fun closestSeason(accountId: String, seasonId: String, at: Instant): SeasonStatsSnapshot? =
        closest(
            seasonRepository.findFirstByAccountIdAndStats_SeasonIdAndCapturedAtLessThanEqualOrderByCapturedAtDescIdAsc(accountId, seasonId, at),
            seasonRepository.findFirstByAccountIdAndStats_SeasonIdAndCapturedAtGreaterThanOrderByCapturedAtAscIdAsc(accountId, seasonId, at),
            at
        ) { it.capturedAt to it.id }

    private fun <T : Any> closest(
        before: T?, after: T?, at: Instant, key: (T) -> Pair<Instant, ObjectId>
    ): T? {
        return listOfNotNull(before, after).minWithOrNull(
            compareBy<T> { Duration.between(key(it).first, at).abs() }
                .thenBy { key(it).first }.thenBy { key(it).second }
        )
    }

    fun loadForMatches(
        matches: Collection<Match>, includeLifetime: Boolean = true, includeSeason: Boolean = true
    ): MatchStatsSnapshots {
        val participants = matches.flatMap { it.rosters }.flatMap { it.participants }
        val lifetimeIds = if (includeLifetime) participants.mapNotNull { it.lifetimeStatsSnapshotId }.toSet() else emptySet()
        val seasonIds = if (includeSeason) participants.mapNotNull { it.seasonStatsSnapshotId }.toSet() else emptySet()
        val lifetime = load(lifetimeIds, "LifetimeStatsSnapshot", lifetimeRepository::findAllById) { it.id }
        val season = load(seasonIds, "SeasonStatsSnapshot", seasonRepository::findAllById) { it.id }
        participants.forEach { participant ->
            participant.lifetimeStatsSnapshotId?.takeIf { includeLifetime }?.let { id ->
                check(lifetime.getValue(id).accountId == participant.accountId) { "Lifetime snapshot $id belongs to another player" }
            }
            participant.seasonStatsSnapshotId?.takeIf { includeSeason }?.let { id ->
                check(season.getValue(id).accountId == participant.accountId) { "Season snapshot $id belongs to another player" }
            }
        }
        matches.forEach { match ->
            match.rosters.flatMap { it.participants }.forEach { participant ->
                participant.seasonStatsSnapshotId?.takeIf { includeSeason }?.let { id ->
                    check(season.getValue(id).stats.seasonId == match.seasonId) { "Season snapshot $id does not match ${match.matchId}" }
                }
            }
        }
        return MatchStatsSnapshots(lifetime, season)
    }

    private fun <T : Any> load(
        ids: Set<ObjectId>, type: String, findAll: (Iterable<ObjectId>) -> Iterable<T>, id: (T) -> ObjectId
    ): Map<ObjectId, T> {
        val found = ids.chunked(500).flatMap { findAll(it).toList() }.associateBy(id)
        check(found.keys.containsAll(ids)) { "Missing $type references: ${ids - found.keys}" }
        return found
    }
}

data class MatchStatsSnapshots(
    val lifetime: Map<ObjectId, LifetimeStatsSnapshot>,
    val season: Map<ObjectId, SeasonStatsSnapshot>
)