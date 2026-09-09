package com.github.semanticgit.core.dao.impl;

import com.github.semanticgit.common.entity.AnalysisType;
import com.github.semanticgit.common.entity.Author;
import com.github.semanticgit.common.entity.ChangeLog;
import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.common.entity.CommitMeta;
import com.github.semanticgit.common.entity.DataQuality;
import com.github.semanticgit.common.entity.Entity;
import com.github.semanticgit.common.entity.EntityKind;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChangeLogDaoImplTest {

    private Path tempDbDir;
    private DatabaseManager dbManager;
    private ChangeLogDao changeLogDao;
    private CommitMetaDao commitMetaDao;
    private CommitMeta testCommit;

    @BeforeEach
    void setUp() throws Exception {
        tempDbDir = Files.createTempDirectory("changelog-dao-test");
        dbManager = new DatabaseManager(tempDbDir.toString(), "test", true);
        changeLogDao = new ChangeLogDaoImpl(dbManager);
        commitMetaDao = new CommitMetaDaoImpl(dbManager);

        testCommit = CommitMeta.builder()
                .hash("abc123def456")
                .author(Author.builder().name("Test").email("test@test.com").build())
                .timestamp(1234567890)
                .message("Test commit")
                .build();
        commitMetaDao.save(testCommit);
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
        if (tempDbDir != null) {
            try (var files = Files.walk(tempDbDir)) {
                files.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("save: 单条 ChangeLog 应正确写入数据库")
    void save_SingleChangeLog_WritesToDatabase() throws Exception {
        ChangeLog changeLog = ChangeLog.builder()
                .commit(testCommit)
                .entity(Entity.builder()
                        .name("com.example.Test#method")
                        .language(EntityLanguage.JAVA)
                        .kind(EntityKind.METHOD)
                        .build())
                .filePath("src/main/java/com/example/Test.java")
                .operation(ChangeOperation.ADD)
                .natureFlag(ChangeNatureFlag.FEAT)
                .dataQuality(DataQuality.AST)
                .analysisType(AnalysisType.INCREMENTAL)
                .build();

        changeLogDao.save(changeLog);

        String dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM change_log")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "应有一条 change_log 记录");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM entity")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "应有一条 entity 记录");
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT cl.file_path, cl.operation, cl.nature_flag, cl.data_quality, cl.analysis_type, " +
                            "e.name, e.language, e.kind " +
                            "FROM change_log cl JOIN entity e ON cl.entity_id = e.id")) {
                assertTrue(rs.next());
                assertEquals("src/main/java/com/example/Test.java", rs.getString("file_path"));
                assertEquals(ChangeOperation.ADD.code, rs.getInt("operation"));
                assertEquals(ChangeNatureFlag.FEAT.code, rs.getInt("nature_flag"));
                assertEquals(DataQuality.AST.code, rs.getInt("data_quality"));
                assertEquals(AnalysisType.INCREMENTAL.code, rs.getInt("analysis_type"));
                assertEquals("com.example.Test#method", rs.getString("name"));
                assertEquals(EntityLanguage.JAVA.code, rs.getInt("language"));
                assertEquals(EntityKind.METHOD.code, rs.getInt("kind"));
            }
        }
    }

    @Test
    @DisplayName("save: 同一实体多次保存不重复创建 entity")
    void save_SameEntityTwice_NoDuplicateEntity() throws Exception {
        Entity entity = Entity.builder()
                .name("com.example.Duplicate#foo")
                .language(EntityLanguage.JAVA)
                .kind(EntityKind.METHOD)
                .build();

        ChangeLog log1 = ChangeLog.builder()
                .commit(testCommit)
                .entity(entity)
                .filePath("Test.java")
                .operation(ChangeOperation.ADD)
                .natureFlag(ChangeNatureFlag.FEAT)
                .dataQuality(DataQuality.AST)
                .analysisType(AnalysisType.INCREMENTAL)
                .build();

        ChangeLog log2 = ChangeLog.builder()
                .commit(testCommit)
                .entity(entity)
                .filePath("Test.java")
                .operation(ChangeOperation.MODIFY)
                .natureFlag(ChangeNatureFlag.FEAT)
                .dataQuality(DataQuality.AST)
                .analysisType(AnalysisType.INCREMENTAL)
                .build();

        changeLogDao.save(log1);
        changeLogDao.save(log2);

        String dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM entity")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "实体不应重复");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM change_log")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "应有两条 change_log");
            }
        }
    }

    @Test
    @DisplayName("saveAll: 批量写入多条 ChangeLog")
    void saveAll_MultipleChangeLogs() throws Exception {
        List<ChangeLog> logs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            logs.add(ChangeLog.builder()
                    .commit(testCommit)
                    .entity(Entity.builder()
                            .name("com.example.Entity" + i + "#method")
                            .language(EntityLanguage.JAVA)
                            .kind(EntityKind.METHOD)
                            .build())
                    .filePath("File" + i + ".java")
                    .operation(ChangeOperation.ADD)
                    .natureFlag(ChangeNatureFlag.FEAT)
                    .dataQuality(DataQuality.AST)
                    .analysisType(AnalysisType.INCREMENTAL)
                    .build());
        }

        changeLogDao.saveAll(logs);

        String dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM change_log")) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM entity")) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("saveAll: 空列表不抛异常")
    void saveAll_EmptyList_NoException() {
        assertDoesNotThrow(() -> changeLogDao.saveAll(List.of()));
    }

    @Test
    @DisplayName("save: 包含 parentEntity 的 ChangeLog")
    void save_WithParentEntity() throws Exception {
        Entity parentEntity = Entity.builder()
                .name("com.example.OldName#oldMethod")
                .language(EntityLanguage.JAVA)
                .kind(EntityKind.METHOD)
                .build();

        ChangeLog changeLog = ChangeLog.builder()
                .commit(testCommit)
                .entity(Entity.builder()
                        .name("com.example.NewName#newMethod")
                        .language(EntityLanguage.JAVA)
                        .kind(EntityKind.METHOD)
                        .build())
                .filePath("Test.java")
                .operation(ChangeOperation.MODIFY)
                .natureFlag(ChangeNatureFlag.REFACTOR)
                .parentEntity(parentEntity)
                .dataQuality(DataQuality.AST)
                .analysisType(AnalysisType.INCREMENTAL)
                .build();

        changeLogDao.save(changeLog);

        String dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM entity")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "应有两条 entity（新实体 + 父实体）");
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT parent_entity_id FROM change_log WHERE parent_entity_id IS NOT NULL")) {
                assertTrue(rs.next(), "应有 parent_entity_id");
                assertTrue(rs.getLong("parent_entity_id") > 0);
            }
        }
    }

    @Test
    @DisplayName("save: 不存在的提交 hash 应被吞掉异常（log error）")
    void save_NonExistentCommitHash_NoException() {
        CommitMeta unknownCommit = CommitMeta.builder()
                .hash("nonexistent")
                .author(Author.builder().name("X").email("x@x.com").build())
                .timestamp(0)
                .message("nonexistent")
                .build();

        ChangeLog changeLog = ChangeLog.builder()
                .commit(unknownCommit)
                .entity(Entity.builder()
                        .name("test")
                        .language(EntityLanguage.JAVA)
                        .kind(EntityKind.CLASS)
                        .build())
                .filePath("Test.java")
                .operation(ChangeOperation.ADD)
                .natureFlag(ChangeNatureFlag.FEAT)
                .dataQuality(DataQuality.FILE)
                .analysisType(AnalysisType.INCREMENTAL)
                .build();

        assertDoesNotThrow(() -> changeLogDao.save(changeLog),
                "不存在的提交不应抛出未捕获异常");
    }

    @Test
    @DisplayName("save: 不同操作类型的 ChangeLog")
    void save_DifferentOperations() throws Exception {
        for (ChangeOperation op : ChangeOperation.values()) {
            ChangeLog changeLog = ChangeLog.builder()
                    .commit(testCommit)
                    .entity(Entity.builder()
                            .name("com.example.OpTest#" + op.name())
                            .language(EntityLanguage.JAVA)
                            .kind(EntityKind.METHOD)
                            .build())
                    .filePath("Test.java")
                    .operation(op)
                    .natureFlag(ChangeNatureFlag.FEAT)
                    .dataQuality(DataQuality.AST)
                    .analysisType(AnalysisType.INCREMENTAL)
                    .build();

            changeLogDao.save(changeLog);
        }

        String dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM change_log")) {
                assertTrue(rs.next());
                assertEquals(ChangeOperation.values().length, rs.getInt(1));
            }
        }
    }

    private String getDbPath() {
        return new File(tempDbDir.toFile(), "test.db").getAbsolutePath().replace("\\", "/");
    }
}
