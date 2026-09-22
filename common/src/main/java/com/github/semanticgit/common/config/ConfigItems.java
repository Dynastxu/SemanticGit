package com.github.semanticgit.common.config;

public final class ConfigItems {
    private static final RangeConfigItem.RangeConfigItemBuilder<Integer> INT = RangeConfigItem.<Integer>builder();
    private static final RangeConfigItem.RangeConfigItemBuilder<Long> LONG = RangeConfigItem.<Long>builder();
    private static final RangeConfigItem.RangeConfigItemBuilder<Double> DOUBLE = RangeConfigItem.<Double>builder();
    private static final RangeConfigItem.RangeConfigItemBuilder<Float> FLOAT = RangeConfigItem.<Float>builder();

    public static RangeConfigItem.RangeConfigItemBuilder<Integer> INT(int defaultValue) {
        return INT.defaultValue(defaultValue);
    }

    public static RangeConfigItem.RangeConfigItemBuilder<Long> LONG(long defaultValue) {
        return LONG.defaultValue(defaultValue);
    }

    public static RangeConfigItem.RangeConfigItemBuilder<Double> DOUBLE(double defaultValue) {
        return DOUBLE.defaultValue(defaultValue);
    }

    public static RangeConfigItem.RangeConfigItemBuilder<Float> FLOAT(float defaultValue) {
        return FLOAT.defaultValue(defaultValue);
    }
}
