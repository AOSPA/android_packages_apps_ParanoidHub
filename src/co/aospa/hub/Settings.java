package co.aospa.hub;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserManager;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.Preference;
import androidx.preference.ListPreference;

public class Settings extends AppCompatActivity {
    private static final String KEY_CHECK_FOR_UDPATES = "check_for_updates";
    static final String KEY_WAITING_FOR_REBOOT = "waiting_for_reboot";

    static SharedPreferences getPreferences(final Context context) {
        final Context deviceContext = context.createDeviceProtectedStorageContext();
        return PreferenceManager.getDefaultSharedPreferences(deviceContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UserManager userManager = (UserManager) getSystemService(USER_SERVICE);
        if (!userManager.isSystemUser()) {
            throw new SecurityException("system user only");
        }
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Closes the activity when the up action is clicked
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setStorageDeviceProtected();
            setPreferencesFromResource(R.xml.settings, rootKey);

            Preference.OnPreferenceClickListener clickListener = preference -> {
                if (!getPreferences(requireContext()).getBoolean(KEY_WAITING_FOR_REBOOT, false)) {
                    PeriodicJob.schedule(requireContext(), true);
                }
                return true;
            };
            final Preference checkForUpdates = findPreference(KEY_CHECK_FOR_UDPATES);
            if (checkForUpdates != null) {
                checkForUpdates.setOnPreferenceClickListener(clickListener);
            }
        }
    }
}
