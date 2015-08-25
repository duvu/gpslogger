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

package com.umaps.gpslogger.common;

import android.app.Application;
import android.content.Context;
import android.telephony.TelephonyManager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import de.greenrobot.event.EventBus;

import java.util.Set;

public class AppSettings extends Application {

    private static JobManager jobManager;
    private static String Imei;

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.builder().logNoSubscriberMessages(false).sendNoSubscriberEvent(false).installDefaultEventBus();

        Configuration config = new Configuration.Builder(getInstance())
                .networkUtil(new WifiNetworkUtil(getInstance()))
                .consumerKeepAlive(60)
                .minConsumerCount(2)
                .build();
        jobManager = new JobManager(this, config);

        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        Imei = tm.getDeviceId();
    }

    public static JobManager GetJobManager(){
        return jobManager;
    }
    public static String getImei() {
        return Imei;
    }

    private static AppSettings instance;
    public AppSettings() {
        instance = this;
    }

    public static AppSettings getInstance() {
        return instance;
    }

    // ---------------------------------------------------
    // User Preferences
    // ---------------------------------------------------
    private static boolean useImperial = false;
    private static boolean hideNotificationButtons = true;
    private static int minimumSeconds;
    private static int retryInterval;
    private static int minimumDistance;
    private static int minimumAccuracy;

    private static boolean logToOpenGts;
    private static boolean openGtsAutoSendEnabled;
    private static String openGTSServer;
    private static String openGTSServerPort;
    private static String openGTSServerCommunicationMethod;
    private static String openGTSServerPath;
    private static String openGTSDeviceId;
    private static String openGTSAccountName;
    private static int absoluteTimeout;
    private static Set<String> chosenListeners;

    private static boolean dontLogIfUserIsStill;

    private static boolean adjustAltitudeFromGeoIdHeight;
    private static int subtractAltitudeOffset;
    /**
     * @return the useImperial
     */
    public static boolean shouldUseImperial() {
        return useImperial;
    }

    /**
     * @param useImperial the useImperial to set
     */
    static void setUseImperial(boolean useImperial) {
        AppSettings.useImperial = useImperial;
    }

    /**
     * @return the minimumSeconds
     */
    public static int getMinimumSeconds() {
        return minimumSeconds;
    }

    /**
     * @param minimumSeconds the minimumSeconds to set
     */
    static void setMinimumSeconds(int minimumSeconds) {
        AppSettings.minimumSeconds = minimumSeconds;
    }

    /**
     * @return the retryInterval
     */
    public static int getRetryInterval() {
        return retryInterval;
    }

    /**
     * @param retryInterval the retryInterval to set
     */
    static void setRetryInterval(int retryInterval) {
        AppSettings.retryInterval = retryInterval;
    }


    /**
     * @return the minimumDistance
     */
    public static int getMinimumDistanceInMeters() {
        return minimumDistance;
    }

    /**
     * @param minimumDistance the minimumDistance to set
     */
    static void setMinimumDistanceInMeters(int minimumDistance) {
        AppSettings.minimumDistance = minimumDistance;
    }

    /**
     * @return the minimumAccuracy
     */
    public static int getMinimumAccuracyInMeters() {
        return minimumAccuracy;
    }

    /**
     * @param minimumAccuracy the minimumAccuracy to set
     */
    static void setMinimumAccuracyInMeters(int minimumAccuracy) {
        AppSettings.minimumAccuracy = minimumAccuracy;
    }

    public static boolean shouldLogToOpenGTS() {
        return logToOpenGts;
    }

    public static void setLogToOpenGts(boolean logToOpenGts) {
        AppSettings.logToOpenGts = logToOpenGts;
    }

    public static boolean isOpenGtsAutoSendEnabled() {
        return openGtsAutoSendEnabled;
    }

    public static void setOpenGtsAutoSendEnabled(boolean openGtsAutoSendEnabled) {
        AppSettings.openGtsAutoSendEnabled = openGtsAutoSendEnabled;
    }

    public static String getOpenGTSServer() {
        return openGTSServer;
    }

    public static void setOpenGTSServer(String openGTSServer) {
        AppSettings.openGTSServer = openGTSServer;
    }

    public static String getOpenGTSServerPort() {
        return openGTSServerPort;
    }

    public static void setOpenGTSServerPort(String openGTSServerPort) {
        AppSettings.openGTSServerPort = openGTSServerPort;
    }

    public static String getOpenGTSServerCommunicationMethod() {
        return openGTSServerCommunicationMethod;
    }

    public static void setOpenGTSServerCommunicationMethod(String openGTSServerCommunicationMethod) {
        AppSettings.openGTSServerCommunicationMethod = openGTSServerCommunicationMethod;
    }

    public static String getOpenGTSServerPath() {
        return openGTSServerPath;
    }

    public static void setOpenGTSServerPath(String openGTSServerPath) {
        AppSettings.openGTSServerPath = openGTSServerPath;
    }

    public static String getOpenGTSDeviceId() {
        return openGTSDeviceId;
    }

    public static void setOpenGTSDeviceId(String openGTSDeviceId) {
        AppSettings.openGTSDeviceId = openGTSDeviceId;
    }

    public static int getAbsoluteTimeout() {
        return absoluteTimeout;
    }

    public static void setAbsoluteTimeout(int absoluteTimeout) {
        AppSettings.absoluteTimeout = absoluteTimeout;
    }

    public static String getOpenGTSAccountName() {
        return openGTSAccountName;
    }

    public static void setOpenGTSAccountName(String openGTSAccountName) {
        AppSettings.openGTSAccountName = openGTSAccountName;
    }

    public static void setChosenListeners(Set<String> chosenListeners) {
        AppSettings.chosenListeners = chosenListeners;
    }

    public static Set<String> getChosenListeners() {
        return chosenListeners;
    }

    public static void setHideNotificationButtons(boolean hideNotificationButtons) {
        AppSettings.hideNotificationButtons = hideNotificationButtons;
    }

    public static boolean shouldHideNotificationButtons() {
        return hideNotificationButtons;
    }

    public static boolean shouldNotLogIfUserIsStill() {
        return AppSettings.dontLogIfUserIsStill;
    }

    public static void setShouldNotLogIfUserIsStill(boolean check){
        AppSettings.dontLogIfUserIsStill = check;
    }


    public static boolean shouldAdjustAltitudeFromGeoIdHeight() {
        return adjustAltitudeFromGeoIdHeight;
    }

    public static void setAdjustAltitudeFromGeoIdHeight(boolean adjustAltitudeFromGeoIdHeight) {
        AppSettings.adjustAltitudeFromGeoIdHeight = adjustAltitudeFromGeoIdHeight;
    }


    public static int getSubtractAltitudeOffset() {
        return subtractAltitudeOffset;
    }

    public static void setSubtractAltitudeOffset(int subtractAltitudeOffset) {
        AppSettings.subtractAltitudeOffset = subtractAltitudeOffset;
    }
}
