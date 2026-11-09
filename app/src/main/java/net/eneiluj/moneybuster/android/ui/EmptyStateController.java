package net.eneiluj.moneybuster.android.ui;

import android.view.View;

/**
 * Owns the empty-state / list visibility for the expense screen.
 *
 * <p>Exactly one of the list or an empty-state panel is shown. The three panels are:
 * no projects (nothing configured), no members (project exists), and no bills.</p>
 */
public final class EmptyStateController {
    private final View listView;
    private final View noProjects;
    private final View noMembers;
    private final View noBills;

    public EmptyStateController(View listView, View noProjects, View noMembers, View noBills) {
        this.listView = listView;
        this.noProjects = noProjects;
        this.noMembers = noMembers;
        this.noBills = noBills;
    }

    public void showNoProjects() {
        listView.setVisibility(View.GONE);
        noProjects.setVisibility(View.VISIBLE);
        noMembers.setVisibility(View.GONE);
        noBills.setVisibility(View.GONE);
    }

    public void showNoMembers() {
        listView.setVisibility(View.GONE);
        noProjects.setVisibility(View.GONE);
        noMembers.setVisibility(View.VISIBLE);
        noBills.setVisibility(View.GONE);
    }

    public void showNoBills() {
        listView.setVisibility(View.GONE);
        noProjects.setVisibility(View.GONE);
        noMembers.setVisibility(View.GONE);
        noBills.setVisibility(View.VISIBLE);
    }

    public void showList() {
        listView.setVisibility(View.VISIBLE);
        noProjects.setVisibility(View.GONE);
        noMembers.setVisibility(View.GONE);
        noBills.setVisibility(View.GONE);
    }
}