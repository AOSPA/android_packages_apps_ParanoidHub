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

public class UpdateRebootState implements State {

    @Override
    public String getHeaderText(Context context) {
        return context.getResources().getString(R.string.system_update_update_download_install_complete);
    }

    @Override
    public String getStepperText(Context context) {
        return null;
    }

    @Override
    public String getDescriptionText(Context context) {
        return context.getResources().getString(R.string.system_update_update_download_install_complete_desc);
    }

    @Override
    public String getActionText(Context context) {
        return context.getResources().getString(R.string.system_update_update_download_install_complete_restart_button);
    }

    @Override
    public View.OnClickListener getAction(Context context) {
        return view -> {
            Intent intent = new Intent(context, UpdateStateService.class);
            intent.setAction(Constants.INTENT_ACTION_UPDATE_INSTALL_LEGACY);
            context.startService(intent);
        };
    }

    @Override
    public boolean getProgressState() {
        return false;
    }
}
