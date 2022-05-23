package co.aospa.hub.views;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import co.aospa.hub.R;
import co.aospa.hub.model.Changelog;
import co.aospa.hub.model.Version;

public class ChangelogDialog extends AlertDialog {

    private Context mContext;
    private View mDialogView;

    private Button mDoneButton;
    private TextView mHeaderStatus;
    private TextView mUpdateDescription;

    private String mClLog;
    private String mClVersion;
    private String mClVersionNumber;
    private String mClBuildType;

    public ChangelogDialog(Context context, Changelog changelog) {
        super(context);
        mContext = context;

        mClLog = changelog.get();
        mClVersion = changelog.getVersion();
        mClVersionNumber = changelog.getVersionNumber();
        mClBuildType = changelog.getBuildType();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDialogView = LayoutInflater.from(mContext).inflate(R.layout.changelog_dialog,
                null);
        final Window window = getWindow();
        window.setContentView(mDialogView);
        window.setWindowAnimations(android.R.style.Animation_InputMethod);

        mHeaderStatus = mDialogView.requireViewById(R.id.header_changelog_status);
        mUpdateDescription = mDialogView.requireViewById(R.id.system_update_desc_detail);

        mDoneButton = mDialogView.requireViewById(R.id.done_button);
        mDoneButton.setOnClickListener(v -> dismiss());

        createView();
    }

    private void createView() {
        mUpdateDescription.setMovementMethod(LinkMovementMethod.getInstance());
        boolean isBetaUpdate = mClBuildType.equals(Version.TYPE_BETA);

        if (mClLog != null) {
            String osName = mContext.getResources().getString(R.string.os_name);
            String header = String.format(mContext.getResources().getString(
                    R.string.update_found_changelog_header), osName, mClVersion, mClVersionNumber);
            String headerBeta = String.format(mContext.getResources().getString(
                    R.string.update_found_changelog_header_beta), osName, mClVersion, mClVersionNumber, mClBuildType);
            mHeaderStatus.setText(Html.fromHtml(isBetaUpdate ? headerBeta : header, Html.FROM_HTML_MODE_COMPACT));

            String description = String.format(mContext.getResources().getString(
                    R.string.update_found_changelog), mClLog);
            String descriptionBeta = String.format(mContext.getResources().getString(
                    R.string.update_found_changelog_beta), mClLog);
            mUpdateDescription.setText(Html.fromHtml(isBetaUpdate ? descriptionBeta : description, Html.FROM_HTML_MODE_COMPACT));
        } else {
            String defaultRes = mContext.getResources().getString(R.string.update_found_changelog_default);
            mUpdateDescription.setText(defaultRes);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mDoneButton.setOnClickListener(null);
    }
}
