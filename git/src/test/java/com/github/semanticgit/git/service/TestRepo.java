package com.github.semanticgit.git.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestRepo implements AutoCloseable {

    private final Path root;
    private final Git git;

    private TestRepo(Path root, Git git) {
        this.root = root;
        this.git = git;
    }

    static TestRepo init(Path dir) throws GitAPIException {
        return new TestRepo(dir, Git.init().setDirectory(dir.toFile()).call());
    }

    String commitFile(String path, String content, String message)
            throws IOException, GitAPIException {
        writeFile(path, content);
        git.add().addFilepattern(path).call();
        return commit(message);
    }

    String commit(String message) throws GitAPIException {
        return git.commit()
                .setMessage(message)
                .setAuthor("Test User", "test@example.com")
                .call()
                .getName();
    }

    String commitAs(String name, String email, String message) throws GitAPIException {
        return git.commit()
                .setMessage(message)
                .setAuthor(name, email)
                .call()
                .getName();
    }

    void writeFile(String path, String content) throws IOException {
        Path file = root.resolve(path);
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    void writeBytes(String path, byte[] content) throws IOException {
        Path file = root.resolve(path);
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    void stage(String path) throws GitAPIException {
        git.add().addFilepattern(path).call();
    }

    void rm(String path) throws GitAPIException {
        git.rm().addFilepattern(path).call();
    }

    String head() throws IOException {
        return git.getRepository().resolve("HEAD").getName();
    }

    Path root() {
        return root;
    }

    Git raw() {
        return git;
    }

    org.eclipse.jgit.lib.Repository repository() {
        return git.getRepository();
    }

    @Override
    public void close() {
        git.close();
    }
}