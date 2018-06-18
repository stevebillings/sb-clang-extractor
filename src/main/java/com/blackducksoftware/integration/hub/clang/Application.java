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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.blackducksoftware.integration.hub.bdio.BdioWriter;
import com.blackducksoftware.integration.hub.bdio.SimpleBdioFactory;
import com.blackducksoftware.integration.hub.bdio.model.SimpleBdioDocument;
import com.google.gson.Gson;

@SpringBootApplication
public class Application {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ClangExtractor clangExtractor;

    @Value("${build.dir:.}")
    private String buildDir;

    @Value("${code.location.name:ClangExtractorCodeLocation}")
    private String codeLocationName;

    @Value("${project.name:ClangExtractorProject}")
    private String projectName;

    @Value("${project.version:default}")
    private String projectVersion;

    public static void main(final String[] args) {
        new SpringApplicationBuilder(Application.class).logStartupInfo(false).run(args);
    }

    @PostConstruct
    public void run() {
        try {
            final SimpleBdioDocument bdioDocument = clangExtractor.extract(buildDir, codeLocationName, projectName, projectVersion);
            logger.info(String.format("Generated BDIO document BOM spdxName: %s", bdioDocument.billOfMaterials.spdxName));
            writeBdioToFile(bdioDocument, new File("clangExtractor.jsonld"));
        } catch (final Exception e) {
            logger.error(String.format("Error: %s", e.getMessage()), e);
        }
    }

    private void writeBdioToFile(final SimpleBdioDocument bdioDocument, final File bdioOutputFile) throws IOException, FileNotFoundException {
        try (FileOutputStream bdioOutputStream = new FileOutputStream(bdioOutputFile)) {
            try (BdioWriter bdioWriter = new BdioWriter(new Gson(), bdioOutputStream)) {
                new SimpleBdioFactory().writeSimpleBdioDocument(bdioWriter, bdioDocument);
            }
        }
    }
}
