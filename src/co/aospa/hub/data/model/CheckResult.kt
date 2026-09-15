/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.model

/** Outcome of a check against the update server. */
enum class CheckResult {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    OFFLINE,
    FAILED,
}
