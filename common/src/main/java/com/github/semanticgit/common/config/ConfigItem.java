package com.github.semanticgit.common.config;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public class ConfigItem<T> {
    @Nullable
    private final Predicate<T> validator;
    @Getter
    private final T defaultValue;
    @Getter
    private volatile T value;

    public ConfigItem(T defaultValue, @Nullable Predicate<T> validator) {
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.validator = validator;
        if (!isValid(defaultValue)) {
            throw new IllegalArgumentException("Invalid value for config");
        }
    }

    public boolean isValid(T value) {
        return validator == null || validator.test(value);
    }

    public void setValue(T value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid value for config");
        }
        this.value = value;
    }

    public void reset() {
        value = defaultValue;
    }
}
