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

import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;

import java.util.Iterator;

class GeneralLocationListener implements LocationListener, GpsStatus.Listener {
    private static final String TAG = "GeneralLocationListener";
    private static String listenerName;
    private static GpsLoggingService loggingService;
    protected String latestHdop;
    protected String latestPdop;
    protected String latestVdop;
    protected String geoIdHeight;
    protected String ageOfDgpsData;
    protected String dgpsId;

    GeneralLocationListener(GpsLoggingService activity, String name) {
        loggingService = activity;
        listenerName = name;
    }

    /**
     * Event raised when a new fix is received.
     */
    public void onLocationChanged(Location loc) {

        try {
            if (loc != null) {
                Bundle b = new Bundle();
                b.putString("HDOP", this.latestHdop);
                b.putString("PDOP", this.latestPdop);
                b.putString("VDOP", this.latestVdop);
                b.putString("GEOIDHEIGHT", this.geoIdHeight);
                b.putString("AGEOFDGPSDATA", this.ageOfDgpsData);
                b.putString("DGPSID", this.dgpsId);

                b.putBoolean("PASSIVE", listenerName.equalsIgnoreCase("PASSIVE"));
                b.putString("LISTENER", listenerName);

                loc.setExtras(b);
                loggingService.OnLocationChanged(loc);

                this.latestHdop = "";
                this.latestPdop = "";
                this.latestVdop = "";
            }

        } catch (Exception ex) {
            Log.e(TAG, "GeneralLocationListener.onLocationChanged", ex);
        }

    }

    public void onProviderDisabled(String provider) {
        Log.i(TAG, "Provider disabled: " + provider);
        loggingService.RestartGpsManagers();
    }

    public void onProviderEnabled(String provider) {

        Log.i(TAG, "Provider enabled: " + provider);
        loggingService.RestartGpsManagers();
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (status == LocationProvider.OUT_OF_SERVICE) {
            Log.i(TAG, provider + " is out of service");
            loggingService.StopManagerAndResetAlarm();
        }

        if (status == LocationProvider.AVAILABLE) {
            Log.i(TAG, provider + " is available");
        }

        if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
            Log.i(TAG, provider + " is temporarily unavailable");
            loggingService.StopManagerAndResetAlarm();
        }
    }

    public void onGpsStatusChanged(int event) {

        switch (event) {
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                Log.d(TAG, loggingService.getString(R.string.fix_obtained));
                break;

            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:

                GpsStatus status = loggingService.gpsLocationManager.getGpsStatus(null);

                int maxSatellites = status.getMaxSatellites();

                Iterator<GpsSatellite> it = status.getSatellites().iterator();
                int count = 0;

                while (it.hasNext() && count <= maxSatellites) {
                    it.next();
                    count++;
                }

                Log.d(TAG, String.valueOf(count) + " satellites");
                loggingService.SetSatelliteInfo(count);
                break;

            case GpsStatus.GPS_EVENT_STARTED:
                Log.i(TAG, loggingService.getString(R.string.started_waiting));
                break;

            case GpsStatus.GPS_EVENT_STOPPED:
                Log.i(TAG, loggingService.getString(R.string.gps_stopped));
                break;

        }
    }
}
