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

/**
 * Data classes for sync API request and response.
 */

/**
 * Request payload sent to the sync server.
 */
data class SyncRequest(
    val clientId: String,
    val lastSyncTimestamp: Long,
    val habits: List<SyncHabitData>,
    val entries: List<SyncEntryData>
)

/**
 * Response payload received from the sync server.
 */
data class SyncResponse(
    val serverTimestamp: Long,
    val habits: List<SyncHabitData>,
    val entries: List<SyncEntryData>,
    val deletedHabitUuids: List<String>,
    val deletedEntries: List<DeletedEntryRef>
)

/**
 * Habit data structure for sync operations.
 */
data class SyncHabitData(
    val uuid: String,
    val name: String,
    val description: String,
    val question: String,
    val frequencyNumerator: Int,
    val frequencyDenominator: Int,
    val color: Int,
    val position: Int,
    val isArchived: Boolean,
    val type: Int,
    val targetType: Int,
    val targetValue: Double,
    val unit: String,
    val reminderHour: Int?,
    val reminderMinute: Int?,
    val reminderDays: Int?,
    val modifiedAt: Long,
    val deletedAt: Long?
)

/**
 * Entry/checkmark data structure for sync operations.
 */
data class SyncEntryData(
    val habitUuid: String,
    val timestamp: Long,
    val value: Int,
    val notes: String,
    val modifiedAt: Long
)

/**
 * Reference to a deleted entry (for sync deletion propagation).
 */
data class DeletedEntryRef(
    val habitUuid: String,
    val timestamp: Long
)
