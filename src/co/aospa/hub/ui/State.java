package co.aospa.hub.ui;

import android.content.Context;
import android.view.View;

public interface State {

    String getHeaderText(Context context);
    String getStepperText(Context context);
    String getDescriptionText(Context context);
    String getActionText(Context context);
    View.OnClickListener getAction(Context context);
    String getSecondaryActionText(Context context);
    View.OnClickListener getSecondaryAction(Context context);
    boolean getProgressState();
}
