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
package co.aospa.hub;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import co.aospa.hub.model.Changelog;
import co.aospa.hub.model.Version;

public class ChangelogActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView mHeaderStatus;
    private TextView mUpdateDescription;

    private String mClLog;
    private String mClVersion;
    private String mClVersionNumber;
    private String mClBuildType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.changelog_activity);

        mHeaderStatus = findViewById(R.id.header_changelog_status);
        mUpdateDescription = findViewById(R.id.system_update_desc_detail);

        Button finishButton = findViewById(R.id.changelog_primary_button);
        finishButton.setOnClickListener(this);

        Intent i = getIntent();
        mClLog = i.getStringExtra("co.aospa.hub.CHANGELOG");
        mClVersion = i.getStringExtra("co.aospa.hub.CHANGELOG_VERSION");
        mClVersionNumber = i.getStringExtra("co.aospa.hub.CHANGELOG_VERSION_NUMBER");
        mClBuildType = i.getStringExtra("co.aospa.hub.CHANGELOG_BUILD_TYPE");

        createView();
    }

    private void createView() {
        mUpdateDescription.setMovementMethod(LinkMovementMethod.getInstance());
        boolean isBetaUpdate = mClBuildType.equals(Version.TYPE_BETA);

        if (mClLog != null) {
            String osName = getResources().getString(R.string.os_name);
            String header = String.format(getResources().getString(
                    R.string.update_found_changelog_header), osName, mClVersion, mClVersionNumber);
            String headerBeta = String.format(getResources().getString(
                    R.string.update_found_changelog_header_beta), osName, mClVersion, mClVersionNumber, mClBuildType);
            mHeaderStatus.setText(Html.fromHtml(isBetaUpdate ? headerBeta : header, Html.FROM_HTML_MODE_COMPACT));

            String description = String.format(getResources().getString(
                    R.string.update_found_changelog), mClLog);
            String descriptionBeta = String.format(getResources().getString(
                    R.string.update_found_changelog_beta), mClLog);
            mUpdateDescription.setText(Html.fromHtml(isBetaUpdate ? descriptionBeta : description, Html.FROM_HTML_MODE_COMPACT));
        } else {
            String defaultRes = getResources().getString(R.string.update_found_changelog_default);
            mUpdateDescription.setText(defaultRes);
        }
    }

    @Override
    public void onClick (View v) {
        finish();
    }
}