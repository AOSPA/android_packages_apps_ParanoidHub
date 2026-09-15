/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import co.aospa.hub.data.model.DownloadState
import co.aospa.hub.data.model.Progress
import co.aospa.hub.data.model.Update
import co.aospa.hub.data.repository.UpdateRepository
import co.aospa.hub.download.UpdateDownloader
import co.aospa.hub.install.UpdateInstaller
import co.aospa.hub.system.UpdateNotifier
import co.aospa.hub.ui.UpdatesActivity
import co.aospa.hub.util.hub
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "UpdateService"
private const val NOTIFICATION_ID = 10
private const val PROGRESS_INTERVAL_MILLIS = 200L

/** Downloads a package, verifies it, and hands it to the installer. */
class UpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var repository: UpdateRepository
    private lateinit var notifier: UpdateNotifier
    private var activeDownloader: UpdateDownloader? = null
    private var installer: UpdateInstaller? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = hub.repository
        notifier = hub.updateNotifier
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> startDownload()
            ACTION_PAUSE -> pauseDownload()
            ACTION_CANCEL -> cancelDownload()
            ACTION_INSTALL -> startInstall()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeDownloader?.cancel()
        installer?.release()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startDownload() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch { download() }
    }

    private suspend fun download() {
        val update = beginForegroundWork(
            R.string.update_downloading_title,
            DownloadState.DOWNLOADING,
        ) ?: return
        val file = repository.fileFor(update)

        val downloader = UpdateDownloader().also { activeDownloader = it }
        var publishedAt = 0L
        val completed = try {
            downloader.download(update.url, file, update.size) { downloadedBytes ->
                val now = SystemClock.elapsedRealtime()
                if (now - publishedAt < PROGRESS_INTERVAL_MILLIS) return@download
                publishedAt = now
                val percent = percentOf(downloadedBytes, update.size)
                repository.reportProgress(Progress(downloadedBytes, percent))
                goToForeground(getString(R.string.update_downloading_title), percent)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Download failed", e)
            repository.setDownloadState(DownloadState.FAILED)
            finish()
            return
        } finally {
            activeDownloader = null
        }

        if (!completed) return

        verify(update, file)
    }

    private suspend fun beginForegroundWork(
        @StringRes title: Int,
        state: DownloadState,
    ): Update? {
        val update = repository.currentUpdate()
        if (update == null) {
            stopSelf()
            return null
        }

        goToForeground(getString(title))
        acquireWakeLock()
        repository.setDownloadState(state)
        return update
    }

    private suspend fun verify(update: Update, file: File) {
        repository.setDownloadState(DownloadState.VERIFYING)
        val verified = UpdateDownloader.verify(file, update.sha256)
        repository.setDownloadState(
            if (verified) DownloadState.VERIFIED else DownloadState.VERIFICATION_FAILED,
        )
        if (!verified) file.delete()
        finish()
    }

    private fun stopDownload(outcome: suspend () -> Unit) = scope.launch {
        activeDownloader?.cancel()
        downloadJob?.join()
        outcome()
        finish()
    }

    private fun pauseDownload() = stopDownload {
        repository.setDownloadState(DownloadState.PAUSED)
    }

    private fun cancelDownload() = stopDownload { repository.discardDownload() }

    private fun startInstall() = scope.launch {
        val update = beginForegroundWork(
            R.string.update_installing_title,
            DownloadState.INSTALLING,
        ) ?: return@launch
        val file = repository.fileFor(update)

        val listener = object : UpdateInstaller.Listener {
            override fun onProgress(percent: Int) {
                repository.reportProgress(Progress(percent = percent))
                goToForeground(getString(R.string.update_installing_title), percent)
            }

            override fun onComplete() {
                scope.launch {
                    // update_engine has written the payload to the other slot.
                    file.delete()
                    repository.setDownloadState(DownloadState.NEEDS_REBOOT)
                    finish()
                }
            }

            override fun onError(reason: String) {
                scope.launch {
                    notifier.showInstallFailed(reason)
                    repository.reportInstallError(reason)
                    repository.setDownloadState(DownloadState.INSTALL_FAILED)
                    finish()
                }
            }
        }

        UpdateInstaller.forDevice(this@UpdateService, listener)
            .also { installer = it }
            .apply {
                setPerformanceMode(
                    hub.preferences.prioritizeInstallSpeed ||
                            hub.batteryRepository.currentState.isAcCharging
                )
            }
            .install(file)
    }

    private fun goToForeground(title: String, percent: Int? = null) {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, UpdatesActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, UpdateNotifier.CHANNEL_ONGOING)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_system_update)
            .setContentIntent(intent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { percent?.let { setProgress(100, it, false) } }
            .build()
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun percentOf(downloadedBytes: Long, totalBytes: Long): Int =
        if (totalBytes > 0) {
            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }

    private fun finish() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:work")
            .apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val ACTION_DOWNLOAD = "co.aospa.hub.action.DOWNLOAD"
        private const val ACTION_PAUSE = "co.aospa.hub.action.PAUSE"
        private const val ACTION_CANCEL = "co.aospa.hub.action.CANCEL"
        private const val ACTION_INSTALL = "co.aospa.hub.action.INSTALL"

        /** Starts, or resumes, downloading the update on offer. */
        fun download(context: Context) = start(context, ACTION_DOWNLOAD)

        /** Stops the download, keeping what is already on disk. */
        fun pause(context: Context) = start(context, ACTION_PAUSE)

        /** Stops the download and throws away what is on disk. */
        fun cancel(context: Context) = start(context, ACTION_CANCEL)

        /** Applies the downloaded and verified package. */
        fun install(context: Context) = start(context, ACTION_INSTALL)

        private fun start(context: Context, action: String) {
            context.startService(Intent(context, UpdateService::class.java).setAction(action))
        }
    }
}
