package net.llego.ihatemoney.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SpliitUrlTest {

    @Test
    public void parsesPlainGroupUrl() {
        SpliitUrl url = SpliitUrl.parse("https://spliit.app/groups/abc123");
        assertNotNull(url);
        assertEquals("https://spliit.app", url.baseUrl);
        assertEquals("abc123", url.groupId);
    }

    @Test
    public void parsesSharedExpensesUrl() {
        SpliitUrl url = SpliitUrl.parse(
                "https://spliit.app/groups/abc123/expenses?ref=share");
        assertNotNull(url);
        assertEquals("https://spliit.app", url.baseUrl);
        assertEquals("abc123", url.groupId);
    }

    @Test
    public void keepsSelfHostedSubPath() {
        SpliitUrl url = SpliitUrl.parse(
                "https://example.com/spliit/groups/abc123/expenses");
        assertNotNull(url);
        assertEquals("https://example.com/spliit", url.baseUrl);
        assertEquals("abc123", url.groupId);
    }

    @Test
    public void keepsPort() {
        SpliitUrl url = SpliitUrl.parse("http://localhost:3000/groups/abc123");
        assertNotNull(url);
        assertEquals("http://localhost:3000", url.baseUrl);
        assertEquals("abc123", url.groupId);
    }

    @Test
    public void acceptsCustomScheme() {
        SpliitUrl url = SpliitUrl.parse("spliit://spliit.app/groups/abc123");
        assertNotNull(url);
        assertEquals("https://spliit.app", url.baseUrl);
        assertEquals("abc123", url.groupId);
    }

    @Test
    public void acceptsCustomSchemeWithSubPath() {
        SpliitUrl url = SpliitUrl.parse("spliit://example.com/spliit/groups/abc123");
        assertNotNull(url);
        assertEquals("https://example.com/spliit", url.baseUrl);
        assertEquals("abc123", url.groupId);
    }

    @Test
    public void acceptsCustomSchemeWithoutGroupsSegment() {
        SpliitUrl url = SpliitUrl.parse("spliit://example.com/abc123");
        assertNotNull(url);
        assertEquals("https://example.com", url.baseUrl);
        assertEquals("abc123", url.groupId);
    }

    @Test
    public void rejectsNonGroupUrls() {
        assertNull(SpliitUrl.parse(null));
        assertNull(SpliitUrl.parse(""));
        assertNull(SpliitUrl.parse("https://spliit.app/"));
        assertNull(SpliitUrl.parse("https://spliit.app/groups/"));
        assertNull(SpliitUrl.parse("https://spliit.app/groups"));
        assertNull(SpliitUrl.parse("not a url"));
    }
}
