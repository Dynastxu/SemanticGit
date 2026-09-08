package com.github.semanticgit.common.entity;

import org.jspecify.annotations.NonNull;

public enum AnalysisType {
    INCREMENTAL(0, "逐提交增量"),
    COMPARATIVE(1, "对比分析")
    ;

    public final int code;
    public final String desc;

    AnalysisType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static @NonNull AnalysisType fromCode(int code) throws IllegalArgumentException {
        for (AnalysisType at : values()) if (at.code == code) return at;
        throw new IllegalArgumentException("Invalid analysis type code: " + code);
    }
}
