package net.llego.ihatemoney.util;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;

/** Resource regressions that would make empty screens unreachable by pull-to-refresh. */
public class ExpenseRefreshLayoutTest {
    @Test
    public void emptyStatesAndListShareTheSameRefreshContainer() throws Exception {
        Document document = layout();
        Element refresh = find(document, "swipe_refresh_layout");
        assertEquals("androidx.swiperefreshlayout.widget.SwipeRefreshLayout", refresh.getTagName());
        for (String id : new String[]{"list_bill_items", "layout_no_projects", "layout_no_project_members", "layout_no_bills"}) {
            assertTrue(id + " must be inside the refresh target", descendant(find(document, id), refresh));
        }
    }

    @Test
    public void refreshHasOneFullHeightChildAndTheListFillsItsTouchArea() throws Exception {
        Document document = layout();
        Element refresh = find(document, "swipe_refresh_layout");
        int directChildren = 0;
        for (Node child = refresh.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) directChildren++;
        }
        assertEquals(1, directChildren);
        assertEquals("match_parent", find(document, "refresh_content").getAttribute("android:layout_height"));
        assertEquals("match_parent", find(document, "list_bill_items").getAttribute("android:layout_height"));
    }

    @Test
    public void progressIsOutsideTheRefreshTargetSoItCannotBeHiddenByAnEmptyState() throws Exception {
        Document document = layout();
        assertFalse(descendant(find(document, "sync_status"), find(document, "swipe_refresh_layout")));
    }

    private static Document layout() throws Exception {
        File file = new File("src/main/res/layout/activity_bills_list_view.xml");
        if (!file.exists()) file = new File("app/src/main/res/layout/activity_bills_list_view.xml");
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
    }

    private static Element find(Document document, String id) {
        var elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (("@+id/" + id).equals(element.getAttribute("android:id"))) return element;
        }
        throw new AssertionError("Missing view: " + id);
    }

    private static boolean descendant(Node view, Node ancestor) {
        for (Node parent = view.getParentNode(); parent != null; parent = parent.getParentNode()) {
            if (parent == ancestor) return true;
        }
        return false;
    }
}
