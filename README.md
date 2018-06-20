# Overview

Clang Extractor is a standalone utility that is a proof-of-concept for a planned Hub Detect feature.

Given a C/C++ build directory and a JSON compilation database (``compile_commands.json file``), Clang Extractor will, for each compile command in the compile_commands.json file:
1. Use the compile command  (plus -M and -MF options) to generate a dependencies file. For each dependency:
2. Use the Linux package manager to determine to which package(s) the dependency belongs. For each package:
3. Include the package in a Hub BOM (BDIO) file, that can be uploaded into the Hub, creating a project, or adding to an existing project's Bill Of Materials (BOM).

# Downloading Clang Extractor

# Using Clang Extractor

```
java -jar sb-clang-extractor-<version>.jar [ <options> ]
```

Options:
```
--json.compilation.database.file=<path to compile_commands.json file> # default: ./compile_commands.json
--working.dir=<path to a dir to create intermediate files in> # default: .
--output.bom.file=<path to output file> # default: hub-bom-file.jsonld
--hub.code.location.name=<Hub code location name> # default: ClangExtractorCodeLocation
--hub.project.name=<Hub project name> # default: ClangExtractorProject
--hub.project.version=<Hub project version> # default: default
```


