package com.umaps.gpslogger;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.umaps.gpslogger.common.AppSettings;

/**
 * Created by beou on 25/08/2015.
 */
public class InformationActivity extends ActionBarActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_infomation);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView txtDeviceId = (TextView) findViewById(R.id.title_device_id);
        txtDeviceId.setText(AppSettings.getImei());

    }


}
