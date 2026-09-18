package com.github.semanticgit.common.entity;

import org.jspecify.annotations.NonNull;

public enum ReferenceType {
    BRANCH(1),
    TAG(2)
    ;

    public final int code;

    ReferenceType(int code) {
        this.code = code;
    }

    public static @NonNull ReferenceType fromCode(int code) throws IllegalArgumentException {
        for (ReferenceType t : values()) if (t.code == code) return t;
        throw new IllegalArgumentException("Invalid reference type code: " + code);
    }
}
