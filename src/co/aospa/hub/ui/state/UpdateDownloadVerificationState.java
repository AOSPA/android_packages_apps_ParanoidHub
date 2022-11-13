package co.aospa.hub.ui.state;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import co.aospa.hub.R;
import co.aospa.hub.UpdateStateService;
import co.aospa.hub.components.ChangelogComponent;
import co.aospa.hub.components.UpdateComponent;
import co.aospa.hub.ui.State;
import co.aospa.hub.util.Constants;
import co.aospa.hub.util.Update;

public class UpdateDownloadVerificationState implements State {

    private final Update mUpdate;

    public UpdateDownloadVerificationState(UpdateComponent updateComponent, ChangelogComponent changelogComponent) {
        mUpdate = new Update(updateComponent, changelogComponent);
    }

    @Override
    public String getHeaderText(Context context) {
        return context.getResources().getString(R.string.system_update_update_download_install_verify);
    }

    @Override
    public String getStepperText(Context context) {
        return context.getResources().getString(R.string.system_update_update_download_install_step_download_verify);
    }

    @Override
    public String getDescriptionText(Context context) {
        return mUpdate.getUpdateDescriptionText(context);
    }

    @Override
    public String getActionText(Context context) {
        return null;
    }

    @Override
    public View.OnClickListener getAction(Context context) {
        return null;
    }

    @Override
    public boolean getProgressState() {
        return false;
    }
}
