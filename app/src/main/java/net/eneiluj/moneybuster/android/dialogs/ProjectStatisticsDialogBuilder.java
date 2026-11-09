package net.eneiluj.moneybuster.android.dialogs;

import static android.view.View.GONE;
import static androidx.core.content.ContextCompat.startActivity;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.DBCategory;
import net.eneiluj.moneybuster.model.DBMember;
import net.eneiluj.moneybuster.model.DBPaymentMode;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;
import net.eneiluj.moneybuster.theme.ThemedMaterialAlertDialogBuilder;
import net.eneiluj.moneybuster.util.SupportUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class ProjectStatisticsDialogBuilder {

    private static final String TAG = ProjectStatisticsDialogBuilder.class.getSimpleName();

    private final Context context;
    private final MoneyBusterSQLiteOpenHelper db;
    private final DBProject proj;
    private final long selectedProjectId;

    private String statsTextToShare;

    private final Calendar calendarMin = Calendar.getInstance();
    private final Calendar calendarMax = Calendar.getInstance();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    private String selectedDateMin;
    private String selectedDateMax;

    private View view;
    private EditText editDateMin;
    private EditText editDateMax;
    private TextView totalPaidText;

    public ProjectStatisticsDialogBuilder(
            @NonNull Context context,
            @NonNull MoneyBusterSQLiteOpenHelper db,
            @NonNull DBProject proj
    ) {
        this.context = context;
        this.db = db;
        this.proj = proj;
        this.selectedProjectId = proj.getId();
    }

    public AlertDialog show() {
        AlertDialog.Builder builder = new ThemedMaterialAlertDialogBuilder(context);

        view = LayoutInflater.from(context).inflate(R.layout.dialog_project_statistics, null);

        editDateMin = view.findViewById(R.id.statsDateMin);
        editDateMax = view.findViewById(R.id.statsDateMax);
        totalPaidText = view.findViewById(R.id.totalPayedText);
        setupDatePickers();

        if (!ProjectType.IHATEMONEY.equals(proj.getType())) {
            setupCategories();
            setupPaymentModes();
        } else {
            LinearLayout statsCategoryLayout = view.findViewById(R.id.statsCategoryLayout);
            statsCategoryLayout.setVisibility(GONE);
            LinearLayout statsPaymentModeLayout = view.findViewById(R.id.statsPaymentModeLayout);
            statsPaymentModeLayout.setVisibility(GONE);
        }

        builder.setView(view);
        builder.setTitle(context.getString(R.string.statistic_dialog_title));
        builder.setIcon(R.drawable.ic_chart_grey_24dp);
        builder.setPositiveButton(context.getString(R.string.simple_ok),
                (DialogInterface dialog, int which) -> dialog.dismiss()
        );
        builder.setNeutralButton(context.getString(R.string.simple_stats_share), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // share it
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.share_stats_title, proj.getName()));
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, statsTextToShare);
                Intent chooserIntent = Intent.createChooser(shareIntent, context.getString(R.string.share_stats_title, proj.getName()));
                startActivity(context, chooserIntent, null);
            }
        });

        updateStatsView();

        return builder.show();
    }

    private void setupCategories() {
        // CATEGORY
        String[] hardCodedCategoryIdsTmp;
        String[] hardCodedCategoryNamesTmp;

        // local projects => hardcoded categories
        if (ProjectType.LOCAL.equals(proj.getType())) {
            hardCodedCategoryNamesTmp = new String[]{
                    context.getString(R.string.category_all),
                    context.getString(R.string.category_all_except_reimbursement),
                    context.getString(R.string.category_none),
                    "\uD83D\uDED2 " + context.getString(R.string.category_groceries),
                    "\uD83C\uDF89 " + context.getString(R.string.category_leisure),
                    "\uD83C\uDFE0 " + context.getString(R.string.category_rent),
                    "\uD83C\uDF29 " + context.getString(R.string.category_bills),
                    "\uD83D\uDEB8 " + context.getString(R.string.category_excursion),
                    "\uD83D\uDC9A " + context.getString(R.string.category_health),
                    "\uD83D\uDECD " + context.getString(R.string.category_shopping),
                    "\uD83D\uDCB0 " + context.getString(R.string.category_reimbursement),
                    "\uD83C\uDF74 " + context.getString(R.string.category_restaurant),
                    "\uD83D\uDECC " + context.getString(R.string.category_accomodation),
                    "\uD83D\uDE8C " + context.getString(R.string.category_transport),
                    "\uD83C\uDFBE " + context.getString(R.string.category_sport)
            };
            hardCodedCategoryIdsTmp = new String[]{
                    "-1000", "-100", "0", "-1", "-2", "-3", "-4", "-5", "-6",
                    "-10", "-11", "-12", "-13", "-14", "-15"
            };
        } else {
            // COSPEND projects => just "no cat" and "reimbursement"
            hardCodedCategoryNamesTmp = new String[]{
                    context.getString(R.string.category_all),
                    context.getString(R.string.category_all_except_reimbursement),
                    context.getString(R.string.category_none),
                    "\uD83D\uDCB0 " + context.getString(R.string.category_reimbursement)
            };
            hardCodedCategoryIdsTmp = new String[]{"-1000", "-100", "0", "-11"};
        }

        List<DBCategory> userCategories = db.getCategories(proj.getId());

        List<String> categoryIdList = new ArrayList<>();
        categoryIdList.add(hardCodedCategoryIdsTmp[0]);
        categoryIdList.add(hardCodedCategoryIdsTmp[1]);
        categoryIdList.add(hardCodedCategoryIdsTmp[2]);
        List<String> categoryNameList = new ArrayList<>();
        categoryNameList.add(hardCodedCategoryNamesTmp[0]);
        categoryNameList.add(hardCodedCategoryNamesTmp[1]);
        categoryNameList.add(hardCodedCategoryNamesTmp[2]);
        for (DBCategory cat : userCategories) {
            categoryIdList.add(String.valueOf(cat.getRemoteId()));
            categoryNameList.add(cat.getIcon() + " " + cat.getName());
        }
        for (int i = 3; i < hardCodedCategoryIdsTmp.length; i++) {
            categoryIdList.add(hardCodedCategoryIdsTmp[i]);
        }
        for (int i = 3; i < hardCodedCategoryNamesTmp.length; i++) {
            categoryNameList.add(hardCodedCategoryNamesTmp[i]);
        }

        String[] categoryIds = categoryIdList.toArray(new String[categoryIdList.size()]);
        String[] categoryNames = categoryNameList.toArray(new String[categoryNameList.size()]);

        ArrayList<Map<String, String>> dataC = new ArrayList<>();
        // add categories
        for (int i = 0; i < categoryNames.length; i++) {
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("name", categoryNames[i]);
            hashMap.put("id", categoryIds[i]);
            dataC.add(hashMap);
        }
        String[] fromC = {"name", "id"};
        int[] toC = new int[]{android.R.id.text1};
        SimpleAdapter simpleAdapterC = new SimpleAdapter(context, dataC, android.R.layout.simple_spinner_item, fromC, toC);
        simpleAdapterC.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner statsCategorySpinner = view.findViewById(R.id.statsCategorySpinner);
        statsCategorySpinner.setAdapter(simpleAdapterC);
        //statsCategorySpinner.setSelection(0);

        statsCategorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                Log.d(TAG, "CATEGORY");
                updateStatsView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.d(TAG, "CATEGORY NOTHING");
            }
        });
    }

    private void setupPaymentModes() {
        // PAYMENT MODE
        List<String> paymentModeNameList = new ArrayList<>();
        List<String> paymentModeIdList = new ArrayList<>();

        paymentModeNameList.add(context.getString(R.string.payment_mode_all));
        paymentModeIdList.add("-1000");
        paymentModeNameList.add(context.getString(R.string.payment_mode_none));
        paymentModeIdList.add("0");
        // local projects => hardcoded pms
        if (ProjectType.LOCAL.equals(proj.getType())) {
            paymentModeNameList.add("\uD83D\uDCB3 " + context.getString(R.string.payment_mode_credit_card));
            paymentModeIdList.add("-1");
            paymentModeNameList.add("\uD83D\uDCB5 " + context.getString(R.string.payment_mode_cash));
            paymentModeIdList.add("-2");
            paymentModeNameList.add("\uD83C\uDFAB " + context.getString(R.string.payment_mode_check));
            paymentModeIdList.add("-3");
            paymentModeNameList.add("⇄ " + context.getString(R.string.payment_mode_transfer));
            paymentModeIdList.add("-4");
            paymentModeNameList.add("\uD83C\uDF0E " + context.getString(R.string.payment_mode_online));
            paymentModeIdList.add("-5");
        }

        List<DBPaymentMode> userPaymentModes = db.getPaymentModes(proj.getId());
        for (DBPaymentMode pm : userPaymentModes) {
            paymentModeIdList.add(String.valueOf(pm.getRemoteId()));
            paymentModeNameList.add(pm.getIcon() + " " + pm.getName());
        }

        String[] paymentModeNames = paymentModeNameList.toArray(new String[paymentModeNameList.size()]);
        String[] paymentModeIds = paymentModeIdList.toArray(new String[paymentModeIdList.size()]);

        ArrayList<Map<String, String>> dataP = new ArrayList<>();
        for (int i = 0; i < paymentModeNames.length; i++) {
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("name", paymentModeNames[i]);
            hashMap.put("id", paymentModeIds[i]);
            dataP.add(hashMap);
        }
        String[] fromP = {"name", "id"};
        int[] toP = new int[]{android.R.id.text1};
        SimpleAdapter simpleAdapterP = new SimpleAdapter(context, dataP, android.R.layout.simple_spinner_item, fromP, toP);
        simpleAdapterP.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner statsPaymentModeSpinner = view.findViewById(R.id.statsPaymentModeSpinner);
        statsPaymentModeSpinner.setAdapter(simpleAdapterP);
        //statsPaymentModeSpinner.setSelection(0);

        statsPaymentModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                Log.d(TAG, "PAYMODE");
                updateStatsView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                Log.d(TAG, "PAYMODE");
            }
        });
    }

    private void setupDatePickers() {
        long[] range = db.getBillDateRange(selectedProjectId);
        selectedDateMin = null;
        selectedDateMax = null;
        if (range != null) {
            calendarMin.setTimeInMillis(range[0] * 1000);
            calendarMax.setTimeInMillis(range[1] * 1000);
            selectedDateMin = sdf.format(calendarMin.getTime());
            selectedDateMax = sdf.format(calendarMax.getTime());
        }
        updateDateLabels();
        editDateMin.setOnClickListener(v -> showDatePicker(true));
        editDateMax.setOnClickListener(v -> showDatePicker(false));
    }

    private void updateDateLabels() {
        java.text.DateFormat dateFormat = android.text.format.DateFormat.getMediumDateFormat(context);
        editDateMin.setText(selectedDateMin == null ? context.getString(R.string.stats_any_date)
                : dateFormat.format(calendarMin.getTime()));
        editDateMax.setText(selectedDateMax == null ? context.getString(R.string.stats_any_date)
                : dateFormat.format(calendarMax.getTime()));
    }

    private void showDatePicker(boolean minimum) {
        Calendar calendar = minimum ? calendarMin : calendarMax;
        DatePickerDialog picker = new DatePickerDialog(context, (datePicker, year, month, day) -> {
            calendar.set(year, month, day, 12, 0, 0);
            String isoDate = sdf.format(calendar.getTime());
            if (minimum) {
                selectedDateMin = isoDate;
            } else {
                selectedDateMax = isoDate;
            }
            updateDateLabels();
            updateStatsView();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        if (minimum && selectedDateMax != null) {
            picker.getDatePicker().setMaxDate(calendarMax.getTimeInMillis());
        } else if (!minimum && selectedDateMin != null) {
            picker.getDatePicker().setMinDate(calendarMin.getTimeInMillis());
        }
        picker.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.stats_any_date),
                (dialog, which) -> {
                    if (minimum) {
                        selectedDateMin = null;
                    } else {
                        selectedDateMax = null;
                    }
                    updateDateLabels();
                    updateStatsView();
                });
        picker.show();
    }

    private void updateStatsView() {
        // get filter values
        int categoryId;
        int paymentModeId;
        if (!proj.getType().equals(ProjectType.IHATEMONEY)) {
            Spinner statsCategorySpinner = view.findViewById(R.id.statsCategorySpinner);
            Map<String, String> item = (Map<String, String>) statsCategorySpinner.getSelectedItem();
            categoryId = item == null ? -1000 : Integer.parseInt(item.get("id"));

            Spinner statsPaymentModeSpinner = view.findViewById(R.id.statsPaymentModeSpinner);
            Map<String, String> itemP = (Map<String, String>) statsPaymentModeSpinner.getSelectedItem();
            paymentModeId = itemP == null ? -1000 : Integer.parseInt(itemP.get("id"));
        } else {
            categoryId = -1;
            paymentModeId = -1;
        }

        Log.v(TAG, "DATESSSS " + selectedDateMin + " and " + selectedDateMax);
        Log.v(TAG, "CATGFIL " + categoryId + " and PAYMODEFIL " + paymentModeId);

        // get stats
        Map<Long, Integer> membersNbBills = new HashMap<>();
        HashMap<Long, Double> membersBalance = new HashMap<>();
        HashMap<Long, Double> membersPaid = new HashMap<>();
        HashMap<Long, Double> membersSpent = new HashMap<>();

        int nbBills = SupportUtil.getStatsOfProject(
                selectedProjectId, db,
                membersNbBills, membersBalance, membersPaid, membersSpent,
                categoryId, paymentModeId, selectedDateMin, selectedDateMax
        );

        List<DBMember> membersSortedByName = db.getMembersOfProject(selectedProjectId, null);
        String projectName;
        if (proj.getName() == null) {
            projectName = proj.getRemoteId();
        } else {
            projectName = proj.getName();
        }
        String statsText = context.getString(R.string.share_stats_intro, projectName) + "\n\n";
        statsText += context.getString(R.string.share_stats_header) + "\n";

        TextView hwho = view.findViewById(R.id.header_who);
        hwho.setTextColor(ContextCompat.getColor(context, R.color.fg_default_low));
        TextView hpaid = view.findViewById(R.id.header_paid);
        hpaid.setTextColor(ContextCompat.getColor(context, R.color.fg_default_low));
        TextView hspent = view.findViewById(R.id.header_spent);
        hspent.setTextColor(ContextCompat.getColor(context, R.color.fg_default_low));
        TextView hbalance = view.findViewById(R.id.header_balance);
        hbalance.setTextColor(ContextCompat.getColor(context, R.color.fg_default_low));
        final TableLayout tl = view.findViewById(R.id.statTable);
        // clear table
        int i;
        for (i = tl.getChildCount() - 1; i > 0; i--) {
            tl.removeViewAt(i);
        }

        double totalPayed = 0.0;

        for (DBMember m : membersSortedByName) {
            totalPayed += membersPaid.get(m.getId());
            statsText += "\n" + m.getName() + " (";

            View row = LayoutInflater.from(context).inflate(R.layout.statistic_row, null);
            TextView wv = row.findViewById(R.id.stat_who);
            wv.setTextColor(ContextCompat.getColor(context, R.color.fg_default));

            wv.setText(m.getName());

            TextView pv = row.findViewById(R.id.stat_paid);
            pv.setTextColor(ContextCompat.getColor(context, R.color.fg_default));
            double rpaid = Math.round((membersPaid.get(m.getId())) * 100.0) / 100.0;
            if (rpaid == 0.0) {
                pv.setText("--");
                statsText += "-- | ";
            } else {
                pv.setText(SupportUtil.normalNumberFormat.format(rpaid));
                statsText += SupportUtil.normalNumberFormat.format(rpaid) + " | ";
            }

            TextView sv = row.findViewById(R.id.stat_spent);
            sv.setTextColor(ContextCompat.getColor(context, R.color.fg_default));
            double rspent = Math.round((membersSpent.get(m.getId())) * 100.0) / 100.0;
            if (rspent == 0.0) {
                sv.setText("--");
                statsText += "-- | ";
            } else {
                sv.setText(SupportUtil.normalNumberFormat.format(rspent));
                statsText += SupportUtil.normalNumberFormat.format(rspent) + " | ";
            }

            TextView bv = row.findViewById(R.id.stat_balance);
            double balance = membersBalance.get(m.getId());
            double rbalance = Math.round(Math.abs(balance) * 100.0) / 100.0;
            String sign = "";
            if (balance > 0) {
                bv.setTextColor(ContextCompat.getColor(context, R.color.green));
                sign = "+";
            } else if (balance < 0) {
                bv.setTextColor(ContextCompat.getColor(context, R.color.red));
                sign = "-";
            } else {
                bv.setTextColor(ContextCompat.getColor(context, R.color.fg_default));
            }
            bv.setText(sign + SupportUtil.normalNumberFormat.format(rbalance));
            statsText += sign + SupportUtil.normalNumberFormat.format(rbalance) + ")";

            tl.addView(row);
        }
        statsTextToShare = statsText;

        totalPaidText.setText(context.getString(R.string.total_payed, SupportUtil.normalNumberFormat.format(totalPayed)));
    }

}
