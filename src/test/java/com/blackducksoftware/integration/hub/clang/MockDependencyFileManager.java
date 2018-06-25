package com.blackducksoftware.integration.hub.clang;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class MockDependencyFileManager implements DependencyFileManager {

    @Override
    public List<String> parse(final Optional<File> depsMkFile) {
        return new SimpleDependencyFileManager().parse(Optional.of(new File("src/test/resources/buildDir/deps.mk")));
    }

    @Override
    public void remove(final Optional<File> depsMkFile) {
    }
}
