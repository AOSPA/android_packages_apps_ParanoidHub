package co.aospa.hub;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.Objects;

import co.aospa.hub.model.Version;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ChangelogFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ChangelogFragment extends Fragment {

    private static final String CL_LOG = "log";
    private static final String CL_VERSION = "version";
    private static final String CL_VERSION_NUMBER = "version_number";
    private static final String CL_BUILD_TYPE = "build_type";

    private TextView mHeaderStatus;
    private TextView mUpdateDescription;

    private String mClLog;
    private String mClVersion;
    private String mClVersionNumber;
    private String mClBuildType;

    public ChangelogFragment() {
        // Required empty public constructor
    }

    public static ChangelogFragment newInstance(String clLog, String clVersion, String clVersionNumber, String clBuildType) {
        ChangelogFragment fragment = new ChangelogFragment();
        Bundle args = new Bundle();
        args.putString(CL_LOG, clLog);
        args.putString(CL_VERSION, clVersion);
        args.putString(CL_VERSION_NUMBER, clVersionNumber);
        args.putString(CL_BUILD_TYPE, clBuildType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mClLog = getArguments().getString(CL_LOG);
            mClVersion = getArguments().getString(CL_VERSION);
            mClVersionNumber = getArguments().getString(CL_VERSION_NUMBER);
            mClBuildType = getArguments().getString(CL_BUILD_TYPE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        requireContext().getTheme().applyStyle(R.style.Theme_Hub_NoActionBar, true);
        return inflater.inflate(R.layout.fragment_changelog, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mHeaderStatus = view.findViewById(R.id.header_changelog_status);
        mUpdateDescription = view.findViewById(R.id.system_update_desc_detail);
        createContent();
    }

    private void createContent() {
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
}