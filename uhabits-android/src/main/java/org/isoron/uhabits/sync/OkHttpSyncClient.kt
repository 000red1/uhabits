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
package org.isoron.uhabits.sync

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.isoron.uhabits.core.sync.DeletedEntryRef
import org.isoron.uhabits.core.sync.SyncClient
import org.isoron.uhabits.core.sync.SyncEntryData
import org.isoron.uhabits.core.sync.SyncException
import org.isoron.uhabits.core.sync.SyncHabitData
import org.isoron.uhabits.core.sync.SyncRequest
import org.isoron.uhabits.core.sync.SyncResponse
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Simple HTTP-based implementation of [SyncClient] using HttpURLConnection.
 */
class OkHttpSyncClient @Inject constructor() : SyncClient {

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override suspend fun sync(
        baseUrl: String,
        syncKey: String,
        request: SyncRequest
    ): SyncResponse = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/api/v1/sync"

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 60_000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $syncKey")
            }

            // Write request body
            val requestJson = objectMapper.writeValueAsString(request)
            connection.outputStream.use { output ->
                output.write(requestJson.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val responseDto = objectMapper.readValue(responseBody, SyncResponseDto::class.java)
                    responseDto.toSyncResponse()
                }
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> {
                    throw SyncException.AuthenticationException("Invalid sync key")
                }
                else -> {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    throw SyncException.ServerException(
                        responseCode,
                        "Server returned $responseCode: $errorBody"
                    )
                }
            }
        } catch (e: SyncException) {
            throw e
        } catch (e: IOException) {
            throw SyncException.NetworkException("Network error: ${e.message}", e)
        } catch (e: Exception) {
            throw SyncException.NetworkException("Unexpected error: ${e.message}", e)
        }
    }

    override suspend fun healthCheck(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/health"

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * DTO for Jackson deserialization of sync response.
 */
internal data class SyncResponseDto(
    val serverTimestamp: Long = 0,
    val habits: List<SyncHabitDto> = emptyList(),
    val entries: List<SyncEntryDto> = emptyList(),
    val deletedHabitUuids: List<String> = emptyList(),
    val deletedEntries: List<DeletedEntryDto> = emptyList()
) {
    fun toSyncResponse() = SyncResponse(
        serverTimestamp = serverTimestamp,
        habits = habits.map { it.toSyncHabitData() },
        entries = entries.map { it.toSyncEntryData() },
        deletedHabitUuids = deletedHabitUuids,
        deletedEntries = deletedEntries.map { it.toDeletedEntryRef() }
    )
}

internal data class SyncHabitDto(
    val uuid: String = "",
    val name: String = "",
    val description: String = "",
    val question: String = "",
    val frequencyNumerator: Int = 1,
    val frequencyDenominator: Int = 1,
    val color: Int = 0,
    val position: Int = 0,
    val isArchived: Boolean = false,
    val type: Int = 0,
    val targetType: Int = 0,
    val targetValue: Double = 0.0,
    val unit: String = "",
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val reminderDays: Int? = null,
    val modifiedAt: Long = 0,
    val deletedAt: Long? = null
) {
    fun toSyncHabitData() = SyncHabitData(
        uuid = uuid,
        name = name,
        description = description,
        question = question,
        frequencyNumerator = frequencyNumerator,
        frequencyDenominator = frequencyDenominator,
        color = color,
        position = position,
        isArchived = isArchived,
        type = type,
        targetType = targetType,
        targetValue = targetValue,
        unit = unit,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        reminderDays = reminderDays,
        modifiedAt = modifiedAt,
        deletedAt = deletedAt
    )
}

internal data class SyncEntryDto(
    val habitUuid: String = "",
    val timestamp: Long = 0,
    val value: Int = 0,
    val notes: String = "",
    val modifiedAt: Long = 0
) {
    fun toSyncEntryData() = SyncEntryData(
        habitUuid = habitUuid,
        timestamp = timestamp,
        value = value,
        notes = notes,
        modifiedAt = modifiedAt
    )
}

internal data class DeletedEntryDto(
    val habitUuid: String = "",
    val timestamp: Long = 0
) {
    fun toDeletedEntryRef() = DeletedEntryRef(
        habitUuid = habitUuid,
        timestamp = timestamp
    )
}
