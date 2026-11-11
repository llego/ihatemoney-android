package net.llego.ihatemoney.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DBProjectTest {

    @Test
    public void spliitProjectsAreReadOnly() {
        DBProject project = new DBProject(
                1, "group-id", null, "Group", "https://spliit.example",
                null, null, ProjectType.SPLIIT, 0L, "€", false,
                DBProject.ACCESS_LEVEL_UNKNOWN, null);

        assertTrue(project.isReadOnly());
        assertFalse(project.isLocal());
        assertEquals("https://spliit.example/groups/group-id", project.getPublicWebUrl());
        assertEquals(project.getPublicWebUrl(), project.getShareUrl());
    }

    @Test
    public void ihatemoneyProjectsAreWritable() {
        DBProject project = new DBProject(
                1, "proj", "pwd", "Project", "https://ihatemoney.example",
                "a@b.c", null, ProjectType.IHATEMONEY, 0L, "EUR", false,
                DBProject.ACCESS_LEVEL_UNKNOWN, null);

        assertFalse(project.isReadOnly());
        assertEquals("https://ihatemoney.example/proj", project.getPublicWebUrl());
        assertEquals("ihatemoney://ihatemoney.example/proj/pwd", project.getShareUrl());
    }
}
