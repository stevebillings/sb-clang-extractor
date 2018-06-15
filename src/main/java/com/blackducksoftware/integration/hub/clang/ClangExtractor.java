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

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.clang.execute.SimpleExecutor;
import com.blackducksoftware.integration.hub.clang.execute.fromdetect.ExecutableRunnerException;
import com.google.gson.Gson;

@Component
public class ClangExtractor {
    private static final String DEPS_MK_PATH = "deps.mk";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public void extract(final String buildDirPath) throws IOException, ExecutableRunnerException, IntegrationException {
        logger.debug(String.format("extract() called; buildDirPath: %s", buildDirPath));
        final File buildDir = new File(buildDirPath);
        final File compileCommandsJsonFile = new File(buildDir, "compile_commands.json");
        final String compileCommandsJson = FileUtils.readFileToString(compileCommandsJsonFile, StandardCharsets.UTF_8);
        final Gson gson = new Gson();
        final CompileCommand[] compileCommands = gson.fromJson(compileCommandsJson, CompileCommand[].class);
        for (final CompileCommand compileCommand : compileCommands) {
            logger.debug(String.format("compileCommand:\n\tdirectory: %s;\n\tcommand: %s;\n\tfile: %s", compileCommand.directory, compileCommand.command, compileCommand.file));
            processCompileCommand(compileCommand);
        }
    }

    private void processCompileCommand(final CompileCommand compileCommand) throws ExecutableRunnerException, IOException, IntegrationException {
        final File depsMkFile = new File(DEPS_MK_PATH);
        final String generateDependenciesFileCommand = String.format("%s -M -MF %s", compileCommand.command, depsMkFile.getAbsolutePath());
        SimpleExecutor.execute(new File(compileCommand.directory), null, generateDependenciesFileCommand);

        final List<String> dependencies = getDependencies(depsMkFile);
        final List<File> dependencyFiles = getDependencyFiles(dependencies);
    }

    private List<String> getDependencies(final File depsMkFile) throws IOException {
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
