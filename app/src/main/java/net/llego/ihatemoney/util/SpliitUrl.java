package net.llego.ihatemoney.util;

import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a Spliit group link.
 *
 * <p>Spliit has no join form: the link is the group. A shared link looks like
 * {@code https://spliit.app/groups/<id>/expenses?ref=share}. The group id is the
 * segment after {@code groups}; the instance base URL is everything before it,
 * so self-hosted instances mounted under a sub-path work too.</p>
 */
public final class SpliitUrl {

    public final String baseUrl;
    public final String groupId;

    private SpliitUrl(String baseUrl, String groupId) {
        this.baseUrl = baseUrl;
        this.groupId = groupId;
    }

    @Nullable
    public static SpliitUrl parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        boolean customScheme = value.startsWith("spliit://");
        if (customScheme) {
            value = "https://" + value.substring("spliit://".length());
        } else if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return null;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null
                || (!scheme.equals("https") && !scheme.equals("http"))) {
            return null;
        }

        String path = uri.getPath();
        if (path == null) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        if (segments.isEmpty()) {
            return null;
        }

        int groupsIndex = segments.indexOf("groups");
        String groupId;
        int baseEnd;
        if (groupsIndex != -1) {
            if (groupsIndex + 1 >= segments.size()) {
                return null;
            }
            groupId = segments.get(groupsIndex + 1);
            baseEnd = groupsIndex;
        } else if (customScheme) {
            // spliit://host/<group> is also accepted.
            groupId = segments.get(segments.size() - 1);
            baseEnd = segments.size() - 1;
        } else {
            return null;
        }
        if (groupId.isEmpty()) {
            return null;
        }

        StringBuilder base = new StringBuilder(scheme)
                .append("://").append(uri.getRawAuthority());
        for (int i = 0; i < baseEnd; i++) {
            base.append("/").append(segments.get(i));
        }
        return new SpliitUrl(base.toString(), groupId);
    }
}
