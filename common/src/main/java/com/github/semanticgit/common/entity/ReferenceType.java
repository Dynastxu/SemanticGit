package com.github.semanticgit.common.entity;

public enum ReferenceType {
    BRANCH(1),
    TAG(2)
    ;

    public final int code;

    ReferenceType(int code) {
        this.code = code;
    }
}
