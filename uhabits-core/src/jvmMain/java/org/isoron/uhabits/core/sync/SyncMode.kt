/*
 * Copyright (C) 2016-2021 Álinson Santos Xavier <git@axavier.org>
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
 * Defines the sync mode for data synchronization.
 *
 * - BIDIRECTIONAL: Upload local changes and download remote changes (default)
 * - UPLOAD_ONLY: Upload local changes to server, ignore remote changes
 * - DOWNLOAD_ONLY: Download remote changes, don't upload local changes
 */
enum class SyncMode {
    /**
     * Two-way sync: upload local changes and download remote changes.
     * This is the default mode for normal synchronization.
     */
    BIDIRECTIONAL,

    /**
     * Upload only: push local changes to the server, but ignore any
     * remote changes. Useful when you want to backup without overwriting
     * local data with server data.
     */
    UPLOAD_ONLY,

    /**
     * Download only: pull remote changes from the server, but don't
     * upload any local changes. Useful when you want to restore from
     * server without pushing local modifications.
     */
    DOWNLOAD_ONLY
}
