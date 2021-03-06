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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.bdio.SimpleBdioFactory;
import com.blackducksoftware.integration.hub.bdio.graph.MutableDependencyGraph;
import com.blackducksoftware.integration.hub.bdio.model.Forge;
import com.blackducksoftware.integration.hub.bdio.model.SimpleBdioDocument;
import com.blackducksoftware.integration.hub.bdio.model.dependency.Dependency;
import com.blackducksoftware.integration.hub.bdio.model.externalid.ExternalId;
import com.blackducksoftware.integration.hub.clang.execute.Executor;
import com.blackducksoftware.integration.hub.clang.execute.fromdetect.ExecutableRunnerException;
import com.blackducksoftware.integration.hub.clang.pkgmgr.PkgMgr;
import com.google.gson.Gson;

@Component
public class ClangExtractor {
    private static final String COMPILE_CMD_PATTERN_WITH_DEPENDENCY_OUTPUT_FILE = "%s -M -MF %s";
    public static final String DEPS_MK_PATH = "deps.mk";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Set<File> processedDependencyFiles = new HashSet<>(200);
    private final Set<PackageDetails> processedDependencies = new HashSet<>(40);

    @Autowired
    private List<PkgMgr> pkgMgrs;

    public SimpleBdioDocument extract(final File sourceDir, final Executor executor, final String compileCommandsJsonFilePath, final String workingDirPath, final String codeLocationName, final String projectName,
            final String projectVersion, final Set<File> filesForIScan)
            throws IOException, ExecutableRunnerException, IntegrationException {
        logger.debug(String.format("extract() called; compileCommandsJsonFilePath: %s", compileCommandsJsonFilePath));
        final PkgMgr pkgMgr = selectPkgMgr(executor);
        final File workingDir = new File(workingDirPath);
        final ExternalId projectExternalId = new SimpleBdioFactory().createNameVersionExternalId(pkgMgr.getDefaultForge(), projectName, projectVersion);
        final SimpleBdioDocument bdioDocument = new SimpleBdioFactory().createSimpleBdioDocument(codeLocationName, projectName, projectVersion, projectExternalId);
        final MutableDependencyGraph dependencyGraph = new SimpleBdioFactory().createMutableDependencyGraph();
        final List<CompileCommand> compileCommands = parseCompileCommandsFile(compileCommandsJsonFilePath);
        final Set<String> dependencyFilePaths = getDependencyFilePaths(sourceDir, executor, pkgMgr, workingDir, dependencyGraph, filesForIScan, compileCommands);
        final Set<DependencyFile> dependencyFiles = getNewValidDependencyFiles(sourceDir, dependencyFilePaths);
        final Set<PackageDetails> packages = getPackages(executor, pkgMgr, dependencyFiles, filesForIScan);
        final List<Dependency> bdioComponents = getBdioComponents(pkgMgr, packages);
        populateGraph(dependencyGraph, bdioComponents);
        new SimpleBdioFactory().populateComponents(bdioDocument, projectExternalId, dependencyGraph);
        return bdioDocument;
    }

    private void populateGraph(final MutableDependencyGraph graph, final List<Dependency> bdioComponents) {
        for (final Dependency bdioComponent : bdioComponents) {
            graph.addChildToRoot(bdioComponent);
        }
    }

    private List<Dependency> getBdioComponents(final PkgMgr pkgMgr, final Set<PackageDetails> packages) {
        final List<Dependency> dependencies = new ArrayList<>();
        for (final PackageDetails pkg : packages) {
            logger.debug(String.format("Package name//arch//version: %s//%s//%s", pkg.getPackageName().orElse("<missing>"), pkg.getPackageArch().orElse("<missing>"),
                    pkg.getPackageVersion().orElse("<missing>")));
            if (dependencyAlreadyProcessed(pkg)) {
                logger.trace(String.format("dependency %s has already been processed", pkg.toString()));
            } else if (pkg.getPackageName().isPresent() && pkg.getPackageVersion().isPresent() && pkg.getPackageArch().isPresent()) {
                dependencies.addAll(getBdioComponents(pkgMgr, pkg.getPackageName().get(), pkg.getPackageVersion().get(), pkg.getPackageArch().get()));
            }
        }
        return dependencies;
    }

    private List<Dependency> getBdioComponents(final PkgMgr pkgMgr, final String name, final String version, final String arch) {
        final List<Dependency> dependencies = new ArrayList<>();
        final String externalId = String.format("%s/%s/%s", name, version, arch);
        logger.trace(String.format("Constructed externalId: %s", externalId));
        for (final Forge forge : pkgMgr.getForges()) {
            final ExternalId extId = new SimpleBdioFactory().createArchitectureExternalId(forge, name, version, arch);
            final Dependency dep = new SimpleBdioFactory().createDependency(name, version, extId);
            logger.info(String.format("forge: %s: adding %s version %s as child to dependency node tree; externalId: %s", forge.getName(), dep.name, dep.version, dep.externalId.createBdioId()));
            dependencies.add(dep);
        }
        return dependencies;
    }

    private List<CompileCommand> parseCompileCommandsFile(final String compileCommandsJsonFilePath) throws IOException {
        final File compileCommandsJsonFile = new File(compileCommandsJsonFilePath);
        final String compileCommandsJson = FileUtils.readFileToString(compileCommandsJsonFile, StandardCharsets.UTF_8);
        final Gson gson = new Gson();
        final CompileCommand[] compileCommands = gson.fromJson(compileCommandsJson, CompileCommand[].class);
        return Arrays.asList(compileCommands);
    }

    private Set<String> getDependencyFilePaths(final File sourceDir, final Executor executor, final PkgMgr pkgMgr, final File workingDir, final MutableDependencyGraph dependencyGraph, final Set<File> filesForIScan,
            final List<CompileCommand> compileCommands) {
        final Set<String> dependencyFilePaths = new HashSet<>();
        for (final CompileCommand compileCommand : compileCommands) {
            logger.debug(String.format("compileCommand:\n\tdirectory: %s;\n\tcommand: %s;\n\tfile: %s", compileCommand.directory, compileCommand.command, compileCommand.file));
            final Optional<File> depsMkFile = generateDependencyFileByCompiling(executor, workingDir, compileCommand);
            dependencyFilePaths.addAll(parseDependencyFile(depsMkFile));
        }
        return dependencyFilePaths;
    }

    private PkgMgr selectPkgMgr(final Executor executor) throws IntegrationException {
        PkgMgr pkgMgr = null;
        for (final PkgMgr pkgMgrCandidate : pkgMgrs) {
            if (pkgMgrCandidate.applies(executor)) {
                pkgMgr = pkgMgrCandidate;
                break;
            }
        }
        if (pkgMgr == null) {
            throw new IntegrationException("Unable to execute any supported package manager; Please make sure that one of the supported package managers is on the PATH");
        }
        return pkgMgr;
    }

    private Optional<File> generateDependencyFileByCompiling(final Executor executor, final File workingDir,
            final CompileCommand compileCommand) {

        final File depsMkFile = new File(workingDir, DEPS_MK_PATH);
        final String generateDependenciesFileCommand = String.format(COMPILE_CMD_PATTERN_WITH_DEPENDENCY_OUTPUT_FILE, compileCommand.command, depsMkFile.getAbsolutePath());
        try {
            executor.execute(new File(compileCommand.directory), null, generateDependenciesFileCommand);
        } catch (ExecutableRunnerException | IntegrationException e) {
            logger.debug(String.format("Error compiling with command '%s': %s", generateDependenciesFileCommand, e.getMessage()));
            return Optional.empty();
        }
        return Optional.of(depsMkFile);
    }

    private List<String> parseDependencyFile(final Optional<File> depsMkFile) {
        if (!depsMkFile.isPresent()) {
            return new ArrayList<>(0);
        }
        List<String> dependencyFilePaths;
        try {
            final String depsDecl = FileUtils.readFileToString(depsMkFile.get(), StandardCharsets.UTF_8);
            final String[] depsDeclParts = depsDecl.split(": ");
            String depsListString = depsDeclParts[1];
            logger.trace(String.format("dependencies: %s", depsListString));

            depsListString = depsListString.replaceAll("\n", " ");
            logger.trace(String.format("dependencies, newlines removed: %s", depsListString));

            depsListString = depsListString.replaceAll("\\\\", " ");
            logger.trace(String.format("dependencies, backslashes removed: %s", depsListString));

            final String[] deps = depsListString.split("\\s+");
            for (final String includeFile : deps) {
                logger.trace(String.format("\t%s", includeFile));
            }
            dependencyFilePaths = Arrays.asList(deps);
        } catch (final IOException e) {
            logger.warn(String.format("Error getting dependency file paths from '%s': %s", depsMkFile.get().getAbsolutePath(), e.getMessage()));
            return new ArrayList<>(0);
        }
        return dependencyFilePaths;
    }

    private Set<PackageDetails> getPackages(final Executor executor, final PkgMgr pkgMgr, final Set<DependencyFile> dependencyFiles, final Set<File> filesForIScan) {
        final Set<PackageDetails> packages = new HashSet<>();
        for (final DependencyFile dependencyFile : dependencyFiles) {
            packages.addAll(pkgMgr.getDependencyDetails(executor, filesForIScan, dependencyFile));
        }
        return packages;
    }

    private Set<DependencyFile> getNewValidDependencyFiles(final File sourceDir, final Set<String> dependencyFilePaths) {
        final Set<DependencyFile> dependencyFiles = new HashSet<>(dependencyFilePaths.size());
        for (final String dependency : dependencyFilePaths) {
            if (StringUtils.isBlank(dependency)) {
                continue;
            }
            logger.trace(String.format("Expanding dependency %s to full path", dependency));
            final File dependencyFile = new File(dependency);
            if (dependencyFileAlreadyProcessed(dependencyFile)) {
                logger.trace(String.format("Dependency file %s has already been processed", dependencyFile.getAbsolutePath()));
            } else if (!dependencyFile.exists()) {
                logger.debug(String.format("Dependency file %s does NOT exist", dependencyFile.getAbsolutePath()));
            } else {
                logger.trace(String.format("Dependency file %s does exist", dependencyFile.getAbsolutePath()));
                final DependencyFile dependencyFileWrapper = new DependencyFile(isUnder(sourceDir, dependencyFile) ? true : false, dependencyFile);
                dependencyFiles.add(dependencyFileWrapper);
            }
        }
        return dependencyFiles;
    }

    private boolean isUnder(final File dir, final File file) {
        logger.trace(String.format("Checking to see if file %s is under dir %s", file.getAbsolutePath(), dir.getAbsolutePath()));
        try {
            final String dirPath = dir.getCanonicalPath();
            final String filePath = file.getCanonicalPath();
            logger.trace(String.format("\tactually comparing file path %s with dir path %s", filePath, dirPath));
            if (filePath.equals(dirPath) || filePath.startsWith(dirPath)) {
                logger.trace("it is");
                return true;
            }
            logger.trace("it is not");
            return false;
        } catch (final IOException e) {
            logger.warn(String.format("Error getting canonical path for either %s or %s", dir.getAbsolutePath(), file.getAbsolutePath()));
            return false;
        }
    }

    private boolean dependencyFileAlreadyProcessed(final File dependencyFile) {
        if (processedDependencyFiles.contains(dependencyFile)) {
            return true;
        }
        processedDependencyFiles.add(dependencyFile);
        return false;
    }

    private boolean dependencyAlreadyProcessed(final PackageDetails dependency) {
        if (processedDependencies.contains(dependency)) {
            return true;
        }
        processedDependencies.add(dependency);
        return false;
    }
}
