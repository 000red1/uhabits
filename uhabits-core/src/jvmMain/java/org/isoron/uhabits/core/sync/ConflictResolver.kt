/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.sync

import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.Reminder
import org.isoron.uhabits.core.models.Timestamp
import org.isoron.uhabits.core.models.WeekdayList

/**
 * Resolves conflicts between local and remote data using "last-modified wins" strategy.
 * When timestamps are equal, the server (remote) version wins as the tie-breaker.
 */
class ConflictResolver {

    /**
     * Determines which habit version should be kept based on modification timestamps.
     *
     * @param local The local habit data with its modification timestamp
     * @param remote The remote habit data from the server
     * @return ResolvedHabit indicating which version to keep and the data to use
     */
    fun resolveHabitConflict(
        local: Habit,
        localModifiedAt: Long,
        remote: SyncHabitData
    ): ResolvedHabit {
        return if (remote.modifiedAt >= localModifiedAt) {
            // Remote wins (also handles ties)
            ResolvedHabit(useRemote = true, data = remote)
        } else {
            // Local wins
            ResolvedHabit(useRemote = false, data = null)
        }
    }

    /**
     * Determines which entry version should be kept based on modification timestamps.
     *
     * @param local The local entry
     * @param localModifiedAt The modification timestamp of the local entry
     * @param remote The remote entry data from the server
     * @return ResolvedEntry indicating which version to keep and the data to use
     */
    fun resolveEntryConflict(
        local: Entry,
        localModifiedAt: Long,
        remote: SyncEntryData
    ): ResolvedEntry {
        return if (remote.modifiedAt >= localModifiedAt) {
            // Remote wins (also handles ties)
            ResolvedEntry(useRemote = true, data = remote)
        } else {
            // Local wins
            ResolvedEntry(useRemote = false, data = null)
        }
    }

    /**
     * Applies remote habit data to a local habit model.
     *
     * @param habit The habit to update
     * @param remote The remote data to apply
     */
    fun applyRemoteHabitData(habit: Habit, remote: SyncHabitData) {
        habit.name = remote.name
        habit.description = remote.description
        habit.question = remote.question
        habit.frequency = Frequency(remote.frequencyNumerator, remote.frequencyDenominator)
        habit.color = PaletteColor(remote.color)
        habit.position = remote.position
        habit.isArchived = remote.isArchived
        habit.type = HabitType.fromInt(remote.type)
        habit.targetType = NumericalHabitType.fromInt(remote.targetType)
        habit.targetValue = remote.targetValue
        habit.unit = remote.unit

        if (remote.reminderHour != null && remote.reminderMinute != null) {
            habit.reminder = Reminder(
                remote.reminderHour,
                remote.reminderMinute,
                WeekdayList(remote.reminderDays ?: 0)
            )
        } else {
            habit.reminder = null
        }
    }

    /**
     * Creates a new Entry from remote sync data.
     *
     * @param remote The remote entry data
     * @return A new Entry instance
     */
    fun createEntryFromRemote(remote: SyncEntryData): Entry {
        return Entry(
            timestamp = Timestamp(remote.timestamp),
            value = remote.value,
            notes = remote.notes
        )
    }

    /**
     * Converts a local Habit to SyncHabitData for upload.
     *
     * @param habit The habit to convert
     * @param modifiedAt The modification timestamp
     * @return SyncHabitData ready for upload
     */
    fun habitToSyncData(habit: Habit, modifiedAt: Long): SyncHabitData {
        return SyncHabitData(
            uuid = habit.uuid ?: throw IllegalStateException("Habit must have UUID"),
            name = habit.name,
            description = habit.description,
            question = habit.question,
            frequencyNumerator = habit.frequency.numerator,
            frequencyDenominator = habit.frequency.denominator,
            color = habit.color.paletteIndex,
            position = habit.position,
            isArchived = habit.isArchived,
            type = habit.type.value,
            targetType = habit.targetType.value,
            targetValue = habit.targetValue,
            unit = habit.unit,
            reminderHour = habit.reminder?.hour,
            reminderMinute = habit.reminder?.minute,
            reminderDays = habit.reminder?.days?.toInteger(),
            modifiedAt = modifiedAt,
            deletedAt = null
        )
    }

    /**
     * Converts a local Entry to SyncEntryData for upload.
     *
     * @param habitUuid The UUID of the habit this entry belongs to
     * @param entry The entry to convert
     * @param modifiedAt The modification timestamp
     * @return SyncEntryData ready for upload
     */
    fun entryToSyncData(habitUuid: String, entry: Entry, modifiedAt: Long): SyncEntryData {
        return SyncEntryData(
            habitUuid = habitUuid,
            timestamp = entry.timestamp.unixTime,
            value = entry.value,
            notes = entry.notes,
            modifiedAt = modifiedAt
        )
    }

    data class ResolvedHabit(
        val useRemote: Boolean,
        val data: SyncHabitData?
    )

    data class ResolvedEntry(
        val useRemote: Boolean,
        val data: SyncEntryData?
    )
}
