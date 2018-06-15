/**
 * sb-clang-extractor
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.hub.clang;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.bdio.SimpleBdioFactory;
import com.blackducksoftware.integration.hub.bdio.graph.MutableDependencyGraph;
import com.blackducksoftware.integration.hub.bdio.model.Forge;
import com.blackducksoftware.integration.hub.bdio.model.dependency.Dependency;
import com.blackducksoftware.integration.hub.bdio.model.externalid.ExternalId;
import com.blackducksoftware.integration.hub.clang.execute.SimpleExecutor;
import com.blackducksoftware.integration.hub.clang.execute.fromdetect.ExecutableRunnerException;
import com.blackducksoftware.integration.hub.imageinspector.lib.OperatingSystemEnum;
import com.google.gson.Gson;

@Component
public class ClangExtractor {
    private static final String DEPS_MK_PATH = "deps.mk";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // DPKG
    final List<Forge> forges = Arrays.asList(OperatingSystemEnum.UBUNTU.getForge(), OperatingSystemEnum.DEBIAN.getForge());

    public void extract(final String buildDirPath) throws IOException, ExecutableRunnerException, IntegrationException {
        logger.debug(String.format("extract() called; buildDirPath: %s", buildDirPath));
        final MutableDependencyGraph dependencyGraph = new SimpleBdioFactory().createMutableDependencyGraph();
        final File buildDir = new File(buildDirPath);
        final File compileCommandsJsonFile = new File(buildDir, "compile_commands.json");
        final String compileCommandsJson = FileUtils.readFileToString(compileCommandsJsonFile, StandardCharsets.UTF_8);
        final Gson gson = new Gson();
        final CompileCommand[] compileCommands = gson.fromJson(compileCommandsJson, CompileCommand[].class);
        for (final CompileCommand compileCommand : compileCommands) {
            logger.debug(String.format("compileCommand:\n\tdirectory: %s;\n\tcommand: %s;\n\tfile: %s", compileCommand.directory, compileCommand.command, compileCommand.file));
            processCompileCommand(dependencyGraph, compileCommand);
        }
    }

    private void processCompileCommand(final MutableDependencyGraph dependencyGraph, final CompileCommand compileCommand) throws ExecutableRunnerException, IOException, IntegrationException {
        final File depsMkFile = new File(DEPS_MK_PATH);
        final String generateDependenciesFileCommand = String.format("%s -M -MF %s", compileCommand.command, depsMkFile.getAbsolutePath());
        SimpleExecutor.execute(new File(compileCommand.directory), null, generateDependenciesFileCommand);

        final List<String> dependencyFilePaths = getDependencyFilePaths(depsMkFile);
        final List<File> dependencyFiles = getDependencyFiles(dependencyFilePaths);
        getPackages(dependencyGraph, dependencyFiles);
    }

    private void getPackages(final MutableDependencyGraph dependencyGraph, final List<File> dependencyFiles) {
        for (final File dependencyFile : dependencyFiles) {
            final Optional<String[]> packageNameArch = getPackageNameArch(dependencyFile);
            final Optional<String> packageName = getPackageName(packageNameArch);
            final Optional<String> packageArch = getPackageArch(packageNameArch);
            final Optional<String> packageVersion = getPackageVersion(packageName);
            logger.info(String.format("Package name//arch//version: %s//%s//%s", packageName.orElse("<missing>"), packageArch.orElse("<missing>"), packageVersion.orElse("<missing>")));
            if (packageName.isPresent() && packageArch.isPresent() && packageVersion.isPresent()) {
                createBdioComponent(dependencyGraph, packageName.get(), packageVersion.get(), packageArch.get());
            }
        }
    }

    private void createBdioComponent(final MutableDependencyGraph dependencies, final String name, final String version, final String arch) {
        final String externalId = String.format("%s/%s/%s", name, version, arch);
        logger.trace(String.format("Constructed externalId: %s", externalId));
        for (final Forge forge : forges) {
            final ExternalId extId = new SimpleBdioFactory().createArchitectureExternalId(forge, name, version, arch);
            final Dependency dep = new SimpleBdioFactory().createDependency(name, version, extId); // createDependencyNode(forge, name, version, arch);
            logger.info(String.format("*** forge: %s: adding %s version %s as child to dependency node tree; externalId: %s", forge.getName(), dep.name, dep.version, dep.externalId.createBdioId()));
            dependencies.addChildToRoot(dep);
        }
    }

    private Optional<String> getPackageVersion(final Optional<String> packageName) {
        Optional<String> packageVersion = Optional.empty();
        if (packageName.isPresent()) {
            final String getPackageVersionCommand = String.format("dpkg -s %s", packageName.get());
            try {
                final String packageStatusOutput = SimpleExecutor.execute(new File("."), null, getPackageVersionCommand);
                logger.info(String.format("packageStatusOutput: %s", packageStatusOutput));
                packageVersion = getPackageVersionFromStatusOutput(packageName.get(), packageStatusOutput);
            } catch (ExecutableRunnerException | IntegrationException e) {
                logger.error(String.format("Error executing %s: %s", getPackageVersionCommand, e.getMessage()));
            }
        }
        return packageVersion;
    }

    private Optional<String> getPackageVersionFromStatusOutput(final String packageName, final String packageStatusOutput) {
        final String[] packageStatusOutputLines = packageStatusOutput.split("\\n");
        for (final String packageStatusOutputLine : packageStatusOutputLines) {
            final String[] packageStatusOutputLineNameValue = packageStatusOutputLine.split(":\\s+");
            final String label = packageStatusOutputLineNameValue[0];
            final String value = packageStatusOutputLineNameValue[1];
            if ("Status".equals(label.trim()) && !value.contains("installed")) {
                logger.info(String.format("%s is not installed; Status is: %s", packageName, value));
                return Optional.empty();
            }
            if ("Version".equals(label)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private Optional<String[]> getPackageNameArch(final File dependencyFile) {
        final String getPackageCommand = String.format("dpkg -S %s", dependencyFile.getAbsolutePath());
        try {
            final String queryPackageOutput = SimpleExecutor.execute(new File("."), null, getPackageCommand);
            logger.info(String.format("queryPackageOutput: %s", queryPackageOutput));
            final String[] queryPackageOutputParts = queryPackageOutput.split("\\s+");
            final String[] packageNameArchParts = queryPackageOutputParts[0].split(":");
            return Optional.of(packageNameArchParts);
        } catch (ExecutableRunnerException | IntegrationException e) {
            logger.error(String.format("Error executing %s: %s", getPackageCommand, e.getMessage()));
            return Optional.empty();
        }
    }

    private Optional<String> getPackageName(final Optional<String[]> packageNameArch) {
        if (packageNameArch.isPresent()) {
            return Optional.of(packageNameArch.get()[0]);
        }
        return Optional.empty();
    }

    private Optional<String> getPackageArch(final Optional<String[]> packageNameArch) {
        if (packageNameArch.isPresent()) {
            return Optional.of(packageNameArch.get()[1]);
        }
        return Optional.empty();
    }

    private List<String> getDependencyFilePaths(final File depsMkFile) throws IOException {
        final String depsDecl = FileUtils.readFileToString(depsMkFile, StandardCharsets.UTF_8);
        final String[] depsDeclParts = depsDecl.split(": ");
        String depsListString = depsDeclParts[1];
        logger.info(String.format("dependencies: %s", depsListString));

        depsListString = depsListString.replaceAll("\n", " ");
        logger.info(String.format("dependencies, newlines removed: %s", depsListString));

        depsListString = depsListString.replaceAll("\\\\", " ");
        logger.info(String.format("dependencies, backslashes removed: %s", depsListString));

        final String[] deps = depsListString.split("\\s+");
        for (final String includeFile : deps) {
            logger.info(String.format("\t%s", includeFile));
        }
        return Arrays.asList(deps);
    }

    private List<File> getDependencyFiles(final List<String> dependencies) {
        final List<File> dependencyFiles = new ArrayList<>(dependencies.size());
        for (final String dependency : dependencies) {
            final File dependencyFile = new File(dependency);
            if (!dependencyFile.exists()) {
                logger.error(String.format("%s does not exist", dependencyFile.getAbsolutePath()));
            } else {
                logger.info(String.format("Happily, %s exists", dependencyFile.getAbsolutePath()));
                dependencyFiles.add(dependencyFile);
            }
        }
        return dependencyFiles;
    }
}
