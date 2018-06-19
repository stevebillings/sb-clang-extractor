package com.blackducksoftware.integration.hub.clang.execute;

import java.io.File;
import java.util.Map;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.clang.execute.fromdetect.ExecutableRunnerException;

public interface Executor {

    String execute(File workingDir, Map<String, String> environmentVariables, String cmd) throws ExecutableRunnerException, IntegrationException;

}
