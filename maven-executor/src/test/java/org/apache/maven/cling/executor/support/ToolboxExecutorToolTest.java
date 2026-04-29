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
package org.apache.maven.cling.executor.support;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.maven.cling.executor.Environment;
import org.apache.maven.cling.executor.ExecutorHelper;
import org.apache.maven.cling.executor.ExecutorRequest;
import org.apache.maven.cling.executor.embedded.EmbeddedMavenExecutor;
import org.apache.maven.cling.executor.forked.ForkedMavenExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
public class ToolboxExecutorToolTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    private static Path tempDir;

    private Path userHome;
    private Path cwd;

    @BeforeEach
    void beforeEach(TestInfo testInfo) throws Exception {
        String testName = testInfo.getTestMethod().orElseThrow().getName();
        userHome = tempDir.resolve(testName);
        cwd = userHome.resolve("cwd");
        Files.createDirectories(cwd.resolve(".mvn"));
        System.out.println("=== " + testInfo.getTestMethod().orElseThrow().getName());
    }

    private ExecutorRequest.Builder getExecutorRequest(ExecutorHelper helper) {
        ExecutorRequest.Builder builder = helper.executorRequest()
                .userHomeDirectory(userHome)
                .cwd(cwd)
                .argument("-Daether.remoteRepositoryFilter.prefixes=false");
        if (System.getProperty("localRepository") != null) {
            builder.argument("-Dmaven.repo.local.tail=" + System.getProperty("localRepository"));
        }
        return builder;
    }

    private Path mvn3Home() {
        return Path.of(Environment.MAVEN3_HOME);
    }

    private Path mvn4Home() {
        return Path.of(Environment.MAVEN4_HOME);
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void dump3(ExecutorHelper.Mode mode) throws Exception {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn3Home()), new ForkedMavenExecutor(mvn3Home()));
        Map<String, String> dump =
                new ToolboxExecutorTool(helper, Environment.TOOLBOX_VERSION).dump(getExecutorRequest(helper));
        System.out.println(mode.name() + ": " + dump.toString());
        assertEquals(System.getProperty("maven3version"), dump.get("maven.version"));
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void dump4(ExecutorHelper.Mode mode) throws Exception {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn4Home()), new ForkedMavenExecutor(mvn4Home()));
        Map<String, String> dump =
                new ToolboxExecutorTool(helper, Environment.TOOLBOX_VERSION).dump(getExecutorRequest(helper));
        System.out.println(mode.name() + ": " + dump.toString());
        assertEquals(System.getProperty("maven4version"), dump.get("maven.version"));
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void version3(ExecutorHelper.Mode mode) {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn3Home()), new ForkedMavenExecutor(mvn3Home()));
        System.out.println(mode.name() + ": " + helper.mavenVersion());
        assertEquals(System.getProperty("maven3version"), helper.mavenVersion());
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void version4(ExecutorHelper.Mode mode) {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn4Home()), new ForkedMavenExecutor(mvn4Home()));
        System.out.println(mode.name() + ": " + helper.mavenVersion());
        assertEquals(System.getProperty("maven4version"), helper.mavenVersion());
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void localRepository3(ExecutorHelper.Mode mode) {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn3Home()), new ForkedMavenExecutor(mvn3Home()));
        String localRepository = new ToolboxExecutorTool(helper, Environment.TOOLBOX_VERSION)
                .localRepository(getExecutorRequest(helper));
        System.out.println(mode.name() + ": " + localRepository);
        Path local = Paths.get(localRepository);
        assertTrue(Files.isDirectory(local));
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void localRepository4(ExecutorHelper.Mode mode) {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn4Home()), new ForkedMavenExecutor(mvn4Home()));
        String localRepository = new ToolboxExecutorTool(helper, Environment.TOOLBOX_VERSION)
                .localRepository(getExecutorRequest(helper));
        System.out.println(mode.name() + ": " + localRepository);
        Path local = Paths.get(localRepository);
        assertTrue(Files.isDirectory(local));
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void artifactPath3(ExecutorHelper.Mode mode) {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn3Home()), new ForkedMavenExecutor(mvn3Home()));
        String path = new ToolboxExecutorTool(helper, Environment.TOOLBOX_VERSION)
                .artifactPath(getExecutorRequest(helper), "aopalliance:aopalliance:1.0", "central");
        System.out.println(mode.name() + ": " + path);
        // split repository: assert "ends with" as split may introduce prefixes
        assertTrue(
                path.endsWith("aopalliance" + File.separator + "aopalliance" + File.separator + "1.0" + File.separator
                        + "aopalliance-1.0.jar"),
                "path=" + path);
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void artifactPath4(ExecutorHelper.Mode mode) {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn4Home()), new ForkedMavenExecutor(mvn4Home()));
        String path = new ToolboxExecutorTool(helper, Environment.TOOLBOX_VERSION)
                .artifactPath(getExecutorRequest(helper), "aopalliance:aopalliance:1.0", "central");
        System.out.println(mode.name() + ": " + path);
        // split repository: assert "ends with" as split may introduce prefixes
        assertTrue(
                path.endsWith("aopalliance" + File.separator + "aopalliance" + File.separator + "1.0" + File.separator
                        + "aopalliance-1.0.jar"),
                "path=" + path);
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void metadataPath3(ExecutorHelper.Mode mode) {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn3Home()), new ForkedMavenExecutor(mvn3Home()));
        String path = new ToolboxExecutorTool(helper, Environment.TOOLBOX_VERSION)
                .metadataPath(getExecutorRequest(helper), "aopalliance", "someremote");
        System.out.println(mode.name() + ": " + path);
        // split repository: assert "ends with" as split may introduce prefixes
        assertTrue(path.endsWith("aopalliance" + File.separator + "maven-metadata-someremote.xml"), "path=" + path);
    }

    @ParameterizedTest
    @EnumSource(ExecutorHelper.Mode.class)
    void metadataPath4(ExecutorHelper.Mode mode) {
        ExecutorHelper helper = new ExecutorHelperImpl(
                mode, new EmbeddedMavenExecutor(mvn4Home()), new ForkedMavenExecutor(mvn4Home()));
        String path = new ToolboxExecutorTool(helper, Environment.TOOLBOX_VERSION)
                .metadataPath(getExecutorRequest(helper), "aopalliance", "someremote");
        System.out.println(mode.name() + ": " + path);
        // split repository: assert "ends with" as split may introduce prefixes
        assertTrue(path.endsWith("aopalliance" + File.separator + "maven-metadata-someremote.xml"), "path=" + path);
    }
}
