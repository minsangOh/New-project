package com.example.pstarchive.pst;

public record ExtractedFolder(
        Long id,
        Long parentId,
        String folderPath,
        String displayName,
        Long descriptorNodeId,
        Integer itemCount,
        Integer subfolderCount
) {
}
