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

import java.util.Optional;

public class DependencyDetails {
    private final Optional<String> packageName;
    private final Optional<String> packageVersion;
    private final Optional<String> packageArch;

    public DependencyDetails(final Optional<String> packageName, final Optional<String> packageVersion, final Optional<String> packageArch) {
        this.packageName = packageName;
        this.packageVersion = packageVersion;
        this.packageArch = packageArch;
    }

    public Optional<String> getPackageName() {
        return packageName;
    }

    public Optional<String> getPackageVersion() {
        return packageVersion;
    }

    public Optional<String> getPackageArch() {
        return packageArch;
    }
}
