package com.github.semanticgit.common.entity;

import org.jspecify.annotations.NonNull;

import java.util.EnumSet;

public enum ChangeNatureFlag {
    LOGICAL(0),
    REFACTOR(1),
    STYLE(2),
    DOC(3)
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

    public static int toCode(@NonNull EnumSet<ChangeNatureFlag> flags) {
        int result = 0;
        for (ChangeNatureFlag flag : flags) {
            result |= flag.code;
        }
        return result;
    }
}
