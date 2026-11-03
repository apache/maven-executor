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
package org.apache.maven.executor.forked;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.maven.executor.Environment;
import org.apache.maven.executor.Executor;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.apache.maven.executor.MavenExecutorTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forked executor UT
 */
public class ForkedMavenExecutorTest extends MavenExecutorTestSupport {

    @Override
    protected Executor doSelectExecutor(Path installationDirectory) {
        return new ForkedMavenExecutor(installationDirectory);
    }

    @Test
    void systemPropertyValueWithSpaces() throws Exception {
        layDownFiles(customizedRequest().build().cwd());
        for (String home : Arrays.asList(Environment.MAVEN3_HOME, Environment.MAVEN4_HOME)) {
            ExecutorResult result = createAndMemoizeExecutor(Paths.get(home))
                    .execute(customizedRequest()
                            .jvmSystemProperty("executor.p", "value with spaces")
                            .argument("help:evaluate")
                            .argument("-Dexpression=executor.p")
                            .build());
            String output =
                    result.stdOutString().orElse("") + result.stdErrString().orElse("");
            assertEquals(0, result.exitCode().orElse(-1), output);
            assertTrue(output.contains("value with spaces"), output);
        }
    }

    /**
     * Maven 4 only: its {@code mvn} script evaluates MAVEN_OPTS, so a quoted token survives; the Maven 3 script
     * word-splits it, and there a user home with a space cannot be passed.
     */
    @Test
    void userHomeWithSpaces4() throws Exception {
        ExecutorRequest.Builder builder = customizedRequest();
        Path userHome = builder.build().userHomeDirectory().resolveSibling("home with space");
        Files.createDirectories(userHome);
        ExecutorResult result = createAndMemoizeExecutor(Paths.get(Environment.MAVEN4_HOME))
                .execute(builder.userHomeDirectory(userHome)
                        .argument("eu.maveniverse.maven.plugins:toolbox:" + Environment.TOOLBOX_VERSION + ":gav-dump")
                        .build());
        String output = result.stdOutString().orElse("") + result.stdErrString().orElse("");
        assertEquals(0, result.exitCode().orElse(-1), output);
        assertTrue(
                output.contains(userHome.resolve(".m2").resolve("settings.xml").toString()), output);
    }
}
