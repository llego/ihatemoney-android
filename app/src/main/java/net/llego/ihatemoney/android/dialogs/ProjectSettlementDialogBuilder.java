package net.llego.ihatemoney.android.dialogs;

import static androidx.core.content.ContextCompat.startActivity;
import static net.llego.ihatemoney.util.SupportUtil.SETTLE_OPTIMAL;
import static net.llego.ihatemoney.util.SupportUtil.settleBills;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.android.ui.UserAdapter;
import net.llego.ihatemoney.android.ui.UserItem;
import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBBillOwer;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.model.Transaction;
import net.llego.ihatemoney.persistence.MoneyBusterSQLiteOpenHelper;
import net.llego.ihatemoney.theme.ThemedMaterialAlertDialogBuilder;
import net.llego.ihatemoney.util.IRefreshBillsListCallback;
import net.llego.ihatemoney.util.SupportUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProjectSettlementDialogBuilder {

    private static final String TAG = ProjectSettlementDialogBuilder.class.getSimpleName();

    private final Context context;
    private final MoneyBusterSQLiteOpenHelper db;
    private final DBProject proj;
    private final long projectId;
    private final IRefreshBillsListCallback callback;

    private View view;

    public ProjectSettlementDialogBuilder(
            @NonNull Context context,
            @NonNull MoneyBusterSQLiteOpenHelper db,
            @NonNull DBProject proj,
            @NonNull IRefreshBillsListCallback callback
    ) {
        this.context = context;
        this.db = db;
        this.proj = proj;
        this.projectId = proj.getId();
        this.callback = callback;
    }

    public AlertDialog show() {
        AlertDialog.Builder builder = new ThemedMaterialAlertDialogBuilder(context);

        builder.setTitle(context.getString(R.string.settle_dialog_title));
        builder.setIcon(R.drawable.ic_compare_arrows_grey_24dp);
        builder.setPositiveButton(R.string.simple_ok, (DialogInterface dialog, int which) -> dialog.dismiss());

        String projectName;
        if (proj.getName() == null) {
            projectName = proj.getRemoteId();
        } else {
            projectName = proj.getName();
        }

        // show member list
        List<DBMember> memberList = db.getMembersOfProject(projectId, null);
        List<String> nameList = new ArrayList<>();
        List<String> idList = new ArrayList<>();
        nameList.add(context.getString(R.string.center_none));
        idList.add(String.valueOf(0));
        for (DBMember member : memberList) {
            //if (member.isActivated() || member.getId() == bill.getPayerId()) {
            nameList.add(member.getName());
            idList.add(String.valueOf(member.getId()));
            //}
        }
        List<UserItem> userList = new ArrayList<>();
        for (int i = 0; i < nameList.size(); i++) {
            userList.add(new UserItem(Long.valueOf(idList.get(i)), nameList.get(i)));
        }

        // get stats
        Map<Long, Integer> membersNbBills = new HashMap<>();
        HashMap<Long, Double> membersBalance = new HashMap<>();
        HashMap<Long, Double> membersPaid = new HashMap<>();
        HashMap<Long, Double> membersSpent = new HashMap<>();

        int nbBills = SupportUtil.getStatsOfProject(
                proj.getId(), db,
                membersNbBills, membersBalance, membersPaid, membersSpent,
                -1000, -1000, null, null
        );

        List<DBMember> membersSortedByName = db.getMembersOfProject(proj.getId(), MoneyBusterSQLiteOpenHelper.key_name);

        // get members names per id
        final Map<Long, String> memberIdToName = new HashMap<>();
        for (DBMember m : membersSortedByName) {
            memberIdToName.put(m.getId(), m.getName());
        }

        // check if expenses are already balanced
        final List<Transaction> transactions_check_balanced = settleBills(membersSortedByName, membersBalance, SETTLE_OPTIMAL);
        if (transactions_check_balanced == null || transactions_check_balanced.size() == 0) {
            view = LayoutInflater.from(context).inflate(R.layout.dialog_project_settlement_balanced, null);
            builder.setView(view);
            return builder.show();
        }

        view = LayoutInflater.from(context).inflate(R.layout.dialog_project_settlement, null);
        builder.setView(view);

        // table header
        @ColorInt int headerColor = ContextCompat.getColor(context, R.color.fg_default_low);
        TextView hwho = view.findViewById(R.id.header_who);
        hwho.setTextColor(headerColor);
        TextView htowhom = view.findViewById(R.id.header_towhom);
        htowhom.setTextColor(headerColor);
        TextView hhowmuch = view.findViewById(R.id.header_howmuch);
        hhowmuch.setTextColor(headerColor);

        UserAdapter userAdapter = new UserAdapter(context, userList);
        Spinner centerMemberSpinner = view.findViewById(R.id.memberCenterSpinner);
        centerMemberSpinner.setAdapter(userAdapter);
        centerMemberSpinner.getSelectedItemPosition();

        builder.setNegativeButton(R.string.simple_create_bills, (DialogInterface dialog, int which) -> {
                    UserItem item = (UserItem) centerMemberSpinner.getSelectedItem();
                    final List<Transaction> transactions = settleBills(membersSortedByName, membersBalance, item.getId());
                    if (transactions == null || transactions.size() == 0) {
                        return;
                    }
                    createBillsFromTransactions(projectId, transactions);
                }
        );
        builder.setNeutralButton(R.string.simple_settle_share, (DialogInterface dialog, int which) -> {
                    String text = context.getString(R.string.share_settle_intro, projectName) + "\n";
                    UserItem item = (UserItem) centerMemberSpinner.getSelectedItem();
                    final List<Transaction> transactions = settleBills(membersSortedByName, membersBalance, item.getId());
                    if (transactions == null || transactions.size() == 0) {
                        return;
                    }
                    // generate text to share
                    for (Transaction t : transactions) {
                        double amount = Math.round(t.getAmount() * 100.0) / 100.0;
                        Log.v(TAG, "TRANSAC " + memberIdToName.get(t.getOwerMemberId()) + " => "
                                + memberIdToName.get(t.getReceiverMemberId()) + " ("
                                + amount + ")"
                        );
                        text += "\n" + context.getString(
                                R.string.share_settle_sentence,
                                memberIdToName.get(t.getOwerMemberId()),
                                memberIdToName.get(t.getReceiverMemberId()),
                                amount
                        );
                    }
                    // share it
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.share_settle_title, projectName));
                    shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, text);
                    Intent chooserIntent = Intent.createChooser(shareIntent, context.getString(R.string.share_settle_title, projectName));
                    startActivity(context, chooserIntent, null);
                }
        );

        // center spinner event
        centerMemberSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (position < 0) return;

                UserItem item = (UserItem) centerMemberSpinner.getSelectedItem();
                Log.d(TAG, "CENTER ON " + item.getId() + " " + item.getName());
                updateSettlement(membersBalance, memberIdToName, item.getId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.d(TAG, "CENTER NOTHING");
            }
        });

        UserItem item = (UserItem) centerMemberSpinner.getSelectedItem();
        updateSettlement(membersBalance, memberIdToName, item.getId());

        return builder.show();
    }

    private void updateSettlement(
            HashMap<Long, Double> membersBalance,
            Map<Long, String> memberIdToName,
            long memberId
    ) {
        List<DBMember> membersSortedByName = db.getMembersOfProject(projectId, MoneyBusterSQLiteOpenHelper.key_name);
        final List<Transaction> transactions = settleBills(membersSortedByName, membersBalance, memberId);
        if (transactions == null || transactions.size() == 0) {
            return;
        }

        final TableLayout tl = view.findViewById(R.id.settleTable);
        //tl.removeAllViews();
        // clear table
        int i;
        for (i = tl.getChildCount() - 1; i > 0; i--) {
            tl.removeViewAt(i);
        }

        @ColorInt int rowColor = ContextCompat.getColor(context, R.color.fg_default);
        for (Transaction t : transactions) {
            View row = LayoutInflater.from(context).inflate(R.layout.settle_row, null);
            TextView wv = row.findViewById(R.id.settle_who);
            wv.setTextColor(rowColor);
            wv.setText(memberIdToName.get(t.getOwerMemberId()));

            TextView pv = row.findViewById(R.id.settle_towhom);
            pv.setTextColor(rowColor);
            pv.setText(memberIdToName.get(t.getReceiverMemberId()));

            TextView sv = row.findViewById(R.id.settle_howmuch);
            sv.setTextColor(rowColor);
            double amount = Math.round(t.getAmount() * 100.0) / 100.0;
            sv.setText(SupportUtil.normalNumberFormat.format(amount));

            tl.addView(row);
        }
    }

    private void createBillsFromTransactions(long projectId, List<Transaction> transactions) {
        long timestamp = System.currentTimeMillis() / 1000;

        for (Transaction t : transactions) {
            long owerId = t.getOwerMemberId();
            long receiverId = t.getReceiverMemberId();
            //double amount = Math.round(t.getAmount() * 100.0) / 100.0;
            double amount = t.getAmount();
            DBBill bill = new DBBill(
                    0, 0, projectId, owerId, amount,
                    timestamp, context.getString(R.string.settle_bill_what),
                    DBBill.STATE_ADDED, DBBill.NON_REPEATED,
                    DBBill.PAYMODE_NONE, DBBill.CATEGORY_NONE,
                    "", DBBill.PAYMODE_ID_NONE);
            bill.getBillOwers().add(new DBBillOwer(0, 0, receiverId));
            db.addBill(bill);
        }
        callback.refreshLists(true);
    }

}
