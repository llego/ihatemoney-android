package net.eneiluj.moneybuster.util;

import org.junit.Test;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExpenseTerminologyTest {
    @Test
    public void englishUiUsesExpenseInsteadOfBill() throws Exception {
        File resources = new File("src/main/res");
        if (!resources.isDirectory()) resources = new File("app/src/main/res");
        Pattern oldTerm = Pattern.compile("\\bbills?\\b", Pattern.CASE_INSENSITIVE);
        for (String locale : new String[]{"values", "values-en-rGB", "values-en-rUS"}) {
            File[] files = new File(resources, locale).listFiles((dir, name) -> name.endsWith(".xml"));
            assertTrue("Missing English resources for " + locale, files != null && files.length > 0);
            for (File file : files) {
                NodeList strings = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                        .parse(file).getElementsByTagName("string");
                for (int i = 0; i < strings.getLength(); i++) {
                    String value = strings.item(i).getTextContent();
                    assertFalse(file + ": " + value, oldTerm.matcher(value).find());
                }
            }
        }
    }
}
