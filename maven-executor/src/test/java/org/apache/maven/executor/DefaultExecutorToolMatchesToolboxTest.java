/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.executor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;

import org.apache.maven.executor.support.DefaultExecutorTool;
import org.apache.maven.executor.support.ToolboxExecutorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultExecutorTool} gives the answers that the Maven under test gives through {@link ToolboxExecutorTool}.
 */
@Timeout(300)
class DefaultExecutorToolMatchesToolboxTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    private static Path tempDir;

    private Path userHome;
    private Path cwd;

    @BeforeEach
    void beforeEach(TestInfo testInfo) throws Exception {
        String testName = testInfo.getTestMethod()
                        .orElseThrow(() -> new NoSuchElementException("No such element"))
                        .getName()
                + testInfo.getDisplayName().replaceAll("\\W", "");
        userHome = tempDir.resolve(testName);
        cwd = userHome.resolve("cwd");
        Files.createDirectories(cwd.resolve(".mvn"));
    }

    private ExecutorRequest.Builder request() {
        ExecutorRequest.Builder builder = ExecutorRequest.mavenBuilder()
                .userHomeDirectory(userHome)
                .cwd(cwd)
                .argument("-Daether.remoteRepositoryFilter.prefixes=false");
        if (System.getProperty("localRepository") != null) {
            builder.argument("-Dmaven.repo.local.tail=" + System.getProperty("localRepository"));
        }
        return builder;
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "4"})
    void sameAnswers(String maven) throws Exception {
        Path mavenHome = Paths.get("3".equals(maven) ? Environment.MAVEN3_HOME : Environment.MAVEN4_HOME);
        try (ExecutorHelper helper = ExecutorHelper.forMavenInstallation(mavenHome, ExecutorHelper.Mode.FORKED)) {
            ExecutorTool toolbox = new ToolboxExecutorTool(helper, Environment.TOOLBOX_VERSION);
            ExecutorTool local = new DefaultExecutorTool(mavenHome);

            assertTrue(Files.isSameFile(
                    Paths.get(toolbox.localRepository(request())), Paths.get(local.localRepository(request()))));

            for (String gav : new String[] {
                "aopalliance:aopalliance:1.0",
                "org.example:lib:pom:1.0",
                "org.example:lib:jar:tests:1.0",
                "org.example:lib:1.0-SNAPSHOT",
                "org.example:lib:1.0-20260101.120000-3"
            }) {
                for (String repositoryId : new String[] {null, "central"}) {
                    assertEquals(
                            toolbox.artifactPath(request(), gav, repositoryId),
                            local.artifactPath(request(), gav, repositoryId),
                            gav + " from " + repositoryId);
                }
            }
            for (String gav : new String[] {"aopalliance", "org.example:lib::", "org.example:lib:1.0-SNAPSHOT:"}) {
                for (String repositoryId : new String[] {null, "someremote"}) {
                    assertEquals(
                            toolbox.metadataPath(request(), gav, repositoryId),
                            local.metadataPath(request(), gav, repositoryId),
                            gav + " from " + repositoryId);
                }
            }
        }
    }
}
