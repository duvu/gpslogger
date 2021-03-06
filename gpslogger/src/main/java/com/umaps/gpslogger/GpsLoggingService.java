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

//TODO: Simplify email logic (too many methods)

package com.umaps.gpslogger;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.umaps.gpslogger.common.*;
import com.umaps.gpslogger.common.events.CommandEvents;
import com.umaps.gpslogger.common.events.ServiceEvents;
import com.umaps.gpslogger.loggers.FileLoggerFactory;

import de.greenrobot.event.EventBus;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

public class GpsLoggingService extends Service  {
    private static final String TAG = "GpsLoggingService";
    private static NotificationManager notificationManager;
    private static int NOTIFICATION_ID = 8675309;
    private final IBinder binder = new GpsLoggingBinder();
    AlarmManager nextPointAlarmManager;
    private NotificationCompat.Builder nfc = null;

    // ---------------------------------------------------
    // Helpers and managers
    // ---------------------------------------------------
    protected LocationManager gpsLocationManager;
    //private LocationManager passiveLocationManager;
    private LocationManager towerLocationManager;
    private GeneralLocationListener gpsLocationListener;
    private GeneralLocationListener towerLocationListener;
    //private GeneralLocationListener passiveLocationListener;
    private Intent alarmIntent;
    private Handler handler = new Handler();
    private long firstRetryTimeStamp;

    PendingIntent activityRecognitionPendingIntent;
    GoogleApiClient googleApiClient;
    // ---------------------------------------------------


    @Override
    public IBinder onBind(Intent arg0) {
        return binder;
    }

    @Override
    public void onCreate() {
        nextPointAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        RegisterEventBus();
    }

    private void RequestActivityRecognitionUpdates() {
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {

                    @Override
                    public void onConnectionSuspended(int arg) {
                    }

                    @Override
                    public void onConnected(Bundle arg0) {
                        try {
                            Log.d(TAG, "Requesting activity recognition updates");
                            Intent intent = new Intent(getApplicationContext(), GpsLoggingService.class);
                            activityRecognitionPendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(googleApiClient, AppSettings.getMinimumSeconds() * 1000, activityRecognitionPendingIntent);
                        }
                        catch(Throwable t){
                            Log.w(TAG, "Can't connect to activity recognition service", t);
                        }

                    }

                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult arg0) {

                    }
                });

        googleApiClient = builder.build();
        googleApiClient.connect();
    }

    private void StopActivityRecognitionUpdates(){
        try{
            Log.d(TAG, "Stopping activity recognition updates");
            if(googleApiClient != null && googleApiClient.isConnected()){
                ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(googleApiClient, activityRecognitionPendingIntent);
                googleApiClient.disconnect();
            }
        }
        catch(Throwable t){
            Log.w(TAG, "Tried to stop activity recognition updates", t);
        }

    }

    private void RegisterEventBus() {
        EventBus.getDefault().registerSticky(this);
    }

    private void UnregisterEventBus(){
        try {
            EventBus.getDefault().unregister(this);
        } catch (Throwable t){
            //this may crash if registration did not go through. just be safe
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        HandleIntent(intent);
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "GpsLoggingService is being destroyed by Android OS.");
        UnregisterEventBus();
        //RemoveNotification();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        Log.e(TAG, "Android is low on memory!");
        super.onLowMemory();
    }

    private void HandleIntent(Intent intent) {

        GetPreferences();

        ActivityRecognitionResult arr = ActivityRecognitionResult.extractResult(intent);
        if(arr != null){
            EventBus.getDefault().post(new ServiceEvents.ActivityRecognitionEvent(arr));
            return;
        }

        if (intent != null) {
            Bundle bundle = intent.getExtras();

            if (bundle != null) {
                boolean needToStartGpsManager = false;

                if (bundle.getBoolean(IntentConstants.IMMEDIATE_START)) {
                    Log.i(TAG, "Intent received - Start Logging Now");
                    EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(true));
                }

                if (bundle.getBoolean(IntentConstants.IMMEDIATE_STOP)) {
                    Log.i(TAG, "Intent received - Stop logging now");
                    EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(false));
                }

                if (bundle.getBoolean(IntentConstants.AUTOSEND_NOW)) {
                    Log.i(TAG, "Intent received - Send Email Now");
                    EventBus.getDefault().postSticky(new CommandEvents.AutoSend(null));
                }

                if (bundle.getBoolean(IntentConstants.GET_NEXT_POINT)) {
                    Log.i(TAG, "Intent received - Get Next Point");
                    needToStartGpsManager = true;
                }

                if (bundle.getString(IntentConstants.SET_DESCRIPTION) != null) {
                    Log.i(TAG, "Intent received - Set Next Point Description: " + bundle.getString(IntentConstants.SET_DESCRIPTION));
                    EventBus.getDefault().post(new CommandEvents.Annotate(bundle.getString(IntentConstants.SET_DESCRIPTION)));
                }

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                if (bundle.get(IntentConstants.PREFER_CELLTOWER) != null) {
                    boolean preferCellTower = bundle.getBoolean(IntentConstants.PREFER_CELLTOWER);
                    Log.d(TAG, "Intent received - Set Prefer Cell Tower: " + String.valueOf(preferCellTower));

                    List<String> listeners = Utilities.GetListeners();
                    if(preferCellTower){
                        listeners.remove(listeners.indexOf("gps"));
                    } else {
                        listeners.remove(listeners.indexOf("network"));
                        listeners.remove(listeners.indexOf("passive"));
                    }
                    Set<String> listenersSet = new HashSet<String>(listeners);
                    prefs.edit().putStringSet("listeners", listenersSet).apply();
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.TIME_BEFORE_LOGGING) != null) {
                    int timeBeforeLogging = bundle.getInt(IntentConstants.TIME_BEFORE_LOGGING);
                    Log.d(TAG, "Intent received - Set Time Before Logging: " + String.valueOf(timeBeforeLogging));
                    prefs.edit().putString("time_before_logging", String.valueOf(timeBeforeLogging)).apply();
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.DISTANCE_BEFORE_LOGGING) != null) {
                    int distanceBeforeLogging = bundle.getInt(IntentConstants.DISTANCE_BEFORE_LOGGING);
                    Log.d(TAG, "Intent received - Set Distance Before Logging: " + String.valueOf(distanceBeforeLogging));
                    prefs.edit().putString("distance_before_logging", String.valueOf(distanceBeforeLogging)).apply();
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.GPS_ON_BETWEEN_FIX) != null) {
                    boolean keepBetweenFix = bundle.getBoolean(IntentConstants.GPS_ON_BETWEEN_FIX);
                    Log.d(TAG, "Intent received - Set Keep Between Fix: " + String.valueOf(keepBetweenFix));
                    prefs.edit().putBoolean("keep_fix", keepBetweenFix).apply();
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.RETRY_TIME) != null) {
                    int retryTime = bundle.getInt(IntentConstants.RETRY_TIME);
                    Log.d(TAG, "Intent received - Set Retry Time: " + String.valueOf(retryTime));
                    prefs.edit().putString("retry_time", String.valueOf(retryTime)).apply();
                    needToStartGpsManager = true;
                }

                if (bundle.get(IntentConstants.ABSOLUTE_TIMEOUT) != null) {
                    int absolumeTimeOut = bundle.getInt(IntentConstants.ABSOLUTE_TIMEOUT);
                    Log.d(TAG, "Intent received - Set Retry Time: " + String.valueOf(absolumeTimeOut));
                    prefs.edit().putString("absolute_timeout", String.valueOf(absolumeTimeOut)).apply();
                    needToStartGpsManager = true;
                }

                if(bundle.get(IntentConstants.LOG_ONCE) != null){
                    boolean logOnceIntent = bundle.getBoolean(IntentConstants.LOG_ONCE);
                    Log.d(TAG, "Intent received - Log Once: " + String.valueOf(logOnceIntent));
                    needToStartGpsManager = false;
                    //LogOnce();
                }

                try {
                    if(bundle.get(Intent.EXTRA_ALARM_COUNT) != "0"){
                        needToStartGpsManager = true;
                    }
                }
                catch (Throwable t){
                    Log.w(TAG, "Received a weird EXTRA_ALARM_COUNT value. Cannot continue.");
                    needToStartGpsManager = false;
                }


                if (needToStartGpsManager && Session.isStarted()) {
                    StartGpsManager();
                }
            }
        } else {
            // A null intent is passed in if the service has been killed and restarted.
            Log.d(TAG, "Service restarted with null intent. Start logging.");
            StartLogging();
        }
    }

    private void CancelAlarm() {
        if (alarmIntent != null) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            PendingIntent sender = PendingIntent.getBroadcast(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            am.cancel(sender);
        }
    }

    /**
     * Gets preferences chosen by the user and populates the AppSettings object.
     * Also sets up email timers if required.
     */
    private void GetPreferences() {
        Utilities.PopulateAppSettings(getApplicationContext());
    }

    /**
     * Resets the form, resets file name if required, reobtains preferences
     */
    protected void StartLogging() {
        Log.d(TAG, ".");

        if (Session.isStarted()) {
            Log.d(TAG, "Session already started, ignoring");
            return;
        }
        try {
            startForeground(NOTIFICATION_ID, new Notification());
        } catch (Exception ex) {
            Log.e(TAG, "Could not start GPSLoggingService in foreground. ", ex);
        }

        Session.setStarted(true);

        GetPreferences();
        //ShowNotification();
        NotifyClientStarted();
        //StartPassiveManager();
        StartGpsManager();
        RequestActivityRecognitionUpdates();

    }

    /**
     * Asks the main service client to clear its form.
     */
    private void NotifyClientStarted() {
        Log.i(TAG, getString(R.string.started));
        EventBus.getDefault().post(new ServiceEvents.LoggingStatus(true));
    }

    /**
     * Stops logging, removes notification, stops GPS manager, stops email timer
     */
    public void StopLogging() {
        Log.d(TAG, ".");
        Session.setTotalTravelled(0);
        Session.setPreviousLocationInfo(null);
        Session.setStarted(false);
        Session.setUserStillSinceTimeStamp(0);
        Session.setLatestTimeStamp(0);
        stopAbsoluteTimer();
        CancelAlarm();
        Session.setCurrentLocationInfo(null);
        stopForeground(true);

        //RemoveNotification();
        StopAlarm();
        StopGpsManager();
        //StopPassiveManager();
        StopActivityRecognitionUpdates();
        NotifyClientStopped();
    }

    /**
     * Starts the location manager. There are two location managers - GPS and
     * Cell Tower. This code determines which manager to request updates from
     * based on user preference and whichever is enabled. If GPS is enabled on
     * the phone, that is used. But if the user has also specified that they
     * prefer cell towers, then cell towers are used. If neither is enabled,
     * then nothing is requested.
     */
    private void StartGpsManager() {

        GetPreferences();

        //If the user has been still for more than the minimum seconds
        if(userHasBeenStillForTooLong()) {
            Log.i(TAG, "No movement detected in the past interval, will not log");
            SetAlarmForNextPoint();
            return;
        }

        if (gpsLocationListener == null) {
            gpsLocationListener = new GeneralLocationListener(this, "GPS");
        }

        if (towerLocationListener == null) {
            towerLocationListener = new GeneralLocationListener(this, "CELL");
        }

        gpsLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        towerLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        CheckTowerAndGpsStatus();

        if (Session.isGpsEnabled() && AppSettings.getChosenListeners().contains("gps")) {
            Log.i(TAG, "Requesting GPS location updates");
            // gps satellite based
            gpsLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsLocationListener);
            gpsLocationManager.addGpsStatusListener(gpsLocationListener);

            Session.setUsingGps(true);
            startAbsoluteTimer();
        }

        if (Session.isTowerEnabled() &&  ( AppSettings.getChosenListeners().contains("network")  || !Session.isGpsEnabled() ) ) {
            Log.i(TAG, "Requesting cell and wifi location updates");
            Session.setUsingGps(false);
            // Cell tower and wifi based
            towerLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, towerLocationListener);

            startAbsoluteTimer();
        }

        if(!Session.isTowerEnabled() && !Session.isGpsEnabled()) {
            Log.e(TAG, "No provider available!");
            Session.setUsingGps(false);
            Log.e(TAG, getString(R.string.gpsprovider_unavailable));
            StopLogging();
            SetLocationServiceUnavailable();
            return;
        }

        EventBus.getDefault().post(new ServiceEvents.WaitingForLocation(true));
        Session.setWaitingForLocation(true);
    }

    private boolean userHasBeenStillForTooLong() {
        return (Session.getUserStillSinceTimeStamp() > 0 &&
                (System.currentTimeMillis() - Session.getUserStillSinceTimeStamp()) > (AppSettings.getMinimumSeconds() * 1000));
    }

    private void startAbsoluteTimer() {
        if (AppSettings.getAbsoluteTimeout() >= 1) {
            handler.postDelayed(stopManagerRunnable, AppSettings.getAbsoluteTimeout() * 1000);
        }
    }

    private Runnable stopManagerRunnable = new Runnable() {
        @Override
        public void run() {
            Log.w(TAG, "Absolute timeout reached, giving up on this point");
            StopManagerAndResetAlarm();
        }
    };

    private void stopAbsoluteTimer() {
        handler.removeCallbacks(stopManagerRunnable);
    }

    /**
     * This method is called periodically to determine whether the cell tower /
     * gps providers have been enabled, and sets class level variables to those
     * values.
     */
    private void CheckTowerAndGpsStatus() {
        Session.setTowerEnabled(towerLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        Session.setGpsEnabled(gpsLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
    }

    /**
     * Stops the location managers
     */
    private void StopGpsManager() {

        if (towerLocationListener != null) {
            Log.d(TAG, "Removing towerLocationManager updates");
            towerLocationManager.removeUpdates(towerLocationListener);
        }

        if (gpsLocationListener != null) {
            Log.d(TAG, "Removing gpsLocationManager updates");
            gpsLocationManager.removeUpdates(gpsLocationListener);
            gpsLocationManager.removeGpsStatusListener(gpsLocationListener);
        }

        Session.setWaitingForLocation(false);
        EventBus.getDefault().post(new ServiceEvents.WaitingForLocation(false));

    }

//    private void StopPassiveManager(){
//        if(passiveLocationManager!=null){
//            Log.d(TAG, "Removing passiveLocationManager updates");
//            passiveLocationManager.removeUpdates(passiveLocationListener);
//        }
//    }

    void SetLocationServiceUnavailable(){
        EventBus.getDefault().post(new ServiceEvents.LocationServicesUnavailable());
    }


    /**
     * Notifies main form that logging has stopped
     */
    void NotifyClientStopped() {
        Log.i(TAG, getString(R.string.stopped));
        EventBus.getDefault().post(new ServiceEvents.LoggingStatus(false));
    }

    /**
     * Stops location manager, then starts it.
     */
    void RestartGpsManagers() {
        Log.d(TAG, "Restarting location managers");
        StopGpsManager();
        StartGpsManager();
    }

    /**
     * This event is raised when the GeneralLocationListener has a new location.
     * This method in turn updates notification, writes to file, reobtains
     * preferences, notifies main service client and resets location managers.
     *
     * @param loc Location object
     */
    void OnLocationChanged(Location loc) {
        if (!Session.isStarted()) {
            Log.d(TAG, "OnLocationChanged called, but Session.isStarted is false");
            StopLogging();
            return;
        }

        long currentTimeStamp = System.currentTimeMillis();

        //Log.d(TAG, "Has description? " + Session.hasDescription() + ", Last timestamp: " + Session.getLatestTimeStamp());
        // Don't log a point until the user-defined time has elapsed
        // However, if user has set an annotation, just log the point, disregard any filters
        if ((currentTimeStamp - Session.getLatestTimeStamp()) < (AppSettings.getMinimumSeconds() * 1000)) {
            return;
        }

        //Don't log a point if user has been still
        // However, if user has set an annotation, just log the point, disregard any filters
        if(userHasBeenStillForTooLong()) {
            Log.i(TAG, "Received location but the user hasn't moved, ignoring");
            return;
        }

        if(!isFromValidListener(loc)){
            return;
        }


        boolean isPassiveLocation = loc.getExtras().getBoolean("PASSIVE");

        // Don't do anything until the user-defined accuracy is reached
        // However, if user has set an annotation, just log the point, disregard any filters
        if (AppSettings.getMinimumAccuracyInMeters() > 0) {

            //Don't apply the retry interval to passive locations
            if (!isPassiveLocation && AppSettings.getMinimumAccuracyInMeters() < Math.abs(loc.getAccuracy())) {

                if (this.firstRetryTimeStamp == 0) {
                    this.firstRetryTimeStamp = System.currentTimeMillis();
                }

                if (currentTimeStamp - this.firstRetryTimeStamp <= AppSettings.getRetryInterval() * 1000) {
                    Log.w(TAG, "Only accuracy of " + String.valueOf(Math.floor(loc.getAccuracy())) + " m. Point discarded." + getString(R.string.inaccurate_point_discarded));
                    //return and keep trying
                    return;
                }

                if (currentTimeStamp - this.firstRetryTimeStamp > AppSettings.getRetryInterval() * 1000) {
                    Log.w(TAG, "Only accuracy of " + String.valueOf(Math.floor(loc.getAccuracy())) + " m and timeout reached." + getString(R.string.inaccurate_point_discarded));
                    //Give up for now
                    StopManagerAndResetAlarm();

                    //reset timestamp for next time.
                    this.firstRetryTimeStamp = 0;
                    return;
                }

                //Success, reset timestamp for next time.
                this.firstRetryTimeStamp = 0;
            }
        }

        //Don't do anything until the user-defined distance has been traversed
        // However, if user has set an annotation, just log the point, disregard any filters
        if (AppSettings.getMinimumDistanceInMeters() > 0 && Session.hasValidLocation()) {

            double distanceTraveled = Utilities.CalculateDistance(loc.getLatitude(), loc.getLongitude(),
                    Session.getCurrentLatitude(), Session.getCurrentLongitude());

            if (AppSettings.getMinimumDistanceInMeters() > distanceTraveled) {
                Log.w(TAG, String.format(getString(R.string.not_enough_distance_traveled), String.valueOf(Math.floor(distanceTraveled))) + ", point discarded");
                StopManagerAndResetAlarm();
                return;
            }
        }


        Log.i(TAG, String.valueOf(loc.getLatitude()) + "," + String.valueOf(loc.getLongitude()));
        AdjustAltitude(loc);
        Session.setLatestTimeStamp(System.currentTimeMillis());
        Session.setCurrentLocationInfo(loc);
        SetDistanceTraveled(loc);
        //ShowNotification();

        if(isPassiveLocation){
            Log.d(TAG, "Logging passive location to file");
        }

        WriteToFile(loc);
        GetPreferences();
        StopManagerAndResetAlarm();

        EventBus.getDefault().post(new ServiceEvents.LocationUpdate(loc));
    }

    private void AdjustAltitude(Location loc) {

        if(!loc.hasAltitude()){ return; }

        if(AppSettings.shouldAdjustAltitudeFromGeoIdHeight() && loc.getExtras() != null){
            String geoidheight = loc.getExtras().getString("GEOIDHEIGHT");
            if (!Utilities.IsNullOrEmpty(geoidheight)) {
                loc.setAltitude((float) loc.getAltitude() - Float.valueOf(geoidheight));
            }
        }

        loc.setAltitude(loc.getAltitude() - AppSettings.getSubtractAltitudeOffset());
    }

    private boolean isFromValidListener(Location loc) {

        if(!AppSettings.getChosenListeners().contains("gps") && !AppSettings.getChosenListeners().contains("network")){
            return true;
        }

        if(!AppSettings.getChosenListeners().contains("network")){
            return loc.getProvider().equalsIgnoreCase("gps");
        }

        if(!AppSettings.getChosenListeners().contains("gps")){
            return !loc.getProvider().equalsIgnoreCase("gps");
        }

        return true;
    }

    private void SetDistanceTraveled(Location loc) {
        // Distance
        if (Session.getPreviousLocationInfo() == null) {
            Session.setPreviousLocationInfo(loc);
        }
        // Calculate this location and the previous location location and add to the current running total distance.
        // NOTE: Should be used in conjunction with 'distance required before logging' for more realistic values.
        double distance = Utilities.CalculateDistance(
                Session.getPreviousLatitude(),
                Session.getPreviousLongitude(),
                loc.getLatitude(),
                loc.getLongitude());
        Session.setPreviousLocationInfo(loc);
        Session.setTotalTravelled(Session.getTotalTravelled() + distance);
    }

    protected void StopManagerAndResetAlarm() {
        stopAbsoluteTimer();
        SetAlarmForNextPoint();
    }


    private void StopAlarm() {
        Intent i = new Intent(this, GpsLoggingService.class);
        i.putExtra(IntentConstants.GET_NEXT_POINT, true);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        nextPointAlarmManager.cancel(pi);
    }

    private void SetAlarmForNextPoint() {
        Log.d(TAG, "Set alarm for " + AppSettings.getMinimumSeconds() + " seconds");

        Intent i = new Intent(this, GpsLoggingService.class);
        i.putExtra(IntentConstants.GET_NEXT_POINT, true);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        nextPointAlarmManager.cancel(pi);

        nextPointAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AppSettings.getMinimumSeconds() * 1000, pi);
    }


    /**
     * Calls file helper to write a given location to a file.
     *
     * @param loc Location object
     */
    private void WriteToFile(Location loc) {
        try {
            FileLoggerFactory.Write(getApplicationContext(), loc);
        } catch(Exception e){
             Log.e(TAG, getString(R.string.could_not_write_to_file), e);
        }

        EventBus.getDefault().post(new ServiceEvents.AnnotationStatus(true));
    }

    /**
     * Informs the main service client of the number of visible satellites.
     *
     * @param count Number of Satellites
     */
    void SetSatelliteInfo(int count) {
        Session.setSatelliteCount(count);
        EventBus.getDefault().post(new ServiceEvents.SatelliteCount(count));
    }

    /**
     * Can be used from calling classes as the go-between for methods and
     * properties.
     */
    public class GpsLoggingBinder extends Binder {
        public GpsLoggingService getService() {
            return GpsLoggingService.this;
        }
    }


    @EventBusHook
    public void onEvent(CommandEvents.RequestToggle requestToggle){
        if (Session.isStarted()) {
            StopLogging();
        } else {
            StartLogging();
        }
    }

    @EventBusHook
    public void onEvent(CommandEvents.RequestStartStop startStop){
        if(startStop.start){
            StartLogging();
        }
        else {
            StopLogging();
        }

        EventBus.getDefault().removeStickyEvent(CommandEvents.RequestStartStop.class);
    }

    @EventBusHook
    public void onEvent(CommandEvents.AutoSend autoSend){
        //AutoSendLogFile(autoSend.formattedFileName);

        EventBus.getDefault().removeStickyEvent(CommandEvents.AutoSend.class);
    }

    @EventBusHook
    public void onEvent(CommandEvents.Annotate annotate){
        final String desc = Utilities.CleanDescription(annotate.annotation);
        if (desc.length() == 0) {
            Log.d(TAG, "Clearing annotation");
        } else {
            Log.d(TAG, "Pending annotation: " + desc);
            EventBus.getDefault().post(new ServiceEvents.AnnotationStatus(false));

            if(Session.isStarted()){
                StartGpsManager();
            }
            else {
                //LogOnce();
            }
        }

        EventBus.getDefault().removeStickyEvent(CommandEvents.Annotate.class);
    }

    @EventBusHook
    public void onEvent(CommandEvents.LogOnce logOnce){
       // LogOnce();
    }

    @EventBusHook
    public void onEvent(ServiceEvents.ActivityRecognitionEvent activityRecognitionEvent){

        if(!AppSettings.shouldNotLogIfUserIsStill()){
            Session.setUserStillSinceTimeStamp(0);
            return;
        }

        if(activityRecognitionEvent.result.getMostProbableActivity().getType() == DetectedActivity.STILL){
            Log.d(TAG, activityRecognitionEvent.result.getMostProbableActivity().toString());
            if(Session.getUserStillSinceTimeStamp() == 0){
                Log.d(TAG, "Just entered still state, attempt to log");
                StartGpsManager();
                Session.setUserStillSinceTimeStamp(System.currentTimeMillis());
            }

        }
        else {
            Log.d(TAG, activityRecognitionEvent.result.getMostProbableActivity().toString());
            //Reset the still-since timestamp
            Session.setUserStillSinceTimeStamp(0);
            Log.d(TAG, "Just exited still state, attempt to log");
            StartGpsManager();
        }
    }

}
