package com.github.semanticgit.core.config;

import com.github.semanticgit.common.config.ConfigItem;
import com.github.semanticgit.common.config.ConfigItems;

import java.util.HashMap;
import java.util.Map;

public class EngineConfigs {
    public static final String CONFIG_KEY_MAX_DEPTH = "max_depth";
    public static final String CONFIG_KEY_MAX_QUEUE = "max_queue";
    public static final String CONFIG_KEY_SIGNATURE_MATCH_THRESHOLD = "signature_match_threshold";
    private static final Map<String, ConfigItem<?>> configs = new HashMap<>();

    static {
        configs.put(CONFIG_KEY_MAX_DEPTH, ConfigItems.INT(1000).build());
        configs.put(CONFIG_KEY_MAX_QUEUE, ConfigItems.INT(64).build());
        configs.put(CONFIG_KEY_SIGNATURE_MATCH_THRESHOLD, ConfigItems.FLOAT(0.7f).min(0f).max(1f).build());
    }

    @SuppressWarnings("unchecked")
    public static <T> void set(String key, T value) {
        ((ConfigItem<T>) configs.get(key)).setValue(value);
    }

    public static ConfigItem<?> get(String key) {
        return configs.get(key);
    }

    public static void setMaxDepth(int depth) {
        set(CONFIG_KEY_MAX_DEPTH, depth);
    }

    public static int getMaxDepth() {
        return (int) get(CONFIG_KEY_MAX_DEPTH).getValue();
    }

    public static void setMaxQueue(int size) {
        set(CONFIG_KEY_MAX_QUEUE, size);
    }

    public static int getMaxQueue() {
        return (int) get(CONFIG_KEY_MAX_QUEUE).getValue();
    }

    public static void setSignatureMatchThreshold(float threshold) {
        set(CONFIG_KEY_SIGNATURE_MATCH_THRESHOLD, threshold);
    }

    public static float getSignatureMatchThreshold() {
        return (float) get(CONFIG_KEY_SIGNATURE_MATCH_THRESHOLD).getValue();
    }
}
