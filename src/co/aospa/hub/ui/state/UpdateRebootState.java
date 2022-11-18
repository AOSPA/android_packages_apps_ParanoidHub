/*
 * Copyright (C) 2022 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.aospa.hub.ui.state;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import co.aospa.hub.R;
import co.aospa.hub.UpdateStateService;
import co.aospa.hub.ui.State;
import co.aospa.hub.util.Constants;

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
    public String getSecondaryActionText(Context context) {
        return null;
    }

    @Override
    public View.OnClickListener getSecondaryAction(Context context) {
        return null;
    }

    @Override
    public boolean getProgressState() {
        return false;
    }
}
