package com.github.semanticgit.common.entity;

import org.jspecify.annotations.NonNull;

import java.util.EnumSet;

public enum ChangeNatureFlag {
    FEAT(0),
    FIX(1),
    REFACTOR(2),
    PERF(3),
    STYLE(4),
    TEST(5),
    DOCS(6)
    ;

    public final int code;

    ChangeNatureFlag(int index) {
        this.code = 1 << index;
    }

    public static @NonNull EnumSet<ChangeNatureFlag> fromCode(int code) {
        EnumSet<ChangeNatureFlag> result = EnumSet.noneOf(ChangeNatureFlag.class);
        for (ChangeNatureFlag flag : values()) {
            if ((code & flag.code) != 0) {
                result.add(flag);
            }
        }
        return result;
    }

    public static int codeOf(@NonNull EnumSet<ChangeNatureFlag> flags) {
        int result = 0;
        for (ChangeNatureFlag flag : flags) {
            result |= flag.code;
        }
        return result;
    }
}
