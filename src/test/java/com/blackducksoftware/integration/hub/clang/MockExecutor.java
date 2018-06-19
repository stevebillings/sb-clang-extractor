package com.blackducksoftware.integration.hub.clang;

import java.io.File;
import java.util.Map;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.clang.execute.Executor;
import com.blackducksoftware.integration.hub.clang.execute.fromdetect.ExecutableRunnerException;

public class MockExecutor implements Executor {

    @Override
    public String execute(final File workingDir, final Map<String, String> environmentVariables, final String cmd) throws ExecutableRunnerException, IntegrationException {
        if ("dpkg --version".equals(cmd)) {
            return "Debian 'dpkg' package management program version 1.19.0.5 (amd64).";
        }
        if ("rpm --version".equals(cmd)) {
            return "RPM version 4.11.3";
        }
        if (cmd.startsWith("dpkg -S /")) {
            return "libc6-dev:amd64: /usr/include/wchar.h";
        }
        if (cmd.startsWith("dpkg -s ")) {
            return "Status: install ok installed\nVersion: 2.27-3ubuntu1\nx\n";
        }
        return String.format("exec: \\\"%s\\\": executable file not found in $PATH\": unknown", cmd);
    }

}
