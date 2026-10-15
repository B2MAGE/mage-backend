package com.bdmage.mage_backend.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.bdmage.mage_backend.exception.InvalidSceneDiscoveryRequestException;

public record SceneDiscoveryQuery(int page, int size, Sort sort, String tag) {
    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 48;

    public enum Sort { ASCENDING, DESCENDING, MOST_VIEWED, MOST_LIKED, FEATURED, RECOMMENDED }

    public static SceneDiscoveryQuery from(int page, int size, String sort, String tag) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (page < 0) errors.put("page", "Page must be zero or greater.");
        if (size < 1 || size > MAX_SIZE) errors.put("size", "Page size must be between 1 and 48.");
        Sort resolvedSort = switch (sort.trim()) {
            case "ascending" -> Sort.ASCENDING;
            case "descending" -> Sort.DESCENDING;
            case "most-viewed" -> Sort.MOST_VIEWED;
            case "most-liked" -> Sort.MOST_LIKED;
            case "featured" -> Sort.FEATURED;
            case "recommended" -> Sort.RECOMMENDED;
            default -> null;
        };
        if (resolvedSort == null) errors.put("sort", "Choose a supported scene sort.");
        String normalizedTag = tag == null ? null : tag.trim().toLowerCase(Locale.ROOT);
        if (normalizedTag != null && normalizedTag.isEmpty()) normalizedTag = null;
        if (normalizedTag != null && normalizedTag.length() > 64) errors.put("tag", "Tag must be at most 64 characters.");
        if (!errors.isEmpty()) throw new InvalidSceneDiscoveryRequestException(errors);
        return new SceneDiscoveryQuery(page, size, resolvedSort, normalizedTag);
    }

    public long offset() { return (long) this.page * this.size; }
}
