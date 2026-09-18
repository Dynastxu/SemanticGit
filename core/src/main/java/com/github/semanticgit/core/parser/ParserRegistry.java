package com.github.semanticgit.core.parser;

import com.github.semanticgit.common.config.ConfigItem;
import com.github.semanticgit.common.entity.EntityLanguage;
import com.github.semanticgit.parser.java.RawJavaParser;
import com.github.semanticgit.parser.java.api.LanguageParser;
import com.github.semanticgit.parser.java.api.ParserConfig;
import com.github.semanticgit.parser.java.api.RawLanguageParser;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class ParserRegistry {
    @Deprecated
    private static final Map<EntityLanguage, Supplier<LanguageParser<?>>> parsers = new EnumMap<>(EntityLanguage.class);
    @Deprecated
    private static final Map<EntityLanguage, Supplier<? extends ParserConfig>> configs = new EnumMap<>(EntityLanguage.class);
    private static final Map<EntityLanguage, Function<Map<String, ConfigItem<?>>, RawLanguageParser>> parserMap = new EnumMap<>(EntityLanguage.class);
    private static final Map<String, ConfigItem<?>> configMap = new HashMap<>();

    static {
        registerParser(RawJavaParser::new, RawJavaParser::new);
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

    @Deprecated
    public static void registerParser(EntityLanguage language, Supplier<LanguageParser<? extends ParserConfig>> parser, Supplier<? extends ParserConfig> defaultConfig) {
        parsers.put(language, parser);
        configs.put(language, defaultConfig);
    }

    public static void registerParser(@NonNull Supplier<RawLanguageParser> parser, Function<Map<String, ConfigItem<?>>, RawLanguageParser> parserGenerator) {
        RawLanguageParser parserExample = parser.get();
        parserMap.put(parserExample.getSupportedLanguage(), parserGenerator);
        parserExample.registerConfigs(configMap);
    }

    /**
     * @deprecated Use {@link #getParserInstance(EntityLanguage)} instead.
     */
    @Deprecated
    public static @NonNull LanguageParser<?> getParser(EntityLanguage language) {
        LanguageParser parser = parsers.get(language).get();
        parser.setConfig(configs.get(language).get());
        return parser;
    }

    public static RawLanguageParser getParserInstance(EntityLanguage language) {
        return parserMap.get(language).apply(configMap);
    }

    public static RawLanguageParser getParserInstance(EntityLanguage language, Map<String, ConfigItem<?>> configMap) {
        return parserMap.get(language).apply(configMap);
    }

    public static boolean hasParser(EntityLanguage language) {
        return parserMap.containsKey(language);
    }

    /**
     * @deprecated Use {@link #getParserInstance(EntityLanguage, Map)} instead.
     */
    @Deprecated
    public static <C extends ParserConfig> @NonNull LanguageParser<C> getParser(EntityLanguage language, C config) {
        LanguageParser parser = getParser(language);
        parser.setConfig(config);
        return parser;
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
