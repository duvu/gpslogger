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

package com.mendhak.gpslogger.senders;

import android.content.Context;
import android.util.Log;

import com.mendhak.gpslogger.common.AppSettings;
import com.mendhak.gpslogger.senders.opengts.OpenGTSHelper;

import java.util.ArrayList;
import java.util.List;

public class FileSenderFactory {
    private static final String TAG = "FileSenderFactory";
    public static IFileSender GetOpenGTSSender(Context applicationContext) {
        return new OpenGTSHelper();
    }
    private static List<IFileSender> GetFileSenders(Context applicationContext) {
        List<IFileSender> senders = new ArrayList<IFileSender>();

        if (AppSettings.isOpenGtsAutoSendEnabled()) {
            senders.add(new OpenGTSHelper());
        }
        return senders;

    }
}
