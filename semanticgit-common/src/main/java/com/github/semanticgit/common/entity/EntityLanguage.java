package com.github.semanticgit.common.entity;

import org.jspecify.annotations.NonNull;

public enum EntityLanguage {
    JAVA(1, "java"),
    PYTHON(2, "python"),
    JS(3, "js"),
    TS(4, "ts"),
    ;

    public final int code;
    public final String desc;

    EntityLanguage(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static @NonNull EntityLanguage fromCode(int code) throws IllegalArgumentException {
        for (EntityLanguage l : values()) if (l.code == code) return l;
        throw new IllegalArgumentException("Invalid language code: " + code);
    }
}
