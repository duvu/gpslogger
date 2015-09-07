/*
*    This file is part of GPSLogger for Android.
*
*    GPSLogger for Android is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 2 of the License, or
*    (at your option) any later version.
*
*    GPSLogger for Android is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with GPSLogger for Android.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.umaps.gpslogger;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;

import com.umaps.gpslogger.common.PreferenceValidationFragment;
import com.umaps.gpslogger.common.Utilities;
import com.umaps.gpslogger.settings.GeneralSettingsFragment;
import com.umaps.gpslogger.settings.PerformanceSettingsFragment;

public class MainPreferenceActivity extends ActionBarActivity {
    private static final String TAG = "MainPreferenceActivity";
    PreferenceFragment preferenceFragment = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_preferences);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        String whichFragment = PreferenceConstants.GENERAL;

        if(getIntent().getExtras() != null) {
           whichFragment = getIntent().getExtras().getString("preference_fragment");
        }

        switch(whichFragment){
            case PreferenceConstants.GENERAL:
                setTitle(R.string.settings_screen_name);
                preferenceFragment = new GeneralSettingsFragment();
                break;
            case PreferenceConstants.PERFORMANCE:
                setTitle(R.string.pref_performance_title);
                preferenceFragment = new PerformanceSettingsFragment();
                break;
        }

        getFragmentManager().beginTransaction()
                .replace(R.id.content_frame, preferenceFragment)
                .commit();

    }


    @Override
    public void onBackPressed() {

       if(isFormValid()){
           super.onBackPressed();
       }
    }

    private boolean isFormValid(){
        if(preferenceFragment instanceof PreferenceValidationFragment){
            if( !((PreferenceValidationFragment)preferenceFragment).IsValid() ){
                Utilities.MsgBox(getString(R.string.autoemail_invalid_form),
                        getString(R.string.autoemail_invalid_form_message),
                        this);
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        final int id = item.getItemId();
        if (id == android.R.id.home) {
           return !isFormValid();
        }

        return false;
    }

    public static class PreferenceConstants{
        public static final String GENERAL = "GeneralSettingsFragment";
        public static final String LOGGING = "LoggingSettingsFragment";
        public static final String PERFORMANCE = "PerformanceSettingsFragment";
    }

}
