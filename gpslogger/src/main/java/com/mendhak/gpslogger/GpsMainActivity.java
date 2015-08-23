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

package com.mendhak.gpslogger;


import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.internal.view.menu.ActionMenuItemView;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.mendhak.gpslogger.common.AppSettings;
import com.mendhak.gpslogger.common.EventBusHook;
import com.mendhak.gpslogger.common.Session;
import com.mendhak.gpslogger.common.Utilities;
import com.mendhak.gpslogger.common.events.CommandEvents;
import com.mendhak.gpslogger.common.events.ServiceEvents;
import com.mendhak.gpslogger.common.events.UploadEvents;
import com.mendhak.gpslogger.senders.FileSenderFactory;
import com.mendhak.gpslogger.senders.IFileSender;
import com.mendhak.gpslogger.views.*;
import com.mikepenz.iconics.typeface.FontAwesome;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.accountswitcher.AccountHeader;
import com.mikepenz.materialdrawer.accountswitcher.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import de.greenrobot.event.EventBus;

public class GpsMainActivity extends ActionBarActivity
        implements
        Toolbar.OnMenuItemClickListener,
        ActionBar.OnNavigationListener {
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
        SetUpNavigationDrawer();
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

        if (Session.hasDescription()) {
            SetAnnotationReady();
        }

        enableDisableMenuItems();
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


    /**
     * Helper method, launches activity in a delayed handler, less stutter
     */
    private void LaunchPreferenceScreen(final String whichFragment) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent targetActivity = new Intent(getApplicationContext(), MainPreferenceActivity.class);
                targetActivity.putExtra("preference_fragment", whichFragment);
                startActivity(targetActivity);
            }
        }, 250);
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
    }

    public void SetUpNavigationDrawer() {
        IProfile profile = new ProfileDrawerItem()
                .withName("Du Quang Vu")
                .withEmail("hoaivubk@gmail.com")
                .withIcon("https://avatars3.githubusercontent.com/u/1476232?v=3&s=460");

        AccountHeader header = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.drawable.header)
                .addProfiles(profile).build();

        DrawerBuilder drawerBuilder = new DrawerBuilder()
                .withActivity(this)
                .withAccountHeader(header)
                .withToolbar(toolbar)
                .addDrawerItems(
                        new PrimaryDrawerItem()
                                .withName(R.string.pref_administration)
                                .withDescription(R.string.pref_login_to_manage)
                                .withIcon(FontAwesome.Icon.faw_archive)
                                .withIdentifier(1001),
                        new PrimaryDrawerItem()
                                .withName(R.string.pref_general_title)
                                .withDescription(R.string.pref_general_summary)
                                .withIcon(FontAwesome.Icon.faw_cog)
                                .withIdentifier(1000),
                        new PrimaryDrawerItem()
                                .withName(R.string.pref_performance_title)
                                .withDescription(R.string.pref_performance_summary)
                                .withIcon(FontAwesome.Icon.faw_bar_chart)
                                .withIdentifier(2),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem()
                                .withName(R.string.opengts_setup_title)
                                .withIcon(FontAwesome.Icon.faw_cab)
                                .withIdentifier(8),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem()
                                .withName(R.string.menu_faq)
                                .withIcon(FontAwesome.Icon.faw_support)
                                .withIdentifier(11),
                        new PrimaryDrawerItem()
                                .withName(R.string.menu_exit)
                                .withIcon(FontAwesome.Icon.faw_sign_out)
                                .withIdentifier(12)

                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(AdapterView<?> adapterView, View view, int i, long l, IDrawerItem iDrawerItem) {

                        if (iDrawerItem != null) {
                            int iden = iDrawerItem.getIdentifier();
                            switch (iden) {
                                case 1000:
                                    LaunchPreferenceScreen(MainPreferenceActivity.PreferenceConstants.GENERAL);
                                    break;
                                case 2:
                                    LaunchPreferenceScreen(MainPreferenceActivity.PreferenceConstants.PERFORMANCE);
                                    break;
                                case 8:
                                    LaunchPreferenceScreen(MainPreferenceActivity.PreferenceConstants.OPENGTS);
                                    break;
                                case 11:
                                    //Intent faqtivity = new Intent(getApplicationContext(), Faqtivity.class);
                                    //startActivity(faqtivity);
                                    break;
                                case 12:
                                    EventBus.getDefault().post(new CommandEvents.RequestStartStop(false));
                                    finish();
                                    break;
                            }
                        }

                        return false;
                    }
                });
        drawerBuilder.build();

        ImageButton helpButton = (ImageButton) findViewById(R.id.imgHelp);
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Intent faqtivity = new Intent(getApplicationContext(), Faqtivity.class);
                //startActivity(faqtivity);
            }
        });
    }

    private int GetUserSelectedNavigationItem(){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return sp.getInt("SPINNER_SELECTED_POSITION", 0);
    }

    private void LoadDefaultFragmentView() {
        int currentSelectedPosition = GetUserSelectedNavigationItem();
        LoadFragmentView(currentSelectedPosition);
    }

    private void LoadFragmentView(int position){
        FragmentTransaction transaction = getFragmentManager().beginTransaction();

        switch (position) {
            default:
            case 0:
                transaction.replace(R.id.container, GpsSimpleViewFragment.newInstance());
                break;
        }
        transaction.commitAllowingStateLoss();
    }

    private GenericViewFragment GetCurrentFragment(){
        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.container);
        if (currentFragment instanceof GenericViewFragment) {
            return ((GenericViewFragment) currentFragment);
        }
        return null;
    }

    @Override
    public boolean onNavigationItemSelected(int position, long itemId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("SPINNER_SELECTED_POSITION", position);
        editor.apply();

        LoadFragmentView(position);
        return true;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        Toolbar toolbarBottom = (Toolbar) findViewById(R.id.toolbarBottom);

        if(toolbarBottom.getMenu().size() > 0){ return true;}

        toolbarBottom.inflateMenu(R.menu.gps_main);
        setupEvenlyDistributedToolbar();
        toolbarBottom.setOnMenuItemClickListener(this);

        enableDisableMenuItems();
        return true;
    }

    public void setupEvenlyDistributedToolbar(){
        //http://stackoverflow.com/questions/26489079/evenly-spaced-menu-items-on-toolbar

        // Use Display metrics to get Screen Dimensions
        Display display = getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);

        // Toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbarBottom);

        // Add 10 spacing on either side of the toolbar
        toolbar.setContentInsetsAbsolute(10, 10);

        // Get the ChildCount of your Toolbar, this should only be 1
        int childCount = toolbar.getChildCount();
        // Get the Screen Width in pixels
        int screenWidth = metrics.widthPixels;

        // Create the Toolbar Params based on the screenWidth
        Toolbar.LayoutParams toolbarParams = new Toolbar.LayoutParams(screenWidth, Toolbar.LayoutParams.WRAP_CONTENT);

        // Loop through the child Items
        for(int i = 0; i < childCount; i++){
            // Get the item at the current index
            View childView = toolbar.getChildAt(i);
            // If its a ViewGroup
            if(childView instanceof ViewGroup){
                // Set its layout params
                childView.setLayoutParams(toolbarParams);
                // Get the child count of this view group, and compute the item widths based on this count & screen size
                int innerChildCount = ((ViewGroup) childView).getChildCount();
                int itemWidth  = (screenWidth / innerChildCount);
                // Create layout params for the ActionMenuView
                ActionMenuView.LayoutParams params = new ActionMenuView.LayoutParams(itemWidth, Toolbar.LayoutParams.WRAP_CONTENT);
                // Loop through the children
                for(int j = 0; j < innerChildCount; j++){
                    View grandChild = ((ViewGroup) childView).getChildAt(j);
                    if(grandChild instanceof ActionMenuItemView){
                        // set the layout parameters on each View
                        grandChild.setLayoutParams(params);
                    }
                }
            }
        }
    }

    private void enableDisableMenuItems() {

        OnWaitingForLocation(Session.isWaitingForLocation());
        SetBulbStatus(Session.isStarted());

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbarBottom);
        MenuItem mnuAnnotate = toolbar.getMenu().findItem(R.id.mnuAnnotate);
        MenuItem mnuOnePoint = toolbar.getMenu().findItem(R.id.mnuOnePoint);
        MenuItem mnuAutoSendNow = toolbar.getMenu().findItem(R.id.mnuAutoSendNow);

        if (mnuOnePoint != null) {
            mnuOnePoint.setEnabled(!Session.isStarted());
            mnuOnePoint.setIcon((Session.isStarted() ? R.drawable.singlepoint_disabled : R.drawable.singlepoint));
        }

        if (mnuAutoSendNow != null) {
            mnuAutoSendNow.setEnabled(Session.isStarted());
        }

        if (mnuAnnotate != null) {
            if (Session.isAnnotationMarked()) {
                mnuAnnotate.setIcon(R.drawable.annotate2_active);
            } else {
                mnuAnnotate.setIcon(R.drawable.annotate2);
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        Log.d(TAG, "Menu Item: " + String.valueOf(item.getTitle()));

        switch (id) {
            case R.id.mnuAnnotate:
                Annotate();
                return true;
            case R.id.mnuOnePoint:
                LogSinglePoint();
                return true;
            case R.id.mnuShare:
                Share();
                return true;
            case R.id.mnuOpenGTS:
                SendToOpenGTS();
                return true;
            default:
                return true;
        }
    }


    private void LogSinglePoint() {
        EventBus.getDefault().post(new CommandEvents.LogOnce());
        enableDisableMenuItems();
    }

    /**
     * Annotates GPX and KML files, TXT files are ignored.
     * The user is prompted for the content of the <name> tag. If a valid
     * description is given, the logging service starts in single point mode.
     */
    private void Annotate() {
        MaterialDialog alertDialog = new MaterialDialog.Builder(GpsMainActivity.this)
                .title(R.string.add_description)
                .customView(R.layout.alertview, true)
                .positiveText(R.string.ok)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        EditText userInput = (EditText) dialog.getCustomView().findViewById(R.id.alert_user_input);
                        EventBus.getDefault().postSticky(new CommandEvents.Annotate(userInput.getText().toString()));
                    }
                }).build();

        EditText userInput = (EditText) alertDialog.getCustomView().findViewById(R.id.alert_user_input);
        userInput.setText(Session.getDescription());
        TextView tvMessage = (TextView)alertDialog.getCustomView().findViewById(R.id.alert_user_message);
        tvMessage.setText(R.string.letters_numbers);
        alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        alertDialog.show();
    }

    private void SendToOpenGTS() {
        if (!Utilities.IsOpenGTSSetup()) {
            LaunchPreferenceScreen(MainPreferenceActivity.PreferenceConstants.OPENGTS);
        } else {
            IFileSender fs = FileSenderFactory.GetOpenGTSSender(getApplicationContext());
            //ShowFileListDialog(fs);
        }
    }

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
     * Allows user to send a GPX/KML file along with location, or location only
     * using a provider. 'Provider' means any application that can accept such
     * an intent (Facebook, SMS, Twitter, Email, K-9, Bluetooth)
     */
    private void Share() {

//        try {
//
//            final String locationOnly = getString(R.string.sharing_location_only);
//            final File gpxFolder = new File(AppSettings.getGpsLoggerFolder());
//            if (gpxFolder.exists()) {
//
//                File[] enumeratedFiles = Utilities.GetFilesInFolder(gpxFolder);
//
//                Arrays.sort(enumeratedFiles, new Comparator<File>() {
//                    public int compare(File f1, File f2) {
//                        return -1 * Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
//                    }
//                });
//
//                List<String> fileList = new ArrayList<String>(enumeratedFiles.length);
//
//                for (File f : enumeratedFiles) {
//                    fileList.add(f.getName());
//                }
//
//                fileList.add(0, locationOnly);
//                final String[] files = fileList.toArray(new String[fileList.size()]);
//
//                new MaterialDialog.Builder(this)
//                        .title(R.string.osm_pick_file)
//                        .items(files)
//                        .positiveText(R.string.ok)
//                        .itemsCallbackMultiChoice(null, new MaterialDialog.ListCallbackMultiChoice() {
//                            @Override
//                            public boolean onSelection(MaterialDialog materialDialog, Integer[] integers, CharSequence[] charSequences) {
//                                List<Integer> selectedItems = Arrays.asList(integers);
//
//                                final Intent intent = new Intent(Intent.ACTION_SEND);
//                                intent.setType("*/*");
//
//                                if (selectedItems.size() <= 0) {
//                                    return false;
//                                }
//
//                                if (selectedItems.contains(0)) {
//
//                                    intent.setType("text/plain");
//
//                                    intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.sharing_mylocation));
//                                    if (Session.hasValidLocation()) {
//                                        String bodyText = getString(R.string.sharing_googlemaps_link,
//                                                String.valueOf(Session.getCurrentLatitude()),
//                                                String.valueOf(Session.getCurrentLongitude()));
//                                        intent.putExtra(Intent.EXTRA_TEXT, bodyText);
//                                        intent.putExtra("sms_body", bodyText);
//                                        startActivity(Intent.createChooser(intent, getString(R.string.sharing_via)));
//                                    }
//                                } else {
//
//                                    intent.setAction(Intent.ACTION_SEND_MULTIPLE);
//                                    intent.putExtra(Intent.EXTRA_SUBJECT, "Here are some files.");
//                                    intent.setType("*/*");
//
//                                    ArrayList<Uri> chosenFiles = new ArrayList<Uri>();
//
//                                    for (Object path : selectedItems) {
//                                        File file = new File(gpxFolder, files[Integer.valueOf(path.toString())]);
//                                        Uri uri = Uri.fromFile(file);
//                                        chosenFiles.add(uri);
//                                    }
//
//                                    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, chosenFiles);
//                                    startActivity(Intent.createChooser(intent, getString(R.string.sharing_via)));
//                                }
//                                return true;
//                            }
//                        }).show();
//
//
//            } else {
//                Utilities.MsgBox(getString(R.string.sorry), getString(R.string.no_files_found), this);
//            }
//        } catch (Exception ex) {
//            Log.e (TAG, "Sharing problem", ex);
//        }
    }


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

    public void SetAnnotationReady() {
        Session.setAnnotationMarked(true);
        enableDisableMenuItems();
    }

    public void SetAnnotationDone() {
        Session.setAnnotationMarked(false);
        enableDisableMenuItems();
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
            Log.e (TAG, getString(R.string.opengts_setup_title)
                    + "-"
                    + getString(R.string.upload_failure));

            if(userInvokedUpload){
                Utilities.MsgBox(getString(R.string.sorry),getString(R.string.upload_failure), this);
                userInvokedUpload = false;
            }
        }
    }

    @EventBusHook
    public void onEventMainThread(UploadEvents.AutoEmail upload){
        Log.d(TAG, "Auto Email Event completed, success: " + upload.success);
        Utilities.HideProgress();

        if(!upload.success){
            Log.e (TAG, getString(R.string.autoemail_title)
                    + "-"
                    + getString(R.string.upload_failure));
            if(userInvokedUpload){
                Utilities.MsgBox(getString(R.string.sorry),getString(R.string.upload_failure), this);
                userInvokedUpload = false;
            }
        }
    }

    @EventBusHook
    public void onEventMainThread(UploadEvents.OpenStreetMap upload){
        Log.d(TAG, "OSM Event completed, success: " + upload.success);
        Utilities.HideProgress();

        if(!upload.success){
            Log.e (TAG, getString(R.string.osm_setup_title)
                    + "-"
                    + getString(R.string.upload_failure));
            if(userInvokedUpload){
                Utilities.MsgBox(getString(R.string.sorry),getString(R.string.upload_failure), this);
                userInvokedUpload = false;
            }
        }
    }

    @EventBusHook
    public void onEventMainThread(UploadEvents.Dropbox upload){
        Log.d(TAG, "Dropbox Event completed, success: " + upload.success);
        Utilities.HideProgress();

        if(!upload.success){
            Log.e (TAG, getString(R.string.dropbox_setup_title)
                    + "-"
                    + getString(R.string.upload_failure));
            if(userInvokedUpload){
                Utilities.MsgBox(getString(R.string.sorry),getString(R.string.upload_failure), this);
                userInvokedUpload = false;
            }
        }
    }

    @EventBusHook
    public void onEventMainThread(UploadEvents.GDocs upload){
        Log.d(TAG, "GDocs Event completed, success: " + upload.success);
        Utilities.HideProgress();

        if(!upload.success){
            Log.e (TAG, getString(R.string.gdocs_setup_title)
                    + "-"
                    + getString(R.string.upload_failure));
            if(userInvokedUpload){
                Utilities.MsgBox(getString(R.string.sorry),getString(R.string.upload_failure), this);
                userInvokedUpload = false;
            }
        }
    }

    @EventBusHook
    public void onEventMainThread(UploadEvents.Ftp upload){
        Log.d(TAG, "FTP Event completed, success: " + upload.success);
        Utilities.HideProgress();

        if(!upload.success){
            Log.e (TAG, getString(R.string.autoftp_setup_title)
                    + "-"
                    + getString(R.string.upload_failure));
            if(userInvokedUpload){
                Utilities.MsgBox(getString(R.string.sorry),getString(R.string.upload_failure), this);
                userInvokedUpload = false;
            }
        }
    }


    @EventBusHook
    public void onEventMainThread(UploadEvents.OwnCloud upload){
        Log.d(TAG, "OwnCloud Event completed, success: " + upload.success);
        Utilities.HideProgress();

        if(!upload.success){
            Log.e (TAG, getString(R.string.owncloud_setup_title)
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
    public void onEventMainThread(ServiceEvents.AnnotationStatus annotationStatus){
        if(annotationStatus.annotationWritten){
            SetAnnotationDone();
        }
        else {
            SetAnnotationReady();
        }
    }

    @EventBusHook
    public void onEventMainThread(ServiceEvents.LoggingStatus loggingStatus){
            enableDisableMenuItems();
    }
}
