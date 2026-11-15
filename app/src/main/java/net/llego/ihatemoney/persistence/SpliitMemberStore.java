package net.llego.ihatemoney.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBMember;

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

    /** Resolve a rename against precisely the server version shown to the user. */
    public boolean resolveRename(long memberId, String expectedConflict, boolean keepLocal) {
        SQLiteDatabase database = db.getWritableDatabase();
        database.beginTransaction();
        try {
            DBMember member = db.getMember(memberId);
            String conflict = get(memberId, CONFLICT);
            if (member == null || member.getRemoteIdStr() == null || expectedConflict == null
                    || !expectedConflict.equals(conflict) || "null".equals(conflict)) return false;
            JsonObject remote = JsonParser.parseString(conflict).getAsJsonObject();
            if (!remote.has("name") || remote.get("name").isJsonNull()) return false;
            String remoteName = remote.get("name").getAsString();
            db.updateMember(memberId, keepLocal ? null : remoteName, null, null,
                    keepLocal ? DBBill.STATE_EDITED : DBBill.STATE_OK, null,
                    null, null, null, null, null);
            put(memberId, BASELINE, remoteName);
            put(memberId, ATTEMPT, null);
            put(memberId, CONFLICT, null);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    private static void checkColumn(String column) {
        if (!BASELINE.equals(column) && !ATTEMPT.equals(column) && !CONFLICT.equals(column)) {
            throw new IllegalArgumentException("Invalid Spliit member state column");
        }
    }
}
