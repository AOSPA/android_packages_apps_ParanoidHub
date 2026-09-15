/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.model

/** Progress of a running download or install. */
data class Progress(val downloadedBytes: Long = 0, val percent: Int = 0)
