package co.aospa.hub.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import co.aospa.hub.HubUpdateManager;
import co.aospa.hub.R;
import co.aospa.hub.model.Changelog;
import co.aospa.hub.model.Update;
import co.aospa.hub.model.Version;

public class ChangelogActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView mHeaderStatus;
    private TextView mUpdateDescription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.changelog_activity);

        Intent i = getIntent();
        Changelog changelog = (Changelog) i.getSerializableExtra("changelog");
        Update update = (Update) i.getSerializableExtra("update");

        mHeaderStatus = findViewById(R.id.header_changelog_status);
        mUpdateDescription = findViewById(R.id.system_update_desc_detail);
        Button finishButton = findViewById(R.id.changelog_primary_button);
        finishButton.setOnClickListener(this);
        createView(changelog, update);
    }

    private void createView(Changelog changelog, Update update) {
        mUpdateDescription.setMovementMethod(LinkMovementMethod.getInstance());
        String clVersion = null;
        String clVersionNumber = null;
        String clBuildType = null;
        String clLog = null;
        if (changelog != null) {
            clVersion = changelog.getVersion();
            clVersionNumber = changelog.getVersionNumber();
            clBuildType = changelog.getBuildType();
            clLog = changelog.get();
        }

        boolean isBetaUpdate = false;
        String updateBuildType = null;
        if (update != null) {
            isBetaUpdate = update.getBuildType().equals(Version.TYPE_BETA);
            updateBuildType = update.getBuildType();
        }

        if (clLog != null) {
            String osName = getResources().getString(R.string.os_name);
            String header = String.format(getResources().getString(
                    R.string.update_found_changelog_header), osName, clVersion, clVersionNumber);
            String headerBeta = String.format(getResources().getString(
                    R.string.update_found_changelog_header_beta), osName, clVersion, clVersionNumber, updateBuildType);
            mHeaderStatus.setText(Html.fromHtml(isBetaUpdate ? headerBeta : header, Html.FROM_HTML_MODE_COMPACT));

            String description = String.format(getResources().getString(
                    R.string.update_found_changelog), clLog);
            String descriptionBeta = String.format(getResources().getString(
                    R.string.update_found_changelog_beta), clLog);
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
