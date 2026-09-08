package com.github.semanticgit.common.entity;

import org.jspecify.annotations.NonNull;

public enum EntityKind {
    CLASS(1, "类/接口/结构体"),
    METHOD(2, "方法/函数"),
    // 可扩展: ENUM(3), FIELD(4), MODULE(5)
    ;

    public final int code;
    public final String desc;

    EntityKind(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static @NonNull EntityKind fromCode(int code) throws IllegalArgumentException {
        for (EntityKind k : values()) if (k.code == code) return k;
        throw new IllegalArgumentException("Invalid kind code: " + code);
    }
}
