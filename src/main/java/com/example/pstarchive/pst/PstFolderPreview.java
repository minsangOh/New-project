package com.example.pstarchive.pst;

public record PstFolderPreview(
        String folderPath,
        String name,
        String itemCount,
        String subFolderCount
) {
}
