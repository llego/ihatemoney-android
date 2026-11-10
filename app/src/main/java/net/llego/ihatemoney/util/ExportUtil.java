package net.llego.ihatemoney.util;

import net.llego.ihatemoney.model.DBBill;
import net.llego.ihatemoney.model.DBBillOwer;
import net.llego.ihatemoney.model.DBCategory;
import net.llego.ihatemoney.model.DBCurrency;
import net.llego.ihatemoney.model.DBMember;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.persistence.MoneyBusterSQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ExportUtil {

    public static String createExportContent(MoneyBusterSQLiteOpenHelper db, long projectId) {
        String fileContent = "";

        // get information
        DBProject project = db.getProject(projectId);
        Map<Long, DBMember> membersById = new HashMap<>();
        List<DBMember> members = db.getMembersOfProject(projectId, null);
        for (DBMember m : members) {
            membersById.put(m.getId(), m);
        }
        List<DBBill> bills = db.getBillsOfProject(projectId);

        String payerName;
        double payerWeight;
        int payerActive;
        Long payerId;
        String owersTxt;

        // write header
        fileContent += "what,amount,date,timestamp,payer_name,payer_weight,payer_active,owers,repeat,categoryid,paymentmode\n";

        // write members
        for (DBMember m : members) {
            DBBill fakeBill = new DBBill(
                    0, 0, projectId, m.getId(), 1, 666,
                    "deleteMeIfYouWant", DBBill.STATE_OK, DBBill.NON_REPEATED,
                    DBBill.PAYMODE_NONE, 0, "", 0
            );
            List<DBBillOwer> fakeBillOwers = new ArrayList<>();
            fakeBillOwers.add(new DBBillOwer(0, 0, m.getId()));
            fakeBill.setBillOwers(fakeBillOwers);
            bills.add(0, fakeBill);
        }

        // write bills
        for (DBBill b : bills) {
            payerId = b.getPayerId();
            payerName = membersById.get(payerId).getName();
            payerWeight = membersById.get(payerId).getWeight();
            payerActive = membersById.get(payerId).isActivated() ? 1 : 0;
            List<DBBillOwer> billOwers = b.getBillOwers();
            owersTxt = "";
            for (DBBillOwer bo : billOwers) {
                owersTxt += membersById.get(bo.getMemberId()).getName() + ",";
            }
            owersTxt = owersTxt.replaceAll(", $", "");
            fileContent += "\"" + b.getWhat() + "\"," + b.getAmount() + "," + b.getDate() + "," + b.getTimestamp() + ",\"" + payerName + "\"," +
                    payerWeight + "," + payerActive + ",\"" + owersTxt + "\"," + b.getRepeat() + "," + b.getCategoryRemoteId() +
                    "," + b.getPaymentMode() + "\n";
        }

        // write categories
        List<DBCategory> cats = db.getCategories(projectId);
        if (cats.size() > 0) {
            fileContent += "\ncategoryname,categoryid,icon,color\n";
            for (DBCategory cat : cats) {
                fileContent += "\"" + cat.getName() + "\"," + cat.getId() + ",\"" + cat.getIcon() + "\",\"" + cat.getColor() + "\"\n";
            }
        }

        // write currencies
        List<DBCurrency> curs = db.getCurrencies(projectId);
        if (curs.size() > 0 && project.getCurrencyName() != null &&
                !project.getCurrencyName().isEmpty() && !project.getCurrencyName().equals("null")) {
            fileContent += "\ncurrencyname,exchange_rate\n";
            fileContent += "\"" + project.getCurrencyName() + "\",1\n";
            for (DBCurrency cur : curs) {
                fileContent += "\"" + cur.getName() + "\"," + cur.getExchangeRate() + "\n";
            }
        }

        return fileContent;
    }

    public static String createExportFileName(MoneyBusterSQLiteOpenHelper db, long projectId) {
        DBProject project = db.getProject(projectId);
        if (project.getName() == null || project.getName().equals("")) {
            return project.getRemoteId() + ".csv";
        } else {
            return project.getName() + ".csv";
        }
    }
}
