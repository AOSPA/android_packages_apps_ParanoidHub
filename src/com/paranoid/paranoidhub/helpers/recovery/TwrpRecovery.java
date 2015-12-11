/*
 * Copyright 2014 ParanoidAndroid Project
 *
 * This file is part of Paranoid OTA.
 *
 * Paranoid OTA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Paranoid OTA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Paranoid OTA.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.paranoid.paranoidhub.helpers.recovery;

import android.content.Context;

import com.paranoid.paranoidhub.utils.UpdateUtils;

import java.util.ArrayList;
import java.util.List;

public class TwrpRecovery extends RecoveryInfo {

    public TwrpRecovery() {
        super();

        setId(UpdateUtils.TWRP);
        setName("twrp");
        setInternalSdcard("sdcard");
        setExternalSdcard("external_sd");
    }

    @Override
    public String getCommandsFile() {
        return "openrecoveryscript";
    }

    @Override
    public String[] getCommands(Context context, String[] items, String[] originalItems)
            throws Exception {

        List<String> commands = new ArrayList<>();
        for (String item : items) {
            commands.add("install " + item);
        }

        return commands.toArray(new String[commands.size()]);

    }
}
