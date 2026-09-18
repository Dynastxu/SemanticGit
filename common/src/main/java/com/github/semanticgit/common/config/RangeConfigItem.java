package com.github.semanticgit.common.config;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

public class RangeConfigItem<C extends Comparable<C>> extends ConfigItem<C> {
    @Nullable
    public final C min;
    @Nullable
    public final C max;

    @Builder
    public RangeConfigItem(C defaultValue, @Nullable C min, @Nullable C max) {
        super(defaultValue, v -> {
            if (min != null && v.compareTo(min) < 0) {
                return false;
            }
            if (max != null && v.compareTo(max) > 0) {
                return false;
            }
            return true;
        });
        this.min = min;
        this.max = max;
    }
}
