package com.example.pstarchive.model;

public record ShardManifest(
        String pstId,
        String displayName,
        String originalPath,
        String currentPath,
        String status,
        String createdAt,
        String indexVersion,
        String storeVersion
) {
}
