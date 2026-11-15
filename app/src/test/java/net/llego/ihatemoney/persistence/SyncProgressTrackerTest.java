package net.llego.ihatemoney.persistence;

import net.llego.ihatemoney.model.SyncProgress;
import net.llego.ihatemoney.model.SyncProgress.Stage;

import org.junit.Test;

import static org.junit.Assert.*;

public class SyncProgressTrackerTest {
    @Test
    public void throttlesEventsButRetainsTheLatestCountForAReopenedScreen() {
        SyncProgressTracker tracker = new SyncProgressTracker();
        assertTrue(tracker.update(progress(1, Stage.DOWNLOADING, 0, 1995), 0));
        assertFalse(tracker.update(progress(1, Stage.DOWNLOADING, 1, 1995), 10));
        assertEquals(1, tracker.get(1).completed);
        assertTrue(tracker.update(progress(1, Stage.DOWNLOADING, 20, 1995), 250));
        assertFalse(tracker.update(progress(1, Stage.DOWNLOADING, 20, 1995), 500));
    }

    @Test
    public void publishesPhaseChangesNewTotalsAndFinalCountsImmediately() {
        SyncProgressTracker tracker = new SyncProgressTracker();
        assertTrue(tracker.update(progress(1, Stage.PROJECT, 0, -1), 0));
        assertTrue(tracker.update(progress(1, Stage.LISTING, 100, -1), 1));
        assertTrue(tracker.update(progress(1, Stage.DOWNLOADING, 0, 100), 2));
        assertTrue(tracker.update(progress(1, Stage.DOWNLOADING, 100, 100), 3));
        assertTrue(tracker.update(progress(1, Stage.SAVING, 0, 100), 4));
        assertEquals(Stage.SAVING, tracker.get(1).stage);
    }

    @Test
    public void doesNotMixProjectsWhenSwitchingOrWhenAnotherProjectFinishes() {
        SyncProgressTracker tracker = new SyncProgressTracker();
        tracker.update(progress(1, Stage.DOWNLOADING, 24, 1995), 0);
        tracker.update(progress(2, Stage.QUEUED, 0, -1), 0);
        assertEquals(24, tracker.get(1).completed);
        assertEquals(Stage.QUEUED, tracker.get(2).stage);
        tracker.clear(2);
        assertNull(tracker.get(2));
        assertEquals(Stage.DOWNLOADING, tracker.get(1).stage);
    }

    @Test
    public void completionOrFailureClearsTheSnapshotAndAllowsImmediateRetry() {
        SyncProgressTracker tracker = new SyncProgressTracker();
        tracker.update(progress(1, Stage.DOWNLOADING, 3, 100), 0);
        tracker.clear(1);
        assertNull(tracker.get(1));
        assertTrue(tracker.update(progress(1, Stage.PREPARING, 0, -1), 1));
        tracker.clear(1);
        tracker.clear(1);
        assertNull(tracker.get(1));
    }

    @Test
    public void unknownTotalNeverPretendsToBeAPercentage() {
        SyncProgress progress = progress(1, Stage.DOWNLOADING, 0, -1);
        assertFalse(progress.hasTotal());
        assertEquals(0, progress.percent());
        SyncProgress empty = progress(1, Stage.SAVING, 0, 0);
        assertFalse(empty.hasTotal());
        assertEquals(0, empty.percent());
    }

    @Test
    public void countsAreSafeAndPercentCalculationDoesNotOverflow() {
        assertEquals(50, progress(1, Stage.DOWNLOADING, 1000, 2000).percent());
        assertEquals(100, progress(1, Stage.SAVING, Integer.MAX_VALUE, Integer.MAX_VALUE).percent());
        assertEquals(0, progress(1, Stage.DOWNLOADING, -1, 2000).completed);
        assertEquals(100, progress(1, Stage.SAVING, 10, 5).percent());
    }

    @Test
    public void finishingAnOlderTaskCannotHideANewerTaskForTheSameProject() {
        SyncProgressTracker tracker = new SyncProgressTracker();
        tracker.update(10, progress(1, Stage.DOWNLOADING, 100, 100), 0);
        tracker.update(11, progress(1, Stage.UPLOADING, 0, -1), 1);
        assertFalse(tracker.clear(1, 10));
        assertEquals(Stage.UPLOADING, tracker.get(1).stage);
        assertTrue(tracker.clear(1, 11));
        assertNull(tracker.get(1));
    }

    @Test
    public void queueingARefreshCannotReplaceTheProgressOfARunningSync() {
        SyncProgressTracker tracker = new SyncProgressTracker();
        tracker.update(10, progress(1, Stage.DOWNLOADING, 500, 1995), 0);
        assertFalse(tracker.queueIfIdle(progress(1, Stage.QUEUED, 0, -1), 1));
        assertEquals(500, tracker.get(1).completed);
        assertTrue(tracker.queueIfIdle(progress(2, Stage.QUEUED, 0, -1), 1));
        assertEquals(Stage.QUEUED, tracker.get(2).stage);
    }

    private static SyncProgress progress(long projectId, Stage stage, int count, int total) {
        return new SyncProgress(projectId, stage, count, total);
    }
}
