/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.util

import android.content.Context
import co.aospa.hub.HubApplication

/** The application, for the components that need what it holds. */
val Context.hub: HubApplication
    get() = applicationContext as HubApplication
