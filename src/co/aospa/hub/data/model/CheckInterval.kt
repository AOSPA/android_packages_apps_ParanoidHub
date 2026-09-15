/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.data.model

/** How often the background check runs. */
enum class CheckInterval(val value: String, val days: Long) {
    NEVER("never", 0),
    DAILY("daily", 1),
    WEEKLY("weekly", 7),
    MONTHLY("monthly", 30);

    /** Whether a check should be scheduled at all. */
    val isPeriodic: Boolean
        get() = days > 0

    companion object {
        /** Returns the interval named by [value], or [WEEKLY] if it names none. */
        fun forValue(value: String?): CheckInterval =
            entries.firstOrNull { it.value == value } ?: WEEKLY
    }
}
