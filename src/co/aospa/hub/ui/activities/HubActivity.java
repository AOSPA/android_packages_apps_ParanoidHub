package co.aospa.hub.ui.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.util.ThemeHelper;
import com.google.android.setupdesign.view.RichTextView;

import co.aospa.hub.R;
import co.aospa.hub.UpdateStateService;
import co.aospa.hub.controllers.UpdateStateController;
import co.aospa.hub.ui.State;
import co.aospa.hub.ui.state.UpdateAvailableState;
import co.aospa.hub.ui.state.UpdateCheckingState;
import co.aospa.hub.ui.state.UpdateDownloadErrorState;
import co.aospa.hub.ui.state.UpdateDownloadInstallState;
import co.aospa.hub.ui.state.UpdateDownloadPausedState;
import co.aospa.hub.ui.state.UpdateDownloadVerificationState;
import co.aospa.hub.ui.state.UpdateInstallErrorState;
import co.aospa.hub.ui.state.UpdateRebootState;
import co.aospa.hub.ui.state.UpdateUnavailableState;
import co.aospa.hub.ui.state.UpdateVerificationErrorState;
import co.aospa.hub.util.Constants;

public class HubActivity extends Activity implements UpdateStateController.StateListener {

    private UpdateStateService mUpdateStateService;

    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            UpdateStateService.LocalBinder binder = (UpdateStateService.LocalBinder) service;
            mUpdateStateService = binder.getService();
            UpdateStateController controller = mUpdateStateService.getController();
            controller.addUpdateStateListener(HubActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            UpdateStateController controller = mUpdateStateService.getController();
            controller.removeUpdateStateListener(HubActivity.this);
            mUpdateStateService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(com.google.android.setupdesign.R.style.SudThemeGlifV4);
        ThemeHelper.applyTheme(this);
        ThemeHelper.trySetDynamicColor(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        updateState(new UpdateCheckingState(), -1);
    }

    private void updateState(State state, int progress) {
        Context context = HubActivity.this;
        setHeader(state.getHeaderText(context));
        setDescription(state.getDescriptionText(context));
        setButtonAction(state.getAction(context), state.getActionText(context));
        setShowProgress(state.getProgressState(), progress);
    }

    private void setHeader(CharSequence headerText) {
        GlifLayout layout = getLayout();
        if (!layout.getHeaderTextView().getText().toString().contentEquals(headerText)) {
            layout.setHeaderText(headerText);
            setTitle(headerText);
        }
    }

    private void setDescription(String descriptionText) {
        GlifLayout layout = getLayout();
        RichTextView description = layout.findViewById(R.id.system_update_description);
        description.setText(Html.fromHtml(descriptionText, Html.FROM_HTML_MODE_COMPACT));
    }

    private void setButtonAction(View.OnClickListener action, String text) {
        GlifLayout layout = getLayout();
        int visibility = action != null ? View.VISIBLE : View.GONE;
        Button button = layout.findViewById(R.id.system_update_primary_button);
        button.setText(text);
        button.setOnClickListener(action);
        button.setVisibility(visibility);

    }

    private void setShowProgress(boolean showProgress, int progress) {
        GlifLayout layout = getLayout();
        ProgressBar checkerProgressBar = layout.findViewById(R.id.system_update_checker_progress);
        checkerProgressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        checkerProgressBar.setIndeterminate(showProgress);

        // Controls the system update progress
        boolean shouldShowProgress = !showProgress && progress != -1;
        ProgressBar progressBar = layout.findViewById(R.id.system_update_progress);
        progressBar.setVisibility(shouldShowProgress ? View.VISIBLE : View.GONE);
        progressBar.setIndeterminate(showProgress && progress == 0);
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, UpdateStateService.class);
        intent.setAction(Constants.INTENT_ACTION_UPDATE_CONFIG);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        if (mUpdateStateService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        finish();
    }

    @Override
    public void onApplyThemeResource(Resources.Theme theme, int resId, boolean first) {
        theme.applyStyle(R.style.SetupWizardPartnerResource, true);
        super.onApplyThemeResource(theme, resId, first);
    }

    private GlifLayout getLayout() {
        return findViewById(R.id.system_update);
    }

    @Override
    public void onUpdateStateChanged(State state, int progress) {
        if ((state instanceof UpdateCheckingState ||
                state instanceof UpdateAvailableState ||
                state instanceof UpdateUnavailableState ||
                state instanceof UpdateDownloadInstallState ||
                state instanceof UpdateDownloadVerificationState ||
                state instanceof UpdateDownloadPausedState||
                state instanceof UpdateDownloadErrorState ||
                state instanceof UpdateInstallErrorState ||
                state instanceof UpdateVerificationErrorState ||
                state instanceof UpdateRebootState)) {
            updateState(state, progress);
        }
    }
}