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
package org.apache.maven.executor.embedded;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.apache.maven.executor.Environment;
import org.apache.maven.executor.ExecutorRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Embedded Maven runs in this JVM, but {@code java.util.logging} is JVM-global. A handler that Maven installs on the
 * root logger comes from a Maven class realm, and must not outlive the executor that closed that realm.
 */
@Timeout(120)
public class EmbeddedMavenExecutorLoggingTest {
    @TempDir
    private Path tempDir;

    @Test
    void closeRemovesJulHandlersInstalledByMaven3() throws Exception {
        assertCloseLeavesRootHandlersAlone(Paths.get(Environment.MAVEN3_HOME));
    }

    @Test
    void closeRemovesJulHandlersInstalledByMaven4() throws Exception {
        assertCloseLeavesRootHandlersAlone(Paths.get(Environment.MAVEN4_HOME));
    }

    private void assertCloseLeavesRootHandlersAlone(Path mavenHome) throws Exception {
        Path cwd =
                Files.createDirectories(tempDir.resolve("cwd").resolve(".mvn")).getParent();
        Path userHome = Files.createDirectories(tempDir.resolve("home"));
        Logger root = Logger.getLogger("");
        Set<Handler> before = new HashSet<>(Arrays.asList(root.getHandlers()));
        try (EmbeddedMavenExecutor executor = new EmbeddedMavenExecutor(mavenHome)) {
            executor.execute(ExecutorRequest.mavenBuilder()
                    .cwd(cwd)
                    .userHomeDirectory(userHome)
                    .argument("--version")
                    .build());
        } finally {
            Set<Handler> leaked = new HashSet<>(Arrays.asList(root.getHandlers()));
            leaked.removeAll(before);
            leaked.forEach(root::removeHandler); // keep this test from poisoning the rest of the JVM
            assertEquals(new HashSet<Handler>(), leaked, "Handlers left on the JUL root logger");
        }
    }
}
