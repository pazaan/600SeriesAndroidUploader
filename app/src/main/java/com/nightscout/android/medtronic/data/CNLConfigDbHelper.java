package com.nightscout.android.medtronic.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by lgoedhart on 9/05/2016.
 */
public class CNLConfigDbHelper extends SQLiteOpenHelper {
    // Database Specific Details

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 1;
    // DB Name, same is used to name the sqlite DB file
    private static final String DATABASE_NAME = "cnl_config.db";

    private static final String SQL_CREATE_CONFIG =
            "CREATE TABLE " + CNLConfigContract.ConfigEntry.TABLE_NAME + " (" +
                    CNLConfigContract.ConfigEntry._ID + " INTEGER PRIMARY KEY," +
                    CNLConfigContract.ConfigEntry.COLUMN_NAME_STICK_SERIAL + " TEXT UNIQUE, " +
                    CNLConfigContract.ConfigEntry.COLUMN_NAME_HMAC + " TEXT, "+
                    CNLConfigContract.ConfigEntry.COLUMN_NAME_KEY + " TEXT, " +
                    CNLConfigContract.ConfigEntry.COLUMN_NAME_LAST_RADIO_CHANNEL + " INTEGER " +
                    ")";

    private static final String SQL_DROP_CONFIG =
            "DROP TABLE IF EXISTS " + CNLConfigContract.ConfigEntry.TABLE_NAME;

    public CNLConfigDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CONFIG);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No upgrades yet, so drop and rebuild
        db.execSQL(SQL_DROP_CONFIG);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public void insertStickSerial( String stickSerial ) {
        SQLiteDatabase configDb = this.getWritableDatabase();
        ContentValues insertValues = new ContentValues();
        insertValues.put(CNLConfigContract.ConfigEntry.COLUMN_NAME_STICK_SERIAL, stickSerial );
        insertValues.put(CNLConfigContract.ConfigEntry.COLUMN_NAME_HMAC, "");
        insertValues.put(CNLConfigContract.ConfigEntry.COLUMN_NAME_KEY, "");
        insertValues.put(CNLConfigContract.ConfigEntry.COLUMN_NAME_LAST_RADIO_CHANNEL, 0x14 );
        configDb.insertWithOnConflict(CNLConfigContract.ConfigEntry.TABLE_NAME, null, insertValues, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public String getHmac( String stickSerial ){
        SQLiteDatabase configDb = this.getWritableDatabase();

        Cursor cursor = configDb.query( CNLConfigContract.ConfigEntry.TABLE_NAME,
                new String[] { CNLConfigContract.ConfigEntry.COLUMN_NAME_HMAC },
                CNLConfigContract.ConfigEntry.COLUMN_NAME_STICK_SERIAL + " = ?", new String[]{ stickSerial }, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String hmac = cursor.getString(cursor.getColumnIndex(CNLConfigContract.ConfigEntry.COLUMN_NAME_HMAC));
            cursor.close();

            return hmac;
        } else {
            return null;
        }
    }

    public String getKey( String stickSerial ){
        SQLiteDatabase configDb = this.getWritableDatabase();

        Cursor cursor = configDb.query( CNLConfigContract.ConfigEntry.TABLE_NAME,
                new String[] { CNLConfigContract.ConfigEntry.COLUMN_NAME_KEY },
                CNLConfigContract.ConfigEntry.COLUMN_NAME_STICK_SERIAL + " = ?", new String[]{ stickSerial }, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String hmac = cursor.getString(cursor.getColumnIndex(CNLConfigContract.ConfigEntry.COLUMN_NAME_KEY));
            cursor.close();

            return hmac;
        } else {
            return null;
        }
    }

    public int setHmacAndKey( String stickSerial, String hmac, String key ) {
        SQLiteDatabase configDb = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(CNLConfigContract.ConfigEntry.COLUMN_NAME_HMAC, hmac);
        values.put(CNLConfigContract.ConfigEntry.COLUMN_NAME_KEY, key);

        // Which row to update, based on the ID
        String whereClause = CNLConfigContract.ConfigEntry.COLUMN_NAME_STICK_SERIAL + " = ?";
        String[] whereArgs = { stickSerial };

        int affectedRows = configDb.update(
                CNLConfigContract.ConfigEntry.TABLE_NAME,
                values,
                whereClause,
                whereArgs
        );

        return affectedRows;
    }

    public Cursor getAllRows(){
        SQLiteDatabase configDb = this.getReadableDatabase();

        String where = null;
        String whereArgs[] = null;
        String groupBy = null;
        String having = null;
        String order = null;
        String limit = null;

        Cursor cursor = configDb.query(CNLConfigContract.ConfigEntry.TABLE_NAME, null, where, whereArgs, groupBy, having, order, limit);
        if (cursor != null){
            cursor.moveToFirst();
        }
        return cursor;
    }

}
