package co.aospa.hub.ui.state;

import android.content.Context;
import android.content.Intent;
import android.text.format.Formatter;
import android.view.View;

import co.aospa.hub.R;
import co.aospa.hub.UpdateStateService;
import co.aospa.hub.components.ChangelogComponent;
import co.aospa.hub.components.UpdateComponent;
import co.aospa.hub.ui.State;
import co.aospa.hub.util.Constants;
import co.aospa.hub.util.Update;

public class UpdateAvailableState implements State {

    private final Update mUpdate;

    public UpdateAvailableState(UpdateComponent updateComponent, ChangelogComponent changelogComponent) {
        mUpdate = new Update(updateComponent, changelogComponent);
    }

    @Override
    public String getHeaderText(Context context) {
        boolean securityUpdate = mUpdate.isSecurityUpdate();
        return securityUpdate ?
                context.getResources().getString(R.string.system_update_update_available_security_update) :
                context.getResources().getString(R.string.system_update_update_available);
    }

    @Override
    public String getStepperText(Context context) {
        return null;
    }

    @Override
    public String getDescriptionText(Context context) {
        return mUpdate.getUpdateDescriptionText(context);
    }

    @Override
    public String getActionText(Context context) {
        return context.getResources().getString(R.string.system_update_update_available_button);
    }

    @Override
    public View.OnClickListener getAction(Context context) {
        return view -> {
            Intent intent = new Intent(context, UpdateStateService.class);
            intent.setAction(Constants.INTENT_ACTION_UPDATE);
            intent.putExtra(Constants.EXTRA_DOWNLOAD, Constants.EXTRA_DOWNLOAD_ACTION_START);
            context.startService(intent);
        };
    }

    @Override
    public boolean getProgressState() {
        return false;
    }
}
