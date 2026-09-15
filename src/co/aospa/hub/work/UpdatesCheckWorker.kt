/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.aospa.hub.data.model.CheckInterval
import co.aospa.hub.data.model.CheckResult
import co.aospa.hub.util.hub
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "periodic_update_check"

/** Checks for updates in the background, on the interval the user picked. */
class UpdatesCheckWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext.hub
        return when (application.repository.check()) {
            CheckResult.UPDATE_AVAILABLE -> {
                application.updateNotifier.showNewUpdate()
                Result.success()
            }

            CheckResult.FAILED,
            CheckResult.OFFLINE -> Result.retry()

            CheckResult.UP_TO_DATE -> Result.success()
        }
    }

    companion object {
        /** Applies [interval], cancelling the check when it is [CheckInterval.NEVER]. */
        fun schedule(context: Context, interval: CheckInterval) {
            val workManager = WorkManager.getInstance(context)
            if (!interval.isPeriodic) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<UpdatesCheckWorker>(interval.days, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build(),
            )
        }
    }
}
