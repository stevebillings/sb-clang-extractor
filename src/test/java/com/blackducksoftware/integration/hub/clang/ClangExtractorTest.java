package com.blackducksoftware.integration.hub.clang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.bdio.model.BdioComponent;
import com.blackducksoftware.integration.hub.bdio.model.SimpleBdioDocument;
import com.blackducksoftware.integration.hub.clang.execute.Executor;
import com.blackducksoftware.integration.hub.clang.execute.fromdetect.ExecutableRunnerException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { AppConfig.class })
public class ClangExtractorTest {

    @Autowired
    private ClangExtractor extractor;

    @BeforeClass
    public static void setup() throws ExecutableRunnerException, IntegrationException {
        final File workingDir = new File("test");
        workingDir.mkdir();
    }

    @Test
    public void test() throws IntegrationException, IOException, ExecutableRunnerException {
        final Executor executor = new MockExecutor();
        final SimpleBdioDocument bdio = extractor.extract(executor, "src/test/resources/buildDir", "src/test/resources/buildDir", "testCodeLocationName", "testProjectName", "testProjectVersion");
        assertEquals("testCodeLocationName", bdio.billOfMaterials.spdxName);
        boolean foundDebianComp = false;
        boolean foundUbuntuComp = false;
        for (final BdioComponent comp : bdio.components) {
            if ("http:ubuntu/libc6_dev/2_27_3ubuntu1/amd64".equals(comp.id)) {
                System.out.printf("Found %s\n", comp.id);
                assertEquals("libc6-dev", comp.name);
                assertEquals("2.27-3ubuntu1", comp.version);
                assertEquals("Component", comp.type);
                foundUbuntuComp = true;
            } else if ("http:debian/libc6_dev/2_27_3ubuntu1/amd64".equals(comp.id)) {
                System.out.printf("Found %s\n", comp.id);
                assertEquals("libc6-dev", comp.name);
                assertEquals("2.27-3ubuntu1", comp.version);
                assertEquals("Component", comp.type);
                foundDebianComp = true;
            }
        }
        assertTrue(foundUbuntuComp && foundDebianComp);
    }

}
