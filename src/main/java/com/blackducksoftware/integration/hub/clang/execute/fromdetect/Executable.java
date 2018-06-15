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
package com.blackducksoftware.integration.hub.clang.execute.fromdetect;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Executable {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final File workingDirectory;
    private final Map<String, String> environmentVariables = new HashMap<>();
    private final String cmd;

    public Executable(final File workingDirectory, final String cmd) {
        this.workingDirectory = workingDirectory;
        this.cmd = cmd;
    }

    public Executable(final File workingDirectory, final Map<String, String> environmentVariables, final String cmd) {
        this.workingDirectory = workingDirectory;
        if (environmentVariables != null) {
            this.environmentVariables.putAll(environmentVariables);
        }
        this.cmd = cmd;
    }

    public ProcessBuilder createProcessBuilder() {
        logger.info("createProcessBuilder()");
        final String[] cmdArgArray = cmd.split("\\s+");
        final List<String> processBuilderArguments = Arrays.asList(cmdArgArray);
        final ProcessBuilder processBuilder = new ProcessBuilder(processBuilderArguments);
        processBuilder.directory(workingDirectory);
        final Map<String, String> processBuilderEnvironment = processBuilder.environment();
        final Map<String, String> systemEnv = System.getenv();
        for (final String key : systemEnv.keySet()) {
            populateEnvironmentMap(processBuilderEnvironment, key, systemEnv.get(key));
        }
        for (final String key : environmentVariables.keySet()) {
            populateEnvironmentMap(processBuilderEnvironment, key, environmentVariables.get(key));
        }
        return processBuilder;
    }

    public String getDescription() {
        return cmd;
    }

    private void populateEnvironmentMap(final Map<String, String> environment, final Object key, final Object value) {
        // ProcessBuilder's environment's keys and values must be non-null java.lang.String's
        if (key != null && value != null) {
            final String keyString = key.toString();
            final String valueString = value.toString();
            if (keyString != null && valueString != null) {
                environment.put(keyString, valueString);
            }
        }
    }
}
