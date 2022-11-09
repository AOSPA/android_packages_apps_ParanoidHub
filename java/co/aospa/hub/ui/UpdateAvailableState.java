package co.aospa.hub.ui;

import android.content.Context;
import android.view.View;

public class UpdateAvailableState implements State {
    @Override
    public String getHeaderText(Context context) {
        return null;
    }

    @Override
    public String getDescriptionText(Context context) {
        return null;
    }

    @Override
    public String getActionText(Context context) {
        return null;
    }

    @Override
    public View.OnClickListener getAction(Context context) {
        return view -> {

        };
    }

    @Override
    public boolean getProgressState() {
        return false;
    }
}
