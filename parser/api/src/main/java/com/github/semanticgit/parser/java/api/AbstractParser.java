package com.github.semanticgit.parser.java.api;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor(force = true)
public abstract class AbstractParser<C extends ParserConfig> implements LanguageParser<C> {
    @Getter
    @Setter
    protected C config;

    protected AbstractParser(C config) {
        this.config = config;
    }
}
