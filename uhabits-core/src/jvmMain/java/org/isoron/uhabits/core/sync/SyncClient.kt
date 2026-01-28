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
 * Interface for HTTP sync operations.
 * Implementations handle the actual network communication with the sync server.
 */
interface SyncClient {
    /**
     * Sends a sync request to the server and returns the response.
     *
     * @param baseUrl The base URL of the sync server (e.g., "https://sync.example.com")
     * @param syncKey The authentication key/token
     * @param request The sync request payload
     * @return The sync response from the server
     * @throws SyncException if the request fails
     */
    suspend fun sync(baseUrl: String, syncKey: String, request: SyncRequest): SyncResponse

    /**
     * Checks if the sync server is reachable and healthy.
     *
     * @param baseUrl The base URL of the sync server
     * @return true if the server is healthy, false otherwise
     */
    suspend fun healthCheck(baseUrl: String): Boolean
}

/**
 * Exception thrown when a sync operation fails.
 */
sealed class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkException(message: String, cause: Throwable? = null) : SyncException(message, cause)
    class AuthenticationException(message: String) : SyncException(message)
    class ServerException(val statusCode: Int, message: String) : SyncException(message)
    class ParseException(message: String, cause: Throwable? = null) : SyncException(message, cause)
}
