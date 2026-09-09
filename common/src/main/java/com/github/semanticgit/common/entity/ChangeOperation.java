package com.github.semanticgit.common.entity;

import org.jspecify.annotations.NonNull;

public enum ChangeOperation {
    ADD(0, "add"),
    REMOVE(1, "remove"),
    MODIFY(2, "modify"),
    ;

    public final int code;
    public final String desc;

    ChangeOperation(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static @NonNull ChangeOperation fromCode(int code) throws IllegalArgumentException {
        for (ChangeOperation op : values()) if (op.code == code) return op;
        throw new IllegalArgumentException("Invalid operation code: " + code);
    }
}
