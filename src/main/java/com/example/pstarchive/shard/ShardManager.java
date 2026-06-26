package com.example.pstarchive.shard;

import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.model.ShardManifest;
import com.example.pstarchive.util.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;

public class ShardManager {
    private final ShardPathResolver pathResolver;

    public ShardManager(ShardPathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    public void createShard(PstFileRecord record) throws IOException {
        Files.createDirectories(pathResolver.shardDirectory(record.pstId()));
        Files.createDirectories(pathResolver.luceneDirectory(record.pstId()));
        if (!Files.exists(pathResolver.storeDatabase(record.pstId()))) {
            Files.createFile(pathResolver.storeDatabase(record.pstId()));
        }
        if (!Files.exists(pathResolver.errorsDatabase(record.pstId()))) {
            Files.createFile(pathResolver.errorsDatabase(record.pstId()));
        }
        writeManifest(record);
    }

    public void writeManifest(PstFileRecord record) throws IOException {
        Files.createDirectories(pathResolver.shardDirectory(record.pstId()));
        ShardManifest manifest = new ShardManifest(
                record.pstId(),
                record.displayName(),
                record.originalPath(),
                record.currentPath(),
                record.status().value(),
                record.createdAt(),
                record.indexVersion(),
                "phase1"
        );
        JsonUtils.write(pathResolver.manifestPath(record.pstId()), manifest);
    }

    public boolean manifestExists(String pstId) {
        return Files.exists(pathResolver.manifestPath(pstId));
    }
}
