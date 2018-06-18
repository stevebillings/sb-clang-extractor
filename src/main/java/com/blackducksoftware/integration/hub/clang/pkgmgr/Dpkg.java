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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.bdio.model.Forge;
import com.blackducksoftware.integration.hub.clang.DependencyDetails;
import com.blackducksoftware.integration.hub.clang.execute.SimpleExecutor;
import com.blackducksoftware.integration.hub.clang.execute.fromdetect.ExecutableRunnerException;
import com.blackducksoftware.integration.hub.imageinspector.lib.OperatingSystemEnum;

public class Dpkg implements PkgMgr {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final List<Forge> forges = Arrays.asList(OperatingSystemEnum.UBUNTU.getForge(), OperatingSystemEnum.DEBIAN.getForge());

    @Override
    public Forge getDefaultForge() {
        return forges.get(0);
    }

    @Override
    public List<Forge> getForges() {
        return forges;
    }

    @Override
    public DependencyDetails getDependencyDetails(final File dependencyFile) {
        final Optional<String[]> packageNameArch = getPackageNameArch(dependencyFile);
        final Optional<String> packageName = getPackageName(packageNameArch);
        final Optional<String> packageArch = getPackageArch(packageNameArch);
        final Optional<String> packageVersion = getPackageVersion(packageName);
        return new DependencyDetails(packageName, packageVersion, packageArch);
    }

    private Optional<String> getPackageVersion(final Optional<String> packageName) {
        if (packageName.isPresent()) {
            final String getPackageVersionCommand = String.format("dpkg -s %s", packageName.get());
            try {
                final String packageStatusOutput = SimpleExecutor.execute(new File("."), null, getPackageVersionCommand);
                logger.info(String.format("packageStatusOutput: %s", packageStatusOutput));
                final Optional<String> packageVersion = getPackageVersionFromStatusOutput(packageName.get(), packageStatusOutput);
                return packageVersion;
            } catch (ExecutableRunnerException | IntegrationException e) {
                logger.error(String.format("Error executing %s: %s", getPackageVersionCommand, e.getMessage()));
            }
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
}
