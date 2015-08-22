package com.mendhak.gpslogger.common;


import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

public class ContentApi extends ContentProvider {
    private static final String TAG = "ContentApi";
    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        String queryType = uri.getPathSegments().get(0);
        Log.d(TAG, queryType);
        String result = "";

        switch(queryType){
            case "gpslogger_folder":
                result = PreferenceManager.getDefaultSharedPreferences(getContext()).getString("gpslogger_folder",
                        Utilities.GetDefaultStorageFolder(getContext()).getAbsolutePath());
                break;
            default:
                result = "NULL";
                break;
        }


        Log.d(TAG, result);
        MatrixCursor matrixCursor = new MatrixCursor(new String[] { "Column1" });

        matrixCursor.newRow().add(result);
        return matrixCursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
