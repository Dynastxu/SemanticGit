package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.dao.CommitMetaDao;
import com.github.semanticgit.core.dao.impl.ChangeLogDaoImpl;
import com.github.semanticgit.core.dao.impl.CommitMetaDaoImpl;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

@Slf4j
public class StatisticsProvider {
    private final DatabaseManager dbManager;

    public StatisticsProvider(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public SimpleEntityChangeStatistics getSimpleEntityChangeStatistics() {
        ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);

        int totalCommits = countCommits();
        log.info("Total commits from commit_meta: {}", totalCommits);

        try(ResultSet rs = changeLogDao.querySimpleEntityChangeStatistics()) {
            Map<ChangeOperation, Float> operationFloatMap = new EnumMap<>(ChangeOperation.class);
            Map<ChangeNatureFlag, Float> natureFlagFloatMap = new EnumMap<>(ChangeNatureFlag.class);

            int rowCount = 0;
            while (rs.next()) {
                rowCount++;
                int code = rs.getInt("value");
                float opRatio = rs.getFloat("operation_ratio");
                float nfRatio = rs.getFloat("nature_flag_ratio");

                log.info("Row {}: code={}, opRatio={}, nfRatio={}", rowCount, code, opRatio, nfRatio);

                if (opRatio > 0 && ChangeOperation.hasCode(code)) {
                    ChangeOperation op = ChangeOperation.fromCode(code);
                    operationFloatMap.put(op, opRatio);
                }

                if (nfRatio > 0) {
                    EnumSet<ChangeNatureFlag> nf = ChangeNatureFlag.fromCode(code);
                    for (ChangeNatureFlag flag : nf) {
                        natureFlagFloatMap.merge(flag, nfRatio, Float::sum);
                    }
                }
            }

            log.info("Query returned {} rows", rowCount);
            log.info("operationFloatMap: {}", operationFloatMap);
            log.info("natureFlagFloatMap: {}", natureFlagFloatMap);

            return SimpleEntityChangeStatistics.builder()
                    .totalCommits(totalCommits)
                    .operationFloatMap(operationFloatMap)
                    .natureFlagFloatMap(natureFlagFloatMap)
                    .build();

        } catch (SQLException e) {
            log.error("Failed to get simple entity change statistics", e);
            return SimpleEntityChangeStatistics.fail();
        }
    }

    private int countCommits() {
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM commit_meta")) {
            if (rs.next()) {
                int count = rs.getInt(1);
                log.info("countCommits result: {}", count);
                return count;
            }
        } catch (SQLException e) {
            log.error("Failed to count commits", e);
        }
        return 0;
    }
}
