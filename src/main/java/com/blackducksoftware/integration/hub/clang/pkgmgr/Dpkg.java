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
package com.blackducksoftware.integration.hub.clang.pkgmgr;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.bdio.model.Forge;
import com.blackducksoftware.integration.hub.clang.DependencyDetails;
import com.blackducksoftware.integration.hub.clang.execute.SimpleExecutor;
import com.blackducksoftware.integration.hub.clang.execute.fromdetect.ExecutableRunnerException;

@Component
public class Dpkg implements PkgMgr {

    private static final String PKG_MGR_NAME = "dpkg";
    private static final String VERSION_COMMAND = "dpkg --version";
    private static final String EXPECTED_TEXT = "package management program version";
    private static final String QUERY_DEPENDENCY_FILE_COMMAND_PATTERN = "dpkg -S %s";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final List<Forge> forges = Arrays.asList(Forge.UBUNTU, Forge.DEBIAN);

    @Override
    public Forge getDefaultForge() {
        return forges.get(0);
    }

    @Override
    public List<Forge> getForges() {
        return forges;
    }

    @Override
    public String getPkgMgrName() {
        return PKG_MGR_NAME;
    }

    @Override
    public String getCheckPresenceCommand() {
        return VERSION_COMMAND;
    }

    @Override
    public String getCheckPresenceCommandOutputExpectedText() {
        return EXPECTED_TEXT;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public List<DependencyDetails> getDependencyDetails(final File dependencyFile) {
        final List<DependencyDetails> dependencyDetailsList = new ArrayList<>(3);
        final String getPackageCommand = String.format(QUERY_DEPENDENCY_FILE_COMMAND_PATTERN, dependencyFile.getAbsolutePath());
        try {
            final String queryPackageOutput = SimpleExecutor.execute(new File("."), null, getPackageCommand);
            logger.info(String.format("queryPackageOutput: %s", queryPackageOutput));
            addToPackageList(dependencyDetailsList, queryPackageOutput);
        } catch (ExecutableRunnerException | IntegrationException e) {
            logger.error(String.format("Error executing %s: %s", getPackageCommand, e.getMessage()));

        }
        return dependencyDetailsList;
    }

    private void addToPackageList(final List<DependencyDetails> dependencyDetailsList, final String queryPackageOutput) {
        final String[] packageLines = queryPackageOutput.split("\n");
        for (final String packageLine : packageLines) {
            if (!valid(packageLine)) {
                logger.info(String.format("Skipping line: %s", packageLine));
                continue;
            }
            final String[] queryPackageOutputParts = packageLine.split("\\s+");
            final String[] packageNameArchParts = queryPackageOutputParts[0].split(":");
            final String packageName = packageNameArchParts[0];
            final String packageArch = packageNameArchParts[1];
            logger.debug(String.format("package name: %s; arch: %s", packageName, packageArch));
            final Optional<String> packageVersion = getPackageVersion(packageName);
            final DependencyDetails dependencyDetails = new DependencyDetails(Optional.of(packageName), packageVersion, Optional.of(packageArch));
            dependencyDetailsList.add(dependencyDetails);
        }
    }

    private boolean valid(final String packageLine) {
        if (packageLine.matches(".+:.+: .+")) {
            return true;
        }
        return false;
    }

    private Optional<String> getPackageVersion(final String packageName) {

        final String getPackageVersionCommand = String.format("dpkg -s %s", packageName);
        try {
            final String packageStatusOutput = SimpleExecutor.execute(new File("."), null, getPackageVersionCommand);
            logger.info(String.format("packageStatusOutput: %s", packageStatusOutput));
            final Optional<String> packageVersion = getPackageVersionFromStatusOutput(packageName, packageStatusOutput);
            return packageVersion;
        } catch (ExecutableRunnerException | IntegrationException e) {
            logger.error(String.format("Error executing %s: %s", getPackageVersionCommand, e.getMessage()));
        }

        return Optional.empty();
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
}
