package com.mahdi.platen.domain

/** Business rules for how many notes a writer may keep. Checked in the domain, not the UI. */
object NoteLimits {
    const val FREE_ACTIVE_NOTES = 3

    fun maxNotes(studio: Boolean): Int = if (studio) Int.MAX_VALUE else FREE_ACTIVE_NOTES

    fun canCreate(existing: Int, studio: Boolean): Boolean = existing < maxNotes(studio)

    fun remaining(existing: Int, studio: Boolean): Int =
        if (studio) Int.MAX_VALUE else (FREE_ACTIVE_NOTES - existing).coerceAtLeast(0)
}

sealed interface CreateResult {
    data class Created(val id: Long) : CreateResult
    data object LimitReached : CreateResult
}
