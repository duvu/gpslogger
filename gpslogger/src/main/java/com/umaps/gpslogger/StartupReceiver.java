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


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.umaps.gpslogger.common.events.CommandEvents;
import de.greenrobot.event.EventBus;

public class StartupReceiver extends BroadcastReceiver {
    private static final String TAG = "StartupReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean startImmediately = prefs.getBoolean("startonbootup", true);

            Log.i(TAG, "Start on bootup - " + String.valueOf(startImmediately));

            if (startImmediately) {
                EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(true));
                Intent serviceIntent = new Intent(context, GpsLoggingService.class);
                context.startService(serviceIntent);
            }
        } catch (Exception ex) {
            Log.e(TAG, "StartupReceiver", ex);
        }

    }

}
