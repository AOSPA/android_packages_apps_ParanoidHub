/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.hub.ui

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.aospa.hub.R
import co.aospa.hub.UpdateService
import co.aospa.hub.data.model.BatteryState
import co.aospa.hub.ui.model.UpdateState
import co.aospa.hub.ui.settings.SettingsActivity
import co.aospa.hub.util.BuildInfo
import co.aospa.hub.util.hub
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.model.KeyPath
import com.android.settingslib.DeviceInfoUtils
import com.android.settingslib.widget.theme.R as SettingsLibR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.setupcompat.partnerconfig.PartnerConfig
import com.google.android.setupcompat.partnerconfig.PartnerConfigHelper
import com.google.android.setupcompat.template.FooterBarMixin
import com.google.android.setupcompat.template.FooterButton
import com.google.android.setupdesign.GlifLayout
import com.google.android.setupdesign.template.FloatingActionButtonMixin
import com.google.android.setupdesign.util.LottieAnimationHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Shows the update screen, one [UpdateState] at a time. */
class UpdatesActivity : AppCompatActivity() {
    private val viewModel by viewModels<UpdatesViewModel>()

    private lateinit var layout: GlifLayout
    private lateinit var footerBar: FooterBarMixin
    private lateinit var animation: LottieAnimationView
    private lateinit var build: TextView
    private lateinit var description: TextView
    private lateinit var size: TextView
    private lateinit var androidVersion: TextView
    private lateinit var securityPatch: TextView
    private lateinit var lastCheck: TextView
    private lateinit var status: TextView
    private lateinit var progressInformation: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var warningFrame: View
    private lateinit var warning: TextView
    private lateinit var buttonQualifier: TextView

    private var footerState: Pair<Footer, Boolean>? = null
    private var activeIndicatorColor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.updates_activity)

        layout = requireViewById(R.id.setup_wizard_layout)
        footerBar = layout.getMixin(FooterBarMixin::class.java)
        animation = requireViewById<LottieAnimationView>(R.id.animation).apply {
            setUpdateIllustration()
        }
        build = requireViewById(R.id.build)
        description = requireViewById(R.id.description)
        size = requireViewById(R.id.size)
        androidVersion = requireViewById(R.id.android_version)
        securityPatch = requireViewById(R.id.security_patch)
        lastCheck = requireViewById(R.id.last_check)
        status = requireViewById(R.id.status)
        progressInformation = requireViewById(R.id.progress_information)
        progressBar = requireViewById<LinearProgressIndicator>(R.id.progress).also {
            activeIndicatorColor = it.indicatorColor.first()
        }
        warningFrame = requireViewById(R.id.warning_frame)
        warning = requireViewById(R.id.warning)
        buttonQualifier = requireViewById(R.id.button_qualifier)
        layout.getMixin(FloatingActionButtonMixin::class.java).apply {
            actionButton?.apply {
                setIconResource(R.drawable.ic_settings)
                contentDescription = getString(R.string.settings_title)
            }
            onClickListener = View.OnClickListener {
                startActivity(Intent(this@UpdatesActivity, SettingsActivity::class.java))
            }
            visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.state, hub.batteryRepository.state, ::Pair)
                    .collect { (state, battery) -> render(state, battery) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            when {
                viewModel.isWorkInterrupted() -> UpdateService.download(this@UpdatesActivity)
                viewModel.isInstallInterrupted() -> UpdateService.reattach(this@UpdatesActivity)
            }
        }
    }

    private fun LottieAnimationView.setUpdateIllustration() {
        val entry = PartnerConfigHelper.get(context).getIllustrationResourceEntry(
            context,
            PartnerConfig.CONFIG_LOADING_LOTTIE_UPDATE,
        )

        if (entry == null) {
            setAnimation(R.raw.loading_illustration)
            LottieAnimationHelper.get().applyColor(
                context,
                this,
                mapOf(KeyPath("**") to themeColor(android.R.attr.colorAccent)),
            )
        } else {
            // Lottie parses off the main thread and closes the stream itself.
            setAnimation(entry.resources.openRawResource(entry.resourceId), null)
            LottieAnimationHelper.get().applyColor(
                context,
                this,
                if (resources.configuration.isNightModeActive) {
                    PartnerConfig.CONFIG_LOTTIE_DARK_THEME_CUSTOMIZATION_UPDATE
                } else {
                    PartnerConfig.CONFIG_LOTTIE_LIGHT_THEME_CUSTOMIZATION_UPDATE
                },
            )
        }
        playAnimation()
    }

    private fun render(state: UpdateState, battery: BatteryState) {
        layout.setHeaderText(headerFor(state))

        val update = state.update
        val checking = state is UpdateState.Checking

        animation.isVisible = checking

        build.isVisible = update != null
        build.text = update?.let {
            getString(R.string.update_build, it.version, it.buildType, it.versionCode)
        }
        description.isVisible = update?.changelog?.isNotEmpty() == true
        description.text = update?.changelog
        size.isVisible = update != null
        size.text = update?.let {
            getString(R.string.update_size, Formatter.formatShortFileSize(this, it.size))
        }

        val idle = state as? UpdateState.Idle
        androidVersion.isVisible = idle != null
        securityPatch.isVisible = idle != null
        lastCheck.isVisible = idle != null && idle.lastCheckedAt > 0
        if (idle != null) {
            androidVersion.text =
                getString(R.string.update_android_version, BuildInfo.androidVersion)
            securityPatch.text = getString(
                R.string.update_security_patch,
                DeviceInfoUtils.getSecurityPatch(resources.configuration.locales[0]),
            )
            if (idle.lastCheckedAt > 0) {
                lastCheck.text = getString(
                    R.string.update_last_check,
                    formatTime(idle.lastCheckedAt),
                )
            }
        }

        renderProgress(state)
        renderWarning(state, battery)
        renderQualifier(state)
        renderButtons(state, battery)
    }

    private fun renderProgress(state: UpdateState) {
        val information = when (state) {
            is UpdateState.Downloaded -> getString(
                R.string.update_download_progress,
                Formatter.formatShortFileSize(this, state.downloadedBytes),
                Formatter.formatShortFileSize(this, state.update.size),
            )

            is UpdateState.Installing ->
                getString(R.string.update_install_progress, state.percent)

            else -> null
        }
        progressInformation.isVisible = information != null
        progressInformation.text = information

        progressBar.isVisible = state is UpdateState.Downloaded ||
            state is UpdateState.Installing ||
            state is UpdateState.Verifying
        progressBar.isIndeterminate = state is UpdateState.Verifying
        progressBar.setIndicatorColor(
            if (state is UpdateState.DownloadPaused) {
                getColor(SettingsLibR.color.settingslib_materialColorOutline)
            } else {
                activeIndicatorColor
            }
        )
        when (state) {
            is UpdateState.Downloading -> progressBar.setProgressCompat(state.percent, true)
            is UpdateState.DownloadPaused -> progressBar.setProgressCompat(state.percent, false)
            is UpdateState.Installing -> progressBar.setProgressCompat(state.percent, true)
            else -> Unit
        }
    }

    private fun renderWarning(state: UpdateState, battery: BatteryState) {
        val message = when {
            state is UpdateState.CheckFailed && state.offline ->
                getString(R.string.update_no_internet_message)

            state is UpdateState.CheckFailed -> getString(R.string.update_check_failed_message)
            state is UpdateState.InstallFailed -> state.reason
            state is UpdateState.DownloadPaused && state.failed ->
                getString(R.string.update_download_failed_message)

            (state is UpdateState.Available || state is UpdateState.ReadyToInstall) &&
                    !battery.hasEnoughCharge ->
                getString(R.string.update_battery_low)

            else -> null
        }
        warningFrame.isVisible = message != null
        warning.text = message
    }

    private fun renderQualifier(state: UpdateState) {
        val qualifier = when (state) {
            is UpdateState.VerificationFailed ->
                getString(R.string.update_verification_problem)

            is UpdateState.InstallFailed -> getString(R.string.update_installation_problem)
            else -> null
        }
        buttonQualifier.isVisible = qualifier != null
        buttonQualifier.text = qualifier

        val metered = hub.connectivityRepository.currentState.isMetered
        val connection = when {
            !metered -> null
            state is UpdateState.Downloaded -> getString(R.string.update_using_cellular)
            state is UpdateState.Available -> getString(R.string.update_using_metered_wifi)
            else -> null
        }
        status.isVisible = connection != null
        status.text = connection
    }

    private fun renderButtons(state: UpdateState, battery: BatteryState) {
        val wanted = footerFor(state) to battery.hasEnoughCharge
        if (wanted == footerState) return
        footerState = wanted

        val (footer, hasBattery) = wanted
        when (footer) {
            Footer.NONE -> {
                footerBar.primaryButton = hiddenButton()
                footerBar.secondaryButton = hiddenButton()
            }

            Footer.CHECK -> {
                footerBar.primaryButton = checkButton()
                footerBar.secondaryButton = hiddenButton()
            }

            Footer.DOWNLOAD -> {
                footerBar.primaryButton = button(getString(R.string.button_download), true) {
                    UpdateService.download(this)
                }
                footerBar.secondaryButton = checkButton()
            }

            Footer.RETRY -> {
                footerBar.primaryButton = button(getString(R.string.button_download), true) {
                    UpdateService.download(this)
                }
                footerBar.secondaryButton = cancelButton()
            }

            Footer.PAUSE -> {
                footerBar.primaryButton = button(getString(R.string.button_pause), true) {
                    UpdateService.pause(this)
                }
                footerBar.secondaryButton = cancelButton()
            }

            Footer.RESUME -> {
                footerBar.primaryButton = button(getString(R.string.button_resume), true) {
                    UpdateService.download(this)
                }
                footerBar.secondaryButton = cancelButton()
            }

            Footer.INSTALL -> {
                footerBar.primaryButton = button(getString(R.string.button_install), true) {
                    UpdateService.install(this)
                }.apply { isEnabled = hasBattery }
                footerBar.secondaryButton = cancelButton()
            }

            Footer.RESTART -> {
                footerBar.primaryButton = button(getString(R.string.button_restart), true) {
                    getSystemService(PowerManager::class.java).reboot(null)
                }
                footerBar.secondaryButton = hiddenButton()
            }
        }
    }

    private fun footerFor(state: UpdateState): Footer = when (state) {
        is UpdateState.Checking -> Footer.NONE
        is UpdateState.Available -> Footer.DOWNLOAD
        is UpdateState.Downloading -> Footer.PAUSE
        is UpdateState.DownloadPaused -> Footer.RESUME
        is UpdateState.ReadyToInstall -> Footer.INSTALL
        is UpdateState.VerificationFailed, is UpdateState.InstallFailed -> Footer.RETRY
        is UpdateState.NeedsReboot -> Footer.RESTART
        else -> Footer.CHECK
    }

    private fun checkButton() =
        button(getString(R.string.button_check_for_updates), primary = true) {
            viewModel.check()
        }

    private fun hiddenButton() = FooterButton.Builder(this)
        .setText("")
        .setVisibility(View.GONE)
        .build()

    private fun cancelButton() = button(getString(android.R.string.cancel), primary = false) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_download_title)
            .setMessage(R.string.delete_download_message)
            .setPositiveButton(R.string.button_delete) { _, _ -> UpdateService.cancel(this) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun button(text: String, primary: Boolean, onClick: () -> Unit) =
        FooterButton.Builder(this)
            .setText(text)
            .setListener { onClick() }
            .setButtonType(
                if (primary) FooterButton.ButtonType.NEXT else FooterButton.ButtonType.OTHER
            )
            .setTheme(
                if (primary) R.style.SudGlifButton_Primary else R.style.SudGlifButton_Secondary
            )
            .build()

    private fun formatTime(timestamp: Long): String =
        DateUtils.formatDateTime(this, timestamp, DateUtils.FORMAT_SHOW_TIME)

    private fun themeColor(attr: Int): Int = TypedValue().also {
        theme.resolveAttribute(attr, it, true)
    }.data

    private fun headerFor(state: UpdateState): String = when (state) {
        is UpdateState.Checking -> getString(R.string.update_checking_title)
        is UpdateState.CheckFailed -> getString(R.string.update_check_failed_title)
        is UpdateState.UpToDate -> getString(R.string.update_up_to_date_title)
        is UpdateState.Available -> getString(R.string.update_available_title)
        is UpdateState.Downloading -> getString(R.string.update_downloading_title)
        is UpdateState.DownloadPaused -> getString(
            if (state.failed) {
                R.string.update_download_failed_title
            } else {
                R.string.update_download_paused_title
            }
        )

        is UpdateState.Verifying -> getString(R.string.update_verifying_title)
        is UpdateState.VerificationFailed -> getString(R.string.update_verification_failed_title)
        is UpdateState.ReadyToInstall -> getString(R.string.update_ready_title)
        is UpdateState.Installing -> getString(R.string.update_installing_title)
        is UpdateState.InstallFailed -> getString(R.string.update_install_failed_title)
        is UpdateState.NeedsReboot -> getString(R.string.update_almost_done_title)
    }

    private enum class Footer { NONE, CHECK, DOWNLOAD, RETRY, PAUSE, RESUME, INSTALL, RESTART }
}
