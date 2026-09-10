package com.github.semanticgit.core;

import com.github.semanticgit.common.entity.ChangeNatureFlag;
import com.github.semanticgit.common.entity.ChangeOperation;
import com.github.semanticgit.core.dao.ChangeLogDao;
import com.github.semanticgit.core.dao.impl.ChangeLogDaoImpl;
import com.github.semanticgit.core.db.DatabaseManager;
import com.github.semanticgit.core.dto.SimpleEntityChangeStatistics;
import lombok.extern.slf4j.Slf4j;

import java.sql.ResultSet;
import java.sql.SQLException;
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
        try {
            Map<ChangeOperation, Float> operationFloatMap = new EnumMap<>(ChangeOperation.class);
            Map<ChangeNatureFlag, Float> natureFlagFloatMap = new EnumMap<>(ChangeNatureFlag.class);

            ChangeLogDao changeLogDao = new ChangeLogDaoImpl(dbManager);
            ResultSet rs = changeLogDao.querySimpleEntityChangeStatistics(dbManager.getConnection());
            while (rs.next()) {
                int code = rs.getInt("value");
                float opRatio = rs.getFloat("operation_ratio");
                float nfRatio = rs.getFloat("nature_flag_ratio");

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

            return SimpleEntityChangeStatistics.builder()
                    .operationFloatMap(operationFloatMap)
                    .natureFlagFloatMap(natureFlagFloatMap)
                    .build();

        } catch (SQLException e) {
            log.error("Failed to get simple entity change statistics", e);
            return SimpleEntityChangeStatistics.fail();
        }
    }
}
