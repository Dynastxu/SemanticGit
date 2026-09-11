package com.github.semanticgit.core.db;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public class DatabaseManager implements AutoCloseable {
    private final String dbUrl;
    private Connection connection;

    /**
     * @param databasePath 数据库文件存放目录
     * @param databaseName     仓库名，用作 db 文件名（如 "myproject" -> "myproject.db"）
     * @param overwrite    是否覆盖已存在的数据库文件
     */
    public DatabaseManager(String databasePath, String databaseName, boolean overwrite) {
        File dir = new File(databasePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File databaseFile = new File(dir, databaseName + ".db");
        this(databaseFile, overwrite);
    }

    public DatabaseManager(File databaseFile, boolean overwrite) {

        if (overwrite && databaseFile.exists()) {
            boolean deleted = databaseFile.delete();
            log.info("Existing database {}: {}", deleted ? "deleted" : "failed to delete", databaseFile.getAbsolutePath());
        }

        this.dbUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath().replace("\\", "/");
        log.info("Database path: {}", dbUrl);
    }

    public DatabaseManager(File databaseFile) {
        this(databaseFile, false);
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(dbUrl);
            initTables();
        }
        return connection;
    }

    private void initTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS author (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL,
                            email TEXT NOT NULL,
                            UNIQUE(name, email)
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS commit_meta (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            hash BLOB NOT NULL UNIQUE,
                            author_id INTEGER NOT NULL,
                            timestamp INTEGER NOT NULL,
                            message TEXT,
                            FOREIGN KEY (author_id) REFERENCES author(id)
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS entity (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL,
                            language INTEGER NOT NULL,
                            kind INTEGER NOT NULL,
                            UNIQUE(name, language, kind)
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS change_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            commit_id INTEGER NOT NULL,
                            entity_id INTEGER NOT NULL,
                            file_path TEXT NOT NULL,
                            operation INTEGER NOT NULL,
                            nature_flag INTEGER DEFAULT 0,
                            parent_entity_id INTEGER,
                            data_quality INTEGER NOT NULL,
                            analysis_type INTEGER NOT NULL,
                            FOREIGN KEY (commit_id) REFERENCES commit_meta(id),
                            FOREIGN KEY (entity_id) REFERENCES entity(id),
                            FOREIGN KEY (parent_entity_id) REFERENCES entity(id)
                        )
                    """);
            log.info("Database tables initialized successfully");
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("Database connection closed");
            } catch (SQLException e) {
                log.error("Failed to close database connection", e);
            }
        }
    }
}
