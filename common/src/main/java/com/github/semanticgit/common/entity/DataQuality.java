package com.github.semanticgit.common.entity;

import org.jspecify.annotations.NonNull;

public enum DataQuality {
    AST(0, "AST"),
    REGEX(1, "REGEX"),
    FILE(2, "FILE");

    public final int code;
    public final String desc;

    DataQuality(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static @NonNull DataQuality fromCode(int code) throws IllegalArgumentException {
        for (DataQuality dq : values()) if (dq.code == code) return dq;
        throw new IllegalArgumentException("Invalid data quality code: " + code);
    }
}
