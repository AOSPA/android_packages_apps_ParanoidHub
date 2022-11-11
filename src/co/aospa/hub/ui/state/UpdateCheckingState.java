package co.aospa.hub.ui.state;

import android.content.Context;
import android.view.View;

import co.aospa.hub.R;
import co.aospa.hub.ui.State;

public class UpdateCheckingState implements State {
    @Override
    public String getHeaderText(Context context) {
        return context.getResources().getString(R.string.system_update_checking_for_update);
    }

    @Override
    public String getDescriptionText(Context context) {
        return "";
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
        return true;
    }
}
