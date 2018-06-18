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

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
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
import com.blackducksoftware.integration.hub.clang.execute.SimpleExecutor;
import com.blackducksoftware.integration.hub.clang.execute.fromdetect.ExecutableRunnerException;
import com.blackducksoftware.integration.hub.clang.pkgmgr.PkgMgr;
import com.google.gson.Gson;

@Component
public class ClangExtractor {
    private static final String COMPILE_COMMANDS_JSON_FILENAME = "compile_commands.json";
    private static final String DEPS_MK_PATH = "deps.mk";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private PkgMgr pkgMgr = null;

    @Autowired
    private List<PkgMgr> pkgMgrs;

    @PostConstruct
    public void init() throws IntegrationException {
        for (final PkgMgr pkgMgr : pkgMgrs) {
            if (pkgMgr.applies()) {
                this.pkgMgr = pkgMgr;
                return;
            }
        }
        throw new IntegrationException("Unable to execute any supported package manager; Please make sure that one of the supported package managers is on the PATH");
    }

    public SimpleBdioDocument extract(final String buildDirPath, final String codeLocationName, final String projectName, final String projectVersion) throws IOException, ExecutableRunnerException, IntegrationException {
        logger.debug(String.format("extract() called; buildDirPath: %s", buildDirPath));
        final ExternalId projectExternalId = new SimpleBdioFactory().createNameVersionExternalId(pkgMgr.getDefaultForge(), projectName, projectVersion);
        final SimpleBdioDocument bdioDocument = new SimpleBdioFactory().createSimpleBdioDocument(codeLocationName, projectName, projectVersion, projectExternalId);
        final MutableDependencyGraph dependencyGraph = new SimpleBdioFactory().createMutableDependencyGraph();
        final File buildDir = new File(buildDirPath);
        final File compileCommandsJsonFile = new File(buildDir, COMPILE_COMMANDS_JSON_FILENAME);
        final String compileCommandsJson = FileUtils.readFileToString(compileCommandsJsonFile, StandardCharsets.UTF_8);
        final Gson gson = new Gson();
        final CompileCommand[] compileCommands = gson.fromJson(compileCommandsJson, CompileCommand[].class);
        for (final CompileCommand compileCommand : compileCommands) {
            logger.debug(String.format("compileCommand:\n\tdirectory: %s;\n\tcommand: %s;\n\tfile: %s", compileCommand.directory, compileCommand.command, compileCommand.file));
            processCompileCommand(dependencyGraph, compileCommand);
        }
        new SimpleBdioFactory().populateComponents(bdioDocument, projectExternalId, dependencyGraph);
        return bdioDocument;
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
            final DependencyDetails dependencyDetails = pkgMgr.getDependencyDetails(dependencyFile);
            logger.info(String.format("Package name//arch//version: %s//%s//%s", dependencyDetails.getPackageName().orElse("<missing>"), dependencyDetails.getPackageArch().orElse("<missing>"),
                    dependencyDetails.getPackageVersion().orElse("<missing>")));
            if (dependencyDetails.getPackageName().isPresent() && dependencyDetails.getPackageVersion().isPresent() && dependencyDetails.getPackageArch().isPresent()) {
                createBdioComponent(dependencyGraph, dependencyDetails.getPackageName().get(), dependencyDetails.getPackageVersion().get(), dependencyDetails.getPackageArch().get());
            }
        }
    }

    private void createBdioComponent(final MutableDependencyGraph dependencies, final String name, final String version, final String arch) {
        final String externalId = String.format("%s/%s/%s", name, version, arch);
        logger.trace(String.format("Constructed externalId: %s", externalId));
        for (final Forge forge : pkgMgr.getForges()) {
            final ExternalId extId = new SimpleBdioFactory().createArchitectureExternalId(forge, name, version, arch);
            final Dependency dep = new SimpleBdioFactory().createDependency(name, version, extId); // createDependencyNode(forge, name, version, arch);
            logger.info(String.format("*** forge: %s: adding %s version %s as child to dependency node tree; externalId: %s", forge.getName(), dep.name, dep.version, dep.externalId.createBdioId()));
            dependencies.addChildToRoot(dep);
        }
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
