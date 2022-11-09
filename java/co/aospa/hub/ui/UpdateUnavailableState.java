package co.aospa.hub.ui;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import co.aospa.hub.R;
import co.aospa.hub.UpdateStateService;
import co.aospa.hub.util.Constants;
import co.aospa.hub.util.PreferenceHelper;
import co.aospa.hub.util.StringGenerator;
import co.aospa.hub.util.Version;

public class UpdateUnavailableState implements State {

    private Context context;

    private String getNoUpdateDescription(Context context) {
        PreferenceHelper preferenceHelper = new PreferenceHelper(context);
        long lastChecked = preferenceHelper.getLongValueByKey(Constants.KEY_LAST_UPDATE_CHECK) / 1000;
        final String desc = context.getResources().getString(
                R.string.system_update_update_to_date_desc,
                Version.getCurrentVersion(),
                Version.getCurrentVersionNumber(),
                StringGenerator.getTimeLocalized(context, lastChecked));
        return desc;
    }

    @Override
    public String getHeaderText(Context context) {
        return context.getResources().getString(R.string.system_update_update_to_date);
    }

    @Override
    public String getDescriptionText(Context context) {
        return getNoUpdateDescription(context);
    }

    @Override
    public String getActionText(Context context) {
        return context.getResources().getString(R.string.system_update_update_to_date_button);
    }

    @Override
    public View.OnClickListener getAction(Context context) {
        return view -> {
            Intent intent = new Intent(context, UpdateStateService.class);
            intent.setAction(Constants.INTENT_ACTION_CHECK_UPDATES);
            context.startService(intent);
        };
    }

    @Override
    public boolean getProgressState() {
        return false;
    }
}
