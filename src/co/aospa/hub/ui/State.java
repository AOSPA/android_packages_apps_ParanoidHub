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
