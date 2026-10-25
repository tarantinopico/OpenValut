package com.openvault.app.sync

import com.openvault.app.domain.model.ConflictResolutionStrategy
import com.openvault.app.domain.model.Note
import com.openvault.app.domain.model.NoteRevision

enum class ConflictWinner {
    LOCAL,
    REMOTE
}

data class ConflictResolutionOutcome<T>(
    val winningItem: T,
    val winner: ConflictWinner,
    val hadCollision: Boolean,
    val collisionSummary: String? = null
)

object ConflictResolver {

    /**
     * Evaluates Last-Write-Wins (or user-configured strategy) between a local item and a remote item.
     * Compare `rev` first; if `rev` is equal, compare `updatedAt`.
     */
    fun determineWinner(
        localRev: Long,
        localUpdatedAt: Long,
        remoteRev: Long,
        remoteUpdatedAt: Long,
        hasLocalUnpushedChanges: Boolean,
        strategy: ConflictResolutionStrategy = ConflictResolutionStrategy.LAST_WRITE_WINS_WITH_HISTORY
    ): ConflictWinner {
        if (!hasLocalUnpushedChanges) {
            return if (remoteRev > localRev || (remoteRev == localRev && remoteUpdatedAt >= localUpdatedAt)) {
                ConflictWinner.REMOTE
            } else {
                ConflictWinner.LOCAL
            }
        }

        return when (strategy) {
            ConflictResolutionStrategy.PREFER_LOCAL -> ConflictWinner.LOCAL
            ConflictResolutionStrategy.PREFER_REMOTE -> ConflictWinner.REMOTE
            ConflictResolutionStrategy.LAST_WRITE_WINS_WITH_HISTORY -> {
                when {
                    remoteRev > localRev -> ConflictWinner.REMOTE
                    remoteRev < localRev -> ConflictWinner.LOCAL
                    remoteUpdatedAt > localUpdatedAt -> ConflictWinner.REMOTE
                    else -> ConflictWinner.LOCAL
                }
            }
        }
    }

    /**
     * Resolves a collision between a local Note and a remote Note, preserving the overwritten
     * version inside `history` (`List<NoteRevision>`) so zero user content is ever lost.
     */
    fun resolveNoteConflict(
        localNote: Note,
        remoteNote: Note,
        hasLocalUnpushedChanges: Boolean,
        strategy: ConflictResolutionStrategy = ConflictResolutionStrategy.LAST_WRITE_WINS_WITH_HISTORY
    ): ConflictResolutionOutcome<Note> {
        val winner = determineWinner(
            localRev = localNote.rev,
            localUpdatedAt = localNote.updatedAt,
            remoteRev = remoteNote.rev,
            remoteUpdatedAt = remoteNote.updatedAt,
            hasLocalUnpushedChanges = hasLocalUnpushedChanges,
            strategy = strategy
        )

        if (!hasLocalUnpushedChanges) {
            val chosen = if (winner == ConflictWinner.REMOTE) remoteNote else localNote
            return ConflictResolutionOutcome(
                winningItem = chosen,
                winner = winner,
                hadCollision = false
            )
        }

        val winningNote = if (winner == ConflictWinner.REMOTE) remoteNote else localNote
        val losingNote = if (winner == ConflictWinner.REMOTE) localNote else remoteNote

        val archivedRevision = NoteRevision(
            rev = losingNote.rev,
            title = "${losingNote.title} (Konfliktní revize)",
            body = losingNote.body,
            timestamp = losingNote.updatedAt
        )
        val mergedHistory = (winningNote.history + archivedRevision)
            .distinctBy { "${it.rev}:${it.timestamp}" }
            .sortedByDescending { it.timestamp }
            .take(30)

        val mergedNote = winningNote.copy(
            history = mergedHistory,
            rev = maxOf(localNote.rev, remoteNote.rev) + 1L
        )
        return ConflictResolutionOutcome(
            winningItem = mergedNote,
            winner = winner,
            hadCollision = true,
            collisionSummary = "Vyřešena kolize poznámky '${winningNote.title}' (vítěz: ${winner.name}, revize ${losingNote.rev} uložena do historie)"
        )
    }
}
