package net.eneiluj.moneybuster.android.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.DBProject;
import net.eneiluj.moneybuster.model.ProjectType;

import java.util.ArrayList;
import java.util.List;


public class ProjectDrawerAdapter extends RecyclerView.Adapter<ProjectDrawerAdapter.ProjectViewHolder> {

    @NonNull
    private final ArrayList<ProjectItem> items = new ArrayList<>();

    private final IOnProjectMenuClick callback;

    public ProjectDrawerAdapter(IOnProjectMenuClick callback) {
        this.callback = callback;
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View itemView = inflater.inflate(R.layout.item_drawer_project, parent, false);
        return new ProjectViewHolder(itemView, callback);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Call this when the project list has changed (projects added/removed)
     */
    public void setItems(List<DBProject> dbProjects, long selectedProjectId) {
        items.clear();
        for (DBProject project : dbProjects) {
            boolean isSelected = project.getId() == selectedProjectId;
            items.add(new ProjectItem(project, isSelected));
        }
        notifyDataSetChanged();
    }

    /**
     * Call this to select a new project without changing the underlying project list
     */
    public void setSelected(long projectId) {
        for (ProjectItem item : items) {
            item.isSelected = item.project.getId() == projectId;
        }
        notifyDataSetChanged();
    }

    /* --- The items in the adapter --- */

    public static class ProjectItem {
        @NonNull
        public DBProject project;

        public boolean isSelected;

        public ProjectItem(@NonNull DBProject project, boolean isSelected) {
            this.project = project;
            this.isSelected = isSelected;
        }

        public Drawable getDrawable(Context context) {
            if (project.isLocal()) {
                return ContextCompat.getDrawable(context, R.drawable.ic_phone_android_grey_24dp);
            } else if (ProjectType.COSPEND.equals(project.getType())) {
                return ContextCompat.getDrawable(context, R.drawable.ic_cospend_grey_24dp);
            } else if (ProjectType.IHATEMONEY.equals(project.getType())) {
                return ContextCompat.getDrawable(context, R.drawable.ic_ihm_grey_24dp);
            }
            return null;
        }
    }

    /* --- The interface for callbacks --- */

    public interface IOnProjectMenuClick {
        void onProjectClick(long projectId);

        void onEditProjectClick(long projectId);

        void onManageMembersClick(long projectId);

        void onProjectStatisticsClick(long projectId);

        void onSettleProjectClick(long projectId);

    }

    /* --- The ViewHolder --- */

    public static class ProjectViewHolder extends RecyclerView.ViewHolder {

        @NonNull
        private final View itemView;

        @NonNull
        private final IOnProjectMenuClick callback;

        private ProjectItem projectItem;

        public ProjectViewHolder(@NonNull View itemView, @NonNull IOnProjectMenuClick callback) {
            super(itemView);

            this.itemView = itemView;
            this.callback = callback;
        }

        public void bind(ProjectItem item) {
            projectItem = item;

            DBProject project = item.project;
            Context context = itemView.getContext();

            TextView title = itemView.findViewById(R.id.text_project_title);
            TextView subtitle = itemView.findViewById(R.id.text_project_url);
            ImageView icon = itemView.findViewById(R.id.project_icon);
            ImageView options = itemView.findViewById(R.id.project_options);

            itemView.setSelected(item.isSelected);
            options.setContentDescription(context.getString(R.string.drawer_project_options,
                    project.getName() == null ? project.getRemoteId() : project.getName()));
            itemView.setOnClickListener((view -> callback.onProjectClick(project.getId())));

            if (project.getName() == null || project.getServerUrl() == null || project.isLocal()) {
                title.setText(project.getRemoteId());
                subtitle.setVisibility(View.GONE);
            } else {
                Uri server = Uri.parse(project.getServerUrl());
                String prettyUrl = server.getAuthority();
                if (prettyUrl == null) {
                    prettyUrl = project.getServerUrl();
                }
                title.setText(project.getName());
                subtitle.setText(prettyUrl);
                subtitle.setVisibility(View.VISIBLE);
            }

            icon.setImageDrawable(item.getDrawable(context));

            options.setOnClickListener(this::onMenuClicked);
        }

        private void onMenuClicked(View view) {
            final var popup = new PopupMenu(itemView.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.menu_drawer_project_options, popup.getMenu());
            popup.setForceShowIcon(true);

            long projectId = projectItem.project.getId();

            popup.setOnMenuItemClickListener((MenuItem menuItem) -> {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.opt_edit_project) {
                    callback.onEditProjectClick(projectId);
                    return true;
                } else if (itemId == R.id.opt_manage_members) {
                    callback.onManageMembersClick(projectId);
                    return true;
                } else if (itemId == R.id.opt_project_statistics) {
                    callback.onProjectStatisticsClick(projectId);
                    return true;
                } else if (itemId == R.id.opt_settle_project) {
                    callback.onSettleProjectClick(projectId);
                    return true;
                }
                return false;

            });
            popup.show();
        }
    }
}
