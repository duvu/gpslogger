/*******************************************************************************
 * This file is part of GPSLogger for Android.
 *
 * GPSLogger for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * GPSLogger for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GPSLogger for Android.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package com.umaps.gpslogger;


import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.umaps.gpslogger.common.EventBusHook;
import com.umaps.gpslogger.common.Session;
import com.umaps.gpslogger.common.Utilities;
import com.umaps.gpslogger.common.events.ServiceEvents;
import com.umaps.gpslogger.common.events.UploadEvents;
import com.umaps.gpslogger.views.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import de.greenrobot.event.EventBus;

public class GpsMainActivity extends ActionBarActivity
        implements
        Toolbar.OnMenuItemClickListener {
    private static final String TAG = "GpsMainActivity";
    private static boolean userInvokedUpload;
    private static Intent serviceIntent;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPresetProperties();
        setContentView(R.layout.activity_gps_main);

        SetUpToolbar();
        LoadDefaultFragmentView();
        StartAndBindService();
        RegisterEventBus();
    }

    private void RegisterEventBus() {
        EventBus.getDefault().register(this);
    }

    private void UnregisterEventBus(){
        try {
        EventBus.getDefault().unregister(this);
        } catch (Throwable t){
            //this may crash if registration did not go through. just be safe
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        StartAndBindService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        GetPreferences();
        StartAndBindService();
        SetBulbStatus(Session.isStarted());
    }

    @Override
    protected void onPause() {
        StopAndUnbindServiceIfRequired();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        StopAndUnbindServiceIfRequired();
        UnregisterEventBus();
        super.onDestroy();

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        //drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        //drawerToggle.onConfigurationChanged(newConfig);
    }


    private void loadPresetProperties() {
        //Either look for /<appfolder>/gpslogger.properties or /sdcard/gpslogger.properties
        File file =  new File(Utilities.GetDefaultStorageFolder(getApplicationContext()) + "/gpslogger.properties");
        if(!file.exists()){
            file = new File(Environment.getExternalStorageDirectory() + "/gpslogger.properties");
            if(!file.exists()){
                return;
            }
        }

        try {
            Properties props = new Properties();
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file));
            props.load(reader);

            for(Object key : props.keySet()){

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = prefs.edit();

                String value = props.getProperty(key.toString());
                Log.i(TAG, "Setting preset property: " + key.toString() + " to " + value.toString());

                if(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")){
                    editor.putBoolean(key.toString(), Boolean.parseBoolean(value));
                }
                else if(key.equals("listeners")){
                    List<String> availableListeners = Utilities.GetListeners();
                    Set<String> chosenListeners = new HashSet<>();
                    String[] csvListeners = value.split(",");
                    for(String l : csvListeners){
                        if(availableListeners.contains(l)){
                            chosenListeners.add(l);
                        }
                    }
                    if(chosenListeners.size() > 0){
                        prefs.edit().putStringSet("listeners", chosenListeners).apply();
                    }

                } else {
                    editor.putString(key.toString(), value);
                }
                editor.apply();
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not load preset properties", e);
        }
    }

    public void SetUpToolbar(){
        try{
            toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayShowTitleEnabled(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Window window = getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            }
        }
        catch(Exception ex){
            //http://stackoverflow.com/questions/26657348/appcompat-v7-v21-0-0-causing-crash-on-samsung-devices-with-android-v4-2-2
            Log.e (TAG, "Thanks for this, Samsung", ex);
        }

        ImageButton helpButton = (ImageButton) findViewById(R.id.imgHelp);
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), InformationActivity.class);
                startActivity(intent);
            }
        });
    }

    private void LoadDefaultFragmentView() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.container, GpsSimpleViewFragment.newInstance());
        transaction.commitAllowingStateLoss();
    }

    private GenericViewFragment GetCurrentFragment(){
        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.container);
        if (currentFragment instanceof GenericViewFragment) {
            return ((GenericViewFragment) currentFragment);
        }
        return null;
    }

    /*@Override
    public boolean onNavigationItemSelected(int position, long itemId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("SPINNER_SELECTED_POSITION", position);
        editor.apply();

        LoadFragmentView(position);
        return true;
    }*/


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SetBulbStatus(Session.isStarted());
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        Log.d(TAG, "Menu Item: " + String.valueOf(item.getTitle()));

        switch (id) {
            /*case R.id.mnuAnnotate:
                //Annotate();
                LaunchPreferenceScreen(MainPreferenceActivity.PreferenceConstants.OPENGTS);
                return true;
            case R.id.mnuOnePoint:
                LogSinglePoint();
                return true;
            case R.id.mnuShare:
                Share();
                return true;
            case R.id.mnuOpenGTS:
                SendToOpenGTS();
                return true;*/
            default:
                return true;
        }
    }



    /*private void SendToOpenGTS() {
        if (!Utilities.IsOpenGTSSetup()) {
            LaunchPreferenceScreen(MainPreferenceActivity.PreferenceConstants.OPENGTS);
        } else {
            IFileSender fs = FileSenderFactory.GetOpenGTSSender(getApplicationContext());
            //ShowFileListDialog(fs);
        }
    }*/

    /*private void ShowFileListDialog(final IFileSender sender) {

        if (!Utilities.isNetworkAvailable(this)) {
            Utilities.MsgBox(getString(R.string.sorry),getString(R.string.no_network_message), this);
            return;
        }

        final File gpxFolder = new File(AppSettings.getGpsLoggerFolder());

        if (gpxFolder != null && gpxFolder.exists() && Utilities.GetFilesInFolder(gpxFolder, sender).length > 0) {
            File[] enumeratedFiles = Utilities.GetFilesInFolder(gpxFolder, sender);

            //Order by last modified
            Arrays.sort(enumeratedFiles, new Comparator<File>() {
                public int compare(File f1, File f2) {
                    if (f1 != null && f2 != null) {
                        return -1 * Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
                    }
                    return -1;
                }
            });

            List<String> fileList = new ArrayList<String>(enumeratedFiles.length);

            for (File f : enumeratedFiles) {
                fileList.add(f.getName());
            }

            final String[] files = fileList.toArray(new String[fileList.size()]);

            new MaterialDialog.Builder(this)
                    .title(R.string.osm_pick_file)
                    .items(files)
                    .positiveText(R.string.ok)
                    .itemsCallbackMultiChoice(null, new MaterialDialog.ListCallbackMultiChoice() {
                        @Override
                        public boolean onSelection(MaterialDialog materialDialog, Integer[] integers, CharSequence[] charSequences) {

                            List<Integer> selectedItems = Arrays.asList(integers);

                            List<File> chosenFiles = new ArrayList<File>();

                            for (Object item : selectedItems) {
                                Log.i(TAG, "Selected file to upload- " + files[Integer.valueOf(item.toString())]);
                                chosenFiles.add(new File(gpxFolder, files[Integer.valueOf(item.toString())]));
                            }

                            if (chosenFiles.size() > 0) {
                                Utilities.ShowProgress(GpsMainActivity.this, getString(R.string.please_wait),
                                        getString(R.string.please_wait));
                                userInvokedUpload = true;
                                sender.UploadFile(chosenFiles);

                            }
                            return true;
                        }
                    }).show();

        } else {
            Utilities.MsgBox(getString(R.string.sorry), getString(R.string.no_files_found), this);
        }
    }*/

    /**
     * Provides a connection to the GPS Logging Service
     */
    private final ServiceConnection gpsServiceConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Disconnected from GPSLoggingService from MainActivity");
            //loggingService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Connected to GPSLoggingService from MainActivity");
            //loggingService = ((GpsLoggingService.GpsLoggingBinder) service).getService();
        }
    };


    /**
     * Starts the service and binds the activity to it.
     */
    private void StartAndBindService() {
        serviceIntent = new Intent(this, GpsLoggingService.class);
        // Start the service in case it isn't already running
        startService(serviceIntent);
        // Now bind to service
        bindService(serviceIntent, gpsServiceConnection, Context.BIND_AUTO_CREATE);
        Session.setBoundToService(true);
    }


    /**
     * Stops the service if it isn't logging. Also unbinds.
     */
    private void StopAndUnbindServiceIfRequired() {
        if (Session.isBoundToService()) {

            try {
                unbindService(gpsServiceConnection);
                Session.setBoundToService(false);
            } catch (Exception e) {
                Log.w(TAG, "Could not unbind service", e);
            }
        }

        if (!Session.isStarted()) {
            Log.d(TAG, "Stopping the service");
            try {
                stopService(serviceIntent);
            } catch (Exception e) {
                Log.e (TAG, "Could not stop the service", e);
            }
        }
    }

    private void SetBulbStatus(boolean started) {
        ImageView bulb = (ImageView) findViewById(R.id.notification_bulb);
        bulb.setImageResource(started ? R.drawable.circle_green : R.drawable.circle_none);
    }


    public void OnWaitingForLocation(boolean inProgress) {
        ProgressBar fixBar = (ProgressBar) findViewById(R.id.progressBarGpsFix);
        fixBar.setVisibility(inProgress ? View.VISIBLE : View.INVISIBLE);
    }


    private void GetPreferences() {
        Utilities.PopulateAppSettings(getApplicationContext());
    }


    @EventBusHook
    public void onEventMainThread(UploadEvents.OpenGTS upload){
        Log.d(TAG, "Open GTS Event completed, success: " + upload.success);
        Utilities.HideProgress();

        if(!upload.success){
            Log.e(TAG, getString(R.string.opengts_setup_title)
                    + "-"
                    + getString(R.string.upload_failure));

            if(userInvokedUpload){
                Utilities.MsgBox(getString(R.string.sorry),getString(R.string.upload_failure), this);
                userInvokedUpload = false;
            }
        }
    }

    @EventBusHook
    public void onEventMainThread(ServiceEvents.WaitingForLocation waitingForLocation){
        OnWaitingForLocation(waitingForLocation.waiting);
    }

    @EventBusHook
    public void onEventMainThread(ServiceEvents.LoggingStatus loggingStatus){
        SetBulbStatus(Session.isStarted());
    }
}
