package net.llego.ihatemoney.model;

import java.util.HashMap;
import java.util.Map;

public enum ProjectType {
    LOCAL("l"), IHATEMONEY("i");

    private static Map<String, ProjectType> reverseMap = new HashMap<>();

    static {
        reverseMap.put(LOCAL.id, LOCAL);
        reverseMap.put(IHATEMONEY.id, IHATEMONEY);
    }

    private String id;

    ProjectType(String id) {
        this.id = id;
    }

    public static ProjectType getTypeById(String id) {
        return reverseMap.get(id);
    }

    /**
     * Resolve a stored type id, falling back to LOCAL for ids that are no longer supported
     * (e.g. legacy Cospend projects whose type is no longer mapped).
     */
    public static ProjectType fromStored(String id) {
        ProjectType type = getTypeById(id);
        return type == null ? LOCAL : type;
    }

    public String getId() {
        return id;
    }
}
