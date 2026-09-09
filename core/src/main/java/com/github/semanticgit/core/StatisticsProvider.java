package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public class StatisticsProvider {
    private final DatabaseManager dbManager;

    public StatisticsProvider(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public SimpleEntityChangeStatistics getSimpleEntityChangeStatistics() {
        try {
            Connection conn = dbManager.getConnection();
            return queryStatistics(conn);
        } catch (SQLException e) {
            log.error("Failed to get simple entity change statistics", e);
            return SimpleEntityChangeStatistics.fail();
        }
    }

    private SimpleEntityChangeStatistics queryStatistics(Connection conn) throws SQLException {
        int totalChanges = 0;
        int adds = 0;
        int removes = 0;
        int modifies = 0;
        int logical = 0;
        int refactors = 0;
        int styles = 0;
        int docs = 0;

        String sql = "SELECT operation, nature_flag, COUNT(*) as cnt FROM change_log GROUP BY operation, nature_flag";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int operation = rs.getInt("operation");
                int natureFlag = rs.getInt("nature_flag");
                int cnt = rs.getInt("cnt");

                totalChanges += cnt;

                switch (operation) {
                    case 0 -> adds += cnt;
                    case 1 -> removes += cnt;
                    case 2 -> modifies += cnt;
                }

                if ((natureFlag & ChangeNatureFlag.FEAT.code) != 0
                        || (natureFlag & ChangeNatureFlag.FIX.code) != 0) {
                    logical += cnt;
                }
                if ((natureFlag & ChangeNatureFlag.REFACTOR.code) != 0) {
                    refactors += cnt;
                }
                if ((natureFlag & ChangeNatureFlag.STYLE.code) != 0) {
                    styles += cnt;
                }
                if ((natureFlag & ChangeNatureFlag.DOCS.code) != 0) {
                    docs += cnt;
                }
            }
        }

        return SimpleEntityChangeStatistics.builder()
                .totalChanges(totalChanges)
                .adds(adds)
                .removes(removes)
                .modifies(modifies)
                .logical(logical)
                .refactors(refactors)
                .styles(styles)
                .docs(docs)
                .build();
    }
}
