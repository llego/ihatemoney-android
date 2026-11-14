package net.llego.ihatemoney.persistence;

import android.content.ContentValues;
import android.database.Cursor;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.util.SpliitExpenseCodec;

/** Durable baselines, unresolved remote versions and uncertain mutation attempts. */
public final class SpliitSyncStore {
    public static final String BASELINE = "SPLIITBASELINE";
    public static final String CONFLICT = "SPLIITCONFLICT";
    public static final String ATTEMPT = "SPLIITATTEMPT";
    static final String COLUMNS = BASELINE + " TEXT, " + CONFLICT + " TEXT, " + ATTEMPT + " TEXT";
    static final String[] MIGRATIONS = {
            "ALTER TABLE BILLS ADD COLUMN " + BASELINE + " TEXT",
            "ALTER TABLE BILLS ADD COLUMN " + CONFLICT + " TEXT",
            "ALTER TABLE BILLS ADD COLUMN " + ATTEMPT + " TEXT"
    };
    private final MoneyBusterSQLiteOpenHelper db;

    public SpliitSyncStore(MoneyBusterSQLiteOpenHelper db) { this.db = db; }

    public String get(long billId, String column) {
        try (Cursor cursor = db.getReadableDatabase().query("BILLS", new String[]{column},
                "ID = ?", new String[]{String.valueOf(billId)}, null, null, null)) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getString(0) : null;
        }
    }

    public void put(long billId, String column, String value) {
        ContentValues values = new ContentValues();
        values.put(column, value);
        db.getWritableDatabase().update("BILLS", values, "ID = ?", new String[]{String.valueOf(billId)});
    }

    public boolean canEdit(DBBill bill) {
        if (bill == null) return false;
        return get(bill.getId(), CONFLICT) == null && get(bill.getId(), ATTEMPT) == null
                && (bill.getId() == 0 || (bill.getState() == DBBill.STATE_ADDED
                && bill.getRemoteIdStr() == null) || SpliitExpenseCodec.editable(get(bill.getId(), BASELINE)));
    }

    /** The next sync rechecks this version; choosing local never bypasses a later conflict. */
    public void resolve(long billId, boolean keepLocal) {
        android.database.sqlite.SQLiteDatabase database = db.getWritableDatabase();
        database.beginTransaction();
        try {
            String conflict = get(billId, CONFLICT);
            if (conflict == null) return;
            if (!keepLocal) {
                // Reimport the full remote version, if it still exists, on the next sync.
                db.deleteBill(billId);
            } else {
                put(billId, BASELINE, "null".equals(conflict) ? null : conflict);
                if (!"null".equals(conflict)) {
                    String remoteId = SpliitExpenseCodec.parse(conflict).get("id").getAsString();
                    DBBill bill = db.getBill(billId);
                    DBBill imported = db.getBillByRemoteIdStr(remoteId, bill.getProjectId());
                    if (imported != null && imported.getId() != billId) {
                        if (imported.getState() != DBBill.STATE_OK) return;
                        db.deleteBill(imported.getId());
                    }
                    ContentValues values = new ContentValues();
                    values.put("REMOTEIDSTR", remoteId);
                    database.update("BILLS", values, "ID = ?", new String[]{String.valueOf(billId)});
                }
                if ("null".equals(conflict)) {
                    ContentValues values = new ContentValues();
                    values.putNull("REMOTEIDSTR");
                    DBBill bill = db.getBill(billId);
                    values.put("STATE", bill.getState() == DBBill.STATE_DELETED
                            ? DBBill.STATE_DELETED : DBBill.STATE_ADDED);
                    database.update("BILLS", values, "ID = ?", new String[]{String.valueOf(billId)});
                }
                put(billId, CONFLICT, null);
                put(billId, ATTEMPT, null);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }
}
