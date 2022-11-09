package co.aospa.hub.ui.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.util.ThemeHelper;

import co.aospa.hub.R;
import co.aospa.hub.UpdateStateService;
import co.aospa.hub.controllers.UpdateStateController;
import co.aospa.hub.ui.State;
import co.aospa.hub.ui.UpdateAvailableState;
import co.aospa.hub.ui.UpdateCheckingState;
import co.aospa.hub.ui.UpdateUnavailableState;
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
        updateState(new UpdateCheckingState());
    }

    private void updateState(State state) {
        Context context = HubActivity.this;
        setHeader(state.getHeaderText(context));
        setDescription(state.getDescriptionText(context));
        setButtonAction(state.getAction(context), state.getActionText(context));
        setShowProgress(state.getProgressState());
    }

    private void setHeader(CharSequence charSequence) {
        GlifLayout layout = getLayout();
        if (!layout.getHeaderTextView().getText().toString().contentEquals(charSequence)) {
            layout.setHeaderText(charSequence);
            setTitle(charSequence);
        }
    }

    private void setDescription(CharSequence charSequence) {
        GlifLayout layout = getLayout();
        if (charSequence != null) {
            if (!layout.getDescriptionTextView().getText().toString().contentEquals(charSequence)) {
                layout.setDescriptionText(charSequence);
            }
        }
    }

    private void setButtonAction(View.OnClickListener action, String text) {
        /** FooterBarMixin footerBarMixin = getLayout().getMixin(FooterBarMixin.class);
        footerBarMixin.setPrimaryButton(new FooterButton.Builder(this)
                .setText(text)
                .setListener(action)
                .setButtonType(FooterButton.ButtonType.OTHER)
                .setTheme(R.style.Hub_Button)
                .build());
        int visibility = action != null ? View.VISIBLE : View.GONE;
        footerBarMixin.getPrimaryButton().setVisibility(visibility);**/

        int visibility = action != null ? View.VISIBLE : View.GONE;
        Button button = findViewById(R.id.system_update_primary_button);
        button.setText(text);
        button.setOnClickListener(action);
        button.setVisibility(visibility);

    }

    private void setShowProgress(boolean showProgress) {
        ProgressBar progressBar = findViewById(R.id.system_update_progress);
        progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        progressBar.setIndeterminate(showProgress);
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
    public void onUpdateStateChanged(State state) {
        if (state instanceof UpdateCheckingState ||
        state instanceof UpdateAvailableState ||
        state instanceof UpdateUnavailableState) {
            updateState(state);
        }
    }
}