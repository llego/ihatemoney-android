package net.llego.ihatemoney.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBMember;

import java.util.Objects;

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
    static final String REMOVE_DUPLICATE_OWERS = "DELETE FROM BILLOWERS WHERE MEMBERID = ? AND EXISTS "
            + "(SELECT 1 FROM BILLOWERS AS existing WHERE existing.BILLID = BILLOWERS.BILLID AND existing.MEMBERID = ?)";

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

    /** Claim the version shown in the editor without racing an in-flight sync. */
    public boolean queueRename(DBMember expected, String name) {
        SQLiteDatabase database = db.getWritableDatabase();
        database.beginTransaction();
        try {
            DBMember current = db.getMember(expected.getId());
            if (current == null || current.getState() != expected.getState()
                    || !Objects.equals(current.getName(), expected.getName())
                    || !Objects.equals(current.getRemoteIdStr(), expected.getRemoteIdStr())) return false;
            int state = SpliitMemberDecision.renamedState(current.getState(), current.getRemoteIdStr(),
                    get(current.getId(), BASELINE), get(current.getId(), ATTEMPT), get(current.getId(), CONFLICT));
            if (state < 0) return false;
            for (DBMember other : db.getMembersOfProject(current.getProjectId(), null)) {
                if (other.getId() != current.getId() && name.equals(other.getName().trim())) return false;
            }
            db.updateMember(current.getId(), name, null, null, state, null,
                    null, null, null, null, null);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    /** Link an uncertain local creation to a server participant explicitly selected by the user. */
    public boolean linkCreatedMember(long pendingId, long importedId, String expectedRemoteId) {
        SQLiteDatabase database = db.getWritableDatabase();
        database.beginTransaction();
        try {
            DBMember pending = db.getMember(pendingId);
            DBMember imported = db.getMember(importedId);
            if (pending == null || imported == null || pendingId == importedId
                    || pending.getProjectId() != imported.getProjectId()
                    || pending.getRemoteIdStr() != null || pending.getState() != DBBill.STATE_ADDED
                    || get(pendingId, CONFLICT) == null || expectedRemoteId == null
                    || !expectedRemoteId.equals(imported.getRemoteIdStr())
                    || imported.getState() != DBBill.STATE_OK
                    || get(importedId, ATTEMPT) != null || get(importedId, CONFLICT) != null) return false;
            // Preserve the pending ID, which local drafts can already refer to. Merge
            // references to the imported copy, without duplicating split participants.
            database.execSQL("UPDATE BILLS SET PAYERID = ? WHERE PAYERID = ?", new Object[]{pendingId, importedId});
            database.execSQL(REMOVE_DUPLICATE_OWERS, new Object[]{importedId, pendingId});
            database.execSQL("UPDATE BILLOWERS SET MEMBERID = ? WHERE MEMBERID = ?", new Object[]{pendingId, importedId});
            database.execSQL("UPDATE PROJECTS SET LASTPAYERID = ? WHERE ID = ? AND LASTPAYERID = ?",
                    new Object[]{pendingId, pending.getProjectId(), importedId});
            db.updateMember(pendingId, imported.getName(), null, null, DBBill.STATE_OK, null,
                    expectedRemoteId, null, null, null, null, null);
            put(pendingId, BASELINE, imported.getName());
            put(pendingId, ATTEMPT, null);
            put(pendingId, CONFLICT, null);
            db.deleteMember(importedId);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    /** Retry only after an explicit warning; a lost server response can mean a duplicate. */
    public boolean retryCreatedMember(long pendingId) {
        SQLiteDatabase database = db.getWritableDatabase();
        database.beginTransaction();
        try {
            DBMember pending = db.getMember(pendingId);
            if (pending == null || pending.getRemoteIdStr() != null
                    || pending.getState() != DBBill.STATE_ADDED
                    || !"null".equals(get(pendingId, CONFLICT))) return false;
            for (DBMember member : db.getMembersOfProject(pending.getProjectId(), null)) {
                if (member.getRemoteIdStr() != null && member.getName().equals(pending.getName())) return false;
            }
            put(pendingId, ATTEMPT, null);
            put(pendingId, CONFLICT, null);
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
