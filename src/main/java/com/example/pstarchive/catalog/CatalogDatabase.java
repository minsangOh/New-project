package com.example.pstarchive.catalog;

import com.example.pstarchive.util.AppPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class CatalogDatabase {
    private final Path dataDir;
    private final Path databasePath;

    public CatalogDatabase(Path dataDir) {
        this.dataDir = dataDir;
        this.databasePath = AppPaths.catalogDatabase(dataDir);
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path databasePath() {
        return databasePath;
    }

    public void initialize() throws IOException, SQLException {
        AppPaths.ensureBaseDirectories(dataDir);
        try (Connection connection = openConnection()) {
            CatalogSchema.migrate(connection);
        }
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
    }
}
