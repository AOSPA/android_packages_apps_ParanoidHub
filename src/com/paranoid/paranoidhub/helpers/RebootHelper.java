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

package com.paranoid.paranoidhub.helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.PowerManager;

import com.paranoid.paranoidhub.R;
import com.paranoid.paranoidhub.utils.UpdateUtils;

import java.io.File;
import java.io.FileOutputStream;

public class RebootHelper {

    private RecoveryHelper mRecoveryHelper;

    public RebootHelper(RecoveryHelper recoveryHelper) {
        mRecoveryHelper = recoveryHelper;
    }

    public void showRebootDialog(final Context context, final String[] items, boolean toRecovery) {

        if (items == null || items.length == 0) {
            return;
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.alert_reboot_install_title);

        alert.setMessage(context.getResources().getString(R.string.alert_reboot_one_message));

        alert.setPositiveButton(R.string.alert_reboot_now, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();

                reboot(context, items, toRecovery);

            }
        });

        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        alert.show();
    }

    private void reboot(final Context context, final String[] items, boolean toRecovery) {

        try {

            File f = new File("/cache/recovery/command");
            f.delete();

            int[] recoveries = new int[]{
                    UpdateUtils.TWRP, UpdateUtils.CWM_BASED
            };

            for (int recovery : recoveries) {
                String file = mRecoveryHelper.getCommandsFile(recovery);

                FileOutputStream os = null;
                try {
                    os = new FileOutputStream("/cache/recovery/" + file, false);

                    String[] files = new String[items.length];
                    for (int k = 0; k < files.length; k++) {
                        files[k] = mRecoveryHelper.getRecoveryFilePath(recovery, items[k]);
                    }

                    String[] commands = mRecoveryHelper.getCommands(recovery, files, items);
                    if (commands != null) {
                        int size = commands.length, j = 0;
                        for (; j < size; j++) {
                            os.write((commands[j] + "\n").getBytes("UTF-8"));
                        }
                    }
                } finally {
                    if (os != null) {
                        os.close();
                        UpdateUtils.setPermissions("/cache/recovery/" + file, 0644,
                                android.os.Process.myUid(), 2001);
                    }
                }
            }

            ((PowerManager) context.getSystemService(Activity.POWER_SERVICE)).reboot(toRecovery? "recovery" : null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
