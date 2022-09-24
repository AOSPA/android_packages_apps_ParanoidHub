package co.aospa.hub.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import co.aospa.hub.R;
import co.aospa.hub.misc.Constants;

public class OptInTestersActivity extends AppCompatActivity implements View.OnClickListener {

    private Button mEnroll;
    private SharedPreferences mPrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_Hub_NoActionBar);
        setContentView(R.layout.activity_opt_in_testers);

        mEnroll = findViewById(R.id.opt_in_testers_button);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    @Override
    public void onClick(View v) {
        if (v == mEnroll) {
            mPrefs.edit().putBoolean(Constants.PREF_ALLOW_TESTERS_UPDATES, true).apply();
            finish();
        }
    }
}
