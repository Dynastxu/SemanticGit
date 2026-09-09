package com.github.semanticgit.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Author {
    private Long id;
    private String name;
    private String email;

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Author author) {
            return Objects.equals(author.name, this.name) && Objects.equals(author.email, this.email);
        }
        return false;
    }
}
