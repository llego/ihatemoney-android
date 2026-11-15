package net.llego.ihatemoney.util;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBBillOwer;
import net.llego.ihatemoney.model.DBMember;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class SpliitFixtures {
    private SpliitFixtures() { }

    public static final String EXPENSE = """
            {"id":"expense-1","groupId":"group-1","title":"Lunch","amount":1250,
             "expenseDate":"2026-11-14T12:00:00.000Z","createdAt":"2026-11-14T12:00:00.000Z",
             "categoryId":7,"paidById":"alice","paidBy":{"id":"alice","name":"Alice"},
             "paidFor":[{"participantId":"bob","shares":"100"},{"participantId":"alice","shares":"100"}],
             "splitMode":"EVENLY","recurrenceRule":"NONE","isReimbursement":true,
             "documents":[{"id":"receipt","url":"https://example.org/receipt.jpg","width":800,"height":600}],
             "notes":"Keep receipt","originalAmount":null,"originalCurrency":null,"conversionRate":null}
            """;

    public static DBBill bill() {
        DBBill bill = new DBBill(1, 0, 1, 1, 12.50,
                Instant.parse("2026-11-14T12:00:00Z").getEpochSecond(), "Lunch", DBBill.STATE_EDITED,
                DBBill.NON_REPEATED, DBBill.PAYMODE_NONE, DBBill.CATEGORY_NONE, "Keep receipt", 0);
        bill.setRemoteIdStr("expense-1");
        bill.getBillOwers().add(new DBBillOwer(1, 1, 1));
        bill.getBillOwers().add(new DBBillOwer(2, 1, 2));
        return bill;
    }

    public static Map<Long, DBMember> members() {
        Map<Long, DBMember> members = new HashMap<>();
        for (long id = 1; id <= 2; id++) {
            DBMember member = new DBMember(id, 0, 1, id == 1 ? "Alice" : "Bob", true, 1,
                    DBBill.STATE_OK, null, null, null, null, null);
            member.setRemoteIdStr(id == 1 ? "alice" : "bob");
            members.put(id, member);
        }
        return members;
    }
}
