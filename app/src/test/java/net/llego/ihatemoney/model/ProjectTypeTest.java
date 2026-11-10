package net.llego.ihatemoney.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProjectTypeTest {

    @Test
    public void fromStoredRecognizesSupportedTypes() {
        assertEquals(ProjectType.LOCAL, ProjectType.fromStored("l"));
        assertEquals(ProjectType.IHATEMONEY, ProjectType.fromStored("i"));
    }

    @Test
    public void fromStoredTreatsUnsupportedCospendRowsAsLocal() {
        // Legacy Cospend rows use TYPE='c', which is no longer supported. They must
        // fall back to LOCAL so loading/upgrades never end up with a null type.
        assertEquals(ProjectType.LOCAL, ProjectType.fromStored("c"));
        assertEquals(ProjectType.LOCAL, ProjectType.fromStored(null));
        assertEquals(ProjectType.LOCAL, ProjectType.fromStored("unknown"));
    }
}