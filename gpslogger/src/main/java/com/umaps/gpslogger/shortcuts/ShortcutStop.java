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

package com.umaps.gpslogger.shortcuts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.umaps.gpslogger.GpsLoggingService;
import com.umaps.gpslogger.common.events.CommandEvents;
import de.greenrobot.event.EventBus;

public class ShortcutStop extends Activity {
    private static final String TAG = "ShortcutStop";
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        Log.i(TAG, "Shortcut - stop logging");
        EventBus.getDefault().postSticky(new CommandEvents.RequestStartStop(false));

        Intent serviceIntent = new Intent(getApplicationContext(), GpsLoggingService.class);
        getApplicationContext().startService(serviceIntent);

        finish();

    }


}