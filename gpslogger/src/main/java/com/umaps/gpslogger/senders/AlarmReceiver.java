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

package com.umaps.gpslogger.senders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.umaps.gpslogger.common.events.CommandEvents;
import de.greenrobot.event.EventBus;


public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Log.d(TAG, "Alarm received");

            EventBus.getDefault().postSticky(new CommandEvents.AutoSend(null));

            Intent serviceIntent = new Intent(context.getPackageName() + ".GpsLoggingService");
            context.startService(serviceIntent);
        } catch (Exception ex) {
            Log.e(TAG, "AlarmReceiver", ex);
        }
    }
}
