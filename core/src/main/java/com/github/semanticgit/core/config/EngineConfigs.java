package com.github.semanticgit.core.config;

import com.github.semanticgit.common.config.ConfigItem;
import com.github.semanticgit.common.config.ConfigItems;

import java.util.HashMap;
import java.util.Map;

public class EngineConfigs {
    public static final String CONFIG_KEY_MAX_DEPTH = "max_depth";
    private static final Map<String, ConfigItem<?>> configs = new HashMap<>();

    static {
        configs.put(CONFIG_KEY_MAX_DEPTH, ConfigItems.INT(1000).build());
    }

    public static void set(String key, ConfigItem<?> value) {
        configs.put(key, value);
    }

    public static ConfigItem<?> get(String key) {
        return configs.get(key);
    }

    public static void setMaxDepth(int depth) {
        set(CONFIG_KEY_MAX_DEPTH, ConfigItems.INT(depth).build());
    }

    public static int getMaxDepth() {
        return (int) get(CONFIG_KEY_MAX_DEPTH).getValue();
    }
}
