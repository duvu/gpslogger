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
import com.mendhak.gpslogger.common.AppSettings;
import com.mendhak.gpslogger.common.Session;
import com.mendhak.gpslogger.common.Utilities;
import com.mendhak.gpslogger.senders.opengts.OpenGTSHelper;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FileSenderFactory {

    private static final org.slf4j.Logger tracer = LoggerFactory.getLogger(FileSenderFactory.class.getSimpleName());

    public static IFileSender GetOpenGTSSender(Context applicationContext) {
        return new OpenGTSHelper();
    }

    public static void SendFiles(Context applicationContext, final String fileToSend) {

        tracer.info("Sending file " + fileToSend);

        File gpxFolder = new File(AppSettings.getGpsLoggerFolder());

        if (Utilities.GetFilesInFolder(gpxFolder).length < 1) {
            tracer.warn("No files found to send.");
            return;
        }

        List<File> files = new ArrayList<File>(Arrays.asList(Utilities.GetFilesInFolder(gpxFolder, new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.contains(fileToSend) && !s.contains("zip");
            }
        })));

        List<File> zipFiles = new ArrayList<>();

        if (files.size() == 0) {
            tracer.warn("No files found to send after filtering.");
            return;
        }

        if (AppSettings.shouldSendZipFile()) {
            File zipFile = new File(gpxFolder.getPath(), fileToSend + ".zip");
            ArrayList<String> filePaths = new ArrayList<String>();

            for (File f : files) {
                filePaths.add(f.getAbsolutePath());
            }

            tracer.info("Zipping file");
            ZipHelper zh = new ZipHelper(filePaths.toArray(new String[filePaths.size()]), zipFile.getAbsolutePath());
            zh.Zip();

            zipFiles.clear();
            zipFiles.add(zipFile);
        }

        List<IFileSender> senders = GetFileSenders(applicationContext);

        for (IFileSender sender : senders) {
            tracer.debug("Sender: " + sender.getClass().getName());
            //Special case for OSM Uploader
            if(!sender.accept(null, ".zip")){
                sender.UploadFile(files);
                continue;
            }

            if(AppSettings.shouldSendZipFile()){
                sender.UploadFile(zipFiles);
            } else {
                sender.UploadFile(files);
            }

        }
    }


    private static List<IFileSender> GetFileSenders(Context applicationContext) {
        List<IFileSender> senders = new ArrayList<IFileSender>();

        if (AppSettings.isOpenGtsAutoSendEnabled()) {
            senders.add(new OpenGTSHelper());
        }
        return senders;

    }
}
