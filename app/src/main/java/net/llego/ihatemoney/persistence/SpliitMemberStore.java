package net.llego.ihatemoney.persistence;

import android.content.ContentValues;
import android.database.Cursor;

/** Durable participant sync state, stored separately from the user's member fields. */
public final class SpliitMemberStore {
    public static final String BASELINE = "SPLIITBASELINE";
    public static final String ATTEMPT = "SPLIITATTEMPT";
    public static final String CONFLICT = "SPLIITCONFLICT";
    static final String COLUMNS = BASELINE + " TEXT, " + ATTEMPT + " TEXT, " + CONFLICT + " TEXT";
    static final String[] MIGRATIONS = {
            "ALTER TABLE MEMBERS ADD COLUMN " + BASELINE + " TEXT",
            "ALTER TABLE MEMBERS ADD COLUMN " + ATTEMPT + " TEXT",
            "ALTER TABLE MEMBERS ADD COLUMN " + CONFLICT + " TEXT"
    };

    private final MoneyBusterSQLiteOpenHelper db;

    public SpliitMemberStore(MoneyBusterSQLiteOpenHelper db) { this.db = db; }

    public String get(long memberId, String column) {
        checkColumn(column);
        try (Cursor cursor = db.getReadableDatabase().query("MEMBERS", new String[]{column},
                "ID = ?", new String[]{String.valueOf(memberId)}, null, null, null)) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getString(0) : null;
        }
    }

    public void put(long memberId, String column, String value) {
        checkColumn(column);
        ContentValues values = new ContentValues();
        values.put(column, value);
        db.getWritableDatabase().update("MEMBERS", values, "ID = ?", new String[]{String.valueOf(memberId)});
    }

    private static void checkColumn(String column) {
        if (!BASELINE.equals(column) && !ATTEMPT.equals(column) && !CONFLICT.equals(column)) {
            throw new IllegalArgumentException("Invalid Spliit member state column");
        }
    }
}
