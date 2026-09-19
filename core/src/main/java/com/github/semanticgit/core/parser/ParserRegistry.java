package com.github.semanticgit.core.parser;

import com.github.semanticgit.common.config.ConfigItem;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.parser.java.JavaParser;
import com.github.semanticgit.parser.java.api.LanguageParser;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class ParserRegistry {
    private static final Map<EntityLanguage, Function<Map<String, ConfigItem<?>>, LanguageParser>> parserMap = new EnumMap<>(EntityLanguage.class);
    private static final Map<String, ConfigItem<?>> configMap = new HashMap<>();

    static {
        registerParser(JavaParser::new, JavaParser::new);
    }

    public static void setConfig(String key, ConfigItem<?> config) {
        configMap.put(key, config);
    }

    public static void resetConfig(String key) {
        configMap.get(key).reset();
    }

    public static void resetConfigs() {
        configMap.forEach((_, v) -> v.reset());
    }

    public static ConfigItem<?> getConfig(String key) {
        return configMap.get(key);
    }

    public static @NonNull @UnmodifiableView Map<String, ConfigItem<?>> getConfigs() {
        return Collections.unmodifiableMap(configMap);
    }

    public static void registerParser(@NonNull Supplier<LanguageParser> parser, Function<Map<String, ConfigItem<?>>, LanguageParser> parserGenerator) {
        LanguageParser parserExample = parser.get();
        parserMap.put(parserExample.getSupportedLanguage(), parserGenerator);
        parserExample.registerConfigs(configMap);
    }

    public static LanguageParser getParserInstance(EntityLanguage language) {
        return parserMap.get(language).apply(configMap);
    }

    public static LanguageParser getParserInstance(EntityLanguage language, Map<String, ConfigItem<?>> configMap) {
        return parserMap.get(language).apply(configMap);
    }

    public static boolean hasParser(EntityLanguage language) {
        return parserMap.containsKey(language);
    }

    public static EntityLanguage detectLanguage(String filePath) {
        if (filePath == null) {
            return null;
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) {
            return EntityLanguage.JAVA;
        }
        if (lower.endsWith(".py")) {
            return EntityLanguage.PYTHON;
        }
        if (lower.endsWith(".js")) {
            return EntityLanguage.JS;
        }
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) {
            return EntityLanguage.TS;
        }
        return null;
    }
}
