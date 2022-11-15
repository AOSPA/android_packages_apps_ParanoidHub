package co.aospa.hub.ui.state;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import co.aospa.hub.R;
import co.aospa.hub.UpdateStateService;
import co.aospa.hub.components.ChangelogComponent;
import co.aospa.hub.components.UpdateComponent;
import co.aospa.hub.controllers.UpdateController;
import co.aospa.hub.ui.State;
import co.aospa.hub.util.Constants;
import co.aospa.hub.util.PreferenceHelper;
import co.aospa.hub.util.Update;

public class UpdateDownloadInstallState implements State {

    private final Update mUpdate;

    public UpdateDownloadInstallState(UpdateComponent updateComponent, ChangelogComponent changelogComponent) {
        mUpdate = new Update(updateComponent, changelogComponent);
    }

    private boolean shouldShowButton(Context context) {
        PreferenceHelper preferenceHelper = new PreferenceHelper(context);
        int status = preferenceHelper.getIntValueByKey(Constants.KEY_UPDATE_STATUS);
        return status == UpdateController.StatusType.DOWNLOAD;
    }

    @Override
    public String getHeaderText(Context context) {
        boolean securityUpdate = mUpdate.isSecurityUpdate();
        return securityUpdate ?
                context.getResources().getString(R.string.system_update_update_download_install_security_update) :
                context.getResources().getString(R.string.system_update_update_download_install);
    }

    @Override
    public String getStepperText(Context context) {
        PreferenceHelper preferenceHelper = new PreferenceHelper(context);
        int state = preferenceHelper.getIntValueByKey(Constants.KEY_UPDATE_STATUS);
        String stepperText = null;
        switch (state) {
            case UpdateController.StatusType.INSTALL: {
                stepperText = context.getResources().getString(
                        R.string.system_update_update_download_install_step_install);
            }
            break;
            case UpdateController.StatusType.DOWNLOAD: {
                stepperText = context.getResources().getString(
                        R.string.system_update_update_download_install_step_download);
            }
            break;
            case UpdateController.StatusType.FINALIZE: {
                stepperText = context.getResources().getString(
                        R.string.system_update_update_download_install_step_finalize);
            }
            break;
        }
        return stepperText;
    }

    @Override
    public String getDescriptionText(Context context) {
        return mUpdate.getUpdateDescriptionText(context);
    }

    @Override
    public String getActionText(Context context) {
        boolean shouldShowButton = shouldShowButton(context);
        return shouldShowButton ? context.getResources().getString(
                R.string.system_update_update_download_install_pause_button) : null;
    }

    @Override
    public View.OnClickListener getAction(Context context) {
        boolean shouldShowButton = shouldShowButton(context);
        return shouldShowButton ? view -> {
            Intent intent = new Intent(context, UpdateStateService.class);
            intent.setAction(Constants.INTENT_ACTION_UPDATE);
            intent.putExtra(Constants.EXTRA_DOWNLOAD, Constants.EXTRA_DOWNLOAD_ACTION_PAUSE);
            context.startService(intent);
        } : null;
    }

    @Override
    public String getSecondaryActionText(Context context) {
        return context.getResources().getString(R.string.system_update_update_download_install_cancel_button);
    }

    @Override
    public View.OnClickListener getSecondaryAction(Context context) {
        boolean shouldShowButton = shouldShowButton(context);
        return shouldShowButton ? view -> {
            Intent intent = new Intent(context, UpdateStateService.class);
            intent.setAction(Constants.INTENT_ACTION_UPDATE);
            intent.putExtra(Constants.EXTRA_DOWNLOAD, Constants.EXTRA_DOWNLOAD_ACTION_CANCEL);
            context.startService(intent);
        } : null;
    }

    @Override
    public boolean getProgressState() {
        return false;
    }
}
