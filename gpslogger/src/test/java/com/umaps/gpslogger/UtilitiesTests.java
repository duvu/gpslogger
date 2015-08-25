package com.umaps.gpslogger;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;
import android.test.mock.MockContext;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.format.Time;
import com.umaps.gpslogger.common.Utilities;

import java.io.File;
import java.util.Date;


public class UtilitiesTests extends AndroidTestCase {

    Context context;

    public void setUp() {
        this.context = new UtilitiesMockContext(getContext());
        Utilities.PopulateAppSettings(context);
        final SharedPreferences.Editor preferencesEditor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        preferencesEditor.clear().commit();
    }

    class UtilitiesMockContext extends MockContext {

        private Context mDelegatedContext;
        private static final String PREFIX = "com.umaps.gpslogger.";

        public UtilitiesMockContext(Context context) {
            mDelegatedContext = context;
        }

        @Override
        public String getPackageName(){
            return PREFIX;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return mDelegatedContext.getSharedPreferences(name, mode);
        }

        @Override
        public File getExternalFilesDir(String type){
            return new File("/sdcard/GPSLogger");
        }


    }


    @SmallTest
    public void testHTMLDecoder(){


        String actual = Utilities.HtmlDecode("Bert &amp; Ernie are here. They wish to &quot;talk&quot; to you.");
        String expected = "Bert & Ernie are here. They wish to \"talk\" to you.";
        assertEquals("HTML Decode did not decode everything", expected, actual);

        actual = Utilities.HtmlDecode(null);
        expected = null;

        assertEquals("HTML Decode should handle null input", expected, actual);

    }

    @SmallTest
    public void testIsoDateTime() {

        String actual = Utilities.GetIsoDateTime(new Date(1417726140000l));
        String expected = "2014-12-04T20:49:00Z";
        assertEquals("Conversion of date to ISO string", expected, actual);
    }

    @SmallTest
    public void testCleanDescription() {
        String content = "This is some annotation that will end up in an " +
                "XML file.  It will either <b>break</b> or Bert & Ernie will show up" +
                "and cause all sorts of mayhem. Either way, it won't \"work\"";

        String expected = "This is some annotation that will end up in an " +
                "XML file.  It will either bbreak/b or Bert &amp; Ernie will show up" +
                "and cause all sorts of mayhem. Either way, it won't &quot;work&quot;";

        String actual = Utilities.CleanDescription(content);

        assertEquals("Clean Description should remove characters", expected, actual);
    }

    @SmallTest
    public void testFolderListFiles() {
        assertNotNull("Null File object should return empty list", Utilities.GetFilesInFolder(null));

        assertNotNull("Empty folder should return empty list", Utilities.GetFilesInFolder(new File("/")));

    }

    @SmallTest
    public void testFormattedCustomFileName() {


        String expected = "basename_" + Build.SERIAL;
        String actual = Utilities.GetFormattedCustomFileName("basename_%ser");
        assertEquals("Static file name %SER should be replaced with Build Serial", expected, actual);

        Time t = new Time();
        t.setToNow();

        expected = "basename_" +  String.valueOf(t.hour);

        actual = Utilities.GetFormattedCustomFileName("basename_%HOUR");
        assertEquals("Static file name %HOUR should be replaced with Hour", expected, actual);

        actual = Utilities.GetFormattedCustomFileName("basename_%HOUR%MIN");
        expected = "basename_" +  String.valueOf(t.hour) + String.valueOf(t.minute);
        assertEquals("Static file name %HOUR, %MIN should be replaced with Hour, Minute", expected, actual);

        actual = Utilities.GetFormattedCustomFileName("basename_%YEAR%MONTH%DAY");
        expected = "basename_" +  String.valueOf(t.year) + String.valueOf(t.month) + String.valueOf(t.monthDay);
        assertEquals("Static file name %YEAR, %MONTH, %DAY should be replaced with Year Month and Day", expected, actual);

    }
}
