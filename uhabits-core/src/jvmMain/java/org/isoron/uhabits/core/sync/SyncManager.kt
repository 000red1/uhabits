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

import org.isoron.uhabits.core.database.Repository
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.ModelFactory
import org.isoron.uhabits.core.models.sqlite.records.EntryRecord
import org.isoron.uhabits.core.models.sqlite.records.HabitRecord
import org.isoron.uhabits.core.preferences.Preferences
import java.util.LinkedList

/**
 * Manages synchronization of habit data with a remote server.
 *
 * The sync manager coordinates:
 * - Collecting local changes since last sync
 * - Sending changes to the server
 * - Applying remote changes using conflict resolution
 * - Updating sync timestamps
 */
class SyncManager(
    private val preferences: Preferences,
    private val habitList: HabitList,
    private val modelFactory: ModelFactory,
    private val syncClient: SyncClient,
    private val conflictResolver: ConflictResolver = ConflictResolver()
) {
    private val habitRepository: Repository<HabitRecord> = modelFactory.buildHabitListRepository()
    private val entryRepository: Repository<EntryRecord> = modelFactory.buildRepetitionListRepository()
    private val listeners: MutableList<Listener> = LinkedList()

    /**
     * Performs a full sync operation.
     *
     * @param networkAvailable Whether network connectivity is available
     * @return The result of the sync operation
     */
    suspend fun sync(networkAvailable: Boolean = true): SyncResult {
        // Check preconditions
        if (!preferences.isSyncEnabled) {
            return SyncResult.Disabled
        }

        val baseUrl = preferences.syncBaseUrl.trim()
        val syncKey = preferences.syncKey

        if (baseUrl.isEmpty() || syncKey.isEmpty()) {
            return SyncResult.NotConfigured
        }

        if (!networkAvailable) {
            return SyncResult.NetworkUnavailable
        }

        notifyListeners { it.onSyncStarted() }

        return try {
            val result = performSync(baseUrl, syncKey)
            notifyListeners { it.onSyncCompleted(result) }
            result
        } catch (e: SyncException.AuthenticationException) {
            val result = SyncResult.AuthenticationFailed
            notifyListeners { it.onSyncCompleted(result) }
            result
        } catch (e: SyncException.ServerException) {
            val result = SyncResult.ServerError(e.statusCode, e.message ?: "Unknown error")
            notifyListeners { it.onSyncCompleted(result) }
            result
        } catch (e: SyncException) {
            val result = SyncResult.Error(e)
            notifyListeners { it.onSyncCompleted(result) }
            result
        } catch (e: Exception) {
            val result = SyncResult.Error(e)
            notifyListeners { it.onSyncCompleted(result) }
            result
        }
    }

    private suspend fun performSync(baseUrl: String, syncKey: String): SyncResult {
        val lastSyncTimestamp = preferences.lastSyncTimestamp
        val clientId = preferences.syncClientId
        val syncMode = preferences.syncMode

        // Collect local changes (skip if download-only mode)
        val localHabits = if (syncMode != SyncMode.DOWNLOAD_ONLY) {
            collectLocalHabits(lastSyncTimestamp)
        } else {
            emptyList()
        }

        val localEntries = if (syncMode != SyncMode.DOWNLOAD_ONLY) {
            collectLocalEntries(lastSyncTimestamp)
        } else {
            emptyList()
        }

        // Build and send request
        val request = SyncRequest(
            clientId = clientId,
            lastSyncTimestamp = lastSyncTimestamp,
            habits = localHabits,
            entries = localEntries
        )

        val response = syncClient.sync(baseUrl, syncKey, request)

        // Check if server requested a full sync (reset and re-upload)
        if (response.forceFullSync && lastSyncTimestamp > 0) {
            // Reset lastSyncTimestamp and perform a full sync
            preferences.lastSyncTimestamp = 0
            return performSync(baseUrl, syncKey)
        }

        // Apply remote changes (skip if upload-only mode)
        val habitsDownloaded = if (syncMode != SyncMode.UPLOAD_ONLY) {
            applyRemoteHabits(response.habits, response.deletedHabitUuids)
        } else {
            0
        }

        val entriesDownloaded = if (syncMode != SyncMode.UPLOAD_ONLY) {
            applyRemoteEntries(response.entries, response.deletedEntries)
        } else {
            0
        }

        // Update last sync timestamp
        preferences.lastSyncTimestamp = response.serverTimestamp

        return SyncResult.Success(
            serverTimestamp = response.serverTimestamp,
            habitsUploaded = localHabits.size,
            habitsDownloaded = habitsDownloaded,
            entriesUploaded = localEntries.size,
            entriesDownloaded = entriesDownloaded
        )
    }

    private fun collectLocalHabits(sinceTimestamp: Long): List<SyncHabitData> {
        val records = if (sinceTimestamp > 0) {
            habitRepository.findAll("where modified_at > ?", sinceTimestamp.toString())
        } else {
            habitRepository.findAll("order by position")
        }

        return records.mapNotNull { record ->
            val uuid = record.uuid ?: return@mapNotNull null
            SyncHabitData(
                uuid = uuid,
                name = record.name ?: "",
                description = record.description ?: "",
                question = record.question ?: "",
                frequencyNumerator = record.freqNum ?: 1,
                frequencyDenominator = record.freqDen ?: 1,
                color = record.color ?: 0,
                position = record.position ?: 0,
                isArchived = (record.archived ?: 0) != 0,
                type = record.type ?: 0,
                targetType = record.targetType ?: 0,
                targetValue = record.targetValue ?: 0.0,
                unit = record.unit ?: "",
                reminderHour = record.reminderHour,
                reminderMinute = record.reminderMin,
                reminderDays = record.reminderDays,
                modifiedAt = record.modifiedAt ?: System.currentTimeMillis(),
                deletedAt = null
            )
        }
    }

    private fun collectLocalEntries(sinceTimestamp: Long): List<SyncEntryData> {
        val records = if (sinceTimestamp > 0) {
            entryRepository.findAll("where modified_at > ?", sinceTimestamp.toString())
        } else {
            entryRepository.findAll("")
        }

        return records.mapNotNull { record ->
            val habitId = record.habitId ?: return@mapNotNull null
            val habitRecord = habitRepository.find(habitId) ?: return@mapNotNull null
            val habitUuid = habitRecord.uuid ?: return@mapNotNull null

            SyncEntryData(
                habitUuid = habitUuid,
                timestamp = record.timestamp ?: return@mapNotNull null,
                value = record.value ?: 0,
                notes = record.notes ?: "",
                modifiedAt = record.modifiedAt ?: System.currentTimeMillis()
            )
        }
    }

    private fun applyRemoteHabits(
        remoteHabits: List<SyncHabitData>,
        deletedUuids: List<String>
    ): Int {
        var applied = 0

        habitRepository.executeAsTransaction {
            // Handle deletions
            for (uuid in deletedUuids) {
                val habit = habitList.getByUUID(uuid)
                if (habit != null) {
                    habitList.remove(habit)
                    applied++
                }
            }

            // Handle updates/additions
            for (remote in remoteHabits) {
                if (remote.deletedAt != null) continue // Skip deleted habits

                val existingHabit = habitList.getByUUID(remote.uuid)

                if (existingHabit == null) {
                    // New habit from server
                    val newHabit = modelFactory.buildHabit()
                    newHabit.uuid = remote.uuid
                    conflictResolver.applyRemoteHabitData(newHabit, remote)
                    habitList.add(newHabit)
                    applied++
                } else {
                    // Existing habit - check conflict
                    val localRecord = habitRepository.find(existingHabit.id!!)
                    val localModifiedAt = localRecord?.modifiedAt ?: 0

                    val resolved = conflictResolver.resolveHabitConflict(
                        existingHabit,
                        localModifiedAt,
                        remote
                    )

                    if (resolved.useRemote && resolved.data != null) {
                        conflictResolver.applyRemoteHabitData(existingHabit, resolved.data)
                        habitList.update(listOf(existingHabit))
                        applied++
                    }
                }
            }
        }

        return applied
    }

    private fun applyRemoteEntries(
        remoteEntries: List<SyncEntryData>,
        deletedEntries: List<DeletedEntryRef>
    ): Int {
        var applied = 0

        habitRepository.executeAsTransaction {
            // Handle deletions
            for (deleted in deletedEntries) {
                val habit = habitList.getByUUID(deleted.habitUuid) ?: continue
                val entries = habit.originalEntries
                // Remove entry at timestamp
                entries.add(conflictResolver.createEntryFromRemote(
                    SyncEntryData(
                        habitUuid = deleted.habitUuid,
                        timestamp = deleted.timestamp,
                        value = 0, // NO value effectively deletes
                        notes = "",
                        modifiedAt = System.currentTimeMillis()
                    )
                ))
                applied++
            }

            // Handle updates/additions
            for (remote in remoteEntries) {
                val habit = habitList.getByUUID(remote.habitUuid) ?: continue

                // Find local entry at this timestamp
                val localEntry = habit.originalEntries.get(
                    org.isoron.uhabits.core.models.Timestamp(remote.timestamp)
                )

                // Look up local modification time from database
                val localModifiedAt = if (localEntry.value != org.isoron.uhabits.core.models.Entry.UNKNOWN) {
                    val records = entryRepository.findAll(
                        "where habit = ? and timestamp = ?",
                        habit.id.toString(),
                        remote.timestamp.toString()
                    )
                    records.firstOrNull()?.modifiedAt ?: 0
                } else {
                    0
                }

                val resolved = conflictResolver.resolveEntryConflict(localEntry, localModifiedAt, remote)

                if (resolved.useRemote && resolved.data != null) {
                    val newEntry = conflictResolver.createEntryFromRemote(resolved.data)
                    habit.originalEntries.add(newEntry)
                    applied++
                }
            }
        }

        return applied
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(action: (Listener) -> Unit) {
        for (listener in listeners) {
            action(listener)
        }
    }

    interface Listener {
        fun onSyncStarted() {}
        fun onSyncCompleted(result: SyncResult) {}
    }
}
