package com.example.pstarchive.shard;

import com.example.pstarchive.util.AppPaths;

import java.nio.file.Path;

public class ShardPathResolver {
    private final Path dataDir;

    public ShardPathResolver(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Path shardDirectory(String pstId) {
        return AppPaths.shardsDir(dataDir).resolve(pstId);
    }

    public Path storeDatabase(String pstId) {
        return shardDirectory(pstId).resolve("store.sqlite");
    }

    public Path luceneDirectory(String pstId) {
        return shardDirectory(pstId).resolve("lucene");
    }

    public Path errorsDatabase(String pstId) {
        return shardDirectory(pstId).resolve("errors.sqlite");
    }

    public Path manifestPath(String pstId) {
        return shardDirectory(pstId).resolve("manifest.json");
    }
}
